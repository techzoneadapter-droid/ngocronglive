package nro.progression;

import java.util.Map;
import java.util.EnumMap;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.services.ItemService;

/** Appends seven slots after the original 13 without reusing mounts/pets/title slots. */
public final class KaizenEquipment {
    public static final int FIRST_SLOT=13, SLOT_COUNT=7;
    private static volatile Map<Integer,KaizenRecipes> templates=Map.of();
    private KaizenEquipment(){}
    public static synchronized void install(Map<KaizenRecipes,Integer> catalog) {
        if(catalog.size()!=SLOT_COUNT)throw new IllegalArgumentException("All seven real templates are required");
        var ids=new java.util.HashMap<Integer,KaizenRecipes>();
        for(var entry:catalog.entrySet()) {
            int id=entry.getValue();
            if(id<0 || ids.put(id,entry.getKey())!=null)throw new IllegalArgumentException("Duplicate equipment ID");
            var item=ItemService.gI().getTemplate((short)id);
            if(item==null || item.id!=id || item.type<0 || item.type>4)
                throw new IllegalArgumentException("Kaizen template must support original combine algorithms");
        }
        if(!templates.isEmpty()&&!templates.equals(ids))throw new IllegalStateException("Cannot rebind live equipment slots");
        templates=Map.copyOf(ids);
    }
    public static int slot(int itemId) {
        var kind=templates.get(itemId);
        return kind==null?-1:FIRST_SLOT+kind.ordinal();
    }
    public static void ensureSlots(Player player) {
        if(templates.isEmpty() || player.isPet || player.isBoss || player.isMiniPet)return;
        while(player.inventory.itemsBody.size()<FIRST_SLOT+SLOT_COUNT)
            player.inventory.itemsBody.add(ItemService.gI().createItemNull());
    }
}
