package nro.progression;

import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Map;

/** Exact progression model. Persistence and network adapters must use decimal strings. */
public final class UnlimitedProgression {
    public enum Stat { POWER, POTENTIAL, HP, KI, ATTACK, DEFENSE }
    public record Allocation(UnlimitedProgression state, BigInteger points, BigInteger cost) {}
    private static final BigInteger TWO = BigInteger.TWO;
    private final EnumMap<Stat, BigInteger> values;

    public UnlimitedProgression(Map<Stat, BigInteger> input) {
        values = new EnumMap<>(Stat.class);
        for (Stat stat : Stat.values()) {
            BigInteger value = input.getOrDefault(stat, BigInteger.ZERO);
            if (value == null || value.signum() < 0) throw new IllegalArgumentException("Negative or null stat");
            values.put(stat, value);
        }
    }

    public BigInteger get(Stat stat) { return values.get(stat); }

    public UnlimitedProgression reward(BigInteger power, BigInteger potential) {
        if (power.signum() < 0 || potential.signum() < 0) throw new IllegalArgumentException("Negative reward");
        var next = new EnumMap<>(values);
        next.put(Stat.POWER, get(Stat.POWER).add(power));
        next.put(Stat.POTENTIAL, get(Stat.POTENTIAL).add(potential));
        return new UnlimitedProgression(next);
    }

    /** Existing HP/KI/attack arithmetic series; defense charges every purchased point. */
    public BigInteger cost(Stat stat, BigInteger points) {
        if (points.signum() < 0) throw new IllegalArgumentException("Negative point count");
        BigInteger base = get(stat), step, multiplier;
        switch (stat) {
            case HP, KI -> { base = base.add(BigInteger.valueOf(1000)); step = BigInteger.valueOf(20); multiplier = BigInteger.ONE; }
            case ATTACK -> { step = BigInteger.ONE; multiplier = BigInteger.valueOf(100); }
            case DEFENSE -> { base = base.add(BigInteger.valueOf(5)); step = BigInteger.ONE; multiplier = BigInteger.valueOf(100000); }
            default -> throw new IllegalArgumentException("Stat cannot be purchased");
        }
        if (points.signum() == 0) return BigInteger.ZERO;
        return points.multiply(base.multiply(TWO).add(points.subtract(BigInteger.ONE).multiply(step)))
                .divide(TWO).multiply(multiplier);
    }

    /** Spend at most the requested potential budget without iterating point by point. */
    public Allocation allocate(Stat stat, BigInteger budget) {
        if (budget.signum() < 0 || budget.compareTo(get(Stat.POTENTIAL)) > 0)
            throw new IllegalArgumentException("Insufficient potential or negative budget");
        cost(stat, BigInteger.ZERO); // Validate the selected stat even with a zero budget.
        if (budget.signum() == 0) return new Allocation(this, BigInteger.ZERO, BigInteger.ZERO);
        BigInteger low = BigInteger.ZERO, high = BigInteger.ONE;
        while (cost(stat, high).compareTo(budget) <= 0) { low = high; high = high.shiftLeft(1); }
        while (high.subtract(low).compareTo(BigInteger.ONE) > 0) {
            BigInteger middle = low.add(high).shiftRight(1);
            if (cost(stat, middle).compareTo(budget) <= 0) low = middle; else high = middle;
        }
        BigInteger used = cost(stat, low);
        var next = new EnumMap<>(values);
        next.put(Stat.POTENTIAL, get(Stat.POTENTIAL).subtract(used));
        BigInteger increment = (stat == Stat.HP || stat == Stat.KI) ? BigInteger.valueOf(20) : BigInteger.ONE;
        next.put(stat, get(stat).add(low.multiply(increment)));
        return new Allocation(new UnlimitedProgression(next), low, used);
    }

    public Map<String, String> snapshot() {
        var result = new java.util.LinkedHashMap<String, String>();
        for (Stat stat : Stat.values()) result.put(stat.name(), get(stat).toString());
        return Map.copyOf(result);
    }

    public static UnlimitedProgression restore(Map<String, String> stored) {
        var result = new EnumMap<Stat, BigInteger>(Stat.class);
        for (Stat stat : Stat.values()) {
            String value = stored.get(stat.name());
            if (value == null || !value.matches("0|[1-9][0-9]*"))
                throw new IllegalArgumentException("Invalid saved stat: " + stat);
            result.put(stat, new BigInteger(value));
        }
        return new UnlimitedProgression(result);
    }
}
