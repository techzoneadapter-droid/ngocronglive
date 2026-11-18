package nro.progression;

import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import nro.admin.AdminGrantItems;

/** Pure forging plan. The NPC must commit the plan and request ledger together before installing it. */
public final class KaizenForge {
    public record Binding(int fragmentId,int equipmentId,JSONArray options) {
        public Binding {
            if(fragmentId<0 || equipmentId<0 || fragmentId==equipmentId || options==null)
                throw new IllegalArgumentException("Invalid catalog binding");
            options=new JSONArray(options.toString());
        }
        @Override public JSONArray options(){return new JSONArray(options.toString());}
    }
    public record Plan(JSONArray bag,long gold) {}
    private final Map<KaizenRecipes,Binding> bindings;
    public KaizenForge(Map<KaizenRecipes,Binding> bindings) {
        this.bindings=Map.copyOf(bindings);
        var ids=new java.util.HashSet<Integer>();
        for(var binding:bindings.values())
            if(!ids.add(binding.fragmentId()) || !ids.add(binding.equipmentId()))
                throw new IllegalArgumentException("Kaizen item IDs must be distinct");
    }
    public Plan craft(KaizenRecipes recipe,JSONArray original,long gold) {
        var binding=bindings.get(recipe);
        if(binding==null)throw new IllegalArgumentException("Equipment is not available in the real catalog yet");
        if(gold<recipe.gold)throw new IllegalArgumentException("Không đủ vàng");
        JSONArray bag=new JSONArray(original.toString());
        long available=0;
        for(int index=0;index<bag.length();index++) {
            JSONObject item=bag.getJSONObject(index);
            if(item.getInt("temp_id")==binding.fragmentId()) {
                int count=item.getInt("quantity");
                if(count<0)throw new IllegalArgumentException("Invalid fragment stack");
                available=Math.addExact(available,count);
            }
        }
        if(available<recipe.fragments)throw new IllegalArgumentException("Không đủ mảnh trang bị");
        int remaining=recipe.fragments;
        for(int index=0;index<bag.length()&&remaining>0;index++) {
            JSONObject item=bag.getJSONObject(index);
            if(item.getInt("temp_id")!=binding.fragmentId())continue;
            int used=Math.min(remaining,item.getInt("quantity"));
            remaining-=used;
            int left=item.getInt("quantity")-used;
            if(left>0)item.put("quantity",left);
            else bag.put(index,new JSONObject().put("temp_id",-1).put("quantity",0).put("option",new JSONArray()).put("create_time",0));
        }
        var reward=new JSONObject().put("kind","ITEM").put("item_id",binding.equipmentId())
                .put("quantity",1).put("options",binding.options()).put("destination","BAG");
        JSONArray result=AdminGrantItems.addSaved(bag,reward);
        if(result==null)throw new IllegalArgumentException("Hành trang không đủ chỗ");
        return new Plan(result,Math.subtractExact(gold,recipe.gold));
    }
}
