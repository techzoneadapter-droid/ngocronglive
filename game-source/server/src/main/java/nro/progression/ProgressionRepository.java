package nro.progression;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import static nro.progression.UnlimitedProgression.Stat.*;

/** Caller-owned transactions; never reads or writes inventory or the legacy stats column. */
public final class ProgressionRepository {
    public record Saved(long playerId, long revision, UnlimitedProgression state) {}
    private static final int FORMAT = 1;
    private ProgressionRepository() {}

    /** Run explicitly at startup, outside a gameplay transaction (DDL can commit implicitly). */
    public static void initialize(Connection connection) throws SQLException {
        if(!connection.getAutoCommit()) throw new SQLException("Initialize schema outside a transaction");
        try(var query=connection.createStatement()) {
            query.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_progression_state (
                    player_id BIGINT PRIMARY KEY,
                    revision BIGINT NOT NULL DEFAULT 0,
                    format_version INT NOT NULL,
                    stats LONGTEXT NOT NULL,
                    migrated_legacy LONGTEXT NOT NULL,
                    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3)
                ) ENGINE=InnoDB
                """);
        }
    }

    public static Saved loadOrMigrate(Connection connection,long playerId) throws SQLException {
        requireTransaction(connection);
        if(playerId<=0)throw new IllegalArgumentException("Invalid player");
        // All mutations use player -> progression lock order, including first migration.
        String legacy;
        try(var query=connection.prepareStatement("SELECT data_point FROM player WHERE id=? FOR UPDATE")) {
            query.setLong(1,playerId);
            try(var rows=query.executeQuery()) {
                if(!rows.next())throw new SQLException("Player not found");
                legacy=rows.getString(1);
            }
        }
        try(var query=connection.prepareStatement("SELECT revision,format_version,stats FROM nero_progression_state WHERE player_id=? FOR UPDATE")) {
            query.setLong(1,playerId);
            try(var rows=query.executeQuery()) {
                if(rows.next()) {
                    if(rows.getInt(2)!=FORMAT)throw new SQLException("Unsupported progression format; refusing to overwrite");
                    try { return new Saved(playerId,rows.getLong(1),decode(rows.getString(3))); }
                    catch(RuntimeException invalid) { throw new SQLException("Invalid saved progression; refusing fallback migration",invalid); }
                }
            }
        }
        UnlimitedProgression state;
        try { state=fromLegacy(legacy); }
        catch(RuntimeException invalid) { throw new SQLException("Invalid original progression; nothing migrated",invalid); }
        try(var insert=connection.prepareStatement("INSERT INTO nero_progression_state(player_id,revision,format_version,stats,migrated_legacy) VALUES(?,0,?,?,?)")) {
            insert.setLong(1,playerId);insert.setInt(2,FORMAT);insert.setString(3,encode(state));insert.setString(4,legacy);
            insert.executeUpdate();
        }
        return new Saved(playerId,0,state);
    }

    public static Saved save(Connection connection,Saved previous,UnlimitedProgression next) throws SQLException {
        requireTransaction(connection);
        long revision;
        try { revision=Math.incrementExact(previous.revision()); }
        catch(ArithmeticException invalid) { throw new SQLException("Progression revision overflow",invalid); }
        // Acquire the same lock as migration; a stale caller must not clobber another allocation.
        try(var lock=connection.prepareStatement("SELECT id FROM player WHERE id=? FOR UPDATE")) {
            lock.setLong(1,previous.playerId());
            try(var rows=lock.executeQuery()) { if(!rows.next())throw new SQLException("Player not found"); }
        }
        try(var update=connection.prepareStatement("UPDATE nero_progression_state SET stats=?,revision=? WHERE player_id=? AND revision=? AND format_version=?")) {
            update.setString(1,encode(next));update.setLong(2,revision);update.setLong(3,previous.playerId());
            update.setLong(4,previous.revision());update.setInt(5,FORMAT);
            if(update.executeUpdate()!=1)throw new SQLException("Stale progression state; reload before retrying","40001");
        }
        return new Saved(previous.playerId(),revision,next);
    }

    public static UnlimitedProgression fromLegacy(String json) {
        JSONArray old=new JSONArray(json);
        if(old.length()<13)throw new IllegalArgumentException("Incomplete legacy stats");
        var values=new EnumMap<UnlimitedProgression.Stat,BigInteger>(UnlimitedProgression.Stat.class);
        int[] indexes={11,8,12,2,10,7};
        var stats=new UnlimitedProgression.Stat[]{POWER,POTENTIAL,HP,KI,ATTACK,DEFENSE};
        for(int i=0;i<stats.length;i++) {
            String value=old.get(indexes[i]).toString();
            if(!value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Invalid original stat: "+stats[i]);
            values.put(stats[i],new BigInteger(value));
        }
        return new UnlimitedProgression(values);
    }
    private static String encode(UnlimitedProgression state) { return new JSONObject(state.snapshot()).toString(); }
    private static UnlimitedProgression decode(String json) {
        JSONObject object=new JSONObject(json);
        var values=new LinkedHashMap<String,String>();
        for(var stat:UnlimitedProgression.Stat.values()) {
            Object value=object.get(stat.name());
            if(!(value instanceof String))throw new IllegalArgumentException("Saved stats must be exact decimal strings");
            values.put(stat.name(),(String)value);
        }
        return UnlimitedProgression.restore(values);
    }
    private static void requireTransaction(Connection connection) throws SQLException {
        if(connection.getAutoCommit())throw new SQLException("Progression operation requires a caller-owned transaction");
    }
}
