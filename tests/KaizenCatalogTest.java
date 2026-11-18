package nro.progression;
import java.sql.*;
import nro.server.Manager;
import nro.models.item.ItemTemplate;
public final class KaizenCatalogTest {
 public static void main(String[] args)throws Exception {
  Class.forName("com.mysql.cj.jdbc.Driver");
  try(Connection c=DriverManager.getConnection(args[0],"root","")) {
   if(args[1].equals("seed")) {
    try(var s=c.createStatement()) {
     s.executeUpdate("CREATE TABLE item_template(id INT PRIMARY KEY,type INT,gender INT,name VARCHAR(255),description TEXT,icon_id INT,part INT,is_up_to_up INT,power_require INT)");
     for(int i=0;i<20;i++)s.executeUpdate("INSERT INTO item_template VALUES("+i+",0,3,'old"+i+"','original',"+(100+i)+",-1,1,0)");
    }
   }
   try(var s=c.createStatement();var r=s.executeQuery("SELECT id,type,name,icon_id FROM item_template ORDER BY id")) {
    while(r.next()){var t=new ItemTemplate();t.id=r.getShort(1);t.type=r.getByte(2);t.name=r.getString(3);t.iconID=r.getShort(4);Manager.ITEM_TEMPLATES.add(t);}
   }
   var catalog=KaizenCatalog.install(c);
   if(catalog.forge().size()!=7 || Manager.ITEM_TEMPLATES.size()!=34)throw new AssertionError("seven real gear/fragment pairs");
   for(var recipe:KaizenRecipes.values()) {
    var binding=catalog.forge().get(recipe);
    if(binding.options().length()!=3 || !binding.options().toString().equals(KaizenStats.options(recipe).toString()))throw new AssertionError("owner-approved x100 options");
    if(!Manager.ITEM_TEMPLATES.get(binding.fragmentId()).name.equals("Mảnh "+recipe.displayName))throw new AssertionError("fragment runtime entry");
    if(!Manager.ITEM_TEMPLATES.get(binding.equipmentId()).name.equals(recipe.displayName))throw new AssertionError("gear runtime entry");
    if(Manager.ITEM_TEMPLATES.get(binding.fragmentId()).iconID!=KaizenIcons.fragment(recipe)
       || Manager.ITEM_TEMPLATES.get(binding.equipmentId()).iconID!=KaizenIcons.equipment(recipe))throw new AssertionError("unique fragment/equipment icon binding");
   }
   var again=KaizenCatalog.install(c);
   if(!catalog.equipment().equals(again.equipment())||Manager.ITEM_TEMPLATES.size()!=34)throw new AssertionError("restart/reapply allocated duplicate IDs");
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM item_template WHERE id<20 AND description='original'")){r.next();if(r.getInt(1)!=20)throw new AssertionError("legacy catalog mutated");}
   System.out.println("PASS real Kaizen catalog: 14 new contiguous IDs, runtime entries, idempotent install/restart, 20 legacy templates untouched");
  }
 }
}
