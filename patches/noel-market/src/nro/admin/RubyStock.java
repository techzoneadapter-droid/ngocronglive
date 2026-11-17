package nro.admin;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;
import nro.jdbc.DBHika;
import nro.models.item.Item;
import nro.models.item.ItemOption;
import nro.server.Manager;
import nro.services.ItemService;
import org.json.JSONArray;

final class RubyStock {
    static final long OWNER = -1;
    static final int COUNT = 10;
    static final int CATEGORIES = 8;
    private static final int[] COSTUMES = {405,406,407};
    private static final int[] EXTRA = {5,19,94,95,96,97};
    private static Map<Integer, Integer> npcStats = Map.of();

    static void loadNpcBaselines() throws SQLException {
        var values = new HashMap<Integer, Integer>();
        try (var connection = DBHika.getConnection(); var query = connection.createStatement();
             var rows = query.executeQuery("""
                 SELECT s.temp_id,o.option_id,MAX(o.param)
                 FROM item_shop s JOIN item_shop_option o ON o.item_shop_id=s.id
                 WHERE s.is_sell=1 GROUP BY s.temp_id,o.option_id
                 """)) {
            while (rows.next()) values.put(rows.getInt(1) * 256 + rows.getInt(2), rows.getInt(3));
        }
        npcStats = Map.copyOf(values);
    }

    record Stock(Item item, int price) {}

    static int priceInRange(int minimum, int maximum, int score) {
        int bounded = Math.max(0, Math.min(1000, score));
        long raw = minimum + ((maximum - (long) minimum) * bounded + 999) / 1000;
        return (int) Math.min(maximum, ((raw + 999) / 1000) * 1000);
    }

    static Stock roll(ThreadLocalRandom rng) {
        return roll(rng, rng.nextInt(8));
    }

    static Stock roll(ThreadLocalRandom rng, int category) {
        Stock stock = switch(category) {
            case 0 -> activation(rng);
            case 1 -> equipment(555 + rng.nextInt(13), 25_000, 60_000, rng);
            case 2 -> equipment(650 + rng.nextInt(13), 50_000, 120_000, rng);
            case 3 -> costume(rng);
            case 4 -> egg(rng);
            case 5 -> potara(rng);
            case 6 -> CrystalStars.stock(rng, 1);
            case 7 -> CrystalStars.stock(rng, 2);
            default -> featured(rng, category);
        };
        return new Stock(stock.item(), Math.max(1, stock.price()/2));
    }

    static int featuredId(int category) {
        return switch(category) {
            case 8 -> 2158;
            case 9 -> 2159;
            case 10 -> 2161;
            case 11 -> Integer.getInteger("nero.noel.costume", 2179);
            default -> throw new IllegalArgumentException("Unknown featured costume");
        };
    }

    private static Stock featured(ThreadLocalRandom rng, int category) {
        Stock vip = costume(rng);
        Item featured = item(featuredId(category));
        for(ItemOption option : vip.item().itemOptions)
            featured.itemOptions.add(new ItemOption(option.optionTemplate.id,
                Math.min(Short.MAX_VALUE, Math.multiplyExact(option.param, 10))));
        return new Stock(featured, vip.price());
    }

    private static Item item(int id) {
        if (id>=Manager.ITEM_TEMPLATES.size() || Manager.ITEM_TEMPLATES.get(id).id!=id)
            throw new IllegalArgumentException("Missing generated stock template: "+id);
        return ItemService.gI().createNewItem((short)id);
    }

    private static Stock activation(ThreadLocalRandom rng) {
        JSONArray rewards=StarterGiftBoxes.activation(rng.nextInt(3),rng.nextInt(3)).getJSONArray("rewards");
        var reward=rewards.getJSONObject(rng.nextInt(rewards.length()));
        Item item=item(reward.getInt("item_id"));
        var options=reward.getJSONArray("options");
        for(int i=0;i<options.length();i++){
            JSONArray option=options.getJSONArray(i);
            if(option.getInt(0)!=107 && option.getInt(0)!=30)
                item.itemOptions.add(new ItemOption(option.getInt(0),option.getInt(1)));
        }
        return gear(item,10_000,30_000,rng);
    }

    private static Stock equipment(int id,int minimum,int maximum,ThreadLocalRandom rng) {
        Item item=item(id);
        if (id >= 650 && id <= 662) {
            item = ItemService.gI().randomCS_DHD(id, item.template.gender);
            item.itemOptions.removeIf(option -> option.optionTemplate.id == 30);
            return gear(item, minimum, maximum, rng);
        }
        var options=AdminGrantItems.defaults(id,null);
        for(int i=0;i<options.length();i++){
            JSONArray option=options.getJSONArray(i);
            item.itemOptions.add(new ItemOption(option.getInt(0),option.getInt(1)));
        }
        return gear(item,minimum,maximum,rng);
    }

