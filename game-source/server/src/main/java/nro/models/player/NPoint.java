package nro.models.player;

import nro.attr.Attribute;
import nro.card.Card;
import nro.card.CollectionBook;
import nro.consts.ConstAttribute;
import nro.consts.ConstPlayer;
import nro.consts.ConstRatio;
import nro.models.clan.Buff;
import nro.models.intrinsic.Intrinsic;
import nro.models.item.Item;
import nro.models.item.ItemOption;
import nro.models.skill.Skill;
import nro.power.PowerLimit;
import nro.power.PowerLimitManager;
import nro.server.Manager;
import nro.server.ServerManager;
import nro.services.*;
import nro.utils.Log;
import nro.utils.SkillUtil;
import nro.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import nro.progression.UnlimitedProgression;

/**
 * @author 💖 Nro Yanii 💖
 * 
 */
public class NPoint {

    public static final byte MAX_LIMIT = 11;

    private Player player;
    public boolean isCrit;
    public boolean isCrit100;

    private Intrinsic intrinsic;
    private int percentDameIntrinsic;
    public int dameAfter;

    /*-----------------------Chỉ số cơ bản------------------------------------*/
    public byte numAttack;
    public short stamina, maxStamina;

    public byte limitPower;
    public long power;
    public long tiemNang;

    public int hp, hpMax, hpg;
    public int mp, mpMax, mpg;
    public int dame, dameg;
    public int def, defg;
    public int crit, critg;
    // Exact computed values; legacy int fields remain compatibility projections until protocol migration.
    private BigInteger exactHpMax, exactMpMax, exactDamage, exactDefense;
    private BigInteger exactHpAdd = BigInteger.ZERO, exactMpAdd = BigInteger.ZERO;
    private BigInteger exactDamageAdd = BigInteger.ZERO, exactDefenseAdd = BigInteger.ZERO;
    private UnlimitedProgression unlimitedBase;
    private BigInteger exactHp, exactMp;
    public byte speed = 5;

    public boolean teleport;

    /**
     * Chỉ số cộng thêm
     */
    public int hpAdd, mpAdd, dameAdd, defAdd, critAdd, hpHoiAdd, mpHoiAdd;

    /**
     * //+#% sức đánh chí mạng
     */
    public List<Integer> tlDameCrit;

    public boolean buffExpSatellite, buffDefenseSatellite;

    /**
     * Tỉ lệ hp, mp cộng thêm
     */
    public List<Integer> tlHp, tlMp;

    /**
     * Tỉ lệ giáp cộng thêm
     */
    public List<Integer> tlDef;

    /**
     * Tỉ lệ sức đánh/ sức đánh khi đánh quái
     */
    public List<Integer> tlDame, tlDameAttMob;

    /**
     * Lượng hp, mp hồi mỗi 30s, mp hồi cho người khác
     */
    public int hpHoi, mpHoi, mpHoiCute;

    /**
     * Tỉ lệ hp, mp hồi cộng thêm
     */
    public short tlHpHoi, tlMpHoi;

    /**
     * Tỉ lệ hp, mp hồi bản thân và đồng đội cộng thêm
     */
    public short tlHpHoiBanThanVaDongDoi, tlMpHoiBanThanVaDongDoi;

    /**
     * Tỉ lệ hút hp, mp khi đánh, hp khi đánh quái
     */
    public short tlHutHp, tlHutMp, tlHutHpMob;

    /**
     * Tỉ lệ hút hp, mp xung quanh mỗi 5s
     */
    public short tlHutHpMpXQ;

    /**
     * Tỉ lệ phản sát thương
     */
    public short tlPST;

    /**
     * Tỉ lệ tiềm năng sức mạnh
     */
    public List<Integer> tlTNSM;
    public int tlTNSMPet;

    /**
     * Tỉ lệ vàng cộng thêm
     */
    public short tlGold;

    /**
     * Tỉ lệ né đòn
     */
    public short tlNeDon;

    /**
     * Tỉ lệ sức đánh đẹp cộng thêm cho bản thân và người xung quanh
     */
    public List<Integer> tlSDDep;

    /**
     * Tỉ lệ giảm sức đánh
     */
    public short tlSubSD;
    public List<Integer> tlSpeed;
    public int mstChuong;
    public int tlGiamst;

    /*------------------------Effect skin-------------------------------------*/
    public Item trainArmor;
    public boolean wornTrainArmor;
    public boolean wearingTrainArmor;

    public boolean wearingVoHinh;
    public boolean isKhongLanh;

    public short tlHpGiamODo;

    private PowerLimit powerLimit;
    public boolean wearingDrabula;
    public boolean wearingMabu;
    public boolean wearingBuiBui;

    public boolean wearingNezuko;
    public boolean wearingTanjiro;
    public boolean wearingInosuke;
    public boolean wearingInoHashi;
    public boolean wearingZenitsu;
    public int tlDameChuong;
    public boolean xDameChuong;
    public boolean wearingYacon;
    public boolean wearingRedNoelHat;
    public boolean wearingGrayNoelHat;
    public boolean wearingBlueNoelHat;
    public boolean wearingNoelHat;

    public NPoint(Player player) {
        this.player = player;
        this.tlHp = new ArrayList<>();
        this.tlMp = new ArrayList<>();
        this.tlDef = new ArrayList<>();
        this.tlDame = new ArrayList<>();
        this.tlDameAttMob = new ArrayList<>();
        this.tlSDDep = new ArrayList<>();
        this.tlTNSM = new ArrayList<>();
        this.tlDameCrit = new ArrayList<>();
        this.tlSpeed = new ArrayList<>();
    }

    public void initPowerLimit() {
        powerLimit = PowerLimitManager.getInstance().get(limitPower);
    }

