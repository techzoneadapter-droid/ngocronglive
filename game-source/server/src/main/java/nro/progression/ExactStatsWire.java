package nro.progression;

import java.io.*;
import java.math.BigInteger;
import java.util.List;

/** Opt-in extension payload. Never changes the layout of legacy int-based packets. */
public final class ExactStatsWire {
    public static final int MAGIC=0x4e455831;
    public static final int VERSION=1, MAX_ACTORS=128, MAX_DIGITS=4096;
    public record Actor(long id,BigInteger hp,BigInteger hpMax,BigInteger ki,BigInteger kiMax,
                        BigInteger attack,BigInteger defense) {
        public Actor {
            for(BigInteger value:new BigInteger[]{hp,hpMax,ki,kiMax,attack,defense})
                if(value==null || value.signum()<0 || value.toString().length()>MAX_DIGITS)
                    throw new IllegalArgumentException("Invalid exact stat");
            if(hp.compareTo(hpMax)>0 || ki.compareTo(kiMax)>0)throw new IllegalArgumentException("Current vitals exceed maximum");
        }
    }
    public record Snapshot(long sequence,int map,int zone,List<Actor> actors) {
        public Snapshot {
            actors=List.copyOf(actors);
            if(sequence<0 || map<0 || zone<0 || actors.size()>MAX_ACTORS ||
                actors.stream().map(Actor::id).distinct().count()!=actors.size())throw new IllegalArgumentException("Invalid exact snapshot");
        }
    }
    private ExactStatsWire(){}
    public static byte[] encode(Snapshot snapshot)throws IOException {
        var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeByte(VERSION);out.writeLong(snapshot.sequence());
        out.writeInt(snapshot.map());out.writeInt(snapshot.zone());out.writeShort(snapshot.actors().size());
        for(Actor actor:snapshot.actors()) {
            out.writeLong(actor.id());
            for(BigInteger value:new BigInteger[]{actor.hp(),actor.hpMax(),actor.ki(),actor.kiMax(),actor.attack(),actor.defense()})out.writeUTF(value.toString());
        }
        out.flush();return bytes.toByteArray();
    }
    public static Snapshot decode(byte[] bytes)throws IOException {
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        if(in.readInt()!=MAGIC || in.readUnsignedByte()!=VERSION)throw new IOException("Unsupported exact stats packet");
        long sequence=in.readLong();int map=in.readInt(),zone=in.readInt(),count=in.readUnsignedShort();
        if(count>MAX_ACTORS)throw new IOException("Too many exact actors");
        var actors=new java.util.ArrayList<Actor>();
        try {
            for(int index=0;index<count;index++)actors.add(new Actor(in.readLong(),number(in),number(in),number(in),number(in),number(in),number(in)));
            if(in.available()!=0)throw new IOException("Trailing exact packet data");
            return new Snapshot(sequence,map,zone,actors);
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid exact snapshot",invalid);}
    }
    private static BigInteger number(DataInputStream in)throws IOException {
        int length=in.readUnsignedShort();
        if(length<1 || length>MAX_DIGITS || length>in.available())throw new IOException("Invalid decimal length");
        byte[] bytes=new byte[length];in.readFully(bytes);
        if(length>1 && bytes[0]=='0')throw new IOException("Noncanonical decimal");
        for(byte digit:bytes)if(digit<'0'||digit>'9')throw new IOException("Invalid decimal digit");
        return new BigInteger(new String(bytes,java.nio.charset.StandardCharsets.US_ASCII));
    }
}
