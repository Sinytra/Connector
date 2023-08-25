package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.RecipeBookCategories;
import net.minecraftforge.client.RecipeBookManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = RecipeBookManager.class, remap = false)
public class RecipeBookManagerMixin {

    @Mutable
    @Shadow
    @Final
    private static Map<RecipeBookCategories, List<RecipeBookCategories>> AGGREGATE_CATEGORIES;

    @Inject(at = @At("TAIL"), method = "init", remap = false)
    private static void init(CallbackInfo ci) {
        //Make lists mutable.
        AGGREGATE_CATEGORIES.replaceAll((category, categories) -> new ArrayList<>(categories));

        //Since we replaced RecipeBookCategories.AGGREGATE_CATEGORIES,
        //we need to add entries that might have been added by fabric mods.
        RecipeBookCategories.AGGREGATE_CATEGORIES.forEach((category, categories) -> {
            List<RecipeBookCategories> categoriesList = AGGREGATE_CATEGORIES.computeIfAbsent(category, categories1 -> new ArrayList<>());
            categories.removeIf(categoriesList::contains);
            //We, unfortunately, can't easily respect insertion order here.
            categoriesList.addAll(categories);
        });

        //Set the reference to the new map.
        RecipeBookCategoriesAccessor.setAGGREGATE_CATEGORIES(AGGREGATE_CATEGORIES);
    }

}