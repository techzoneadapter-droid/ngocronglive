package nro.progression;

import java.math.BigInteger;
import java.sql.*;
import java.util.*;
import nro.models.player.Player;

/** Explicitly enabled player integration; all monetary/inventory columns remain untouched. */
public final class PlayerProgressionRuntime {
    private static volatile BossProgressionRuntime.Connections connections;
    private static final Map<Player,ProgressionRepository.Saved> states=Collections.synchronizedMap(new WeakHashMap<>());
    private PlayerProgressionRuntime(){}
    public static void install(BossProgressionRuntime.Connections value){connections=Objects.requireNonNull(value);}
    public static boolean active(Player player){return states.containsKey(player);}
    public static void attach(Player player)throws SQLException {
        if(connections==null || active(player) || player.isPet || player.isBoss || player.isMiniPet)return;
        synchronized(player) {
            if(active(player))return;
            try(Connection c=connections.open()) {
                c.setAutoCommit(false);
                try {
                    var saved=ProgressionCommands.open(c,player.id,player.getSession().userId);
                    c.commit();
                    int oldHp=player.nPoint.hp,oldMp=player.nPoint.mp;
                    int oldHpMax=Math.max(1,player.nPoint.hpMax),oldMpMax=Math.max(1,player.nPoint.mpMax);
                    apply(player,saved);player.nPoint.calPoint();
                    player.nPoint.setExactVitals(player.nPoint.exactHpMax().multiply(BigInteger.valueOf(Math.max(0,oldHp))).divide(BigInteger.valueOf(oldHpMax)),
                        player.nPoint.exactMpMax().multiply(BigInteger.valueOf(Math.max(0,oldMp))).divide(BigInteger.valueOf(oldMpMax)));
                }catch(Exception error){c.rollback();throw error;}
            }
        }
    }
    public static boolean reward(Player player,long power,long potential) {
        if(!active(player))return false;
        if(power<0||potential<0)throw new IllegalArgumentException("Negative progression reward");
        synchronized(player) {
            try(Connection c=connections.open()) {
                c.setAutoCommit(false);
                try {
                    var result=ProgressionCommands.reward(c,player.id,player.getSession().userId,UUID.randomUUID().toString(),BigInteger.valueOf(power),BigInteger.valueOf(potential));
                    c.commit();apply(player,result.saved());return true;
                }catch(Exception error){c.rollback();throw error;}
            }catch(SQLException error){throw new IllegalStateException("Progression reward was not saved",error);}
        }
    }
    public static boolean increase(Player player,byte type,short count) {
        if(!active(player))return false;
        if(count<=0 || type<0 || type>3)throw new IllegalArgumentException("Loại chỉ số không hỗ trợ tăng nhiều");
        var stat=new UnlimitedProgression.Stat[]{UnlimitedProgression.Stat.HP,UnlimitedProgression.Stat.KI,UnlimitedProgression.Stat.ATTACK,UnlimitedProgression.Stat.DEFENSE}[type];
        synchronized(player) {
            try(Connection c=connections.open()) {
                c.setAutoCommit(false);
                try {
                    var current=ProgressionCommands.open(c,player.id,player.getSession().userId);
                    var next=current.state().purchase(stat,BigInteger.valueOf(count));
                    var saved=ProgressionRepository.save(c,current,next.state());
                    c.commit();apply(player,saved);player.nPoint.calPoint();
                    nro.services.Service.gI().point(player);ExactStatsChannel.broadcast(player);return true;
                }catch(Exception error){c.rollback();throw error;}
            }catch(SQLException error){throw new IllegalStateException("Progression allocation was not saved",error);}
        }
    }
    private static void apply(Player player,ProgressionRepository.Saved saved) {
        states.put(player,saved);player.nPoint.bindUnlimitedBase(saved.state());
        player.nPoint.power=saved.state().get(UnlimitedProgression.Stat.POWER).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        player.nPoint.tiemNang=saved.state().get(UnlimitedProgression.Stat.POTENTIAL).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        player.nPoint.hpg=project(saved.state().get(UnlimitedProgression.Stat.HP));
        player.nPoint.mpg=project(saved.state().get(UnlimitedProgression.Stat.KI));
        player.nPoint.dameg=project(saved.state().get(UnlimitedProgression.Stat.ATTACK));
        player.nPoint.defg=project(saved.state().get(UnlimitedProgression.Stat.DEFENSE));
    }
    private static int project(BigInteger value){return value.min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();}
}
