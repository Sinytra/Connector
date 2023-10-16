package dev.su5ed.sinytra.connector.mod.mixin.recipebook;

import net.minecraft.client.RecipeBookCategories;
import net.minecraftforge.client.RecipeBookManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = RecipeBookManager.class, remap = false)
public class RecipeBookManagerMixin {
    @Shadow
    @Final
    private static Map<RecipeBookCategories, List<RecipeBookCategories>> AGGREGATE_CATEGORIES;

    @Inject(at = @At("TAIL"), method = "init", remap = false)
    private static void init(CallbackInfo ci) {
        //Make lists mutable.
        RecipeBookCategoriesAccessor.setAGGREGATE_CATEGORIES(new HashMap<>(RecipeBookCategories.AGGREGATE_CATEGORIES));
        RecipeBookCategories.AGGREGATE_CATEGORIES.replaceAll((category, categories) -> new ArrayList<>(categories));

        //Since we replaced RecipeBookCategories.AGGREGATE_CATEGORIES,
        //we need to add entries that might have been added by fabric mods.
        AGGREGATE_CATEGORIES.forEach((category, categories) -> {
            List<RecipeBookCategories> bookCategories = RecipeBookCategories.AGGREGATE_CATEGORIES.computeIfAbsent(category, categories1 -> new ArrayList<>());
            //We need to make categories mutable, so we can remove them. Just in case mods return an immutable list.
            List<RecipeBookCategories> mutableCategories = new ArrayList<>(categories);
            mutableCategories.removeIf(bookCategories::contains);
            //We, unfortunately, can't easily respect insertion order here.
            bookCategories.addAll(mutableCategories);
        });
    }
}