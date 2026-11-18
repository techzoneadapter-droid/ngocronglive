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
   System.out.println("UNLIMITED_QA_BOSS id="+boss.id+" hp="+boss.nPoint.exactHp());
  }
  System.out.println("UNLIMITED_LOCAL_READY game=14615 Kaizen_map=0 exact_stats=true");
 }
}
