package nro.companion;

import nro.models.boss.*;
import nro.models.player.*;
import nro.services.*;
import nro.utils.Util;

/** Three ordinary world bosses with owner-bound incubation rewards. */
public final class CompanionBoss extends Boss {
    private final String species;
    private boolean rewarded;
    private static BossData data(String s){return new BossData("Hộ vệ trứng "+CompanionService.name(s),(byte)0,CompanionAssets.outfit(s),s.equals("MABU")?2000:s.equals("JIREN")?5000:10000,
        new int[][]{{s.equals("MABU")?5000000:s.equals("JIREN")?20000000:50000000}},new short[]{5,13,20},new int[][]{{0,7,1000},{1,7,1200}},300);}
    public CompanionBoss(String s){super(s.equals("MABU")?-91001:s.equals("JIREN")?-91002:-91003,data(s));species=s;lastTimeRest=0;}
    @Override public void respawn(){rewarded=false;super.respawn();}
    @Override public void initTalk(){textTalkBefore=new String[]{"Hãy chứng minh sức mạnh để nhận trứng!"};textTalkMidle=new String[]{"Bảo vệ trứng!"};textTalkAfter=new String[]{"Hẹn gặp lại!"};}
    @Override public void rewards(Player p){if(rewarded||p==null)return;rewarded=true;if(p.isPet)p=((Pet)p).master;if(p==null||p.getSession()==null)return;
        if(p.mabuEgg!=null){Service.getInstance().sendThongBao(p,"Bạn đang ấp một trứng; boss không thay trứng đang có.");return;}
        int chance=CompanionService.settings().getInt(species.toLowerCase(java.util.Locale.ROOT)+"_drop");
        if(Util.isTrue(chance,100))CompanionService.awardEgg(p,species);
    }
    @Override protected boolean useSpecialSkill(){return false;}
    @Override public void checkPlayerDie(Player p){}
}
