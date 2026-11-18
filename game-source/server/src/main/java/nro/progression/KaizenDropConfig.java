package nro.progression;

import java.util.*;
import java.util.function.IntSupplier;
import org.json.*;

/** Source-specific configuration consumed by the existing RewardService. */
public final class KaizenDropConfig {
    public enum SourceType { BOSS, NORMAL_MOB, ELITE_MOB }
    public enum Form { WHOLE, FRAGMENT }
    public record Reward(KaizenRecipes kind,Form form,int chance,int quantity) {}
    public record Source(String key,SourceType type,int mapId,String mapName,String bossClass,int mobId,List<Reward> rewards) {
        public Source { rewards=List.copyOf(rewards); }
    }
    public record Parsed(List<Source> enabled,Map<String,String> disabled) {
        public Parsed { enabled=List.copyOf(enabled);disabled=Map.copyOf(disabled); }
    }
    public interface RuntimeCatalog {
        boolean mapExists(int id,String exactName);
        boolean bossRegistered(String className,int mapId);
        boolean mobExists(int templateId,int mapId);
        boolean itemReady(KaizenRecipes kind,Form form);
    }
    private KaizenDropConfig(){}
    public static Parsed parse(String text,RuntimeCatalog runtime) {
        JSONObject json=new JSONObject(text);
        if(json.getInt("version")!=1 || json.getInt("denominator")!=10000)
            throw new IllegalArgumentException("Unsupported Kaizen drop configuration");
        List<Source> enabled=new ArrayList<>();Map<String,String> disabled=new LinkedHashMap<>();Set<String> keys=new HashSet<>();
        JSONArray rows=json.getJSONArray("sources");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);String key=row.getString("key");
            if(!key.matches("[a-z0-9_]{1,64}") || !keys.add(key))throw new IllegalArgumentException("Duplicate/invalid source key");
            SourceType type=SourceType.valueOf(row.getString("source_type"));List<Reward> rewards=new ArrayList<>();
            JSONArray entries=row.getJSONArray("rewards");
            if(entries.isEmpty() || entries.length()>14)throw new IllegalArgumentException("Invalid reward count");
            Set<String> rewardKeys=new HashSet<>();
            for(int j=0;j<entries.length();j++) {
                JSONObject entry=entries.getJSONObject(j);KaizenRecipes kind=KaizenRecipes.valueOf(entry.getString("kind"));
                Form form=Form.valueOf(entry.getString("form"));int chance=entry.getInt("chance"),quantity=entry.getInt("quantity");
                if(chance<0 || chance>10000 || quantity<1 || quantity>999 || (form==Form.WHOLE && quantity!=1)
                    || (type!=SourceType.BOSS && form==Form.WHOLE) || !rewardKeys.add(kind+":"+form))
                    throw new IllegalArgumentException("Invalid Kaizen source reward: "+key);
                rewards.add(new Reward(kind,form,chance,quantity));
            }
            if(!row.getBoolean("enabled")) {disabled.put(key,row.optString("blocked_reason","Disabled"));continue;}
            int map=row.getInt("map_id");String mapName=row.getString("map_name");
            if(map<0 || mapName.isBlank() || !runtime.mapExists(map,mapName))throw new IllegalArgumentException("Unverified map: "+key);
            String boss=type==SourceType.BOSS?row.getString("boss_class"):"";
            int mob=type==SourceType.BOSS?-1:row.getInt("mob_id");
            if(type==SourceType.BOSS ? !runtime.bossRegistered(boss,map) : !runtime.mobExists(mob,map))
                throw new IllegalArgumentException("Unverified spawn: "+key);
            for(Reward reward:rewards)if(!runtime.itemReady(reward.kind(),reward.form()))
                throw new IllegalArgumentException("Unverified item/options: "+key);
            enabled.add(new Source(key,type,map,mapName,boss,mob,rewards));
        }
        // One source per actual spawn selector; overlapping rules could otherwise pay twice.
        Set<String> selectors=new HashSet<>();
        for(Source s:enabled)if(!selectors.add(s.type()+":"+s.mapId()+":"+s.bossClass()+":"+s.mobId()))
            throw new IllegalArgumentException("Overlapping Kaizen spawn rules");
        return new Parsed(enabled,disabled);
    }
    public static List<Reward> roll(Source source,IntSupplier existingRandom) {
        List<Reward> result=new ArrayList<>();
        for(Reward reward:source.rewards()) {
            int draw=existingRandom.getAsInt();
            if(draw<0 || draw>=10000)throw new IllegalArgumentException("Invalid random draw");
            if(draw<reward.chance())result.add(reward);
        }
        return result;
    }
}
