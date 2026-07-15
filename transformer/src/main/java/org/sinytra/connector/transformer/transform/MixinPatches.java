package org.sinytra.connector.transformer.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.transform.patch.MethodPatch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MixinPatches {
    public static List<MethodPatch> getPriorityPatches() {
        return List.of(
            MethodPatch.builder()
                .targetClass("net/minecraft/world/item/ItemStack")
                .targetMethod("useOn")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;")
                .modifyTarget("connector_useOn")
                .replaceInjectionPoint("RETURN", null)
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/Minecraft")
                .targetMethod("<init>")
                .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startClient(Ljava/io/File;Ljava/lang/Object;)V")
                .modifyInjectionPoint("Ljava/lang/Thread;currentThread()Ljava/lang/Thread;")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/server/Main")
                .targetMethod("main([Ljava/lang/String;)V")
                .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V")
                .modifyInjectionPoint("Lnet/neoforged/neoforge/server/loading/ServerModLoader;load()V")
                .build(),

            // We not only have to extract the mixin but we also have to retarget it to a method we inject
            // as the NeoForge extension method accepts a LevelReader rather than a Level so our
            // injected method will handle a safe cast and reordering the locals (swap the beacon pos and the block pos)
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/block/entity/BeaconBlockEntity")
                .targetMethod("tick")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/DyeColor;getTextureDiffuseColor()I")
                .modifyStatic(false)
                .extractMixin("net/neoforged/neoforge/common/extensions/IBlockExtension")
                .modifyTarget("connector_getTextureDiffuseColor")
                .build()
        );
    }

    public static List<MethodPatch> getPatches() {
        final List<Object> patches = List.of(
            // ======= Necessary manual patches 
            MethodPatch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("set")
                .targetInjectionPoint("TAIL", null)
                .modifyInjectionPoint("INVOKE", "Lnet/minecraft/client/KeyMapping;setDown(Z)V")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("click")
                .targetInjectionPoint("TAIL", null)
                .modifyInjectionPoint("FIELD", "Lnet/minecraft/client/KeyMapping;clickCount:I")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/block/piston/PistonStructureResolver")
                .targetMethod("isSticky")
                .modifyTarget("canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z")
                .modifyStatic(false) // TODO Should be automatic
                .extractMixin("net/neoforged/neoforge/common/extensions/IBlockStateExtension")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("goDownInWater")
                .targetMixinType(MixinAnnotations.MODIFY_EXPR_VAL)
                .targetConstant(-0.03999999910593033D)
                .extractMixin("net/neoforged/neoforge/common/extensions/ILivingEntityExtension")
                .modifyTarget("sinkInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("travelInAir")
                .targetMixinType(MixinAnnotations.MODIFY_EXPR_VAL)
                .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;getFriction()F")
                .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/inventory/AnvilMenu")
                .targetMethod("createResult")
                .targetMixinType(MixinAnnotations.MODIFY_CONST)
                .modifyTarget("createResultInternal")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/item/ItemStack")
                .targetMethod("forEachModifier(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V")
                .targetMixinType(MixinAnnotations.INJECT)
                .targetInjectionPoint("Lnet/minecraft/world/item/component/ItemAttributeModifiers;forEach(Lnet/minecraft/world/entity/EquipmentSlot;Ljava/util/function/BiConsumer;)V")
                .disable()
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/server/level/ServerPlayer")
                .targetMethod("startSleepInBed")
                .targetMixinType(MixinAnnotations.INJECT)
                .targetInjectionPoint("Lnet/minecraft/server/level/ServerPlayer;setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V")
                .modifyTarget("lambda$startSleepInBed$0(Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/item/BoneMealItem")
                .targetMethod("growCrop")
                .targetMixinType(MixinAnnotations.INJECT)
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;shrink(I)V")
                .modifyTarget("applyBonemeal(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
                .targetMethod("destroyBlock")
                .targetMixinType(MixinAnnotations.MODIFY_VAR)
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;mineBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)V")
                .modifyOrdinal(0)
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/levelgen/PhantomSpawner")
                .targetMethod("tick")
                .targetMixinType(MixinAnnotations.INJECT)
                .targetInjectionPoint("Lnet/minecraft/world/level/dimension/DimensionType;hasSkyLight()Z")
                .modifyInjectionPoint("Lnet/minecraft/server/level/ServerPlayer;blockPosition()Lnet/minecraft/core/BlockPos;")
                .modifyInjectionPointOrdinal(0)
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/block/FenceGateBlock")
                .targetMixinType(MixinAnnotations.MODIFY_EXPR_VAL)
                .targetInjectionPoint("Lnet/minecraft/world/level/block/state/properties/WoodType;fenceGateOpen()Lnet/minecraft/sounds/SoundEvent;")
                .modifyInjectionPoint("FIELD", "Lnet/minecraft/world/level/block/FenceGateBlock;openSound:Lnet/minecraft/sounds/SoundEvent;")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/block/FenceGateBlock")
                .targetMixinType(MixinAnnotations.MODIFY_EXPR_VAL)
                .targetInjectionPoint("Lnet/minecraft/world/level/block/state/properties/WoodType;fenceGateClose()Lnet/minecraft/sounds/SoundEvent;")
                .modifyInjectionPoint("FIELD", "Lnet/minecraft/world/level/block/FenceGateBlock;closeSound:Lnet/minecraft/sounds/SoundEvent;")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/item/component/BlocksAttacks")
                .targetMethod("hurtBlockingItem")
                .targetMixinType(MixinAnnotations.WRAP_OPERATION)
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V")
                .disable()
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/ai/goal/RangedBowAttackGoal")
                .targetMethod("tick")
                .targetMixinType(MixinAnnotations.INJECT)
                .targetInjectionPoint("Lnet/minecraft/world/entity/monster/Monster;lookAt(Lnet/minecraft/world/entity/Entity;FF)V")
                .modifyInjectionPoint("Lnet/minecraft/world/entity/Mob;lookAt(Lnet/minecraft/world/entity/Entity;FF)V")
                .modifyInjectionPointOrdinal(1)
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/animal/fox/Fox")
                .targetMethod("dropAllDeathLoot")
                .targetMixinType(MixinAnnotations.MODIFY_EXPR_VAL)
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;isEmpty()Z")
                .modifyTarget("dropEquipment")
                .transform((context, configuration) -> {
                    MethodNode methodNode = context.methodNode();
                    LabelNode preventDrop = new LabelNode();
                    methodNode.desc = "(Z)Z";
                    methodNode.instructions.clear();
                    methodNode.tryCatchBlocks.clear();
                    methodNode.localVariables = null;
                    methodNode.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    methodNode.instructions.add(new JumpInsnNode(Opcodes.IFNE, preventDrop));
                    methodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    methodNode.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/world/entity/animal/fox/Fox"));
                    methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/entity/animal/fox/Fox", "level", "()Lnet/minecraft/world/level/Level;", false));
                    methodNode.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/server/level/ServerLevel"));
                    methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/server/level/ServerLevel", "getGameRules", "()Lnet/minecraft/world/level/gamerules/GameRules;", false));
                    methodNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/world/level/gamerules/GameRules", "MOB_DROPS", "Lnet/minecraft/world/level/gamerules/GameRule;"));
                    methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/gamerules/GameRules", "get", "(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;", false));
                    methodNode.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                    methodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false));
                    methodNode.instructions.add(new JumpInsnNode(Opcodes.IFEQ, preventDrop));
                    methodNode.instructions.add(new InsnNode(Opcodes.ICONST_0));
                    methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
                    methodNode.instructions.add(preventDrop);
                    methodNode.instructions.add(new InsnNode(Opcodes.ICONST_1));
                    methodNode.instructions.add(new InsnNode(Opcodes.IRETURN));
                    methodNode.maxLocals = 2;
                    methodNode.maxStack = 2;
                    return PatchResult.APPLY;
                })
                .build(),
            // Move redirectors of Map.put to KeyMappingLookup.put
            MethodPatch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("resetMapping()V")
                .targetInjectionPoint("Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .modifyInjectionPoint("Lnet/neoforged/neoforge/client/settings/KeyMappingLookup;put(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V")
                .targetMixinType(MixinAnnotations.REDIRECT)
                .transform((context, configuration) -> {
                    MethodNode methodNode = context.methodNode();
                    for (AbstractInsnNode insn : methodNode.instructions) {
                        if (insn.getOpcode() == Opcodes.ARETURN) {
                            methodNode.instructions.insertBefore(insn, new InsnNode(Opcodes.POP));
                            methodNode.instructions.set(insn, new InsnNode(Opcodes.RETURN));
                        } else if (insn instanceof MethodInsnNode minsn && minsn.name.equals("put") && minsn.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                            minsn.desc = "(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V";
                            minsn.itf = false;
                            minsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                            methodNode.instructions.insert(minsn, new InsnNode(Opcodes.ACONST_NULL));
                        }
                    }
                    methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(methodNode.desc));
                    return PatchResult.APPLY;
                })
                .build(),
            // NeoForge moves this behaviour out completely with no viable replacement, so we disable it for now
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/animal/SnowGolem", "net/minecraft/world/entity/animal/Sheep", "net/minecraft/world/entity/animal/MushroomCow")
                .targetMethod("mobInteract")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .disable()
                .build(),
            // ======= Rendering patches =======
            // TODO These should be automatic
            MethodPatch.builder()
                .targetClass("net/minecraft/world/level/block/PowderSnowBlock")
                .targetMethod("canEntityWalkOnPowderSnow(Lnet/minecraft/world/entity/Entity;)Z")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinAnnotations.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canWalkOnPowderedSnow(Lnet/minecraft/world/entity/LivingEntity;)Z")
                .build(),
            MethodPatch.builder() // This is the annoying instanceof CrossbowItem patch TODO see DynamicSyntheticInstanceofPatch
                .targetClass("net/minecraft/client/renderer/ItemInHandRenderer")
                .targetMethod("renderArmWithItem")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinAnnotations.MODIFY_ARG)
                .modifyMixinType(MixinAnnotations.REDIRECT)
                .modifyTarget("renderArmWithItem")
                .modifyInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;")
                .build()
        );

        return patches.stream()
            .flatMap(p -> p instanceof List<?> lst ? lst.stream() : Stream.of(p))
            .map(MethodPatch.class::cast)
            .collect(Collectors.toList()); // Mutable list
    }
}
