package org.sinytra.connector.transformer.transform;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.visitors.ForwardingVisitor;
import net.neoforged.art.api.Transformer;
import org.sinytra.connector.transformer.jar.FabricModFileMetadata;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

public final class FabricEnumExtensionTransformer implements Transformer {
    static final String ENUM_EXTENSIONS_METADATA = "launchpad:enum_extensions";
    static final String ENUM_EXTENSIONS_PATH = "META-INF/connector_enum_extensions.json";

    private static final String FARMERS_DELIGHT = "farmersdelight";
    private static final String RECIPE_BOOK_TYPE = "net/minecraft/world/inventory/RecipeBookType";
    private static final String COOKING_CONSTANT = "FARMERSDELIGHT_COOKING";
    private static final String RECIPE_BOOK_TYPE_MIXIN = "vectorwing/farmersdelight/common/mixin/refabricated/RecipeBookTypeMixin";
    private static final String RECIPE_BOOK_TYPE_MIXIN_ENTRY = "refabricated.RecipeBookTypeMixin";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final boolean active;
    private final String classTweaker;
    private final Collection<String> mixinConfigs;

    public FabricEnumExtensionTransformer(FabricModFileMetadata metadata) {
        this(
            metadata.modMetadata().getId(),
            metadata.modMetadata().getClassTweaker(),
            metadata.mixinClasses(),
            metadata.mixinConfigs()
        );
    }

    FabricEnumExtensionTransformer(String modId, String classTweaker, Collection<String> mixinClasses, Collection<String> mixinConfigs) {
        this.active = FARMERS_DELIGHT.equals(modId) && mixinClasses.contains(RECIPE_BOOK_TYPE_MIXIN);
        this.classTweaker = classTweaker;
        this.mixinConfigs = mixinConfigs;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (!this.active) {
            return entry;
        }
        if (entry.getName().equals(TransformerUtil.FABRIC_MOD_JSON)) {
            return transformMetadata(entry);
        }
        if (entry.getName().equals(this.classTweaker)) {
            return transformClassTweaker(entry);
        }
        if (this.mixinConfigs.contains(entry.getName())) {
            return transformMixinConfig(entry);
        }
        return entry;
    }

    public void writeGeneratedResources(Path root) throws java.io.IOException {
        if (!this.active) {
            return;
        }
        Path output = root.resolve(ENUM_EXTENSIONS_PATH);
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(createEnumExtensions()), StandardCharsets.UTF_8);
    }

    private static ResourceEntry transformMetadata(ResourceEntry entry) {
        JsonObject json = parseJson(entry).getAsJsonObject();
        JsonObject custom = json.has("custom") ? json.getAsJsonObject("custom") : new JsonObject();
        custom.addProperty(ENUM_EXTENSIONS_METADATA, ENUM_EXTENSIONS_PATH);
        json.add("custom", custom);
        return withJson(entry, json);
    }

    private static ResourceEntry transformClassTweaker(ResourceEntry entry) {
        ClassTweakerWriter writer = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
        ForwardingVisitor filter = new ForwardingVisitor(writer) {
            @Override
            public void visitEnumExtension(String owner, String name, boolean transitive) {
                if (!owner.equals(RECIPE_BOOK_TYPE) || !name.equals(COOKING_CONSTANT)) {
                    super.visitEnumExtension(owner, name, transitive);
                }
            }
        };
        ClassTweakerReader.create(filter).read(entry.getData(), "official");
        return ResourceEntry.create(entry.getName(), entry.getTime(), writer.getOutput());
    }

    private static ResourceEntry transformMixinConfig(ResourceEntry entry) {
        JsonObject json = parseJson(entry).getAsJsonObject();
        JsonArray mixins = json.getAsJsonArray("mixins");
        if (mixins != null) {
            mixins.remove(new com.google.gson.JsonPrimitive(RECIPE_BOOK_TYPE_MIXIN_ENTRY));
        }
        return withJson(entry, json);
    }

    private static JsonObject createEnumExtensions() {
        JsonObject extension = new JsonObject();
        extension.addProperty("enum", RECIPE_BOOK_TYPE);
        extension.addProperty("name", COOKING_CONSTANT);
        extension.addProperty("constructor", "()V");
        extension.add("parameters", new JsonArray());

        JsonArray entries = new JsonArray();
        entries.add(extension);
        JsonObject root = new JsonObject();
        root.add("entries", entries);
        return root;
    }

    private static com.google.gson.JsonElement parseJson(ResourceEntry entry) {
        return JsonParser.parseReader(new InputStreamReader(new ByteArrayInputStream(entry.getData()), StandardCharsets.UTF_8));
    }

    private static ResourceEntry withJson(ResourceEntry entry, JsonObject json) {
        return ResourceEntry.create(entry.getName(), entry.getTime(), GSON.toJson(json).getBytes(StandardCharsets.UTF_8));
    }
}
