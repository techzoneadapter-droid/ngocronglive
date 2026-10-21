package nro.live;

/** Shared integer arithmetic; no intermediate int overflow or repeated-percent compounding. */
public final class Balance {
    private Balance(){}
    public static int fused(long master,long disciple,int bonus,int cap){return (int)Math.min(cap,Math.max(0,(master+disciple)*(100L+bonus)/100));}
    public static long cost(int type,int base,int count){
        if(count<1||count>32767)throw new IllegalArgumentException("Số điểm không hợp lệ");
        return switch(type){
            case 0,1 -> (long)count*(2L*(base+1000L)+(long)count*20-20)/2;
            case 2 -> (long)count*(2L*base+count-1)/2*100;
            case 3 -> (long)count*(2L*(base+5L)+count-1)/2*100000;
            case 4 -> {long total=0,unit=50000000L;for(int i=0;i<base;i++)unit=Math.multiplyExact(unit,5);for(int i=0;i<count;i++){total=Math.addExact(total,unit);if(i<count-1)unit=Math.multiplyExact(unit,5);}yield total;}
            default -> throw new IllegalArgumentException("Loại chỉ số không hợp lệ");};
    }
    public static long extend(long now,long expires,long seconds){return Math.addExact(Math.max(now,expires),Math.multiplyExact(seconds,1000));}
}
