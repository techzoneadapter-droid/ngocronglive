package nro.admin;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossManager;
import nro.models.item.Item;
import nro.models.item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.player.Pet;
import nro.models.player.Player;
import nro.services.ItemService;
import nro.services.MapService;
import nro.services.RewardService;
import nro.services.Service;
import nro.services.PlayerService;

public final class NoelBoss extends Boss {
    static final int ID = -1007230;
    static final int HP = Integer.MAX_VALUE;
    static final int ARMOR = 0;
    static final int MAX_HIT = (int)(((long)HP + 399) / 400);
    private static final short[] MAPS = {3, 4, 5, 29, 30};
    private static final int[] VIP_OPTIONS = {5, 19, 94, 95, 96, 97};
    private static final int[] MAIN_STATS = {0, 6, 7, 14, 22, 23, 27, 28, 47, 48, 50, 77, 103};
    private int paid;
    private int walkTarget;
    private long nextWalkAt, nextWanderAt;
    private static boolean started;

    public NoelBoss() {
        super(ID, BossData.ONG_GIA_NOEL);
        // Short initial wait. Subsequent respawns still use the normal random delay.
        lastTimeRest = System.currentTimeMillis() - (secondTimeRestToNextTimeAppear - 30L) * 1000;
    }

    public static void ensureSpawn() {
        if (!NoelEvent.active()) {
            var boss = BossManager.gI().getBossById(ID);
            if (boss != null && boss.zone != null) MapService.gI().exitMap(boss);
            return;
        }
        if (started || !NoelEvent.rewardReady()) return;
        if (BossManager.gI().getBossById(ID) == null) new NoelBoss();
        started = true;
    }

    @Override public void init() {
        super.init();
        name = "Bố Già Noel";
        mapJoin = MAPS;
        secondTimeRestToNextTimeAppear = ThreadLocalRandom.current().nextInt(30, 91) * 60;
        nPoint.hpg = nPoint.hpMax = HP;
        nPoint.defg = ARMOR / 4;
        nPoint.def = ARMOR;
        paid = 0;
        walkTarget = -1;
        nextWalkAt = nextWanderAt = 0;
    }

    @Override public void joinMap() {
        if (!NoelEvent.active()) return;
        Zone target = zone != null ? zone : getMapCanJoin(MAPS[ThreadLocalRandom.current().nextInt(MAPS.length)]);
        if (target != null && !target.getBosses().contains(this)) {
            spawnAt(target, target.map.mapWidth / 2, 24);
        }
    }

    synchronized boolean spawnAt(Zone target, int nearX, int nearY) {
        if(target==null || (zone!=null && zone.getBosses().contains(this)))return false;
        int mapWidth = target.map.mapWidth;
        int start = Math.max(24, Math.min(mapWidth - 24, nearX));
        int fromY = Math.max(0, Math.min(target.map.mapHeight - 1, nearY - 24));
        for (int i=0; i<mapWidth/24; i++) {
            int offset = ((i+1)/2)*24*(i%2==0?1:-1);
            int x = start + offset;
            if (x<24 || x>mapWidth-24) continue;
            int y = target.map.yPhysicInTop(x, fromY);
            if (y==0 && fromY!=0) y=target.map.yPhysicInTop(x,0);
            if (y<=0) continue;
            location.x=x;
            location.y=y;
            MapService.gI().goToMap(this,target);
            if(zone!=target || !target.getBosses().contains(this)) {
                System.err.println("NOEL_BOSS_JOIN_FAILED map="+target.map.mapId+" zone="+target.zoneId);
                return false;
            }
            target.load_Me_To_Another(this);
            nro.server.ServerNotify.gI().notify("Boss " + name + " vừa xuất hiện tại "
                    + target.map.mapName + " khu vực " + target.zoneId);
            System.out.println("NOEL_BOSS_SPAWN_CONFIRMED id="+ID+" name="+name+" map="
                    +target.map.mapId+" zone="+target.zoneId+" x="+location.x+" y="+location.y);
            NoelWeather.broadcastBossBell();
            return true;
        }
        return false;
    }

