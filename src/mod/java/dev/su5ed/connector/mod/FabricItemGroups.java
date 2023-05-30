package dev.su5ed.connector.mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.CreativeModeTabEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

public final class FabricItemGroups {
    private static final Map<ResourceLocation, CreativeModeTab> TABS = new HashMap<>();
    
    public static void register(ResourceLocation id, CreativeModeTab tab) {
        TABS.put(id, tab);
    }

    @SubscribeEvent
    public static void registerCreativeTabs(CreativeModeTabEvent.Register event) {
        TABS.forEach((id, tab) -> event.registerCreativeModeTab(id, builder -> {
            builder.title(tab.getDisplayName());
            builder.icon(tab::getIconItem);
            builder.displayItems((params, output) -> {
                tab.buildContents(params);
                output.acceptAll(tab.getDisplayItems(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
                output.acceptAll(tab.getSearchTabDisplayItems(), CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
            });
            if (tab.isAlignedRight()) builder.alignedRight();
            if (!tab.showTitle()) builder.hideTitle();
            if (!tab.canScroll()) builder.noScrollBar();
            builder.backgroundSuffix(tab.getBackgroundSuffix());
            if (tab.hasSearchBar()) builder.withSearchBar();
            builder.withTabsImage(tab.getTabsImage());
            builder.withLabelColor(tab.getLabelColor());
            builder.withSlotColor(tab.getSlotColor());
        }));
    }

    private FabricItemGroups() {}
}
