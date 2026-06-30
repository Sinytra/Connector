package org.sinytra.connector.transformer.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.transform.patch.MethodPatch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MixinPatches {
    // TODO 26.1 Update
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
                .targetMethod("baseTick")
                .targetInjectionPoint("Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z")
                .modifyTarget("onLivingBreathe")
                .modifyInjectionPoint("Lnet/neoforged/neoforge/fluids/FluidType;isAir()Z")
                .extractMixin("net/neoforged/neoforge/common/CommonHooks")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("goDownInWater()V")
                .targetConstant(-0.03999999910593033D)
                .extractMixin("net/neoforged/neoforge/common/extensions/ILivingEntityExtension")
                .modifyTarget("sinkInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("updateFallFlying")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")
                .extractMixin("net/minecraft/world/item/ElytraItem")
                .modifyTarget("elytraFlightTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;I)Z")
                .build(),
            // Move redirectors of Map.put to KeyMappingLookup.put
            MethodPatch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("resetMapping()V")
                .targetInjectionPoint("Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .modifyInjectionPoint("Lnet/minecraftforge/client/settings/KeyMappingLookup;put(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V")
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
            // ======= Rendering patches 
            MethodPatch.builder()
                .targetClass("net/minecraft/client/renderer/ShaderInstance", "net/minecraft/client/renderer/EffectInstance")
                .targetMethod("<init>", "getOrCreate")
                .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                // Axiom-specific (broken) target we still want to match
                .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;")
                .disable()
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/renderer/PostChain")
                .targetMethod("parsePassNode")
                // TODO update these
                .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
                .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .targetMixinType(MixinAnnotations.REDIRECT)
                .disable()
                .build(),
            // Disable potential duplicate attempts at making shaders IDs namespace aware - Neo already does this for us.
            // Attempts at doing so again will fail.
            MethodPatch.builder()
                .targetClass("net/minecraft/client/renderer/EffectInstance")
                .targetMethod("<init>", "getOrCreate")
                .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .targetMixinType(MixinAnnotations.REDIRECT)
                .disable()
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/renderer/entity/layers/ElytraLayer")
                .targetMethod("render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinAnnotations.REDIRECT)
//                .modifyParams(builder -> builder
//                    .insert(0, Type.getObjectType("net/minecraft/client/renderer/entity/layers/ElytraLayer"))
//                    .replace(2, Type.getObjectType("net/minecraft/world/entity/LivingEntity"))
//                    .targetType(ParamTransformTarget.INJECTION_POINT)
//                    .ignoreOffset())
                .divertRedirector(adapter -> {
                    adapter.visitVarInsn(Opcodes.ALOAD, 1);
                    adapter.visitVarInsn(Opcodes.ALOAD, 2);
                    adapter.visitVarInsn(Opcodes.ALOAD, 3);
                    adapter.invokevirtual("net/minecraft/client/renderer/entity/layers/ElytraLayer", "shouldRender", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z", false);
                })
                .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/ElytraLayer;shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")
                .build(),
            // ======= TODO Handle in adapter
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/vehicle/Boat")
                .targetMethod("m_38394_", "m_38393_", "m_38371_", "m_7840_")
                .targetInjectionPoint("Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z")
                .targetMixinType(MixinAnnotations.REDIRECT)
                .modifyInjectionPoint("Lnet/minecraft/world/entity/vehicle/Boat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z")
//                .modifyParams(b -> b
//                    .targetType(ParamTransformTarget.INJECTION_POINT)
//                    .ignoreOffset()
//                    .insert(0, Type.getObjectType("net/minecraft/world/entity/vehicle/Boat"))
//                    .inline(2, i -> i.getstatic("net/minecraft/tags/FluidTags", "WATER", "Lnet/minecraft/tags/TagKey;")))
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/player/Player")
                .targetMethod("hurtCurrentlyUsedShield(F)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinAnnotations.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/renderer/entity/FishingHookRenderer")
                .targetMethod("render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinAnnotations.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
                .build(),
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
            .map(o -> (MethodPatch) o)
            .collect(Collectors.toList()); // Mutable list
    }
}
