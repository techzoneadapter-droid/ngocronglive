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
  int rewards;
  public TestBoss(){super(-12345,null);}
  @Override public void init(){nPoint.hpg=1000000;nPoint.mpg=1000;nPoint.dameg=100000;nPoint.defg=100;nPoint.calPoint();}
  @Override public void rewards(Player player){rewards++;}
  @Override protected void notifyPlayeKill(Player player){}
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
  BigInteger huge=BigInteger.TEN.pow(100);
  try(Connection c=DriverManager.getConnection(args[0],"root","")) {
   c.setAutoCommit(false);
   BossProgressionRepository.loadOrCreate(c,TestBoss.class.getName()+":-777",new BossProgression(BigInteger.ZERO,huge,huge,BigInteger.ONE));c.commit();
  }
  var large=(TestBoss)memory.allocateInstance(TestBoss.class);large.id=-777;NPointExactStatsTest.initialize(large);large.respawn();
  var attacker=(NPointExactStatsTest.Subject)memory.allocateInstance(NPointExactStatsTest.Subject.class);NPointExactStatsTest.initialize(attacker);
  attacker.nPoint.bindUnlimitedBase(new UnlimitedProgression(Map.of(UnlimitedProgression.Stat.ATTACK,huge.multiply(BigInteger.TEN),UnlimitedProgression.Stat.HP,huge,UnlimitedProgression.Stat.KI,huge)));
  attacker.nPoint.calPoint();
  attacker.playerIntrinsic=(nro.models.intrinsic.IntrinsicPlayer)memory.allocateInstance(nro.models.intrinsic.IntrinsicPlayer.class);
  attacker.playerIntrinsic.intrinsic=new nro.models.intrinsic.Intrinsic();
  attacker.playerSkill=new nro.models.skill.PlayerSkill(attacker);var skill=new nro.models.skill.Skill();skill.template=new nro.models.skill.SkillTemplate();skill.template.id=nro.models.skill.Skill.DRAGON;skill.damage=100;attacker.playerSkill.skillSelect=skill;
  check(large.acceptsExactSkillDamage(),"configured standard boss dispatcher active");
  check(ExactSkillDamage.hit(attacker,large,false,-1).equals(huge),"SkillService adapter applied 10^100 HP rather than int cap");
  check(large.rewards==1 && large.nPoint.exactHp().signum()==0,"exact dispatch killed and rewarded once");
  check(ExactSkillDamage.hit(attacker,large,false,-1).signum()==0&&large.rewards==1,"duplicate skill hit did not reward twice");
  large.respawn();check(large.nPoint.exactHp().equals(huge.multiply(BigInteger.valueOf(105)).divide(BigInteger.valueOf(100))),"skill kill grew next boss spawn");
  System.out.println("PASS actual Boss die/respawn: scripted transition ignored, duplicate death ignored, next-spawn HP/attack/armor growth, repeat respawn stable");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
