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

    private static final Pattern METHOD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-z0-9_]+)(?<desc>\\((?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)*\\)(?:[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_;$]+))$");
    private static final Pattern FIELD_REF_PATTERN = Pattern.compile("^(?<owner>L[a-zA-Z0-9/_$]+;)?(?<name>[a-z0-9_]+):(?<desc>[VZCBSIFJD]|\\[?L[a-zA-Z0-9/_$]+;)$");

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
        Matcher methodMatcher = METHOD_REF_PATTERN.matcher(reference);
        if (methodMatcher.matches()) {
            return remapRefMapEntry(methodMatcher, "", (name, desc) -> this.methods.get(name + desc), IMappingFile.IMethod::getMappedDescriptor);
        }
        Matcher fieldMatcher = FIELD_REF_PATTERN.matcher(reference);
        if (fieldMatcher.matches()) {
            return remapRefMapEntry(fieldMatcher, ":", (name, desc) -> this.fields.get(name), IMappingFile.IField::getMappedDescriptor);
        }
        return reference;
    }

    private <T extends IMappingFile.INode> String remapRefMapEntry(Matcher matcher, String separator, BiFunction<String, String, T> nodeFunction, Function<T, String> descGetter) {
        String owner = matcher.group("owner");
        String name = matcher.group("name");
        String desc = matcher.group("desc");
        T node = nodeFunction.apply(name, desc);

        String mappedOwner = owner != null ? this.mappingFile.remapDescriptor(owner) : "";
        return mappedOwner + node.getMapped() + separator + descGetter.apply(node);
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
