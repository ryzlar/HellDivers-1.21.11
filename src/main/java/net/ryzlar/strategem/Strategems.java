package net.ryzlar.strategem;

import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.ryzlar.strategem.StrategemInput.*;

/**
 * All stratagems, in menu order; the menu shows {@link #PAGE_SIZE} per page.
 * Adding one is a single line here, an {@link Attack} in {@link Attacks}
 * and a name in the lang file ({@code strategem.lasermod.<id>}).
 */
public final class Strategems {

    public static final int PAGE_SIZE = 5;

    private static final Map<Identifier, Strategem> BY_ID = new LinkedHashMap<>();

    // ---- Page 1
    public static final Strategem VOID_RIFT = register("void_rift", 0xB04BFF, DestructionLevel.EXTREME,
            Attacks::voidRift, LEFT, RIGHT, LEFT, DOWN, UP, DOWN);
    public static final Strategem METEOR_STRIKE = register("meteor_strike", 0xFF7A1F, DestructionLevel.HIGH,
            Attacks::meteorStrike, DOWN, LEFT, DOWN, UP, RIGHT);
    public static final Strategem ORBITAL_LIGHTNING = register("orbital_lightning", 0x4FD8FF, DestructionLevel.MODERATE,
            Attacks::orbitalLightning, UP, RIGHT, UP, DOWN);
    public static final Strategem VOLCANIC_ERUPTION = register("volcanic_eruption", 0xFF4A12, DestructionLevel.EXTREME,
            Attacks::volcanicEruption, DOWN, DOWN, UP, LEFT, DOWN, RIGHT);
    public static final Strategem NUCLEAR_STRIKE = register("nuclear_strike", 0xF2FF3D, DestructionLevel.CATASTROPHIC,
            Attacks::nuclearStrike, UP, UP, DOWN, DOWN, LEFT, RIGHT, LEFT, RIGHT);

    private Strategems() {
    }

    private static Strategem register(String name, int color, DestructionLevel level, Attack attack, StrategemInput... code) {
        if (code.length != level.inputs()) {
            throw new IllegalStateException("Stratagem " + name + " needs " + level.inputs() + " inputs for level " + level);
        }
        Identifier id = Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, name);
        Strategem strategem = new Strategem(id, color, level, List.of(code), attack);
        for (Strategem other : BY_ID.values()) {
            if (other.matchesPrefix(strategem.code()) || strategem.matchesPrefix(other.code())) {
                throw new IllegalStateException("Code of " + name + " clashes with " + other.id());
            }
        }
        BY_ID.put(id, strategem);
        return strategem;
    }

    @Nullable
    public static Strategem get(@Nullable Identifier id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<Strategem> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static int pageCount() {
        return Math.max(1, (BY_ID.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** The stratagems on one menu page (0-based). */
    public static List<Strategem> page(int page) {
        List<Strategem> all = new ArrayList<>(BY_ID.values());
        int from = Math.min(all.size(), page * PAGE_SIZE);
        return all.subList(from, Math.min(all.size(), from + PAGE_SIZE));
    }

    /** Forces class loading so all stratagems are registered at startup. */
    public static void initialize() {
    }
}
