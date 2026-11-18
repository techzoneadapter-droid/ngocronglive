package nro.progression;

/** Reserved icon range checked against unrelated templates before installation. */
public final class KaizenIcons {
    public static final int FIRST=32050;
    private KaizenIcons(){}
    public static short fragment(KaizenRecipes kind){return (short)(FIRST+kind.ordinal()*2);}
    public static short equipment(KaizenRecipes kind){return (short)(FIRST+kind.ordinal()*2+1);}
}
