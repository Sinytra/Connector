package org.sinytra.connector.transformer.transform;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import net.fabricmc.classtweaker.api.ClassTweaker;
import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.ClassTweakerWriter;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.classtweaker.visitors.ForwardingVisitor;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

public class FieldToMethodTransformer implements ClassNodeTransformer.ClassProcessor {
    public static final Map<String, Map<String, String>> REPLACEMENTS = ImmutableMap.<String, Map<String, String>>builder()
        // Extracted from forge's coremods/field_to_method.js
        .put("net.minecraft.world.level.biome.Biome", Map.of(
            "climateSettings", "getModifiedClimateSettings",
            "specialEffects", "getModifiedSpecialEffects"
        ))
        .put("net.minecraft.world.level.levelgen.structure.Structure", Map.of(
            "settings", "getModifiedStructureSettings"
        ))
        .put("net.minecraft.world.level.block.FlowerPotBlock", Map.of(
            "potted", "getPotted"
        ))
        .buildOrThrow();

    private static final Logger LOGGER = LogUtils.getLogger();
    private final String accessWidenerResource;
    private final Map<String, String> mappedReplacements;

    public FieldToMethodTransformer(String accessWidenerResource, IMappingFile mappings) {
        this.accessWidenerResource = accessWidenerResource;
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        REPLACEMENTS.forEach((cls, replacements) -> {
            IMappingFile.IClass classMap = Objects.requireNonNull(mappings.getClass(cls.replace('.', '/')));
            replacements.forEach((field, getter) -> builder.put(classMap.remapField(field), getter));
        });
        this.mappedReplacements = builder.build();
    }

    @Override
    public PatchResult process(ClassNode node) {
        return processClass(node) ? PatchResult.APPLY : PatchResult.PASS;
    }

    @Override
    public Transformer.ResourceEntry process(Transformer.ResourceEntry entry) {
        if (entry.getName().equals(this.accessWidenerResource)) {
            ClassTweakerWriter writer = ClassTweakerWriter.create(ClassTweaker.CT_LATEST);

            ClassTweakerVisitor visitor = new FilteringClassTweakerVisitor(this.mappedReplacements.keySet(), writer);
            ClassTweakerReader.create(visitor).read(entry.getData());
            
            return Transformer.ResourceEntry.create(entry.getName(), entry.getTime(), writer.getOutput());
        }
        return entry;
    }

    private boolean processClass(ClassNode cls) {
        boolean replaced = false;
        for (MethodNode method : cls.methods) {
            for (ListIterator<AbstractInsnNode> iterator = method.instructions.iterator(); iterator.hasNext(); ) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof FieldInsnNode fieldInsn && (fieldInsn.getOpcode() == Opcodes.GETFIELD || fieldInsn.getOpcode() == Opcodes.GETSTATIC)) {
                    for (Map.Entry<String, String> entry : this.mappedReplacements.entrySet()) {
                        String source = entry.getKey();
                        if (source.equals(fieldInsn.name)) {
                            LOGGER.trace("Replacing field getter {} to method {} in {}#{}", source, entry.getValue(), cls.name, method.name);
                            iterator.remove();
                            String getterDesc = "()" + fieldInsn.desc;
                            MethodInsnNode getterCall = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fieldInsn.owner, entry.getValue(), getterDesc, false);
                            iterator.add(getterCall);
                            replaced = true;
                        }
                    }
                }
            }
        }
        return replaced;
    }

    private static class FilteringClassTweakerVisitor extends ForwardingVisitor {
        private final Collection<String> exclude;

        public FilteringClassTweakerVisitor(Collection<String> exclude, ClassTweakerVisitor... visitors) {
            super(visitors);
            this.exclude = exclude;
        }

        @Nullable
        @Override
        public AccessWidenerVisitor visitAccessWidener(String owner) {
            return new FilteringAccessWidenerVisitor(super.visitAccessWidener(owner), this.exclude);
        }
    }

    private static class FilteringAccessWidenerVisitor implements AccessWidenerVisitor {
        private final Collection<String> exclude;
        private final AccessWidenerVisitor parent;

        public FilteringAccessWidenerVisitor(AccessWidenerVisitor parent, Collection<String> exclude) {
            this.parent = parent;
            this.exclude = exclude;
        }

        @Override
        public void visitClass(AccessType access, boolean transitive) {
            this.parent.visitClass(access, transitive);
        }

        @Override
        public void visitMethod(String name, String descriptor, AccessType access, boolean transitive) {
            if (this.exclude.contains(name)) return;

            this.parent.visitMethod(name, descriptor, access, transitive);
        }

        @Override
        public void visitField(String name, String descriptor, AccessType access, boolean transitive) {
            if (this.exclude.contains(name)) return;

            this.parent.visitField(name, descriptor, access, transitive);
        }
    }
}
