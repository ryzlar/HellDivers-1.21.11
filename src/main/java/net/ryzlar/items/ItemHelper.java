package net.ryzlar.items;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.ryzlar.LaserMod;

import java.util.function.Function;

public class ItemHelper {




    public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings ) {

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(LaserMod.MOD_ID, name));

        T item = itemFactory.apply(settings.setId(itemKey));

        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        return item;
    }

//    public static void AddToCreativeTab(Item item) {
//        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS)
//                .register((itemGroup) -> itemGroup.accept(item));
//    }


}
