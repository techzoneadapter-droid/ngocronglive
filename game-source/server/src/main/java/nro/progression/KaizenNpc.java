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
    private static final int ROOT=30100,RECIPES=30101,CONFIRM=30102,SOCKET=30103;
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
                case 3 -> createOtherMenu(player,SOCKET,"Chọn thao tác sao pha lê.","Tạo lỗ sao","Khảm sao pha lê","Đóng");
                case 4 -> Service.gI().sendThongBaoOK(player,guide());
            }
        }else if(menu==SOCKET) {
            if(select==0)KaizenCombine.createSocket(player);
            else if(select==1)KaizenCombine.socket(player);
        }else if(menu==RECIPES && select>=0 && select<KaizenRecipes.values().length) {
            var recipe=KaizenRecipes.values()[select];
            pending.put(player,new Pending(recipe,UUID.randomUUID().toString()));
            createOtherMenu(player,CONFIRM,recipe.displayName+"\n"+recipe.fragments+" mảnh + "+String.format(java.util.Locale.ROOT,"%,d",recipe.gold)+" vàng\nTỉ lệ thành công 100%.","Ghép","Đóng");
        }else if(menu==CONFIRM && select==0) {
            var request=pending.get(player);if(request==null)return;
            try {forge.craft(player,request.recipe(),request.request());pending.remove(player);Service.gI().sendThongBao(player,"Đã ghép "+request.recipe().displayName+".");}
            catch(java.sql.SQLException error){System.err.println("KAIZEN_CRAFT_REFUSED state="+error.getSQLState()+" reason="+error.getMessage());Service.gI().sendThongBao(player,"Dữ liệu hành trang vừa thay đổi. Vui lòng mở lại Thần Rèn.");}
            catch(IllegalArgumentException error){Service.gI().sendThongBao(player,error.getMessage());}
        }else if(menu==ConstNpc.MENU_START_COMBINE) {
            int type=player.combineNew.typeCombine;
            if(type==CombineServiceNew.NANG_CAP_VAT_PHAM||type==CombineServiceNew.EP_SAO_TRANG_BI||type==CombineServiceNew.PHA_LE_HOA_TRANG_BI)
                CombineServiceNew.gI().startCombine(player,select);
        }
    }
    private static String guide() {
        StringBuilder text=new StringBuilder("Ghép mảnh thành công 100%. Trang bị nhận cấp +0.\n");
        for(var recipe:KaizenRecipes.values())text.append(recipe.displayName).append(": ").append(recipe.fragments).append(" mảnh + ").append(String.format(java.util.Locale.ROOT,"%,d",recipe.gold)).append(" vàng.\n");
        return text.append("Nâng cấp tối đa +7; khảm sao theo giới hạn trang bị.\nCác khu săn Thần Trang mới chưa mở.").toString();
    }
}
