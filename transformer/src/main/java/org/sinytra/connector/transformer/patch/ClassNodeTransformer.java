package org.sinytra.connector.transformer.patch;

import net.neoforged.art.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.env.ctx.PatchResult;

import java.util.List;

public class ClassNodeTransformer implements Transformer {
    private final List<ClassProcessor> processors;

    public ClassNodeTransformer(ClassProcessor... processors) {
        this.processors = List.of(processors);
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        PatchResult patchResult = PatchResult.PASS;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (ClassProcessor processor : this.processors) {
            patchResult = patchResult.or(processor.process(node));
        }

        if (patchResult != PatchResult.PASS) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | (patchResult == PatchResult.COMPUTE_FRAMES ? ClassWriter.COMPUTE_FRAMES : 0));
            node.accept(writer);
            return ClassEntry.create(entry.getName(), entry.getTime(), writer.toByteArray());
        }
        return entry;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        for (ClassProcessor processor : this.processors) {
            entry = processor.process(entry);
        }
        return entry;
    }

    public interface ClassProcessor {
        PatchResult process(ClassNode node);

        default ResourceEntry process(ResourceEntry entry) {
            return entry;
        }
    }
}
