package nro.progression;
import java.nio.file.*;
import java.sql.*;
import org.json.*;
/** Offline fixture only; never reads live server credentials or connects outside loopback. */
public final class SeedLocalUpgrade {
 public static void main(String[] args)throws Exception {
  Path lab=Path.of(args[0]);var creds=new JSONObject(Files.readString(lab.resolve("local-data/credentials.json")));
  try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:15484/ngocrong?useSSL=false&allowPublicKeyRetrieval=true","root",creds.getString("root"))) {
   c.setAutoCommit(false);try {
    JSONArray bag,body,money;
    try(var q=c.createStatement();var r=q.executeQuery("SELECT items_bag,items_body,data_inventory FROM player WHERE id=6 AND account_id=3 FOR UPDATE")) {
     if(!r.next())throw new SQLException("Local fixture missing");bag=new JSONArray(r.getString(1));body=new JSONArray(r.getString(2));money=new JSONArray(r.getString(3));
    }
    try(var q=c.createStatement();var r=q.executeQuery("SELECT equipment_id FROM nero_kaizen_catalog ORDER BY equipment_id")) {
     int slot=13;while(r.next()) {int id=r.getInt(1);if(body.getJSONObject(slot).getInt("temp_id")<0)for(int i=0;i<bag.length();i++)if(bag.getJSONObject(i).getInt("temp_id")==id){body.put(slot,bag.getJSONObject(i));bag.put(i,empty());break;}slot++;}
    }
    for(int id:new int[]{220,221,222,223,224}) {
     boolean found=false;for(int i=0;i<bag.length();i++)if(bag.getJSONObject(i).getInt("temp_id")==id){bag.getJSONObject(i).put("quantity",999);found=true;break;}
     if(!found) {int slot=-1;for(int i=0;i<bag.length();i++)if(bag.getJSONObject(i).getInt("temp_id")<0){slot=i;break;}
      if(slot<0)throw new SQLException("No local fixture space");bag.put(slot,new JSONObject().put("temp_id",id).put("quantity",999).put("create_time",System.currentTimeMillis()).put("option",new JSONArray()));}
    }
    money.put(0,100_000_000_000L);
    try(var q=c.prepareStatement("UPDATE player SET items_bag=?,items_body=?,data_inventory=? WHERE id=6 AND account_id=3")){q.setString(1,bag.toString());q.setString(2,body.toString());q.setString(3,money.toString());q.executeUpdate();}
    c.commit();System.out.println("LOCAL_ONLY_UPGRADE_MATERIALS_READY player6 five_stones999 gold100B");
   }catch(Exception error){c.rollback();throw error;}
  }
 }
 private static JSONObject empty(){return new JSONObject().put("temp_id",-1).put("quantity",0).put("create_time",0).put("option",new JSONArray());}
}
