package net.ryzlar.tabs;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.ryzlar.LaserMod;
import net.ryzlar.items.ModItems;

public class TabHelper {


    public static final ResourceKey<CreativeModeTab> MOD_TAB_KEY = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, "laser_tab"));

    public static final CreativeModeTab MOD_TAB = FabricItemGroup.builder()
            .icon(() -> new ItemStack(ModItems.ARM_PIECE))
            .title(Component.translatable("itemGroup.lasermod"))
            .displayItems((params, output) -> {

                for (Item item: ModItems.ALL_ITEMS) {
                    output.accept(item);
                }

            })
            .build();

    public static void RegisterTab() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MOD_TAB_KEY, MOD_TAB);
    }

}
