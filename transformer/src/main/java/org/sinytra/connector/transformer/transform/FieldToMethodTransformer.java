package org.sinytra.connector.transformer.transform;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.classtweaker.visitors.ForwardingVisitor;
import net.neoforged.art.api.Transformer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.slf4j.Logger;

import java.util.ListIterator;
import java.util.Map;
import java.util.function.BiPredicate;

public class FieldToMethodTransformer implements ClassNodeTransformer.ClassProcessor {
    public static final Map<String, Map<String, String>> REPLACEMENTS = ImmutableMap.<String, Map<String, String>>builder()
        // Extracted from net.neoforged.neoforge.coremods.NeoForgeCoreMod
        .put("net/minecraft/world/level/biome/Biome", Map.of(
            "climateSettings", "getModifiedClimateSettings",
            "specialEffects", "getModifiedSpecialEffects"
        ))
        .put("net/minecraft/world/level/levelgen/structure/Structure", Map.of(
            "settings", "getModifiedStructureSettings"
        ))
        .put("net/minecraft/world/level/block/FlowerPotBlock", Map.of(
            "potted", "getPotted"
        ))
        .buildOrThrow();

    private static final Logger LOGGER = LogUtils.getLogger();
    private final String accessWidenerResource;

    public FieldToMethodTransformer(String accessWidenerResource) {
        this.accessWidenerResource = accessWidenerResource;
    }

    @Override
    public PatchResult process(ClassNode node) {
        return processClass(node) ? PatchResult.APPLY : PatchResult.PASS;
    }

    @Override
    public Transformer.ResourceEntry process(Transformer.ResourceEntry entry) {
        if (entry.getName().equals(this.accessWidenerResource)) {
            BiPredicate<String, String> allowField = (owner, name) -> getReplacementMethodName(owner, name) == null;

            ClassTweakerWriter writer = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);
            ClassTweakerVisitor filter = new FilteringClassTweakerVisitor(allowField, writer);
            ClassTweakerReader.create(filter).read(entry.getData(), "official");

            return Transformer.ResourceEntry.create(entry.getName(), entry.getTime(), writer.getOutput());
        }
        return entry;
    }

    @Nullable
    private static String getReplacementMethodName(String owner, String field) {
        Map<String, String> replacements = REPLACEMENTS.get(owner);
        return replacements != null ? replacements.get(field) : null;
    }

    private boolean processClass(ClassNode cls) {
        boolean replaced = false;
        for (MethodNode method : cls.methods) {
            for (ListIterator<AbstractInsnNode> iterator = method.instructions.iterator(); iterator.hasNext(); ) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof FieldInsnNode fieldInsn
                    && (fieldInsn.getOpcode() == Opcodes.GETFIELD || fieldInsn.getOpcode() == Opcodes.GETSTATIC)
                ) {
                    String methodName = getReplacementMethodName(fieldInsn.owner, fieldInsn.name);
                    if (methodName != null) {
                        LOGGER.trace("Replacing field getter {} to method {} in {}#{}", fieldInsn.name, methodName, cls.name, method.name);
                        iterator.remove();
                        String getterDesc = "()" + fieldInsn.desc;
                        MethodInsnNode getterCall = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fieldInsn.owner, methodName, getterDesc, false);
                        iterator.add(getterCall);
                        replaced = true;
                    }
                }
            }
        }
        return replaced;
    }

    private static class FilteringClassTweakerVisitor extends ForwardingVisitor {
        private final BiPredicate<String, String> fieldFilter;

        public FilteringClassTweakerVisitor(BiPredicate<String, String> fieldFilter, ClassTweakerVisitor... visitors) {
            super(visitors);
            this.fieldFilter = fieldFilter;
        }

        @Override
        public @Nullable AccessWidenerVisitor visitAccessWidener(String owner) {
            AccessWidenerVisitor parent = super.visitAccessWidener(owner);
            return new ForwardingAccessWidenerVisitor(owner, parent, this.fieldFilter);
        }
    }

    private static class ForwardingAccessWidenerVisitor implements AccessWidenerVisitor {
        private final String owner;
        private final AccessWidenerVisitor parent;
        private final BiPredicate<String, String> fieldFilter;

        private ForwardingAccessWidenerVisitor(String owner, AccessWidenerVisitor parent, BiPredicate<String, String> fieldFilter) {
            this.owner = owner;
            this.parent = parent;
            this.fieldFilter = fieldFilter;
        }

        @Override
        public void visitClass(AccessType access, boolean transitive) {
            this.parent.visitClass(access, transitive);
        }

        @Override
        public void visitMethod(String name, String descriptor, AccessType access, boolean transitive) {
            this.parent.visitMethod(name, descriptor, access, transitive);
        }

        @Override
        public void visitField(String name, String descriptor, AccessType access, boolean transitive) {
            if (this.fieldFilter.test(this.owner, name)) {
                this.parent.visitField(name, descriptor, access, transitive);
            }
        }
    }
}
