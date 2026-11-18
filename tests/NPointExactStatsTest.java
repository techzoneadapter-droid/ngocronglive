package nro.models.player;

import java.lang.reflect.*;
import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import nro.card.CollectionBook;
import nro.models.clan.Buff;
import nro.models.item.*;
import nro.progression.UnlimitedProgression;
import nro.server.Manager;

/** Invoke the real equipment calculation; no database, server manager or inventory grant. */
public final class NPointExactStatsTest {
    public static class Subject extends Player {
        @Override public Buff getBuff(){return Buff.NONE;}
        @Override public CollectionBook getCollectionBook(){return null;}
    }
    public static void main(String[] args)throws Exception {
        for(int i=0;i<251;i++)Manager.ITEM_OPTION_TEMPLATES.add(new ItemOptionTemplate(i,"fixture",0));
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);
        var memory=(sun.misc.Unsafe)f.get(null);
        var player=(Subject)memory.allocateInstance(Subject.class);initialize(player);
        Method calculate=NPoint.class.getDeclaredMethod("setPointWhenWearClothes");calculate.setAccessible(true);
        NPoint point=player.nPoint;
        point.hpg=point.mpg=20000000;point.dameg=100000000;point.defg=1000000;
        player.inventory.itemsBody.set(5,equipment(2179,new int[][]{{50,7180},{77,12800},{103,12320}}));
        player.inventory.itemsBody.set(9,equipment(2180,new int[][]{{50,200},{77,300},{103,100}}));
        calculate.invoke(point);
        check(point.exactHpMax().equals(new BigInteger("10320000000")),"real shop costume/mount HP above int");
        check(point.exactMpMax().equals(new BigInteger("4968000000")),"real shop costume/mount KI above int");
        check(point.exactDamage().equals(new BigInteger("21840000000")),"real shop costume/mount attack above int");
        check(point.hpMax==Integer.MAX_VALUE && point.dame==Integer.MAX_VALUE,"bounded compatibility projection");
        calculate.invoke(point);check(point.exactHpMax().equals(new BigInteger("10320000000")),"recalculate does not stack bonuses");
        player.inventory.itemsBody.set(5,new Item());player.inventory.itemsBody.set(9,new Item());
        calculate.invoke(point);check(point.exactHpMax().intValueExact()==20000000 && point.exactDamage().intValueExact()==100000000,"unequip restores original base");
        Random random=new Random(18112026);
        for(int i=0;i<1000;i++) {
            int base=1+random.nextInt(10000),flat=random.nextInt(1000),first=random.nextInt(100),second=random.nextInt(100);
            point.hpg=point.mpg=point.dameg=point.defg=base;
            player.inventory.itemsBody.set(0,equipment(0,new int[][]{{0,flat},{6,flat},{7,flat},{47,flat},{50,first},{77,first},{103,first}}));
            player.inventory.itemsBody.set(1,equipment(1,new int[][]{{50,second},{77,second},{103,second}}));
            calculate.invoke(point);
            long expected=base+flat;expected+=expected*first/100;expected+=expected*second/100;
            check(point.hpMax==expected && point.mpMax==expected && point.dame==expected,"legacy ordinary equipment result");
            check(point.def==base*4+flat,"ordinary armor result");
        }
        for(int i=0;i<13;i++)player.inventory.itemsBody.set(i,new Item());
        BigInteger huge=BigInteger.TEN.pow(100);
        point.bindUnlimitedBase(new UnlimitedProgression(Map.of(UnlimitedProgression.Stat.HP,huge,
            UnlimitedProgression.Stat.KI,huge,UnlimitedProgression.Stat.ATTACK,huge,UnlimitedProgression.Stat.DEFENSE,huge)));
        player.inventory.itemsBody.set(5,equipment(2179,new int[][]{{50,300},{77,500},{103,150}}));
        calculate.invoke(point);
        check(point.exactHpMax().equals(huge.multiply(BigInteger.valueOf(6))),"unbounded base HP in real NPoint");
        check(point.exactMpMax().equals(huge.multiply(BigInteger.valueOf(5)).divide(BigInteger.TWO)),"unbounded base KI in real NPoint");
        check(point.exactDamage().equals(huge.multiply(BigInteger.valueOf(4))),"unbounded base attack in real NPoint");
        check(point.exactDefense().equals(huge.multiply(BigInteger.valueOf(4))),"unbounded armor in real NPoint");
        player.playerIntrinsic=(nro.models.intrinsic.IntrinsicPlayer)memory.allocateInstance(nro.models.intrinsic.IntrinsicPlayer.class);
        player.playerIntrinsic.intrinsic=new nro.models.intrinsic.Intrinsic();
        player.playerSkill=new nro.models.skill.PlayerSkill(player);
        var skill=new nro.models.skill.Skill();skill.template=new nro.models.skill.SkillTemplate();
        skill.template.id=nro.models.skill.Skill.DRAGON;skill.damage=100;player.playerSkill.skillSelect=skill;
        for(int i=0;i<100;i++) {
            BigInteger hit=point.getExactDameAttack(false);
            check(hit.compareTo(huge.multiply(BigInteger.valueOf(380)).divide(BigInteger.valueOf(100)))>=0
                && hit.compareTo(huge.multiply(BigInteger.valueOf(420)).divide(BigInteger.valueOf(100)))<=0,"exact normal skill variance");
        }
        point.isCrit100=true;
        BigInteger critical=point.getExactDameAttack(false);
        check(critical.compareTo(huge.multiply(BigInteger.valueOf(760)).divide(BigInteger.valueOf(100)))>=0
            && critical.compareTo(huge.multiply(BigInteger.valueOf(840)).divide(BigInteger.valueOf(100)))<=0,"exact critical variance");
        skill.template.id=nro.models.skill.Skill.MAKANKOSAPPO;skill.damage=150;
        check(point.getExactDameAttack(false).equals(huge.multiply(BigInteger.valueOf(375)).divide(BigInteger.valueOf(100))),"KI-based skill beyond long");
        check(point.getDameAttack(false)==Integer.MAX_VALUE,"legacy attack wrapper never wraps negative");
        check(point.subDameInjureWithDeff(huge.multiply(BigInteger.TEN)).equals(huge.multiply(BigInteger.valueOf(6))),"exact defense deduction");
        check(point.subDameInjureWithDeff(huge).equals(BigInteger.ONE),"armor minimum damage");
        check(point.subDameInjureWithDeff(BigInteger.ZERO).signum()==0,"zero damage remains zero");
        player.itemTime.isUseGiapXen=true;
        check(point.subDameInjureWithDeff(huge.multiply(BigInteger.TEN)).equals(huge.multiply(BigInteger.valueOf(3))),"exact armor buff");
        player.itemTime.isUseGiapXen=false;player.itemTime.isUseGiapXen2=true;
        check(point.subDameInjureWithDeff(huge.multiply(BigInteger.TEN)).equals(huge.multiply(BigInteger.valueOf(24)).divide(BigInteger.TEN)),"exact second armor buff");
        point.setExactVitals(point.exactHpMax(),point.exactMpMax());
        BigInteger initialHp=point.exactHp();
        var hit=point.damageExact(huge);
        check(hit.applied().equals(huge)&&!hit.killed()&&point.exactHp().equals(initialHp.subtract(huge)),"actual exact HP deduction");
        point.healExact(huge.multiply(BigInteger.TEN),BigInteger.ZERO);
        check(point.exactHp().equals(initialHp),"recovery clamps to exact maximum");
        check(!point.spendExactKi(huge.multiply(BigInteger.TEN))&&point.exactMp().equals(point.exactMpMax()),"insufficient KI unchanged");
        check(point.spendExactKi(huge)&&point.exactMp().equals(point.exactMpMax().subtract(huge)),"exact KI consumption");
        point.setExactVitals(huge,BigInteger.ZERO);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->point.damageExact(huge));var second=pool.submit(()->point.damageExact(huge));
            var a=first.get();var b=second.get();
            check(a.killed()!=b.killed()&&a.applied().add(b.applied()).equals(huge),"concurrent lethal transition only once");
        }finally{pool.shutdownNow();}
        point.setFullHpMp();check(point.exactHp().equals(initialHp)&&point.hp>0,"respawn restores exact HP");
        point.subHP(100);check(point.exactHp().equals(initialHp.subtract(BigInteger.valueOf(100))),"legacy HP method routes exact session");
        player.inventory.itemsBody.set(5,new Item());calculate.invoke(point);
        check(point.exactHp().equals(huge)&&point.exactHpMax().equals(huge),"unequip clamps exact current HP");
        System.out.println("PASS real NPoint: VIP costume/mount exceed int, equip/unequip/recalculate, 1000 ordinary formula comparisons, 10^100 base stats");
    }
    static void initialize(Player p) {
        p.isBoss=true;
        p.inventory=new Inventory(p);for(int i=0;i<13;i++)p.inventory.itemsBody.add(new Item());
        p.setClothes=new SetClothes(p);p.effectSkill=new EffectSkill(p);p.effectSkin=new EffectSkin(p);
        p.fusion=new Fusion(p);p.itemTime=new ItemTime(p);p.effectFlagBag=new EffectFlagBag(p);p.rewardBlackBall=new RewardBlackBall(p);
        p.nPoint=new NPoint(p);
    }
    static Item equipment(int id,int[][] options) {
        Item result=new Item();result.template=new ItemTemplate();result.template.id=(short)id;result.quantity=1;
        for(int[] option:options)result.itemOptions.add(new ItemOption(option[0],option[1]));return result;
    }
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
