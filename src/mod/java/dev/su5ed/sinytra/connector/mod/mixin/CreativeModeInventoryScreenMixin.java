package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = CreativeModeInventoryScreen.class, priority = 100)
public class CreativeModeInventoryScreenMixin {
    private static int fabric_currentPage = -1;

    private void fabric_updateSelection() {}

    private boolean fabric_isGroupVisible(CreativeModeTab tab) {
        return true;
    }

    private static int fabric_getPage() {
        return -1;
    }
}
