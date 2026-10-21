import nro.live.Balance;
public class BalanceProbe {
 public static void main(String[] args){
  if(Balance.fused(1000,500,30,Integer.MAX_VALUE)!=1950||Balance.fused(1000,500,50,Integer.MAX_VALUE)!=2250||Balance.fused(1000,500,70,Integer.MAX_VALUE)!=2550)throw new AssertionError("Fusion must increase the full combined total");
  if(Balance.fused(Integer.MAX_VALUE,Integer.MAX_VALUE,70,Integer.MAX_VALUE)!=Integer.MAX_VALUE||Balance.fused(80,50,70,100)!=100)throw new AssertionError("Bounded combat stats");
  if(Balance.cost(0,20000000,30000)!=609029700000L)throw new AssertionError("Large HP cost overflow");
  if(Balance.cost(3,20,2)!=5100000L)throw new AssertionError("Armor batch cost");
  try{Balance.cost(4,30,1);throw new AssertionError("Critical cost overflow was accepted");}catch(ArithmeticException expected){}
  if(Balance.extend(1000,4000,60)!=64000||Balance.extend(1000,0,60)!=61000)throw new AssertionError("Same-type duration does not extend");
  System.out.println("PASS: full-total fusion 30/50/70, stat caps, large batch point costs and timer extension");
 }
}
