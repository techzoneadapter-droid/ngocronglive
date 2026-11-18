package nro.progression;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;
import nro.admin.LiveReward;
import nro.models.player.Player;
import nro.models.item.Item;
import nro.models.item.ItemOption;
import nro.services.*;
import org.json.JSONArray;

/** Real player adapter: prepare decoded inventory before commit, install only after commit. */
public final class KaizenPlayerForge {
    private final BossProgressionRuntime.Connections connections;
    private final KaizenForge forge;
    public KaizenPlayerForge(BossProgressionRuntime.Connections connections,KaizenForge forge){this.connections=connections;this.forge=forge;}
    public void craft(Player player,KaizenRecipes recipe,String requestKey)throws SQLException {
        synchronized(player) {
            if(player.getSession()==null || player.isBoss || player.isPet || player.isMiniPet || player.beforeDispose)
                throw new IllegalArgumentException("Nhân vật chưa sẵn sàng");
            String bag=new LiveReward(player.inventory.gold,player.inventory.gem,player.inventory.ruby,
                    player.inventory.goldLimit,player.inventory.itemsBag,"").bagJson();
            String money=new JSONArray().put(player.inventory.gold).put(player.inventory.gem)
                    .put(player.inventory.ruby).put(player.inventory.goldLimit).toString();
            // Flush through the game's existing save path before comparing the locked
            // row: ordinary purchases/rewards may still exist only in live memory.
            try(Connection save=connections.open()) {
                nro.jdbc.daos.PlayerDAO.updatePlayer(player,save);
            }
            try(Connection c=connections.open()) {
                c.setAutoCommit(false);
                try {
                    var result=KaizenTransactions.craft(c,forge,player.id,player.getSession().userId,requestKey,recipe,bag,money);
                    var decoded=new ArrayList<Item>();
                    for(Object raw:result.bag()) {
                        var row=(org.json.JSONObject)raw;int id=row.getInt("temp_id");
                        Item item=id<0?ItemService.gI().createItemNull():ItemService.gI().createNewItem((short)id,row.getInt("quantity"));
                        if(id>=0) {
                            item.createTime=row.getLong("create_time");
                            JSONArray options=row.getJSONArray("option");
                            for(Object value:options){var pair=(JSONArray)value;var option=new ItemOption(pair.getInt(0),pair.getInt(1));if(pair.length()>2)option.activeCard=(byte)pair.getInt(2);item.itemOptions.add(option);}
                        }
                        decoded.add(item);
                    }
                    // A retry must not install a potentially older DB inventory over unsaved live rewards.
                    if(result.replay()){c.rollback();return;}
                    c.commit();
                    player.inventory.itemsBag=decoded;player.inventory.gold=result.money().getLong(0);
                }catch(Exception error){c.rollback();throw error;}
            }
            InventoryService.gI().sendItemBags(player);Service.gI().sendMoney(player);
        }
    }
}
