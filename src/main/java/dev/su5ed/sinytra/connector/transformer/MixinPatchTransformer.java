package dev.su5ed.sinytra.connector.transformer;

import dev.su5ed.sinytra.connector.transformer.patch.ClassResourcesTransformer;
import dev.su5ed.sinytra.connector.transformer.patch.ClassTransform;
import dev.su5ed.sinytra.connector.transformer.patch.Patch;
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
import java.util.Set;

public class MixinPatchTransformer implements Transformer {
    private static final List<Patch> PATCHES = List.of(
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/BoatRenderer")
            .targetMethod("render")
            .targetInjectionPoint("INVOKE", "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
            .modifyTarget("getModelWithLocation")
            .build(),
        // TODO Add mirror mixin method that injects into ForgeHooks#onPlaceItemIntoWorld for server side behavior
        Patch.builder()
            .targetClass("net/minecraft/world/item/ItemStack")
            .targetMethod("useOnBlock")
            .modifyParams(params -> params.add(1, Type.getType("Lnet/minecraft/world/item/context/UseOnContext;")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/resources/model/MultiPartBakedModel", "net/minecraft/client/resources/model/WeightedBakedModel")
            .targetMethod("m_213637_", "getQuads")
            .modifyTarget("getQuads")
            .modifyParams(params -> {
                params.add(Type.getType("Lnet/minecraftforge/client/model/data/ModelData;"));
                params.add(Type.getType("Lnet/minecraft/client/renderer/RenderType;"));
            })
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/server/level/ServerPlayer")
            .targetMethod("changeDimension")
            .modifyTarget("changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;Lorg/spongepowered/reloc/asm/mixin/injection/callback/CallbackInfoReturable;)V")
            .modifyParams(params -> params.add(1, Type.getObjectType("net/minecraftforge/common/util/ITeleporter")))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/particle/ParticleEngine")
            .targetMethod("render")
            .modifyTarget("render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V")
            .modifyParams(params -> {
                Type type = Type.getObjectType("net/minecraft/client/renderer/culling/Frustum");
                if (params.size() < 5) params.add(type);
                else params.add(5, type);
            })
            .build(),
        // float breakChance; added by Forge in AnvilMenu#onTake
        Patch.builder()
            .targetClass("net/minecraft/world/inventory/AnvilMenu")
            .targetMethod("lambda$onTake$2", "m_150476_")
            .modifyTarget("lambda$onTake$2(Lnet/minecraft/world/entity/player/Player;FLnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V")
            .modifyParams(params -> params.add(1, Type.FLOAT_TYPE))
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/chunk/ChunkRenderDispatcher")
            .targetMethod("<init>")
            .modifyTarget("<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;ZLnet/minecraft/client/renderer/ChunkBufferBuilderPack;I)V")
            .modifyVariableIndex(i -> i >= 6 ? i + 1 : i)
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/world/item/ItemStack")
            .targetMethod("useOnBlock")
            .modifyTarget("lambda$useOn$5")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen")
            .targetMethod("renderEffects")
            .targetInjectionPoint("Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;")
            .modifyInjectionPoint("Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;")
            .build(),
        Patch.builder()
            .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
            .targetMethod("tryBreakBlock")
            .targetInjectionPoint("Lnet/minecraft/block/Block;onBreak(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/entity/player/PlayerEntity;)V")
            .modifyTarget("removeBlock")
            .modifyParams(params -> params.add(1, Type.BOOLEAN_TYPE))
            .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z")
            .build(),
        // Disable potential duplicate attempts at making shaders IDs namespace aware - forge already does this for us.
        // Attempts at doing it again will fail.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/EffectInstance")
            .targetMethod("<init>", "loadEffect")
            .targetInjectionPoint("NEW", "net/minecraft/util/Identifier")
            .targetMixinType(Patch.REDIRECT)
            .disable()
            .build(),
        // Forge adds a new boolean shouldSit local variable in the render method. We prepend it to mixin LVT params.
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/LivingEntityRenderer")
            .targetMethod("render")
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
            .targetMethod("renderArmorParts")
            .targetMixinType(Patch.MODIFY_ARG)
            .modifyTarget("renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
            .build(),
        // Forge adds 2 new params to this method: ModelData and RenderType
        Patch.builder()
            .targetInjectionPoint("INVOKE", "Lnet/minecraft/client/render/block/BlockModelRenderer;render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLnet/minecraft/util/math/random/Random;JI)V")
            .targetMixinType(Patch.INJECT)
            .modifyInjectionPoint("Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V")
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
            .targetMethod("renderArmorPiece")
            .targetMixinType(Patch.MODIFY_ARG)
            .targetAnnotationValues(values -> (Integer) values.get("index").get() == 4)
            // TODO Normalize all mixin target mappings to Mojmap
            .targetInjectionPoint("INVOKE", "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;ZFFFLjava/lang/String;)V")
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
        new ClassResourcesTransformer()
    );

    private final Set<String> mixins;

    public MixinPatchTransformer(Set<String> mixins) {
        this.mixins = mixins;
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
            for (Patch patch : PATCHES) {
                needsWriting |= patch.apply(node);
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
