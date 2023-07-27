package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.datafixers.util.Pair;
import org.apache.commons.lang3.RandomStringUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParameterToSupplierPatch implements ClassTransform {
    private static final Handle META_FACTORY = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;", false);

    private final Map<MethodQualifier, Pair<MethodQualifier, Type>> patches = new HashMap<>();

    public ParameterToSupplierPatch add(MethodQualifier from, MethodQualifier to, Type suppliedType) {
        this.patches.put(from, Pair.of(to, suppliedType));
        return this;
    }

    record Replacement(MethodQualifier qualifier, Type suppliedType, MethodNode methodNode, MethodInsnNode source, int start, int end) {}

    @Override
    public boolean apply(ClassNode node) {
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
            String lambdaName = "lambda$" + replacement.methodNode.name + "$" + RandomStringUtils.randomAlphabetic(4);
            String lambdaDesc = Type.getMethodDescriptor(replacement.suppliedType);

            boolean makeStatic = true;
            MethodNode lambdaMethod = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, lambdaName, lambdaDesc, null, null);
            node.methods.add(lambdaMethod);
            lambdaMethod.visitCode();
            for (int i = 0; i <= replacement.end - replacement.start; i++) {
                AbstractInsnNode insn = replacement.methodNode.instructions.get(replacement.start);
                if (insn instanceof VarInsnNode varInsnNode) {
                    if (varInsnNode.var == 0) {
                        makeStatic = false;
                    }
                    else {
                        throw new UnsupportedOperationException("Capturing local variables is not supported");
                    }
                }
                replacement.methodNode.instructions.remove(insn);
                lambdaMethod.instructions.add(insn);
            }
            lambdaMethod.visitInsn(Opcodes.ARETURN);
            lambdaMethod.visitEnd();
            if (makeStatic) {
                lambdaMethod.access |= Opcodes.ACC_STATIC;
            }
            else {
                lambdaMethod.instructions.insert(new VarInsnNode(Opcodes.ALOAD, 0));
            }
            replacement.methodNode.instructions.insertBefore(replacement.source, new InvokeDynamicInsnNode(
                "get", "()Ljava/util/function/Supplier;",
                META_FACTORY,
                Type.getType("()Ljava/lang/Object;"), new Handle(Opcodes.H_INVOKESTATIC, node.name, lambdaName, lambdaDesc, false), Type.getType(lambdaDesc)
            ));
        }
        return !replacements.isEmpty();
    }

    public class ScanningSourceInterpreter extends SourceInterpreter {
        private final MethodNode methodNode;
        private final List<Replacement> replacements;

        public ScanningSourceInterpreter(int api, MethodNode methodNode, List<Replacement> replacements) {
            super(api);
            this.methodNode = methodNode;
            this.replacements = replacements;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn) {
                Pair<MethodQualifier, Type> pair = ParameterToSupplierPatch.this.patches.get(new MethodQualifier(methodInsn.owner, methodInsn.name, methodInsn.desc));
                if (pair != null) {
                    for (int i = 0; i < values.size(); i++) {
                        SourceValue value = values.get(i);
                        if (value.insns.size() == 1) {
                            // TODO Add ability to patch first parameter
                            if (i > 0) {
                                AbstractInsnNode previous = values.get(i - 1).insns.iterator().next();
                                int start = this.methodNode.instructions.indexOf(previous.getNext());
                                int end = this.methodNode.instructions.indexOf(value.insns.iterator().next());
                                this.replacements.add(new Replacement(pair.getFirst(), pair.getSecond(), this.methodNode, methodInsn, start, end));
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
