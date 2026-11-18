package nro.progression;
import java.sql.*;
import java.util.*;
import org.json.*;
import nro.server.Manager;
import nro.models.item.*;
public final class KaizenTransactionsTest {
 public static void main(String[] args)throws Exception {
  check(KaizenTransactions.sameInventory(new JSONArray("[{\"temp_id\":-1,\"quantity\":0,\"create_time\":0,\"option\":[]}]"),new JSONArray("[{\"temp_id\":-1,\"quantity\":1,\"create_time\":123,\"option\":[]}]")),"empty slot metadata should not block crafting");
  check(!KaizenTransactions.sameInventory(new JSONArray("[{\"temp_id\":80,\"quantity\":1}]"),new JSONArray("[{\"temp_id\":80,\"quantity\":2}]")),"real quantity mismatch must remain blocked");
  Class.forName("com.mysql.cj.jdbc.Driver");
  check(KaizenTransactions.sameInventory(new JSONArray().put(item(-1,0)).put(item(80,3)),new JSONArray().put(item(80,3)).put(item(-1,1))),"native inventory compaction is harmless");
  check(!KaizenTransactions.sameInventory(new JSONArray().put(item(80,3)).put(item(80,3)),new JSONArray().put(item(80,3)).put(item(-1,0))),"duplicate item cannot match the same slot twice");
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture",0));
  for(int i=0;i<104;i++){var t=new ItemTemplate();t.id=(short)i;t.name="fixture";t.description="";t.type=27;Manager.ITEM_TEMPLATES.add(t);}
  var bindings=new EnumMap<KaizenRecipes,KaizenForge.Binding>(KaizenRecipes.class);int id=80;
  for(var recipe:KaizenRecipes.values()){Manager.ITEM_TEMPLATES.get(id+1).type=5;bindings.put(recipe,new KaizenForge.Binding(id,id+1,new JSONArray("[[50,35]]")));id+=2;}
  var forge=new KaizenForge(bindings);
  try(Connection c=DriverManager.getConnection(args[0],"root","")) {
   KaizenTransactions.initialize(c);
   if(args[1].equals("reopen")) {
    c.setAutoCommit(false);
    for(var recipe:KaizenRecipes.values())check(KaizenTransactions.craft(c,forge,100+recipe.ordinal(),77,"craft-one",recipe,"[]","[]").replay(),"receipt survived restart");
    c.commit();System.out.println("PASS seven Kaizen crafting receipts survive database restart");return;
   }
   try(var q=c.createStatement()){q.executeUpdate("ALTER TABLE player ADD COLUMN data_inventory LONGTEXT NOT NULL DEFAULT '[0,0,0,0]'");}
   for(var recipe:KaizenRecipes.values()) {
    String bag=new JSONArray().put(item(bindings.get(recipe).fragmentId(),recipe.fragments)).put(item(-1,0)).toString();
    String money=new JSONArray().put(recipe.gold+100).put(123).put(456).put(789).toString();
    try(var q=c.prepareStatement("INSERT INTO player(id,data_point,items_bag,items_body,data_inventory) VALUES(?,'[]',?,'[]',?)")) {
     q.setLong(1,100+recipe.ordinal());q.setString(2,bag);q.setString(3,money);q.executeUpdate();
    }
    c.setAutoCommit(false);
    var plan=KaizenTransactions.craft(c,forge,100+recipe.ordinal(),77,"craft-one",recipe,bag,money);
    check(plan.money().getLong(0)==100&&plan.money().getInt(1)==123&&plan.money().getInt(2)==456,"only gold debited");
    c.rollback();
    var retry=KaizenTransactions.craft(c,forge,100+recipe.ordinal(),77,"craft-one",recipe,bag,money);check(!retry.replay(),"rollback did not consume request");c.commit();
    var twice=KaizenTransactions.craft(c,forge,100+recipe.ordinal(),77,"craft-one",recipe,bag,money);
    check(twice.replay()&&twice.money().getLong(0)==100,"duplicate did not charge again");c.commit();
    try{KaizenTransactions.craft(c,forge,100+recipe.ordinal(),77,"stale",recipe,bag,money);throw new AssertionError("stale bag accepted");}
    catch(SQLException expected){check("40001".equals(expected.getSQLState()),"stale state");c.rollback();}
    c.setAutoCommit(true);
   }
   System.out.println("PASS seven actual MariaDB crafts: atomic bag/gold/receipt, rollback, retry once, stale inventory refused, gems/rubies preserved");
  }
 }
 static JSONObject item(int id,int qty){return new JSONObject().put("temp_id",id).put("quantity",qty).put("option",new JSONArray()).put("create_time",123);}
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
