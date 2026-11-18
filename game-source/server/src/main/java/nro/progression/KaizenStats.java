package nro.progression;

import org.json.JSONArray;

/** Owner-approved percentages multiplied by 100; native option IDs, no upgrade level option. */
public final class KaizenStats {
    private KaizenStats(){}
    public static JSONArray options(KaizenRecipes recipe) {
        int[][] values=switch(recipe) {
            case CROWN -> new int[][]{{77,500},{50,200},{103,200}};
            case BRACELET -> new int[][]{{50,500},{14,100},{77,200}};
            case NECKLACE -> new int[][]{{103,500},{77,200},{50,200}};
            case CAPE -> new int[][]{{77,400},{94,300},{50,200}};
            case MASK -> new int[][]{{50,400},{77,200},{14,100}};
            case SCARF -> new int[][]{{103,400},{50,200},{94,200}};
            case HALO -> new int[][]{{50,400},{77,300},{103,300}};
        };
        JSONArray result=new JSONArray();
        for(int[] value:values)result.put(new JSONArray().put(value[0]).put(value[1]));
        return result;
    }
}
