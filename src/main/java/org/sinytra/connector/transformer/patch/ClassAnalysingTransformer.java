package org.sinytra.connector.transformer.patch;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Map;

public class ClassAnalysingTransformer implements ClassNodeTransformer.ClassProcessor {
    private static final Map<MethodQualifier, MethodQualifier> REPLACEMENTS = Map.of(
        new MethodQualifier("Ljava/lang/Class;", "getResourceAsStream", "(Ljava/lang/String;)Ljava/io/InputStream;"),
        new MethodQualifier("org/sinytra/connector/mod/ConnectorMod", "getModResourceAsStream", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;"),

        new MethodQualifier("Lcom/electronwill/nightconfig/core/file/FileConfigBuilder;", "defaultResource", "(Ljava/lang/String;)Lcom/electronwill/nightconfig/core/file/GenericBuilder;"),
        new MethodQualifier("org/sinytra/connector/mod/ConnectorMod", "useModConfigResource", "(Lcom/electronwill/nightconfig/core/file/FileConfigBuilder;Ljava/lang/String;)Lcom/electronwill/nightconfig/core/file/GenericBuilder;")
    );

    @Override
    public Patch.Result process(ClassNode node) {
        boolean applied = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    for (Map.Entry<MethodQualifier, MethodQualifier> entry : REPLACEMENTS.entrySet()) {
                        if (entry.getKey().matches(minsn)) {
                            MethodQualifier replacement = entry.getValue();
                            method.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, replacement.owner(), replacement.name(), replacement.desc(), false));
                            applied = true;
                        }
                    }
                }
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }
}
