package nro.progression;
import java.math.BigInteger;
import java.sql.*;
import static nro.progression.UnlimitedProgression.Stat.*;

/** Run only against the disposable database created by TEST-PROGRESSION-DATABASE.ps1. */
public final class ProgressionRepositoryTest {
 private static final String LEGACY="[0,100,200,5,11,100,100,40,900000000000,100,30,120000000000,500]";
 private static final String BAG="[{\"temp_id\":457,\"quantity\":123,\"option\":[],\"create_time\":123456}]";
 public static void main(String[] args)throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  try(Connection c=DriverManager.getConnection(args[0],"root","")) {
   if(args[1].equals("reopen")) {
    c.setAutoCommit(false);
    var saved=ProgressionRepository.loadOrMigrate(c,1);
    check(saved.state().get(POWER).equals(BigInteger.TEN.pow(100).add(BigInteger.valueOf(120000000000L))),"restart persistence");
    check(saved.revision()==2,"restart revision");
    unchanged(c);c.rollback();
    var boss=BossProgressionRepository.loadOrCreate(c,"fixture-boss",bossSeed());
    check(boss.defeated().equals(BigInteger.ONE),"boss progression survived restart");
    check(!BossProgressionRepository.recordDefeat(c,"fixture-boss","spawn-one",bossProfile()).advanced(),"replay after restart");c.commit();
    var session=ProgressionCommands.open(c,3,77);
    check(session.revision()==3,"reconnected command revision");
    check(ProgressionCommands.reward(c,3,77,"reward-one",BigInteger.TEN.pow(60),BigInteger.TEN.pow(60)).replay(),"reward retry after restart");
    check(ProgressionCommands.allocate(c,3,77,"allocate-one",0,HP,BigInteger.valueOf(1000000)).replay(),"allocation retry after restart");c.commit();
    System.out.println("PASS database restart: exact 10^100 state, same revision, original inventory/stats preserved");return;
   }
   try(var s=c.createStatement()) {
    s.executeUpdate("CREATE TABLE player(id BIGINT PRIMARY KEY,account_id BIGINT NOT NULL DEFAULT 77,data_point LONGTEXT NOT NULL,items_bag LONGTEXT NOT NULL,items_body LONGTEXT NOT NULL)");
   }
   try(var s=c.prepareStatement("INSERT INTO player(id,data_point,items_bag,items_body) VALUES(?,?,?,?)")) {
    s.setLong(1,1);s.setString(2,LEGACY);s.setString(3,BAG);s.setString(4,"[]");s.executeUpdate();
    s.setLong(1,2);s.setString(2,LEGACY.replace("120000000000","-1"));s.executeUpdate();
    s.setLong(1,3);s.setString(2,LEGACY);s.executeUpdate();
   }
   ProgressionRepository.initialize(c);
   BossProgressionRepository.initialize(c);
   ProgressionCommands.initialize(c);
   try{ProgressionRepository.loadOrMigrate(c,1);throw new AssertionError("auto-commit accepted");}catch(SQLException expected){}
   c.setAutoCommit(false);
   var original=ProgressionRepository.loadOrMigrate(c,1);
   check(original.revision()==0 && original.state().get(HP).intValueExact()==500
     && original.state().get(KI).intValueExact()==200 && original.state().get(ATTACK).intValueExact()==30
     && original.state().get(DEFENSE).intValueExact()==40,"legacy field mapping");
   c.commit();
   var changed=original.state().reward(BigInteger.TEN.pow(100),BigInteger.TEN.pow(100));
   var first=ProgressionRepository.save(c,original,changed);c.commit();
   try{ProgressionRepository.save(c,original,original.state());throw new AssertionError("stale update accepted");}
   catch(SQLException expected){check("40001".equals(expected.getSQLState()),"stale conflict type");c.rollback();}
   var allocation=changed.allocate(HP,BigInteger.TEN.pow(50));
   ProgressionRepository.save(c,first,allocation.state());c.rollback();
   var restored=ProgressionRepository.loadOrMigrate(c,1);
   check(restored.state().snapshot().equals(changed.snapshot()) && restored.revision()==1,"rollback preserved exact state");
   ProgressionRepository.save(c,restored,allocation.state());c.commit();
   var reloaded=ProgressionRepository.loadOrMigrate(c,1);
   check(reloaded.revision()==2 && reloaded.state().snapshot().equals(allocation.state().snapshot()),"allocation persisted");
   unchanged(c);c.commit();
   try{ProgressionRepository.loadOrMigrate(c,2);throw new AssertionError("negative old stats accepted");}catch(SQLException expected){c.rollback();}
   ProgressionRepository.loadOrMigrate(c,3);c.rollback();
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM nero_progression_state WHERE player_id IN (2,3)")) {r.next();check(r.getInt(1)==0,"failed migration wrote rows");}c.commit();
   try(var s=c.createStatement()){s.executeUpdate("UPDATE nero_progression_state SET format_version=99 WHERE player_id=1");}c.commit();
   try{ProgressionRepository.loadOrMigrate(c,1);throw new AssertionError("future format reset");}catch(SQLException expected){c.rollback();}
   try(var s=c.createStatement()){s.executeUpdate("UPDATE nero_progression_state SET format_version=1 WHERE player_id=1");}c.commit();
   BossProgressionRepository.loadOrCreate(c,"fixture-boss",bossSeed());c.commit();
   var defeat=BossProgressionRepository.recordDefeat(c,"fixture-boss","spawn-one",bossProfile());
   check(defeat.advanced() && defeat.nextSpawn().defeated().equals(BigInteger.ONE),"first defeat");c.commit();
   check(!BossProgressionRepository.recordDefeat(c,"fixture-boss","spawn-one",bossProfile()).advanced(),"duplicate defeat");c.commit();
   BossProgressionRepository.recordDefeat(c,"fixture-boss","spawn-two",bossProfile());c.rollback();
   var boss=BossProgressionRepository.loadOrCreate(c,"fixture-boss",bossSeed());
   check(boss.defeated().equals(BigInteger.ONE),"rolled back defeat");c.commit();
   BossProgressionRepository.loadOrCreate(c,"concurrent-boss",bossSeed());c.commit();
   var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
   var start=new java.util.concurrent.CountDownLatch(1);
   try {
    java.util.concurrent.Callable<Boolean> duplicate=()->{
     start.await();
     try(Connection separate=DriverManager.getConnection(args[0],"root","")) {
      separate.setAutoCommit(false);
      boolean advanced=BossProgressionRepository.recordDefeat(separate,"concurrent-boss","same-spawn",bossProfile()).advanced();
      separate.commit();return advanced;
     }
    };
    var one=executor.submit(duplicate);var two=executor.submit(duplicate);start.countDown();
    int applied=(one.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0)+(two.get(15,java.util.concurrent.TimeUnit.SECONDS)?1:0);
    check(applied==1,"concurrent duplicate advanced twice");
   } finally {executor.shutdownNow();}
   check(BossProgressionRepository.loadOrCreate(c,"concurrent-boss",bossSeed()).defeated().equals(BigInteger.ONE),"concurrent persisted count");c.commit();
   commandTests(c,args[0]);
   System.out.println("PASS real MariaDB: migrate, huge reward, exact allocation, stale-write rejection, rollback, future-format refusal, untouched inventory, atomic and duplicate-free boss advancement");
  }
 }
 private static void commandTests(Connection c,String jdbc)throws Exception {
  try{ProgressionCommands.open(c,3,999);throw new AssertionError("other account accessed character");}
  catch(SQLException expected){check("28000".equals(expected.getSQLState()),"ownership failure");c.rollback();}
  var session=ProgressionCommands.open(c,3,77);check(session.revision()==0,"first open");c.commit();
  var allocated=ProgressionCommands.allocate(c,3,77,"allocate-one",0,HP,BigInteger.valueOf(1000000));
  check(!allocated.replay() && allocated.points().signum()>0 && allocated.saved().revision()==1,"allocated points transaction");c.commit();
  var duplicate=ProgressionCommands.allocate(c,3,77,"allocate-one",0,HP,BigInteger.valueOf(1000000));
  check(duplicate.replay() && duplicate.saved().revision()==1 && duplicate.cost().equals(allocated.cost()),"retry not charged twice");c.commit();
  try{ProgressionCommands.allocate(c,3,77,"allocate-one",0,HP,BigInteger.ONE);throw new AssertionError("request key reused");}
  catch(IllegalArgumentException expected){c.rollback();}
  try{ProgressionCommands.allocate(c,3,77,"stale",0,HP,BigInteger.ONE);throw new AssertionError("stale client accepted");}
  catch(SQLException expected){check("40001".equals(expected.getSQLState()),"revision conflict");c.rollback();}
  ProgressionCommands.allocate(c,3,77,"rollback-request",1,ATTACK,BigInteger.valueOf(100000));c.rollback();
  check(ProgressionCommands.open(c,3,77).revision()==1,"rolled back operation");c.commit();
  var reward=ProgressionCommands.reward(c,3,77,"reward-one",BigInteger.TEN.pow(60),BigInteger.TEN.pow(60));
  check(!reward.replay() && reward.saved().revision()==2,"internal reward persisted");c.commit();
  check(ProgressionCommands.reward(c,3,77,"reward-one",BigInteger.TEN.pow(60),BigInteger.TEN.pow(60)).replay(),"reward duplicate");c.commit();
  try{ProgressionCommands.allocate(c,3,77,"overspend",2,HP,BigInteger.TEN.pow(80));throw new AssertionError("overspend accepted");}
  catch(IllegalArgumentException expected){c.rollback();}
  var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
  var start=new java.util.concurrent.CountDownLatch(1);
  try {
   java.util.concurrent.Callable<Boolean> request=()->{
    start.await();
    try(Connection separate=DriverManager.getConnection(jdbc,"root","")) {
     separate.setAutoCommit(false);
     boolean replay=ProgressionCommands.allocate(separate,3,77,"simultaneous-allocation",2,ATTACK,BigInteger.valueOf(1000000)).replay();
     separate.commit();return replay;
    }
   };
   var first=executor.submit(request);var second=executor.submit(request);start.countDown();
   check(first.get(15,java.util.concurrent.TimeUnit.SECONDS)!=second.get(15,java.util.concurrent.TimeUnit.SECONDS),"simultaneous request not deduplicated");
  }finally{executor.shutdownNow();}
  check(ProgressionCommands.open(c,3,77).revision()==3,"single allocation revision");c.commit();
  System.out.println("PASS authenticated open/reconnect, exact budget allocation, concurrent retry once, payload mismatch, stale revision, reward deduplication, no overspend");
 }
 private static BossProgression bossSeed(){return new BossProgression(BigInteger.ZERO,BigInteger.TEN.pow(100),BigInteger.TEN.pow(80),BigInteger.TEN.pow(60));}
 private static BossProgression.Profile bossProfile(){return new BossProgression.Profile(100,50,25);}
 private static void unchanged(Connection c)throws Exception {
  try(var s=c.createStatement();var r=s.executeQuery("SELECT data_point,items_bag,items_body FROM player WHERE id=1")) {
   r.next();check(r.getString(1).equals(LEGACY)&&r.getString(2).equals(BAG)&&r.getString(3).equals("[]"),"legacy inventory/stat mutation");
  }
 }
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
