package nro.admin;

import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import nro.consts.ConstNpc;
import nro.jdbc.DBHika;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.shop.ItemShop;
import nro.server.io.Message;
import nro.server.io.Session;
import nro.services.InventoryService;
import nro.services.ItemService;
import nro.services.Service;
import nro.services.func.ShopService;

/** Runs bundle and multi-buy purchases through one inventory/currency commit. */
public final class NpcBundlePurchase {
    private static final ThreadLocal<Integer> LOTS = ThreadLocal.withInitial(() -> 1);
    private static final java.util.Map<Player,Long> LAST =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static boolean packet(Session session, Message message) {
        if(message.command!=6 || session.player==null) return false;
        var input=message.reader();
        try {
            input.mark(16);
            byte kind=input.readByte();
            int template=input.readUnsignedShort();
            int quantity=input.available()==2 ? input.readUnsignedShort() : 1;
            if(input.available()!=0 || quantity<1 || quantity>999) {
                Service.gI().sendThongBao(session.player,"Số lượng mua từ 1 đến 999.");
                return true;
            }
            if(quantity==1) {input.reset();return false;}
            LOTS.set(quantity);
            synchronized(session.player) {
                ShopService.gI().buyItem(session.player,kind,template);
            }
            return true;
        } catch(Exception error) {
            Service.gI().sendThongBao(session.player,"Chưa mua được vật phẩm. Hãy mở lại cửa hàng.");
            System.err.println("NPC bundle purchase: "+error);
            return true;
        } finally {LOTS.remove();}
    }

    public static int units(ItemShop offer) {
        int units=1;
        for(var option:offer.options)
            if(option.optionTemplate.id==31) {
                if(option.param<1 || option.param>9999)
                    throw new IllegalArgumentException("Invalid shop bundle size");
                units=option.param;
            }
        return units;
    }

    public static boolean buy(Player player, ItemShop offer) {
        int lots=LOTS.get();
        if(offer==null) return false;
        int pack=units(offer);
        if(lots==1 && pack==1) return false;
        // Santa's ordinary priced inventory uses gold or blue gems.
        if(offer.tabShop==null || offer.tabShop.shop==null
            || offer.tabShop.shop.npcId!=ConstNpc.SANTA || offer.itemExchange>0
            || offer.costSpec>0 || offer.gold<0 || offer.gem<0 || (offer.gold<=0 && offer.gem<=0)
            || offer.temp.type<0 || offer.temp.type==9 || offer.temp.type==10
            || offer.temp.type==34 || offer.temp.id==457) {
            if(lots==1) return false;
            Service.gI().sendThongBao(player,"Món này cần mua từng lượt.");
            return true;
        }
        synchronized(player) {
            long now=System.nanoTime();
            Long last=LAST.get(player);
            if(last!=null && now-last<TimeUnit.MILLISECONDS.toNanos(250)) return true;
            LAST.put(player,now);
            try {
                long gold=Math.multiplyExact((long)offer.gold,lots);
                long gems=Math.multiplyExact((long)offer.gem,lots);
                if(gold>player.inventory.gold || gems>player.inventory.gem)
                    throw new IllegalArgumentException("Không đủ vàng hoặc kim cương xanh.");
                Item reward=ItemService.gI().createItemFromItemShop(offer);
                reward.quantity=Math.multiplyExact(pack,lots);
                reward.itemOptions.removeIf(option->option.optionTemplate.id==31);
                var bag=ConsignmentStore.copyBag(player);
                if(!InventoryService.gI().addItemList(bag,reward,99999))
                    throw new IllegalArgumentException("Hành trang không đủ chỗ nhận đủ vật phẩm.");
                long remainingGold=player.inventory.gold-gold;
                int remainingGems=Math.toIntExact(player.inventory.gem-gems);
                try(Connection connection=DBHika.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try(var lock=connection.prepareStatement("SELECT id FROM player WHERE id=? FOR UPDATE")) {
                            lock.setLong(1,player.id);
                            try(var rows=lock.executeQuery()) {if(!rows.next()) throw new java.sql.SQLException("Player missing");}
                        }
                        try(var save=connection.prepareStatement("UPDATE player SET items_bag=?,data_inventory=? WHERE id=? AND account_id=?")) {
                            save.setString(1,ConsignmentStore.bagJson(bag));
                            save.setString(2,new org.json.JSONArray().put(remainingGold).put(remainingGems)
                                .put(player.inventory.ruby).put(player.inventory.goldLimit).toString());
                            save.setLong(3,player.id);save.setInt(4,player.getSession().userId);
                            if(save.executeUpdate()!=1) throw new java.sql.SQLException("Player save failed");
                        }
                        connection.commit();
                    } catch(Exception error) {connection.rollback();throw error;}
                }
                player.inventory.itemsBag=bag;
                player.inventory.gold=remainingGold;
                player.inventory.gem=remainingGems;
                InventoryService.gI().sendItemBags(player);
                Service.gI().sendMoney(player);
                Service.gI().sendThongBao(player,"Đã mua "+reward.quantity+" "+offer.temp.name+".");
            } catch(IllegalArgumentException error) {
                Service.gI().sendThongBao(player,error.getMessage());
            } catch(Exception error) {
                Service.gI().sendThongBao(player,"Chưa hoàn tất mua hàng. Hãy mở lại cửa hàng.");
                System.err.println("NPC bundle transaction: "+error);
            }
        }
        return true;
    }
}
