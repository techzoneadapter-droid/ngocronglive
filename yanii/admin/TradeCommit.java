package nro.admin;
import java.sql.*;
import java.util.List;
import nro.jdbc.DBHika;
import nro.models.item.Item;
import nro.models.player.Player;
/** Commit both inventories together; never overwrite a concurrent code/item change with a stale trade snapshot. */
public final class TradeCommit {
    public static void commit(Player a,Player b,List<Item> oldA,List<Item> oldB,List<Item> nextA,List<Item> nextB,long goldA,long goldB,int giveA,int giveB)throws Exception{
        Player first=a.id<b.id?a:b,second=a.id<b.id?b:a;
        synchronized(first){synchronized(second){
            if(a.isDisposed()||b.isDisposed()||a.zone!=b.zone||a.inventory.gold!=goldA||b.inventory.gold!=goldB||!OnlineRewards.items(a.inventory.itemsBag).similar(OnlineRewards.items(oldA))||!OnlineRewards.items(b.inventory.itemsBag).similar(OnlineRewards.items(oldB)))throw new IllegalArgumentException("Tài nguyên đã thay đổi; hãy mở lại giao dịch");
            if(giveA<0||giveB<0||giveA>goldA||giveB>goldB)throw new IllegalArgumentException("Không đủ vàng giao dịch");
            long resultA=goldA-giveA+giveB,resultB=goldB-giveB+giveA;if(resultA>a.inventory.getGoldLimit()||resultB>b.inventory.getGoldLimit())throw new IllegalArgumentException("Vàng vượt giới hạn");
            var moneyA=new org.json.JSONArray().put(resultA).put(a.inventory.gem).put(a.inventory.ruby).put(a.inventory.goldLimit);var moneyB=new org.json.JSONArray().put(resultB).put(b.inventory.gem).put(b.inventory.ruby).put(b.inventory.goldLimit);
            try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{OnlineRewards.execute(c,"UPDATE player SET items_bag=?,data_inventory=? WHERE id=?",OnlineRewards.items(nextA).toString(),moneyA.toString(),a.id);OnlineRewards.execute(c,"UPDATE player SET items_bag=?,data_inventory=? WHERE id=?",OnlineRewards.items(nextB).toString(),moneyB.toString(),b.id);c.commit();}catch(Exception e){c.rollback();throw e;}}
            a.inventory.itemsBag=nextA;b.inventory.itemsBag=nextB;a.inventory.gold=resultA;b.inventory.gold=resultB;
        }}
    }
}
