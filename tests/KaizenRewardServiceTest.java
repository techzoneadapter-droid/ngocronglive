package nro.progression;

import java.util.*;
import java.util.concurrent.*;
import nro.models.item.*;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.server.Manager;
import nro.server.io.Session;
import nro.services.RewardService;
import org.json.JSONArray;

/** Calls the real mob reward entry point and native ItemMap constructor. */
public final class KaizenRewardServiceTest {
 public static final class Recipient extends Player {Session session;@Override public Session getSession(){return session;}}
 public static final class Ground extends nro.models.map.Map {
  Ground(){super(123,"fixture",(byte)0,(byte)0,(byte)0,(byte)0,(byte)0,new int[][]{{0}},new int[0],1,false,30,new ArrayList<>(),new ArrayList<>());}
  @Override public int yPhysicInTop(int x,int y){return 200;}
 }
 public static final class Target extends nro.models.boss.Boss {Target(){super(-123,null);} @Override public void rewards(Player player){} @Override public void initTalk(){} @Override public void idle(){} @Override public void checkPlayerDie(Player player){} @Override protected boolean useSpecialSkill(){return false;}}
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var memory=(sun.misc.Unsafe)f.get(null);
  for(int i=0;i<40;i++){var t=new ItemTemplate();t.id=(short)i;t.type=27;t.name="fixture";Manager.ITEM_TEMPLATES.add(t);}
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture",0));
  var map=(Ground)memory.allocateInstance(Ground.class);map.mapId=123;
  var zone=new Zone(map,0,30);var player=(Recipient)memory.allocateInstance(Recipient.class);
  player.id=9;player.zone=zone;player.session=(Session)memory.allocateInstance(Session.class);player.session.actived=true;
  var mob=new Mob();mob.tempId=19;mob.zone=zone;
  var source=new KaizenDropConfig.Source("fixture",KaizenDropConfig.SourceType.NORMAL_MOB,123,"fixture","",19,
   List.of(new KaizenDropConfig.Reward(KaizenRecipes.CROWN,KaizenDropConfig.Form.FRAGMENT,10000,1)));
  var service=RewardService.gI();
  service.configureKaizen(new KaizenDropConfig.Parsed(List.of(source),Map.of()),Map.of(KaizenRecipes.CROWN,new KaizenForge.Binding(20,21,KaizenStats.options(KaizenRecipes.CROWN))));
  var pool=Executors.newFixedThreadPool(8);List<Future<Integer>> calls=new ArrayList<>();
  for(int i=0;i<32;i++)calls.add(pool.submit(()->service.getRewardItems(player,mob,100,200).size()));
  int count=0;for(var result:calls)count+=result.get();pool.shutdown();
  check(count==1,"Concurrent mob death paid more than once");
  var drops=zone.getItemMapsForPlayer(player);check(drops.size()==1 && drops.get(0).itemTemplate.id==20 && drops.get(0).playerId==9,"Native ItemMap/owner mismatch");
  service.resetKaizenReward(mob);player.session.actived=false;
  check(service.getRewardItems(player,mob,100,200).isEmpty(),"Inactive account received reward");
  player.session.actived=true;player.zone=new Zone(map,1,30);
  check(service.getRewardItems(player,mob,100,200).isEmpty(),"Cross-zone reward");
  player.zone=zone;check(service.getRewardItems(player,mob,100,200).size()==1,"Rejected recipient consumed new spawn reward");
  service.resetKaizenReward(mob);mob.point.hp=1;
  check(service.getRewardItems(player,mob,100,200).isEmpty(),"Living mob paid reward");
  var boss=(Target)memory.allocateInstance(Target.class);boss.zone=zone;boss.location=new nro.models.player.Location();
  boss.location.x=100;boss.location.y=200;boss.nPoint=new nro.models.player.NPoint(boss);boss.nPoint.hpMax=100;
  var bossSource=new KaizenDropConfig.Source("fixture_boss",KaizenDropConfig.SourceType.BOSS,123,"fixture",Target.class.getName(),-1,
   List.of(new KaizenDropConfig.Reward(KaizenRecipes.CROWN,KaizenDropConfig.Form.WHOLE,10000,1),
           new KaizenDropConfig.Reward(KaizenRecipes.CROWN,KaizenDropConfig.Form.FRAGMENT,10000,2)));
  service.configureKaizen(new KaizenDropConfig.Parsed(List.of(bossSource),Map.of()),Map.of(KaizenRecipes.CROWN,new KaizenForge.Binding(20,21,KaizenStats.options(KaizenRecipes.CROWN))));
  int before=zone.getItemMapsForPlayer(player).size();
  var bossPool=Executors.newFixedThreadPool(8);var jobs=new ArrayList<Future<?>>();
  for(int i=0;i<32;i++)jobs.add(bossPool.submit(()->service.kaizenBossReward(boss,player)));
  for(var job:jobs)job.get();bossPool.shutdown();
  var items=zone.getItemMapsForPlayer(player);check(items.size()==before+2,"Concurrent boss paid duplicate whole/extra fragments");
  var gear=items.stream().filter(item->item.itemTemplate.id==21).findFirst().orElseThrow();
  check(gear.options.size()==3 && gear.options.get(0).param==500 && gear.options.get(1).param==200 && gear.options.get(2).param==200,"Whole drop lost owner stats or added upgrade level");
  check(items.stream().anyMatch(item->item.itemTemplate.id==20 && item.quantity==2),"Boss extra fragment rule lost");
  service.resetKaizenReward(boss);boss.nPoint.hp=1;service.kaizenBossReward(boss,player);
  check(zone.getItemMapsForPlayer(player).size()==before+2,"Living boss paid reward");
  System.out.println("PASS native RewardService/ItemMap: concurrent mob/boss requests pay once, whole +0 with 3 exact stats, separate extra fragments, owner lock, activated recipient, zone check, respawn reset, living targets rejected");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}



