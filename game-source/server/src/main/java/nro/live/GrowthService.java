package nro.live;
import java.sql.*;
import java.util.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.player.*;
import nro.models.item.*;
import nro.models.npc.specialnpc.MabuEgg;
import nro.models.map.ItemMap;
import nro.services.*;
import nro.admin.OnlineRewards;

public final class GrowthService {
    private static volatile JSONObject config=new JSONObject();
    public static JSONObject defaults(){return new JSONObject().put("mabu_drop",25).put("mabu_broken",30).put("buff_seconds",3600).put("buff_price",100).put("max_exp",100).put("vip",new JSONArray()).put("zalo_url","");}
    public static JSONObject settings(){return new JSONObject(config.toString());}
    public static void reload()throws SQLException{try(Connection c=DBHika.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT payload FROM live_gameplay_config WHERE id=1")){JSONObject next=defaults();if(r.next()){JSONObject x=new JSONObject(r.getString(1));for(String k:x.keySet())next.put(k,x.get(k));}config=next;}}
    public static JSONObject validate(JSONObject in){JSONObject out=defaults();for(String k:List.of("mabu_drop","mabu_broken","buff_seconds","buff_price","max_exp")){long n=in.getLong(k);long max=k.equals("buff_seconds")?604800:k.equals("buff_price")?1000000000:100;if(n<0||n>max||k.equals("buff_seconds")&&n<60||k.equals("max_exp")&&n<2)throw new IllegalArgumentException("Cấu hình "+k+" ngoài giới hạn");out.put(k,n);}String link=in.optString("zalo_url");if(!link.isEmpty()&&!link.matches("https://zalo\\.me/[a-zA-Z0-9/_-]+"))throw new IllegalArgumentException("Link nhóm phải là https://zalo.me/...");out.put("zalo_url",link);
        JSONArray vip=in.optJSONArray("vip");if(vip==null)vip=new JSONArray();if(vip.length()>20)throw new IllegalArgumentException("Tối đa 20 mẫu đệ VIP");Set<String> keys=new HashSet<>();for(int i=0;i<vip.length();i++){JSONObject v=vip.getJSONObject(i);String id=v.getString("id");if(!id.matches("VIP_[A-Z0-9_]{1,24}")||!keys.add(id))throw new IllegalArgumentException("ID VIP phải duy nhất và bắt đầu VIP_");if(v.getString("name").isBlank()||v.getString("name").length()>40)throw new IllegalArgumentException("Tên VIP không hợp lệ");if(v.getLong("price")<1||v.getLong("price")>Integer.MAX_VALUE)throw new IllegalArgumentException("Giá VIP phải lớn hơn 0 hồng ngọc");for(int k=0;k<3;k++){int part=v.getJSONArray("outfit").getInt(k);var p=nro.models.PartManager.getInstance().find(part);if(p==null||p.getType()!=k)throw new IllegalArgumentException("Part VIP không đúng loại đầu/thân/chân");} }out.put("vip",vip);return out;}
    public static JSONObject vip(String id){JSONArray a=config.optJSONArray("vip");if(a!=null)for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("id").equals(id))return a.getJSONObject(i);return null;}
    public static void load(Player p)throws SQLException{try(Connection c=DBHika.getConnection();PreparedStatement s=c.prepareStatement("SELECT buffs,leases FROM live_player_growth WHERE player_id=?")){s.setLong(1,p.id);try(ResultSet r=s.executeQuery()){if(r.next()){p.liveBuffs=new JSONObject(r.getString(1));p.liveLeases=new JSONObject(r.getString(2));}}}}
    public static void persist(Connection c,Player p,JSONObject buffs,JSONObject leases)throws SQLException{OnlineRewards.execute(c,"INSERT INTO live_player_growth(player_id,buffs,leases) VALUES(?,?,?) ON DUPLICATE KEY UPDATE buffs=VALUES(buffs),leases=VALUES(leases)",p.id,buffs.toString(),leases.toString());}
    private static long nativeExpiry(Player p,int id){return id==2155?p.charms.tdTriTue:id==2156?p.charms.tdTriTue3:id==2157?p.charms.tdTriTue4:0;}
    public static long calculate(Player player,long raw){Player owner=player.isPet?((Pet)player).master:player;long now=System.currentTimeMillis(),percent=200;
        for(int value:player.nPoint.tlTNSM)percent+=Math.max(0,value);
        if(player.nPoint.buffExpSatellite)percent+=20;if(player.cFlag!=0)percent+=player.cFlag==8?10:5;

        if(player.isPet){percent+=Math.max(0,owner.nPoint.tlTNSMPet);if(owner.charms.tdDeTu>now)percent+=200;}
        for(int id=2155;id<=2158;id++)if(Math.max(owner.liveBuffs.optLong(Integer.toString(id)),nativeExpiry(player,id))>now)percent+=(id-2154)*100L;
        percent+=nro.admin.LiveAdmin.eventBonus(owner);
        percent=Math.min(config.optInt("max_exp",100)*100L,percent);
        return Math.max(1,Math.min(Integer.MAX_VALUE,raw>Long.MAX_VALUE/Math.max(1,percent)?Integer.MAX_VALUE:raw*percent/100));
    }
    public static boolean use(Player p,Item item){int id=item.template.id;if(id!=568&&id!=2154&&(id<2155||id>2158))return false;
        synchronized(p){try{
            if(id==2154)throw new IllegalArgumentException("Trứng Ma Bư đã hỏng, không thể ấp. Có thể bỏ vật phẩm này.");
            JSONArray bag=OnlineRewards.items(p.inventory.itemsBag);int index=p.inventory.itemsBag.indexOf(item);if(index<0||item.quantity<1)throw new IllegalArgumentException("Vật phẩm không còn trong hành trang");
            JSONObject old=bag.getJSONObject(index);if(item.quantity==1)bag.put(index,new JSONObject().put("temp_id",-1).put("quantity",0).put("create_time",0).put("option",new JSONArray()));else old.put("quantity",item.quantity-1);
            OnlineRewards.compact(bag);List<Item> next=OnlineRewards.loadItems(bag);JSONObject buffs=new JSONObject(p.liveBuffs.toString());MabuEgg egg=null;
            if(id==568){if(p.mabuEgg!=null)throw new IllegalArgumentException("Đang ấp một trứng; chưa dùng trứng mới");egg=new MabuEgg(p,System.currentTimeMillis(),nro.companion.CompanionService.incubation("MABU"));}
            else buffs.put(Integer.toString(id),Balance.extend(System.currentTimeMillis(),Math.max(buffs.optLong(Integer.toString(id)),nativeExpiry(p,id)),config.getLong("buff_seconds")));
            try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{OnlineRewards.execute(c,"UPDATE player SET items_bag=? WHERE id=?",bag.toString(),p.id);if(egg!=null)OnlineRewards.execute(c,"UPDATE player SET data_mabu_egg=? WHERE id=?",new JSONObject().put("species","MABU").put("create_time",egg.lastTimeCreate).put("time_done",egg.timeDone).toString(),p.id);persist(c,p,buffs,p.liveLeases);c.commit();}catch(Exception e){c.rollback();throw e;}}
            p.inventory.itemsBag=next;p.liveBuffs=buffs;if(egg!=null){p.mabuEgg=egg;egg.sendMabuEgg();Service.gI().sendThongBao(p,"Bắt đầu ấp trứng Ma Bư nguyên vẹn. Trứng đang ấp không thể giao dịch.");}else Service.gI().sendThongBao(p,"Đã dùng "+item.template.name+". Cùng loại chỉ nối thời gian; khác loại cộng phần tăng.");InventoryService.gI().sendItemBags(p);
        }catch(Exception e){Service.gI().sendThongBao(p,e instanceof IllegalArgumentException?e.getMessage():"Chưa dùng được vật phẩm; chưa trừ số lượng");}return true;}
    }
    public static void brolyDrop(nro.models.boss.Boss boss,Player killer){if(killer==null)return;if(killer.isPet)killer=((Pet)killer).master;if(killer==null||killer.getSession()==null)return;synchronized(boss){if(boss.liveEggRewarded)return;boss.liveEggRewarded=true;}if(nro.utils.Util.isTrue(config.getInt("mabu_drop"),100)){boolean broken=nro.utils.Util.isTrue(config.getInt("mabu_broken"),100);ItemMap drop=new ItemMap(boss.zone,broken?2154:568,1,boss.location.x,boss.location.y,killer.id);if(broken)drop.options.add(new ItemOption(30,0));Service.gI().dropItemMap(boss.zone,drop);Service.gI().sendThongBao(killer,broken?"Broly rơi trứng Ma Bư hỏng":"Broly rơi trứng Ma Bư nguyên vẹn, nhặt để dùng hoặc giao dịch");}}
    public static void buyBuff(Player p,int multiplier){synchronized(p){try{if(multiplier<2||multiplier>5)throw new IllegalArgumentException("Chọn x2 đến x5");long cost=(long)config.getInt("buff_price")*(multiplier-1);if(p.inventory.ruby<cost)throw new IllegalArgumentException("Không đủ hồng ngọc");JSONArray bag=OnlineRewards.items(p.inventory.itemsBag),box=OnlineRewards.items(p.inventory.itemsBox);OnlineRewards.add(bag,box,new JSONObject().put("item_id",2153+multiplier).put("quantity",1).put("options",new JSONArray()));OnlineRewards.compact(bag);List<Item> a=OnlineRewards.loadItems(bag),b=OnlineRewards.loadItems(box);JSONArray money=new JSONArray().put(p.inventory.gold).put(p.inventory.gem).put(p.inventory.ruby-cost).put(p.inventory.goldLimit);try(Connection c=DBHika.getConnection()){OnlineRewards.execute(c,"UPDATE player SET items_bag=?,items_box=?,data_inventory=? WHERE id=?",bag.toString(),box.toString(),money.toString(),p.id);}p.inventory.ruby-=cost;p.inventory.itemsBag=a;p.inventory.itemsBox=b;Service.gI().sendMoney(p);InventoryService.gI().sendItemBags(p);Service.gI().sendThongBao(p,"Đã mua bùa x"+multiplier+". Dùng từ hành trang; rương nếu túi đầy.");}catch(Exception e){Service.gI().sendThongBao(p,e instanceof IllegalArgumentException?e.getMessage():"Chưa mua được; chưa trừ tiền");}}}
    public static void guide(Player p){if(!p.liveGuideSent){p.liveGuideSent=true;Service.gI().sendThongBao(p,"Chat tanthu để xem 7 mã quà; nhập ở Nhận quà hoặc code TEST. Chat khuup chọn khu luyện, vip xem đệ VIP, santrung tìm Broly, buff xem bùa.");}}
    public static boolean chat(Player p,String text){if(p.getSession()==null)return false;String[] a=text.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if(a[0].equals("vip")){JSONArray profiles=config.getJSONArray("vip");List<JSONObject> list=new ArrayList<>();for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).optBoolean("enabled",true))list.add(profiles.getJSONObject(i));if(list.isEmpty()){Service.gI().sendThongBao(p,"Chủ server chưa mở bán đệ VIP. Đệ VIP chỉ mua bằng hồng ngọc.");return true;}String[] buttons=new String[list.size()+1];for(int i=0;i<list.size();i++)buttons[i]=list.get(i).getString("name")+"\n"+list.get(i).getLong("price")+" hồng ngọc";buttons[list.size()]="Đóng";new nro.dialog.MenuDialog("Đệ VIP +70% tổng chỉ số hợp thể. Giữ đồ/kỹ năng/chỉ số; hãy tách hợp thể trước khi mua.",buttons,new nro.dialog.MenuRunable(){public void run(){int i=getIndexSelected();if(i>=0&&i<list.size())Service.gI().sendThongBao(p,nro.admin.LiveAdmin.buyVip(p,list.get(i).getString("id")));}}).show(p);return true;}
        if(a[0].equals("nhanqua")){Service.gI().sendThongBao(p,nro.admin.LiveAdmin.collect(p));return true;}
        if(a[0].equals("code")&&a.length==2){nro.services.GiftService.gI().use(p,a[1]);return true;}
        if(a[0].equals("muavip")&&a.length==2){Service.gI().sendThongBao(p,nro.admin.LiveAdmin.buyVip(p,a[1].toUpperCase(Locale.ROOT)));return true;}
        if(a[0].equals("muabua")){try{buyBuff(p,Integer.parseInt(a[1]));}catch(Exception e){Service.gI().sendThongBao(p,"Dùng muabua 2 / 3 / 4 / 5; trả bằng hồng ngọc");}return true;}
        if(a[0].equals("buff")){StringBuilder s=new StringBuilder("Nền map thường x2; khác nguồn cộng phần tăng. Tổng hiện tại x"+(calculate(p,1000)/1000.0)+"\n");long now=System.currentTimeMillis();for(String key:p.liveBuffs.keySet())if(p.liveBuffs.optLong(key)>now)s.append(ItemService.gI().getTemplate(Integer.parseInt(key)).name).append(": ").append((p.liveBuffs.getLong(key)-now)/1000).append(" giây\n");Service.gI().sendThongBao(p,s.toString());return true;}
        if(a[0].equals("tanthu")){Service.gI().sendThongBao(p,"Nhập tại Nhận quà: TANTHUVANG, TANTHUNGOC, TANTHUTHOIVANG, TANTHUPHUKIEN, TANTHUBUFF, TANTHUTIEMNANG, TANTHU8SAO. Mỗi tài khoản nhận một lần.");return true;}
        if(a[0].equals("zalo")){Service.gI().sendThongBao(p,config.optString("zalo_url").isEmpty()?"Chủ server chưa cấu hình nhóm Zalo":config.getString("zalo_url"));return true;}
        return TrainingWorld.chat(p,text);
    }
}
