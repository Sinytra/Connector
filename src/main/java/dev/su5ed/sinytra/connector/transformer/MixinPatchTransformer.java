package dev.su5ed.sinytra.connector.transformer;

import com.google.common.collect.ImmutableList;
import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.transformer.DynamicLVTPatch;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import dev.su5ed.sinytra.connector.transformer.patch.ClassResourcesTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.EnvironmentStripperTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.FieldTypeAdapter;
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
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class MixinPatchTransformer implements Transformer {
    private static final List<Patch> PATCHES = List.of(
        // TODO Add mirror mixin method that injects into ForgeHooks#onPlaceItemIntoWorld for server side behavior
        Patch.builder()
            .targetClass("net/minecraft/world/item/enchantment/EnchantmentHelper")
            .targetInjectionPoint("Lnet/minecraft/world/item/Item;m_6473_()I")
            .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;getEnchantmentValue()I")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/entity/LivingEntity")
            .targetMethod("m_7023_(Lnet/minecraft/world/phys/Vec3;)V")
            .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;m_49958_()F")
            .targetMixinType(Patch.MODIFY_VAR)
            .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F")
            .modifyAnnotationValues(annotation -> {
                if (PatchInstance.findAnnotationValue(annotation.values, "ordinal").isEmpty()) {
                    annotation.values.add("ordinal");
                    annotation.values.add(0);
                }
                return false;
            })
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
        // Redirect HUD rendering calls to Forge's replacement class
        Patch.builder()
            .targetClass("net/minecraft/client/gui/Gui")
            .targetMethod("m_280421_")
            .targetInjectionPoint("Lnet/minecraft/client/Options;f_92063_:Z")
            .modifyTarget("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
            .modifyInjectionPoint("TAIL", "")
            .modifyTargetClasses(classes -> classes.add(Type.getObjectType("net/minecraftforge/client/gui/overlay/ForgeGui")))
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

    private final Set<String> mixinPackages;
    private final PatchEnvironment refmap;
    private final List<? extends Patch> patches;

    public MixinPatchTransformer(Set<String> mixinPackages, Map<String, Map<String, String>> refmap, List<? extends Patch> adapterPatches) {
        this.mixinPackages = mixinPackages;
        this.refmap = new PatchEnvironment(refmap);
        this.patches = ImmutableList.<Patch>builder().addAll(adapterPatches).addAll(PATCHES).build();
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
                patchResult = patchResult.or(patch.apply(node, this.refmap));
            }
        }

        if (patchResult != Patch.Result.PASS) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | (patchResult == Patch.Result.COMPUTE_FRAMES ? ClassWriter.COMPUTE_FRAMES : 0));
            node.accept(writer);
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
        return entry;
    }
}
