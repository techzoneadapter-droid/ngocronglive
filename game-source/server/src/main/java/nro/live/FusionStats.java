package nro.live;
import nro.models.player.*;
/** Merge derived battle stats once, after both complete independent equipment calculations. */
public final class FusionStats {
    public static void apply(Player master){
        if(master.isPet)return;
        if(master.pet==null||master.fusion.typeFusion==0){master.nPoint.mergeStamina(null,0);return;}
        NPoint a=master.nPoint,b=master.pet.nPoint;int pct=master.pet.liveSpecies.equals("NORMAL")?30:master.pet.liveSpecies.equals("MABU")?50:master.pet.liveSpecies.startsWith("VIP_")?70:30;
        a.mergeStamina(b,pct);
        merge(a.tlTNSM,b.tlTNSM,pct);merge(a.tlDameCrit,b.tlDameCrit,pct);merge(a.tlDameAttMob,b.tlDameAttMob,pct);
        a.hpMax=Balance.fused(a.hpMax,b.hpMax,pct,Integer.MAX_VALUE);a.mpMax=Balance.fused(a.mpMax,b.mpMax,pct,Integer.MAX_VALUE);
        a.dame=Balance.fused(a.dame,b.dame,pct,Integer.MAX_VALUE);a.def=Balance.fused(a.def,b.def,pct,Integer.MAX_VALUE);a.crit=Balance.fused(a.crit,b.crit,pct,100);
        a.speed=(byte)Balance.fused(a.speed,b.speed,pct,20);a.hpHoi=Balance.fused(a.hpHoi,b.hpHoi,pct,Integer.MAX_VALUE);a.mpHoi=Balance.fused(a.mpHoi,b.mpHoi,pct,Integer.MAX_VALUE);
        a.mpHoiCute=Balance.fused(a.mpHoiCute,b.mpHoiCute,pct,Integer.MAX_VALUE);
        a.tlHutHp=(short)Balance.fused(a.tlHutHp,b.tlHutHp,pct,100);a.tlHutMp=(short)Balance.fused(a.tlHutMp,b.tlHutMp,pct,100);a.tlHutHpMob=(short)Balance.fused(a.tlHutHpMob,b.tlHutHpMob,pct,100);
        a.tlHutHpMpXQ=(short)Balance.fused(a.tlHutHpMpXQ,b.tlHutHpMpXQ,pct,100);a.tlPST=(short)Balance.fused(a.tlPST,b.tlPST,pct,100);a.tlNeDon=(short)Balance.fused(a.tlNeDon,b.tlNeDon,pct,100);
        a.tlGiamst=Balance.fused(a.tlGiamst,b.tlGiamst,pct,60);a.mstChuong=Balance.fused(a.mstChuong,b.mstChuong,pct,Integer.MAX_VALUE);
        a.tlGold=(short)Balance.fused(a.tlGold,b.tlGold,pct,32767);a.tlHpHoi=(short)Balance.fused(a.tlHpHoi,b.tlHpHoi,pct,100);a.tlMpHoi=(short)Balance.fused(a.tlMpHoi,b.tlMpHoi,pct,100);
        a.tlHpHoiBanThanVaDongDoi=(short)Balance.fused(a.tlHpHoiBanThanVaDongDoi,b.tlHpHoiBanThanVaDongDoi,pct,100);a.tlMpHoiBanThanVaDongDoi=(short)Balance.fused(a.tlMpHoiBanThanVaDongDoi,b.tlMpHoiBanThanVaDongDoi,pct,100);
    }
    private static void merge(java.util.List<Integer> a,java.util.List<Integer> b,int pct){long x=0,y=0;for(int i:a)x+=i;for(int i:b)y+=i;a.clear();int v=Balance.fused(x,y,pct,10000);if(v>0)a.add(v);}
}
