package net.ryzlar.renderer;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.ryzlar.LaserMod;
import net.ryzlar.fx.DeviceFx;
import org.jetbrains.annotations.Nullable;

/**
 * Item-model hooks used by assets/lasermod/items/*.json:
 * <ul>
 *     <li>{@code lasermod:arm_screen}, {@code lasermod:arm_led}, {@code lasermod:ball_core} - live tint sources that
 *     animate the emissive screen, LEDs and ball core.</li>
 *     <li>{@code lasermod:menu_raise} - 0..1, raises the Arm Piece toward the eye in first person while the menu is open.</li>
 *     <li>{@code lasermod:program_progress} - 0..1 while a ball is being programmed (drives the data-scan ring).</li>
 * </ul>
 */
public final class DeviceModelProperties {

    private DeviceModelProperties() {
    }

    public static void register() {
        ItemTintSources.ID_MAPPER.put(id("arm_screen"), ArmScreen.CODEC);
        ItemTintSources.ID_MAPPER.put(id("arm_led"), ArmLed.CODEC);
        ItemTintSources.ID_MAPPER.put(id("ball_core"), BallCore.CODEC);
        RangeSelectItemModelProperties.ID_MAPPER.put(id("menu_raise"), MenuRaise.CODEC);
        RangeSelectItemModelProperties.ID_MAPPER.put(id("program_progress"), ProgramProgress.CODEC);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, path);
    }

    private static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    public record ArmScreen() implements ItemTintSource {
        public static final MapCodec<ArmScreen> CODEC = MapCodec.unit(new ArmScreen());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            return opaque(DeviceFx.armScreen(entity));
        }

        @Override
        public MapCodec<ArmScreen> type() {
            return CODEC;
        }
    }

    public record ArmLed() implements ItemTintSource {
        public static final MapCodec<ArmLed> CODEC = MapCodec.unit(new ArmLed());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            return opaque(DeviceFx.armLed(entity));
        }

        @Override
        public MapCodec<ArmLed> type() {
            return CODEC;
        }
    }

    public record BallCore() implements ItemTintSource {
        public static final MapCodec<BallCore> CODEC = MapCodec.unit(new BallCore());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            return opaque(DeviceFx.ballCore(stack, entity));
        }

        @Override
        public MapCodec<BallCore> type() {
            return CODEC;
        }
    }

    public record MenuRaise() implements RangeSelectItemModelProperty {
        public static final MapCodec<MenuRaise> CODEC = MapCodec.unit(new MenuRaise());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            Entity holder = owner instanceof Entity entity ? entity : null;
            return DeviceFx.menuRaise(holder);
        }

        @Override
        public MapCodec<MenuRaise> type() {
            return CODEC;
        }
    }

    public record ProgramProgress() implements RangeSelectItemModelProperty {
        public static final MapCodec<ProgramProgress> CODEC = MapCodec.unit(new ProgramProgress());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            Entity holder = owner instanceof Entity entity ? entity : null;
            return DeviceFx.programProgress(stack, holder);
        }

        @Override
        public MapCodec<ProgramProgress> type() {
            return CODEC;
        }
    }
}
