package nro.progression;

import java.sql.*;
import nro.models.npc.*;
import nro.server.Manager;
import nro.services.MapService;

/** Explicit local/development startup only; call after the existing maps and catalogs are loaded. */
public final class KaizenBootstrap {
    private static boolean installed;
    private KaizenBootstrap(){}
    public static synchronized void install(BossProgressionRuntime.Connections connections)throws SQLException {
        if(installed)return;
        var map=MapService.gI().getMapById(0);
        if(map==null || !map.mapName.toLowerCase(java.util.Locale.ROOT).contains("aru"))
            throw new SQLException("Làng Aru map 0 could not be verified");
        int x=-1,y=-1;
        for(int candidate=96;candidate<map.mapWidth-96;candidate+=24) {
            final int location=candidate;
            if(Manager.NPCS.stream().anyMatch(n->n.mapId==0&&Math.abs(n.cx-location)<96))continue;
            if(map.wayPoints!=null && map.wayPoints.stream().anyMatch(w->location>=w.minX-48&&location<=w.maxX+48))continue;
            int ground=map.yPhysicInTop(candidate,0);
            if(ground<=0 || ground>=map.mapHeight-24)continue;
            x=candidate;y=ground;break;
        }
        if(x<0)throw new SQLException("No non-overlapping Kaizen location in Làng Aru");
        KaizenCatalog.Catalog catalog;NpcTemplate npc;
        try(Connection c=connections.open()) {
            catalog=KaizenCatalog.install(c);
            KaizenTransactions.initialize(c);
            npc=installNpcTemplate(c);
        }
        KaizenEquipment.install(catalog.equipment());
        installDropConfig(catalog);
        var forge=new KaizenPlayerForge(connections,new KaizenForge(catalog.forge()));
        var actor=new KaizenNpc(0,x,y,npc.id,KaizenIcons.NPC_PORTRAIT,forge);
        map.addNpc(actor);
        // New catalog entries must be fetched rather than read from an older item/map cache.
        nro.data.DataGame.vsItem=27;nro.data.DataGame.vsMap=6;
        installed=true;
    }
    private static void installDropConfig(KaizenCatalog.Catalog catalog)throws SQLException {
        java.nio.file.Path path=java.nio.file.Path.of("resources/config/kaizen-drops.json");
        try {
            var parsed=KaizenDropConfig.parse(java.nio.file.Files.readString(path),new KaizenDropConfig.RuntimeCatalog() {
                public boolean mapExists(int id,String name) {
                    var map=MapService.gI().getMapById(id);return map!=null && map.mapName.equals(name);
                }
                public boolean bossRegistered(String name,int id) {
                    return nro.models.boss.BossManager.BOSSES_IN_GAME.stream().anyMatch(b->b.getClass().getName().equals(name)
                        && b.zone!=null && b.zone.map.mapId==id && terminalHookPresent(b));
                }
                private boolean terminalHookPresent(nro.models.boss.Boss boss) {
                    try {return boss.getClass().getMethod("injured",nro.models.player.Player.class,int.class,boolean.class,boolean.class)
                            .getDeclaringClass()==nro.models.boss.Boss.class;}
                    catch(NoSuchMethodException error){return false;}
                }
                public boolean mobExists(int template,int id) {
                    var map=MapService.gI().getMapById(id);
                    return map!=null && map.zones.stream().anyMatch(z->z.mobs.stream().anyMatch(m->m.tempId==template));
                }
                public boolean itemReady(KaizenRecipes kind,KaizenDropConfig.Form form) {
                    var binding=catalog.forge().get(kind);
                    return binding!=null && (form==KaizenDropConfig.Form.FRAGMENT || binding.options().toString().equals(KaizenStats.options(kind).toString()));
                }
            });
            nro.services.RewardService.gI().configureKaizen(parsed,catalog.forge());
            System.out.println("KAIZEN_DROP_CONFIG enabled="+parsed.enabled().size()+" pending="+parsed.disabled().size());
        }catch(java.io.IOException|IllegalArgumentException error){throw new SQLException("Kaizen drop configuration not verified",error);}
    }
    private static NpcTemplate installNpcTemplate(Connection c)throws SQLException {
        var donor=Manager.NPC_TEMPLATES.stream().filter(n->n.id==21).findFirst()
                .orElseThrow(()->new SQLException("Native forge NPC sprite missing"));
        try(var q=c.prepareStatement("SELECT id,head,body,leg FROM npc_template WHERE name=?")) {
            q.setString(1,"Thần Rèn Kaizen");
            try(var r=q.executeQuery()) {
                if(r.next()) {
                    var existing=new NpcTemplate();existing.id=r.getInt(1);existing.name="Thần Rèn Kaizen";
                    existing.head=r.getInt(2);existing.body=r.getInt(3);existing.leg=r.getInt(4);
                    if(r.next())throw new SQLException("Multiple Kaizen NPC templates");
                    if(Manager.NPC_TEMPLATES.stream().noneMatch(n->n.id==existing.id))Manager.NPC_TEMPLATES.add(existing);
                    return existing;
                }
            }
        }
        int id;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),-1)+1 FROM npc_template")){r.next();id=r.getInt(1);}
        if(id>127)throw new SQLException("NPC ID exceeds existing signed-byte protocol");
        try(var q=c.prepareStatement("INSERT INTO npc_template(id,name,head,body,leg) VALUES(?,?,?,?,?)")) {
            q.setInt(1,id);q.setString(2,"Thần Rèn Kaizen");q.setInt(3,donor.head);q.setInt(4,donor.body);q.setInt(5,donor.leg);q.executeUpdate();
        }
        var npc=new NpcTemplate();npc.id=id;npc.name="Thần Rèn Kaizen";npc.head=donor.head;npc.body=donor.body;npc.leg=donor.leg;
        Manager.NPC_TEMPLATES.add(npc);return npc;
    }
}
