package nro.progression;

import java.lang.reflect.*;
import java.util.*;
import nro.models.item.*;
import nro.models.player.*;
import nro.server.Manager;
import nro.server.io.Message;
import nro.services.*;
import nro.services.func.*;

/** Real CombineServiceNew methods; deterministic success only in this disposable fixture. */
public final class KaizenCombineTest {
 public static final class Subject extends Player {@Override public void sendMessage(Message message){}}
 public static void main(String[] args)throws Exception {
  nro.server.Maintenance.isRuning=true;
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture #",0));
  for(int i=0;i<1100;i++){var t=new ItemTemplate();t.id=(short)i;t.name="fixture";t.description="";t.gender=3;t.type=27;Manager.ITEM_TEMPLATES.add(t);}
  var ids=new EnumMap<KaizenRecipes,Integer>(KaizenRecipes.class);
  int offset=1000;
  for(var kind:KaizenRecipes.values()) {ids.put(kind,offset);Manager.ITEM_TEMPLATES.get(offset++).type=(byte)(kind.ordinal()%5);}
  for(int id:new int[]{220,221,222,223,224})Manager.ITEM_TEMPLATES.get(id).type=14;
  KaizenEquipment.install(ids);
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
  var player=(Subject)unsafe.allocateInstance(Subject.class);player.name="fixture";player.inventory=new Inventory(player);player.combineNew=new CombineNew();
  var service=CombineServiceNew.gI();
  Method upgrade=method("nangCapVatPham"),holes=method("phaLeHoaTrangBi"),socket=method("epSaoTrangBi");
  int[] stoneIds={223,222,224,221,220};
  for(var kind:KaizenRecipes.values()) {
   var item=ItemService.gI().createNewItem(ids.get(kind).shortValue(),1);
   for(Object raw:KaizenStats.options(kind)){var option=(org.json.JSONArray)raw;item.itemOptions.add(new ItemOption(option.getInt(0),option.getInt(1)));}
   var stone=ItemService.gI().createNewItem((short)stoneIds[item.template.type],100);
   player.inventory.itemsBag.clear();player.inventory.itemsBag.add(item);player.inventory.itemsBag.add(stone);
   player.inventory.gold=1_000_000;player.inventory.gem=1_000_000;
   player.combineNew.itemsCombine=new ArrayList<>(List.of(item,stone));player.combineNew.goldCombine=10;player.combineNew.countDaNangCap=1;player.combineNew.ratioCombine=100;
   int expected=item.itemOptions.get(0).param,secondary=item.itemOptions.get(1).param,third=item.itemOptions.get(2).param;
   for(int level=1;level<=7;level++) {upgrade.invoke(service,player);expected+=expected*10/100;check(option(item,72)==level && item.itemOptions.get(0).param==expected,"Upgrade +"+level+" "+kind);}
   check(item.itemOptions.get(1).param==secondary && item.itemOptions.get(2).param==third,"Secondary stats changed");
   long gold=player.inventory.gold;int stones=stone.quantity;upgrade.invoke(service,player);
   check(option(item,72)==7 && gold==player.inventory.gold && stones==stone.quantity,"Upgrade exceeded +7 or charged at cap");
   player.combineNew.itemsCombine=new ArrayList<>(List.of(item));player.combineNew.goldCombine=10;player.combineNew.gemCombine=1;player.combineNew.ratioCombine=400;
   for(int stars=1;stars<=8;stars++){holes.invoke(service,player);check(option(item,107)==stars,"Original hole creation failed");}
   gold=player.inventory.gold;int gems=player.inventory.gem;holes.invoke(service,player);check(option(item,107)==8 && gold==player.inventory.gold && gems==player.inventory.gem,"Exceeded hole cap");
   var crystal=ItemService.gI().createNewItem((short)20,20);player.inventory.itemsBag.add(crystal);player.combineNew.itemsCombine=new ArrayList<>(List.of(item,crystal));
   for(int stars=1;stars<=8;stars++){socket.invoke(service,player);check(option(item,102)==stars,"Original socket attachment failed");}
   gems=player.inventory.gem;int crystals=crystal.quantity;socket.invoke(service,player);check(gems==player.inventory.gem && crystals==crystal.quantity,"Charged for unavailable socket");
  }
  var legacy=ItemService.gI().createNewItem((short)0,1);legacy.template.type=0;legacy.itemOptions.add(new ItemOption(47,100));
  var stone=ItemService.gI().createNewItem((short)223,10);player.inventory.itemsBag=new ArrayList<>(List.of(legacy,stone));player.combineNew.itemsCombine=new ArrayList<>(List.of(legacy,stone));
  player.combineNew.ratioCombine=100;upgrade.invoke(service,player);check(option(legacy,47)==110 && option(legacy,72)==1,"Original equipment upgrade regressed");
  System.out.println("PASS real CombineServiceNew: all 7 gear +0 through +7, correct main stat, secondary stats preserved, 8 holes/8 sockets, no charges past caps, original gear unchanged");
 }
 private static Method method(String name)throws Exception{var method=CombineServiceNew.class.getDeclaredMethod(name,Player.class);method.setAccessible(true);return method;}
 private static int option(Item item,int id){return item.itemOptions.stream().filter(o->o.optionTemplate.id==id).mapToInt(o->o.param).findFirst().orElse(0);}
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
