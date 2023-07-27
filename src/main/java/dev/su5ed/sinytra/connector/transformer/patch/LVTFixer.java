package dev.su5ed.sinytra.connector.transformer.patch;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public interface LVTFixer {
    void accept(int index, AbstractInsnNode insn, InsnList list);
}
