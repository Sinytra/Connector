package org.sinytra.connector.transformer.transform;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.env.ctx.MixinClassGenerator;
import org.sinytra.adapter.env.ctx.PatchContext;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.DynamicPatches;
import org.sinytra.adapter.patch.Patcher;
import org.sinytra.adapter.transform.ClassTransformer;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.patch.EnvironmentStripperTransformer;
import org.sinytra.connector.transformer.patch.SimpleRefmap;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.sinytra.connector.transformer.transform.TransformerUtil.rethrowConsumer;

public class MixinPatchTransformer implements Transformer {
    private static final List<MethodPatch> PRIORITY_PATCHES = MixinPatches.getPriorityPatches();
    private static final List<MethodPatch> PATCHES = MixinPatches.getPatches();
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PatchEnvironment environment;

    // Applied to all classes including non-mixins
    private final List<ClassTransformer> classTransforms;
    // Applied to mixins only
    private final Patcher patcher;

    public MixinPatchTransformer(TransformerEnvironment runtimeEnvironment, PatchEnvironment environment, List<? extends MethodPatch> extraPatches) {
        this.environment = environment;

        this.classTransforms = List.of(
            new EnvironmentStripperTransformer(runtimeEnvironment.getEnvType()),
            new FieldTypeUsageTransformer()
        );

        List<MethodPatch> allPatches = Stream.of(PRIORITY_PATCHES, extraPatches, PATCHES)
            .<MethodPatch>flatMap(Collection::stream)
            .toList();
        this.patcher = Patcher.builder(this.environment)
            .classTransformers(DynamicPatches.CLASS_PATCHES)
            .methodTransformers(DynamicPatches.methodTransformers(allPatches))
            .build();
    }

    public void finalize(Path zipRoot, Collection<String> configs, Map<String, SimpleRefmap> refmapFiles, Set<String> dirtyRefmaps) throws IOException {
        Map<String, MixinClassGenerator.GeneratedClass> generatedMixinClasses = this.environment.classGenerator().getGeneratedMixinClasses();
        if (!generatedMixinClasses.isEmpty()) {
            for (String config : configs) {
                Path entry = zipRoot.resolve(config);
                if (Files.exists(entry)) {
                    try (Reader reader = Files.newBufferedReader(entry)) {
                        JsonElement element = JsonParser.parseReader(reader);
                        JsonObject json = element.getAsJsonObject();
                        if (json.has("package")) {
                            String pkg = json.get("package").getAsString();
                            Map<String, MixinClassGenerator.GeneratedClass> mixins = getMixinsInPackage(pkg, generatedMixinClasses);
                            if (!mixins.isEmpty()) {
                                JsonArray jsonMixins = json.has("mixins") ? json.get("mixins").getAsJsonArray() : new JsonArray();
                                LOGGER.info("Adding {} mixins to config {}", mixins.size(), config);
                                mixins.keySet().forEach(jsonMixins::add);
                                json.add("mixins", jsonMixins);

                                String output = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(json);
                                Files.writeString(entry, output, StandardCharsets.UTF_8);

                                // Update refmap
                                if (json.has("refmap")) {
                                    String refmapName = json.get("refmap").getAsString();
                                    if (dirtyRefmaps.contains(refmapName)) {
                                        SimpleRefmap refmap = refmapFiles.get(refmapName);
                                        Path path = zipRoot.resolve(refmapName);
                                        if (Files.exists(path)) {
                                            String refmapString = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(refmap);
                                            Files.writeString(path, refmapString, StandardCharsets.UTF_8);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        // Strip unused service providers
        Path services = zipRoot.resolve("META-INF/services");
        if (Files.exists(services)) {
            try (Stream<Path> stream = Files.walk(services)) {
                stream
                    .filter(Files::isRegularFile)
                    .forEach(rethrowConsumer(path -> {
                        String serviceName = path.getFileName().toString();
                        List<String> providers = Files.readAllLines(path);
                        List<String> existingProviders = providers.stream()
                            .filter(cls -> Files.exists(zipRoot.resolve(cls.replace('.', '/') + ".class")))
                            .toList();
                        int diff = providers.size() - existingProviders.size();
                        if (diff > 0) {
                            LOGGER.debug("Removing {} nonexistent service providers for service {}", diff, serviceName);
                            if (existingProviders.isEmpty()) {
                                Files.delete(path);
                            } else {
                                String newText = String.join("\n", existingProviders);
                                Files.writeString(path, newText, StandardCharsets.UTF_8);
                            }
                        }
                    }));
            }
        }
    }

    private Map<String, MixinClassGenerator.GeneratedClass> getMixinsInPackage(String mixinPackage, Map<String, MixinClassGenerator.GeneratedClass> generatedMixinClasses) {
        Map<String, MixinClassGenerator.GeneratedClass> classes = new HashMap<>();
        for (Map.Entry<String, MixinClassGenerator.GeneratedClass> entry : generatedMixinClasses.entrySet()) {
            String name = entry.getKey();
            String className = name.replace('/', '.');
            if (className.startsWith(mixinPackage)) {
                String specificPart = className.substring(mixinPackage.length() + 1);
                classes.put(specificPart, entry.getValue());
                generatedMixinClasses.remove(name);
            }
        }
        return classes;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        PatchResult patchResult = PatchResult.PASS;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        // Some mods generate their mixin configs at runtime, therefore we must scan all classes
        // regardless of whether they're listed in present config files (see Andromeda)
        if (isMixinClass(node)) {
            PatchResult txResult = this.patcher.process(node);
            patchResult = patchResult.or(txResult);
        } else {
            for (ClassTransformer transform : this.classTransforms) {
                patchResult = patchResult.or(transform.apply(node, null, PatchContext.create(node, List.of(), this.environment)));
            }
        }

        // TODO if a mixin method is extracted, roll back the status from compute frames to apply,
        // Alternatively, change the order of patches so that extractmixin comes first
        if (patchResult != PatchResult.PASS) {
            ClassWriter writer = new InheritingClassWriter(ClassWriter.COMPUTE_MAXS | (patchResult == PatchResult.COMPUTE_FRAMES ? ClassWriter.COMPUTE_FRAMES : 0), this.environment);
            node.accept(writer);
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        List<Entry> entries = new ArrayList<>();
        this.environment.classGenerator().getGeneratedMixinClasses().forEach((name, cls) -> {
            this.patcher.process(cls.node());

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cls.node().accept(writer);
            byte[] bytes = writer.toByteArray();
            entries.add(ClassEntry.create(name + ".class", TransformerUtil.ZIP_TIME, bytes));
        });
        return entries;
    }

    private static boolean isMixinClass(ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MixinAnnotations.MIXIN)) {
                    return true;
                }
            }
        }
        return false;
    }
}
