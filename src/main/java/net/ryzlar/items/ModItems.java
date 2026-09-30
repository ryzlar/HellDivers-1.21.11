package net.ryzlar.items;

import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;


public class ModItems {

    public static List<Item> ALL_ITEMS = new ArrayList<>();

    public static final Item STRATEGEM_BALL = ItemHelper.register("strategem_ball", StrategemBallItem::new, new Item.Properties());
    public static final Item ARM_PIECE = ItemHelper.register("arm_piece",Item::new, new Item.Properties());


    public static void initialize() {

        ALL_ITEMS.add(STRATEGEM_BALL);
        ALL_ITEMS.add(ARM_PIECE);

    }
}
