package nro.progression;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.player.Player;
import nro.server.io.Message;
import nro.server.io.Session;

/** Separate opt-in transport: no extension packets are sent to unmodified clients. */
public final class ExactStatsChannel {
    public static final int PREFIX=101;
    private static final Set<Player> clients=Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Player,Long> requests=Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicLong sequence=new AtomicLong();
    private ExactStatsChannel(){}
    public static boolean request(Session session,Message message) {
        if(message.command!=(byte)-128 || session.player==null)return false;
        var input=message.reader();input.mark(16);
        try {
            if(input.available()!=7 || input.readUnsignedByte()!=PREFIX || input.readInt()!=ExactStatsWire.MAGIC
                    || input.readUnsignedByte()!=ExactStatsWire.VERSION || input.readUnsignedByte()!=0) {
                input.reset();return false;
            }
            Player player=session.player;long now=System.currentTimeMillis();
            Long last=requests.put(player,now);
            if(last!=null && now-last<500)return true;
            PlayerProgressionRuntime.attach(player);
            clients.add(player);
            send(player,player);
            if(player.zone!=null)for(var boss:player.zone.getBosses())send(player,boss);
            return true;
        }catch(java.io.IOException | java.sql.SQLException error){System.err.println("Exact stats subscription failed: "+error.getClass().getSimpleName());return true;}
    }
    public static boolean subscribed(Player player){return clients.contains(player);}
    public static void broadcast(Player actor) {
        if(actor==null || actor.zone==null)return;
        for(Player viewer:actor.zone.getPlayers())if(clients.contains(viewer))send(viewer,actor);
    }
    public static void send(Player viewer,Player actor) {
        if(viewer==null || actor==null || viewer.zone==null || actor.zone!=viewer.zone || actor.nPoint==null || !clients.contains(viewer))return;
        var point=actor.nPoint;
        try {
            var row=new ExactStatsWire.Actor(actor.id,point.exactHp(),point.exactHpMax(),point.exactMp(),point.exactMpMax(),point.exactDamage(),point.exactDefense());
            byte[] payload=ExactStatsWire.encode(new ExactStatsWire.Snapshot(sequence.incrementAndGet(),viewer.zone.map.mapId,viewer.zone.zoneId,List.of(row)));
            Message message=new Message(-128);
            try {message.writer().writeByte(PREFIX);message.writer().write(payload);viewer.sendMessage(message);}
            finally {message.cleanup();}
        }catch(Exception error){System.err.println("Exact stat synchronization failed: "+error.getClass().getSimpleName());}
    }
}
