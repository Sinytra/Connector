package dev.su5ed.connector.replacement;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.Map;

@SuppressWarnings("unused")
public abstract class LifecycleApiReplacements {

    @Redirect(
        method = {"getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;"},
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;"
        ),
        slice = @Slice(
            to = @At(
                value = "FIELD",
                target = "net/minecraft/world/level/chunk/LevelChunk.pendingBlockEntities:Ljava/util/Map;",
                opcode = Opcodes.GETFIELD
            )
        )
    )
    private Object onRemoveBlockEntity(Map map, Object key) {
        Object removed = map.remove(key);
        ServerLevel level = (ServerLevel) ((LevelChunk) (Object) this).getLevel();
        if (removed != null && level instanceof ServerLevel) {
            ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload((BlockEntity) removed, level);
        }
        return removed;
    }
}
