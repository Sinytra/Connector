package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.RecipeBookCategories;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(RecipeBookCategories.class)
public interface RecipeBookCategoriesAccessor {

    @Accessor("AGGREGATE_CATEGORIES")
    static void setAGGREGATE_CATEGORIES(Map<RecipeBookCategories, List<RecipeBookCategories>> map) {
        throw new UnsupportedOperationException();
    }

}
