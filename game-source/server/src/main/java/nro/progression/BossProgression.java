package nro.progression;

import java.math.BigInteger;
import java.util.Map;

/** Per-boss growth state. A runtime adapter must persist it at a confirmed boss death. */
public record BossProgression(BigInteger defeated, BigInteger hp, BigInteger attack, BigInteger defense) {
    public record Profile(int hpBasisPoints, int attackBasisPoints, int defenseBasisPoints) {
        public Profile {
            if(hpBasisPoints<0 || attackBasisPoints<0 || defenseBasisPoints<0)
                throw new IllegalArgumentException("Negative boss growth rate");
        }
    }
    public BossProgression {
        if(defeated.signum()<0 || hp.signum()<=0 || attack.signum()<0 || defense.signum()<0)
            throw new IllegalArgumentException("Invalid boss progression");
    }
    /** The live boss keeps its current stats; this result belongs to its next spawn. */
    public BossProgression afterDefeat(Profile profile) {
        return new BossProgression(defeated.add(BigInteger.ONE),
            grow(hp,profile.hpBasisPoints()),grow(attack,profile.attackBasisPoints()),grow(defense,profile.defenseBasisPoints()));
    }
    private static BigInteger grow(BigInteger value,int basisPoints) {
        if(basisPoints==0 || value.signum()==0)return value;
        BigInteger increase=value.multiply(BigInteger.valueOf(basisPoints)).add(BigInteger.valueOf(9999))
            .divide(BigInteger.valueOf(10000));
        return value.add(increase);
    }
    public Map<String,String> snapshot() {
        return Map.of("defeated",defeated.toString(),"hp",hp.toString(),"attack",attack.toString(),"defense",defense.toString());
    }
    public static BossProgression restore(Map<String,String> saved) {
        return new BossProgression(read(saved,"defeated"),read(saved,"hp"),read(saved,"attack"),read(saved,"defense"));
    }
    private static BigInteger read(Map<String,String> saved,String field) {
        String value=saved.get(field);
        if(value==null || !value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException("Invalid boss field: "+field);
        return new BigInteger(value);
    }
}
