package nro.admin;
import java.sql.*;
import java.util.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.item.*;
import nro.models.player.*;
import nro.services.*;
import nro.companion.CompanionService;

/** Transactional staging: the live object is changed only after SQL commits. Caller holds Player monitor. */
public final class OnlineRewards {
    public static JSONArray items(List<Item> list){JSONArray a=new JSONArray();for(Item i:list){JSONArray opts=new JSONArray();if(i.isNotNullItem())for(ItemOption o:i.itemOptions)opts.put(new JSONArray().put(o.optionTemplate.id).put(o.param));a.put(new JSONObject().put("temp_id",i.isNotNullItem()?i.template.id:-1).put("quantity",i.isNotNullItem()?i.quantity:0).put("create_time",i.isNotNullItem()?i.createTime:0).put("option",opts));}return a;}
    public static List<Item> loadItems(JSONArray a){List<Item> list=new ArrayList<>();for(int k=0;k<a.length();k++){JSONObject o=a.getJSONObject(k);Item i=o.getInt("temp_id")==-1?ItemService.gI().createItemNull():ItemService.gI().createNewItem((short)o.getInt("temp_id"),o.getInt("quantity"));i.createTime=o.getLong("create_time");if(i.isNotNullItem()){i.itemOptions.clear();for(int j=0;j<o.getJSONArray("option").length();j++){JSONArray opt=o.getJSONArray("option").getJSONArray(j);i.itemOptions.add(new ItemOption(opt.getInt(0),opt.getInt(1)));}}list.add(i);}return list;}
    public static JSONArray points(Player p){NPoint n=p.nPoint;return new JSONArray().put(0).put(n.mp).put(n.mpg).put(n.critg).put(n.limitPower).put(n.stamina).put(n.hp).put(n.defg).put(n.tiemNang).put(n.maxStamina).put(n.dameg).put(n.power).put(n.hpg);}
    public static void execute(Connection c,String sql,Object... values)throws SQLException{try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)s.setObject(i+1,values[i]);s.executeUpdate();}}
    public static void add(JSONArray bag,JSONArray box,JSONObject reward){JSONObject item=new JSONObject().put("temp_id",reward.getInt("item_id")).put("quantity",reward.getInt("quantity")).put("option",reward.getJSONArray("options")).put("create_time",System.currentTimeMillis());for(JSONArray target:new JSONArray[]{bag,box})for(int i=0;i<target.length();i++)if(target.getJSONObject(i).getInt("temp_id")==-1){target.put(i,item);return;}throw new IllegalArgumentException("Hành trang và rương đầy; hãy dọn chỗ rồi nhập lại mã, chưa mất lượt");}
    public static Runnable stage(Connection c,Player p,String parent)throws Exception{
        JSONArray money=new JSONArray().put(p.inventory.gold).put(p.inventory.gem).put(p.inventory.ruby).put(p.inventory.goldLimit),point=points(p),bag=items(p.inventory.itemsBag),box=items(p.inventory.itemsBox);List<String> ids=new ArrayList<>();String petSpecies=null;boolean giveEgg=false;
        try(PreparedStatement s=c.prepareStatement("SELECT id,payload FROM live_admin_actions WHERE (id=? OR id LIKE ?) AND status='PENDING' ORDER BY id FOR UPDATE")){
            s.setString(1,parent);s.setString(2,parent+"\\_%");try(ResultSet rs=s.executeQuery()){while(rs.next()){
                JSONObject r=new JSONObject(rs.getString("payload"));String kind=r.getString("kind");
                switch(kind){
                    case "CURRENCY"->{int i=r.getString("currency").equals("gold")?0:r.getString("currency").equals("gem")?1:2;long v=r.optString("mode","ADD").equals("AT_LEAST")?Math.max(money.getLong(i),r.getLong("amount")):Math.addExact(money.getLong(i),r.getLong("amount"));if(v<0||v>(i==0?Inventory.LIMIT_GOLD:Integer.MAX_VALUE))throw new IllegalArgumentException("Số dư sau nhận quà vượt trần; chưa mất lượt mã");money.put(i,v);}
                    case "ITEM"->add(bag,box,r);
                    case "POINTS"->{int level=Math.max(point.getInt(4),r.getInt("limit_index"));var limit=nro.power.PowerLimitManager.getInstance().get(level);point.put(4,level).put(8,Math.max(point.getLong(8),r.getLong("potential"))).put(11,Math.max(point.getLong(11),r.getLong("power")));if(r.getBoolean("max_base")){point.put(12,Math.max(point.getInt(12),limit.getHp())).put(2,Math.max(point.getInt(2),limit.getMp())).put(10,Math.max(point.getInt(10),limit.getDamage())).put(7,Math.max(point.getInt(7),limit.getDefense())).put(3,Math.max(point.getInt(3),limit.getCritical()));}}
                    case "COMPANION"->{String species=r.getString("species");if(r.getString("mode").equals("EGG")){if(!species.equals("MABU"))throw new IllegalArgumentException("Chỉ hỗ trợ trứng Ma Bư trong mã mới");add(bag,box,new JSONObject().put("item_id",568).put("quantity",1).put("options",new JSONArray()));}else{if(p.fusion.typeFusion!=0)throw new IllegalArgumentException("Tách hợp thể trước khi nhận quà đổi đệ; chưa mất lượt mã");if(p.pet!=null&&!r.getBoolean("replace")){if(!species.equals("NORMAL"))throw new IllegalArgumentException("Mã cần cho phép nâng loại đệ hiện tại");}else petSpecies=species;}}
                    default->throw new IllegalArgumentException("Loại quà chưa hỗ trợ nhận trực tiếp");
                }ids.add(rs.getString("id"));
            }}
        }
        final List<Item> nextBag=loadItems(bag),nextBox=loadItems(box);final String species=petSpecies;Pet created=species!=null&&p.pet==null?CompanionService.construct(p,species):null;
        if(species!=null){Pet pet=p.pet==null?created:p.pet;JSONObject pi=CompanionService.info(species,pet.gender).put("status",Pet.GOHOME);JSONObject pp=CompanionService.petPoint(pet);pp.put("power",Math.max(pp.getLong("power"),species.equals("NORMAL")?2000:1500000));execute(c,"UPDATE player SET pet_info=?,pet_point=?,pet_body=?,pet_skill=?,pet_power=? WHERE id=?",pi.toString(),pp.toString(),items(pet.inventory.itemsBody).toString(),CompanionService.petSkills(pet).toString(),pp.getLong("power"),p.id);}
        execute(c,"UPDATE player SET data_inventory=?,data_point=?,power=?,items_bag=?,items_box=? WHERE id=?",money.toString(),point.toString(),point.getLong(11),bag.toString(),box.toString(),p.id);
        for(String id:ids)execute(c,"UPDATE live_admin_actions SET status='APPLIED',details='Received online',applied_at=CURRENT_TIMESTAMP WHERE id=?",id);
        return ()->{p.inventory.gold=money.getLong(0);p.inventory.gem=money.getInt(1);p.inventory.ruby=money.getInt(2);p.inventory.itemsBag=nextBag;p.inventory.itemsBox=nextBox;
            NPoint n=p.nPoint;n.limitPower=(byte)point.getInt(4);n.initPowerLimit();n.power=point.getLong(11);n.tiemNang=point.getLong(8);n.hpg=point.getInt(12);n.mpg=point.getInt(2);n.dameg=point.getInt(10);n.defg=point.getInt(7);n.critg=point.getInt(3);
            if(species!=null){if(p.pet==null)p.pet=created;else MapService.gI().exitMap(p.pet);CompanionService.updateSpecies(p.pet,species);p.pet.status=Pet.GOHOME;p.pet.nPoint.calPoint();p.pet.nPoint.setFullHpMp();Service.gI().sendHavePet(p);}
            Service.gI().point(p);Service.gI().sendMoney(p);InventoryService.gI().sendItemBags(p);InventoryService.gI().sendItemBox(p);
        };
    }
}
