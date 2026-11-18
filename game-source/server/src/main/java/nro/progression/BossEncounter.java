package nro.progression;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import nro.models.player.NPoint;

/** Encounter adapter between exact damage and durable next-spawn progression. */
public final class BossEncounter {
    public record Spawn(String bossKey,String spawnKey,BossProgression strength) {}
    private BossEncounter() {}

    /** Commit before attaching this spawn to a live boss. A reconnect never resets strength. */
    public static Spawn spawn(Connection connection,String bossKey,BossProgression seed)throws SQLException {
        BossProgression current=BossProgressionRepository.loadOrCreate(connection,bossKey,seed);
        return new Spawn(bossKey,UUID.randomUUID().toString(),current);
    }

    /** For already-calculated boss base stats; recalculate before enabling exact current HP. */
    public static void attach(NPoint point,Spawn spawn,Runnable recalculate) {
        var state=spawn.strength();
        point.bindUnlimitedBase(new UnlimitedProgression(java.util.Map.of(
            UnlimitedProgression.Stat.HP,state.hp(),UnlimitedProgression.Stat.ATTACK,state.attack(),
            UnlimitedProgression.Stat.DEFENSE,state.defense(),UnlimitedProgression.Stat.KI,
            java.math.BigInteger.valueOf(Math.max(0,point.mpg)))));
        recalculate.run();
        point.setExactVitals(point.exactHpMax(),point.exactMpMax());
    }

    /** Call after a confirmed lethal result, inside the caller-owned reward/death transaction. */
    public static BossProgressionRepository.Defeat defeated(Connection connection,Spawn spawn,
            ExactCombat.Result hit,BossProgression.Profile profile)throws SQLException {
        if(!hit.killed() || hit.remaining().signum()!=0 || hit.applied().signum()<=0)
            throw new IllegalArgumentException("A lethal exact-combat result is required");
        return BossProgressionRepository.recordDefeat(connection,spawn.bossKey(),spawn.spawnKey(),profile);
    }
}
