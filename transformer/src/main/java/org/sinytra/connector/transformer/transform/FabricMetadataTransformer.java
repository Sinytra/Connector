package org.sinytra.connector.transformer.transform;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Transformer;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

public class FabricMetadataTransformer implements Transformer {
    public static final FabricMetadataTransformer INSTANCE = new FabricMetadataTransformer();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NORMALIZER_SUFFIX = "_nojpms";
    private static final String FAPI_MODID = "fabric-api";
    private static final Pattern PATCH_VERSION = Pattern.compile("(\\d+\\.\\d+)\\.\\d+(?:\\.\\d+)*");

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create();
    private static final List<String> DEPENDENCY_FIELDS = List.of("depends", "recommends", "suggests", "breaks", "conflicts");
    private final Map<String, String> modIdAliases;
    // Never run entrypoints matching these values
    private static final Collection<String> DISABLED_MIXINEXTRAS_ENTRYPOINTS = Set.of(
        // Mixinextras initializes itself from within its own mixin config plugin.
        // Attempting to initialize it from an entrypoint at the GAME layer will result in an "attempted duplicate class definition" error
        // It is redundant and no longer required, as mentioned in https://gist.github.com/LlamaLad7/ec597b6d02d39b8a2e35559f9fcce42f#initialization
        "com.llamalad7.mixinextras.MixinExtrasBootstrap",
        "com.llamalad7.mixinextras.MixinExtrasBootstrap::init"
    );

    public FabricMetadataTransformer() {
        this(Map.of());
    }

    public FabricMetadataTransformer(Map<String, String> modIdAliases) {
        this.modIdAliases = Map.copyOf(modIdAliases);
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (entry.getName().equals(TransformerUtil.FABRIC_MOD_JSON)) {
            try {
                JsonElement raw = JsonParser.parseReader(new InputStreamReader(new ByteArrayInputStream(entry.getData())));
                JsonObject obj = raw.getAsJsonObject();

                processMetadata(obj, this.modIdAliases);

                byte[] data = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
                return ResourceEntry.create(entry.getName(), entry.getTime(), data);
            } catch (Exception e) {
                LOGGER.error("Error processing {}", entry.getName(), e);
            }
        }
        return entry;
    }

    public static String normalizeModId(String modId) {
        return modId.replace('-', '_');
    }

    private static void processMetadata(JsonObject json, Map<String, String> modIdAliases) {
        String modId = json.get("id").getAsString();
        String version = json.get("version").getAsString();

        // Adjust modid to accomodate JPMS requirements
        String jpmsModId = normalizeModId(modId);
        // If modid is amongst reserved keywords, add a suffix
        String normalModId = TransformerUtil.isJavaReservedKeyword(jpmsModId) ? jpmsModId + NORMALIZER_SUFFIX : jpmsModId;

        json.addProperty("id", normalModId);
        json.addProperty("version", version);

        JsonArray provides = Objects.requireNonNullElseGet(json.getAsJsonArray("provides"), JsonArray::new);
        if (!normalModId.equals(modId)) {
            provides.remove(new JsonPrimitive(normalModId));
            provides.add(modId);

            json.add("provides", provides);
        }

        // Remove disabled entrypoints
        JsonObject entrypoints = json.getAsJsonObject("entrypoints");
        if (entrypoints != null) {
            for (Map.Entry<String, JsonElement> entry : entrypoints.entrySet()) {
                JsonArray values = entry.getValue().getAsJsonArray();
                for (Iterator<JsonElement> iterator = values.iterator(); iterator.hasNext(); ) {
                    JsonElement value = iterator.next();
                    if (value.isJsonPrimitive()) {
                        String str = value.getAsString();
                        if (DISABLED_MIXINEXTRAS_ENTRYPOINTS.contains(str)) {
                            iterator.remove();
                        }
                    }
                }
            }
        }

        // Strip patch FAPI dep version
        JsonObject depends = json.getAsJsonObject("depends");
        if (depends != null && depends.has(FAPI_MODID)) {
            String ver = depends.getAsJsonPrimitive(FAPI_MODID).getAsString();
            String stripped = stripPatchVersion(ver);
            depends.addProperty(FAPI_MODID, stripped);
        }

        DEPENDENCY_FIELDS.stream()
            .map(json::getAsJsonObject)
            .filter(Objects::nonNull)
            .forEach(dependencies -> applyModIdAliases(dependencies, modIdAliases));

        JsonObject custom = Objects.requireNonNullElseGet(json.getAsJsonObject("custom"), JsonObject::new);
        custom.addProperty(TransformerUtil.METADATA_MARKER, true);
        custom.addProperty(TransformerUtil.LAUNCHPAD_MARKER, true);
        json.add("custom", custom);
    }

    private static void applyModIdAliases(JsonObject dependencies, Map<String, String> modIdAliases) {
        modIdAliases.forEach((alias, target) -> {
            JsonElement predicate = dependencies.remove(alias);
            if (predicate == null) {
                return;
            }

            JsonElement existing = dependencies.remove(target);
            dependencies.add(target, existing == null ? predicate : mergePredicates(existing, predicate));
        });
    }

    private static JsonArray mergePredicates(JsonElement first, JsonElement second) {
        JsonArray merged = new JsonArray();
        addPredicates(merged, first);
        addPredicates(merged, second);
        return merged;
    }

    private static void addPredicates(JsonArray output, JsonElement predicates) {
        if (predicates.isJsonArray()) {
            predicates.getAsJsonArray().forEach(output::add);
        } else {
            output.add(predicates);
        }
    }

    private static String stripPatchVersion(String predicate) {
        int plus = predicate.indexOf('+');
        if (plus < 0) {
            return PATCH_VERSION.matcher(predicate).replaceAll("$1");
        }
        String head = predicate.substring(0, plus);
        String tail = predicate.substring(plus);
        return PATCH_VERSION.matcher(head).replaceAll("$1") + tail;
    }
}
