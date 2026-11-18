package nro.progression;
import java.math.BigInteger;
import java.util.*;
import java.io.*;
import nro.models.player.*;
import nro.models.map.Zone;
import nro.server.io.*;
public final class ExactStatsChannelTest {
 public static final class Viewer extends Player {
  final List<byte[]> packets=new ArrayList<>();
  @Override public void sendMessage(Message message){packets.add(message.getData().clone());}
 }
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var memory=(sun.misc.Unsafe)f.get(null);
  var map=(nro.models.map.Map)memory.allocateInstance(nro.models.map.Map.class);map.mapId=84;
  var zone=new Zone(map,0,30);
  // Normal construction initializes only the test capture list; Player construction would start unrelated services.
  var viewer=(Viewer)memory.allocateInstance(Viewer.class);
  var packets=Viewer.class.getDeclaredField("packets");packets.setAccessible(true);packets.set(viewer,new ArrayList<byte[]>());
  viewer.id=101;viewer.zone=zone;viewer.nPoint=new NPoint(viewer);
  viewer.nPoint.hp=viewer.nPoint.hpMax=100;viewer.nPoint.mp=viewer.nPoint.mpMax=200;viewer.nPoint.dame=300;
  zone.getPlayers().add(viewer);
  var session=(Session)memory.allocateInstance(Session.class);session.player=viewer;
  ExactStatsChannel.send(viewer,viewer);check(viewer.packets.isEmpty(),"legacy client received extension");
  byte[] unrelated={100,78,87,88,49,1,0};
  var old=new Message((byte)-128,unrelated);
  check(!ExactStatsChannel.request(session,old)&&old.reader().readUnsignedByte()==100,"Noel packet reader not preserved");
  var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeByte(101);out.writeInt(ExactStatsWire.MAGIC);out.writeByte(1);out.writeByte(0);
  check(ExactStatsChannel.request(session,new Message((byte)-128,bytes.toByteArray())),"subscription not accepted");
  check(viewer.packets.size()==1,"one initial exact actor response");
  byte[] received=viewer.packets.get(0);check(received[0]==101,"extension envelope");
  var snapshot=ExactStatsWire.decode(Arrays.copyOfRange(received,1,received.length));
  check(snapshot.actors().get(0).hp().equals(BigInteger.valueOf(100)),"actual channel values");
  ExactStatsChannel.request(session,new Message((byte)-128,bytes.toByteArray()));check(viewer.packets.size()==1,"poll flood throttle");
  ExactStatsChannel.broadcast(viewer);check(viewer.packets.size()==2,"battle broadcast");
  var foreign=(Player)memory.allocateInstance(Player.class);foreign.id=999;foreign.nPoint=viewer.nPoint;foreign.zone=new Zone(map,1,30);
  ExactStatsChannel.send(viewer,foreign);check(viewer.packets.size()==2,"cross-zone leak");
  System.out.println("PASS actual exact channel: opt-in only, Noel reader preserved, handshake response, packet decode, poll throttle, combat broadcast, zone isolation");
 }
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
