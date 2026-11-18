package nro.progression;

import java.util.Map;
import nro.models.boss.*;
import nro.services.MapService;

/** Isolated lab entry point. Never package as the production main class. */
public final class UnlimitedLocalBootstrap {
 public static void main(String[] args)throws Exception {
  if(!"1".equals(System.getenv("NRO_UNLIMITED_LOCAL")) || !"127.0.0.1".equals(System.getenv("NRO_GAME_BIND")))
   throw new IllegalStateException("Unlimited QA must use the isolated loopback runner");
  nro.server.Manager.loginPort=3198;
  nro.LocalBootstrap.start();
  Thread game=new Thread(()->nro.server.ServerManager.gI().run(),"unlimited-local-game");game.start();
  for(int attempt=0;attempt<90;attempt++) {
   if(nro.server.ServerManager.isRunning && MapService.gI().getMapById(0)!=null)break;
   if(!game.isAlive())throw new IllegalStateException("Local game exited during startup");
   Thread.sleep(1000);
  }
  // Allow the original release's content initialization to finish before allocating new IDs.
  for(int attempt=0;attempt<60;attempt++) {
   if(nro.server.Manager.ITEM_TEMPLATES.size()>2180 &&
      nro.server.Manager.ITEM_TEMPLATES.get(2179).name.contains("Noel"))break;
   Thread.sleep(1000);
  }
  if(nro.server.Manager.ITEM_TEMPLATES.size()<=2180)throw new IllegalStateException("Original content catalog not ready");
  BossProgressionRuntime.Connections database=nro.jdbc.DBHika::getConnection;
  try(var c=database.open()) {
   ProgressionRepository.initialize(c);ProgressionCommands.initialize(c);BossProgressionRepository.initialize(c);
  }
  seedLocalPlayer(database);
  PlayerProgressionRuntime.install(database);
  BossProgressionRuntime.install(database,Map.of(
    "nro.models.boss.nappa.Kuku",new BossProgression.Profile(500,200,100),
    "nro.models.boss.nappa.Rambo",new BossProgression.Profile(800,300,150)));
  KaizenBootstrap.install(database);
  var aru=MapService.gI().getMapById(0);
  for(Boss boss:BossManager.BOSSES_IN_GAME) {
   if(!boss.getClass().getName().equals("nro.models.boss.nappa.Kuku"))continue;
   if(boss.zone!=null)nro.services.MapService.gI().exitMap(boss);
   boss.respawn();boss.zone=aru.zones.get(0);boss.joinMap();
   var rest=Boss.class.getDeclaredField("secondTimeRestToNextTimeAppear");rest.setAccessible(true);rest.setInt(boss,2);
   var mapJoin=Boss.class.getDeclaredField("mapJoin");mapJoin.setAccessible(true);mapJoin.set(boss,new short[]{0});
   var dataField=Boss.class.getDeclaredField("data");dataField.setAccessible(true);
   BossData original=(BossData)dataField.get(boss);
   BossData local=new BossData(original.name,original.gender,original.typeDame,original.typeHp,original.dame,
      original.hp,original.outfit,new short[]{0},original.skillTemp,2);
   dataField.set(boss,local);
   System.out.println("UNLIMITED_QA_BOSS id="+boss.id+" hp="+boss.nPoint.exactHp());
  }
  System.out.println("UNLIMITED_LOCAL_READY game=14615 Kaizen_map=0 exact_stats=true");
  Thread audit=new Thread(()->{
   try {
    for(int attempt=0;attempt<300;attempt++) {
     var player=nro.server.Client.gI().getPlayers().stream().filter(p->p.id==6).findFirst().orElse(null);
     if(player!=null && PlayerProgressionRuntime.active(player)) { auditEquipment(player,database);auditBoss(player);return; }
     Thread.sleep(1000);
    }
    throw new IllegalStateException("Local test player did not subscribe");
   }catch(Exception error){error.printStackTrace();}
  },"unlimited-local-equipment-audit");audit.setDaemon(true);audit.start();
 }
 private static void auditBoss(nro.models.player.Player player)throws Exception {
  Boss boss=BossManager.BOSSES_IN_GAME.stream().filter(b->b.getClass().getName().equals("nro.models.boss.nappa.Kuku")).findFirst().orElseThrow();
  nro.services.func.ChangeMapService.gI().changeMap(boss,player.zone,player.location.x+70,player.location.y);
  Thread.sleep(2000);
  var hp=boss.nPoint.exactHpMax();var attack=boss.nPoint.exactDamage();var armor=boss.nPoint.exactDefense();
  if(player.playerSkill.skillSelect==null && !player.playerSkill.skills.isEmpty())player.playerSkill.skillSelect=player.playerSkill.skills.get(0);
  var hit=nro.services.SkillService.class.getDeclaredMethod("playerAttackPlayer",nro.models.player.Player.class,nro.models.player.Player.class,boolean.class,int.class);hit.setAccessible(true);
  for(int count=0;count<10 && boss.nPoint.exactHp().signum()>0;count++)hit.invoke(nro.services.SkillService.gI(),player,boss,false,-1);
  if(boss.nPoint.exactHp().signum()!=0)throw new IllegalStateException("Local real SkillService did not kill test boss");
  long killed=System.currentTimeMillis();
  // Skip only the cosmetic departure delay in this isolated lab, retaining actual die/respawn.
  var idle=Boss.class.getDeclaredField("DO_NOTHING");idle.setAccessible(true);boss.setStatus(idle.getByte(null));
  Thread.sleep(Math.max(0,2000-(System.currentTimeMillis()-killed)));
  boss.respawn();
  if(boss.zone==null)boss.zone=player.zone;
  nro.services.func.ChangeMapService.gI().changeMap(boss,player.zone,player.location.x+70,player.location.y);
  var active=Boss.class.getDeclaredField("ATTACK");active.setAccessible(true);boss.setStatus(active.getByte(null));
  if(boss.nPoint.exactHpMax().compareTo(hp)<=0 || boss.nPoint.exactDamage().compareTo(attack)<=0)
      throw new IllegalStateException("Local boss respawn failed to grow");
  System.out.println("UNLIMITED_LOCAL_REAL_SKILL_KILL_RESPAWN_PASS elapsedMs="+(System.currentTimeMillis()-killed)+" hp="+hp+"->"+boss.nPoint.exactHpMax()+" attack="+attack+"->"+boss.nPoint.exactDamage()+" armor="+armor+"->"+boss.nPoint.exactDefense());
  ExactStatsChannel.broadcast(boss);
 }
 private static void auditEquipment(nro.models.player.Player player,BossProgressionRuntime.Connections database)throws Exception {
  KaizenCatalog.Catalog catalog;try(var c=database.open()){catalog=KaizenCatalog.install(c);}
  var put=nro.services.InventoryService.class.getDeclaredMethod("putItemBody",nro.models.player.Player.class,nro.models.item.Item.class);put.setAccessible(true);
  synchronized(player) {
   KaizenEquipment.ensureSlots(player);
   for(var recipe:KaizenRecipes.values()) {
    var binding=catalog.forge().get(recipe);int slot=KaizenEquipment.slot(binding.equipmentId());
    var old=player.inventory.itemsBody.get(slot);player.inventory.itemsBody.set(slot,nro.services.ItemService.gI().createItemNull());player.nPoint.calPoint();
    var hp=player.nPoint.exactHpMax();var ki=player.nPoint.exactMpMax();var attack=player.nPoint.exactDamage();var armor=player.nPoint.exactDefense();
    var item=nro.services.ItemService.gI().createNewItem((short)binding.equipmentId(),1);
    for(Object raw:binding.options()){var pair=(org.json.JSONArray)raw;item.itemOptions.add(new nro.models.item.ItemOption(pair.getInt(0),pair.getInt(1)));}
    put.invoke(nro.services.InventoryService.gI(),player,item);player.nPoint.calPoint();
    if(player.inventory.itemsBody.get(slot)!=item || item.itemOptions.size()!=3 || player.nPoint.exactDamage().compareTo(attack)<=0
       || ((recipe==KaizenRecipes.CAPE || recipe==KaizenRecipes.SCARF) && player.nPoint.exactDefense().compareTo(armor)<=0))
       throw new IllegalStateException("Local equipment stat/slot test failed: "+recipe);
    System.out.println("KAIZEN_LOCAL_EQUIP_PASS "+recipe+" slot="+slot+" hp="+hp+"->"+player.nPoint.exactHpMax()+" ki="+ki+"->"+player.nPoint.exactMpMax()+" attack="+attack+"->"+player.nPoint.exactDamage()+" armor="+armor+"->"+player.nPoint.exactDefense());
    // Only the dedicated local fixture receives these items; original player data copy remains separate.
   }
   player.nPoint.setFullHpMp();nro.services.InventoryService.gI().sendItemBody(player);nro.services.InventoryService.gI().sendItemBags(player);
   nro.services.Service.gI().point(player);ExactStatsChannel.broadcast(player);
  }
 }
 private static void seedLocalPlayer(BossProgressionRuntime.Connections database)throws Exception {
  java.nio.file.Path marker=java.nio.file.Path.of("../local-data/unlimited-x10-seeded.txt");
  if(java.nio.file.Files.exists(marker))return;
  try(var c=database.open()) {
   c.setAutoCommit(false);
   try {
    var values=new java.util.EnumMap<UnlimitedProgression.Stat,java.math.BigInteger>(UnlimitedProgression.Stat.class);
    try(var q=c.createStatement();var r=q.executeQuery("SELECT MAX(power),MAX(hp),MAX(mp),MAX(damage),MAX(defense) FROM power_limit")) {
     r.next();int[] columns={1,0,2,3,4,5};
     for(var stat:UnlimitedProgression.Stat.values())values.put(stat,stat==UnlimitedProgression.Stat.POTENTIAL?java.math.BigInteger.TEN.pow(30):new java.math.BigInteger(r.getString(columns[stat.ordinal()])).multiply(java.math.BigInteger.TEN));
    }
    var saved=ProgressionCommands.open(c,6,3);ProgressionRepository.save(c,saved,new UnlimitedProgression(values));
    try(var q=c.prepareStatement("UPDATE player SET data_location='[300,432,0]' WHERE id=6 AND account_id=3")){q.executeUpdate();}
    c.commit();java.nio.file.Files.writeString(marker,values.toString());
    System.out.println("UNLIMITED_QA_X10_OLD_CAP "+values);
   }catch(Exception error){c.rollback();throw error;}
  }
 }
}
