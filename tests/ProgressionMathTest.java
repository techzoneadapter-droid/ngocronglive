package nro.models.player;

import java.math.BigInteger;
import java.util.Random;

/** Compare the production cost helper with an independent exact-integer oracle. */
public final class ProgressionMathTest {
    public static void main(String[] args) {
        Random random = new Random(18112026);
        for (int trial=0; trial<20000; trial++) {
            int count=1+random.nextInt(32767), step=random.nextInt(21), multiplier=1+random.nextInt(100000);
            long first=trial%2==0 ? random.nextInt(Integer.MAX_VALUE) : random.nextLong() & Long.MAX_VALUE;
            BigInteger n=BigInteger.valueOf(count);
            BigInteger exact=n.multiply(BigInteger.valueOf(first).multiply(BigInteger.TWO)
                .add(n.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(step))))
                .divide(BigInteger.TWO).multiply(BigInteger.valueOf(multiplier));
            long expected=exact.compareTo(BigInteger.valueOf(Long.MAX_VALUE))>0 ? -1 : exact.longValueExact();
            check(ProgressionMath.arithmeticCost(count,first,step,multiplier)==expected,"cost oracle");
        }
        check(ProgressionMath.arithmeticCost(1,Long.MAX_VALUE,0,1)==Long.MAX_VALUE,"single large cost");
        check(ProgressionMath.arithmeticCost(0,1000,20,1)==-1,"invalid count");
        check(ProgressionMath.arithmeticCost(1,-1,20,1)==-1,"negative base");
        check(ProgressionMath.addReward(Long.MAX_VALUE-2,10)==Long.MAX_VALUE,"reward overflow");
        check(ProgressionMath.addReward(100,-1)==100,"negative reward");
        check(ProgressionMath.criticalCost(0)==50000000L,"first critical cost");
        check(ProgressionMath.criticalCost(100)==-1,"critical overflow refused");
        System.out.println("PASS 20000 exact progression cost comparisons and overflow boundaries");
    }
    private static void check(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
}
