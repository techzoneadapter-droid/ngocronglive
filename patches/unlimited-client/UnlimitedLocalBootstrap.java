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
  Thread shopCheck=new Thread(()->{
   var request=java.nio.file.Path.of("../local-data/bulma-test.request");
   while(true)try{
    Thread.sleep(500);
    var noelRequest=java.nio.file.Path.of("../local-data/noel-boss-test.request");
    if(java.nio.file.Files.exists(noelRequest)){
     java.nio.file.Files.delete(noelRequest);
     var noel=BossManager.gI().getBossById(-1007230);
     if(noel==null)throw new IllegalStateException("Actual Noel boss not initialized");
     if(noel.zone!=null)MapService.gI().exitMap(noel);
     var target=MapService.gI().getMapById(3).zones.get(0);
     var spawn=noel.getClass().getDeclaredMethod("spawnAt",nro.models.map.Zone.class,int.class,int.class);spawn.setAccessible(true);
     if(!(boolean)spawn.invoke(noel,target,target.map.mapWidth/2,24)||noel.zone!=target||!target.getBosses().contains(noel))throw new IllegalStateException("Noel spawn failed");
     if((boolean)spawn.invoke(noel,target,target.map.mapWidth/2,24))throw new IllegalStateException("Duplicate spawn accepted");
     if(!noel.name.equals("Bố Già Noel"))throw new IllegalStateException("Noel name mismatch");
     System.out.println("NOEL_ACTUAL_SPAWN_AND_DUPLICATE_PASS map="+target.map.mapId+" zone="+target.zoneId+" name="+noel.name);
    }
    if(!java.nio.file.Files.exists(request))continue;
    var player=nro.server.Client.gI().getPlayers().stream().filter(p->p.id==6).findFirst().orElse(null);
    if(player==null)continue;
    java.nio.file.Files.delete(request);
    synchronized(player){
     int oldShop=player.iDMark.getShopId();long gold=player.inventory.gold;
     int before=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==379).mapToInt(i->i.quantity).sum();
     player.iDMark.setShopId(nro.consts.ConstNpc.SHOP_BUNMA_TL_0);
     nro.services.func.ShopService.gI().buyItem(player,(byte)0,379);
     int after=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==379).mapToInt(i->i.quantity).sum();
     player.iDMark.setShopId(oldShop);
     System.out.println("BULMA_NATIVE_PURCHASE_RESULT count="+before+"->"+after+" gold="+gold+"->"+player.inventory.gold);
     if(after!=before+1||player.inventory.gold!=gold-100_000_000L)throw new IllegalStateException("Bulma actual purchase failed");
     System.out.println("BULMA_NATIVE_PURCHASE_PASS");
     player.iDMark.setShopId(nro.consts.ConstNpc.SHOP_BUNMA_TL_0);
     var bytes=new java.io.ByteArrayOutputStream();var out=new java.io.DataOutputStream(bytes);
     out.writeByte(0);out.writeShort(379);out.writeShort(3);
     var packet=new nro.server.io.Message((byte)6,bytes.toByteArray());
     if(!nro.admin.NpcBundlePurchase.packet(player.getSession(),packet))throw new IllegalStateException("Bulk packet not handled");
     int bulk=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==379).mapToInt(i->i.quantity).sum();
     if(bulk!=after+3||player.inventory.gold!=gold-400_000_000L)throw new IllegalStateException("Bulma bulk purchase failed");
     System.out.println("BULMA_BULK_PURCHASE_PASS quantity=3 gold=300000000");
     Thread.sleep(300);
     long gems=player.inventory.gem;
     int capsules=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==194).mapToInt(i->i.quantity).sum();
     player.iDMark.setShopId(nro.consts.ConstNpc.SHOP_BUNMA_QK_0);
     bytes=new java.io.ByteArrayOutputStream();out=new java.io.DataOutputStream(bytes);
     out.writeByte(0);out.writeShort(194);out.writeShort(2);
     packet=new nro.server.io.Message((byte)6,bytes.toByteArray());
     nro.admin.NpcBundlePurchase.packet(player.getSession(),packet);
     int capsulesAfter=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==194).mapToInt(i->i.quantity).sum();
     if(capsulesAfter!=capsules+2||player.inventory.gem!=gems-2000)throw new IllegalStateException("Normal non-Santa gem shop bulk failed");
     System.out.println("ALL_NPC_BULK_GEM_PURCHASE_PASS quantity=2 gems=2000");
     Thread.sleep(300);
     player.iDMark.setShopId(nro.consts.ConstNpc.SHOP_BUNMA_TL_0);
     long balance=player.inventory.gold;player.inventory.gold=0;
     bytes=new java.io.ByteArrayOutputStream();out=new java.io.DataOutputStream(bytes);
     out.writeByte(0);out.writeShort(379);out.writeShort(3);
     packet=new nro.server.io.Message((byte)6,bytes.toByteArray());
     nro.admin.NpcBundlePurchase.packet(player.getSession(),packet);
     int refused=player.inventory.itemsBag.stream().filter(i->i.isNotNullItem()&&i.template.id==379).mapToInt(i->i.quantity).sum();
     if(refused!=bulk||player.inventory.gold!=0)throw new IllegalStateException("Insufficient funds granted items");
     player.inventory.gold=balance;
     System.out.println("BULK_INSUFFICIENT_FUNDS_NO_DEBIT_OR_ITEMS_PASS");
     player.iDMark.setShopId(oldShop);
    }
   }catch(Exception error){error.printStackTrace();}
  },"local-bulma-purchase-check");shopCheck.setDaemon(true);shopCheck.start();
  Thread audit=new Thread(()->{
   try {
    for(int attempt=0;attempt<300;attempt++) {
     var player=nro.server.Client.gI().getPlayers().stream().filter(p->p.id==6).findFirst().orElse(null);
     if(player!=null && PlayerProgressionRuntime.active(player)) {
      auditEquipment(player,database);auditBoss(player);
      var forge=nro.server.Manager.NPCS.stream().filter(n->n instanceof KaizenNpc).findFirst().orElseThrow();
      nro.services.func.ChangeMapService.gI().changeMap(player,player.zone,forge.cx+32,forge.cy);
      return;
     }
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
  // Keep the test boss distinct from Noel during the user's Aru review.
  var reviewMap=MapService.gI().getMapById(3).zones.get(1);
  nro.services.func.ChangeMapService.gI().changeMap(boss,reviewMap,240,reviewMap.map.yPhysicInTop(240,0));
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
