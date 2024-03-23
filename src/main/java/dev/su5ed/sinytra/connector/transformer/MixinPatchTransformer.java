package dev.su5ed.sinytra.connector.transformer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.transformer.patch.EnvironmentStripperTransformer;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.forgespi.locating.IModFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.LVTOffsets;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.MixinClassGenerator;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.fixes.FieldTypePatchTransformer;
import org.sinytra.adapter.patch.fixes.FieldTypeUsageTransformer;
import org.sinytra.adapter.patch.transformer.dynamic.DynamicAnonymousShadowFieldTypePatch;
import org.sinytra.adapter.patch.transformer.dynamic.DynamicInheritedInjectionPointPatch;
import org.sinytra.adapter.patch.transformer.dynamic.DynamicInjectorOrdinalPatch;
import org.sinytra.adapter.patch.transformer.dynamic.DynamicLVTPatch;
import org.sinytra.adapter.patch.transformer.dynamic.DynamicModifyVarAtReturnPatch;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowConsumer;
import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;

public class MixinPatchTransformer implements Transformer {
    private static final List<Patch> PRIORITY_PATCHES = Lists.newArrayList(
        Patch.builder()
            .targetClass("net/minecraft/world/item/ItemStack")
            .targetMethod("m_41661_")
            .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;m_41720_()Lnet/minecraft/world/item/Item;")
            .modifyTarget("connector_useOn")
            .modifyInjectionPoint("RETURN", "", true)
            .build()
    );
    private static final List<Patch> PATCHES = MixinPatches.getPatches();
    // Applied to non-mixins
    private static final List<ClassTransform> CLASS_TRANSFORMS = List.of(
        new EnvironmentStripperTransformer(),
        new FieldTypeUsageTransformer()
    );
    // Applied to mixins only
    private static final Patch CLASS_PATCH = Patch.builder()
        .transform(CLASS_TRANSFORMS)
        .build();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean completedSetup = false;

    private final PatchEnvironment environment;
    private final List<? extends Patch> patches;

    public MixinPatchTransformer(LVTOffsets lvtOffsets, PatchEnvironment environment, List<? extends Patch> adapterPatches) {
        this.environment = environment;
        this.patches = ImmutableList.<Patch>builder()
            .addAll(PRIORITY_PATCHES)
            .addAll(adapterPatches)
            .addAll(PATCHES)
            .add(
                Patch.builder()
                    .transform(new DynamicInjectorOrdinalPatch())
                    .transform(new DynamicLVTPatch(() -> lvtOffsets))
                    .transform(new DynamicAnonymousShadowFieldTypePatch())
                    .transform(new DynamicModifyVarAtReturnPatch())
                    .transform(new DynamicInheritedInjectionPointPatch())
                    .build(),
                Patch.interfaceBuilder()
                    .transform(new FieldTypePatchTransformer())
                    .build()
            )
            .build();
    }

    public void finalize(Path zipRoot, Collection<String> configs, Map<String, SrgRemappingReferenceMapper.SimpleRefmap> refmapFiles, Set<String> dirtyRefmaps) throws IOException {
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
                                        SrgRemappingReferenceMapper.SimpleRefmap refmap = refmapFiles.get(refmapName);
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
                            }
                            else {
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

    public static void completeSetup(Iterable<IModFile> mods) {
        if (completedSetup) {
            return;
        }
        // Injection point data extracted from coremods/method_redirector.js
        String[] targetClasses = StreamSupport.stream(mods.spliterator(), false)
            .filter(m -> m.getModFileInfo() != null && !m.getModInfos().isEmpty() && m.getModInfos().get(0).getModId().equals(ConnectorUtil.FORGE_MODID))
            .map(m -> m.findResource("coremods/finalize_spawn_targets.json"))
            .filter(Files::exists)
            .map(rethrowFunction(path -> {
                try (Reader reader = Files.newBufferedReader(path)) {
                    return JsonParser.parseReader(reader);
                }
            }))
            .filter(JsonElement::isJsonArray)
            .flatMap(json -> json.getAsJsonArray().asList().stream()
                .map(JsonElement::getAsString))
            .toArray(String[]::new);
        if (targetClasses.length > 0) {
            PATCHES.add(Patch.builder()
                .targetClass(targetClasses)
                .targetInjectionPoint("m_6518_(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;")
                .modifyInjectionPoint("Lnet/minecraftforge/event/ForgeEventFactory;onFinalizeSpawn(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/MobSpawnType;Lnet/minecraft/world/entity/SpawnGroupData;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/entity/SpawnGroupData;")
                .build());
        }
        completedSetup = true;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        Patch.Result patchResult = Patch.Result.PASS;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        // Some mods generate their mixin configs at runtime, therefore we must scan all classes
        // regardless of whether they're listed in present config files (see Andromeda)
        if (isMixinClass(node)) {
            patchResult = patchResult.or(CLASS_PATCH.apply(node, this.environment));

            for (Patch patch : this.patches) {
                patchResult = patchResult.or(patch.apply(node, this.environment));
            }
        }
        else {
            for (ClassTransform transform : CLASS_TRANSFORMS) {
                patchResult = patchResult.or(transform.apply(node, null, PatchContext.create(node, List.of(), this.environment)));
            }
        }

        // TODO if a mixin method is extracted, roll back the status from compute frames to apply,
        // Alternatively, change the order of patches so that extractmixin comes first
        if (patchResult != Patch.Result.PASS) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | (patchResult == Patch.Result.COMPUTE_FRAMES ? ClassWriter.COMPUTE_FRAMES : 0));
            node.accept(writer);
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
        return entry;
    }

    @Override
    public Collection<? extends Entry> getExtras() {
        List<Entry> entries = new ArrayList<>();
        Patch patch = Patch.builder()
            .transform(new DynamicInheritedInjectionPointPatch())
            .build();
        this.environment.classGenerator().getGeneratedMixinClasses().forEach((name, cls) -> {
            patch.apply(cls.node(), this.environment);

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            cls.node().accept(writer);
            byte[] bytes = writer.toByteArray();
            entries.add(ClassEntry.create(name + ".class", ConnectorUtil.ZIP_TIME, bytes));
        });
        return entries;
    }

    private static boolean isMixinClass(ClassNode classNode) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MixinConstants.MIXIN)) {
                    return true;
                }
            }
        }
        return false;
    }
}