    /*-------------------------------------------------------------------------*/
    /**
     * Tính toán mọi chỉ số sau khi có thay đổi
     */
    public void calPoint() {
        try {
            if (this.player.pet != null) {
                this.player.pet.nPoint.setPointWhenWearClothes();
            }
            this.setPointWhenWearClothes();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setPoint(ItemOption io) {
        accumulateExactFlatOption(io.optionTemplate.id, io.param);
        switch (io.optionTemplate.id) {
            case 0: // Tấn công +#
                this.dameAdd += io.param;
                break;
            case 2: // HP, KI+#000
                this.hpAdd += io.param * 1000;
                this.mpAdd += io.param * 1000;
                break;
            case 3: // vô hiệu vả biến st chưởng thành ki
                this.mstChuong += io.param;
                break;
            case 5: // +#% sức đánh chí mạng
                this.tlDameCrit.add(io.param);
                break;
            case 6: // HP+#
                this.hpAdd += io.param;
                break;
            case 7: // KI+#
                this.mpAdd += io.param;
                break;
            case 8: // Hút #% HP, KI xung quanh mỗi 5 giây
                this.tlHutHpMpXQ += io.param;
                break;
            case 14: // Chí mạng+#%
                this.critAdd += io.param;
                break;
            case 19: // Tấn công+#% khi đánh quái
                this.tlDameAttMob.add(io.param);
                break;
            case 22: // HP+#K
                this.hpAdd += io.param * 1000;
                break;
            case 23: // MP+#K
                this.mpAdd += io.param * 1000;
                break;
            case 24:
                this.wearingBuiBui = true;
                break;
            case 25:
                this.wearingYacon = true;
                break;
            case 26:
                this.wearingDrabula = true;
                this.player.effectSkin.lastTimeDrabula = System.currentTimeMillis();
                break;
            case 29:
                this.wearingMabu = true;
                break;
            case 27: // +# HP/30s
                this.hpHoiAdd += io.param;
                break;
            case 28: // +# KI/30s
                this.mpHoiAdd += io.param;
                break;
            case 33: // dịch chuyển tức thời
                this.teleport = true;
                break;
            case 47: // Giáp+#
                this.defAdd += io.param;
                break;
            case 48: // HP/KI+#
                this.hpAdd += io.param;
                this.mpAdd += io.param;
                break;
            case 49: // Tấn công+#%
            case 50: // Sức đánh+#%
                this.tlDame.add(io.param);
                break;
            case 77: // HP+#%
                this.tlHp.add(io.param);
                break;
            case 80: // HP+#%/30s
                this.tlHpHoi += io.param;
                break;
            case 81: // MP+#%/30s
                this.tlMpHoi += io.param;
                break;
            case 88: // Cộng #% exp khi đánh quái
                this.tlTNSM.add(io.param);
                break;
            case 94: // Giáp #%
                this.tlDef.add(io.param);
                break;
            case 95: // Biến #% tấn công thành HP
                this.tlHutHp += io.param;
                break;
            case 96: // Biến #% tấn công thành MP
                this.tlHutMp += io.param;
                break;
            case 97: // Phản #% sát thương
                this.tlPST += io.param;
                break;
            case 100: // +#% vàng từ quái
                this.tlGold += io.param;
                break;
            case 101: // +#% TN,SM
                this.tlTNSM.add(io.param);
                break;
            case 103: // KI +#%
                this.tlMp.add(io.param);
                break;
            case 104: // Biến #% tấn công quái thành HP
                this.tlHutHpMob += io.param;
                break;
            // case 105: //Vô hình khi không đánh quái và boss
            // this.wearingVoHinh = true;
            // break;
            case 106: // Không ảnh hưởng bởi cái lạnh
                this.isKhongLanh = true;
                break;
            case 108: // #% Né đòn
                this.tlNeDon += io.param;
                break;
            case 109: // Hôi, giảm #% HP
                this.tlHpGiamODo += io.param;
                break;
            case 114:
                this.tlSpeed.add(io.param);
                break;
            case 117: // Đẹp +#% SĐ cho mình và người xung quanh
                this.tlSDDep.add(io.param);
                break;
            case 147: // +#% sức đánh
                this.tlDame.add(io.param);
                break;
            case 156: // Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng từ quái
                this.tlSubSD += 50;
                this.tlTNSM.add(io.param);
                this.tlGold += io.param;
                break;
            case 160:
                this.tlTNSMPet += io.param;
                break;
            case 162: // Cute hồi #% KI/s bản thân và xung quanh
                this.mpHoiCute += io.param;
                break;
            case 173: // Phục hồi #% HP và KI cho đồng đội
                this.tlHpHoiBanThanVaDongDoi += io.param;
                this.tlMpHoiBanThanVaDongDoi += io.param;
                break;
            case 189:
                this.wearingNezuko = true;
                break;
            case 190:
                this.wearingTanjiro = true;
                break;
            case 191:
                this.wearingInoHashi = true;
                break;
            case 192:
                this.wearingInosuke = true;
                break;
            case 193:
                this.wearingZenitsu = true;
                break;
            case 194:
                this.tlDameChuong = 3;
                break;
            case 195:
                this.tlDameChuong = 4;
                break;
        }
    }

    private void setPointWhenWearClothes() {
        resetPoint();
        for (Item item : this.player.inventory.itemsBody) {
            if (item.isNotNullItem()) {
                int tempID = item.template.id;
                if (tempID >= 592 && tempID <= 594) {
                    teleport = true;
                }
                for (ItemOption io : item.itemOptions) {
                    setPoint(io);
                }
            }
        }
        List<Item> itemsBody = player.inventory.itemsBody;
        // if (!player.isBoss && !player.isMiniPet) {
        // Item pants = itemsBody.get(1);
        // if (pants.isNotNullItem() && pants.getId() >= 691 && pants.getId() >= 693) {
        // player.event.setUseQuanHoa(true);
        // }
        // }
        if (Manager.EVENT_SEVER == 3) {
            if (!this.player.isBoss && !this.player.isMiniPet) {
                if (itemsBody.get(5).isNotNullItem()) {
                    int tempID = itemsBody.get(5).getId();
                    switch (tempID) {
                        case 386:
                        case 389:
                        case 392:
                            wearingGrayNoelHat = true;
                            wearingNoelHat = true;
                            break;
                        case 387:
                        case 390:
                        case 393:
                            wearingRedNoelHat = true;
                            wearingNoelHat = true;
                            break;
                        case 388:
                        case 391:
                        case 394:
                            wearingBlueNoelHat = true;
                            wearingNoelHat = true;
                            break;
                        default:
                            wearingRedNoelHat = false;
                            wearingBlueNoelHat = false;
                            wearingGrayNoelHat = false;
                            wearingNoelHat = false;
                    }
                }
            }
        }
        CollectionBook book = player.getCollectionBook();

        if (book != null) {
            List<Card> cards = book.getCards();
            if (cards != null) {
                for (Card c : cards) {
                    if (c.getLevel() > 0) {
                        int index = 0;
                        for (ItemOption o : c.getCardTemplate().getOptions()) {
                            if ((index == 0 || c.isUse()) && c.getLevel() >= o.activeCard) {
                                setPoint(o);
                            }
                            index++;
                        }
                    }
                }
            }
        }

        if ((!this.player.isPet && !this.player.isMiniPet && !this.player.isBoss)
                && this.player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            for (Item item : this.player.inventory.itemsBag) {
                if (item.isNotNullItem() && item.template.id == 921) {
                    for (ItemOption io : item.itemOptions) {
                        switch (io.optionTemplate.id) {
                            case 14: // Chí mạng+#%
                                this.critAdd += io.param;
                                break;
                            case 50: // Sức đánh+#%
                                this.tlDame.add(io.param);
                                break;
                            case 77: // HP+#%
                                this.tlHp.add(io.param);
                                break;
                            case 80: // HP+#%/30s
                                this.tlHpHoi += io.param;
                                break;
                            case 81: // MP+#%/30s
                                this.tlMpHoi += io.param;
                                break;
                            case 94: // Giáp #%
                                this.tlDef.add(io.param);
                                break;
                            case 103: // KI +#%
                                this.tlMp.add(io.param);
                                break;
                            case 108: // #% Né đòn
                                this.tlNeDon += io.param;
                                break;
                        }
                    }
                    break;
                }
            }
        }

        setDameTrainArmor();
        setBasePoint();
    }

    private void setDameTrainArmor() {
        if (!this.player.isPet && !this.player.isBoss && !this.player.isMiniPet) {
            try {
                Item gtl = this.player.inventory.itemsBody.get(6);
                if (gtl.isNotNullItem()) {
                    this.wearingTrainArmor = true;
                    this.wornTrainArmor = true;
                    this.player.inventory.trainArmor = gtl;
                    this.tlSubSD += ItemService.gI().getPercentTrainArmor(gtl);
                } else {
                    if (this.wornTrainArmor) {
                        this.wearingTrainArmor = false;
                        for (ItemOption io : this.player.inventory.trainArmor.itemOptions) {
                            if (io.optionTemplate.id == 9 && io.param > 0) {
                                this.tlDame
                                        .add(ItemService.gI().getPercentTrainArmor(this.player.inventory.trainArmor));
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.error("Lỗi get giáp tập luyện " + this.player.name);
            }
        }
    }

    private void setNeDon() {
        // ngọc rồng đen 6 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[5] > System.currentTimeMillis()) {
            this.tlNeDon += RewardBlackBall.R6S;
        }
    }

    private void setHpHoi() {
        this.hpHoi = (int) calPercent(this.hpMax, 1);
        this.hpHoi += this.hpHoiAdd;
        this.hpHoi += calPercent(this.hpMax, this.tlHpHoi);
        this.hpHoi += calPercent(this.hpMax, this.tlHpHoiBanThanVaDongDoi);
        if (this.player.effectSkin.isNezuko) {
            this.hpHoi += calPercent(this.hpMax, 3);
        }
    }

    private void setMpHoi() {
        this.mpHoi = (int) calPercent(this.mpMax, 1);
        this.mpHoi += this.mpHoiAdd;
        this.mpHoi += calPercent(this.mpMax, this.tlMpHoi);
        this.mpHoi += calPercent(this.mpMax, this.tlMpHoiBanThanVaDongDoi);
        if (this.player.effectSkin.isNezuko) {
            this.mpHoi += calPercent(this.mpMax, 3);
        }
    }

    private void setHpMax() {
        BigInteger value = exactBase(UnlimitedProgression.Stat.HP, this.hpg).add(exactHpAdd);
        // đồ
        for (Integer tl : this.tlHp) {
            value = value.add(calPercent(value, tl));
        }

        if (player.DH1) {
            value = value.add(calPercent(value, player.ChiSoHP_1));
        }
        if (player.DH2) {
            value = value.add(calPercent(value, player.ChiSoHP_2));
        }
        if (player.DH3) {
            value = value.add(calPercent(value, player.ChiSoHP_3));
        }
        if (player.DH4) {
            value = value.add(calPercent(value, player.ChiSoHP_4));
        }
        if (player.DH5) {
            value = value.add(calPercent(value, player.ChiSoHP_5));
        }

        // set nappa
        if (this.player.setClothes.nappa == 5) {
            value = value.add(calPercent(value, 100));
        }
        // ngọc rồng đen 2 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[1] > System.currentTimeMillis()) {
            value = value.add(calPercent(value, RewardBlackBall.R2S));
        }
        // BIẾN HÌNH
        if (this.player.effectSkill.isBienHinh) {
            if (!this.player.isPet || (this.player.isPet
                    && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentHpMonkey(player.effectSkill.levelBienHinh);
                value = value.add(calPercent(value, percent));
            }
        }
        // khỉ
        if (this.player.effectSkill.isMonkey) {
            if (!this.player.isPet || (this.player.isPet
                    && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentHpMonkey(player.effectSkill.levelMonkey);
                value = value.add(calPercent(value, percent));
            }
        }
        /// bông tai cấp 2
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 10));
        }
        /// Đệ vip
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        /// end
        // bông tai cấp 1
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 10));
        }
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        // phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            value = value.multiply(BigInteger.valueOf(this.player.effectSkin.xHPKI));
        }
        // +hp đệ
        if (this.player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            value = value.add(this.player.pet.nPoint.exactHpMax());
        }
        // huýt sáo
        if (!this.player.isPet
                || (this.player.isPet
                        && ((Pet) this.player).status != Pet.FUSION)) {
            if (this.player.effectSkill.tiLeHPHuytSao != 0) {
                value = value.add(calPercent(value, this.player.effectSkill.tiLeHPHuytSao));
            }
        }
        // bổ huyết
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet) {
            value = value.multiply(BigInteger.valueOf(2));
        }
        // bổ huyết 2
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet2) {
            value = value.add(calPercent(value, 120));
        }
        if (this.player.zone != null && MapService.gI().isMapCold(this.player.zone.map)
                && !this.isKhongLanh) {
            value = value.divide(BigInteger.valueOf(2));
        }
        if (!player.isBoss) {
            Attribute at = ServerManager.gI().getAttributeManager().find(ConstAttribute.HP);
            if (at != null && !at.isExpired()) {
                value = value.add(calPercent(value, at.getValue()));
            }
        }
        if (this.player.itemTime != null) {
            if (this.player.itemTime.isUseBanhTet) {
                value = value.add(calPercent(value, 20));
            }
        }
        if (player.getBuff() == Buff.BUFF_HP) {
            value = value.add(calPercent(value, 20));
        }
        this.exactHpMax = value.max(BigInteger.ZERO);
        this.hpMax = legacyStat(this.exactHpMax);
    }

    // (hp sư phụ + hp đệ tử ) + 15%
    // (hp sư phụ + 15% +hp đệ tử)
    private void setHp() {
        if (exactHp != null) { exactHp=exactHp.min(exactHpMax());hp=legacyStat(exactHp);return; }
        if (this.hp > this.hpMax) {
            this.hp = this.hpMax;
        }
    }

    private void setMpMax() {
        BigInteger value = exactBase(UnlimitedProgression.Stat.KI, this.mpg).add(exactMpAdd);
        // đồ
        for (Integer tl : this.tlMp) {
            value = value.add(calPercent(value, tl));
        }
        if (this.player.setClothes.picolo == 5) {
            value = value.multiply(BigInteger.valueOf(3));
        }
        // ngọc rồng đen 3 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[2] > System.currentTimeMillis()) {
            value = value.add(calPercent(value, RewardBlackBall.R3S));

        }
        if (player.DH1) {
            value = value.add(calPercent(value, player.ChiSoKI_1));
        }
        // BIẾN HÌNH
        if (this.player.effectSkill.isBienHinh) {
            if (!this.player.isPet || (this.player.isPet
                    && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentHpMonkey(player.effectSkill.levelBienHinh);
                value = value.add(calPercent(value, percent));
            }
        }
        if (player.DH2) {
            value = value.add(calPercent(value, player.ChiSoKI_2));
        }
        if (player.DH3) {
            value = value.add(calPercent(value, player.ChiSoKI_3));
        }
        if (player.DH4) {
            value = value.add(calPercent(value, player.ChiSoKI_4));
        }
        if (player.DH5) {
            value = value.add(calPercent(value, player.ChiSoKI_5));
        }
        // pet mabư
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 10));
        }
        /// đệ vip
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        /// end
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 10));
        }
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        // hợp thể
        if (this.player.fusion.typeFusion != 0) {
            value = value.add(this.player.pet.nPoint.exactMpMax());
        }
        // bổ khí
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi) {
            value = value.multiply(BigInteger.valueOf(2));
        }
        // bổ khí 2
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi2) {
            value = value.add(calPercent(value, 120));
        }
        // phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            value = value.multiply(BigInteger.valueOf(this.player.effectSkin.xHPKI));
        }
        // xiên cá
        if (this.player.effectFlagBag.useXienCa) {
            value = value.add(calPercent(value, 15));
        }
        // Kiem z
        if (this.player.effectFlagBag.useKiemz) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.effectFlagBag.useDieuRong) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.effectFlagBag.useHoaVang || this.player.effectFlagBag.useHoaHong) {
            value = value.add(calPercent(value, 20));
        }
        if (!player.isBoss) {
            Attribute at = ServerManager.gI().getAttributeManager().find(ConstAttribute.KI);
            if (at != null && !at.isExpired()) {
                value = value.add(calPercent(value, at.getValue()));
            }
        }
        if (this.player.itemTime != null) {
            if (this.player.itemTime.isUseBanhTet) {
                value = value.add(calPercent(value, 20));
            }
        }
        if (player.getBuff() == Buff.BUFF_KI) {
            value = value.add(calPercent(value, 20));
        }
        this.exactMpMax = value.max(BigInteger.ZERO);
        this.mpMax = legacyStat(this.exactMpMax);
    }
    private void setMp() {
        if (exactMp != null) { exactMp=exactMp.min(exactMpMax());mp=legacyStat(exactMp);return; }
        if (this.mp > this.mpMax) {
            this.mp = this.mpMax;
        }
    }

    private void setDame() {
        BigInteger value = exactBase(UnlimitedProgression.Stat.ATTACK, this.dameg).add(exactDamageAdd);
        // đồ
        for (Integer tl : this.tlDame) {
            value = value.add(calPercent(value, tl));
        }
        for (Integer tl : this.tlSDDep) {
            value = value.add(calPercent(value, tl));
        }
        if (player.DH1) {
            value = value.add(calPercent(value, player.ChiSoSD_1));
        }
        if (player.DH2) {
            value = value.add(calPercent(value, player.ChiSoSD_2));
        }
        if (player.DH3) {
            value = value.add(calPercent(value, player.ChiSoSD_3));
        }
        if (player.DH4) {
            value = value.add(calPercent(value, player.ChiSoSD_4));
        }
        if (player.DH5) {
            value = value.add(calPercent(value, player.ChiSoSD_5));
        }
        // BIẾN HÌNH
        if (this.player.effectSkill.isBienHinh) {
            if (!this.player.isPet || (this.player.isPet
                    && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentHpMonkey(player.effectSkill.levelBienHinh);
                value = value.add(calPercent(value, percent));
            }
        }
        // pet mabư
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 10));
        }
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            value = value.add(calPercent(value, 30));
        }
        // end
        /// bong tai 1
        if (this.player.isPet && ((Pet) this.player).isMabu
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 10));
        }
        if (this.player.isPet && ((Pet) this.player).isBulo
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 20));
        }
        if (this.player.isPet && ((Pet) this.player).isCellBao
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 40));
        }
        if (this.player.isPet && ((Pet) this.player).isBillNhi
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        if (this.player.isPet && ((Pet) this.player).isFideTrau
                && ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA) {
            value = value.add(calPercent(value, 30));
        }
        // thức ăn
        if (!this.player.isPet && this.player.itemTime.isEatMeal
                || this.player.isPet && ((Pet) this.player).master.itemTime.isEatMeal) {
            value = value.add(calPercent(value, 10));
        }
        // hợp thể
        if (this.player.fusion.typeFusion != 0) {
            value = value.add(this.player.pet.nPoint.exactDamage());
        }
        // cuồng nộ
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo) {
            value = value.multiply(BigInteger.valueOf(2));
        }
        // cuồng nộ 2
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo2) {
            value = value.add(calPercent(value, 120));
        }
        // giảm dame
        value = value.subtract(calPercent(value, tlSubSD));
        // map cold
        if (this.player.zone != null && MapService.gI().isMapCold(this.player.zone.map)
                && !this.isKhongLanh) {
            value = value.divide(BigInteger.valueOf(2));
        }
        // ngọc rồng đen 1 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[0] > System.currentTimeMillis()) {
            value = value.add(calPercent(value, RewardBlackBall.R1S));
        }
        if (!player.isBoss) {
            Attribute at = ServerManager.gI().getAttributeManager().find(ConstAttribute.SUC_DANH);
            if (at != null && !at.isExpired()) {
                value = value.add(calPercent(value, at.getValue()));
            }
        }
        if (this.player.itemTime != null) {
            if (this.player.itemTime.isUseBanhChung) {
                value = value.add(calPercent(value, 20));
            }
        }
        if (player.getBuff() == Buff.BUFF_ATK) {
            value = value.add(calPercent(value, 20));
        }
        this.exactDamage = value.max(BigInteger.ZERO);
        this.dame = legacyStat(this.exactDamage);
    }
    private void setDef() {
        BigInteger value = exactBase(UnlimitedProgression.Stat.DEFENSE, this.defg).multiply(BigInteger.valueOf(4)).add(exactDefenseAdd);
        // đồ
        for (Integer tl : this.tlDef) {
            this.tlGiamst += tl;
        }
        if (tlGiamst > 60) {
            tlGiamst = 60;
        }
        // ngọc rồng đen 5 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[4] > System.currentTimeMillis()) {
            value = value.add(calPercent(value, RewardBlackBall.R5S));
        }
        if (this.player.effectSkin.isInosuke) {
            value = value.add(calPercent(value, 50));
        }
        if (this.player.effectSkin.isInoHashi) {
            value = value.add(calPercent(value, 60));
        }
        this.exactDefense = value.max(BigInteger.ZERO);
        this.def = legacyStat(this.exactDefense);
    }
    private void setCrit() {
        this.crit = this.critg;
        this.crit += this.critAdd;
        // ngọc rồng đen 4 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[3] > System.currentTimeMillis()) {
            this.crit += RewardBlackBall.R4S;
        }
        // biến khỉ
        if (this.player.effectSkill.isMonkey) {
            this.crit = 110;

        }
        /// SET BIEN HINH CM VE MAT DINH
        if (this.player.effectSkill.isBienHinh) {
            this.crit = 0 + this.critg + this.critAdd;
        }
        if (player.getBuff() == Buff.BUFF_CRIT) {
            crit += 10;
        }
    }

    private void setCritDame() {
        if (this.player.effectSkin.isTanjiro) {
            this.tlDameCrit.add(30);
        }
        if (this.player.itemTime != null) {
            if (this.player.itemTime.isUseBanhChung) {
                this.tlDameCrit.add(15);
            }
        }
    }

    private void setSpeed() {
        for (Integer tl : this.tlSpeed) {
            this.speed += calPercent(this.speed, tl);
        }
        if (this.player.effectSkin.isSlow) {
            this.speed = 1;
        }
    }

    private void resetPoint() {
        exactHpAdd = exactMpAdd = exactDamageAdd = exactDefenseAdd = BigInteger.ZERO;
        this.hpAdd = 0;
        this.mpAdd = 0;
        this.dameAdd = 0;
        this.defAdd = 0;
        this.critAdd = 0;
        this.tlHp.clear();
        this.tlMp.clear();
        this.tlDef.clear();
        this.tlDame.clear();
        this.tlDameAttMob.clear();
        this.tlDameCrit.clear();
        this.tlHpHoiBanThanVaDongDoi = 0;
        this.tlMpHoiBanThanVaDongDoi = 0;
        this.hpHoi = 0;
        this.mpHoi = 0;
        this.mpHoiCute = 0;
        this.tlHpHoi = 0;
        this.tlMpHoi = 0;
        this.tlHutHp = 0;
        this.tlHutMp = 0;
        this.tlHutHpMob = 0;
        this.tlHutHpMpXQ = 0;
        this.tlPST = 0;
        this.tlTNSM.clear();
        this.tlDameAttMob.clear();
        this.tlDameCrit.clear();
        this.tlGold = 0;
        this.tlNeDon = 0;
        this.tlSDDep.clear();
        this.tlSubSD = 0;
        this.tlHpGiamODo = 0;
        this.teleport = false;
        this.tlSpeed.clear();
        this.speed = 5;
        this.mstChuong = 0;
        this.tlGiamst = 0;
        this.tlTNSMPet = 0;

        this.wearingVoHinh = false;
        this.isKhongLanh = false;
        this.wearingDrabula = false;
        this.wearingNezuko = false;
        this.wearingZenitsu = false;
        this.wearingInosuke = false;
        this.wearingInoHashi = false;
        this.wearingTanjiro = false;
        this.wearingMabu = false;
        this.wearingBuiBui = false;
        this.xDameChuong = false;
        this.wearingYacon = false;
    }

    public void addHp(long hp) {
        if (exactHp != null) { healExact(BigInteger.valueOf(hp), BigInteger.ZERO); return; }
        this.hp += hp;
        if (this.hp > this.hpMax) {
            this.hp = this.hpMax;
        }
    }

    public void subHp(long hp) {
        if (exactHp != null) { damageExact(BigInteger.valueOf(hp)); return; }
        this.hp -= hp;
    }

    public void addMp(long mp) {
        if (exactMp != null) { healExact(BigInteger.ZERO, BigInteger.valueOf(mp)); return; }
        this.mp += mp;
        if (this.mp > this.mpMax) {
            this.mp = this.mpMax;
        }
    }

    public void setHp(long hp) {
        if (exactHp != null) { setExactVitals(BigInteger.valueOf(hp), exactMp); return; }
        if (hp > this.hpMax) {
            this.hp = this.hpMax;
        } else {
            this.hp = (int) hp;
        }
    }

    public void setMp(long mp) {
        if (exactMp != null) { setExactVitals(exactHp, BigInteger.valueOf(mp)); return; }
        if (mp > this.mpMax) {
            this.mp = this.mpMax;
        } else {
            this.mp = (int) mp;
        }
    }

    private void setIsCrit() {
        if (intrinsic != null && intrinsic.id == 25
                && this.getCurrPercentHP() <= intrinsic.param1) {
            isCrit = true;
        } else if (isCrit100) {
            isCrit100 = false;
            isCrit = true;
        } else {
            isCrit = Util.isTrue(this.crit, ConstRatio.PER100);
        }
    }

    public int getDameAttack(boolean isAttackMob) {
        return legacyStat(getExactDameAttack(isAttackMob));
    }

    public BigInteger getExactDameAttack(boolean isAttackMob) {
        setIsCrit();
        BigInteger dameAttack = exactDamage();
        intrinsic = this.player.playerIntrinsic.intrinsic;
        percentDameIntrinsic = 0;
        int percentDameSkill = 0;
        int percentXDame = 0;
        Skill skillSelect = player.playerSkill.skillSelect;
        switch (skillSelect.template.id) {
            case Skill.DRAGON:
                if (intrinsic.id == 1) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.KAMEJOKO:
                if (intrinsic.id == 2) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.songoku == 5) {
                    percentXDame = 90;
                }
                // if (this.player.effectSkin.xDameChuong) {
                // percentXDame += tlDameChuong;
                // this.player.effectSkin.xDameChuong = false;
                // }
                break;
            case Skill.GALICK:
                if (intrinsic.id == 16) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.kakarot == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.ANTOMIC:
                if (intrinsic.id == 17) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                // if (this.player.effectSkin.xDameChuong) {
                // percentXDame += tlDameChuong;
                // this.player.effectSkin.xDameChuong = false;
                // }
                break;
            case Skill.DEMON:
                if (intrinsic.id == 8) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.MASENKO:
                if (intrinsic.id == 9) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                // if (this.player.effectSkin.xDameChuong) {
                // percentXDame += tlDameChuong * 100;
                // this.player.effectSkin.xDameChuong = false;
                // }
                break;
            case Skill.KAIOKEN:
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.LIEN_HOAN:
                if (intrinsic.id == 13) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.ocTieu == 5) {
                    percentXDame = 90;
                }
                break;
            case Skill.DICH_CHUYEN_TUC_THOI:
                dameAttack = dameAttack.multiply(BigInteger.valueOf(2));
                dameAttack = randomizeDamage(dameAttack);
                return dameAttack;
            case Skill.MAKANKOSAPPO:
                percentDameSkill = skillSelect.damage;
                BigInteger dameSkill = calPercent(exactMpMax(), percentDameSkill);
                return dameSkill;
            case Skill.QUA_CAU_KENH_KHI:
                long totalHP = 0;
                if (player.zone != null) {
                    totalHP = player.zone.getTotalHP();
                }
                BigInteger damage = BigInteger.valueOf(totalHP).divide(BigInteger.TEN).add(exactDamage().multiply(BigInteger.TEN));
                if (this.player.setClothes.kirin == 5) {
                    damage = damage.multiply(BigInteger.valueOf(3));
                }
                return damage;
        }
        if (intrinsic.id == 18 && this.player.effectSkill.isMonkey) {
            percentDameIntrinsic = intrinsic.param1;
        }
        if (percentDameSkill != 0) {
            dameAttack = calPercent(dameAttack, percentDameSkill);
        }
        dameAttack = dameAttack.add(calPercent(dameAttack, percentDameIntrinsic));
        dameAttack = dameAttack.add(calPercent(dameAttack, dameAfter));

        if (isAttackMob) {
            for (Integer tl : this.tlDameAttMob) {
                dameAttack = dameAttack.add(calPercent(dameAttack, tl));
            }
        }
        dameAfter = 0;
        if (this.player.isPet && ((Pet) this.player).master.charms.tdDeTu > System.currentTimeMillis()) {
            dameAttack = dameAttack.multiply(BigInteger.valueOf(2));
        }
        if (isCrit) {
            dameAttack = dameAttack.multiply(BigInteger.valueOf(2));
            for (Integer tl : this.tlDameCrit) {
                dameAttack = dameAttack.add(calPercent(dameAttack, tl));
            }
        }
        dameAttack = dameAttack.add(calPercent(dameAttack, percentXDame));
        // System.out.println(dameAttack);
        dameAttack = randomizeDamage(dameAttack);

        // check activation set
        return dameAttack;
    }

    public int getDameAttackSkillNotFocus() {
        return legacyStat(getExactDameAttackSkillNotFocus());
    }

    public BigInteger getExactDameAttackSkillNotFocus() {
        setIsCrit();
        BigInteger dameAttack = exactDamage();
        intrinsic = this.player.playerIntrinsic.intrinsic;
        percentDameIntrinsic = 0;
        int percentDameSkill = 0;
        int percentXDame = 0;
        Skill skillSelect = player.playerSkill.skillSelect;
        switch (skillSelect.template.id) {

        }
        if (intrinsic.id == 18 && this.player.effectSkill.isMonkey) {
            percentDameIntrinsic = intrinsic.param1;
        }
        if (percentDameSkill != 0) {
            dameAttack = calPercent(dameAttack, percentDameSkill);
        }
        dameAttack = dameAttack.add(calPercent(dameAttack, percentDameIntrinsic));
        dameAttack = dameAttack.add(calPercent(dameAttack, dameAfter));
        dameAfter = 0;
        if (this.player.isPet && ((Pet) this.player).master.charms.tdDeTu > System.currentTimeMillis()) {
            dameAttack = dameAttack.multiply(BigInteger.valueOf(2));
        }
        if (isCrit) {
            dameAttack = dameAttack.multiply(BigInteger.valueOf(2));
            for (Integer tl : this.tlDameCrit) {
                dameAttack = dameAttack.add(calPercent(dameAttack, tl));
            }
        }
        dameAttack = dameAttack.add(calPercent(dameAttack, percentXDame));
        dameAttack = randomizeDamage(dameAttack);
        return dameAttack;
    }

    public int getCurrPercentHP() {
        if (exactHp != null) return exactPercent(exactHp, exactHpMax());
        if (this.hpMax == 0) {
            return 100;
        }
        return (int) ((long) this.hp * 100 / this.hpMax);
    }

    public int getCurrPercentMP() {
        if (exactMp != null) return exactPercent(exactMp, exactMpMax());
        return (int) ((long) this.mp * 100 / this.mpMax);
    }

    public void setFullHpMp() {
        if (exactHp != null) { setExactVitals(exactHpMax(), exactMpMax()); return; }
        this.hp = this.hpMax;
        this.mp = this.mpMax;
    }

    public void subHP(long sub) {
        if (exactHp != null) { damageExact(BigInteger.valueOf(sub)); return; }
        this.hp -= sub;
        if (this.hp < 0) {
            this.hp = 0;
        }
    }

    public void subMP(long sub) {
        if (exactMp != null) { spendExactKi(BigInteger.valueOf(sub)); return; }
        this.mp -= sub;
        if (this.mp < 0) {
            this.mp = 0;
        }
    }

    public long calSucManhTiemNang(long tiemNang) {
        if (power < getPowerLimit()) {
            for (Integer tl : this.tlTNSM) {
                tiemNang += calPercent(tiemNang, tl);
            }
            if (this.player.cFlag != 0) {
                if (this.player.cFlag == 8) {
                    tiemNang += calPercent(tiemNang, 10);
                } else {
                    tiemNang += calPercent(tiemNang, 5);
                }
            }
            if (buffExpSatellite) {
                tiemNang += calPercent(tiemNang, 20);
            }
            if (player.isPet) {
                Attribute at = ServerManager.gI().getAttributeManager().find(ConstAttribute.TNSM);
                if (at != null && !at.isExpired()) {
                    tiemNang += calPercent(tiemNang, at.getValue());
                }
            }
            if (this.player.isPet) {
                int tltnsm = ((Pet) this.player).master.nPoint.tlTNSMPet;
                if (tltnsm > 0) {
                    tiemNang += calPercent(tiemNang, tltnsm);
                }
            }
            long tn = tiemNang;
            if (this.player.charms.tdTriTue > System.currentTimeMillis()) {
                tiemNang += tn;
            }
            if (this.player.charms.tdTriTue3 > System.currentTimeMillis()) {
                tiemNang += tn * 2;
            }
            if (this.player.charms.tdTriTue4 > System.currentTimeMillis()) {
                tiemNang += tn * 3;
            }
            if (this.intrinsic != null && this.intrinsic.id == 24) {
                tiemNang += calPercent(tiemNang, this.intrinsic.param1);
            }
            if (this.power >= 40000000000L) {
                tiemNang -= calPercent(tiemNang, 90);
            }
            if (this.player.isPet) {
                if (((Pet) this.player).master.charms.tdDeTu > System.currentTimeMillis()) {
                    tiemNang += tn * 2;
                }
            }
            tiemNang *= Manager.RATE_EXP_SERVER;
            tiemNang /= 2;
            tiemNang = calSubTNSM(tiemNang);
            if (tiemNang <= 0) {
                tiemNang = 1;
            }
        } else {
            tiemNang = 10;
        }
        return tiemNang;
    }

    public long calSubTNSM(long tiemNang) {
        if (power >= 110000000000L) {
            tiemNang -= calPercent(tiemNang, 90);
        } else if (power >= 100000000000L) {
            tiemNang -= calPercent(tiemNang, 85);
        } else if (power >= 90000000000L) {
            tiemNang -= calPercent(tiemNang, 80);
        } else if (power >= 80000000000L) {
            tiemNang -= calPercent(tiemNang, 75);
        }
        return tiemNang;
    }

    public short getTileHutHp(boolean isMob) {
        if (isMob) {
            return (short) (this.tlHutHp + this.tlHutHpMob);
        } else {
            return this.tlHutHp;
        }
    }

    public short getTiLeHutMp() {
        return this.tlHutMp;
    }

    public int subDameInjureWithDeff(int dame) {
        return legacyStat(subDameInjureWithDeff(BigInteger.valueOf(dame)));
    }

    public BigInteger subDameInjureWithDeff(BigInteger incoming) {
        if (incoming.signum() <= 0) return BigInteger.ZERO;
        BigInteger damage = incoming.subtract(exactDefense());
        if (this.player.itemTime.isUseGiapXen) {
            damage = damage.divide(BigInteger.TWO);
        }
        if (this.player.itemTime.isUseGiapXen2) {
            damage = damage.subtract(calPercent(damage, 60));
        }
        return damage.max(BigInteger.ONE);
    }

    /*------------------------------------------------------------------------*/
    public boolean canOpenPower() {
        return this.power >= getPowerLimit();
    }

    public long getPowerLimit() {
        if (powerLimit != null) {
            return powerLimit.getPower();
        }
        return 0;
    }

    public long getPowerNextLimit() {
        PowerLimit powerLimit = PowerLimitManager.getInstance().get(limitPower + 1);
        if (powerLimit != null) {
            return powerLimit.getPower();
        }
        return 0;
    }

    // **************************************************************************
    // POWER - TIEM NANG
    public void powerUp(long power) {
        this.power = ProgressionMath.addReward(this.power, power);
        TaskService.gI().checkDoneTaskPower(player, this.power);
    }

    public void tiemNangUp(long tiemNang) {
        this.tiemNang = ProgressionMath.addReward(this.tiemNang, tiemNang);
    }

    public void increasePoint(byte type, short point) {
        if (powerLimit == null) {
            return;
        }
        if (point <= 0) {
            return;
        }
        boolean updatePoint = false;
        long tiemNangUse = 0;
        if (type == 0) {
            int pointHp = point * 20;
            tiemNangUse = ProgressionMath.arithmeticCost(point, (long) this.hpg + 1000, 20, 1);
            if (((long) this.hpg + pointHp) <= powerLimit.getHp()) {
                if (doUseTiemNang(tiemNangUse)) {
                    hpg += pointHp;
                    updatePoint = true;
                }
            } else {
                Service.getInstance().sendThongBao(player, "HP của bạn đã đạt mức tối đa");
                Service.getInstance().sendMoney(player);
                return;
            }
        }
        if (type == 1) {
            int pointMp = point * 20;
            tiemNangUse = ProgressionMath.arithmeticCost(point, (long) this.mpg + 1000, 20, 1);
            if (((long) this.mpg + pointMp) <= powerLimit.getMp()) {
                if (doUseTiemNang(tiemNangUse)) {
                    mpg += pointMp;
                    updatePoint = true;
                }
            } else {
                Service.getInstance().sendThongBao(player, "KI của bạn đã đạt mức tối đa");
                Service.getInstance().sendMoney(player);
                return;
            }
        }
        if (type == 2) {
            tiemNangUse = ProgressionMath.arithmeticCost(point, this.dameg, 1, 100);
            if (((long) this.dameg + point) <= powerLimit.getDamage()) {
                if (doUseTiemNang(tiemNangUse)) {
                    dameg += point;
                    updatePoint = true;
                }
            } else {
                Service.getInstance().sendThongBao(player, "Sức đánh của bạn đã đạt mức tối đa");
                Service.getInstance().sendMoney(player);
                return;
            }
        }
        if (type == 3) {
            tiemNangUse = ((long) this.defg + 5) * 100000;
            if (((long) this.defg + point) <= powerLimit.getDefense()) {
                if (doUseTiemNang(tiemNangUse)) {
                    defg += point;
                    updatePoint = true;
                }
            } else {
                Service.getInstance().sendThongBao(player, "Giáp của bạn đã đạt mức tối đa");
                Service.getInstance().sendMoney(player);
                return;
            }
        }
        if (type == 4) {
            tiemNangUse = ProgressionMath.criticalCost(this.critg);
            if (((long) this.critg + point) <= powerLimit.getCritical()) {
                if (doUseTiemNang(tiemNangUse)) {
                    critg += point;
                    updatePoint = true;
                }
            } else {
                Service.getInstance().sendThongBao(player, "Chí mạng của bạn đã đạt mức tối đa");
                Service.getInstance().sendMoney(player);
                return;
            }
        }
        if (updatePoint) {
            Service.getInstance().point(player);
        }
    }

    private boolean doUseTiemNang(long tiemNang) {
        if (tiemNang < 0 || this.tiemNang < tiemNang) {
            Service.getInstance().sendThongBaoOK(player, "Bạn không đủ tiềm năng");
            return false;
        }
        if (this.tiemNang >= tiemNang) {
            this.tiemNang -= tiemNang;
            TaskService.gI().checkDoneTaskUseTiemNang(player);
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------------------
    private long lastTimeHoiPhuc;
    private long lastTimeHoiStamina;

    public void update() {
        if (player != null && player.effectSkill != null) {
            if (player.effectSkill.isCharging && player.effectSkill.countCharging < 10) {
                int tiLeHoiPhuc = SkillUtil.getPercentCharge(player.playerSkill.skillSelect.point);
                if (player.effectSkill.isCharging && !player.isDie() && !player.effectSkill.isHaveEffectSkill()
                        && (hp < hpMax || mp < mpMax)) {
                    PlayerService.gI().hoiPhuc(player, (int) calPercent(hpMax, tiLeHoiPhuc),
                            (int) calPercent(mpMax, tiLeHoiPhuc));
                    if (player.effectSkill.countCharging % 3 == 0) {
                        Service.getInstance().chat(player, "Phục hồi năng lượng " + getCurrPercentHP() + "%");
                    }
                } else {
                    EffectSkillService.gI().stopCharge(player);
                }
                if (++player.effectSkill.countCharging >= 10) {
                    EffectSkillService.gI().stopCharge(player);
                }
            }
            if (Util.canDoWithTime(lastTimeHoiPhuc, 30000)) {
                PlayerService.gI().hoiPhuc(this.player, hpHoi, mpHoi);
                this.lastTimeHoiPhuc = System.currentTimeMillis();
            }
            if (Util.canDoWithTime(lastTimeHoiStamina, 60000) && this.stamina < this.maxStamina) {
                this.stamina++;
                this.lastTimeHoiStamina = System.currentTimeMillis();
                if (!this.player.isBoss && !this.player.isPet) {
                    PlayerService.gI().sendCurrentStamina(this.player);
                }
            }
        }
        // hồi phục 30s
        // hồi phục thể lực
    }

    private void setBasePoint() {
        setHpMax();
        setMpMax();
        setDame();
        setDef();
        setCrit();
        setHpHoi();
        setMpHoi();
        setNeDon();
        setCritDame();
        setSpeed();
        setAttributeOverLimit();
    }

    public void setAttributeOverLimit() {
        int max = Integer.MAX_VALUE;
        int min = -100000000;
        if (this.hpMax < 0) {
            if (this.hpMax < min) {
                this.hpMax = max;
            } else {
                this.hpMax = 1;
            }
        }
        if (this.mpMax < 0) {
            if (this.mpMax < min) {
                this.mpMax = max;
            } else {
                this.mpMax = 1;
            }
        }
        if (this.dame < 0) {
            if (this.dame < min) {
                this.dame = max;
            } else {
                this.dame = 1;
            }
        }
        if (this.def < 0) {
            if (this.def < min) {
                this.def = max;
            } else {
                this.def = 1;
            }
        }
        if (this.crit < 0) {
            if (this.crit < min) {
                this.crit = max;
            } else {
                this.crit = 1;
            }
        }
        setHp();
        setMp();
    }

    public long calPercent(long param, int percent) {
        return ProgressionMath.percent(param, percent);
    }

    public BigInteger calPercent(BigInteger value, int percent) {
        return value.multiply(BigInteger.valueOf(percent)).divide(BigInteger.valueOf(100));
    }

    public BigInteger exactHpMax() { return exactHpMax == null ? BigInteger.valueOf(hpMax) : exactHpMax; }
    public BigInteger exactMpMax() { return exactMpMax == null ? BigInteger.valueOf(mpMax) : exactMpMax; }
    public BigInteger exactDamage() { return exactDamage == null ? BigInteger.valueOf(dame) : exactDamage; }
    public BigInteger exactDefense() { return exactDefense == null ? BigInteger.valueOf(def) : exactDefense; }

    public synchronized BigInteger exactHp() { return exactHp == null ? BigInteger.valueOf(hp) : exactHp; }
    public synchronized BigInteger exactMp() { return exactMp == null ? BigInteger.valueOf(mp) : exactMp; }

    /** Enable only for a session whose combat and protocol adapters use exact values. */
    public synchronized void setExactVitals(BigInteger currentHp, BigInteger currentMp) {
        if (currentHp.signum()<0 || currentMp.signum()<0) throw new IllegalArgumentException("Negative current vitals");
        exactHp=currentHp.min(exactHpMax());exactMp=currentMp.min(exactMpMax());
        hp=legacyStat(exactHp);mp=legacyStat(exactMp);
    }

    public record ExactHit(BigInteger applied, boolean killed) {}

    /** Raw HP deduction; caller resolves defense/effects first. Only the lethal transition reports killed. */
    public synchronized ExactHit damageExact(BigInteger damage) {
        if(exactHp==null)throw new IllegalStateException("Exact vitals not enabled");
        if(damage.signum()<0)throw new IllegalArgumentException("Negative damage");
        BigInteger before=exactHp,applied=before.min(damage);
        exactHp=before.subtract(applied);hp=legacyStat(exactHp);
        return new ExactHit(applied,before.signum()>0 && exactHp.signum()==0);
    }

    public synchronized void healExact(BigInteger health,BigInteger ki) {
        if(exactHp==null)throw new IllegalStateException("Exact vitals not enabled");
        if(health.signum()<0 || ki.signum()<0)throw new IllegalArgumentException("Negative recovery");
        setExactVitals(exactHp.add(health),exactMp.add(ki));
    }

    public synchronized boolean spendExactKi(BigInteger amount) {
        if(exactMp==null)throw new IllegalStateException("Exact vitals not enabled");
        if(amount.signum()<0)throw new IllegalArgumentException("Negative KI cost");
        if(exactMp.compareTo(amount)<0)return false;
        exactMp=exactMp.subtract(amount);mp=legacyStat(exactMp);return true;
    }

    private static int exactPercent(BigInteger current,BigInteger maximum) {
        return maximum.signum()==0 ? 0 : current.multiply(BigInteger.valueOf(100)).divide(maximum).min(BigInteger.valueOf(100)).intValueExact();
    }

    /** Call only after a confirmed progression transaction; no inventory mutation. */
    public void bindUnlimitedBase(UnlimitedProgression state) { this.unlimitedBase = java.util.Objects.requireNonNull(state); }

    /** Boss spawn initialization must start from its original BossData, not the previous grown state. */
    public synchronized void clearExactStateForRespawn() {
        unlimitedBase=null;exactHp=exactMp=null;
        exactHpMax=exactMpMax=exactDamage=exactDefense=null;
    }

    private BigInteger exactBase(UnlimitedProgression.Stat stat, int legacy) {
        return unlimitedBase == null ? BigInteger.valueOf(legacy) : unlimitedBase.get(stat);
    }

    private static int legacyStat(BigInteger value) {
        return value.max(BigInteger.ZERO).min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact();
    }

    private BigInteger randomizeDamage(BigInteger damage) {
        damage = damage.max(BigInteger.ZERO);
        BigInteger variation = calPercent(damage, 5);
        BigInteger minimum = damage.subtract(variation);
        BigInteger count = variation.multiply(BigInteger.TWO).add(BigInteger.ONE);
        BigInteger random;
        do { random = new BigInteger(count.bitLength(), java.util.concurrent.ThreadLocalRandom.current()); }
        while (random.compareTo(count) >= 0);
        return minimum.add(random);
    }

    private void accumulateExactFlatOption(int id, int param) {
        BigInteger value = BigInteger.valueOf(param);
        switch (id) {
            case 0 -> exactDamageAdd = exactDamageAdd.add(value);
            case 2 -> { value=value.multiply(BigInteger.valueOf(1000)); exactHpAdd=exactHpAdd.add(value); exactMpAdd=exactMpAdd.add(value); }
            case 6 -> exactHpAdd=exactHpAdd.add(value);
            case 7 -> exactMpAdd=exactMpAdd.add(value);
            case 22 -> exactHpAdd=exactHpAdd.add(value.multiply(BigInteger.valueOf(1000)));
            case 23 -> exactMpAdd=exactMpAdd.add(value.multiply(BigInteger.valueOf(1000)));
            case 47 -> exactDefenseAdd=exactDefenseAdd.add(value);
            case 48 -> { exactHpAdd=exactHpAdd.add(value); exactMpAdd=exactMpAdd.add(value); }
        }
    }

    public void dispose() {
        this.intrinsic = null;
        this.player = null;
        this.tlHp = null;
        this.tlMp = null;
        this.tlDef = null;
        this.tlDame = null;
        this.tlDameAttMob = null;
        this.tlSDDep = null;
        this.tlTNSM = null;
        this.tlDameCrit = null;
        this.tlSpeed = null;
    }
}
