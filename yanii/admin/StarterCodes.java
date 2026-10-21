package nro.admin;
import java.sql.*;
import org.json.*;
import nro.models.item.*;
import nro.server.Manager;
/** Editable beta presets, seeded once; never overwrite an owner's edited package/code. */
public final class StarterCodes {
    private static JSONObject item(int id,int amount,JSONArray options){return new JSONObject().put("kind","ITEM").put("item_id",id).put("quantity",amount).put("options",options);}
    private static JSONObject money(String type,long amount){return new JSONObject().put("kind","CURRENCY").put("currency",type).put("amount",amount).put("mode","ADD");}
    public static void install(Connection c)throws SQLException{
        String[] codes={"TANTHUVANG","TANTHUNGOC","TANTHUTHOIVANG","TANTHUPHUKIEN","TANTHUBUFF","TANTHUTIEMNANG","TANTHU8SAO"};
        JSONArray[] lists={new JSONArray().put(money("gold",10000000)),new JSONArray().put(money("gem",1000)),new JSONArray().put(item(457,2,new JSONArray())),new JSONArray().put(item(741,1,new JSONArray().put(new JSONArray().put(30).put(0)))).put(item(920,1,new JSONArray().put(new JSONArray().put(30).put(0)))).put(item(1039,1,new JSONArray().put(new JSONArray().put(30).put(0)))),new JSONArray(),new JSONArray(),new JSONArray()};
        for(int id:new int[]{381,382,383})lists[4].put(item(id,5,new JSONArray()));for(int id=2155;id<=2158;id++)lists[5].put(item(id,1,new JSONArray()));
        // Three usable low-tier sets, one for each planet; no fabricated high-tier requirement bypass.
        for(ItemTemplate t:Manager.ITEM_TEMPLATES)if(t.id<100&&t.type>=0&&t.type<=4&&t.strRequire<=2000){JSONArray options=RewardCatalog.defaults(t.id);options.put(new JSONArray().put(107).put(8)).put(new JSONArray().put(102).put(8)).put(new JSONArray().put(101).put(40)).put(new JSONArray().put(30).put(0));lists[6].put(item(t.id,1,options).put("match_gender",true));}
        for(int i=0;i<codes.length;i++){JSONObject bundle=new JSONObject().put("kind","BUNDLE").put("rewards",lists[i]);OnlineRewards.execute(c,"INSERT IGNORE INTO live_admin_packages(id,name,reward) VALUES(?,?,?)","starter_"+(i+1),"Tân thủ "+(i+1)+" — "+codes[i],bundle.toString());OnlineRewards.execute(c,"INSERT IGNORE INTO live_admin_codes(code,max_uses,expires_ms,reward) VALUES(?,2147483647,4102444800000,?)",codes[i],bundle.toString());}
    }
}
