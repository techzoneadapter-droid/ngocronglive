package nro.progression;
import java.io.*;
import java.util.*;
import nro.models.item.*;
import nro.models.player.*;
import nro.server.Manager;
import nro.server.io.Message;
import nro.services.*;
public final class InventoryPacketTest {
 public static final class Viewer extends Player {byte[] packet;@Override public void sendMessage(Message message){packet=message.getData().clone();}}
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);
  for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture #",0));
  for(int i=0;i<20;i++){var t=new ItemTemplate();t.id=(short)i;t.type=0;t.name="fixture";t.description="";Manager.ITEM_TEMPLATES.add(t);}
  var viewer=(Viewer)unsafe.allocateInstance(Viewer.class);viewer.inventory=new Inventory(viewer);
  for(int pass=0;pass<4;pass++) {
   viewer.inventory.itemsBag.clear();
   for(int i=0;i<20;i++) {
    var item=(pass==0 || (pass==1 && i%2==0) || pass==3)?ItemService.gI().createItemNull():ItemService.gI().createNewItem((short)i,1);
    if(item.isNotNullItem())item.itemOptions.add(new ItemOption(77,500));
    viewer.inventory.itemsBag.add(item);
   }
   InventoryService.gI().sendItemBags(viewer);
   check(viewer.packet!=null,"No inventory packet");var in=new DataInputStream(new ByteArrayInputStream(viewer.packet));
   check(in.readUnsignedByte()==0 && in.readUnsignedByte()==20,"Incorrect capacity");
   int occupied=0;
   for(int i=0;i<20;i++) {
    short id=in.readShort();if(id==-1)continue;
    occupied++;check(id==viewer.inventory.itemsBag.get(i).template.id,"Slot mismatch after compaction");
    check(in.readInt()==1,"Incorrect quantity");in.readUTF();in.readUTF();int count=in.readUnsignedByte();
    check(count==1 && in.readUnsignedByte()==77 && in.readUnsignedShort()==500,"Option lost");
   }
   check(in.available()==0,"Trailing or missing slot bytes");check(occupied==(pass==0 || pass==3?0:pass==1?10:20),"Wrong occupied slots");
  }
  System.out.println("PASS actual inventory packet: all empty, alternating empty, full and cleared bags decode exactly with capacity and options preserved");
 }
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
