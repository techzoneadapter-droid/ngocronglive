package nro.progression;

import java.math.BigInteger;
import nro.models.boss.Boss;
import nro.models.player.Player;

/** SkillService adapter for configured bosses with verified native injury behavior. */
public final class ExactSkillDamage {
    private ExactSkillDamage(){}
    public static BigInteger hit(Player attacker,Player target,boolean miss,int fixedDamage) {
        if(!(target instanceof Boss boss) || !boss.acceptsExactSkillDamage())return null;
        BigInteger incoming=miss ? BigInteger.ZERO : fixedDamage>=0 ? BigInteger.valueOf(fixedDamage)
                : attacker.nPoint.getExactDameAttack(false);
        return boss.injuredExact(attacker,incoming,false);
    }
    public static int legacy(BigInteger damage) {
        return damage.max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
    }
}
