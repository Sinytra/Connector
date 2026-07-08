package org.sinytra.connector.service.coremod;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleMethodProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.connector.api.Constants;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ConnectorMethodProcessor extends SimpleMethodProcessor {
    public static final ProcessorName ID = new ProcessorName(Constants.CONNECTOR_MODID, "method_coremod");
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Target, Consumer<MethodNode>> SUBPROCESSORS;

    static {
        ImmutableMap.Builder<Target, Consumer<MethodNode>> builder = new Builder<>();

        builder.put(
            new Target("net.minecraft.client.gui.screens.inventory.EffectsInInventory", "extractRenderState", "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"),
            input -> {
                MethodInsnNode insn = findFirstMethodCall(input, Opcodes.INVOKEINTERFACE, "java/util/stream/Stream", "collect", "(Ljava/util/stream/Collector;)Ljava/lang/Object;");
                if (insn != null && insn.getNext() instanceof TypeInsnNode typeInsn) {
                    input.instructions.insert(typeInsn, listOf(
                        new MethodInsnNode(Opcodes.INVOKESTATIC, "com/google/common/collect/Ordering", "natural", "()Lcom/google/common/collect/Ordering;"),
                        new InsnNode(Opcodes.SWAP),
                        new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "com/google/common/collect/Ordering", "sortedCopy", "(Ljava/lang/Iterable;)Ljava/util/List;")
                    ));
                }
            }
        );

        builder.put(
            new Target("net.minecraft.world.entity.LivingEntity", "forceAddEffect", "(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)V"),
            input -> {
                LocalVariableLookup lvt = new LocalVariableLookup(input);
                LocalVariableNode mobEffect = lvt.getByTypedOrdinal(Type.getObjectType("net/minecraft/world/effect/MobEffectInstance"), 1).orElse(null);
                if (mobEffect != null) {
                    mobEffect.start = lvt.getByIndex(0).start;
                    input.instructions.insert(mobEffect.start, listOf(
                        new InsnNode(Opcodes.ACONST_NULL),
                        new VarInsnNode(Opcodes.ASTORE, mobEffect.index)
                    ));
                    LOGGER.debug("Expanded local variable scope for LivingEntity#forceAddEffect index {}", mobEffect.index);
                }
            }
        );

        Consumer<MethodNode> injectFabricASM = input -> {
            var insns = new InsnList();
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/coremod/FabricASMClassGenerator", "fishAddURL", "()Ljava/util/function/Consumer;"));
            insns.add(new InsnNode(Opcodes.ARETURN));
            input.instructions.insert(insns);
            LOGGER.debug("Injected fishAddURL hook into FabricASM plugin");
        };
        builder.put(new Target("com.chocohead.mm.Plugin", "fishAddURL", "()Ljava/util/function/Consumer;"), injectFabricASM);
        builder.put(new Target("me.shedaniel.mm.Plugin", "fishAddURL", "()Ljava/util/function/Consumer;"), injectFabricASM);

        Consumer<MethodNode> renameGeneratedMixinClassName = input -> {
            var insns = new InsnList();
            insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/coremod/FabricASMClassGenerator", "flattenMixinClass", "(Ljava/lang/String;)Ljava/lang/String;"));
            insns.add(new VarInsnNode(Opcodes.ASTORE, 1));
            input.instructions.insert(insns);
            LOGGER.debug("Injected flattenMixinClass modifier into FabricASM Plugin$1");
        };
        builder.put(new Target("com.chocohead.mm.Plugin$1", "generate", "()Ljava/util/function/Consumer;"), renameGeneratedMixinClassName);
        builder.put(new Target("com.chocohead.mm.Plugin$1", "generate", "(Ljava/lang/String;Ljava/util/Collection;)V"), renameGeneratedMixinClassName);
        builder.put(new Target("me.shedaniel.mm.Plugin$1", "generate", "(Ljava/lang/String;Ljava/util/Collection;)V"), renameGeneratedMixinClassName);

        Consumer<MethodNode> permitEnumSubclass = input -> {
            var insns = new InsnList();
            insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
            insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
            insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "org/sinytra/connector/service/coremod/FabricASMClassGenerator", "permitEnumSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Ljava/lang/String;)V"));
            input.instructions.insert(insns);
            LOGGER.debug("Injected permitEnumSubclass modifier into FabricASM EnumSubclasser");
        };
        builder.put(new Target("com.chocohead.mm.EnumSubclasser", "defineAnonymousSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Lcom/chocohead/mm/api/EnumAdder$EnumAddition;Ljava/lang/String;Ljava/lang/String;)[B"), permitEnumSubclass);
        builder.put(new Target("me.shedaniel.mm.EnumSubclasser", "defineAnonymousSubclass", "(Lorg/objectweb/asm/tree/ClassNode;Lcom/chocohead/mm/api/EnumAdder$EnumAddition;Ljava/lang/String;Ljava/lang/String;)[B"), permitEnumSubclass);

        SUBPROCESSORS = builder.build();
    }

    @Override
    public ProcessorName name() {
        return ID;
    }

    @Override
    public Set<Target> targets() {
        return SUBPROCESSORS.keySet();
    }

    @Override
    public void transform(MethodNode input, SimpleTransformationContext context) {
        Target target = new Target(context.type().getClassName(), input.name, input.desc);
        Consumer<MethodNode> processor = Objects.requireNonNull(SUBPROCESSORS.get(target));

        processor.accept(input);
    }

    private static InsnList listOf(AbstractInsnNode... nodes) {
        InsnList list = new InsnList();
        for (AbstractInsnNode node : nodes)
            list.add(node);
        return list;
    }

    private static MethodInsnNode findFirstMethodCall(MethodNode method, int opcode, String owner, String name, String descriptor) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode minsn && insn.getOpcode() == opcode) {
                if (minsn.owner.equals(owner) && minsn.name.equals(name) && minsn.desc.equals(descriptor)) {
                    return minsn;
                }
            }   
        }
        return null;
    }
}
