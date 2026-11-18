package nro.progression;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import org.json.JSONObject;

/** Transactional progression operations. Caller commits before updating its live player state. */
public final class ProgressionCommands {
    public record Result(ProgressionRepository.Saved saved, BigInteger points, BigInteger cost, boolean replay) {}
    private ProgressionCommands() {}

    public static void initialize(Connection c)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Initialize request schema outside a transaction");
        try(var s=c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_progression_requests (
                    player_id BIGINT NOT NULL,
                    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    payload LONGTEXT NOT NULL,
                    result LONGTEXT NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY(player_id,request_key)
                ) ENGINE=InnoDB
                """);
        }
    }

    /** Suitable for login/reconnect: only the authenticated account can load this character. */
    public static ProgressionRepository.Saved open(Connection c,long playerId,long accountId)throws SQLException {
        own(c,playerId,accountId);
        return ProgressionRepository.loadOrMigrate(c,playerId);
    }

    public static Result allocate(Connection c,long playerId,long accountId,String requestKey,
            long expectedRevision,UnlimitedProgression.Stat stat,BigInteger budget)throws SQLException {
        requestKey(requestKey);
        if(stat==null || budget==null || budget.signum()<0)throw new IllegalArgumentException("Invalid allocation");
        var saved=open(c,playerId,accountId);
        var payload=new JSONObject().put("kind","ALLOCATE").put("stat",stat.name())
                .put("budget",budget.toString()).put("revision",expectedRevision);
        Result replay=replay(c,saved,requestKey,payload);
        if(replay!=null)return replay;
        if(saved.revision()!=expectedRevision)throw new SQLException("Progression changed; refresh before allocating","40001");
        var allocation=saved.state().allocate(stat,budget);
        // Keep a zero-point request idempotent too, but do not bump the state revision.
        var next=allocation.points().signum()==0 ? saved : ProgressionRepository.save(c,saved,allocation.state());
        record(c,next,requestKey,payload,allocation.points(),allocation.cost());
        return new Result(next,allocation.points(),allocation.cost(),false);
    }

    /** Internal reward path only. Never accept client-supplied reward amounts. */
    public static Result reward(Connection c,long playerId,long accountId,String eventKey,
            BigInteger power,BigInteger potential)throws SQLException {
        requestKey(eventKey);
        if(power==null || potential==null || power.signum()<0 || potential.signum()<0)
            throw new IllegalArgumentException("Invalid reward");
        var saved=open(c,playerId,accountId);
        var payload=new JSONObject().put("kind","REWARD").put("power",power.toString()).put("potential",potential.toString());
        Result replay=replay(c,saved,eventKey,payload);
        if(replay!=null)return replay;
        var next=ProgressionRepository.save(c,saved,saved.state().reward(power,potential));
        record(c,next,eventKey,payload,BigInteger.ZERO,BigInteger.ZERO);
        return new Result(next,BigInteger.ZERO,BigInteger.ZERO,false);
    }

    private static Result replay(Connection c,ProgressionRepository.Saved current,String key,JSONObject payload)throws SQLException {
        try(var q=c.prepareStatement("SELECT payload,result FROM nero_progression_requests WHERE player_id=? AND request_key=?")) {
            q.setLong(1,current.playerId());q.setString(2,key);
            try(var rows=q.executeQuery()) {
                if(!rows.next())return null;
                JSONObject original=new JSONObject(rows.getString(1));
                if(!original.similar(payload))throw new IllegalArgumentException("Request key reused with different input");
                JSONObject result=new JSONObject(rows.getString(2));
                // Return fresh state, not an old snapshot that could roll the client's display back.
                return new Result(current,new BigInteger(result.getString("points")),new BigInteger(result.getString("cost")),true);
            }
        }
    }
    private static void record(Connection c,ProgressionRepository.Saved saved,String key,JSONObject payload,BigInteger points,BigInteger cost)throws SQLException {
        String result=new JSONObject().put("revision",saved.revision()).put("points",points.toString()).put("cost",cost.toString()).toString();
        try(var q=c.prepareStatement("INSERT INTO nero_progression_requests(player_id,request_key,payload,result) VALUES(?,?,?,?)")) {
            q.setLong(1,saved.playerId());q.setString(2,key);q.setString(3,payload.toString());q.setString(4,result);q.executeUpdate();
        }
    }
    private static void own(Connection c,long playerId,long accountId)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Command requires a caller-owned transaction");
        if(playerId<=0 || accountId<=0)throw new IllegalArgumentException("Invalid player or account");
        try(var q=c.prepareStatement("SELECT account_id FROM player WHERE id=? FOR UPDATE")) {
            q.setLong(1,playerId);
            try(var rows=q.executeQuery()) {
                if(!rows.next() || rows.getLong(1)!=accountId)throw new SQLException("Character is not owned by this account","28000");
            }
        }
    }
    private static void requestKey(String key) {
        if(key==null || !key.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException("Invalid request key");
    }
}
