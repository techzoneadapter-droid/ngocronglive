package nro.progression;

import java.math.BigInteger;
import java.util.Objects;
import nro.models.player.NPoint;

/** Shared exact HP injury primitive. Encounter adapters still own eligibility, effects and rewards. */
public final class ExactCombat {
    public record Rules(boolean piercing,int reductionPercent,BigInteger maximumHit) {
        public Rules {
            if(reductionPercent<0 || reductionPercent>100 || (maximumHit!=null && maximumHit.signum()<0))
                throw new IllegalArgumentException("Invalid damage rules");
        }
    }
    public record Result(BigInteger applied,BigInteger remaining,boolean killed) {}
    private ExactCombat() {}

    /** Serializes simultaneous hits with recovery/respawn on the same target. */
    public static Result hit(NPoint target,BigInteger incoming,Rules rules) {
        Objects.requireNonNull(target);Objects.requireNonNull(incoming);Objects.requireNonNull(rules);
        if(incoming.signum()<0)throw new IllegalArgumentException("Negative incoming damage");
        synchronized(target) {
            BigInteger damage=incoming;
            if(damage.signum()>0) {
                if(!rules.piercing())damage=target.subDameInjureWithDeff(damage);
                damage=damage.multiply(BigInteger.valueOf(100-rules.reductionPercent())).divide(BigInteger.valueOf(100));
                if(rules.reductionPercent()<100)damage=damage.max(BigInteger.ONE);
                if(rules.maximumHit()!=null)damage=damage.min(rules.maximumHit());
            }
            var result=target.damageExact(damage);
            return new Result(result.applied(),target.exactHp(),result.killed());
        }
    }
}
