package nro.progression;

import java.sql.*;
import org.json.JSONArray;

/** Inventory/gold/request receipt commit together; caller must hold the live player lock. */
public final class KaizenTransactions {
    public record Receipt(JSONArray bag,JSONArray money,boolean replay) {}
    private KaizenTransactions() {}
    public static void initialize(Connection c)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("DDL requires auto-commit");
        try(var s=c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_kaizen_requests (
                    player_id BIGINT NOT NULL,
                    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
                    recipe VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                    PRIMARY KEY(player_id,request_key)
                ) ENGINE=InnoDB
                """);
        }
    }
    /** liveBag/liveMoney prevent an unsaved live inventory from being overwritten by an old DB row. */
    public static Receipt craft(Connection c,KaizenForge forge,long playerId,long accountId,String request,
            KaizenRecipes recipe,String liveBag,String liveMoney)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Craft requires caller-owned transaction");
        if(playerId<=0||accountId<=0||request==null||!request.matches("[A-Za-z0-9_.:-]{1,128}"))
            throw new IllegalArgumentException("Invalid crafting request");
        String bag,money;
        try(var q=c.prepareStatement("SELECT account_id,items_bag,data_inventory FROM player WHERE id=? FOR UPDATE")) {
            q.setLong(1,playerId);
            try(var r=q.executeQuery()) {
                if(!r.next()||r.getLong(1)!=accountId)throw new SQLException("Character ownership mismatch","28000");
                bag=r.getString(2);money=r.getString(3);
            }
        }
        try(var q=c.prepareStatement("SELECT recipe FROM nero_kaizen_requests WHERE player_id=? AND request_key=?")) {
            q.setLong(1,playerId);q.setString(2,request);
            try(var r=q.executeQuery()) {
                if(r.next()) {
                    if(!recipe.name().equals(r.getString(1)))throw new IllegalArgumentException("Request reused for another recipe");
                    return new Receipt(new JSONArray(bag),new JSONArray(money),true);
                }
            }
        }
        if(!sameInventory(new JSONArray(bag),new JSONArray(liveBag)) || !new JSONArray(money).similar(new JSONArray(liveMoney))) {
            throw new SQLException("Inventory changed; save and refresh before crafting","40001");
        }
        JSONArray balances=new JSONArray(money);
        KaizenForge.Plan plan=forge.craft(recipe,new JSONArray(liveBag),balances.getLong(0));
        balances.put(0,plan.gold());
        try(var q=c.prepareStatement("UPDATE player SET items_bag=?,data_inventory=? WHERE id=? AND account_id=?")) {
            q.setString(1,plan.bag().toString());q.setString(2,balances.toString());q.setLong(3,playerId);q.setLong(4,accountId);
            if(q.executeUpdate()!=1)throw new SQLException("Craft inventory was not saved");
        }
        try(var q=c.prepareStatement("INSERT INTO nero_kaizen_requests(player_id,request_key,recipe) VALUES(?,?,?)")) {
            q.setLong(1,playerId);q.setString(2,request);q.setString(3,recipe.name());q.executeUpdate();
        }
        return new Receipt(plan.bag(),balances,false);
    }
    static boolean sameInventory(JSONArray stored,JSONArray current) {
        if(stored.length()!=current.length())return false;
        // Native sendItemBags compacts slots without saving. Compare every real item,
        // including quantity/options/time, while allowing only this harmless reorder.
        boolean[] matched=new boolean[current.length()];
        for(int i=0;i<stored.length();i++) {
            var a=stored.getJSONObject(i);boolean found=false;
            for(int j=0;j<current.length();j++) {
                if(matched[j])continue;
                var b=current.getJSONObject(j);
                if((a.getInt("temp_id")==-1 && b.getInt("temp_id")==-1) || a.similar(b)) {
                    matched[j]=true;found=true;break;
                }
            }
            if(!found)return false;
        }
        return true;
    }
}
