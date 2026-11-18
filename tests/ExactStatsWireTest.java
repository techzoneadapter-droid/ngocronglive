package nro.progression;
import java.math.BigInteger;
import java.util.List;
import java.nio.file.*;
public final class ExactStatsWireTest {
 public static void main(String[] args)throws Exception {
  var huge=BigInteger.TEN.pow(100);
  var actor=new ExactStatsWire.Actor(-1007230,huge,huge.multiply(BigInteger.TWO),BigInteger.ZERO,huge,huge,huge);
  var original=new ExactStatsWire.Snapshot(123,84,0,List.of(actor));
  byte[] packet=ExactStatsWire.encode(original);
  check(ExactStatsWire.decode(packet).equals(original),"exact wire roundtrip");
  for(int length=0;length<packet.length;length++) {
   try{ExactStatsWire.decode(java.util.Arrays.copyOf(packet,length));throw new AssertionError("truncated packet accepted");}catch(java.io.IOException expected){}
  }
  byte[] invalid=packet.clone();invalid[35]='-';
  try{ExactStatsWire.decode(invalid);throw new AssertionError("negative decimal accepted");}catch(java.io.IOException expected){}
  try{new ExactStatsWire.Snapshot(0,0,0,List.of(actor,actor));throw new AssertionError("duplicate actor");}catch(IllegalArgumentException expected){}
  if(args.length>0)Files.write(Path.of(args[0]),packet);
  System.out.println("PASS exact stat packet: 10^100 roundtrip, signed actor ID, all truncated lengths rejected, malformed decimal and duplicate actors refused");
 }
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