    @Override public synchronized void attack() { wander(); }
    @Override public synchronized void idle() { wander(); }

    private void wander() {
        if (!NoelEvent.active() || zone == null || isDie()) return;
        long now = System.currentTimeMillis();
        if (now < nextWalkAt || now < nextWanderAt) return;
        nextWalkAt = now + 250;
        if (walkTarget < 0 || Math.abs(walkTarget - location.x) <= 12) {
            var rng = ThreadLocalRandom.current();
            int direction = rng.nextBoolean() ? 1 : -1;
            walkTarget = Math.max(72, Math.min(zone.map.mapWidth - 72,
                    location.x + direction * rng.nextInt(96, 241)));
            nextWanderAt = now + rng.nextInt(1000, 2501);
            return;
        }
        int[] step = walkStep(location.x, location.y, walkTarget, zone.map.mapWidth,
                x -> zone.map.yPhysicInTop(x, Math.max(0, location.y - 24)));
        if (step == null) {
            walkTarget = -1;
            nextWanderAt = now + 1000;
            return;
        }
        PlayerService.gI().playerMove(this, step[0], step[1]);
    }

    // Walk along nearby ground only: avoid map gates, cliffs and gaps rather than teleport.
    static int[] walkStep(int x, int y, int target, int width, java.util.function.IntUnaryOperator ground) {
        if (width <= 144 || x < 72 || x > width - 72) return null;
        int destination = Math.max(72, Math.min(width - 72, target));
        int direction = Integer.compare(destination, x);
        if (direction == 0) return null;
        int nextX = x + direction * Math.min(12, Math.abs(destination - x));
        int previousY = y;
        for (int checkX = x + direction; ; checkX += direction) {
            int nextY = ground.applyAsInt(checkX);
            if (nextY <= 0 || Math.abs((long)nextY - previousY) > 24 || Math.abs((long)nextY - y) > 24) return null;
            previousY = nextY;
            if (checkX == nextX) break;
        }
        return new int[]{nextX, previousY};
    }

    static int damageTaken(int damage, int remainingHp) {
        if (damage <= 0 || remainingHp <= 0) return 0;
        return (int)Math.min(remainingHp, Math.min(MAX_HIT, Math.max(1L, damage / 10L)));
    }
    @Override public void checkPlayerDie(Player player) {}
    @Override public short getMount() { return -1; }
    @Override protected boolean useSpecialSkill() { return false; }
    @Override public void initTalk() {
        textTalkBefore = new String[0];
        textTalkMidle = new String[]{
            "zing zing zing dượng đây",
            "muốn quà gì dượng cho",
            "chực cà chực cà ớ ớ"
        };
        textTalkAfter = new String[0];
    }

    static int milestones(int hp) {
        return (int) (((long) HP - Math.max(0, hp)) * 10 / HP);
    }

    static int group(int roll) {
        if (roll < 45) return 0;
        if (roll < 70) return 1;
        if (roll < 84) return 2;
        if (roll < 90) return 3;
        if (roll < 95) return 4;
        if (roll < 98) return 5;
        return 6;
    }

    static int special(int roll) {
        return roll < 5 ? 1 : roll < 8 ? 2 : 0;
    }

