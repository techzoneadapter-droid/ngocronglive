package nro.progression;

import java.math.BigInteger;
import java.sql.*;

/** Current vitals remain decimal strings; saved on the original player-save connection. */
public final class ExactVitalsRepository {
    public record Vitals(BigInteger hp,BigInteger ki){}
    private ExactVitalsRepository(){}
    public static void initialize(Connection c)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Vitals DDL requires standalone connection");
        try(var s=c.createStatement()){s.executeUpdate("CREATE TABLE IF NOT EXISTS nero_exact_vitals (player_id BIGINT PRIMARY KEY,hp TEXT NOT NULL,ki TEXT NOT NULL) ENGINE=InnoDB");}
    }
    public static Vitals load(Connection c,long player)throws SQLException {
        try(var q=c.prepareStatement("SELECT hp,ki FROM nero_exact_vitals WHERE player_id=?")) {
            q.setLong(1,player);try(var r=q.executeQuery()) {
                if(!r.next())return null;
                try{return new Vitals(number(r.getString(1)),number(r.getString(2)));}
                catch(IllegalArgumentException error){throw new SQLException("Invalid exact vitals; refusing fallback",error);}
            }
        }
    }
    public static void save(Connection c,long player,BigInteger hp,BigInteger ki)throws SQLException {
        if(player<=0 || hp.signum()<0 || ki.signum()<0)throw new IllegalArgumentException("Invalid exact vitals");
        try(var q=c.prepareStatement("INSERT INTO nero_exact_vitals(player_id,hp,ki) VALUES(?,?,?) ON DUPLICATE KEY UPDATE hp=VALUES(hp),ki=VALUES(ki)")) {
            q.setLong(1,player);q.setString(2,hp.toString());q.setString(3,ki.toString());q.executeUpdate();
        }
    }
    private static BigInteger number(String value){if(value==null || !value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Invalid vitals decimal");return new BigInteger(value);}
}
