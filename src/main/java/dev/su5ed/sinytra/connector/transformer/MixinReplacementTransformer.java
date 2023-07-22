package dev.su5ed.sinytra.connector.transformer;

import dev.su5ed.sinytra.connector.transformer.patch.Patch;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;
import java.util.Set;

public class MixinReplacementTransformer implements Transformer {
    private static final List<Patch> PATCHES = List.of(
        Patch.builder()
            .targetClass("net/minecraft/client/renderer/entity/BoatRenderer")
            .targetMethod("render")
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
            .build()
    );

    private final Set<String> mixins;

    public MixinReplacementTransformer(Set<String> mixins) {
        this.mixins = mixins;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        if (this.mixins.contains(className)) {
            ClassReader reader = new ClassReader(entry.getData());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            List<Patch> replacements = PATCHES.stream()
                .filter(r -> r.apply(node))
                .toList();
            if (!replacements.isEmpty()) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); // TODO Compute frames
                node.accept(writer);
                return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
            }
        }
        return entry;
    }
}
