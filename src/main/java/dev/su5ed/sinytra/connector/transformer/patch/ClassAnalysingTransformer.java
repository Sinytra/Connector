package dev.su5ed.sinytra.connector.transformer.patch;

import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.connector.transformer.jar.IntermediateMapping;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
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

public class ClassAnalysingTransformer implements ClassNodeTransformer.ClassProcessor {
    private final IMappingFile mappings;
    private final IntermediateMapping fastMappings;

    public ClassAnalysingTransformer(IMappingFile mappings, IntermediateMapping fastMappings) {
        this.mappings = mappings;
        this.fastMappings = fastMappings;
    }

    record Replacement(MethodInsnNode methodInsn, AbstractInsnNode paramInsn) {}

    @Override
    public Patch.Result process(ClassNode node) {
        boolean applied = false;
        for (MethodNode method : node.methods) {
            List<Replacement> replacements = new ArrayList<>();
            ScanningSourceInterpreter i = new ScanningSourceInterpreter(Opcodes.ASM9, replacements);
            Analyzer<SourceValue> analyzer = new Analyzer<>(i);

            try {
                analyzer.analyze(method.name, method);
            } catch (AnalyzerException e) {
                throw new RuntimeException(e);
            }

            applied |= i.remapApplied();
            for (Replacement replacement : replacements) {
                method.instructions.insert(replacement.paramInsn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;"));
                replacement.methodInsn.owner = "java/lang/ClassLoader";
                applied = true;
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private class ScanningSourceInterpreter extends SourceInterpreter {
        private static final Type STR_TYPE = Type.getType(String.class);
        private final List<Replacement> replacements;
        private final Collection<MethodInsnNode> seen = new HashSet<>();
        private boolean remapApplied = false;

        public ScanningSourceInterpreter(int api, List<Replacement> replacements) {
            super(api);
            this.replacements = replacements;
        }

        public boolean remapApplied() {
            return this.remapApplied;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn && !this.seen.contains(methodInsn)) {
                if (methodInsn.owner.equals("java/lang/Class") && methodInsn.name.equals("getResourceAsStream") && methodInsn.desc.equals("(Ljava/lang/String;)Ljava/io/InputStream;")) {
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
                // Try to remap reflection method call args
                Type[] args = Type.getArgumentTypes(methodInsn.desc);
                if (args.length >= 3 && STR_TYPE.equals(args[0]) && STR_TYPE.equals(args[1]) && STR_TYPE.equals(args[2]) && values.size() >= 3) {
                    LdcInsnNode ownerInsn = getSingleLDCString(values.get(0));
                    LdcInsnNode nameInsn = getSingleLDCString(values.get(1));
                    LdcInsnNode descInsn = getSingleLDCString(values.get(2));
                    if (ownerInsn != null && nameInsn != null && descInsn != null) {
                        String owner = (String) ownerInsn.cst;
                        IMappingFile.IClass cls = ClassAnalysingTransformer.this.mappings.getClass(owner.replace('.', '/'));
                        if (cls != null) {
                            String name = (String) nameInsn.cst;
                            String desc = (String) descInsn.cst;
                            IMappingFile.IMethod mtd = cls.getMethod(name, desc);
                            if (mtd != null) {
                                ownerInsn.cst = owner.contains(".") ? cls.getMapped().replace('/', '.') : cls.getMapped();
                                nameInsn.cst = mtd.getMapped();
                                descInsn.cst = mtd.getMappedDescriptor();
                                this.remapApplied = true;
                            }
                            else {
                                String mappedName = ClassAnalysingTransformer.this.fastMappings.mapMethod(name, desc);
                                if (mappedName != null) {
                                    ownerInsn.cst = owner.contains(".") ? cls.getMapped().replace('/', '.') : cls.getMapped();
                                    nameInsn.cst = mappedName;
                                    descInsn.cst = ClassAnalysingTransformer.this.mappings.remapDescriptor(desc);
                                    this.remapApplied = true;
                                }
                            }
                        }
                    }
                }
            }
            return super.naryOperation(insn, values);
        }

        @Nullable
        private static LdcInsnNode getSingleLDCString(SourceValue value) {
            return value.insns.size() == 1 && value.insns.iterator().next() instanceof LdcInsnNode ldc && ldc.cst instanceof String ? ldc : null;
        }
    }
}
