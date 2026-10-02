package net.ryzlar.items;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.ryzlar.LaserMod;

public class ModComponents {

    /** The stratagem a Strategem Ball is programmed with. Absent = empty ball. Stored per stack. */
    public static final DataComponentType<Identifier> STRATEGEM = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "strategem"),
            DataComponentType.<Identifier>builder()
                    .persistent(Identifier.CODEC)
                    .networkSynchronized(Identifier.STREAM_CODEC)
                    .build()
    );

    public static void initialize() {
    }
}
