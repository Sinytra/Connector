package dev.su5ed.sinytra.connector.transformer;

import com.google.common.collect.ImmutableList;
import dev.su5ed.sinytra.adapter.patch.MixinRemaper;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.connector.transformer.patch.ClassResourcesTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.ClassTransform;
import dev.su5ed.sinytra.connector.transformer.patch.FieldTypeAdapter;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
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
            .modifyTarget("lambda$useOn$5")
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
            .modifyParams(params -> params.add(1, Type.BOOLEAN_TYPE))
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
        // Forge adds a new boolean shouldSit local variable in the render method. We prepend it to mixin LVT params.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/LivingEntityRenderer")
            .targetMethod("m_7392_")
            .modifyParams(params -> {
                Type ci = Type.getObjectType("org/spongepowered/reloc/asm/mixin/injection/callback/CallbackInfo");
                for (int i = 0; i < params.size(); i++) {
                    Type param = params.get(i);
                    // Add after CallbackInfo param
                    if (param.equals(ci) && params.size() >= i + 2) {
                        params.add(i + 1, Type.BOOLEAN_TYPE);
                        break;
                    }
                }
            })
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
            .modifyParams(params -> {
                if (params.get(0).getInternalName().equals("net/minecraft/client/model/HumanoidModel")) {
                    params.set(0, Type.getObjectType("net/minecraft/client/model/Model"));
                }
            }, (index, insn, list) -> {
                if (index == 1) {
                    list.insert(insn, new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/model/HumanoidModel"));
                }
            })
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
                return true;
            })
            .build()
    );
    private static final List<ClassTransform> CLASS_TRANSFORMS = List.of(
        new ClassResourcesTransformer(),
        new FieldTypeAdapter()
    );

    private final Set<String> mixins;
    private final MixinRemaper refmap;
    private final List<? extends Patch> patches;

    public MixinPatchTransformer(Set<String> mixins, Map<String, Map<String, String>> refmap, List<? extends Patch> adapterPatches) {
        this.mixins = mixins;
        this.refmap = new MixinRemaper(refmap);
        this.patches = ImmutableList.<Patch>builder().addAll(PATCHES).addAll(adapterPatches).build();
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        boolean needsWriting = false;
        boolean computeFrames = false;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (ClassTransform transform : CLASS_TRANSFORMS) {
            ClassTransform.Result result = transform.apply(node);
            if (result.applied()) {
                needsWriting = true;
                computeFrames |= result.computeFrames();
            }
        }

        if (this.mixins.contains(className)) {
            for (Patch patch : this.patches) {
                needsWriting |= patch.apply(node, this.refmap);
            }
        }

        if (needsWriting) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | (computeFrames ? ClassWriter.COMPUTE_FRAMES : 0));
            node.accept(writer);
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
        return entry;
    }
}
