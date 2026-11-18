package nro.progression;

import java.sql.*;
import java.util.*;
import nro.models.item.ItemTemplate;
import nro.server.Manager;
import org.json.JSONArray;

/** Explicit startup migration; allocates real unused IDs and verifies persisted mappings on restart. */
public final class KaizenCatalog {
    public record Catalog(Map<KaizenRecipes,KaizenForge.Binding> forge,Map<KaizenRecipes,Integer> equipment) {}
    private KaizenCatalog(){}
    public static Catalog install(Connection c)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Catalog installation requires standalone connection");
        try(var s=c.createStatement()) {
            s.executeUpdate("""
                CREATE TABLE IF NOT EXISTS nero_kaizen_catalog (
                    kind VARCHAR(32) PRIMARY KEY,fragment_id INT NOT NULL UNIQUE,equipment_id INT NOT NULL UNIQUE
                ) ENGINE=InnoDB
                """);
        }
        c.setAutoCommit(false);
        var pending=new ArrayList<ItemTemplate>();
        var forge=new EnumMap<KaizenRecipes,KaizenForge.Binding>(KaizenRecipes.class);
        var equipment=new EnumMap<KaizenRecipes,Integer>(KaizenRecipes.class);
        try {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT GET_LOCK('nero_kaizen_catalog_install',5)")) {
                if(!r.next()||r.getInt(1)!=1)throw new SQLException("Catalog migration busy");
            }
            for(var recipe:KaizenRecipes.values()) {
                int fragment=-1,item=-1;
                try(var q=c.prepareStatement("SELECT fragment_id,equipment_id FROM nero_kaizen_catalog WHERE kind=?")) {
                    q.setString(1,recipe.name());try(var r=q.executeQuery()){if(r.next()){fragment=r.getInt(1);item=r.getInt(2);}}
                }
                if(fragment<0) {
                    int next;
                    try(var s=c.createStatement();var r=s.executeQuery("SELECT COALESCE(MAX(id),-1)+1 FROM item_template")){r.next();next=r.getInt(1);}
                    if(next!=Manager.ITEM_TEMPLATES.size()+pending.size()||next+1>Short.MAX_VALUE)
                        throw new SQLException("Item catalog is not contiguous or full");
                    fragment=next;item=next+1;
                    // Existing icon resource/normal gear types keep native combine and trade semantics.
                    int donor=switch(recipe){case CROWN,BRACELET,NECKLACE -> 12;case CAPE,SCARF -> 0;case MASK,HALO -> 6;};
                    if(donor>=Manager.ITEM_TEMPLATES.size())throw new SQLException("Native icon donor missing");
                    short icon=Manager.ITEM_TEMPLATES.get(donor).iconID;
                    pending.add(insert(c,fragment,(byte)27,"Mảnh "+recipe.displayName,icon));
                    byte type=switch(recipe){case CROWN,BRACELET,NECKLACE -> 4;case CAPE,SCARF -> 0;case MASK -> 2;case HALO -> 3;};
                    pending.add(insert(c,item,type,recipe.displayName,icon));
                    try(var q=c.prepareStatement("INSERT INTO nero_kaizen_catalog(kind,fragment_id,equipment_id) VALUES(?,?,?)")) {
                        q.setString(1,recipe.name());q.setInt(2,fragment);q.setInt(3,item);q.executeUpdate();
                    }
                } else {
                    if(fragment>=Manager.ITEM_TEMPLATES.size()||item>=Manager.ITEM_TEMPLATES.size()
                        ||!Manager.ITEM_TEMPLATES.get(fragment).name.equals("Mảnh "+recipe.displayName)
                        ||!Manager.ITEM_TEMPLATES.get(item).name.equals(recipe.displayName))
                        throw new SQLException("Kaizen catalog mapping mismatch; refusing reallocation");
                }
                forge.put(recipe,new KaizenForge.Binding(fragment,item,KaizenStats.options(recipe)));
                equipment.put(recipe,item);
            }
            c.commit();
            Manager.ITEM_TEMPLATES.addAll(pending);
            return new Catalog(Map.copyOf(forge),Map.copyOf(equipment));
        }catch(Exception error){c.rollback();throw error;}
        finally {
            try(var s=c.createStatement()){s.executeQuery("SELECT RELEASE_LOCK('nero_kaizen_catalog_install')").close();}
            c.setAutoCommit(true);
        }
    }
    private static ItemTemplate insert(Connection c,int id,byte type,String name,short icon)throws SQLException {
        try(var q=c.prepareStatement("INSERT INTO item_template(id,type,gender,name,description,icon_id,part,is_up_to_up,power_require) VALUES(?,?,3,?,'',?,-1,1,0)")) {
            q.setInt(1,id);q.setByte(2,type);q.setString(3,name);q.setShort(4,icon);q.executeUpdate();
        }
        var t=new ItemTemplate();t.id=(short)id;t.type=type;t.gender=3;t.name=name;t.description="";t.iconID=icon;t.part=-1;t.isUpToUp=true;
        return t;
    }
}
