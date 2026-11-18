package nro.progression;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import static nro.progression.UnlimitedProgression.Stat.*;

public final class UnlimitedProgressionTest {
    public static void main(String[] args) {
        BigInteger huge=BigInteger.TEN.pow(100);
        var state=new UnlimitedProgression(Map.of(POWER,huge,POTENTIAL,huge,HP,BigInteger.valueOf(100),KI,BigInteger.valueOf(100),ATTACK,BigInteger.ONE));
        var hp=state.allocate(HP,huge);
        check(hp.points().compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0,"point count beyond long");
        check(state.cost(HP,hp.points()).compareTo(huge)<=0
            && state.cost(HP,hp.points().add(BigInteger.ONE)).compareTo(huge)>0,"maximum affordable allocation");
        check(hp.state().get(POTENTIAL).add(hp.cost()).equals(huge),"exact conservation");
        check(state.get(HP).equals(BigInteger.valueOf(100)),"immutable input");
        check(UnlimitedProgression.restore(hp.state().snapshot()).snapshot().equals(hp.state().snapshot()),"lossless decimal restore");
        check(state.reward(huge,huge).get(POWER).equals(huge.multiply(BigInteger.TWO)),"unbounded reward");
        var purchased=state.purchase(HP,BigInteger.valueOf(100));
        check(purchased.points().intValueExact()==100 && purchased.state().get(HP).intValueExact()==2100,"exact legacy point count purchase");
        check(purchased.state().get(POTENTIAL).add(purchased.cost()).equals(huge),"purchase balance conservation");
        Random random=new Random(1811);
        for(var stat:new UnlimitedProgression.Stat[]{HP,KI,ATTACK,DEFENSE}) {
            for(int test=0;test<200;test++) {
                long budget=random.nextInt(1000000), base=random.nextInt(1000);
                var small=new UnlimitedProgression(Map.of(stat,BigInteger.valueOf(base),POTENTIAL,BigInteger.valueOf(budget)));
                long remaining=budget, count=0, current=base;
                while(true) {
                    long next=switch(stat){case HP,KI -> current+1000;case ATTACK -> current*100;case DEFENSE -> (current+5)*100000;default -> throw new AssertionError();};
                    if(next>remaining)break;
                    remaining-=next;count++;current+=(stat==HP||stat==KI)?20:1;
                }
                var allocation=small.allocate(stat,BigInteger.valueOf(budget));
                check(allocation.points().longValueExact()==count,"independent per-point oracle");
                check(allocation.state().get(POTENTIAL).longValueExact()==remaining,"remainder oracle");
                check(allocation.state().get(stat).longValueExact()==current,"stat oracle");
            }
        }
        try{state.allocate(POWER,BigInteger.ONE);throw new AssertionError("power purchasable");}catch(IllegalArgumentException expected){}
        try{state.allocate(HP,huge.add(BigInteger.ONE));throw new AssertionError("overspend");}catch(IllegalArgumentException expected){}
        System.out.println("PASS 10^100 progression, counts above long, decimal roundtrip, 800 independent allocation comparisons, overspend rejection");
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
