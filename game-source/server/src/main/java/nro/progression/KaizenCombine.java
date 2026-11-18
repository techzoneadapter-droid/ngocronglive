package nro.progression;

import nro.models.player.Player;
import nro.services.func.CombineServiceNew;

/** NPC routes directly to the existing upgrade/socket implementation; no copied formulas. */
public final class KaizenCombine {
    private KaizenCombine(){}
    public static void upgrade(Player player) {
        CombineServiceNew.gI().openTabCombine(player,CombineServiceNew.NANG_CAP_VAT_PHAM);
    }
    public static void socket(Player player) {
        CombineServiceNew.gI().openTabCombine(player,CombineServiceNew.EP_SAO_TRANG_BI);
    }
    public static void createSocket(Player player) {
        CombineServiceNew.gI().openTabCombine(player,CombineServiceNew.PHA_LE_HOA_TRANG_BI);
    }
}
