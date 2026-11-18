package nro.progression;
import java.util.*;
import org.json.*;
import nro.models.item.*;
import nro.server.Manager;

public final class KaizenForgeTest {
 public static void main(String[] args) {
  for(int id=0;id<251;id++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(id,"fixture",0));
  // Fixture-only IDs. No production catalog is allocated by this test.
  for(int id=0;id<104;id++){var t=new ItemTemplate();t.id=(short)id;t.type=27;t.name="fixture";t.description="";Manager.ITEM_TEMPLATES.add(t);}
  var bindings=new EnumMap<KaizenRecipes,KaizenForge.Binding>(KaizenRecipes.class);
  int id=80;
  for(var recipe:KaizenRecipes.values()){Manager.ITEM_TEMPLATES.get(id+1).type=5;bindings.put(recipe,new KaizenForge.Binding(id,id+1,new JSONArray("[[50,35],[77,35]]")));id+=2;}
  var forge=new KaizenForge(bindings);
  for(var recipe:KaizenRecipes.values()) {
   var binding=bindings.get(recipe);
   var bag=new JSONArray().put(item(binding.fragmentId(),recipe.fragments/2)).put(item(binding.fragmentId(),recipe.fragments-recipe.fragments/2))
       .put(item(10,17).put("custom","preserve"));
   String before=bag.toString();
   var result=forge.craft(recipe,bag,recipe.gold+123);
   check(result.gold()==123,"gold debit");check(bag.toString().equals(before),"input unchanged");
   int fragments=0,equipment=0;
   for(Object raw:result.bag()){var row=(JSONObject)raw;if(row.getInt("temp_id")==binding.fragmentId())fragments+=row.getInt("quantity");if(row.getInt("temp_id")==binding.equipmentId())equipment+=row.getInt("quantity");}
   check(fragments==0&&equipment==1,"exact fragment consumption and one item");
   check(result.bag().getJSONObject(2).getString("custom").equals("preserve"),"unrelated saved fields");
   try{forge.craft(recipe,bag,recipe.gold-1);throw new AssertionError("insufficient gold");}catch(IllegalArgumentException expected){}
   try{forge.craft(recipe,new JSONArray().put(item(binding.fragmentId(),recipe.fragments-1)),recipe.gold);throw new AssertionError("insufficient fragments");}catch(IllegalArgumentException expected){}
   try{forge.craft(recipe,result.bag(),recipe.gold);throw new AssertionError("same ingredients reused");}catch(IllegalArgumentException expected){}
  }
  System.out.println("PASS seven Kaizen recipes: exact gold/fragments, original bag unchanged, unrelated fields preserved, insufficient resources rejected, consumed ingredients cannot be reused");
 }
 static JSONObject item(int id,int count){return new JSONObject().put("temp_id",id).put("quantity",count).put("option",new JSONArray()).put("create_time",123);}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
