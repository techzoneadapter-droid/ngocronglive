package nro.progression;

import java.nio.file.*;
import org.json.*;

public final class KaizenDropConfigTest {
    private static final KaizenDropConfig.RuntimeCatalog VERIFIED=new KaizenDropConfig.RuntimeCatalog() {
        public boolean mapExists(int id,String name){return id==123 && name.equals("fixture");}
        public boolean bossRegistered(String name,int map){return name.equals("fixture.Boss") && map==123;}
        public boolean mobExists(int id,int map){return id==19 && map==123;}
        public boolean itemReady(KaizenRecipes kind,KaizenDropConfig.Form form){return true;}
    };
    public static void main(String[] args)throws Exception {
        String text=Files.readString(Path.of(args[0]));var parsed=KaizenDropConfig.parse(text,VERIFIED);
        check(parsed.enabled().isEmpty() && parsed.disabled().size()==14,"Prototype sources accidentally enabled");
        int[] expected={30,300,200,100,100,200,20,2000,2000,1500,2000,1500,1500,1000};
        var config=new JSONObject(text);var rows=config.getJSONArray("sources");
        for(int i=0;i<rows.length();i++) {
            var row=new JSONObject(rows.getJSONObject(i).toString());
            row.put("enabled",true).put("map_id",123).put("map_name","fixture").put("boss_class","fixture.Boss");
            var one=new JSONObject().put("version",1).put("denominator",10000).put("sources",new JSONArray().put(row));
            var source=KaizenDropConfig.parse(one.toString(),VERIFIED).enabled().get(0);
            check(source.rewards().get(0).chance()==expected[i],"Owner rate changed at "+i);
            int hits=0;
            for(int n=0;n<10000;n++){final int draw=n;hits+=KaizenDropConfig.roll(source,()->draw).size();}
            check(hits==expected[i],"Exact rate/boundary mismatch");
            row.put("map_id",124);reject(one,"Missing map accepted");row.put("map_id",123);
            row.put("boss_class","unregistered.Boss");reject(one,"Missing boss accepted");row.put("boss_class","fixture.Boss");
            row.put("source_type","NORMAL_MOB").put("mob_id",19);
            if(i<7)reject(one,"Normal mob dropped whole equipment");
        }
        var valid=new JSONObject(rows.getJSONObject(0).toString()).put("enabled",true).put("map_id",123).put("map_name","fixture").put("boss_class","fixture.Boss");
        reject(new JSONObject().put("version",1).put("denominator",10000).put("sources",new JSONArray().put(valid).put(new JSONObject(valid.toString()).put("key","duplicate_selector"))),"Overlapping sources accepted");
        int[][][] oracle={{{77,500},{50,200},{103,200}},{{50,500},{14,100},{77,200}},{{103,500},{77,200},{50,200}},{{77,400},{94,300},{50,200}},{{50,400},{77,200},{14,100}},{{103,400},{50,200},{94,200}},{{50,400},{77,300},{103,300}}};
        for(var kind:KaizenRecipes.values()) {
            var options=KaizenStats.options(kind);check(options.length()==3,"Three stats required");
            for(int j=0;j<3;j++) {var option=options.getJSONArray(j);check(option.getInt(0)==oracle[kind.ordinal()][j][0] && option.getInt(1)==oracle[kind.ordinal()][j][1],"x100 owner stat mismatch");}
            options.getJSONArray(0).put(1,0);check(KaizenStats.options(kind).getJSONArray(0).getInt(1)>0,"Shared mutable options");
        }
        System.out.println("PASS 14 exact source rates over 140000 draws, disabled prototypes, unknown map/boss rejection, no whole mob drops, overlap rejection, all 21 owner stats x100");
    }
    private static void reject(JSONObject value,String message){try{KaizenDropConfig.parse(value.toString(),VERIFIED);throw new AssertionError(message);}catch(IllegalArgumentException expected){}}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
