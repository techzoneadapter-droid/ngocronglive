package nro.progression;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.services.Service;
import nro.services.func.CombineServiceNew;
import nro.consts.ConstNpc;

/** Native NPC menus backed by actual crafting and original CombineServiceNew routes. */
public final class KaizenNpc extends Npc {
    private static final int ROOT=30100,RECIPES=30101,CONFIRM=30102;
    private record Pending(KaizenRecipes recipe,String request){}
    private final Map<Player,Pending> pending=java.util.Collections.synchronizedMap(new WeakHashMap<>());
    private final KaizenPlayerForge forge;
    public KaizenNpc(int map,int x,int y,int template,int avatar,KaizenPlayerForge forge) {
        super(map,15,x,y,template,avatar);this.forge=forge;
    }
    @Override public void openBaseMenu(Player player) {
        if(!canOpenNpc(player))return;
        createOtherMenu(player,ROOT,"Thần Rèn Kaizen",
                "Đổi Thần Trang","Ghép mảnh\ntrang bị","Nâng cấp\ntrang bị","Khảm sao\npha lê","Hướng dẫn");
    }
    @Override public void confirmMenu(Player player,int select) {
        if(!canOpenNpc(player))return;
        int menu=player.iDMark.getIndexMenu();
        if(menu==ROOT) {
            switch(select) {
                case 0,1 -> createOtherMenu(player,RECIPES,"Chọn trang bị cần ghép.",java.util.Arrays.stream(KaizenRecipes.values()).map(r->r.displayName).toArray(String[]::new));
                case 2 -> KaizenCombine.upgrade(player);
                case 3 -> KaizenCombine.socket(player);
                case 4 -> Service.gI().sendThongBaoOK(player,guide());
            }
        }else if(menu==RECIPES && select>=0 && select<KaizenRecipes.values().length) {
            var recipe=KaizenRecipes.values()[select];
            pending.put(player,new Pending(recipe,UUID.randomUUID().toString()));
            createOtherMenu(player,CONFIRM,recipe.displayName+"\n"+recipe.fragments+" mảnh + "+String.format(java.util.Locale.ROOT,"%,d",recipe.gold)+" vàng\nTỉ lệ thành công 100%.","Ghép","Đóng");
        }else if(menu==CONFIRM && select==0) {
            var request=pending.get(player);if(request==null)return;
            try {forge.craft(player,request.recipe(),request.request());pending.remove(player);Service.gI().sendThongBao(player,"Đã ghép "+request.recipe().displayName+".");}
            catch(java.sql.SQLException error){Service.gI().sendThongBao(player,"Dữ liệu hành trang vừa thay đổi. Vui lòng mở lại Thần Rèn.");}
            catch(IllegalArgumentException error){Service.gI().sendThongBao(player,error.getMessage());}
        }else if(menu==ConstNpc.MENU_START_COMBINE) {
            int type=player.combineNew.typeCombine;
            if(type==CombineServiceNew.NANG_CAP_VAT_PHAM||type==CombineServiceNew.EP_SAO_TRANG_BI||type==CombineServiceNew.PHA_LE_HOA_TRANG_BI)
                CombineServiceNew.gI().startCombine(player,select);
        }
    }
    private static String guide() {
        return "Vương miện: boss Thần Linh.\nVòng tay: quái tinh anh và boss.\nVòng cổ: nhiệm vụ hằng ngày hoặc đổi thưởng.\nÁo choàng: boss cuối phó bản.\nMặt nạ: Cell, Broly, Ma Bư, Black Goku.\nKhăn choàng: Đại Hội Võ Thuật và sự kiện.\nHào quang sau đầu: boss thần cấp cao và ghép mảnh.\nTrang bị nâng cấp tối đa +7; khảm sao theo giới hạn trang bị.";
    }
}
