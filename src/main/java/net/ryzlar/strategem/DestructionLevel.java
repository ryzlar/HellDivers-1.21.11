package net.ryzlar.strategem;

/**
 * How destructive a stratagem is. Decides its cooldown and how many arrows its code has.
 */
public enum DestructionLevel {
    LOW(1, 30, 3, 0x7CE07C),
    MODERATE(2, 2 * 60, 4, 0xE8D44D),
    HIGH(3, 5 * 60, 5, 0xFF9A3C),
    EXTREME(4, 12 * 60, 6, 0xFF4040),
    CATASTROPHIC(5, 25 * 60, 8, 0xD94BFF);

    private final int tier;
    private final int cooldownSeconds;
    private final int inputs;
    private final int color;

    DestructionLevel(int tier, int cooldownSeconds, int inputs, int color) {
        this.tier = tier;
        this.cooldownSeconds = cooldownSeconds;
        this.inputs = inputs;
        this.color = color;
    }

    public int tier() {
        return tier;
    }

    public int cooldownTicks() {
        return cooldownSeconds * 20;
    }

    /** Number of arrows a code of this level must have. */
    public int inputs() {
        return inputs;
    }

    public int color() {
        return color;
    }
}
