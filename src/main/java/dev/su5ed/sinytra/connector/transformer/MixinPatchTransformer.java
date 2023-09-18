package dev.su5ed.sinytra.connector.transformer;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.MixinClassGenerator;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.transformer.DynamicLVTPatch;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodAccess;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.transformer.patch.ClassResourcesTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.EnvironmentStripperTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.FieldTypeAdapter;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
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

public class MixinPatchTransformer implements Transformer {
    private static final List<Patch> PATCHES = List.of(
        // TODO Add mirror mixin method that injects into ForgeHooks#onPlaceItemIntoWorld for server side behavior
        Patch.builder()
            .targetClass("net/minecraft/client/Minecraft")
            .targetMethod("<init>")
            .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startClient(Ljava/io/File;Ljava/lang/Object;)V")
            .modifyInjectionPoint("Ljava/lang/Thread;currentThread()Ljava/lang/Thread;")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/server/Main")
            .targetMethod("main([Ljava/lang/String;)V")
            .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V")
            .modifyInjectionPoint("Lnet/minecraftforge/server/loading/ServerModLoader;load()V")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/LevelRenderer")
            .targetMethod("m_109514_(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/core/BlockPos;)V")
            .targetInjectionPoint("Lnet/minecraft/client/gui/Gui;m_93055_(Lnet/minecraft/network/chat/Component;)V")
            .modifyTarget("playStreamingMusic(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/RecordItem;)V")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/gui/screens/inventory/AbstractContainerScreen")
            .targetMethod("m_280072_")
            .targetInjectionPoint("Lnet/minecraft/client/gui/GuiGraphics;m_280677_(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V")
            .modifyInjectionPoint("Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;Lnet/minecraft/world/item/ItemStack;II)V")
            .modifyParams(builder -> builder.insert(3, Type.getObjectType("net/minecraft/world/item/ItemStack")).targetType(ModifyMethodParams.TargetType.INJECTION_POINT))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/entity/LivingEntity")
            .targetMethod("m_21208_()V")
            .targetMixinType(Patch.MODIFY_CONST)
            .extractMixin("net/minecraftforge/common/extensions/IForgeLivingEntity")
            .modifyTarget("sinkInFluid(Lnet/minecraftforge/fluids/FluidType;)V")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/layers/ElytraLayer")
            .targetMethod("m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
            .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
            .targetMixinType(Patch.MODIFY_EXPR_VAL)
            .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/ElytraLayer;shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/ShaderInstance")
            .targetMethod("<init>")
            .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;<init>(Ljava/lang/String;)V")
            .disable()
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/item/enchantment/EnchantmentHelper")
            .targetInjectionPoint("Lnet/minecraft/world/item/Item;m_6473_()I")
            .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;getEnchantmentValue()I")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/entity/LivingEntity")
            .targetMethod("m_7023_(Lnet/minecraft/world/phys/Vec3;)V")
            .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;m_49958_()F")
            .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
            .targetMethod("m_214168_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;Lnet/minecraft/core/Direction;II)V")
            .targetInjectionPoint("Lnet/minecraft/world/phys/Vec3;m_82557_(Lnet/minecraft/world/phys/Vec3;)D")
            .modifyTarget("canReach(Lnet/minecraft/core/BlockPos;D)Z")
            .extractMixin("net/minecraftforge/common/extensions/IForgePlayer")
            .build(),
        // There exists a variable in this method that is an exact copy of the previous one. It gets removed by forge binpatches that follow recompiled java code.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/LightTexture")
            .targetMethod("m_109881_")
            .targetInjectionPoint("Lnet/minecraft/client/renderer/LightTexture;m_252983_(Lorg/joml/Vector3f;)V")
            .modifyParams(builder -> builder.substitute(17, 16))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/Minecraft")
            .targetMethod("m_285666_")
            .modifyTarget("lambda$new$4(Lcom/mojang/realmsclient/client/RealmsClient;Lnet/minecraft/server/packs/resources/ReloadInstance;Lnet/minecraft/client/main/GameConfig;)V")
            .modifyParams(builder -> builder
                .insert(0, Type.getObjectType("com/mojang/realmsclient/client/RealmsClient"))
                .insert(1, Type.getObjectType("net/minecraft/server/packs/resources/ReloadInstance"))
                .insert(2, Type.getObjectType("net/minecraft/client/main/GameConfig")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/level/block/FireBlock")
            .redirectShadowMethod(
                "Lnet/minecraft/world/level/block/FireBlock;m_221150_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V",
                "Lnet/minecraft/world/level/block/FireBlock;tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
                (insn, list) -> list.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "NORTH", "Lnet/minecraft/core/Direction;")))
            .build(),
        // Move HUD rendering calls at Options.renderDebug to a lambda in Forge's vanilla gui overlay enum class
        Patch.builder()
            .targetClass("net/minecraft/client/gui/Gui")
            .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
            .targetInjectionPoint("Lnet/minecraft/client/Options;f_92063_:Z")
            .modifyTarget("lambda$static$18(Lnet/minecraftforge/client/gui/overlay/ForgeGui;Lnet/minecraft/client/gui/GuiGraphics;FII)V")
            .modifyInjectionPoint("HEAD", "")
            .modifyMethodAccess(new ModifyMethodAccess.AccessChange(true, Opcodes.ACC_STATIC))
            .modifyParams(builder -> builder
                .replace(0, Type.getObjectType("net/minecraftforge/client/gui/overlay/ForgeGui"))
                .insert(3, Type.INT_TYPE)
                .insert(4, Type.INT_TYPE))
            .modifyTargetClasses(classes -> classes.add(Type.getObjectType("net/minecraftforge/client/gui/overlay/VanillaGuiOverlay")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/entity/player/Player")
            .targetMethod("m_7909_(F)V")
            .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
            .targetMixinType(Patch.WRAP_OPERATION)
            .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
            .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraftforge/common/ToolAction")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/FishingHookRenderer")
            .targetMethod("render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
            .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
            .targetMixinType(Patch.WRAP_OPERATION)
            .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
            .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraftforge/common/ToolAction")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/level/block/PowderSnowBlock")
            .targetMethod("m_154255_(Lnet/minecraft/world/entity/Entity;)Z")
            .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
            .targetMixinType(Patch.WRAP_OPERATION)
            .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canWalkOnPowderedSnow(Lnet/minecraft/world/entity/LivingEntity;)Z")
            .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraft/world/entity/LivingEntity")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/ItemInHandRenderer")
            .targetMethod("m_117184_")
            .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
            .targetMixinType(Patch.MODIFY_ARG)
            .modifyMixinType(Patch.REDIRECT, builder -> builder
                .sameTarget()
                .injectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;m_41720_()Lnet/minecraft/world/item/Item;"))
            .modifyParams(builder -> builder
                .replace(0, Type.getObjectType("net/minecraft/world/item/ItemStack"))
                .lvtFixer((index, insn, list) -> {
                    if (index == 1) {
                        list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", ASMAPI.mapMethod("m_41720_"), "()Lnet/minecraft/world/item/Item;"));
                    }
                }))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/GameRenderer")
            .targetMethod("m_109093_(FJZ)V")
            .targetInjectionPoint("Lnet/minecraft/client/gui/screens/Screen;m_280264_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
            .modifyInjectionPoint("Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/BoatRenderer")
            .targetMethod("m_7392_")
            .targetInjectionPoint("INVOKE", "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
            .modifyTarget("getModelWithLocation")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher")
            .targetMethod("<init>")
            .modifyTarget("<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;ZLnet/minecraft/client/renderer/ChunkBufferBuilderPack;I)V")
            .modifyVariableIndex(6, 1)
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/item/ItemStack")
            .targetMethod("m_41661_")
            .modifyTarget("lambda$useOn$3")
            .modifyParams(builder -> builder.insert(1, Type.getObjectType("net/minecraft/world/item/context/UseOnContext")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen")
            .targetMethod("m_280113_")
            .targetInjectionPoint("Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;")
            .modifyInjectionPoint("Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
            .targetMethod("m_9280_")
            .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;m_5707_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V")
            .modifyTarget("removeBlock")
            .modifyParams(builder -> builder.insert(1, Type.BOOLEAN_TYPE))
            .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z")
            .build(),
        // Disable potential duplicate attempts at making shaders IDs namespace aware - Forge already does this for us.
        // Attempts at doing so again will fail.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/EffectInstance")
            .targetMethod("<init>", "m_172566_")
            .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
            .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
            .targetMixinType(Patch.REDIRECT)
            .disable()
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/PostChain")
            .targetMethod("m_110030_")
            .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
            .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
            .targetMixinType(Patch.REDIRECT)
            .disable()
            .build(),
        // Move arg modifier to the forge method, which replaces all usages of the vanilla one
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
            .targetMethod("m_289609_")
            .targetMixinType(Patch.MODIFY_ARG)
            .modifyTarget("renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
            .targetMethod("m_289609_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;ZFFFLjava/lang/String;)V")
            .modifyTarget("renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
            .modifyParams(builder -> builder
                .replace(4, Type.getObjectType("net/minecraft/client/model/Model"))
                .replace(9, Type.getObjectType("net/minecraft/resources/ResourceLocation"))
                .lvtFixer((index, insn, list) -> {
                    if (index == 10) {
                        list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "toString", "()Ljava/lang/String;"));
                    }
                }))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
            .targetMethod("m_289604_(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/HumanoidModel;Z)V")
            .modifyTarget("renderTrim(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V")
            .modifyParams(builder -> builder.replace(5, Type.getObjectType("net/minecraft/client/model/Model")))
            .build(),
        // For mods who wish to override HumanoidArmorLayer parts. On Fabric, the usual approach seems to be modifying the model (4th) arg of renderModel in HumanoidArmorLayer#renderArmorPiece.
        // To make this kind of modification forge-compatible, we:
        // 1. Replace the injection point with forge's overloaded method that takes in a ResourceLocation as its last argument
        // 2. Set the mixin method's first parameter and return type to be Model instead of HumanoidModel. Unfortunately, forge's hook narrows down the variable's type, so we
        //    change it in the method accordingly.
        // 3. Add a cast check to the mixin method so that it doesn't apply to models that are not a HumanoidModel. This only applies to cases where a forge mod has modified
        //    the model, so we don't really care about modifying it anymore.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
            .targetMethod("m_117118_")
            .targetMixinType(Patch.MODIFY_ARG)
            .targetAnnotationValues(values -> (Integer) values.get("index").get() == 4)
            .targetInjectionPoint("INVOKE", "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;m_289609_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;ZFFFLjava/lang/String;)V")
            .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
            .modifyParams(builder -> builder
                .targetType(ModifyMethodParams.TargetType.ALL)
                .replace(0, Type.getObjectType("net/minecraft/client/model/Model"))
                .lvtFixer((index, insn, list) -> {
                    if (index == 1) {
                        list.insert(insn, new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/model/HumanoidModel"));
                    }
                }))
            .transform((classNode, methodNode, annotation, annotationValues, context) -> {
                methodNode.desc = "(Lnet/minecraft/client/model/Model;)Lnet/minecraft/client/model/Model;";
                methodNode.signature = null;
                InsnList insns = new InsnList();
                LabelNode cont = new LabelNode();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new TypeInsnNode(Opcodes.INSTANCEOF, "net/minecraft/client/model/HumanoidModel"));
                insns.add(new JumpInsnNode(Opcodes.IFNE, cont));
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new InsnNode(Opcodes.ARETURN));
                insns.add(cont);
                methodNode.instructions.insert(insns);
                return Patch.Result.APPLY;
            })
            .build(),
        Patch.builder()
            .transform(new DynamicLVTPatch(JarTransformer::getLvtOffsetsData))
            .build()
    );
    private static final List<ClassTransform> CLASS_TRANSFORMS = List.of(
        new ClassResourcesTransformer(),
        new FieldTypeAdapter(),
        new EnvironmentStripperTransformer()
    );
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Set<String> mixinPackages;
    private final PatchEnvironment environment;
    private final List<? extends Patch> patches;

    public MixinPatchTransformer(Set<String> mixinPackages, Map<String, Map<String, String>> environment, List<? extends Patch> adapterPatches) {
        this.mixinPackages = mixinPackages;
        this.environment = new PatchEnvironment(environment);
        this.patches = ImmutableList.<Patch>builder().addAll(adapterPatches).addAll(PATCHES).build();
    }

    public void finalize(Path zipRoot, Collection<String> configs, SrgRemappingReferenceMapper.SimpleRefmap refmap) {
        Map<String, MixinClassGenerator.GeneratedClass> generatedMixinClasses = this.environment.getClassGenerator().getGeneratedMixinClasses();
        if (generatedMixinClasses.isEmpty()) {
            return;
        }
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
                                for (MixinClassGenerator.GeneratedClass generatedClass : mixins.values()) {
                                    moveRefmapMappings(generatedClass.originalName(), generatedClass.generatedName(), json.get("refmap").getAsString(), zipRoot, refmap);
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

    private void moveRefmapMappings(String oldClass, String newClass, String refmap, Path root, SrgRemappingReferenceMapper.SimpleRefmap oldRefmap) throws IOException {
        Map<String, String> mappingsEntry = oldRefmap.mappings.get(oldClass);
        if (mappingsEntry == null) {
            return;
        }
        Map<String, String> dataMappingsEntry = oldRefmap.data.get("searge").get(oldClass);
        if (dataMappingsEntry == null) {
            return;
        }
        Path path = root.resolve(refmap);
        if (Files.notExists(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            SrgRemappingReferenceMapper.SimpleRefmap configRefmap = new Gson().fromJson(reader, SrgRemappingReferenceMapper.SimpleRefmap.class);

            configRefmap.mappings.put(newClass, mappingsEntry);
            configRefmap.data.get("searge").put(newClass, dataMappingsEntry);

            String output = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(configRefmap);
            Files.writeString(path, output, StandardCharsets.UTF_8);
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

    private boolean isInMixinPackage(String className) {
        for (String pkg : this.mixinPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        Patch.Result patchResult = Patch.Result.PASS;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (ClassTransform transform : CLASS_TRANSFORMS) {
            patchResult = patchResult.or(transform.apply(node));
        }

        if (isInMixinPackage(className)) {
            for (Patch patch : this.patches) {
                patchResult = patchResult.or(patch.apply(node, this.environment));
            }
        }

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
        this.environment.getClassGenerator().getGeneratedMixinClasses().forEach((name, cls) -> {
            ClassWriter writer = new ClassWriter(0);
            cls.node().accept(writer);
            byte[] bytes = writer.toByteArray();
            entries.add(ClassEntry.create(name + ".class", ConnectorUtil.ZIP_TIME, bytes));
        });
        return entries;
    }
}
