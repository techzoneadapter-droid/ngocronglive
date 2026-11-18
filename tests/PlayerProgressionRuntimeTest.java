package nro.progression;
import java.sql.*;
import java.lang.reflect.Field;
import java.math.BigInteger;
import nro.models.player.*;
import nro.server.io.*;
import nro.server.Manager;
public final class PlayerProgressionRuntimeTest {
 public static class Probe extends NPointExactStatsTest.Subject {
  Session session;
  @Override public Session getSession(){return session;}
  @Override public void sendMessage(Message message){}
 }
 public static void main(String[] args)throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var memory=(sun.misc.Unsafe)f.get(null);
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new nro.models.item.ItemOptionTemplate(i,"fixture",0));
  var manager=(nro.server.ServerManager)memory.allocateInstance(nro.server.ServerManager.class);
  var instance=nro.server.ServerManager.class.getDeclaredField("instance");instance.setAccessible(true);instance.set(null,manager);
  var attributes=nro.server.ServerManager.class.getDeclaredField("attributeManager");attributes.setAccessible(true);attributes.set(manager,new nro.attr.AttributeManager());
  PlayerProgressionRuntime.install(()->DriverManager.getConnection(args[0],"root",""));
  if(args[1].equals("seed"))try(Connection c=DriverManager.getConnection(args[0],"root","" );var q=c.createStatement()) {
   q.executeUpdate("INSERT INTO player(id,data_point,items_bag,items_body) VALUES(250,'[0,100,200,5,11,100,250,40,900000000000,100,30,120000000000,500]','[]','[]')");
  }
  var player=make(memory);PlayerProgressionRuntime.attach(player);
  if(args[1].equals("reopen")) {
   check(player.nPoint.hpg==2500&&player.nPoint.power==120000000123L,"real player reconnect did not restore exact state");
   System.out.println("PASS actual player progression reconnect after database restart");return;
  }
  check(PlayerProgressionRuntime.active(player)&&player.nPoint.exactHp().intValueExact()==250,"attach preserves HP ratio");
  player.nPoint.powerUp(123);player.nPoint.tiemNangUp(123);
  player.nPoint.increasePoint((byte)0,(short)100);
  check(player.nPoint.hpg==2500&&player.nPoint.power==120000000123L,"real NPoint hooks not applied");
  check(player.nPoint.tiemNang==900000000000L+123-249000,"exact potential debit");
  var other=make(memory);PlayerProgressionRuntime.attach(other);
  check(other.nPoint.hpg==2500&&other.nPoint.tiemNang==player.nPoint.tiemNang,"new player instance restored committed allocation");
  try(Connection c=DriverManager.getConnection(args[0],"root","" );var q=c.createStatement();var r=q.executeQuery("SELECT items_bag,items_body FROM player WHERE id=250")) {
   r.next();check(r.getString(1).equals("[]")&&r.getString(2).equals("[]"),"progression touched inventory");
  }
  System.out.println("PASS actual NPoint player hooks: authenticated attach, HP ratio, reward persistence, 100-point purchase, reconnect and untouched inventory");
 }
 static Probe make(sun.misc.Unsafe memory)throws Exception {
  var p=(Probe)memory.allocateInstance(Probe.class);NPointExactStatsTest.initialize(p);p.isBoss=false;p.id=250;
  p.session=(Session)memory.allocateInstance(Session.class);p.session.userId=77;
  p.playerTask=new nro.models.task.TaskPlayer(p);p.playerTask.taskMain=new nro.models.task.TaskMain();p.playerTask.taskMain.id=999;
  p.nPoint.hpg=500;p.nPoint.mpg=200;p.nPoint.dameg=30;p.nPoint.defg=40;p.nPoint.calPoint();
  p.nPoint.hp=250;p.nPoint.mp=100;return p;
 }
 static void check(boolean test,String label){if(!test)throw new AssertionError(label);}
}
