package nro.progression;

import java.math.BigInteger;
import java.sql.*;
import java.lang.reflect.Field;
import nro.models.player.*;
import nro.models.item.Item;

/** Real NPoint injury -> JDBC death advancement -> next-spawn exact strength. */
public final class BossEncounterDatabaseTest {
 public static void main(String[] args)throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  try(Connection c=DriverManager.getConnection(args[0],"root","")) {
   c.setAutoCommit(false);
   BigInteger initial=BigInteger.TEN.pow(100);
   var seed=new BossProgression(BigInteger.ZERO,initial,initial.divide(BigInteger.TEN),BigInteger.ONE);
   var profile=new BossProgression.Profile(500,200,100);
   var spawn=BossEncounter.spawn(c,"encounter-fixture",seed);c.commit();
   if(args[1].equals("reopen")) {
    check(spawn.strength().defeated().equals(BigInteger.ONE),"defeat persisted through restart");
    check(spawn.strength().hp().equals(initial.multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100))),"grown HP persisted through restart");
    System.out.println("PASS boss encounter restart: next-spawn growth retained");return;
   }
   Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
   var memory=(sun.misc.Unsafe)f.get(null);
   for(int i=0;i<251;i++)nro.server.Manager.ITEM_OPTION_TEMPLATES.add(new nro.models.item.ItemOptionTemplate(i,"fixture",0));
   var player=(NPointExactStatsTest.Subject)memory.allocateInstance(NPointExactStatsTest.Subject.class);
   NPointExactStatsTest.initialize(player);
   var point=player.nPoint;
   var calculate=NPoint.class.getDeclaredMethod("setPointWhenWearClothes");calculate.setAccessible(true);
   Runnable recalculate=()->{try{calculate.invoke(point);}catch(Exception error){throw new RuntimeException(error);}};
   BossEncounter.attach(point,spawn,recalculate);
   check(point.exactHp().equals(initial)&&point.exactDamage().equals(seed.attack()),"attached exact initial strength");
   var hit=ExactCombat.hit(point,initial.multiply(BigInteger.TWO),new ExactCombat.Rules(true,0,null));
   var result=BossEncounter.defeated(c,spawn,hit,profile);
   check(result.advanced()&&result.nextSpawn().defeated().equals(BigInteger.ONE),"death advanced next spawn");c.commit();
   check(point.exactHp().signum()==0&&point.exactHpMax().equals(initial),"current dead boss did not gain HP");
   check(!BossEncounter.defeated(c,spawn,hit,profile).advanced(),"same lethal report not advanced twice");c.commit();
   var next=BossEncounter.spawn(c,"encounter-fixture",seed);c.commit();
   check(!next.spawnKey().equals(spawn.spawnKey()),"new lifecycle identity");
   BossEncounter.attach(point,next,recalculate);
   check(point.exactHp().equals(initial.multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100))),"next spawn actually has 5 percent more HP");
   check(point.exactDamage().equals(seed.attack().multiply(BigInteger.valueOf(102)).divide(BigInteger.valueOf(100))),"next spawn actually has 2 percent more attack");
   try{BossEncounter.defeated(c,next,new ExactCombat.Result(BigInteger.ONE,point.exactHp().subtract(BigInteger.ONE),false),profile);throw new AssertionError("nonlethal growth");}
   catch(IllegalArgumentException expected){c.rollback();}
   System.out.println("PASS real exact boss encounter: spawn, attach, lethal hit, durable advancement, replay refusal, next spawn HP/attack growth");
  }
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
