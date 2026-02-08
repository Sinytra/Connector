package org.sinytra.connector.transformer.transform;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.connector.transformer.patch.AccessorToInvokerTransformer;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessorRedirectTransformer implements ClassNodeTransformer.ClassProcessor {
    private static final String PREFIX = "connector$redirect$";

    private final Map<String, Map<String, String>> methodRenames = new HashMap<>();
    private final List<? extends MethodPatch> patches = FieldToMethodTransformer.REPLACEMENTS.entrySet().stream()
        .flatMap(entry -> entry.getValue().values().stream()
            .map(s -> MethodPatch.builder()
                .targetClass(entry.getKey().replace('.', '/'))
                .targetField(s)
                .transform(new AccessorToInvokerTransformer(s))
                .transform((context, configuration) -> {
                    ClassNode classNode = context.classNode();
                    MethodNode methodNode = context.methodNode();

                    // Add prefix to invoker
                    String newName = PREFIX + methodNode.name;
                    this.methodRenames.computeIfAbsent(classNode.name, a -> new HashMap<>())
                        .put(methodNode.name + methodNode.desc, newName);
                    methodNode.name = newName;

                    return PatchResult.APPLY;
                })
                .build()))
        .toList();

    public List<? extends MethodPatch> getPatches() {
        return this.patches;
    }

    @Override
    public PatchResult process(ClassNode node) {
        boolean applied = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    Map<String, String> renames = this.methodRenames.get(minsn.owner);
                    if (renames != null) {
                        String newName = renames.get(minsn.name + minsn.desc);
                        if (newName != null) {
                            minsn.name = newName;
                            applied = true;
                        }
                    }
                }
            }
        }
        return applied ? PatchResult.APPLY : PatchResult.PASS;
    }
}
