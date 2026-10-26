package org.sinytra.connector.mod.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TrimmedArmorItemDefinitions {
    private static final String TRIMMED_ARMOR_TYPE = "neoforge:trimmed_armor";
    private static final Identifier SELECT_TYPE = Identifier.withDefaultNamespace("select");
    private static final Identifier TRIM_MATERIAL_PROPERTY = Identifier.withDefaultNamespace("trim_material");

    public static boolean isItemDefinition(Identifier location) {
        return location.getPath().startsWith("items/") && location.getPath().endsWith(".json");
    }

    public interface OverriddenResourceLookup {
        @Nullable
        IoSupplier<InputStream> find() throws IOException;
    }

    public static IoSupplier<InputStream> wrap(IoSupplier<InputStream> resource, OverriddenResourceLookup overridden) {
        return () -> {
            byte[] bytes;
            try (InputStream is = resource.get()) {
                bytes = is.readAllBytes();
            }
            byte[] merged = merge(bytes, overridden);
            return new ByteArrayInputStream(merged != null ? merged : bytes);
        };
    }

    @Nullable
    private static byte[] merge(byte[] bytes, OverriddenResourceLookup overridden) throws IOException {
        JsonObject definition = parse(new ByteArrayInputStream(bytes));
        JsonObject model = definition != null ? GsonHelper.getAsJsonObject(definition, "model", null) : null;
        if (model == null || !TRIMMED_ARMOR_TYPE.equals(GsonHelper.getAsString(model, "type", null))) {
            return null;
        }

        IoSupplier<InputStream> overriddenResource = overridden.find();
        if (overriddenResource == null) {
            return null;
        }
        JsonObject overriddenDefinition;
        try (InputStream is = overriddenResource.get()) {
            overriddenDefinition = parse(is);
        }

        JsonObject overriddenModel = overriddenDefinition != null ? GsonHelper.getAsJsonObject(overriddenDefinition, "model", null) : null;
        if (overriddenModel == null || !isTrimMaterialSelect(overriddenModel)) {
            return null;
        }

        JsonObject merged = overriddenModel.deepCopy();
        merged.add("fallback", model);
        definition.add("model", merged);

        return definition.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static boolean isTrimMaterialSelect(JsonObject model) {
        return SELECT_TYPE.equals(Identifier.tryParse(GsonHelper.getAsString(model, "type", "")))
            && TRIM_MATERIAL_PROPERTY.equals(Identifier.tryParse(GsonHelper.getAsString(model, "property", "")))
            && GsonHelper.isArrayNode(model, "cases");
    }

    @Nullable
    private static JsonObject parse(InputStream is) {
        try {
            JsonElement element = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private TrimmedArmorItemDefinitions() {}
}
