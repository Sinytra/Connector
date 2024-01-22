package dev.su5ed.sinytra.connector.transformer;

import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;
import java.util.ListIterator;
import java.util.stream.Stream;

public class MixinPatches {
    public static List<Patch> getPatches() {
        final List<Object> patches = List.of(Patch.builder()
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
                        .targetClass("net/minecraft/client/KeyMapping")
                        .targetMethod("m_90837_")
                        .targetInjectionPoint("TAIL", "")
                        .modifyTarget("connector_onSetKeyMapping")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/item/enchantment/EnchantmentHelper")
                        .targetMethod("m_44817_")
                        .targetInjectionPoint("Lnet/minecraft/world/item/enchantment/EnchantmentCategory;m_7454_(Lnet/minecraft/world/item/Item;)Z")
                        .modifyInjectionPoint("Lnet/minecraft/world/item/enchantment/Enchantment;canApplyAtEnchantingTable(Lnet/minecraft/world/item/ItemStack;)Z")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/item/ShovelItem")
                        .targetMethod("m_6225_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;m_60795_()Z")
                        .modifyInjectionPoint("Lnet/minecraft/world/level/Level;m_46859_(Lnet/minecraft/core/BlockPos;)Z")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/level/NaturalSpawner")
                        .targetMethod("m_220443_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure;f_228517_:Lnet/minecraft/util/random/WeightedRandomList;")
                        .modifyInjectionPoint("INVOKE", "Lnet/minecraft/world/level/StructureManager;m_220521_()Lnet/minecraft/core/RegistryAccess;")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/item/Item")
                        .targetMethod("m_7203_")
                        .targetInjectionPoint("Lnet/minecraft/world/item/Item;m_41473_()Lnet/minecraft/world/food/FoodProperties;")
                        .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;getFoodProperties(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/food/FoodProperties;")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/Entity")
                        .targetMethod("m_204031_(Lnet/minecraft/tags/TagKey;D)Z")
                        .targetInjectionPoint("Lnet/minecraft/world/phys/Vec3;m_82553_()D")
                        .modifyTarget("lambda$updateFluidHeightAndDoFluidPushing$26")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/Entity")
                        .targetMethod("m_204031_(Lnet/minecraft/tags/TagKey;D)Z")
                        .targetInjectionPoint("Lnet/minecraft/world/entity/Entity;m_146899_()Z")
                        .modifyTarget("updateFluidHeightAndDoFluidPushing()V")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/player/Player")
                        .targetMethod("m_6728_")
                        .targetInjectionPoint("Lnet/minecraft/world/entity/LivingEntity;m_213824_()Z")
                        .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canDisableShield(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;)Z")
                        .modifyParams(builder -> builder
                                .insert(0, Type.getObjectType("net/minecraft/world/item/ItemStack"))
                                .insert(1, Type.getObjectType("net/minecraft/world/item/ItemStack"))
                                .insert(2, Type.getObjectType("net/minecraft/world/entity/LivingEntity"))
                                .targetType(ModifyMethodParams.TargetType.INJECTION_POINT))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/item/MilkBucketItem")
                        .targetMethod("m_5922_")
                        .targetInjectionPoint("Lnet/minecraft/world/entity/LivingEntity;m_21219_()Z")
                        .modifyInjectionPoint("Lnet/minecraft/world/entity/LivingEntity;curePotionEffects(Lnet/minecraft/world/item/ItemStack;)Z")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/LivingEntity")
                        .targetMethod("m_21208_()V")
                        .targetMixinType(MixinConstants.MODIFY_CONST)
                        .extractMixin("net/minecraftforge/common/extensions/IForgeLivingEntity")
                        .modifyTarget("sinkInFluid(Lnet/minecraftforge/fluids/FluidType;)V")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/item/BoneMealItem")
                        .targetMethod("m_40627_")
                        .modifyTarget("applyBonemeal(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Z")
                        .modifyParams(builder -> builder.insert(3, Type.getObjectType("net/minecraft/world/entity/player/Player")))
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
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/vehicle/Boat")
                        .targetMethod("m_38394_", "m_38393_", "m_38371_", "m_7840_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/material/FluidState;m_205070_(Lnet/minecraft/tags/TagKey;)Z")
                        .targetMixinType(MixinConstants.REDIRECT)
                        .modifyInjectionPoint("Lnet/minecraft/world/entity/vehicle/Boat;canBoatInFluid(Lnet/minecraft/world/level/material/FluidState;)Z")
                        .modifyParams(b -> b
                                .targetType(ModifyMethodParams.TargetType.INJECTION_POINT)
                                .ignoreOffset()
                                .insert(0, Type.getObjectType("net/minecraft/world/entity/vehicle/Boat"))
                                .inline(2, i -> i.getstatic("net/minecraft/tags/FluidTags", "f_13131_", "Lnet/minecraft/tags/TagKey;")))
                        .build(),
                // There exists a variable in this method that is an exact copy of the previous one. It gets removed by forge binpatches that follow recompiled java code.
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/LightTexture")
                        .targetMethod("m_109881_")
                        .targetInjectionPoint("Lnet/minecraft/client/renderer/LightTexture;m_252983_(Lorg/joml/Vector3f;)V")
                        .modifyParams(builder -> builder.substitute(17, 16))
                        .build(),
                // This code is being removed by forge, mixins to it can be safely disabled
                Patch.builder()
                        .targetClass("net/minecraft/server/players/PlayerList")
                        .targetMethod("m_11239_")
                        .targetInjectionPoint("Lnet/minecraft/world/entity/player/Player;m_7755_()Lnet/minecraft/network/chat/Component;")
                        .targetMixinType(MixinConstants.REDIRECT)
                        .disable()
                        .build(),
                // TODO Automate
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
                                "m_221150_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V",
                                "tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
                                (insn, list) -> list.insertBefore(insn, new FieldInsnNode(Opcodes.GETSTATIC, "net/minecraft/core/Direction", "NORTH", "Lnet/minecraft/core/Direction;")))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/level/block/FireBlock")
                        .targetMethod("m_221150_")
                        .modifyTarget("tryCatchFire")
                        .modifyParams(b -> b
                                .insert(5, Type.getObjectType("net/minecraft/core/Direction"))
                                .targetType(ModifyMethodParams.TargetType.METHOD))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/level/block/FireBlock")
                        .targetInjectionPoint("Lnet/minecraft/world/level/block/FireBlock;m_221150_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;I)V")
                        .modifyInjectionPoint("Lnet/minecraft/world/level/block/FireBlock;tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280173_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .targetInjectionPoint("Lnet/minecraft/client/gui/Gui;m_168688_(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V")
                        .extractMixin("net/minecraftforge/client/gui/overlay/ForgeGui")
                        .modifyTarget("renderHealth(IILnet/minecraft/client/gui/GuiGraphics;)V")
                        .modifyParams(b -> b.insert(0, Type.INT_TYPE).insert(1, Type.INT_TYPE).targetType(ModifyMethodParams.TargetType.METHOD))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
                        .targetInjectionPoint("HEAD", "")
                        .modifyTarget("connector_preRender")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
                        .targetInjectionPoint("RETURN", "")
                        .targetInjectionPoint("TAIL", "")
                        .modifyTarget("connector_postRender")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280173_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .targetInjectionPoint("Lnet/minecraft/util/profiling/ProfilerFiller;m_6182_(Ljava/lang/String;)V")
                        .extractMixin("net/minecraftforge/client/gui/overlay/ForgeGui")
                        .modifyTarget("renderFood(IILnet/minecraft/client/gui/GuiGraphics;)V")
                        .modifyParams(b -> b.insert(0, Type.INT_TYPE).insert(1, Type.INT_TYPE).targetType(ModifyMethodParams.TargetType.METHOD))
                        .modifyInjectionPoint("HEAD", "", true)
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280173_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .targetInjectionPoint("HEAD", "")
                        .modifyTarget("connector_renderHealth")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280173_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .extractMixin("net/minecraftforge/client/gui/overlay/ForgeGui")
                        .modifyTarget("renderArmor(Lnet/minecraft/client/gui/GuiGraphics;II)V")
                        .modifyParams(b -> b.insert(1, Type.INT_TYPE).insert(2, Type.INT_TYPE).targetType(ModifyMethodParams.TargetType.METHOD))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
                        .targetInjectionPoint("Lnet/minecraft/client/gui/Gui;m_280518_(FLnet/minecraft/client/gui/GuiGraphics;)V")
                        .modifyTarget("connector_renderHotbar")
                        .modifyInjectionPoint("HEAD", "")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
                        .targetInjectionPoint("Lnet/minecraft/client/gui/Gui;m_280523_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .modifyTarget("connector_renderEffects")
                        .modifyInjectionPoint("HEAD", "")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280421_(Lnet/minecraft/client/gui/GuiGraphics;F)V")
                        .targetInjectionPoint("FIELD", "Lnet/minecraft/client/Options;f_92063_:Z")
                        .modifyTarget("connector_beforeDebugEnabled")
                        .modifyInjectionPoint("HEAD", "")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/Gui")
                        .targetMethod("m_280250_(Lnet/minecraft/client/gui/GuiGraphics;)V")
                        .targetInjectionPoint("HEAD", "")
                        .extractMixin("net/minecraftforge/client/gui/overlay/ForgeGui")
                        .modifyTarget("renderHealthMount(IILnet/minecraft/client/gui/GuiGraphics;)V")
                        .modifyParams(b -> b.insert(0, Type.INT_TYPE).insert(1, Type.INT_TYPE).targetType(ModifyMethodParams.TargetType.METHOD))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/world/entity/player/Player")
                        .targetMethod("m_7909_(F)V")
                        .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
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
                        .targetMethod("m_154255_(Lnet/minecraft/world/entity/Entity;)Z")
                        .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
                        .targetMixinType(MixinConstants.WRAP_OPERATION)
                        .modifyInjectionPoint("Lnet/minecraft/world/item/ItemStack;canWalkOnPowderedSnow(Lnet/minecraft/world/entity/LivingEntity;)Z")
                        .modifyParams(builder -> builder.replace(1, Type.getObjectType("net/minecraft/world/entity/LivingEntity")))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/GameRenderer")
                        .targetMethod("m_109087_")
                        .targetConstant(9.0)
                        .modifyMixinType(MixinConstants.MODIFY_VAR, builder -> builder
                                .sameTarget()
                                .injectionPoint("INVOKE_ASSIGN", "Lnet/minecraft/world/phys/Vec3;m_82557_(Lnet/minecraft/world/phys/Vec3;)D", v -> v.visit("ordinal", 1))
                                .putValue("ordinal", 3))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/ItemInHandRenderer")
                        .targetMethod("m_117184_")
                        .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
                        .targetMixinType(MixinConstants.MODIFY_ARG)
                        .modifyParams(builder -> builder
                                .replace(0, Type.getObjectType("net/minecraft/world/item/ItemStack")))
                        .modifyMixinType(MixinConstants.REDIRECT, builder -> builder
                                .sameTarget()
                                .injectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;m_41720_()Lnet/minecraft/world/item/Item;"))
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/GameRenderer")
                        .targetMethod("m_109093_(FJZ)V")
                        .targetInjectionPoint("Lnet/minecraft/client/gui/screens/Screen;m_280264_(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
                        .modifyInjectionPoint("Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
                        .modifyVariableIndex(0, 1)
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/entity/BoatRenderer")
                        .targetMethod("m_7392_")
                        .targetInjectionPoint("INVOKE", "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
                        .modifyTarget("getModelWithLocation")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/gui/screens/inventory/EffectRenderingInventoryScreen")
                        .targetMethod("m_280113_")
                        .targetInjectionPoint("Lcom/google/common/collect/Ordering;sortedCopy(Ljava/lang/Iterable;)Ljava/util/List;")
                        .modifyInjectionPoint("Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/Minecraft")
                        .targetMethod("m_91280_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;m_7397_(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/item/ItemStack;")
                        .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;getCloneItemStack(Lnet/minecraft/world/phys/HitResult;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;")
                        .modifyParams(builder -> builder.swap(0, 3).ignoreOffset())
                        .modifyParams(builder -> builder
                                .inline(3, adapter -> {
                                    adapter.load(1, Type.getType("Lnet/minecraft/world/level/block/state/BlockState;"));
                                    adapter.invokevirtual("net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase", "m_60734_", "()Lnet/minecraft/world/level/block/Block;", false);
                                })
                                .ignoreOffset())
                        .modifyParams(builder -> builder
                                .insert(1, Type.getType("Lnet/minecraft/world/phys/HitResult;"))
                                .insert(4, Type.getType("Lnet/minecraft/world/entity/player/Player;"))
                                .ignoreOffset())
                        .targetMixinType(MixinConstants.REDIRECT)
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
                        .targetMethod("m_9280_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/block/Block;m_5707_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/player/Player;)V")
                        .modifyTarget("removeBlock")
                        .modifyParams(builder -> builder.insert(1, Type.BOOLEAN_TYPE))
                        .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z")
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/server/level/ServerPlayerGameMode")
                        .targetMethod("m_9280_")
                        .targetInjectionPoint("Lnet/minecraft/server/level/ServerLevel;m_7471_(Lnet/minecraft/core/BlockPos;Z)Z")
                        .modifyInjectionPoint("Lnet/minecraft/server/level/ServerPlayerGameMode;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z")
                        .modifyParams(builder -> builder
                                .inline(0, adapter -> {
                                    adapter.load(0, Type.getType("Lnet/minecraft/server/level/ServerPlayerGameMode;"));
                                    adapter.getfield("net/minecraft/server/level/ServerPlayerGameMode", "f_9244_", "Lnet/minecraft/server/level/ServerLevel;");
                                })
                                .ignoreOffset())
                        // Since transformers (fortunately) obey order, we first inline and then add the parameter with a different type
                        .modifyParams(builder -> builder.insert(0, Type.getType("Lnet/minecraft/server/level/ServerPlayerGameMode;")).ignoreOffset())
                        .targetMixinType(MixinConstants.REDIRECT)
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/multiplayer/MultiPlayerGameMode")
                        .targetMethod("m_105267_")
                        .targetInjectionPoint("Lnet/minecraft/world/level/Level;m_7731_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z")
                        .modifyInjectionPoint("Lnet/minecraft/world/level/block/state/BlockState;onDestroyedByPlayer(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;ZLnet/minecraft/world/level/material/FluidState;)Z")
                        .modifyParams(builder -> builder
                                .inline(3, adapter -> adapter.visitIntInsn(Opcodes.BIPUSH, 11))
                                // Starts as Level | BlockPos | BlockState
                                .swap(2, 1) // Level | BlockState | BlockPos
                                .swap(1, 0) // BlockState | Level | BlockPos
                                .insert(4, Type.getType("Lnet/minecraft/world/entity/player/Player;"))
                                .insert(5, Type.BOOLEAN_TYPE)
                                .insert(6, Type.getType("Lnet/minecraft/world/level/material/FluidState;"))
                                .ignoreOffset())
                        .targetMixinType(MixinConstants.REDIRECT)
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/entity/layers/ElytraLayer")
                        .targetMethod("m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
                        .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;m_150930_(Lnet/minecraft/world/item/Item;)Z")
                        .targetMixinType(MixinConstants.REDIRECT)
                        .modifyParams(builder -> builder
                                .insert(0, Type.getObjectType("net/minecraft/client/renderer/entity/layers/ElytraLayer"))
                                .replace(2, Type.getObjectType("net/minecraft/world/entity/LivingEntity"))
                                .targetType(ModifyMethodParams.TargetType.INJECTION_POINT))
                        .divertRedirector(adapter -> {
                            adapter.visitVarInsn(Opcodes.ALOAD, 1);
                            adapter.visitVarInsn(Opcodes.ALOAD, 2);
                            adapter.visitVarInsn(Opcodes.ALOAD, 3);
                            adapter.invokevirtual("net/minecraft/client/renderer/entity/layers/ElytraLayer", "shouldRender", "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z", false);
                        })
                        .modifyInjectionPoint("Lnet/minecraft/client/renderer/entity/layers/ElytraLayer;shouldRender(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z")
                        .build(),
                // Disable potential duplicate attempts at making shaders IDs namespace aware - Forge already does this for us.
                // Attempts at doing so again will fail.
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/EffectInstance")
                        .targetMethod("<init>", "m_172566_")
                        .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
                        .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                        .targetMixinType(MixinConstants.REDIRECT)
                        .disable()
                        .build(),
                Patch.builder()
                        .targetClass("net/minecraft/client/renderer/PostChain")
                        .targetMethod("m_110030_")
                        .targetInjectionPoint("NEW", "net/minecraft/resources/ResourceLocation")
                        .targetInjectionPoint("NEW", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;")
                        .targetMixinType(MixinConstants.REDIRECT)
                        .disable()
                        .build(),
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
                        .targetMixinType(MixinConstants.MODIFY_ARG)
                        .targetAnnotationValues(values -> values.<Integer>getValue("index").map(handle -> handle.get() == 4).orElse(false))
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
                                } else if (insn instanceof MethodInsnNode minsn && minsn.name.equals("put") && minsn.desc.equals("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")) {
                                    minsn.desc = "(Lcom/mojang/blaze3d/platform/InputConstants$Key;Lnet/minecraft/client/KeyMapping;)V";
                                    minsn.itf = false;
                                    minsn.setOpcode(Opcodes.INVOKEVIRTUAL);
                                    methodNode.instructions.insert(minsn, new InsnNode(Opcodes.ACONST_NULL));
                                }
                            }
                            methodNode.desc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(methodNode.desc));
                            return Patch.Result.APPLY;
                        })
                        .build());

        return patches.stream()
                .flatMap(p -> p instanceof List<?> lst ? lst.stream() : Stream.of(p))
                .map(o -> (Patch) o)
                .toList();
    }
}
