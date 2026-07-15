package org.sinytra.connector.transformer.transform;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.PatchContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.transform.ClassTransformer;

final class DebugifyShadowFieldTransformer implements ClassTransformer {
    static final String MIXIN = "dev/isxander/debugify/mixins/basic/mc121706/RangedBowAttackGoalMixin";
    static final String OLD_TYPE = "net/minecraft/world/entity/monster/Monster";
    static final String NEW_TYPE = "net/minecraft/world/entity/Mob";

    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        if (!MIXIN.equals(classNode.name)) {
            return PatchResult.PASS;
        }

        boolean applied = false;
        String oldDescriptor = 'L' + OLD_TYPE + ';';
        String newDescriptor = 'L' + NEW_TYPE + ';';
        for (FieldNode field : classNode.fields) {
            if (field.name.equals("mob") && field.desc.equals(oldDescriptor)) {
                field.desc = newDescriptor;
                applied = true;
            }
        }
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field
                    && field.owner.equals(classNode.name)
                    && field.name.equals("mob")
                    && field.desc.equals(oldDescriptor)) {
                    field.desc = newDescriptor;
                    applied = true;
                } else if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(OLD_TYPE)
                    && (call.name.equals("getLookControl") || call.name.equals("getTarget"))) {
                    call.owner = NEW_TYPE;
                    applied = true;
                }
            }
        }
        return applied ? PatchResult.APPLY : PatchResult.PASS;
    }
}
