package com.Chris__.pest_control.util;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.util.List;

public final class InventoryUtil {

    private InventoryUtil() {
    }

    /**
     * Tries to add the full stack to the player's non-armor inventory.
     * Returns false if the complete quantity cannot fit.
     */
    public static boolean tryGive(Player player, ItemStack stack) {
        if (player == null) return false;
        if (stack == null || stack.isEmpty() || stack.getQuantity() <= 0) return false;

        Inventory inv = player.getInventory();
        if (inv == null) return false;

        Item item;
        try {
            item = stack.getItem();
        } catch (Throwable ignored) {
            item = null;
        }
        if (item == null) return false;

        int maxStack = Math.max(1, item.getMaxStack());
        int quantity = stack.getQuantity();
        List<ItemContainer> containers = List.of(inv.getStorage(), inv.getBackpack(), inv.getHotbar(), inv.getTools(), inv.getUtility());

        int available = 0;
        for (ItemContainer container : containers) {
            if (container == null) continue;
            short cap = container.getCapacity();
            for (short i = 0; i < cap; i++) {
                ItemStack cur = container.getItemStack(i);
                if (cur == null || cur.isEmpty() || cur.getQuantity() <= 0) {
                    available += maxStack;
                } else if (cur.isStackableWith(stack)) {
                    available += Math.max(0, maxStack - cur.getQuantity());
                }
            }
        }

        if (available < quantity) return false;

        int remaining = quantity;

        // Fill existing stacks first.
        for (ItemContainer container : containers) {
            if (container == null) continue;
            short cap = container.getCapacity();
            for (short i = 0; i < cap; i++) {
                if (remaining <= 0) break;
                ItemStack cur = container.getItemStack(i);
                if (cur == null || cur.isEmpty() || cur.getQuantity() <= 0) continue;
                if (!cur.isStackableWith(stack)) continue;

                int space = Math.max(0, maxStack - cur.getQuantity());
                if (space <= 0) continue;

                int toAdd = Math.min(space, remaining);
                container.setItemStackForSlot(i, cur.withQuantity(cur.getQuantity() + toAdd));
                remaining -= toAdd;
            }
            if (remaining <= 0) break;
        }

        // Fill empty slots.
        for (ItemContainer container : containers) {
            if (container == null) continue;
            short cap = container.getCapacity();
            for (short i = 0; i < cap; i++) {
                if (remaining <= 0) break;
                ItemStack cur = container.getItemStack(i);
                if (cur != null && !cur.isEmpty() && cur.getQuantity() > 0) continue;

                int toAdd = Math.min(maxStack, remaining);
                container.setItemStackForSlot(i, stack.withQuantity(toAdd));
                remaining -= toAdd;
            }
            if (remaining <= 0) break;
        }

        return remaining <= 0;
    }
}
