package nro.progression;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import nro.models.boss.Boss;

/** Optional game-loop integration. Not installed until exact combat/client adapters are ready. */
public final class BossProgressionRuntime {
    @FunctionalInterface public interface Connections { Connection open() throws SQLException; }
    private static volatile BossProgressionRuntime installed;
    private final Connections connections;
    private final Map<String,BossProgression.Profile> profiles;
    private BossProgressionRuntime(Connections connections,Map<String,BossProgression.Profile> profiles) {
        this.connections=Objects.requireNonNull(connections);this.profiles=Map.copyOf(profiles);
    }
    /** Explicit startup step, after schema initialization; never implicitly enables every boss. */
    public static void install(Connections connections,Map<String,BossProgression.Profile> profiles) {
        if(installed!=null)throw new IllegalStateException("Boss progression already installed");
        installed=new BossProgressionRuntime(connections,profiles);
    }
    public static Encounter forBoss(Boss boss) {
        var runtime=installed;
        if(runtime==null)return null;
        var profile=runtime.profiles.get(boss.getClass().getName());
        return profile==null ? null : new Encounter(runtime,boss,profile);
    }
    public static final class Encounter {
        private final BossProgressionRuntime runtime;
        private final Boss boss;
        private final BossProgression.Profile profile;
        private BossEncounter.Spawn spawn;
        private boolean deathRecorded;
        private Encounter(BossProgressionRuntime runtime,Boss boss,BossProgression.Profile profile) {
            this.runtime=runtime;this.boss=boss;this.profile=profile;
        }
        public void beforeRespawn() {
            if(spawn!=null && !deathRecorded && boss.nPoint.exactHp().signum()==0)
                died(); // Retry a failed death transaction before allowing the next spawn.
            boss.nPoint.clearExactStateForRespawn();
        }
        public void afterInit() {
            var seed=new BossProgression(BigInteger.ZERO,BigInteger.valueOf(boss.nPoint.hpg),
                    BigInteger.valueOf(boss.nPoint.dameg),BigInteger.valueOf(boss.nPoint.defg));
            BossEncounter.Spawn next;
            try(Connection c=runtime.connections.open()) {
                c.setAutoCommit(false);
                try {
                    next=BossEncounter.spawn(c,boss.getClass().getName()+":"+boss.id,seed);
                    c.commit();
                }catch(Exception error){c.rollback();throw error;}
            }catch(SQLException error){throw new IllegalStateException("Cannot load next boss strength",error);}
            BossEncounter.attach(boss.nPoint,next,boss.nPoint::calPoint);
            spawn=next;deathRecorded=false;
        }
        public void died() {
            if(spawn==null || deathRecorded)return;
            if(boss.nPoint.exactHp().signum()!=0)
                return; // Scripted departure or phase switch is not a confirmed kill.
            try(Connection c=runtime.connections.open()) {
                c.setAutoCommit(false);
                try {
                    BossProgressionRepository.recordDefeat(c,spawn.bossKey(),spawn.spawnKey(),profile);
                    c.commit();
                    deathRecorded=true;
                }catch(Exception error){c.rollback();throw error;}
            }catch(SQLException error){throw new IllegalStateException("Cannot persist boss growth",error);}
        }
    }
}
