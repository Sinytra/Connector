package dev.su5ed.connector.fart;

import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.srgutils.IMappingFile;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SrgRemappingReferenceMapper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, String> SUBSTITUTIONS = Map.of(
        "Lnet/minecraft/class_1309;method_18405(Lnet/minecraft/class_2338;)Ljava/lang/Boolean;", "Lnet/minecraft/world/entity/LivingEntity;lambda$checkBedExists$9(Lnet/minecraft/core/BlockPos;)Ljava/lang/Boolean;",
        
        "Lnet/minecraft/class_1309;method_18404(Lnet/minecraft/class_2338;)V", "Lnet/minecraft/world/entity/LivingEntity;lambda$stopSleeping$11(Lnet/minecraft/core/BlockPos;)V", 
        
        "Lnet/minecraft/class_5619;method_32174(Lcom/google/common/collect/ImmutableMap$Builder;Lnet/minecraft/class_5617$class_5618;Lnet/minecraft/class_1299;Lnet/minecraft/class_5617;)V",
        "Lnet/minecraft/client/renderer/entity/EntityRenderers;lambda$createEntityRenderers$2(Lcom/google/common/collect/ImmutableMap$Builder;Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/client/renderer/entity/EntityRendererProvider;)V",
        
        "Lnet/minecraft/class_5619;method_32175(Lcom/google/common/collect/ImmutableMap$Builder;Lnet/minecraft/class_5617$class_5618;Ljava/lang/String;Lnet/minecraft/class_5617;)V",
        "Lnet/minecraft/client/renderer/entity/EntityRenderers;lambda$createPlayerRenderers$3(Lcom/google/common/collect/ImmutableMap$Builder;Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Ljava/lang/String;Lnet/minecraft/client/renderer/entity/EntityRendererProvider;)V",
        
        "Lnet/minecraft/class_702;method_3049(Lnet/minecraft/class_4587;Lnet/minecraft/class_4597$class_4598;Lnet/minecraft/class_765;Lnet/minecraft/class_4184;F)V",
        "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V"
    );

    private static final Pattern METHOD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-zA-Z0-9_]+|<[a-z0-9_]+>)(?<desc>\\((?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)*\\)(?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_;$]+))$");
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-zA-Z0-9_]+):(?<desc>[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)$");

    private final IMappingFile mappingFile;
    private final Map<String, IMappingFile.IMethod> methods;
    private final Map<String, IMappingFile.IField> fields;

    public SrgRemappingReferenceMapper(IMappingFile mappingFile) {
        this.mappingFile = mappingFile;
        this.methods = this.mappingFile.getClasses().stream()
            .flatMap(cls -> cls.getMethods().stream())
            .collect(Collectors.toMap(m -> m.getOriginal() + m.getDescriptor(), Function.identity(), (a, b) -> a));
        this.fields = this.mappingFile.getClasses().stream()
            .flatMap(cls -> cls.getFields().stream())
            .collect(Collectors.toMap(IMappingFile.INode::getOriginal, Function.identity(), (a, b) -> a));
    }

    public SimpleRefmap remap(SimpleRefmap refmap, Map<String, String> replacements) {
        Map<String, Map<String, String>> mappings = remapReferences(refmap.mappings);
        Map<String, Map<String, Map<String, String>>> data = new HashMap<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> entry : refmap.data.entrySet()) {
            String key = entry.getKey();
            data.put(replacements.getOrDefault(key, key), remapReferences(entry.getValue()));
        }
        return new SimpleRefmap(mappings, data);
    }

    private Map<String, Map<String, String>> remapReferences(Map<String, Map<String, String>> map) {
        Map<String, Map<String, String>> mapped = Maps.newHashMap();
        for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
            Map<String, String> refEntries = new HashMap<>();
            for (Map.Entry<String, String> refEntry : entry.getValue().entrySet()) {
                String reference = refEntry.getValue();
                refEntries.put(refEntry.getKey(), remapRef(reference));
            }
            mapped.put(entry.getKey(), refEntries);
        }
        return mapped;
    }

    private String remapRef(String reference) {
        String sub = SUBSTITUTIONS.get(reference);
        if (sub != null) {
            return sub;
        }
        Matcher methodMatcher = METHOD_REF_PATTERN.matcher(reference);
        if (methodMatcher.matches()) {
            return remapRefMapEntry(methodMatcher, "", (name, desc) -> this.methods.get(name + desc));
        }
        Matcher fieldMatcher = FIELD_REF_PATTERN.matcher(reference);
        if (fieldMatcher.matches()) {
            return remapRefMapEntry(fieldMatcher, ":", (name, desc) -> this.fields.get(name));
        }
        return this.mappingFile.remapClass(reference);
    }

    private <T extends IMappingFile.INode> String remapRefMapEntry(Matcher matcher, String separator, BiFunction<String, String, T> nodeFunction) {
        String owner = matcher.group("owner");
        String name = matcher.group("name");
        String desc = matcher.group("desc");
        T node = nodeFunction.apply(name, desc);

        String mappedOwner = owner != null ? this.mappingFile.remapDescriptor(owner) : "";
        return mappedOwner + node.getMapped() + separator + this.mappingFile.remapDescriptor(desc);
    }

    public static class SimpleRefmap {
        public final Map<String, Map<String, String>> mappings;
        public final Map<String, Map<String, Map<String, String>>> data;

        @SuppressWarnings("unused")
        public SimpleRefmap() {
            this.mappings = new HashMap<>();
            this.data = new HashMap<>();
        }

        public SimpleRefmap(Map<String, Map<String, String>> mappings, Map<String, Map<String, Map<String, String>>> data) {
            this.mappings = mappings;
            this.data = data;
        }

        public void write(Appendable writer) {
            new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this, writer);
        }
    }
}
