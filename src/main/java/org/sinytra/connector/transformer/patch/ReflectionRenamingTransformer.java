package org.sinytra.connector.transformer.patch;

import org.sinytra.connector.transformer.jar.IntermediateMapping;
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
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.Patch;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ReflectionRenamingTransformer implements ClassNodeTransformer.ClassProcessor {
    private final IMappingFile mappingFile;
    private final IntermediateMapping flatMappings;

    public ReflectionRenamingTransformer(IMappingFile mappingFile, IntermediateMapping flatMappings) {
        this.mappingFile = mappingFile;
        this.flatMappings = flatMappings;
    }

    @Override
    public Patch.Result process(ClassNode node) {
        boolean applied = false;
        for (MethodNode method : node.methods) {
            ReflectionRemapperInterpreter interpreter = new ReflectionRemapperInterpreter(Opcodes.ASM9, this.mappingFile, this.flatMappings);
            MethodCallAnalyzer.analyzeInterpretMethod(method, interpreter);
            applied |= interpreter.remapApplied();
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static class ReflectionRemapperInterpreter extends SourceInterpreter {
        private static final Type STR_TYPE = Type.getType(String.class);

        private final Collection<MethodInsnNode> seen = new HashSet<>();
        private final IMappingFile mappings;
        private final IntermediateMapping fastMappings;
        private boolean remapApplied = false;

        public ReflectionRemapperInterpreter(int api, IMappingFile mappings, IntermediateMapping fastMappings) {
            super(api);
            this.mappings = mappings;
            this.fastMappings = fastMappings;
        }

        public boolean remapApplied() {
            return this.remapApplied;
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            if (insn instanceof MethodInsnNode methodInsn && !this.seen.contains(methodInsn)) {
                this.seen.add(methodInsn);
                // Try to remap reflection method call args
                Type[] args = Type.getArgumentTypes(methodInsn.desc);
                if (args.length >= 3 && STR_TYPE.equals(args[0]) && STR_TYPE.equals(args[1]) && STR_TYPE.equals(args[2]) && values.size() >= 3) {
                    LdcInsnNode ownerInsn = getSingleLDCString(values.get(0));
                    LdcInsnNode nameInsn = getSingleLDCString(values.get(1));
                    LdcInsnNode descInsn = getSingleLDCString(values.get(2));
                    if (ownerInsn != null && nameInsn != null && descInsn != null) {
                        String owner = (String) ownerInsn.cst;
                        IMappingFile.IClass cls = this.mappings.getClass(owner.replace('.', '/'));
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
                                String mappedName = this.fastMappings.mapMethod(name, desc);
                                if (mappedName != null) {
                                    ownerInsn.cst = owner.contains(".") ? cls.getMapped().replace('/', '.') : cls.getMapped();
                                    nameInsn.cst = mappedName;
                                    descInsn.cst = this.mappings.remapDescriptor(desc);
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
