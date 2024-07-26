package org.sinytra.connector;

import com.google.common.collect.ImmutableList;
import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.coremod.api.ASMAPI;
import net.neoforged.neoforgespi.coremod.ICoreMod;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class ConnectorCoremods implements ICoreMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Iterable<? extends ITransformer<?>> getTransformers() {
        ITransformer<ClassNode> keyMappingFieldTypeTransform = new BaseTransformer<>(
            TargetType.CLASS,
            ITransformer.Target.targetClass("net.minecraft.client.KeyMapping"),
            input -> {
                FieldNode field = input.fields.stream().filter(f -> f.name.equals("CATEGORY_SORT_ORDER")).findFirst().orElse(null);
                if (field != null) {
                    int index = input.fields.indexOf(field);
                    // Add the field before KeyMapping#CATEGORY_SORT_ORDER which is the 3rd map
                    // See https://github.com/Sinytra/Connector/issues/723
                    input.fields.add(index, new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "MAP", "Ljava/util/Map;", null, null));

                    LOGGER.debug("Added field for KeyMapping#MAP at index {}", index + 1);
                }
            }
        );
        ITransformer<ClassNode> creativeModeTabConstructorTransform = new BaseTransformer<>(
            TargetType.CLASS,
            ITransformer.Target.targetClass("net.minecraft.world.item.CreativeModeTab"),
            input -> {
                MethodVisitor method = input.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;)V", null, null);
                method.visitCode();

                method.visitVarInsn(Opcodes.ALOAD, 0);
                // row
                method.visitVarInsn(Opcodes.ALOAD, 1);
                // column
                method.visitVarInsn(Opcodes.ILOAD, 2);
                // type
                method.visitVarInsn(Opcodes.ALOAD, 3);
                // displayName
                method.visitVarInsn(Opcodes.ALOAD, 4);
                // iconGenerator
                method.visitVarInsn(Opcodes.ALOAD, 5);
                // displayItemsGenerator
                method.visitVarInsn(Opcodes.ALOAD, 6);
                // backgroundLocation
                method.visitTypeInsn(Opcodes.NEW, "net/minecraft/resources/ResourceLocation");
                method.visitInsn(Opcodes.DUP);
                method.visitLdcInsn("textures/gui/container/creative_inventory/tab_items.png");
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/resources/ResourceLocation", "<init>", "(Ljava/lang/String;)V", false);
                // hasSearchBar
                method.visitInsn(Opcodes.ICONST_0);
                // searchBarWidth
                method.visitLdcInsn(89);
                method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/CreativeModeTab$Builder", "CREATIVE_INVENTORY_TABS_IMAGE", "Lnet/minecraft/resources/ResourceLocation;"); // tabsImage
                // labelColor
                method.visitLdcInsn(4210752);
                // slotColor
                method.visitLdcInsn(-2130706433);
                // tabsBefore
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                method.visitInsn(Opcodes.DUP);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                // tabsAfter
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                method.visitInsn(Opcodes.DUP);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                // invoke ctr
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/item/CreativeModeTab", "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;Lnet/minecraft/resources/ResourceLocation;ZILnet/minecraft/resources/ResourceLocation;IILjava/util/List;Ljava/util/List;)V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitEnd();

                LOGGER.debug("Injected vanilla CreativeModeTab constructor");
            }
        );
        List<ITransformer<?>> addedFields = List.of(
            addFieldToClass("net.minecraft.client.particle.ParticleEngine", "providers", "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;", Opcodes.ACC_PRIVATE),
            addFieldToClass("net.minecraft.client.color.block.BlockColors", "blockColors", "Lnet/minecraft/core/IdMapper;", Opcodes.ACC_PRIVATE),
            addFieldToClass("net.minecraft.client.color.item.ItemColors", "itemColors", "Lnet/minecraft/core/IdMapper;", Opcodes.ACC_PRIVATE)
        );
        ITransformer<?> missingOrderingCall = new BaseTransformer<>(
            TargetType.METHOD,
            ITransformer.Target.targetMethod("net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen", "renderEffects", "(Lnet/minecraft/client/gui/GuiGraphics;II)V"),
            input -> {
                MethodInsnNode insn = ASMAPI.findFirstMethodCall(input, ASMAPI.MethodType.INTERFACE, "java/util/stream/Stream", "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;");
                if (insn != null && insn.getNext() instanceof TypeInsnNode typeInsn) {
                    input.instructions.insert(typeInsn, ASMAPI.listOf(
                        new MethodInsnNode(Opcodes.INVOKESTATIC, "com/google/common/collect/Ordering", "natural", "()Lcom/google/common/collect/Ordering;"),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/google/common/collect/Ordering", "sortedCopy", "(Ljava/lang/Iterable;)Ljava/util/List;")
                    ));
                }
            }
        );

        return ImmutableList.<ITransformer<?>>builder()
            .add(keyMappingFieldTypeTransform, creativeModeTabConstructorTransform)
            .addAll(addedFields)
            .addAll(getFabricASMTransformers())
            .add(missingOrderingCall)
            .build();
    }

    private static ITransformer<?> addFieldToClass(String cls, String name, String desc, int access) {
        return new BaseTransformer<>(
            TargetType.CLASS,
            ITransformer.Target.targetClass(cls),
            input -> {
                input.fields.add(new FieldNode(access, name, desc, null, null));
                
                LOGGER.debug("Added field {} to class {}", name, cls);
            }
        );
    }

    private static List<ITransformer<?>> getFabricASMTransformers() {
        ITransformer<MethodNode> injectFabricASM = new BaseTransformer<>(
            TargetType.METHOD,
            Set.of(
                ITransformer.Target.targetMethod("com.chocohead.mm.Plugin", "fishAddURL", "()Ljava/util/function/Consumer;"),
                ITransformer.Target.targetMethod("me.shedaniel.mm.Plugin", "fishAddURL", "()Ljava/util/function/Consumer;")
            ),
            input -> {
                var insns = new InsnList();
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/hacks/FabricASMFixer", "fishAddURL", "()Ljava/util/function/Consumer;"));
                insns.add(new InsnNode(Opcodes.ARETURN));
                input.instructions.insert(insns);
                LOGGER.debug("Injected fishAddURL hook into FabricASM plugin");
            }
        );
        ITransformer<MethodNode> renameGeneratedMixinClassName = new BaseTransformer<>(
            TargetType.METHOD,
            Set.of(
                ITransformer.Target.targetMethod("com.chocohead.mm.Plugin$1", "generate", "()Ljava/util/function/Consumer;"),
                ITransformer.Target.targetMethod("me.shedaniel.mm.Plugin$1", "generate", "(Ljava/lang/String;Ljava/util/Collection;)V")
            ),
            input -> {
                var insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/hacks/FabricASMFixer", "flattenMixinClass", "(Ljava/lang/String;)Ljava/lang/String;"));
                insns.add(new VarInsnNode(Opcodes.ASTORE, 1));
                input.instructions.insert(insns);
                LOGGER.debug("Injected flattenMixinClass modifier into FabricASM Plugin$1");
            }
        );
        ITransformer<MethodNode> permitEnumSubclass = new BaseTransformer<>(
            TargetType.METHOD,
            Set.of(
                ITransformer.Target.targetMethod("com.chocohead.mm.EnumSubclasser", "defineAnonymousSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Lcom/chocohead/mm/api/EnumAdder$EnumAddition;Ljava/lang/String;Ljava/lang/String;)[B"),
                ITransformer.Target.targetMethod("me.shedaniel.mm.EnumSubclasser", "defineAnonymousSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Lcom/chocohead/mm/api/EnumAdder$EnumAddition;Ljava/lang/String;Ljava/lang/String;)[B")
            ),
            input -> {
                var insns = new InsnList();
                insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
                insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/hacks/FabricASMFixer", "permitEnumSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;)V"));
                input.instructions.insert(insns);
                LOGGER.debug("Injected permitEnumSubclass modifier into FabricASM EnumSubclasser");
            }
        );

        return List.of(injectFabricASM, renameGeneratedMixinClassName, permitEnumSubclass);
    }

    private record BaseTransformer<T>(TargetType<T> type, Set<ITransformer.Target<T>> targets, Consumer<T> transform) implements ITransformer<T> {
        public BaseTransformer(TargetType<T> type, ITransformer.Target<T> target, Consumer<T> transform) {
            this(type, Set.of(target), transform);
        }

        @Override
        public T transform(T input, ITransformerVotingContext context) {
            this.transform.accept(input);
            return input;
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public TargetType<T> getTargetType() {
            return this.type;
        }
    }
}