    private static Stock gear(Item item,int minimum,int maximum,ThreadLocalRandom rng) {
        int quality=rng.nextInt(200, 326);
        for(ItemOption option:item.itemOptions) {
            int id=option.optionTemplate.id;
            if(id==0||id==6||id==7||id==14||id==22||id==23||id==27||id==28||id==47||id==48) {
                int baseline = baseStat(item, option);
                option.param = scaled(baseline, quality);
            }
        }
        int level=rng.nextInt(5,9), sockets=rng.nextInt(5,9);
        set(item,72,level);
        set(item,107,sockets);
        int bonus = extraOptions(item, rng, 5, 45);
        int score = (quality-200)*700/125 + level*100/8
                + (sockets-1)*100/7 + bonus*100/90;
        return new Stock(item,priceInRange(minimum,maximum,score));
    }

    private static Stock costume(ThreadLocalRandom rng) {
        Item costume=item(COSTUMES[rng.nextInt(COSTUMES.length)]);
        int attackBase = Math.max(350, npcStats.getOrDefault(costume.template.id * 256 + 50, 0));
        int hpBase = Math.max(350, npcStats.getOrDefault(costume.template.id * 256 + 77, 0));
        int kiBase = Math.max(350, npcStats.getOrDefault(costume.template.id * 256 + 103, 0));
        int attackPercent=rng.nextInt(200,401), hpPercent=rng.nextInt(200,401), kiPercent=rng.nextInt(200,401);
        int attack=scaled(attackBase,attackPercent);
        int hp=scaled(hpBase,hpPercent);
        int ki=scaled(kiBase,kiPercent);
        set(costume,50,attack);set(costume,77,hp);set(costume,103,ki);
        int bonus=extraOptions(costume,rng,50,500);
        int primaryScore = (attackPercent + hpPercent + kiPercent - 600)*700/600;
        int score = primaryScore + bonus*300/1000;
        long strongExtras = costume.itemOptions.stream()
                .filter(option -> option.optionTemplate.id != 50 && option.optionTemplate.id != 77
                        && option.optionTemplate.id != 103 && option.param >= 300).count();
        boolean rare = strongExtras == 2 && attackPercent + hpPercent + kiPercent >= 1050;
        int average = (attackPercent + hpPercent + kiPercent) / 3;
        return new Stock(costume,costumePrice(score,average,bonus,rare));
    }

    static int costumePrice(int score,int primaryAverage,int extras,boolean rare) {
        if (rare) return priceInRange(150_000,250_000,score);
        if (primaryAverage >= 275 || extras >= 350) return priceInRange(70_000,120_000,score);
        return priceInRange(30_000,60_000,score);
    }

    static int scaled(int baseline, int percent) {
        return Math.toIntExact((baseline * (long) percent + 99) / 100);
    }

    static int uplift(int value, int baseline) {
        if (baseline <= 0) throw new IllegalArgumentException("Missing positive stat baseline");
        return Math.max(0, Math.toIntExact((value * 100L + baseline - 1) / baseline) - 100);
    }

    private static int baseStat(Item item, ItemOption option) {
        int baseline = Math.max(option.param,
                npcStats.getOrDefault(item.template.id * 256 + option.optionTemplate.id, 0));
        if (item.template.id < 650 || item.template.id > 662) return baseline;
        int nativeMaximum = switch(option.optionTemplate.id) {
            case 0 -> 9514;
            case 14 -> 19;
            case 22 -> 111;
            case 23 -> 100;
            case 47 -> 3081;
            default -> option.param;
        };
        return Math.max(baseline, nativeMaximum);
    }

    private static int extraOptions(Item item, ThreadLocalRandom rng, int minimum, int maximum) {
        var available = new java.util.ArrayList<Integer>();
        for (int id : EXTRA)
            if (item.itemOptions.stream().noneMatch(option -> option.optionTemplate.id == id)) available.add(id);
        int total = 0;
        int count = rng.nextInt(1, 3);
        for (int index = 0; index < count && !available.isEmpty(); index++) {
            int id = available.remove(rng.nextInt(available.size()));
            int value = rng.nextInt(minimum, maximum + 1);
            item.itemOptions.add(new ItemOption(id, value));
            total += value;
        }
        return total;
    }

    private static Stock egg(ThreadLocalRandom rng) {
        if(rng.nextBoolean() && EggHunt.JIREN<Manager.ITEM_TEMPLATES.size())
            return new Stock(item(EggHunt.JIREN),4_000);
        return new Stock(item(568),2_000);
    }

    private static Stock potara(ThreadLocalRandom rng) {
        boolean hasThird = Manager.ITEM_TEMPLATES.size() > 1128
                && Manager.ITEM_TEMPLATES.get(1128).name.toLowerCase(Locale.ROOT).contains("porata");
        int level=rng.nextInt(2,hasThird?4:3);
        return potara(level);
    }

    static Stock potara(int level) {
        Item potara=item(switch(level) {
            case 2 -> 921;
            case 3 -> 1128;
            default -> throw new IllegalArgumentException("Unknown Porata tier");
        });
        return new Stock(potara,potaraPrice(level));
    }

    static int potaraPrice(int level) {
        if (level < 2 || level > 3) throw new IllegalArgumentException("Unsupported Porata tier");
        return 10_000 << (level - 1);
    }

    private static void set(Item item,int option,int value) {
        item.itemOptions.removeIf(o->o.optionTemplate.id==option);
        if(value>0)item.itemOptions.add(new ItemOption(option,value));
    }
}