    @Override public synchronized int injured(Player attacker, int damage, boolean piercing, boolean mobAttack) {
        if (!NoelEvent.active() || zone == null || attacker == null || mobAttack || damage <= 0 || isDie()) return 0;
        Player owner = attacker instanceof Pet pet ? pet.master : attacker;
        if (owner == null || owner.getSession() == null) return 0;
        int before = nPoint.hp;
        // Same event mitigation for ordinary and piercing hits, without flat armor subtraction.
        int applied = damageTaken(damage, before);
        int after = before - applied;
        var drops = new ArrayList<Item>();
        int target = milestones(after);
        var rng = ThreadLocalRandom.current();
        for (int milestone = paid; milestone < target; milestone++) drops.add(roll(rng));
        if (after == 0) {
            int reward = special(rng.nextInt(100));
            if (reward > 0) drops.add(prize(NoelEvent.rewardId(reward)));
        }
        nPoint.setHp(after);
        paid = target;
        for (int i = 0; i < drops.size(); i++) {
            Item item = drops.get(i);
            int x = Math.max(24, Math.min(zone.map.mapWidth - 24, location.x + i * 18 - 18));
            int y = zone.map.yPhysicInTop(x, Math.max(0, location.y - 24));
            var drop = new ItemMap(zone,item.template.id,item.quantity,x,y,owner.id);
            drop.options.addAll(item.itemOptions);
            Service.gI().dropItemMap(zone,drop);
        }
        if (after == 0) {
            NoelEvent.bossPoint(owner);
            die();
            notifyPlayeKill(owner);
        }
        return applied;
    }

    @Override public void rewards(Player player) {}

    static Item prize(short template) {
        return vip(withDefaults(ItemService.gI().createNewItem(template,1)),ThreadLocalRandom.current(),true);
    }

    public static Item vipWish(Item item) {
        if (!NoelEvent.active() || item == null || item.template == null || item.template.type != 5)
            return item;
        return vip(item, ThreadLocalRandom.current(), true);
    }

    private static Item roll(ThreadLocalRandom rng) {
       var service = ItemService.gI();
       return switch (group(rng.nextInt(100))) {
           case 0 -> service.createNewItem((short)2026,rng.nextInt(99,1000));
           case 1 -> service.createNewItem((short)861,rng.nextInt(50,201));
           case 2 -> service.createNewItem((short)rng.nextInt(925,932),1);
           case 3 -> CrystalStars.stock(rng,1).item();
           case 4 -> CrystalStars.stock(rng,2).item();
           case 5 -> vip(withDefaults(service.createNewItem((short)rng.nextInt(405,408),1)),rng,true);
           default -> activated(rng);
       };
    }

    private static Item activated(ThreadLocalRandom rng) {
        var service=ItemService.gI();
        var item=service.createNewItem((short)rng.nextInt(555,568),1);
        int faction=item.template.gender==3 ? rng.nextInt(3) : item.template.gender;
        int[] set=RewardService.ACTIVATION_SET[faction][rng.nextInt(3)];
        withDefaults(item);
        item.itemOptions.removeIf(option -> option.optionTemplate.id >= 127 && option.optionTemplate.id <= 144);
        item.itemOptions.add(new ItemOption(set[0],1));
        item.itemOptions.add(new ItemOption(set[1],1));
        return vip(item,rng,false);
    }

    private static Item withDefaults(Item item) {
        var defaults=AdminGrantItems.defaults(item.template.id,null);
        for(int i=0;i<defaults.length();i++) {
            var option=defaults.getJSONArray(i);
            item.itemOptions.add(new ItemOption(option.getInt(0),option.getInt(1)));
        }
        return item;
    }

    private static Item vip(Item item,ThreadLocalRandom rng,boolean percentageGear) {
        for (int id : MAIN_STATS)
            for (ItemOption option : item.itemOptions)
                if (option.optionTemplate.id == id)
                    option.param = Math.min(Short.MAX_VALUE,RubyStock.scaled(option.param,rng.nextInt(200,301)));
        if (percentageGear)
            for (int id : new int[]{50,77,103})
                if (item.itemOptions.stream().noneMatch(option->option.optionTemplate.id==id))
                    item.itemOptions.add(new ItemOption(id,RubyStock.scaled(35,rng.nextInt(200,301))));
        var choices=new ArrayList<Integer>();
        for(int id:VIP_OPTIONS)
            if(item.itemOptions.stream().noneMatch(option->option.optionTemplate.id==id)) choices.add(id);
        int count=rng.nextInt(1,3);
        while(count-- > 0 && !choices.isEmpty())
            item.itemOptions.add(new ItemOption(choices.remove(rng.nextInt(choices.size())),rng.nextInt(15,46)));
        return item;
    }
}
