package com.Chris__.pest_control.data;

import java.util.ArrayList;
import java.util.List;

public final class PestShopConfig {

    public int version = 1;
    public List<ShopItem> items = new ArrayList<>(List.of(
            item("void_helm", "Void Helm", 150, "ARMOR_HELMET_IRON", 1),
            item("void_chest", "Void Chest", 250, "ARMOR_CHESTPLATE_IRON", 1),
            item("void_legs", "Void Legs", 250, "ARMOR_LEGGINGS_IRON", 1),
            item("void_gloves", "Void Gloves", 120, "ARMOR_GLOVES_LEATHER", 1),
            item("void_wand", "Void Wand", 400, "WEAPON_WAND_BASIC", 1),
            item("repair_kit", "Repair Kit", 25, "ITEM_TOOLKIT", 1),
            item("battle_ration", "Battle Ration", 10, "ITEM_FOOD_BREAD", 4)
    ));

    public static final class ShopItem {
        public String id = "";
        public String name = "";
        public int cost = 0;
        public String itemId = "REPLACE_ME";
        public int amount = 1;
        public boolean enabled = true;
    }

    private static ShopItem item(String id, String name, int cost, String itemId, int amount) {
        ShopItem item = new ShopItem();
        item.id = id;
        item.name = name;
        item.cost = cost;
        item.itemId = itemId;
        item.amount = amount;
        return item;
    }
}
