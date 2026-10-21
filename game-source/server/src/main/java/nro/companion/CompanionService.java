package nro.companion;

import java.sql.*;
import java.util.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.player.*;
import nro.models.item.Item;
import nro.models.item.ItemOption;
import nro.models.skill.Skill;
import nro.models.npc.specialnpc.MabuEgg;
import nro.services.*;
import nro.server.Client;
import nro.utils.SkillUtil;

/** One active disciple and one owner-bound incubation slot. No paid-currency alias. */
public final class CompanionService {
    private CompanionService() {}
    private static volatile JSONObject config=defaults();
    public static JSONObject defaults(){return new JSONObject().put("auto_normal",true).put("welcome_gold",1000000)
        .put("hatch_seconds",3600).put("fast_gold",100000000).put("mabu_drop",25).put("jiren_drop",10).put("zeno_drop",5).put("bosses",true);}
    public static JSONObject settings(){return new JSONObject(config.toString());}
    public static void reload()throws SQLException{try(Connection c=DBHika.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT payload FROM live_companion_config WHERE id=1")){if(r.next())config=new JSONObject(r.getString(1));}}
    public static JSONObject validate(JSONObject b){JSONObject out=new JSONObject().put("auto_normal",b.getBoolean("auto_normal")).put("bosses",b.getBoolean("bosses"));
        for(String k:List.of("welcome_gold","hatch_seconds","fast_gold","mabu_drop","jiren_drop","zeno_drop")){
            long v=b.getLong(k),max=k.endsWith("drop")?100:k.equals("hatch_seconds")?604800:1000000000L;
            if(v<0||v>max||k.equals("hatch_seconds")&&v<60)throw new IllegalArgumentException("Cấu hình "+k+" ngoài giới hạn");out.put(k,v);
        }return out;}
    public static String valid(String s){if(!Set.of("NORMAL","MABU","JIREN","ZENO").contains(s)&&nro.live.GrowthService.vip(s)==null)throw new IllegalArgumentException("Loại đệ tử không hỗ trợ");return s;}
    public static String name(String s){if(nro.live.GrowthService.vip(s)!=null)return nro.live.GrowthService.vip(s).getString("name");return switch(valid(s)){case "MABU"->"Ma Bư";case "JIREN"->"Jiren";case "ZENO"->"Zeno";default->"Đệ tử";};}
    public static int bonus(String s){return switch(s){case "NORMAL"->30;case "MABU"->50;default->s.startsWith("VIP_")?70:30;};}
    public static long incubation(String s){return config.getLong("hatch_seconds")*1000L*(s.equals("JIREN")?6:s.equals("ZENO")?24:1);}
    public static JSONArray catalog(){JSONArray a=new JSONArray();for(String s:List.of("NORMAL","MABU","JIREN","ZENO"))a.put(new JSONObject().put("species",s).put("name",name(s)).put("fusion_bonus",bonus(s)).put("incubation_seconds",incubation(s)/1000));return a;}
    public static void start()throws Exception{reload();CompanionAssets.install();// New eggs come from Broly; paid VIP profiles are purchased separately.}
    public static JSONObject info(String species,int gender){JSONObject b=new JSONObject().put("name","$"+name(species)).put("gender",gender).put("live_species",species).put("status",0).put("type_fusion",0).put("left_fusion",0);
        for(String k:List.of("is_mabu","is_Bulo","is_CellBao","is_BillNhi","is_FideTrau","is_SuperPicolo"))b.put(k,k.equals("is_mabu")&&species.equals("MABU")?1:0);return b;}
    public static JSONObject points(String species){return new JSONObject().put("power",species.equals("NORMAL")?2000:1500000).put("tiem_nang",2000).put("stamina",1000).put("max_stamina",1000).put("hpg",2000).put("mpg",2000).put("damg",species.equals("NORMAL")?30:80).put("defg",20).put("critg",1).put("limit_power",0).put("hp",2000).put("mp",2000);}
    private static JSONArray emptyBody(){JSONArray b=new JSONArray();for(int i=0;i<6;i++)b.put(new JSONObject().put("temp_id",-1).put("quantity",0).put("option",new JSONArray()).put("create_time",0));return b;}
    private static JSONArray skills(int gender){return new JSONArray().put(new JSONArray().put(gender*2).put(1)).put(new JSONArray().put(-1).put(0)).put(new JSONArray().put(-1).put(0)).put(new JSONArray().put(-1).put(0));}
    /** Runs on the existing login transaction, before a player object is loaded. */
    public static void grant(Connection c,JSONObject row,String species,boolean egg,boolean replace)throws SQLException{
        valid(species);
        if(egg){JSONObject old=new JSONObject(row.getString("data_mabu_egg"));if(old.has("create_time"))throw new IllegalArgumentException("Đang có trứng ấp; chưa thay trứng hiện tại");
            JSONObject e=new JSONObject().put("create_time",System.currentTimeMillis()).put("time_done",incubation(species)).put("species",species);
            try(PreparedStatement p=c.prepareStatement("UPDATE player SET data_mabu_egg=? WHERE id=?")){p.setString(1,e.toString());p.setLong(2,row.getLong("id"));p.executeUpdate();}return;
        }
        JSONObject old=new JSONObject(row.getString("pet_info"));boolean exists=old.has("name");
        if(exists&&!replace){if(species.equals("NORMAL"))return;throw new IllegalArgumentException("Đã có đệ tử; cần chọn nâng loại, giữ đồ/chỉ số");}
        int gender=exists?old.getInt("gender"):row.getInt("gender");JSONObject next=info(species,gender),point=exists?new JSONObject(row.getString("pet_point")):points(species);
        if(exists){next.put("status",3);point.put("power",Math.max(point.getLong("power"),species.equals("NORMAL")?2000:1500000));}
        try(PreparedStatement p=c.prepareStatement("UPDATE player SET pet_info=?,pet_point=?,pet_body=?,pet_skill=?,pet_power=? WHERE id=?")){
            p.setString(1,next.toString());p.setString(2,point.toString());p.setString(3,exists?row.getString("pet_body"):emptyBody().toString());p.setString(4,exists?row.getString("pet_skill"):skills(gender).toString());p.setLong(5,point.getLong("power"));p.setLong(6,row.getLong("id"));p.executeUpdate();}
    }
    public static void welcome(Connection c,int user)throws SQLException{
        c.setAutoCommit(false);
        try(PreparedStatement p=c.prepareStatement("SELECT id,gender,pet_info,pet_point,pet_body,pet_skill,data_mabu_egg,data_inventory FROM player WHERE account_id=? FOR UPDATE")){
            p.setInt(1,user);try(ResultSet r=p.executeQuery()){
                if(!r.next()){c.rollback();return;}JSONObject row=new JSONObject();for(String k:List.of("pet_info","pet_point","pet_body","pet_skill","data_mabu_egg","data_inventory"))row.put(k,r.getString(k));row.put("id",r.getLong("id")).put("gender",r.getInt("gender"));
                boolean first;try(PreparedStatement claim=c.prepareStatement("INSERT IGNORE INTO live_companion_welcome(account_id) VALUES(?)")){claim.setInt(1,user);first=claim.executeUpdate()==1;}
                if(config.getBoolean("auto_normal")&&!new JSONObject(row.getString("pet_info")).has("name"))grant(c,row,"NORMAL",false,false);
                if(first){JSONArray money=new JSONArray(row.getString("data_inventory"));money.put(0,Math.min(Inventory.LIMIT_GOLD,Math.addExact(money.getLong(0),config.getLong("welcome_gold"))));try(PreparedStatement m=c.prepareStatement("UPDATE player SET data_inventory=? WHERE id=?")){m.setString(1,money.toString());m.setLong(2,row.getLong("id"));m.executeUpdate();}}
            }c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
    }
    public static void updateSpecies(Pet pet,String species){pet.liveSpecies=species;pet.isMabu=species.equals("MABU");pet.isBulo=pet.isCellBao=pet.isBillNhi=pet.isFideTrau=pet.isSuperPicolo=false;pet.name="$"+name(species);pet.nPoint.power=Math.max(pet.nPoint.power,species.equals("NORMAL")?2000:1500000);pet.nPoint.initPowerLimit();}
    public static JSONObject petPoint(Pet p){return new JSONObject().put("power",p.nPoint.power).put("tiem_nang",p.nPoint.tiemNang).put("stamina",p.nPoint.stamina).put("max_stamina",p.nPoint.maxStamina).put("hpg",p.nPoint.hpg).put("mpg",p.nPoint.mpg).put("damg",p.nPoint.dameg).put("defg",p.nPoint.defg).put("critg",p.nPoint.critg).put("limit_power",p.nPoint.limitPower).put("hp",p.nPoint.hp).put("mp",p.nPoint.mp);}
    private static JSONArray petBody(Pet p){JSONArray b=new JSONArray();for(Item item:p.inventory.itemsBody){JSONObject o=new JSONObject().put("temp_id",item.isNotNullItem()?item.template.id:-1).put("quantity",item.isNotNullItem()?item.quantity:0).put("create_time",item.isNotNullItem()?item.createTime:0);JSONArray opts=new JSONArray();if(item.isNotNullItem())for(ItemOption io:item.itemOptions)opts.put(new JSONArray().put(io.optionTemplate.id).put(io.param));b.put(o.put("option",opts));}return b;}
    public static JSONArray petSkills(Pet p){JSONArray a=new JSONArray();for(Skill s:p.playerSkill.skills)a.put(new JSONArray().put(s.skillId==-1?-1:s.template.id).put(s.skillId==-1?0:s.point));return a;}
    public static Pet construct(Player master,String species){Pet pet=new Pet(master);pet.id=-master.id;pet.gender=master.gender;pet.status=Pet.GOHOME;JSONObject v=points(species);pet.nPoint.hpg=v.getInt("hpg");pet.nPoint.mpg=v.getInt("mpg");pet.nPoint.dameg=v.getInt("damg");pet.nPoint.defg=v.getInt("defg");pet.nPoint.critg=v.getInt("critg");pet.nPoint.stamina=pet.nPoint.maxStamina=1000;pet.nPoint.tiemNang=2000;for(int j=0;j<6;j++)pet.inventory.itemsBody.add(ItemService.gI().createItemNull());pet.playerSkill.skills.add(SkillUtil.createSkill(master.gender*2,1));for(int j=0;j<3;j++)pet.playerSkill.skills.add(SkillUtil.createEmptySkill());updateSpecies(pet,species);pet.nPoint.calPoint();pet.nPoint.setFullHpMp();return pet;}
    public static boolean awardEgg(Player player,String species){synchronized(player){
        if(player.isDisposed()||player.getSession()==null||player.mabuEgg!=null)return false;
        MabuEgg egg=new MabuEgg(player,System.currentTimeMillis(),incubation(species));egg.species=valid(species);
        try(Connection c=DBHika.getConnection();PreparedStatement p=c.prepareStatement("UPDATE player SET data_mabu_egg=? WHERE id=?")){
            p.setString(1,new JSONObject().put("create_time",egg.lastTimeCreate).put("time_done",egg.timeDone).put("species",species).toString());p.setLong(2,player.id);if(p.executeUpdate()!=1)return false;
            player.mabuEgg=egg;egg.sendMabuEgg();Service.getInstance().sendThongBao(player,"Nhận trứng "+name(species)+". Về nhà gặp Quả trứng hoặc chat aptrung / notrung.");return true;
        }catch(Exception e){System.err.println("Companion egg save failed: "+e.getClass().getSimpleName());return false;}
    }}
    public static void hatch(Player player,MabuEgg egg){synchronized(player){
        if(egg==null||player.mabuEgg!=egg)throw new IllegalArgumentException("Trứng không còn tồn tại");
        if(egg.getSecondDone()>0)throw new IllegalArgumentException("Trứng chưa đủ thời gian ấp");
        if(player.fusion.typeFusion!=0)throw new IllegalArgumentException("Hãy tách hợp thể trước khi nở trứng");
        String species=valid(egg.species);Pet old=player.pet;boolean created=old==null;
        if(created){PetService.gI().createNewPet(player,false,false,false,false,false,false,player.gender);}
        Pet pet=player.pet;JSONObject next=info(species,pet.gender).put("status",Pet.GOHOME),point=petPoint(pet);point.put("power",Math.max(point.getLong("power"),species.equals("NORMAL")?2000:1500000));
        try(Connection c=DBHika.getConnection();PreparedStatement p=c.prepareStatement("UPDATE player SET pet_info=?,pet_point=?,pet_body=?,pet_skill=?,pet_power=?,data_mabu_egg='{}' WHERE id=?")){
            p.setString(1,next.toString());p.setString(2,point.toString());p.setString(3,petBody(pet).toString());p.setString(4,petSkills(pet).toString());p.setLong(5,point.getLong("power"));p.setLong(6,player.id);if(p.executeUpdate()!=1)throw new SQLException("Player missing");
        }catch(Exception e){if(created){player.pet=null;pet.dispose();}throw new IllegalArgumentException("Chưa lưu được đệ tử; trứng được giữ nguyên, hãy thử lại");}
        MapService.gI().exitMap(pet);updateSpecies(pet,species);pet.status=Pet.GOHOME;pet.nPoint.calPoint();pet.nPoint.setFullHpMp();player.mabuEgg=null;
        Service.getInstance().sendHavePet(player);Service.getInstance().sendThongBao(player,"Nở thành công đệ tử "+name(species)+". Giữ nguyên trang bị, kỹ năng và chỉ số đã luyện. Mở Đệ tử để gọi theo.");
    }}
    public static void fast(Player player,MabuEgg egg){synchronized(player){
        if(egg==null||player.mabuEgg!=egg)throw new IllegalArgumentException("Không có trứng đang ấp");if(egg.getSecondDone()==0)return;
        long cost=config.getLong("fast_gold");if(player.inventory.gold<cost)throw new IllegalArgumentException("Cần "+cost+" vàng để ấp nhanh");
        JSONArray money=new JSONArray().put(player.inventory.gold-cost).put(player.inventory.gem).put(player.inventory.ruby).put(player.inventory.goldLimit);
        JSONObject data=new JSONObject().put("create_time",egg.lastTimeCreate).put("time_done",0).put("species",egg.species);
        try(Connection c=DBHika.getConnection();PreparedStatement p=c.prepareStatement("UPDATE player SET data_inventory=?,data_mabu_egg=? WHERE id=?")){
            p.setString(1,money.toString());p.setString(2,data.toString());p.setLong(3,player.id);if(p.executeUpdate()!=1)throw new SQLException("Player missing");
        }catch(Exception e){throw new IllegalArgumentException("Chưa ấp nhanh được; chưa trừ vàng");}
        player.inventory.gold-=cost;egg.timeDone=0;Service.getInstance().sendMoney(player);egg.sendMabuEgg();
    }}
    public static void cancel(Player player,MabuEgg egg){synchronized(player){if(egg==null||player.mabuEgg!=egg)return;try(Connection c=DBHika.getConnection();PreparedStatement p=c.prepareStatement("UPDATE player SET data_mabu_egg='{}' WHERE id=?")){p.setLong(1,player.id);p.executeUpdate();player.mabuEgg=null;}catch(Exception e){throw new IllegalArgumentException("Chưa hủy được trứng");}}}
    public static boolean chat(Player p,String text){String command=text.trim().toLowerCase(Locale.ROOT);if(!Set.of("detu","aptrung","notrung","apnhanh","santrung").contains(command))return false;
        try{if(command.equals("santrung")){StringBuilder message=new StringBuilder("Săn Broly lấy trứng Ma Bư:\n");for(nro.models.boss.Boss boss:nro.models.boss.BossManager.BOSSES_IN_GAME)if(boss.name!=null&&boss.name.toLowerCase(Locale.ROOT).contains("broly")&&boss.zone!=null&&!boss.isDie())message.append(boss.name).append(": ").append(boss.zone.map.mapName).append(" / khu ").append(boss.zone.zoneId).append("\n");Service.getInstance().sendThongBao(p,message.toString());}
            else if(command.equals("detu")){Service.getInstance().sendThongBao(p,p.pet==null?"Chưa có đệ tử; đăng nhập lại để nhận quà tân thủ.":"Đệ tử: "+p.pet.name+". Mở menu Đệ tử để theo/bảo vệ/tấn công/về nhà/hợp thể.");}
            else if(p.mabuEgg==null)throw new IllegalArgumentException("Chưa có trứng. Săn boss Ma Bư/Jiren/Zeno hoặc nhận giftcode trứng.");
            else if(command.equals("notrung"))hatch(p,p.mabuEgg);
            else if(command.equals("apnhanh"))fast(p,p.mabuEgg);
            else Service.getInstance().sendThongBao(p,"Trứng "+name(p.mabuEgg.species)+": còn "+p.mabuEgg.getSecondDone()+" giây. Về nhà gặp Quả trứng để ấp nhanh bằng vàng.");
        }catch(IllegalArgumentException e){Service.getInstance().sendThongBao(p,e.getMessage());}return true;}
}
