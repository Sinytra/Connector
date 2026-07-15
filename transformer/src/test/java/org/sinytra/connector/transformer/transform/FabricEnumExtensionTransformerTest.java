package org.sinytra.connector.transformer.transform;

import com.google.gson.JsonParser;
import net.neoforged.art.api.Transformer.ResourceEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricEnumExtensionTransformerTest {
    private static final String MIXIN_CLASS = "vectorwing/farmersdelight/common/mixin/refabricated/RecipeBookTypeMixin";

    @Test
    void convertsFarmersDelightRecipeBookType(@TempDir Path tempDir) throws Exception {
        FabricEnumExtensionTransformer transformer = new FabricEnumExtensionTransformer(
            "farmersdelight", "farmersdelight.classtweaker", List.of(MIXIN_CLASS), List.of("farmersdelight.mixins.json")
        );

        ResourceEntry metadata = transformer.process(resource("fabric.mod.json", "{\"id\":\"farmersdelight\"}"));
        assertEquals(
            FabricEnumExtensionTransformer.ENUM_EXTENSIONS_PATH,
            JsonParser.parseString(text(metadata)).getAsJsonObject().getAsJsonObject("custom")
                .get(FabricEnumExtensionTransformer.ENUM_EXTENSIONS_METADATA).getAsString()
        );

        ResourceEntry classTweaker = transformer.process(resource("farmersdelight.classtweaker", """
            classTweaker v2 official
            transitive-extend-enum net/minecraft/world/inventory/RecipeBookType FARMERSDELIGHT_COOKING
            transitive-extend-enum net/minecraft/client/gui/screens/recipebook/SearchRecipeBookCategory FARMERSDELIGHT_COOKING
            """));
        assertFalse(text(classTweaker).contains("RecipeBookType FARMERSDELIGHT_COOKING"));
        assertTrue(text(classTweaker).contains("SearchRecipeBookCategory"), text(classTweaker));

        ResourceEntry mixins = transformer.process(resource("farmersdelight.mixins.json", """
            {"mixins":["OtherMixin","refabricated.RecipeBookTypeMixin"]}
            """));
        assertFalse(text(mixins).contains("RecipeBookTypeMixin"));
        assertTrue(text(mixins).contains("OtherMixin"));

        transformer.writeGeneratedResources(tempDir);
        String extensions = Files.readString(tempDir.resolve(FabricEnumExtensionTransformer.ENUM_EXTENSIONS_PATH));
        assertTrue(extensions.contains("FARMERSDELIGHT_COOKING"));
        assertTrue(extensions.contains("\"constructor\": \"()V\""));
    }

    @Test
    void leavesOtherModsUntouched() {
        FabricEnumExtensionTransformer transformer = new FabricEnumExtensionTransformer(
            "other", "other.classtweaker", List.of(), List.of()
        );
        ResourceEntry input = resource("fabric.mod.json", "{\"id\":\"other\"}");
        assertEquals(text(input), text(transformer.process(input)));
    }

    private static ResourceEntry resource(String name, String value) {
        return ResourceEntry.create(name, 0, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(ResourceEntry entry) {
        return new String(entry.getData(), StandardCharsets.UTF_8);
    }
}
