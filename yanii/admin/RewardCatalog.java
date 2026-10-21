package nro.admin;

import java.text.Normalizer;
import java.util.*;
import org.json.*;
import nro.server.Manager;
import nro.models.item.*;
import nro.models.shop.*;
import nro.power.*;

/** Catalog and templates come from this server, never from IDs on another server. */
public final class RewardCatalog {
    private RewardCatalog() {}
    private static String plain(String s) { return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('đ','d'); }
    static String group(ItemTemplate t) {
        String n=plain(t.name);
        if(t.type>=0&&t.type<=4)return "equipment";
        if(n.contains("dau than"))return "healing";
        if(n.contains("ngoc rong")||n.matches(".*ngoc (den|namek|namec).*"))return "dragonballs";
        if(n.contains("pha le")||n.contains("dui duc")||n.contains("hematite"))return "crystal";
        if(n.startsWith("da "))return "stones";
        if(n.contains("sach")||n.startsWith("bua "))return "growth";
        if(n.contains("manh"))return "materials";
        if(n.contains("trung")||n.contains("de tu")||n.contains("pet"))return "companion";
        if(n.contains("capsule")||n.contains("ve tinh")||n.contains("van bay"))return "support";
        if(n.contains("ve ")||n.contains("hop")||n.contains("ruong")||n.contains("phieu"))return "events";
        return "other";
    }
    static String tier(ItemTemplate t) {
        if(t.type<0||t.type>4)return "";
        String n=plain(t.name);
        if(n.contains("thien su"))return "angel";
        if(n.contains("huy diet"))return "destruction";
        if(n.contains("than linh"))return "divine";
        return "normal";
    }
    static JSONArray defaults(int id) {
        for(Shop s:Manager.SHOPS)for(TabShop tab:s.tabShops)for(ItemShop it:tab.itemShops)
            if(it.temp.id==id&&!it.options.isEmpty()) {
                JSONArray a=new JSONArray();for(ItemOption o:it.options)a.put(new JSONArray().put(o.optionTemplate.id).put(o.param));return a;
            }
        return new JSONArray();
    }
    static JSONArray items() {
        JSONArray a=new JSONArray();for(ItemTemplate t:Manager.ITEM_TEMPLATES) {
            String n=plain(t.name);java.util.regex.Matcher m=java.util.regex.Pattern.compile("cap\\s+(\\d+)").matcher(n);
            a.put(new JSONObject().put("id",t.id).put("name",t.name).put("type",t.type).put("gender",t.gender)
                .put("group",group(t)).put("tier",tier(t)).put("named_level",m.find()?Integer.parseInt(m.group(1)):0)
                .put("power_require",t.strRequire).put("base_options",defaults(t.id)));
        }return a;
    }
    static JSONArray limits() {
        JSONArray a=new JSONArray();List<PowerLimit> list=PowerLimitManager.getInstance().getPowers();
        for(int i=0;i<list.size();i++){PowerLimit p=list.get(i);a.put(new JSONObject().put("index",i).put("power",p.getPower()).put("hp",p.getHp()).put("mp",p.getMp()).put("damage",p.getDamage()).put("defense",p.getDefense()).put("critical",p.getCritical()));}return a;
    }
    static JSONArray expand(JSONObject b) {
        JSONArray out=new JSONArray();
        for(String key:List.of("gold","gem","ruby","power","potential"))if(b.has(key)&&!b.get(key).toString().matches("[0-9]{1,16}"))throw new IllegalArgumentException("Giá trị "+key+" phải là số nguyên không âm");
        for(String currency:List.of("gold","gem","ruby"))if(b.optLong(currency)>0)out.put(new JSONObject().put("kind","CURRENCY").put("currency",currency).put("amount",b.getLong(currency)).put("mode",b.optString("currency_mode","ADD")));
        if(b.optLong("power")>0||b.optLong("potential")>0||b.optBoolean("max_base"))out.put(new JSONObject().put("kind","POINTS").put("power",b.optLong("power")).put("potential",b.optLong("potential")).put("limit_index",b.optInt("limit_index",0)).put("max_base",b.optBoolean("max_base")));
        JSONArray explicit=b.optJSONArray("rewards");if(explicit!=null){if(explicit.length()>2500)throw new IllegalArgumentException("Tối đa 2500 phần thưởng");for(int i=0;i<explicit.length();i++){JSONObject r=explicit.getJSONObject(i);if(r.optString("kind").equals("BUNDLE"))throw new IllegalArgumentException("Không lồng gói quà");out.put(r);}}
        JSONObject filter=b.optJSONObject("selection");
        if(filter!=null){
            Set<Integer> ids=new HashSet<>();JSONArray selected=filter.optJSONArray("ids");if(selected!=null)for(int i=0;i<selected.length();i++)ids.add(selected.getInt(i));
            String mode=filter.optString("mode","selected"),g=filter.optString("group","all"),tier=filter.optString("tier","all");
            if(!Set.of("selected","all","filtered").contains(mode))throw new IllegalArgumentException("Kiểu chọn không hợp lệ");
            int quantity=filter.optInt("quantity",1),upgrade=filter.optInt("upgrade",0),stars=filter.optInt("stars",0),gender=filter.optInt("gender",-1),level=filter.optInt("named_level",0);
            if(quantity<1||quantity>9999||upgrade<0||upgrade>7||stars<0||stars>8)throw new IllegalArgumentException("Bản server hỗ trợ nâng +0–7, lỗ sao 0–8, số lượng 1–9999");
            int found=0;
            for(ItemTemplate t:Manager.ITEM_TEMPLATES){
                if(mode.equals("selected")&&!ids.contains((int)t.id))continue;
                if(!mode.equals("selected")&&!mode.equals("all")) {
                    if(!g.equals("all")&&!g.equals(group(t)))continue;
                    if(!tier.equals("all")&&!tier.equals(tier(t)))continue;
                    if(gender>=0&&t.gender!=gender&&t.gender<=2)continue;
                    if(level>0&&!plain(t.name).matches(".*cap\\s+"+level+"(?:\\D.*|$)"))continue;
                }
                found++;
                JSONArray opts=defaults(t.id);
                if(t.type>=0&&t.type<=4){
                    if(upgrade>0&&opts.isEmpty())throw new IllegalArgumentException("#"+t.id+" chưa có chỉ số gốc trong shop; chọn +0 hoặc phát riêng với chỉ số tùy chọn");
                    // Regenerate the upgrade from base options, not from an arbitrary forged stat.
                    int baseLevel=0;for(int j=0;j<opts.length();j++)if(opts.getJSONArray(j).getInt(0)==72)baseLevel=opts.getJSONArray(j).getInt(1);
                    if(upgrade>0&&upgrade<baseLevel)throw new IllegalArgumentException("#"+t.id+" chỉ có mẫu shop đã nâng +"+baseLevel+"; không tự giảm chỉ số");
                    int primary=-1,regen=-1;for(int j=0;j<opts.length();j++){int id=opts.getJSONArray(j).getInt(0);if(Set.of(47,6,0,7,14,22,23).contains(id))primary=j;else if(id==27||id==28)regen=j;}
                    for(int j:new int[]{primary,regen})if(j>=0){JSONArray o=opts.getJSONArray(j);long value=o.getLong(1);for(int k=baseLevel;k<upgrade;k++)value+=o.getInt(0)==14?1:value*10/100;o.put(1,value);}
                    if(upgrade>0)putOption(opts,72,upgrade);
                    if(stars>0)putOption(opts,107,stars); // Open slots only; no forged socketed crystal bonus.
                }
                out.put(new JSONObject().put("kind","ITEM").put("item_id",t.id).put("quantity",t.type>=0&&t.type<=4?1:quantity).put("options",opts));
            }
            if(mode.equals("selected")&&found!=ids.size())throw new IllegalArgumentException("Có ID vật phẩm không thuộc server");
        }
        if(out.isEmpty()||out.length()>3000)throw new IllegalArgumentException("Gói phải có 1–3000 phần thưởng");return out;
    }
    private static void putOption(JSONArray a,int id,int value){for(int i=0;i<a.length();i++)if(a.getJSONArray(i).getInt(0)==id){a.getJSONArray(i).put(1,value);return;}a.put(new JSONArray().put(id).put(value));}
}
