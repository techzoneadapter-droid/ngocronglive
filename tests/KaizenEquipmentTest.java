package nro.progression;
import java.util.*;
import java.lang.reflect.*;
import nro.models.item.*;
import nro.models.player.*;
import nro.services.*;
import nro.server.Manager;
public final class KaizenEquipmentTest {
 public static void main(String[] args)throws Exception {
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture",0));
  for(int i=0;i<100;i++){var t=new ItemTemplate();t.id=(short)i;t.name="fixture";t.description="";t.gender=3;t.type=0;Manager.ITEM_TEMPLATES.add(t);}
  var catalog=new EnumMap<KaizenRecipes,Integer>(KaizenRecipes.class);int id=80;
  for(var kind:KaizenRecipes.values())catalog.put(kind,id++);
  KaizenEquipment.install(catalog);
  Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var memory=(sun.misc.Unsafe)f.get(null);
  var player=(Player)memory.allocateInstance(Player.class);player.gender=0;player.inventory=new Inventory(player);player.nPoint=new NPoint(player);player.nPoint.power=Long.MAX_VALUE;
  for(int i=0;i<13;i++)player.inventory.itemsBody.add(ItemService.gI().createNewItem((short)i));
  var old=new ArrayList<>(player.inventory.itemsBody);
  Method equip=InventoryService.class.getDeclaredMethod("putItemBody",Player.class,Item.class);equip.setAccessible(true);
  for(var kind:KaizenRecipes.values()) {
   Item item=ItemService.gI().createNewItem(catalog.get(kind).shortValue());
   Item previous=(Item)equip.invoke(InventoryService.gI(),player,item);
   check(!previous.isNotNullItem(),"first equip replaces only empty new slot");
   check(player.inventory.itemsBody.get(13+kind.ordinal())==item,"correct dedicated slot");
  }
  check(player.inventory.itemsBody.size()==20,"seven appended slots");
  for(int i=0;i<13;i++)check(player.inventory.itemsBody.get(i)==old.get(i),"original slots untouched");
  for(var kind:KaizenRecipes.values()) {
   Item previous=player.inventory.itemsBody.get(13+kind.ordinal());Item replacement=ItemService.gI().createNewItem(catalog.get(kind).shortValue());
   check(equip.invoke(InventoryService.gI(),player,replacement)==previous,"swap returns previous equipment");
  }
  KaizenEquipment.ensureSlots(player);check(player.inventory.itemsBody.size()==20,"slot initialization idempotent");
  check(KaizenEquipment.slot(1)==-1,"old equipment routing unchanged");
  System.out.println("PASS actual InventoryService: seven dedicated slots, original 13 preserved, equipment swap returns original item, repeated initialization stable");
 }
 static void check(boolean test,String label){if(!test)throw new AssertionError(label);}
}
