package nro.live;
import java.sql.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
import nro.jdbc.DBHika;
import nro.models.map.Zone;
import nro.models.map.MapTemplate;
import nro.models.mob.Mob;
import nro.models.player.*;
import nro.server.Manager;
import nro.services.*;
import nro.services.func.ChangeMapService;
import nro.admin.OnlineRewards;
import nro.dialog.*;

/** Independent training maps reuse existing terrain/sprites and have no quest/boss side effects. */
public final class TrainingWorld {
    private static volatile JSONArray rules=new JSONArray();
    public static JSONArray settings(){return new JSONArray(rules.toString());}
    public static JSONObject rule(int id){JSONArray a=rules;for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getInt("map_id")==id)return a.getJSONObject(i);return null;}
    public static JSONObject validate(JSONObject r){int id=r.getInt("map_id"),source=r.getInt("source_map");if(id<176||id>230||source>=176||MapService.gI().getMapById(source)==null)throw new IllegalArgumentException("Map mới 176–230, nguồn phải là map gốc hiện có");String title=r.getString("name").trim();if(title.isEmpty()||title.length()>80)throw new IllegalArgumentException("Tên khu không hợp lệ");if(r.getLong("fee_ruby")<0||r.getLong("fee_ruby")>1000000000||r.getInt("lease_seconds")<60||r.getInt("lease_seconds")>604800||r.getLong("min_power")<0||r.getLong("min_power")>250000000000L||r.getInt("mob_hp")<100||r.getInt("mob_hp")>100000000||r.getInt("exp_extra")<0||r.getInt("exp_extra")>900)throw new IllegalArgumentException("Phí/thời gian/sức mạnh/HP hoặc phần trăm tăng ngoài giới hạn");if(r.getLong("end_ms")<=r.getLong("start_ms"))throw new IllegalArgumentException("Giờ kết thúc phải sau giờ bắt đầu");r.getBoolean("enabled");r.getBoolean("pk");return new JSONObject(r.toString());}
    public static synchronized void reload()throws Exception{JSONArray a=new JSONArray();try(Connection c=DBHika.getConnection();Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT payload FROM live_training_maps ORDER BY map_id")){while(r.next())a.put(new JSONObject(r.getString(1)));}for(int i=0;i<a.length();i++)install(a.getJSONObject(i));rules=a;nro.data.DataGame.vsMap=(byte)(6+a.length());for(Player player:nro.server.Client.gI().getPlayers())if(player.getSession()!=null)nro.data.DataGame.createMap(player.getSession());}
    private static void install(JSONObject r)throws Exception{int id=r.getInt("map_id");if(MapService.gI().getMapById(id)!=null)return;int source=r.getInt("source_map");var old=MapService.gI().getMapById(source);byte[] terrain=Files.readAllBytes(Path.of("resources/map/"+source));Files.write(Path.of("resources/map/"+id),terrain);DataInputStream input=new DataInputStream(new ByteArrayInputStream(terrain));int w=input.readUnsignedByte(),h=input.readUnsignedByte();int[][] tiles=new int[h][w];for(int y=0;y<h;y++)for(int x=0;x<w;x++)tiles[y][x]=input.readByte();
        nro.models.map.Map map=new nro.models.map.Map(id,r.getString("name"),old.planetId,old.tileId,old.bgId,old.bgType,(byte)0,tiles,old.tileTop,5,false,30,new ArrayList<>(),new ArrayList<>());
        List<Mob> sourceMobs=old.zones.get(0).mobs;if(sourceMobs.isEmpty())sourceMobs=MapService.gI().getMapById(1).zones.get(0).mobs;
        for(Zone zone:map.zones)for(int j=0;j<6;j++){Mob mob=new Mob(sourceMobs.get(j%sourceMobs.size()));mob.id=j;mob.zone=zone;mob.level=(byte)10;mob.location.x=100+(map.mapWidth-200)*j/5;mob.location.y=map.yPhysicInTop(mob.location.x,0);mob.point.setHpFull(r.getInt("mob_hp"));mob.point.setHP(mob.point.getHpFull());mob.setTiemNang();zone.addMob(mob);}
        Manager.MAPS.add(map);MapTemplate template=new MapTemplate();template.id=id;template.name=r.getString("name");template.tileId=old.tileId;template.planetId=old.planetId;Manager.MAP_TEMPLATES=Arrays.copyOf(Manager.MAP_TEMPLATES,Manager.MAP_TEMPLATES.length+1);Manager.MAP_TEMPLATES[Manager.MAP_TEMPLATES.length-1]=template;new Thread(map,"Training map "+id).start();
    }
    public static boolean active(JSONObject r){long now=System.currentTimeMillis();return r.getBoolean("enabled")&&r.getLong("start_ms")<=now&&r.getLong("end_ms")>now;}
    public static boolean allowed(Player p,int map){JSONObject r=rule(map);if(r==null||p.isBoss)return true;if(p.isPet)return allowed(((Pet)p).master,map);return active(r)&&p.nPoint.power>=r.getLong("min_power")&&(r.getLong("fee_ruby")==0||p.liveLeases.optLong(Integer.toString(map))>System.currentTimeMillis());}
    public static boolean safe(Zone zone){JSONObject r=zone==null?null:rule(zone.map.mapId);return r!=null&&!r.getBoolean("pk");}
    public static int bonus(Player p){JSONObject r=p.zone==null?null:rule(p.zone.map.mapId);return r!=null&&active(r)?r.getInt("exp_extra"):0;}
    public static void enter(Player p,int id){synchronized(p){try{JSONObject r=rule(id);if(r==null||!active(r)||p.nPoint.power<r.getLong("min_power"))throw new IllegalArgumentException("Khu chưa mở hoặc chưa đủ sức mạnh");var zone=MapService.gI().getMapCanJoin(p,id);if(zone==null||zone.getNumOfPlayers()>=zone.maxPlayer)throw new IllegalArgumentException("Khu đang đầy, chưa trừ phí");long now=System.currentTimeMillis(),cost=r.getLong("fee_ruby");if(cost>0&&p.liveLeases.optLong(Integer.toString(id))<=now){if(p.inventory.ruby<cost)throw new IllegalArgumentException("Không đủ hồng ngọc vào khu");JSONObject leases=new JSONObject(p.liveLeases.toString()).put(Integer.toString(id),Balance.extend(now,0,r.getLong("lease_seconds")));JSONArray money=new JSONArray().put(p.inventory.gold).put(p.inventory.gem).put(p.inventory.ruby-cost).put(p.inventory.goldLimit);try(Connection c=DBHika.getConnection()){c.setAutoCommit(false);try{OnlineRewards.execute(c,"UPDATE player SET data_inventory=? WHERE id=?",money.toString(),p.id);GrowthService.persist(c,p,p.liveBuffs,leases);c.commit();}catch(Exception e){c.rollback();throw e;}}p.inventory.ruby-=cost;p.liveLeases=leases;Service.gI().sendMoney(p);}
            int x=120;ChangeMapService.gI().changeMap(p,zone,x,zone.map.yPhysicInTop(x,0));Service.gI().sendThongBao(p,"Đã vào "+r.getString("name")+". Chat khuup để chọn khu hoặc thoát.");
        }catch(Exception e){Service.gI().sendThongBao(p,e instanceof IllegalArgumentException?e.getMessage():"Chưa vào được khu luyện");}}}
    public static void check(Player p){if(p.getSession()!=null&&p.zone!=null&&rule(p.zone.map.mapId)!=null&&!allowed(p,p.zone.map.mapId))ChangeMapService.gI().changeMap(p,p.gender+21,-1,300,336);}
    public static boolean chat(Player p,String text){if(!text.trim().equalsIgnoreCase("khuup"))return false;List<JSONObject> list=new ArrayList<>();for(int i=0;i<rules.length();i++)if(active(rules.getJSONObject(i)))list.add(rules.getJSONObject(i));String[] labels=new String[list.size()+1];for(int i=0;i<list.size();i++){JSONObject r=list.get(i);labels[i]=r.getString("name")+"\n"+(r.getLong("fee_ruby")==0?"Miễn phí":r.getLong("fee_ruby")+" hồng ngọc / "+r.getInt("lease_seconds")/60+" phút");}labels[list.size()]="Về nhà";new MenuDialog("Chọn khu luyện. Nền x2; đồ/bùa/sự kiện cộng phần tăng. Không đủ điều kiện sẽ không trừ phí.",labels,new MenuRunable(){public void run(){int i=getIndexSelected();if(i==list.size())ChangeMapService.gI().changeMap(p,p.gender+21,-1,300,336);else if(i>=0&&i<list.size())enter(p,list.get(i).getInt("map_id"));}}).show(p);return true;}
}
