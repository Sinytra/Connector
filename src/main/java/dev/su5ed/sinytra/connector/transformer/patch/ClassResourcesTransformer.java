package dev.su5ed.sinytra.connector.transformer.patch;

import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ClassResourcesTransformer implements ClassTransform {

    record Replacement(MethodInsnNode methodInsn, AbstractInsnNode paramInsn) {}

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        boolean applied = false;
        for (MethodNode method : classNode.methods) {
            List<Replacement> replacements = new ArrayList<>();
            SourceInterpreter i = new ScanningSourceInterpreter(Opcodes.ASM9, replacements);
            Analyzer<SourceValue> analyzer = new Analyzer<>(i);

            try {
                analyzer.analyze(method.name, method);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }

            for (Replacement replacement : replacements) {
                method.instructions.insert(replacement.paramInsn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;"));
                replacement.methodInsn.owner = "java/lang/ClassLoader";
                applied = true;
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static class ScanningSourceInterpreter extends SourceInterpreter {
        private final List<Replacement> replacements;
        private final Collection<MethodInsnNode> seen = new HashSet<>();

        public ScanningSourceInterpreter(int api, List<Replacement> replacements) {
            super(api);
            this.replacements = replacements;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn && !this.seen.contains(methodInsn)
                && methodInsn.owner.equals("java/lang/Class")
                && methodInsn.name.equals("getResourceAsStream")
                && methodInsn.desc.equals("(Ljava/lang/String;)Ljava/io/InputStream;")
            ) {
                SourceValue value = values.get(0);
                if (value.insns.size() == 1) {
                    AbstractInsnNode sourceInsn = value.insns.iterator().next();
                    this.replacements.add(new Replacement(methodInsn, sourceInsn));
                    this.seen.add(methodInsn);
                }
                else {
                    throw new IllegalStateException("Got multiple source value insns: " + value.insns);
                }
            }
            return super.naryOperation(insn, values);
        }
    }
}
