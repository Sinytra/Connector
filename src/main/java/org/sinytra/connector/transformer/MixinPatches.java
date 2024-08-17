package org.sinytra.connector.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.transformer.operation.ModifyMethodAccess;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformTarget;

import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("deprecation")
public class MixinPatches {
    public static List<Patch> getPriorityPatches() {
        return List.of(
            Patch.builder()
                .targetClass("net/minecraft/world/item/ItemStack")
                .targetMethod("useOn")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;")
                .modifyTarget("connector_useOn")
                .modifyInjectionPoint("RETURN", "", true)
                .build(),
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
                .modifyInjectionPoint("Lnet/neoforged/neoforge/server/loading/ServerModLoader;load()V")
                .build()
        );
    }

    public static List<Patch> getPatches() {
        final List<Object> patches = List.of(
            // ======= Necessary manual patches 
            Patch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("set")
                .targetInjectionPoint("TAIL", "")
                .modifyInjectionPoint("Lnet/minecraft/client/KeyMapping;setDown(Z)V")
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/level/block/piston/PistonStructureResolver")
                .targetMethod("isSticky")
                .modifyTarget("canStickTo(Lnet/minecraft/world/level/block/state/BlockState;)Z")
                .modifyMethodAccess(new ModifyMethodAccess.AccessChange(false, Opcodes.ACC_STATIC)) // TODO Should be automatic
                .extractMixin("net/neoforged/neoforge/common/extensions/IBlockStateExtension")
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("baseTick")
                .targetInjectionPoint("Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z")
                .modifyTarget("onLivingBreathe")
                .modifyInjectionPoint("Lnet/neoforged/neoforge/fluids/FluidType;isAir()Z")
                .extractMixin("net/neoforged/neoforge/common/CommonHooks")
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("goDownInWater()V")
                .targetConstant(-0.03999999910593033D)
                .extractMixin("net/neoforged/neoforge/common/extensions/ILivingEntityExtension")
                .modifyTarget("sinkInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V")
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/entity/LivingEntity")
                .targetMethod("updateFallFlying")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V")
                .extractMixin("net/minecraft/world/item/ElytraItem")
                .modifyTarget("elytraFlightTick(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;I)Z")
                .build(),
            // Move redirectors of Map.put to KeyMappingLookup.put
            Patch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("resetMapping()V")
                .targetInjectionPoint("Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .modifyInjectionPoint("Lnet/minecraftforge/client/settings/KeyMappingLookup;put(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V")
                .targetMixinType(MixinConstants.REDIRECT)
                .modifyParams(builder -> builder
                    .replace(0, Type.getObjectType("net/minecraftforge/client/settings/KeyMappingLookup"))
                    .replace(1, Type.getObjectType("com/mojang/blaze3d/platform/InputConstants$Key"))
                    .replace(2, Type.getObjectType("net/minecraft/client/KeyMapping")))
                .transform((classNode, methodNode, methodContext, patchContext) -> {
                    for (ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                        AbstractInsnNode insn = iterator.next();
                        if (insn.getOpcode() == Opcodes.ARETURN) {
                            methodNode.instructions.insertBefore(insn, new InsnNode(Opcodes.POP));
                            methodNode.instructions.set(insn, new InsnNode(Opcodes.RETURN));
                        }
                        else if (insn instanceof MethodInsnNode minsn && minsn.name.equals("put") && minsn.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                            minsn.desc = "(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V";
                            minsn.itf = false;
                            minsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                            methodNode.instructions.insert(minsn, new InsnNode(Opcodes.ACONST_NULL));
                        }
                    }
                    methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(methodNode.desc));
                    return Patch.Result.APPLY;
                })
                .build(),
            // NeoForge moves this behaviour out completely with no viable replacement, so we disable it for now
            Patch.builder()
                .targetClass("net/minecraft/world/entity/animal/SnowGolem", "net/minecraft/world/entity/animal/Sheep", "net/minecraft/world/entity/animal/MushroomCow")
                .targetMethod("mobInteract")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .disable()
                .build(),
            // ======= Rendering patches 
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/ShaderInstance")
                .targetMethod("<init>")
                .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .disable()
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/PostChain")
                .targetMethod("parsePassNode")
                // TODO update these
                .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
                .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .targetMixinType(MixinConstants.REDIRECT)
                .disable()
                .build(),
            // Disable potential duplicate attempts at making shaders IDs namespace aware - Neo already does this for us.
            // Attempts at doing so again will fail.
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/EffectInstance")
                .targetMethod("<init>", "getOrCreate")
                .targetInjectionPoint("Lnet/minecraft/resources/ResourceLocation;withDefaultNamespace(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                .targetMixinType(MixinConstants.REDIRECT)
                .disable()
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/entity/layers/ElytraLayer")
                .targetMethod("render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinConstants.REDIRECT)
                .modifyParams(builder -> builder
                    .insert(0, Type.getObjectType("net/minecraft/client/renderer/entity/layers/ElytraLayer"))
                    .replace(2, Type.getObjectType("net/minecraft/world/entity/LivingEntity"))
                    .targetType(ParamTransformTarget.INJECTION_POINT)
                    .ignoreOffset())
                .divertRedirector(adapter -> {
                    adapter.visitVarInsn(Opcodes.ALOAD, 1);
                    adapter.visitVarInsn(Opcodes.ALOAD, 2);
                    adapter.visitVarInsn(Opcodes.ALOAD, 3);
                    adapter.invokevirtual("net/minecraft/client/renderer/entity/layers/ElytraLayer", "shouldRender", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z", false);
                })
                .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/ElytraLayer;shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")
                .build(),
            // ======= TODO Handle in adapter
            Patch.builder()
                .targetClass("net/minecraft/world/entity/vehicle/Boat")
                .targetMethod("m_38394_", "m_38393_", "m_38371_", "m_7840_")
                .targetInjectionPoint("Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z")
                .targetMixinType(MixinConstants.REDIRECT)
                .modifyInjectionPoint("Lnet/minecraft/world/entity/vehicle/Boat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z")
                .modifyParams(b -> b
                    .targetType(ParamTransformTarget.INJECTION_POINT)
                    .ignoreOffset()
                    .insert(0, Type.getObjectType("net/minecraft/world/entity/vehicle/Boat"))
                    .inline(2, i -> i.getstatic("net/minecraft/tags/FluidTags", "WATER", "Lnet/minecraft/tags/TagKey;")))
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/entity/player/Player")
                .targetMethod("hurtCurrentlyUsedShield(F)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinConstants.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
                .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraftforge/common/ToolAction")))
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/entity/FishingHookRenderer")
                .targetMethod("render(Lnet/minecraft/world/entity/projectile/FishingHook;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinConstants.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canPerformAction(Lnet/minecraftforge/common/ToolAction;)Z")
                .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraftforge/common/ToolAction")))
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/world/level/block/PowderSnowBlock")
                .targetMethod("canEntityWalkOnPowderSnow(Lnet/minecraft/world/entity/Entity;)Z")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinConstants.WRAP_OPERATION)
                .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canWalkOnPowderedSnow(Lnet/minecraft/world/entity/LivingEntity;)Z")
                .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraft/world/entity/LivingEntity")))
                .build(),
            Patch.builder() // This is the annoying instanceof CrossbowItem patch TODO see DynamicSyntheticInstanceofPatch
                .targetClass("net/minecraft/client/renderer/ItemInHandRenderer")
                .targetMethod("renderArmWithItem")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z")
                .targetMixinType(MixinConstants.MODIFY_ARG)
                .modifyParams(builder -> builder
                    .replace(0, Type.getObjectType("net/minecraft/world/item/ItemStack")))
                .modifyMixinType(MixinConstants.REDIRECT, builder -> builder
                    .sameTarget()
                    .injectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
                .build()
            // ========
            /*
            // Move arg modifier to the forge method, which replaces all usages of the vanilla one
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
                .targetMethod("m_289609_")
                .targetMixinType(MixinConstants.MODIFY_ARG)
                .modifyTarget("renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
                .build(),
            Patch.builder()
                .targetClass("net/minecraft/client/renderer/entity/layers/HumanoidArmorLayer")
                .targetMethod("m_289604_(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/HumanoidModel;Z)V")
                .modifyTarget("renderTrim(Lnet/minecraft/world/item/ArmorMaterial;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/armortrim/ArmorTrim;Lnet/minecraft/client/model/Model;Z)V")
                .transformParams(builder -> builder.replace(5, Type.getObjectType("net/minecraft/client/model/Model")))
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
                .targetMixinType(MixinConstants.MODIFY_ARG)
                .targetAnnotationValues(values -> values.<Integer>getValue("index").map(handle -> handle.get() == 4).orElse(false))
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;m_289609_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/HumanoidModel;ZFFFLjava/lang/String;)V")
                .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/HumanoidArmorLayer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ArmorItem;Lnet/minecraft/client/model/Model;ZFFFLnet/minecraft/resources/ResourceLocation;)V")
                .modifyParams(builder -> builder
                    .targetType(ParamTransformTarget.ALL)
                    .replace(0, Type.getObjectType("net/minecraft/client/model/Model"))
                    .lvtFixer((index, insn, list) -> {
                        if (index == 1) {
                            list.insert(insn, new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/model/HumanoidModel"));
                        }
                    }))
                .transform((classNode, methodNode, methodContext, context) -> {
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
            // Move redirectors of Map.put to KeyMappingLookup.put
            Patch.builder()
                .targetClass("net/minecraft/client/KeyMapping")
                .targetMethod("m_90854_()V")
                .targetInjectionPoint("Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                .modifyInjectionPoint("Lnet/minecraftforge/client/settings/KeyMappingLookup;put(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V")
                .targetMixinType(MixinConstants.REDIRECT)
                .modifyParams(builder -> builder
                    .replace(0, Type.getObjectType("net/minecraftforge/client/settings/KeyMappingLookup"))
                    .replace(1, Type.getObjectType("com/mojang/blaze3d/platform/InputConstants$Key"))
                    .replace(2, Type.getObjectType("net/minecraft/client/KeyMapping")))
                .transform((classNode, methodNode, methodContext, patchContext) -> {
                    for (ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                        AbstractInsnNode insn = iterator.next();
                        if (insn.getOpcode() == Opcodes.ARETURN) {
                            methodNode.instructions.insertBefore(insn, new InsnNode(Opcodes.POP));
                            methodNode.instructions.set(insn, new InsnNode(Opcodes.RETURN));
                        }
                        else if (insn instanceof MethodInsnNode minsn && minsn.name.equals("put") && minsn.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                            minsn.desc = "(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V";
                            minsn.itf = false;
                            minsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                            methodNode.instructions.insert(minsn, new InsnNode(Opcodes.ACONST_NULL));
                        }
                    }
                    methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(methodNode.desc));
                    return Patch.Result.APPLY;
                })
                .build(),*/);

        return patches.stream()
            .flatMap(p -> p instanceof List<?> lst ? lst.stream() : Stream.of(p))
            .map(o -> (Patch) o)
            .collect(Collectors.toList()); // Mutable list
    }
}
