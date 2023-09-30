package dev.su5ed.sinytra.connector.transformer.patch;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import net.minecraftforge.coremod.api.ASMAPI;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Function;

public class FieldTypeAdapter implements ClassTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<FieldTypeFixup> FIXUPS = List.of(
        new FieldTypeFixup("net/minecraft/world/level/storage/loot/LootTable", ASMAPI.mapField("f_79109_"), "Ljava/util/List;", patch -> {
            InsnList list = patch.loadShadowValue();
            list.add(new InsnNode(Opcodes.ICONST_0));
            list.add(new TypeInsnNode(Opcodes.ANEWARRAY, "net/minecraft/world/level/storage/loot/LootPool"));
            list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "toArray", "([Ljava/lang/Object;)[Ljava/lang/Object;", true));
            list.add(new TypeInsnNode(Opcodes.CHECKCAST, "[Lnet/minecraft/world/level/storage/loot/LootPool;"));
            return list;
        })
    );

    record FieldTypeFixup(String owner, String field, String newType, Function<PatcherCallback, InsnList> patcher) {
        public boolean matches(String owner, String field) {
            return this.owner.equals(owner) && this.field.equals(field);
        }

        public boolean apply(MethodNode method, FieldInsnNode fieldInsn) {
            if (matches(fieldInsn.owner, fieldInsn.name)) {
                LOGGER.info("Running fixup for field {}.{}{} in method {}{}", fieldInsn.owner, fieldInsn.name, fieldInsn.desc, method.name, method.desc);
                fieldInsn.desc = this.newType;
                method.instructions.insert(fieldInsn, this.patcher.apply(() -> {
                    InsnList list = new InsnList();
                    list.add(new FieldInsnNode(fieldInsn.getOpcode(), fieldInsn.owner, fieldInsn.name, fieldInsn.desc));
                    return list;
                }));
                method.instructions.remove(fieldInsn);
                return true;
            }
            return false;
        }
    }

    interface PatcherCallback {
        InsnList loadShadowValue();
    }

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchEnvironment environment) {
        boolean applied = false;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode finsn) {
                    for (FieldTypeFixup fixup : FIXUPS) {
                        applied |= fixup.apply(method, finsn);
                    }
                }
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }
}
