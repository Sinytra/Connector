package org.sinytra.connector.mod.mixin.recipebook;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.client.RecipeBookCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;

import static net.minecraft.client.RecipeBookCategories.*;

@Mixin(RecipeBookCategories.class)
public abstract class RecipeBookCategoriesMixin {
    @Redirect(at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/RecipeBookManager;getAggregateCategories()Ljava/util/Map;"), method = "<clinit>", remap = false)
    private static Map<RecipeBookCategories, List<RecipeBookCategories>> getAggregateCategories() {
        //Reset to vanilla values.
        //This will be merged back during RecipeBookManager.init().
        return ImmutableMap.of(CRAFTING_SEARCH, ImmutableList.of(CRAFTING_EQUIPMENT, CRAFTING_BUILDING_BLOCKS, CRAFTING_MISC, CRAFTING_REDSTONE), FURNACE_SEARCH, ImmutableList.of(FURNACE_FOOD, FURNACE_BLOCKS, FURNACE_MISC), BLAST_FURNACE_SEARCH, ImmutableList.of(BLAST_FURNACE_BLOCKS, BLAST_FURNACE_MISC), SMOKER_SEARCH, ImmutableList.of(SMOKER_FOOD));
    }
}
