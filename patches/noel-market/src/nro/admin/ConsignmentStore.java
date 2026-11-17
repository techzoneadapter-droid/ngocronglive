package nro.admin;
import java.sql.*;
import java.util.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.item.*;
import nro.models.player.Player;
import nro.services.ItemService;
/** Durable escrow. Listing and the acting player's inventory commit together. */
public final class ConsignmentStore {
 public record Listing(long id,long owner,int currency,int price,boolean sold,Item item,String seller,int stockCategory){}
 public static final int GOLD=0,INGOT=1,RUBY=2;
 private static boolean ready;
 public static synchronized void init()throws SQLException{
  if(ready)return;
  try(Connection c=DBHika.getConnection();Statement s=c.createStatement()){
   s.executeUpdate("CREATE TABLE IF NOT EXISTS nero_consignment (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,owner_id BIGINT NOT NULL,currency TINYINT NOT NULL,price INT NOT NULL,sold TINYINT NOT NULL DEFAULT 0,item_json LONGTEXT NOT NULL,seller VARCHAR(64) NOT NULL,created_ms BIGINT NOT NULL,INDEX(owner_id),INDEX(currency,sold)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.executeUpdate("CREATE TABLE IF NOT EXISTS nero_consignment_imported (legacy_id BIGINT PRIMARY KEY,listing_id BIGINT NOT NULL) ENGINE=InnoDB");
   // Existing escrow is imported once, never truncated or paid twice on restart.
   c.setAutoCommit(false);
   try(ResultSet rows=s.executeQuery("SELECT old.* FROM consignment_shop old LEFT JOIN nero_consignment_imported done ON done.legacy_id=old.id WHERE done.legacy_id IS NULL FOR UPDATE")){
    while(rows.next()){
     Item item=ItemService.gI().createNewItem(rows.getShort("item_id"),rows.getInt("quantity"));
     item.itemOptions.clear();JSONArray options=new JSONArray(rows.getString("item_options"));for(int n=0;n<options.length();n++){JSONArray option=options.getJSONArray(n);item.itemOptions.add(new ItemOption(option.getInt(0),option.getInt(1)));}
     item.createTime=rows.getLong("time_consign");
     int oldRuby=rows.getInt("gem"),oldIngot=rows.getInt("gold");
     if(oldRuby<0||oldIngot<0||(oldRuby>0&&oldIngot>0)||(oldRuby==0&&oldIngot==0))
      throw new SQLException("Legacy listing price requires review: "+rows.getLong("id"));
     try(PreparedStatement q=c.prepareStatement("INSERT INTO nero_consignment(owner_id,currency,price,sold,item_json,seller,created_ms) VALUES(?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
      q.setLong(1,rows.getLong("consignor_id"));q.setInt(2,oldRuby>0?RUBY:INGOT);q.setInt(3,oldRuby>0?oldRuby:oldIngot);q.setInt(4,rows.getInt("sold"));q.setString(5,encode(item).toString());q.setString(6,rows.getString("consignor_name"));q.setLong(7,item.createTime);q.executeUpdate();
      try(ResultSet key=q.getGeneratedKeys();PreparedStatement mark=c.prepareStatement("INSERT INTO nero_consignment_imported VALUES(?,?)")){key.next();mark.setLong(1,rows.getLong("id"));mark.setLong(2,key.getLong(1));mark.executeUpdate();}
     }
    }c.commit();
   }catch(Exception error){c.rollback();throw error;}
  }ready=true;
 }
 public static long fee(long price){if(price<1)throw new IllegalArgumentException("Giá phải lớn hơn 0");return (price+19)/20;}
 public static String currencyName(int mode){return switch(mode){case GOLD->"vàng";case INGOT->"thỏi vàng";case RUBY->"hồng ngọc";default->throw new IllegalArgumentException("Loại tiền không hợp lệ");};}
 public static boolean allowed(Item item) {
  if(item==null || !item.isNotNullItem() || item.quantity<1) return false;
  return item.itemOptions.stream().noneMatch(option -> option.optionTemplate.id==30);
 }

 private static void requireActivated(Player player) {
  if(player.getSession()==null || !player.getSession().actived)
   throw new IllegalArgumentException("Cần kích hoạt tài khoản để giao dịch ký gửi");
 }
 static JSONObject encode(Item i){JSONArray opts=new JSONArray();for(ItemOption o:i.itemOptions)opts.put(new JSONArray().put(o.optionTemplate.id).put(o.param).put(o.activeCard));return new JSONObject().put("temp_id",i.template.id).put("quantity",i.quantity).put("create_time",i.createTime).put("option",opts);}
 private static Item decode(String raw){JSONObject j=new JSONObject(raw);Item i=ItemService.gI().createNewItem((short)j.getInt("temp_id"),j.getInt("quantity"));i.createTime=j.getLong("create_time");i.itemOptions.clear();JSONArray a=j.getJSONArray("option");for(int n=0;n<a.length();n++){JSONArray o=a.getJSONArray(n);ItemOption io=new ItemOption(o.getInt(0),o.getInt(1));io.activeCard=(byte)o.optInt(2,0);i.itemOptions.add(io);}return i;}
 static List<Item> copyBag(Player p){List<Item>b=new ArrayList<>();for(Item i:p.inventory.itemsBag)b.add(ItemService.gI().copyItem(i));return b;}
 static String bagJson(List<Item>b){JSONArray a=new JSONArray();for(Item i:b)a.put(i.isNotNullItem()?encode(i):new JSONObject().put("temp_id",-1).put("quantity",0).put("create_time",0).put("option",new JSONArray()));return a.toString();}
 private static void save(Connection c,Player p,List<Item>b,long gold,int ruby)throws SQLException{
  try(PreparedStatement q=c.prepareStatement("UPDATE player SET items_bag=?,data_inventory=? WHERE id=? AND account_id=?")){
   q.setString(1,bagJson(b));q.setString(2,new JSONArray().put(gold).put(p.inventory.gem).put(ruby).put(p.inventory.goldLimit).toString());q.setLong(3,p.id);q.setInt(4,p.getSession().userId);if(q.executeUpdate()!=1)throw new SQLException("Player not saved");
  }
 }
 private static void install(Player p,List<Item>b,long gold,int ruby){p.inventory.itemsBag=b;p.inventory.gold=gold;p.inventory.ruby=ruby;}
 public static List<Listing> list(int mode,long owner)throws SQLException{
  init();List<Listing> rows=new ArrayList<>();try(Connection c=DBHika.getConnection();PreparedStatement q=c.prepareStatement("""
   SELECT listings.* FROM (
     (SELECT * FROM nero_consignment WHERE currency=? AND sold=0
       ORDER BY CASE WHEN owner_id=-1 AND JSON_EXTRACT(item_json,'$.stock_category') BETWEEN 8 AND 11
                     THEN 0 ELSE 1 END,id DESC LIMIT 2400)
     UNION
     (SELECT * FROM nero_consignment WHERE currency=? AND owner_id=?)
   ) listings ORDER BY CASE WHEN owner_id=-1 AND JSON_EXTRACT(item_json,'$.stock_category') BETWEEN 8 AND 11
                           THEN 0 ELSE 1 END,id DESC
   """)){
   q.setInt(1,mode);q.setInt(2,mode);q.setLong(3,owner);try(ResultSet r=q.executeQuery()){while(r.next())rows.add(row(r));}
  }return rows;
 }
 private static Listing row(ResultSet r)throws SQLException{return new Listing(r.getLong("id"),r.getLong("owner_id"),r.getInt("currency"),r.getInt("price"),r.getBoolean("sold"),decode(r.getString("item_json")),r.getString("seller"),new JSONObject(r.getString("item_json")).optInt("stock_category",-1));}
 private static Listing lock(Connection c,long id)throws SQLException{try(PreparedStatement q=c.prepareStatement("SELECT * FROM nero_consignment WHERE id=? FOR UPDATE")){q.setLong(1,id);try(ResultSet r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Món này không còn được ký gửi");return row(r);}}}
 public static long post(Player p,int slot,Item expected,int quantity,int mode,int price)throws SQLException{
  currencyName(mode);if(quantity<1||price<2||price>2000000000)throw new IllegalArgumentException("Số lượng phải dương, giá từ 2 đến 2 tỷ");
  synchronized(p){
   requireActivated(p);
   if(slot<0||slot>=p.inventory.itemsBag.size()||p.inventory.itemsBag.get(slot)!=expected||!allowed(expected)||quantity>expected.quantity)throw new IllegalArgumentException("Vật phẩm đã đổi hoặc không được phép ký gửi");
   List<Item>b=copyBag(p);Item escrow=ItemService.gI().copyItem(expected);escrow.quantity=quantity;b.get(slot).quantity-=quantity;if(b.get(slot).quantity==0)b.set(slot,ItemService.gI().createItemNull());init();
   try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{
    try(PreparedStatement q=c.prepareStatement("SELECT id FROM player WHERE id=? FOR UPDATE")){q.setLong(1,p.id);try(ResultSet r=q.executeQuery()){if(!r.next())throw new SQLException("Player missing");}}
    try(PreparedStatement q=c.prepareStatement("SELECT COUNT(*) FROM nero_consignment WHERE owner_id=?")){q.setLong(1,p.id);try(ResultSet r=q.executeQuery()){r.next();if(r.getInt(1)>=40)throw new IllegalArgumentException("Tối đa 40 món đang gửi/chờ nhận tiền");}}
    long id;try(PreparedStatement q=c.prepareStatement("INSERT INTO nero_consignment(owner_id,currency,price,item_json,seller,created_ms) VALUES(?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){q.setLong(1,p.id);q.setInt(2,mode);q.setInt(3,price);q.setString(4,encode(escrow).toString());q.setString(5,p.name);q.setLong(6,System.currentTimeMillis());q.executeUpdate();try(ResultSet r=q.getGeneratedKeys()){r.next();id=r.getLong(1);}}
    save(c,p,b,p.inventory.gold,p.inventory.ruby);c.commit();install(p,b,p.inventory.gold,p.inventory.ruby);return id;
   }catch(Exception e){c.rollback();throw e;}}
  }
 }
 public static void act(Player p,long id,int action,int mode,int quotedPrice)throws SQLException{
  synchronized(p){if(action==3) requireActivated(p);init();try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{
   Listing l=lock(c,id);if(l.currency()!=mode)throw new IllegalArgumentException("Loại tiền đã thay đổi");List<Item>b=copyBag(p);long gold=p.inventory.gold;int ruby=p.inventory.ruby;
   if(action==3){
    if(l.owner()==p.id||l.sold()||quotedPrice!=l.price())throw new IllegalArgumentException("Giá/món đã thay đổi hoặc đây là đồ của bạn");
    if(mode==GOLD){if(gold<l.price())throw new IllegalArgumentException("Không đủ vàng");gold-=l.price();}
    else if(mode==RUBY){if(ruby<l.price())throw new IllegalArgumentException("Không đủ hồng ngọc");ruby-=l.price();}
    else{long left=l.price();for(int n=0;n<b.size()&&left>0;n++){Item i=b.get(n);if(i.isNotNullItem()&&i.template.id==457){int take=(int)Math.min(left,i.quantity);i.quantity-=take;left-=take;if(i.quantity==0)b.set(n,ItemService.gI().createItemNull());}}if(left>0)throw new IllegalArgumentException("Không đủ thỏi vàng 25M");}
    if(!AdminGrantItems.insert(b,ItemService.gI().copyItem(l.item())))throw new IllegalArgumentException("Túi không đủ chỗ");
    try(PreparedStatement q=c.prepareStatement(l.owner()==RubyStock.OWNER?"DELETE FROM nero_consignment WHERE id=? AND sold=0":"UPDATE nero_consignment SET sold=1 WHERE id=? AND sold=0")){q.setLong(1,id);if(q.executeUpdate()!=1)throw new SQLException("Already sold");}if(l.owner()==RubyStock.OWNER && (mode==RUBY || mode==INGOT && l.stockCategory()>=12)) {
        if(l.stockCategory()>=8 && l.stockCategory()<=11) RubyRestock.insert(c,l.stockCategory());
        else RubyRestock.schedule(c,l.id(),System.currentTimeMillis(),l.stockCategory());
    }
   }else{
    if(l.owner()!=p.id||(action==1&&l.sold())||(action==2&&!l.sold())||(action!=1&&action!=2))throw new IllegalArgumentException("Không được nhận/hủy món này");
    if(action==1){if(!AdminGrantItems.insert(b,ItemService.gI().copyItem(l.item())))throw new IllegalArgumentException("Túi không đủ chỗ; đồ vẫn ở ký gửi");}
    else{long net=l.price()-fee(l.price());if(mode==GOLD){gold=Math.addExact(gold,net);if(gold>p.inventory.getGoldLimit())throw new IllegalArgumentException("Vượt giới hạn vàng; tiền vẫn ở ký gửi");}else if(mode==RUBY){long next=(long)ruby+net;if(next>Integer.MAX_VALUE)throw new IllegalArgumentException("Vượt giới hạn hồng ngọc");ruby=(int)next;}else{Item pay=ItemService.gI().createNewItem((short)457,(int)net);if(!AdminGrantItems.insert(b,pay))throw new IllegalArgumentException("Túi không đủ chỗ nhận thỏi vàng");}}
    try(PreparedStatement q=c.prepareStatement("DELETE FROM nero_consignment WHERE id=?")){q.setLong(1,id);q.executeUpdate();}
   }
   save(c,p,b,gold,ruby);c.commit();install(p,b,gold,ruby);
  }catch(Exception e){c.rollback();throw e;}}}
 }
}
