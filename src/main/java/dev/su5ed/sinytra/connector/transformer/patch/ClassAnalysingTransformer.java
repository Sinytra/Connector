package dev.su5ed.sinytra.connector.transformer.patch;

import net.minecraftforge.coremod.api.ASMAPI;
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
        new MethodQualifier("dev/su5ed/sinytra/connector/mod/ConnectorMod", "getModResourceAsStream", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/io/InputStream;"),

        new MethodQualifier("Lnet/minecraft/world/level/storage/loot/LootDataType;", ASMAPI.mapMethod("m_278763_"), "(Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonElement;)Ljava/util/Optional;"),
        new MethodQualifier("dev/su5ed/sinytra/connector/mod/ConnectorMod", "deserializeLootTable", "(Lnet/minecraft/world/level/storage/loot/LootDataType;Lnet/minecraft/resources/ResourceLocation;Lcom/google/gson/JsonElement;)Ljava/util/Optional;")
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
