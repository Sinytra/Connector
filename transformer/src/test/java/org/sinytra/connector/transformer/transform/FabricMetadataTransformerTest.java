package org.sinytra.connector.transformer.transform;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.art.api.Transformer.ResourceEntry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FabricMetadataTransformerTest {
    @Test
    void rewritesFabricDependencyWhenNativeAliasIsActive() {
        JsonObject transformed = transform("""
            {
              "schemaVersion": 1,
              "id": "example",
              "version": "1.0.0",
              "depends": {
                "cloth-config2": ">=17"
              }
            }
            """, Map.of("cloth-config2", "cloth_config"));

        JsonObject dependencies = transformed.getAsJsonObject("depends");
        assertEquals(">=17", dependencies.get("cloth_config").getAsString());
        assertFalse(dependencies.has("cloth-config2"));
    }

    @Test
    void preservesBothPredicatesWhenAliasAndNativeIdsAreDeclared() {
        JsonObject transformed = transform("""
            {
              "schemaVersion": 1,
              "id": "example",
              "version": "1.0.0",
              "depends": {
                "cloth-config2": ">=17",
                "cloth_config": "<20"
              }
            }
            """, Map.of("cloth-config2", "cloth_config"));

        JsonArray predicates = transformed.getAsJsonObject("depends").getAsJsonArray("cloth_config");
        assertEquals(2, predicates.size());
        assertEquals("<20", predicates.get(0).getAsString());
        assertEquals(">=17", predicates.get(1).getAsString());
    }

    @Test
    void leavesDependencyUntouchedWithoutAnActiveNativeAlias() {
        JsonObject transformed = transform("""
            {
              "schemaVersion": 1,
              "id": "example",
              "version": "1.0.0",
              "depends": {
                "cloth-config2": "*"
              }
            }
            """, Map.of());

        JsonObject dependencies = transformed.getAsJsonObject("depends");
        assertEquals("*", dependencies.get("cloth-config2").getAsString());
        assertFalse(dependencies.has("cloth_config"));
    }

    private static JsonObject transform(String json, Map<String, String> aliases) {
        FabricMetadataTransformer transformer = new FabricMetadataTransformer(aliases);
        ResourceEntry input = ResourceEntry.create(
            TransformerUtil.FABRIC_MOD_JSON,
            0,
            json.getBytes(StandardCharsets.UTF_8)
        );
        ResourceEntry output = transformer.process(input);
        return JsonParser.parseString(new String(output.getData(), StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
