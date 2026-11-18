package nro.progression;

import java.math.BigInteger;
import java.sql.*;
import java.lang.reflect.Field;
import java.util.Map;
import nro.models.boss.*;
import nro.models.player.*;
import nro.models.clan.Buff;
import nro.card.CollectionBook;
import nro.server.Manager;

/** Executes the modified base Boss.respawn/die methods against a disposable MariaDB. */
public final class BossRuntimeDatabaseTest {
 public static final class TestBoss extends Boss {
  public TestBoss(){super(-12345,null);}
  @Override public void init(){nPoint.hpg=1000000;nPoint.mpg=1000;nPoint.dameg=100000;nPoint.defg=100;nPoint.calPoint();}
  @Override public void rewards(Player player){}
  @Override public void initTalk(){}
  @Override public void idle(){}
  @Override public void checkPlayerDie(Player player){}
  @Override protected boolean useSpecialSkill(){return false;}
  @Override public Buff getBuff(){return Buff.NONE;}
  @Override public CollectionBook getCollectionBook(){return null;}
 }
 public static void main(String[] args)throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new nro.models.item.ItemOptionTemplate(i,"fixture",0));
  Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var memory=(sun.misc.Unsafe)field.get(null);
  var boss=(TestBoss)memory.allocateInstance(TestBoss.class);boss.id=-12345;NPointExactStatsTest.initialize(boss);
  BossProgressionRuntime.install(()->DriverManager.getConnection(args[0],"root",""),Map.of(TestBoss.class.getName(),new BossProgression.Profile(500,200,100)));
  boss.respawn();
  if(args[1].equals("reopen")) {
   check(boss.nPoint.exactHp().equals(BigInteger.valueOf(1050000)),"actual respawn retained grown HP after database restart");
   check(boss.nPoint.exactDamage().equals(BigInteger.valueOf(102000)),"actual respawn retained attack");
   System.out.println("PASS actual Boss.respawn after database restart: grown HP/attack restored");return;
  }
  check(boss.nPoint.exactHp().equals(BigInteger.valueOf(1000000)),"actual first respawn");
  boss.die(); // Scripted state transition while alive must not count as a kill.
  boss.respawn();check(boss.nPoint.exactHp().equals(BigInteger.valueOf(1000000)),"scripted die not grown");
  var hit=ExactCombat.hit(boss.nPoint,BigInteger.TEN.pow(50),new ExactCombat.Rules(true,0,null));
  check(hit.killed(),"lethal exact hit");
  boss.die();boss.die();
  boss.respawn();
  check(boss.nPoint.exactHp().equals(BigInteger.valueOf(1050000)),"actual respawn grew once");
  check(boss.nPoint.exactDamage().equals(BigInteger.valueOf(102000)),"actual attack grew separately");
  check(boss.nPoint.exactDefense().equals(BigInteger.valueOf(404)),"actual armor grew separately");
  boss.respawn();check(boss.nPoint.exactHp().equals(BigInteger.valueOf(1050000)),"respawn itself does not grow boss again");
  System.out.println("PASS actual Boss die/respawn: scripted transition ignored, duplicate death ignored, next-spawn HP/attack/armor growth, repeat respawn stable");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
