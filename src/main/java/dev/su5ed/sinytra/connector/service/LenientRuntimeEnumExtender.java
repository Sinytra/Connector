package dev.su5ed.sinytra.connector.service;

import net.minecraftforge.fml.common.asm.RuntimeEnumExtender;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.List;

public class LenientRuntimeEnumExtender extends RuntimeEnumExtender {
    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if ((classNode.access & Opcodes.ACC_ENUM) == 0)
            return ComputeFlags.NO_REWRITE;
        // Modified query flags that do not include ACC_FINAL 
        int flags = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        Type array = Type.getType("[" + classType.getDescriptor());
        List<FieldNode> values = classNode.fields.stream().filter(f -> f.desc.contentEquals(array.getDescriptor()) && (f.access & flags) == flags)
            .toList();
        if (values.size() == 1) {
            FieldNode node = values.get(0);
            if ((node.access & Opcodes.ACC_FINAL) == 0) {
                // It is likely a mixin already made the field mutable
                // Make it final before it is processed and de-finalized again by super
                node.access |= Opcodes.ACC_FINAL;
                return super.processClassWithFlags(phase, classNode, classType, reason);
            }
        }
        // Let super deal with this
        return super.processClassWithFlags(phase, classNode, classType, reason);
    }
}
