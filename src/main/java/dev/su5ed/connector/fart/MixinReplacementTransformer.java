package dev.su5ed.connector.fart;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowFunction;

public class MixinReplacementTransformer implements Transformer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MIXIN_ANN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String REDIRECT_ANN = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String ACCESSOR_ANN = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

    private static final List<Replacement> REPLACEMENTS = List.of(
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/LivingEntityMixin",
            "setOccupiedState",
            "dev.su5ed.connector.replacement.EntityApiReplacements"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/PlayerEntityMixin",
            "redirectDaySleepCheck"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/ServerPlayerEntityMixin",
            "redirectDaySleepCheck"
        ),
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/entity/event/ServerPlayerEntityMixin",
            "onTrySleepDirectionCheck",
            "dev.su5ed.connector.replacement.EntityApiReplacements"
        ),
        Replacement.redirectMixinTarget(
            "net/fabricmc/fabric/mixin/dimension/EntityMixin",
            "stopEndSpecificBehavior",
            List.of(
                "lambda$changeDimension$12(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/PortalInfo;Ljava/lang/Boolean;)Lnet/minecraft/world/entity/Entity;",
                "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;",
                "lambda$changeDimension$8(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/portal/PortalInfo;Ljava/lang/Boolean;)Lnet/minecraft/world/entity/Entity"
            )
        ),
        // Redundant fabric fix, https://github.com/MinecraftForge/MinecraftForge/pull/7527
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/dimension/RegistryCodecsMixin",
            "modifyCodecLocalVariable"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/content/registry/AbstractFurnaceBlockEntityMixin",
            "canUseAsFuelRedirect"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/content/registry/AbstractFurnaceBlockEntityMixin",
            "getFuelTimeRedirect"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/registry/sync/StructuresToConfiguredStructuresFixMixin",
            "findUpdatedStructureType"
        ),
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/client/rendering/ScreenMixin",
            "injectRenderTooltipLambda",
            "dev/su5ed/connector/replacement/RenderingApiReplacements"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramMixin",
            "modifyProgramId"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/event/interaction/ServerPlayerInteractionManagerMixin",
            "breakBlock"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/event/interaction/ServerPlayerInteractionManagerMixin",
            "onBlockBroken"
        ),
        // TODO Server mixin, too
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/event/lifecycle/client/WorldChunkMixin",
            "onRemoveBlockEntity(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;",
            "dev/su5ed/connector/replacement/LifecycleApiReplacements",
            "onRemoveBlockEntity"
        ),
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/networking/client/ClientLoginNetworkHandlerMixin",
            "handleQueryRequest",
            "dev/su5ed/connector/replacement/NetworkingApiReplacements"
        ),
        Replacement.patchCapturedLVT(
            "net/fabricmc/fabric/mixin/event/interaction/client/MinecraftClientMixin",
            "injectUseEntityCallback",
            "(Lorg/spongepowered/reloc/asm/mixin/injection/callback/CallbackInfo;[Lnet/minecraft/world/InteractionHand;IILnet/minecraft/world/InteractionHand;Lnet/minecraftforge/client/event/InputEvent$InteractionKeyMappingTriggered;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/EntityHitResult;Lnet/minecraft/world/entity/Entity;)V"
        ),
        Replacement.renameField(
            "net/minecraft/server/network/ServerPlayNetworkHandler$1",
            "val$target",
            "val$entity"
        ),
        Replacement.removeMixinMethod(
            "net/fabricmc/fabric/mixin/item/ItemStackMixin",
            "hookGetAttributeModifiers"
        ),
        Replacement.replaceMixinMethod(
            "net/fabricmc/fabric/mixin/item/ItemStackMixin",
            "hookIsSuitableFor",
            "dev/su5ed/connector/replacement/ItemApiReplacements"
        ),
        Replacement.shadowFieldTypeReplacement(
            "net/minecraft/world/level/storage/loot/LootTable",
            "pools", // TODO Use SRG names
            "[Lnet/minecraft/world/level/storage/loot/LootPool;",
            "Ljava/util/List;",
            patch -> {
                InsnList list = patch.loadShadowValue();
                list.add(new InsnNode(Opcodes.ICONST_0));
                list.add(new TypeInsnNode(Opcodes.ANEWARRAY, "net/minecraft/world/level/storage/loot/LootPool"));
                list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true));
                list.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Lnet/minecraft/world/level/storage/loot/LootPool;"));
                return list;
            }
        ),
        Replacement.shadowFieldTypeReplacement(
            "net/minecraft/client/particle/ParticleEngine",
            "factories",
            "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;",
            "Ljava/util/Map;",
            patch -> {
                InsnList list = new InsnList();
                list.add(new TypeInsnNode(Opcodes.NEW, "dev/su5ed/connector/mod/DelegatingInt2ObjectMap"));
                list.add(new InsnNode(Opcodes.DUP));
                list.add(patch.loadShadowValue());
                list.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries", "PARTICLE_TYPE", "Lnet/minecraft/core/Registry;"));
                list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "dev/su5ed/connector/mod/DelegatingInt2ObjectMap", "<init>", "(Ljava/util/Map;Lnet/minecraft/core/Registry;)V"));
                return list;
            }
        ),
        Replacement.shadowFieldTypeReplacement(
            "net/minecraft/client/color/block/BlockColors",
            "blockColors",
            "Lnet/minecraft/core/IdMapper;",
            "Ljava/util/Map;",
            patch -> {
                InsnList list = new InsnList();
                list.add(new TypeInsnNode(Opcodes.NEW, "dev/su5ed/connector/mod/DelegatingIdMapper"));
                list.add(new InsnNode(Opcodes.DUP));
                list.add(patch.loadShadowValue());
                list.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries", "BLOCK", "Lnet/minecraft/core/Registry;"));
                list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "dev/su5ed/connector/mod/DelegatingIdMapper", "<init>", "(Ljava/util/Map;Lnet/minecraft/core/Registry;)V"));
                return list;
            }
        ),
        Replacement.shadowFieldTypeReplacement(
            "net/minecraft/client/color/item/ItemColors",
            "itemColors",
            "Lnet/minecraft/core/IdMapper;",
            "Ljava/util/Map;",
            patch -> {
                InsnList list = new InsnList();
                list.add(new TypeInsnNode(Opcodes.NEW, "dev/su5ed/connector/mod/DelegatingIdMapper"));
                list.add(new InsnNode(Opcodes.DUP));
                list.add(patch.loadShadowValue());
                list.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/registries/BuiltInRegistries", "ITEM", "Lnet/minecraft/core/Registry;"));
                list.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "dev/su5ed/connector/mod/DelegatingIdMapper", "<init>", "(Ljava/util/Map;Lnet/minecraft/core/Registry;)V"));
                return list;
            }
        )
    );
    private static final List<String> REMOVALS = List.of(
        // TODO All of these reply on ID maps that have been replaced by forge, find a way to proxy them
        "net/fabricmc/fabric/mixin/registry/sync/client/BlockColorsMixin",
        "net/fabricmc/fabric/mixin/registry/sync/client/ItemColorsMixin",
        "net/fabricmc/fabric/mixin/registry/sync/client/ParticleManagerMixin",
        
        "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramMixin",
        "net/fabricmc/fabric/mixin/client/rendering/shader/ShaderProgramImportProcessorMixin",
        "net/fabricmc/fabric/mixin/item/AbstractFurnaceBlockEntityMixin",
        "net/fabricmc/fabric/mixin/item/BrewingStandBlockEntityMixin",
        "net/fabricmc/fabric/mixin/item/RecipeMixin",
        "net/fabricmc/fabric/mixin/itemgroup/ItemGroupsMixin",
        "net/fabricmc/fabric/mixin/itemgroup/client/CreativeInventoryScreenMixin"
    );

    private final Set<String> configs;
    private final Set<String> mixins;

    public MixinReplacementTransformer(Set<String> configs, Set<String> mixins) {
        this.configs = configs;
        this.mixins = mixins;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        if (this.mixins.contains(className)) {
            ClassReader reader = new ClassReader(entry.getData());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<Replacement> replacements = REPLACEMENTS.stream()
                .filter(r -> r.handles(node))
                .toList();
            if (applyReplacements(node, replacements)) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                node.accept(writer);
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
            }
        }
        return entry;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (this.configs.contains(entry.getName())) {
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(entry.getData()))) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                String pkg = json.get("package").getAsString();
                String pkgPath = pkg.replace('.', '/') + '/';
                Set.of("mixins", "client", "server").stream()
                    .filter(json::has)
                    .forEach(str -> {
                        JsonArray array = json.getAsJsonArray(str);
                        for (Iterator<JsonElement> it = array.iterator(); it.hasNext(); ) {
                            JsonElement element = it.next();
                            if (REMOVALS.contains(pkgPath + element.getAsString().replace('.', '/'))) {
                                it.remove();
                            }
                        }
                    });
                try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream()) {
                    try (Writer writer = new OutputStreamWriter(byteStream)) {
                        new Gson().toJson(json, writer);
                        writer.flush();
                    }
                    byte[] data = byteStream.toByteArray();
                    return ResourceEntry.create(entry.getName(), entry.getTime(), data);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return entry;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T, U> U findAnnotationValue(List<Object> values, String key, Function<T, U> processor) {
        for (int i = 0; i < values.size(); i += 2) {
            String atKey = (String) values.get(i);
            if (atKey.equals(key)) {
                Object atValue = values.get(i + 1);
                return processor.apply((T) atValue);
            }
        }
        return null;
    }

    @Nullable
    private static void setAnnotationValue(List<Object> values, String key, Object value) {
        for (int i = 0; i < values.size(); i += 2) {
            String atKey = (String) values.get(i);
            if (atKey.equals(key)) {
                values.set(i + 1, value);
            }
        }
    }

    private static boolean applyReplacements(ClassNode node, List<Replacement> replacements) {
        if (!replacements.isEmpty()) {
            for (Replacement replacement : replacements) {
                replacement.apply(node);
            }
            return true;
        }
        return false;
    }

    private static boolean targetsType(ClassNode classNode, String type) {
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (annotation.desc.equals(MIXIN_ANN)) {
                    Boolean value = MixinReplacementTransformer.<List<Type>, Boolean>findAnnotationValue(annotation.values, "value", val -> {
                        for (Type targetType : val) {
                            if (type.equals(targetType.getInternalName())) {
                                return true;
                            }
                        }
                        return false;
                    });
                    if (value == null || !value) {
                        value = MixinReplacementTransformer.<List<String>, Boolean>findAnnotationValue(annotation.values, "targets", val -> {
                            for (String targetType : val) {
                                if (type.equals(targetType)) {
                                    return true;
                                }
                            }
                            return false;
                        });
                    }
                    return value != null && value;
                }
            }
        }
        return false;
    }

    interface Replacement {
        boolean handles(ClassNode classNode);

        void apply(ClassNode classNode);

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass) {
            return replaceMixinMethod(mixinClass, mixinMethod, replacementClass, mixinMethod);
        }

        static Replacement replaceMixinMethod(String mixinClass, String mixinMethod, String replacementClass, String replacementMethod) {
            return new ReplaceMixin(mixinClass, mixinMethod, replacementClass, replacementMethod);
        }

        static Replacement removeMixinMethod(String mixinClass, String mixinMethod) {
            return new RemoveMixin(mixinClass, mixinMethod);
        }

        static Replacement redirectMixinTarget(String mixinClass, String mixinMethod, List<String> replacementMethods) {
            return new RedirectMixin(mixinClass, mixinMethod, replacementMethods);
        }

        static Replacement shadowFieldTypeReplacement(String ownerClass, String name, String type, String replacementType, Function<ShadowFieldTypeReplacement.PatcherCallback, InsnList> patcher) {
            return new ShadowFieldTypeReplacement(ownerClass, name, type, replacementType, patcher);
        }

        static Replacement patchCapturedLVT(String mixinClass, String mixinMethod, String newDesc) {
            return new LVTCaptureReplacement(mixinClass, mixinMethod, newDesc);
        }

        static Replacement renameField(String ownerClass, String fieldName, String newName) {
            return new RenameField(ownerClass, fieldName, newName);
        }
    }

    public record RenameField(String ownerClass, String fieldName, String newName) implements Replacement {
        @Override
        public boolean handles(ClassNode classNode) {
            return targetsType(classNode, this.ownerClass);
        }

        @Override
        public void apply(ClassNode classNode) {
            LOGGER.info("Renaming field {}.{} to {}", classNode.name, this.fieldName, this.newName);

            for (FieldNode field : classNode.fields) {
                if (field.name.equals(this.fieldName)) {
                    field.name = this.newName;
                    break;
                }
            }
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof FieldInsnNode fieldInsnNode && fieldInsnNode.owner.equals(classNode.name) && fieldInsnNode.name.equals(this.fieldName)) {
                        fieldInsnNode.name = this.newName;
                    }
                }
            }
        }
    }

    public record LVTCaptureReplacement(String mixinClass, String mixinMethod, String newDesc) implements MethodReplacement {
        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode node, MethodNode methodNode) {
            LOGGER.info("Changing descriptor of method {}.{}{} to {}", node.name, methodNode.name, methodNode.desc, this.newDesc);
            Type[] parameterTypes = Type.getArgumentTypes(methodNode.desc);
            Type[] newParameterTypes = Type.getArgumentTypes(this.newDesc);
            Int2ObjectMap<Type> insertionIndices = new Int2ObjectOpenHashMap<>();
            int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

            int i = 0;
            for (int j = 0; j < newParameterTypes.length && i < methodNode.parameters.size(); j++) {
                Type type = newParameterTypes[j];
                if (!parameterTypes[i].equals(type)) {
                    insertionIndices.put(j + offset, type);
                    continue;
                }
                i++;
            }
            if (i != methodNode.parameters.size()) {
                throw new RuntimeException("Unable to patch LVT capture, incompatible parameters");
            }
            insertionIndices.forEach((index, type) -> {
                ParameterNode newParameter = new ParameterNode(null, 0);
                methodNode.parameters.add(index, newParameter);
                for (LocalVariableNode localVariable : methodNode.localVariables) {
                    if (localVariable.index >= index) {
                        localVariable.index++;
                    }
                }
                // TODO Invisible annotations
                if (methodNode.visibleLocalVariableAnnotations != null) {
                    for (LocalVariableAnnotationNode localVariableAnnotation : methodNode.visibleLocalVariableAnnotations) {
                        List<Integer> annotationIndices = localVariableAnnotation.index;
                        for (int j = 0; j < annotationIndices.size(); j++) {
                            Integer annoIndex = annotationIndices.get(j);
                            if (annoIndex >= index) {
                                annotationIndices.set(j, annoIndex + 1);
                            }
                        }
                    }
                }
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof VarInsnNode varInsnNode && varInsnNode.var >= index) {
                        varInsnNode.var++;
                    }
                }
            });
            methodNode.desc = this.newDesc;
            return MethodAction.keep();
        }
    }

    public interface MethodReplacement extends Replacement {
        String mixinClass();

        String mixinMethod();

        Pair<MethodAction, MethodNode> apply(ClassNode node, MethodNode methodNode);

        @Override
        default boolean handles(ClassNode classNode) {
            return classNode.name.equals(mixinClass());
        }

        @Override
        default void apply(ClassNode node) {
            String mixinMethod = mixinMethod();
            int descIndex = mixinMethod().indexOf('(');
            String methodName = descIndex == -1 ? mixinMethod : mixinMethod.substring(0, descIndex);
            String methodDesc = descIndex == -1 ? null : mixinMethod.substring(descIndex);

            List<MethodNode> toRemove = new ArrayList<>();
            for (int i = 0; i < node.methods.size(); i++) {
                MethodNode method = node.methods.get(i);
                if (method.name.equals(methodName) && (methodDesc == null || method.desc.equals(methodDesc))) {
                    Pair<MethodAction, MethodNode> pair = apply(node, method);
                    MethodAction action = pair.getFirst();

                    if (action == MethodAction.REMOVE) {
                        toRemove.add(method);
                    } else if (action == MethodAction.REPLACE) {
                        node.methods.set(i, pair.getSecond());
                    }
                }
            }
            node.methods.removeAll(toRemove);
        }

        enum MethodAction {
            KEEP,
            REPLACE,
            REMOVE;

            static Pair<MethodAction, MethodNode> keep() {
                return Pair.of(KEEP, null);
            }

            static Pair<MethodAction, MethodNode> replace(MethodNode replacement) {
                return Pair.of(REPLACE, replacement);
            }

            static Pair<MethodAction, MethodNode> remove() {
                return Pair.of(REMOVE, null);
            }
        }
    }

    record ReplaceMixin(String mixinClass, String mixinMethod, String replacementClass, String replacementMethod) implements MethodReplacement {
        private static final Map<String, ClassNode> CLASS_CACHE = new ConcurrentHashMap<>();

        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info("Replacing mixin {}.{}", classNode.name, methodNode.name);
            ClassNode replNode = CLASS_CACHE.computeIfAbsent(this.replacementClass, rethrowFunction(c -> {
                ClassReader replReader = new ClassReader(this.replacementClass);
                ClassNode rNode = new ClassNode();
                replReader.accept(rNode, 0);
                return rNode;
            }));

            MethodNode replMethod = replNode.methods.stream().filter(m -> m.name.equals(this.replacementMethod)).findFirst().orElseThrow();
            return MethodAction.replace(replMethod);
        }
    }

    record RemoveMixin(String mixinClass, String mixinMethod) implements MethodReplacement {
        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode classNode, MethodNode methodNode) {
            LOGGER.info("Removing mixin {}.{}", classNode.name, methodNode.name);
            return MethodAction.remove();
        }
    }

    record RedirectMixin(String mixinClass, String mixinMethod, List<String> replacementMethods) implements MethodReplacement {
        @Override
        public Pair<MethodAction, MethodNode> apply(ClassNode node, MethodNode method) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (annotation.desc.equals(REDIRECT_ANN)) {
                        List<String> targetMethods = MixinReplacementTransformer.findAnnotationValue(annotation.values, "method", Function.identity());

                        if (targetMethods.size() == 1) {
                            LOGGER.info("Redirecting mixin {}.{} to {}", node.name, method.name, this.replacementMethods);
                            targetMethods.clear();
                            targetMethods.addAll(this.replacementMethods);
                        }
                    }
                }
            }
            return MethodAction.keep();
        }
    }

    public record ShadowFieldTypeReplacement(String ownerClass, String name, String type, String replacementType, Function<PatcherCallback, InsnList> patcher) implements Replacement {
        interface PatcherCallback {
            InsnList loadShadowValue();
        }

        @Override
        public boolean handles(ClassNode classNode) {
            return targetsType(classNode, this.ownerClass);
        }

        @Override
        public void apply(ClassNode classNode) {
            for (FieldNode field : classNode.fields) {
                if (field.name.equals(this.name)) {
                    if (!field.desc.equals(this.type)) {
                        throw new RuntimeException("Invalid replacement origin descriptor, expected " + this.type + ", found " + field.desc);
                    }
                    LOGGER.info("Changing type of field {}.{} to {}", classNode.name, field.name, this.replacementType);
                    field.desc = this.replacementType;
                }
            }

            List<MethodNode> methodNodes = List.copyOf(classNode.methods);
            for (MethodNode method : methodNodes) {
                if (method.visibleAnnotations != null) {
                    for (AnnotationNode annotation : method.visibleAnnotations) {
                        if (annotation.desc.equals(ACCESSOR_ANN)) {
                            String value = findAnnotationValue(annotation.values, "value", Function.identity());
                            String fieldName = value != null ? value : method.name;
                            if (fieldName.equals(this.name)) {
                                LOGGER.info("Found accessor {} for field {}", method.name, fieldName);
                                if (!Type.getReturnType(method.desc).getDescriptor().equals(this.type)) {
                                    throw new RuntimeException("Unexpected return type for method " + method.name + ", expected " + this.type);
                                }

                                String oldName = method.name;
                                String newName = oldName + "$internal";
                                String oldDesc = method.desc;
                                String newDesc = Type.getMethodDescriptor(Type.getType(this.replacementType));
                                method.name = newName;
                                method.desc = newDesc;

                                MethodNode wrapper = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC, oldName, oldDesc, null, new String[0]);
                                wrapper.visitCode();
                                wrapper.instructions.add(this.patcher.apply(() -> {
                                    InsnList shadowList = new InsnList();
                                    shadowList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                    shadowList.add(new TypeInsnNode(Opcodes.CHECKCAST, this.ownerClass));
                                    shadowList.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, this.ownerClass, newName, method.desc, false));
                                    return shadowList;
                                }));
                                wrapper.visitInsn(Opcodes.ARETURN);
                                wrapper.visitEnd();
                                classNode.methods.add(wrapper);

                                break;
                            }
                        }
                    }
                }

                for (ListIterator<AbstractInsnNode> iterator = method.instructions.iterator(); iterator.hasNext(); ) {
                    AbstractInsnNode insn = iterator.next();

                    if (insn.getOpcode() == Opcodes.GETFIELD && insn instanceof FieldInsnNode fieldInsn && fieldInsn.name.equals(this.name)) {
                        LOGGER.info("Converting field {} reference type in method {}.{}{}", this.name, classNode.name, method.name, method.desc);

                        fieldInsn.desc = this.replacementType;
                        method.instructions.insert(fieldInsn, this.patcher.apply(() -> {
                            InsnList list = new InsnList();
                            list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            list.add(new FieldInsnNode(fieldInsn.getOpcode(), fieldInsn.owner, fieldInsn.name, fieldInsn.desc));
                            return list;
                        }));
                        method.instructions.remove(insn.getPrevious());
                        method.instructions.remove(insn);
                    }
                }
            }
        }
    }
}
