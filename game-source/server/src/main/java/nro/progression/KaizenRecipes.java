package nro.progression;

/** Recipes fixed by the owner. Template IDs are bound only after real catalog allocation. */
public enum KaizenRecipes {
    CROWN("Vương miện",100,500_000_000L),
    BRACELET("Vòng tay",30,50_000_000L),
    NECKLACE("Vòng cổ",40,75_000_000L),
    CAPE("Áo choàng",60,150_000_000L),
    MASK("Mặt nạ",50,100_000_000L),
    SCARF("Khăn choàng",50,100_000_000L),
    HALO("Hào quang sau đầu",150,1_000_000_000L);

    public final String displayName;
    public final int fragments;
    public final long gold;
    KaizenRecipes(String name,int fragments,long gold){this.displayName=name;this.fragments=fragments;this.gold=gold;}
}
