package nro.models.npc.specialnpc;

import nro.models.player.Player;
import nro.server.io.Message;
import nro.services.Service;
import nro.companion.CompanionService;
import nro.utils.Log;

/** Backwards-compatible egg packet/NPC; durable state also records its species. */
public class MabuEgg {
    private Player player;
    public long lastTimeCreate,timeDone;
    public String species="MABU";
    public MabuEgg(Player player,long created,long duration){this.player=player;lastTimeCreate=created;timeDone=duration;}
    public static void createMabuEgg(Player player){CompanionService.awardEgg(player,"MABU");}
    public void sendMabuEgg(){try{Message m=new Message(-122);m.writer().writeShort(50);m.writer().writeByte(1);m.writer().writeShort(4664);m.writer().writeByte(0);m.writer().writeInt(getSecondDone());player.sendMessage(m);m.cleanup();}catch(Exception e){Log.error(MabuEgg.class,e);}}
    public int getSecondDone(){long left=lastTimeCreate+Math.max(0,timeDone)-System.currentTimeMillis();return(int)Math.min(Integer.MAX_VALUE,Math.max(0,(left+999)/1000));}
    public void openEgg(int gender){try{CompanionService.hatch(player,this);}catch(IllegalArgumentException e){Service.gI().sendThongBao(player,e.getMessage());}}
    public void destroyEgg(){try{CompanionService.cancel(player,this);Message m=new Message(-117);m.writer().writeByte(101);player.sendMessage(m);m.cleanup();}catch(Exception e){Service.gI().sendThongBao(player,"Chưa hủy được trứng");}}
    public void subTimeDone(int d,int h,int m,int s){synchronized(player){long reduce=((long)d*86400+(long)h*3600+(long)m*60+s)*1000;if(reduce>0)timeDone=Math.max(0,timeDone-reduce);sendMabuEgg();}}
    public void dispose(){player=null;}
}
