package nro.progression;
import java.math.BigInteger;

public final class BossProgressionTest {
 public static void main(String[] args) {
  var initial=new BossProgression(BigInteger.ZERO,BigInteger.valueOf(1000),BigInteger.valueOf(100),BigInteger.valueOf(10));
  // Test rates only, not a gameplay balance configuration.
  var slow=new BossProgression.Profile(100,50,0);
  var fast=new BossProgression.Profile(500,200,100);
  var a=initial.afterDefeat(slow);var b=initial.afterDefeat(fast);
  check(a.hp().intValueExact()==1010 && a.attack().intValueExact()==101 && a.defense().intValueExact()==10,"separate stat rates");
  check(b.hp().compareTo(a.hp())>0 && b.attack().compareTo(a.attack())>0,"different boss rates");
  check(initial.hp().intValueExact()==1000 && initial.defeated().signum()==0,"current spawn immutable");
  var huge=new BossProgression(BigInteger.TEN.pow(30),BigInteger.TEN.pow(100),BigInteger.TEN.pow(80),BigInteger.TEN.pow(60));
  var grown=huge.afterDefeat(fast);
  check(grown.hp().equals(BigInteger.TEN.pow(98).multiply(BigInteger.valueOf(105))),"exact HP beyond long");
  check(grown.defeated().equals(huge.defeated().add(BigInteger.ONE)),"unbounded defeat count");
  check(BossProgression.restore(grown.snapshot()).equals(grown),"lossless snapshot");
  check(initial.afterDefeat(new BossProgression.Profile(0,0,0)).hp().equals(initial.hp()),"zero rates preserve scripted stats");
  try{new BossProgression.Profile(-1,0,0);throw new AssertionError("negative rate");}catch(IllegalArgumentException expected){}
  System.out.println("PASS per-boss/per-stat growth, immutable current spawn, 10^100 HP, decimal restore, zero/negative rates");
 }
 static void check(boolean test,String label){if(!test)throw new AssertionError(label);}
}
