package nro.models.kigui;
import java.util.*;
import java.io.*;
import nro.admin.ConsignmentStore;
import nro.admin.ConsignmentStore.Listing;
import nro.models.item.*;
import nro.models.player.Player;
import nro.server.io.Message;
import nro.services.*;
/** Native shop wire format with per-view unique handles, durable escrow and exact prices. */
public final class KiGuiShop {
 private static final KiGuiShop INSTANCE=new KiGuiShop();
 public static KiGuiShop getInstance(){return INSTANCE;}
 private record Handle(long listing,int slot,Item original,int quantity,long created){}
 private static final class View {int currency;long expires;short next=1;Map<Short,Handle> handles=new HashMap<>();}
 private final Map<Player,View> views=Collections.synchronizedMap(new WeakHashMap<>());
 private final List<KiGuiItem> legacy=new ArrayList<>();
 public String[] tabName={"Trang bị","Phụ kiện","Hỗ trợ","Linh tinh","Đồ của bạn"};
 public String[] tabNameSuKien={"1","2","3","4","Đồ của bạn"};
 public List<KiGuiItem> getList(){return legacy;}
 public void addItem(KiGuiItem i){legacy.add(i);}
 public void addExpiredItem(KiGuiItem i){}
 public void removeItem(KiGuiItem i){legacy.remove(i);}
 public KiGuiItem findItemConsign(long owner,short id){return null;}
 public int getDaysExpried(Long time){return (int)Math.max(0,(System.currentTimeMillis()-time)/86400000L);}
 public void showExpiringItems(Player p){notice(p,"Đồ ký gửi được giữ tới khi bán hoặc chủ gửi hủy; không tự xóa.");}
 public void sendExpirationNotification(Player p){}
 private static void notice(Player p,String s){Service.gI().sendThongBao(p,s);}
 public void open(Player p,int mode){ConsignmentStore.currencyName(mode);View v=new View();v.currency=mode;views.put(p,v);show(p);}
 private View view(Player p){View v=views.get(p);if(v==null||System.currentTimeMillis()>v.expires||p.zone==null||p.zone.map.mapId!=84)throw new IllegalArgumentException("Hãy mở lại cửa hàng ký gửi tại Siêu Thị");return v;}
 public void handler(Player p,Message m){
  try{synchronized(p){View v=view(p);DataInputStream in=m.reader();int action=in.readUnsignedByte();
   if(action==4){nextPage(p,in.readUnsignedByte(),in.readUnsignedByte());return;}
   short token=in.readShort();Handle h=v.handles.get(token);if(h==null)throw new IllegalArgumentException("Danh sách đã đổi; hãy mở lại cửa hàng");
   if(action==0){int wire=in.readUnsignedByte(),price=in.readInt(),quantity=readQuantity(in);if(!validWireCurrency(v.currency,wire)||h.listing()!=0||h.original().quantity!=h.quantity()||h.original().createTime!=h.created())throw new IllegalArgumentException("Vật phẩm/loại giá đã đổi");ConsignmentStore.post(p,h.slot(),h.original(),quantity,v.currency,price);}
   else if(action==3){int wire=in.readUnsignedByte(),price=in.readInt();if(!validWireCurrency(v.currency,wire)||h.listing()==0)throw new IllegalArgumentException("Giá không hợp lệ");ConsignmentStore.act(p,h.listing(),3,v.currency,price);}
   else if(action==1||action==2){if(h.listing()==0)throw new IllegalArgumentException("Món này chưa ký gửi");ConsignmentStore.act(p,h.listing(),action,v.currency,0);}
   else throw new IllegalArgumentException("Chức năng không hỗ trợ");
   InventoryService.gI().sendItemBags(p);Service.gI().sendMoney(p);show(p);notice(p,action==0?"Đã ký gửi. Phí 5% chỉ thu khi bán thành công.":action==3?"Mua thành công":action==1?"Đã hủy và nhận lại đồ":"Đã nhận tiền sau phí 5%");
  }}catch(IllegalArgumentException e){notice(p,e.getMessage());}catch(Exception e){System.err.println("Consignment failed: "+e.getClass().getSimpleName());notice(p,"Chưa hoàn tất giao dịch. Đồ và tiền chưa bị trừ; hãy mở lại cửa hàng.");}
 }
 static boolean validWireCurrency(int selectedMarket, int wire) {
  return wire == 0 || (wire == 1 && selectedMarket == ConsignmentStore.RUBY);
 }
 static int readQuantity(DataInputStream in) throws IOException {
  return switch (in.available()) {
   case 1 -> in.readUnsignedByte();
   case 4 -> in.readInt();
   default -> throw new IOException("Invalid consignment quantity length");
  };
 }
 private int tab(Item i){return i.template.type==0||i.template.type==1||i.template.type==3?0:i.template.type==2||i.template.type==4?1:i.template.type==12?3:2;}
 private short handle(View v,Handle h){if(v.next>=30000)throw new IllegalArgumentException("Hãy mở lại cửa hàng");short id=v.next++;v.handles.put(id,h);return id;}
 private void row(DataOutputStream out,Player p,View v,Item item,long listing,long owner,int price,boolean sold,int slot,boolean firstPage)throws IOException{
  out.writeShort(item.template.id);out.writeShort(handle(v,new Handle(listing,slot,listing==0?item:null,item.quantity,item.createTime)));
  boolean ruby = v.currency == ConsignmentStore.RUBY;
  out.writeInt(ruby ? -1 : price);out.writeInt(ruby ? price : -1);
  out.writeByte(listing==0?0:sold?2:1);
  if(!firstPage || p.isVersionAbove(222))out.writeInt(item.quantity);else out.writeByte(Math.min(99,item.quantity));
  List<ItemOption> options=item.getDisplayOptions();
  out.writeByte(owner==p.id?1:0);out.writeByte(options.size());
  for(ItemOption o:options){out.writeByte(o.optionTemplate.id);out.writeShort(Math.min(Short.MAX_VALUE,o.param));}
  if(firstPage) out.writeByte(0); // Only initial shop response has a new-item flag.
  out.writeByte(0); // Optional costume parts.
 }
 private List<Listing> publicRows(List<Listing> all,int tab){return all.stream().filter(l->!l.sold()&&tab(l.item())==tab).toList();}
 private void own(DataOutputStream out,Player p,View v,List<Listing> all)throws IOException{
  List<Listing> mine=all.stream().filter(l->l.owner()==p.id).toList();List<Integer> bag=new ArrayList<>();for(int i=0;i<p.inventory.itemsBag.size();i++)if(ConsignmentStore.allowed(p.inventory.itemsBag.get(i)))bag.add(i);
  int count=Math.min(100,mine.size()+bag.size());out.writeUTF("Đồ của bạn");out.writeByte(1);out.writeByte(count);int n=0;
  for(Listing l:mine){if(n++>=count)break;row(out,p,v,l.item(),l.id(),l.owner(),l.price(),l.sold(),-1,true);}
  for(int slot:bag){if(n++>=count)break;row(out,p,v,p.inventory.itemsBag.get(slot),0,-1,-1,false,slot,true);}
 }
 public void show(Player p){try{synchronized(p){View v=views.get(p);if(v==null){v=new View();views.put(p,v);}v.expires=System.currentTimeMillis()+300000;v.handles.clear();List<Listing> all=ConsignmentStore.list(v.currency,p.id);Message m=new Message(-44);try{DataOutputStream out=m.writer();out.writeByte(2);out.writeByte(5);for(int t=0;t<4;t++){List<Listing> rows=publicRows(all,t);out.writeUTF(tabName[t]+"\n"+ConsignmentStore.currencyName(v.currency));out.writeByte(Math.max(1,(rows.size()+19)/20));out.writeByte(Math.min(20,rows.size()));for(Listing l:rows.subList(0,Math.min(20,rows.size())))row(out,p,v,l.item(),l.id(),l.owner(),l.price(),l.sold(),-1,true);}own(out,p,v,all);p.sendMessage(m);}finally{m.cleanup();}}}catch(Exception e){System.err.println("Consignment open failed: "+e.getClass().getSimpleName()+": "+e.getMessage());Service.gI().hideWaitDialog(p);notice(p,"Chưa mở được ký gửi: "+e.getClass().getSimpleName());}}
 public void nextPage(Player p,byte tab,int page){nextPage(p,(int)tab,page);}
 public void nextPage(Player p,int tab,int page){try{synchronized(p){View v=view(p);if(tab<0||tab>3||page<0)throw new IllegalArgumentException("Trang không hợp lệ");List<Listing> rows=publicRows(ConsignmentStore.list(v.currency,p.id),tab);int pages=Math.max(1,(rows.size()+19)/20);if(page>=pages)throw new IllegalArgumentException("Trang không tồn tại");int start=Math.min(rows.size(),page*20),end=Math.min(rows.size(),start+20);Message m=new Message(-100);try{DataOutputStream out=m.writer();out.writeByte(tab);out.writeByte(pages);out.writeByte(page);out.writeByte(end-start);for(Listing l:rows.subList(start,end))row(out,p,v,l.item(),l.id(),l.owner(),l.price(),l.sold(),-1,false);p.sendMessage(m);}finally{m.cleanup();}}}catch(Exception e){notice(p,"Không mở được trang; hãy vào lại cửa hàng");}}
 public void buy(Player p,short token,byte wire,int price){notice(p,"Hãy chọn mua từ danh sách cửa hàng đang mở");}
 public void getMoney(Player p,short token){notice(p,"Hãy chọn nhận tiền trong cửa hàng đang mở");}
}
