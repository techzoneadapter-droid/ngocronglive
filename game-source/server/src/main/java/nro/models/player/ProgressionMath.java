package nro.models.player;

/** Checked arithmetic for the existing progression and potential APIs. */
final class ProgressionMath {
    private ProgressionMath() {
    }

    static long addReward(long current, long reward) {
        if (reward <= 0) {
            return current;
        }
        return current > Long.MAX_VALUE - reward ? Long.MAX_VALUE : current + reward;
    }

    static long arithmeticCost(int count, long first, int increment, int multiplier) {
        if (count <= 0 || first < 0 || increment < 0 || multiplier <= 0) {
            return -1;
        }
        try {
            long last = Math.addExact(first, Math.multiplyExact(count - 1L, increment));
            // Divide before adding/multiplying so a representable total is not
            // rejected merely because the intermediate endpoint sum overflowed.
            long sum;
            if (count % 2 == 0) {
                sum = Math.multiplyExact(count / 2L, Math.addExact(first, last));
            } else {
                long midpoint = Math.addExact(first, (last - first) / 2);
                sum = Math.multiplyExact(count, midpoint);
            }
            return Math.multiplyExact(sum, multiplier);
        } catch (ArithmeticException overflow) {
            return -1;
        }
    }

    static long criticalCost(int current) {
        if (current < 0) {
            return -1;
        }
        long cost = 50_000_000L;
        for (int level = 0; level < current; level++) {
            if (cost > Long.MAX_VALUE / 5) {
                return -1;
            }
            cost *= 5;
        }
        return cost;
    }
}
