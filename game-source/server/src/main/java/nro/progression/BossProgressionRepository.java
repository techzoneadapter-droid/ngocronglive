package nro.progression;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import org.json.JSONObject;

/** Durable next-spawn strength. Does not distribute rewards or mutate a live boss. */
public final class BossProgressionRepository {
    public record Defeat(BossProgression nextSpawn,boolean advanced) {}
    private BossProgressionRepository() {}

    public static void initialize(Connection c)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Initialize boss schema outside transaction");
        try(var s=c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_boss_progression (
                    boss_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
                    format_version INT NOT NULL,
                    stats LONGTEXT NOT NULL
                ) ENGINE=InnoDB
                """);
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_boss_progression_defeats (
                    boss_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    spawn_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY(boss_key,spawn_key)
                ) ENGINE=InnoDB
                """);
        }
    }
    public static BossProgression loadOrCreate(Connection c,String bossKey,BossProgression original)throws SQLException {
        transaction(c);key(bossKey);
        try(var s=c.prepareStatement("INSERT INTO nero_boss_progression(boss_key,format_version,stats) VALUES(?,1,?) ON DUPLICATE KEY UPDATE boss_key=VALUES(boss_key)")) {
            s.setString(1,bossKey);s.setString(2,encode(original));s.executeUpdate();
        }
        return lock(c,bossKey);
    }
    public static Defeat recordDefeat(Connection c,String bossKey,String spawnKey,BossProgression.Profile profile)throws SQLException {
        transaction(c);key(bossKey);key(spawnKey);
        BossProgression current=lock(c,bossKey);
        try(var q=c.prepareStatement("SELECT 1 FROM nero_boss_progression_defeats WHERE boss_key=? AND spawn_key=?")) {
            q.setString(1,bossKey);q.setString(2,spawnKey);
            try(var r=q.executeQuery()){if(r.next())return new Defeat(current,false);}
        }
        BossProgression next=current.afterDefeat(profile);
        try(var q=c.prepareStatement("INSERT INTO nero_boss_progression_defeats(boss_key,spawn_key) VALUES(?,?)")) {
            q.setString(1,bossKey);q.setString(2,spawnKey);q.executeUpdate();
        }
        try(var q=c.prepareStatement("UPDATE nero_boss_progression SET stats=? WHERE boss_key=? AND format_version=1")) {
            q.setString(1,encode(next));q.setString(2,bossKey);
            if(q.executeUpdate()!=1)throw new SQLException("Boss progression update failed");
        }
        return new Defeat(next,true);
    }
    private static BossProgression lock(Connection c,String bossKey)throws SQLException {
        try(var q=c.prepareStatement("SELECT format_version,stats FROM nero_boss_progression WHERE boss_key=? FOR UPDATE")) {
            q.setString(1,bossKey);
            try(var r=q.executeQuery()) {
                if(!r.next())throw new SQLException("Boss progression not initialized");
                if(r.getInt(1)!=1)throw new SQLException("Unknown boss progression format");
                try {
                    JSONObject json=new JSONObject(r.getString(2));var values=new LinkedHashMap<String,String>();
                    for(String field:new String[]{"defeated","hp","attack","defense"}) {
                        Object value=json.get(field);
                        if(!(value instanceof String))throw new IllegalArgumentException("Expected exact decimal string");
                        values.put(field,(String)value);
                    }
                    return BossProgression.restore(values);
                }catch(RuntimeException error){throw new SQLException("Invalid boss progression; refusing reset",error);}
            }
        }
    }
    private static String encode(BossProgression state){return new JSONObject(state.snapshot()).toString();}
    private static void transaction(Connection c)throws SQLException {if(c.getAutoCommit())throw new SQLException("Boss progression requires caller-owned transaction");}
    private static void key(String key){if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException("Invalid boss or spawn key");}
}
