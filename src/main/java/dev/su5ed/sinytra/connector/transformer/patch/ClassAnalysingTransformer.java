package dev.su5ed.sinytra.connector.transformer.patch;

import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.util.MethodQualifier;
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
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ClassAnalysingTransformer implements ClassNodeTransformer.ClassProcessor {
    private static final MethodQualifier GET_RESOURCE_AS_STREAM = new MethodQualifier("Ljava/lang/Class;", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;");

    private final IMappingFile mappings;
    private final IntermediateMapping fastMappings;

    public ClassAnalysingTransformer(IMappingFile mappings, IntermediateMapping fastMappings) {
        this.mappings = mappings;
        this.fastMappings = fastMappings;
    }

    @Override
    public Patch.Result process(ClassNode node) {
        boolean applied = false;
        for (MethodNode method : node.methods) {
            ScanningSourceInterpreter i = MethodCallAnalyzer.analyzeInterpretMethod(method, new ScanningSourceInterpreter(Opcodes.ASM9));
            applied |= i.remapApplied();

            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode minsn && GET_RESOURCE_AS_STREAM.matches(minsn)) {
                    method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, "dev/su5ed/sinytra/connector/mod/ConnectorMod", "getModResourceAsStream", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;", false));
                    applied = true;
                }
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private class ScanningSourceInterpreter extends SourceInterpreter {
        private static final Type STR_TYPE = Type.getType(String.class);
        private final Collection<MethodInsnNode> seen = new HashSet<>();
        private boolean remapApplied = false;

        public ScanningSourceInterpreter(int api) {
            super(api);
        }

        public boolean remapApplied() {
            return this.remapApplied;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn && !this.seen.contains(methodInsn)) {
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
