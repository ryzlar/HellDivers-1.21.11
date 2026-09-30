package net.ryzlar.entities;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.ryzlar.LaserMod;

public class ModEntities {

    public static final EntityType<StrategemBallEntity> STRATAGEM_BALL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_ball"),
            EntityType.Builder.<StrategemBallEntity>of(StrategemBallEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "stratagem_ball")))
    );

    public static final EntityType<OrbitalLaserEntity> ORBITAL_LASER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "orbital_laser"),
            EntityType.Builder.<OrbitalLaserEntity>of(OrbitalLaserEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .fireImmune()
                    .clientTrackingRange(32)  // beam is visible from far away
                    .updateInterval(20)       // never moves
                    .build(ResourceKey.create(Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "orbital_laser")))
    );

    public static void initialize() {
    }
}