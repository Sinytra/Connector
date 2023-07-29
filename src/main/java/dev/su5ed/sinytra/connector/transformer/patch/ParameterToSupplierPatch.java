package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ParameterToSupplierPatch implements ClassTransform {
    private static final Handle META_FACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    private final Map<MethodQualifier, Pair<MethodQualifier, Type>> patches = new HashMap<>();

    public ParameterToSupplierPatch add(MethodQualifier from, MethodQualifier to, Type suppliedType) {
        this.patches.put(from, Pair.of(to, suppliedType));
        return this;
    }

    record Replacement(MethodQualifier qualifier, Type suppliedType, MethodNode methodNode, MethodInsnNode source, AbstractInsnNode start, AbstractInsnNode end) {}

    record VarMapping(int from, VarInsnNode insn) {}

    @Override
    public Result apply(ClassNode node) {
        List<Replacement> replacements = new ArrayList<>();

        for (MethodNode method : node.methods) {
            SourceInterpreter i = new ScanningSourceInterpreter(Opcodes.ASM9, method, replacements);
            Analyzer<SourceValue> analyzer = new Analyzer<>(i);

            try {
                analyzer.analyze(node.name, method);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }
        }

        for (Replacement replacement : replacements) {
            replacement.source.owner = replacement.qualifier.owner();
            replacement.source.name = replacement.qualifier.name();
            replacement.source.desc = replacement.qualifier.desc();

            // Skip original insns
            LabelNode skip = new LabelNode();
            replacement.methodNode.instructions.insertBefore(replacement.start, new JumpInsnNode(Opcodes.GOTO, skip));
            replacement.methodNode.instructions.insert(replacement.end, skip);

            // Handle static method reference            
            if (replacement.start == replacement.end && replacement.start instanceof MethodInsnNode methodInsn && methodInsn.getOpcode() == Opcodes.INVOKESTATIC) {
                replacement.methodNode.instructions.insertBefore(replacement.source, new InvokeDynamicInsnNode(
                    "get", Type.getMethodDescriptor(Type.getType(Supplier.class)),
                    META_FACTORY,
                    Type.getMethodType(Type.getType(Object.class)),
                    new Handle(Opcodes.H_INVOKESTATIC, methodInsn.owner, methodInsn.name, methodInsn.desc, methodInsn.itf),
                    Type.getMethodType(replacement.suppliedType)
                ));
                continue;
            }

            Int2ObjectMap<VarMapping> lvtVars = new Int2ObjectLinkedOpenHashMap<>();
            String lambdaName = "lambda$" + replacement.methodNode.name + "$" + RandomStringUtils.randomAlphabetic(4);
            MethodNode lambdaMethod = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, lambdaName, null, null, null);
            node.methods.add(lambdaMethod);
            Label start = new Label();
            Label end = new Label();
            lambdaMethod.visitCode();
            lambdaMethod.visitLabel(start);
            for (AbstractInsnNode insn = replacement.start; insn != null; insn = insn.getNext()) {
                AbstractInsnNode clone = insn.clone(Map.of());
                if (clone instanceof VarInsnNode varInsnNode) {
                    VarMapping varMapping = lvtVars.get(varInsnNode.var);
                    if (varMapping == null) {
                        varMapping = new VarMapping(varInsnNode.var, varInsnNode);
                        lvtVars.put(varInsnNode.var, varMapping);
                    }
                }
                if (clone != null) lambdaMethod.instructions.add(clone);

                if (insn == replacement.end) {
                    break;
                }
            }
            lambdaMethod.visitInsn(Opcodes.ARETURN);
            lambdaMethod.visitLabel(end);
            List<Type> params = new ArrayList<>();
            boolean makeStatic = !lvtVars.containsKey(0);
            if (makeStatic) {
                lambdaMethod.access |= Opcodes.ACC_STATIC;
            }
            // Visit LVT
            int lvtIndex = 0;
            for (VarMapping lvtVar : lvtVars.values()) {
                LocalVariableNode local = replacement.methodNode.localVariables.stream().filter(lvNode -> lvNode.index == lvtVar.from).findFirst().orElseThrow();
                int to = makeStatic ? lvtIndex++ : lvtVar.from == 0 ? 0 : ++lvtIndex;
                lvtVar.insn.var = to;
                lambdaMethod.visitLocalVariable(local.name, local.desc, local.signature, start, end, to);
                params.add(Type.getType(local.desc));
                replacement.methodNode.instructions.insertBefore(replacement.source, new VarInsnNode(lvtVar.insn.getOpcode(), lvtVar.from));
            }
            lambdaMethod.visitEnd();

            Type[] paramTypes = params.toArray(Type[]::new);
            lambdaMethod.desc = Type.getMethodDescriptor(replacement.suppliedType, makeStatic ? paramTypes : params.subList(1, params.size()).toArray(Type[]::new));
            replacement.methodNode.instructions.insertBefore(replacement.source, new InvokeDynamicInsnNode(
                "get", Type.getMethodDescriptor(Type.getType(Supplier.class), paramTypes),
                META_FACTORY,
                Type.getMethodType(Type.getType(Object.class)),
                new Handle(makeStatic ? Opcodes.H_INVOKESTATIC : Opcodes.H_INVOKEVIRTUAL, node.name, lambdaName, lambdaMethod.desc, false),
                Type.getMethodType(replacement.suppliedType)
            ));
        }
        return new Result(!replacements.isEmpty(), !replacements.isEmpty());
    }

    public class ScanningSourceInterpreter extends SourceInterpreter {
        private final MethodNode methodNode;
        private final Collection<MethodInsnNode> seen = new HashSet<>();
        private final List<Replacement> replacements;

        public ScanningSourceInterpreter(int api, MethodNode methodNode, List<Replacement> replacements) {
            super(api);
            this.methodNode = methodNode;
            this.replacements = replacements;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn && !this.seen.contains(methodInsn)) {
                Pair<MethodQualifier, Type> pair = ParameterToSupplierPatch.this.patches.get(new MethodQualifier(methodInsn.owner, methodInsn.name, methodInsn.desc));
                if (pair != null) {
                    for (int i = 0; i < values.size(); i++) {
                        SourceValue value = values.get(i);
                        if (value.insns.size() == 1) {
                            // TODO Add ability to patch first parameter
                            if (i > 0) {
                                AbstractInsnNode previous = values.get(i - 1).insns.iterator().next();
                                this.replacements.add(new Replacement(pair.getFirst(), pair.getSecond(), this.methodNode, methodInsn, previous.getNext(), value.insns.iterator().next()));
                                this.seen.add(methodInsn);
                            }
                        }
                        else {
                            throw new IllegalStateException("Got multiple source value insns: " + value.insns);
                        }
                    }
                }
            }
            return super.naryOperation(insn, values);
        }
    }
}
