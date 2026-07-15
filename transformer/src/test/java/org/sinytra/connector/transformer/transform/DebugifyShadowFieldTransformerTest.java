package org.sinytra.connector.transformer.transform;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.PatchResult;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugifyShadowFieldTransformerTest {
    @Test
    void widensDebugifyRangedBowGoalShadowToMob() {
        ClassNode node = new ClassNode();
        node.name = DebugifyShadowFieldTransformer.MIXIN;
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "mob", "L" + DebugifyShadowFieldTransformer.OLD_TYPE + ";", null, null));
        MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, "lookAtTarget", "()V", null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "mob", "L" + DebugifyShadowFieldTransformer.OLD_TYPE + ";"));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DebugifyShadowFieldTransformer.OLD_TYPE, "getTarget", "()Lnet/minecraft/world/entity/LivingEntity;", false));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(method);

        assertEquals(PatchResult.APPLY, new DebugifyShadowFieldTransformer().apply(node, null, null));
        assertEquals("L" + DebugifyShadowFieldTransformer.NEW_TYPE + ";", node.fields.getFirst().desc);
        assertEquals("L" + DebugifyShadowFieldTransformer.NEW_TYPE + ";", ((FieldInsnNode) method.instructions.getFirst()).desc);
        assertEquals(DebugifyShadowFieldTransformer.NEW_TYPE, ((MethodInsnNode) method.instructions.get(1)).owner);
    }

    @Test
    void leavesOtherMixinsUntouched() {
        ClassNode node = new ClassNode();
        node.name = "example/OtherMixin";
        assertEquals(PatchResult.PASS, new DebugifyShadowFieldTransformer().apply(node, null, null));
    }
}
