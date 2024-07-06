package org.sinytra.connector.transformer.patch;

import org.sinytra.adapter.patch.api.Patch;
import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.List;

public class ClassNodeTransformer implements Transformer {
    private final List<ClassProcessor> processors;

    public ClassNodeTransformer(ClassProcessor... processors) {
        this.processors = List.of(processors);
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        Patch.Result patchResult = Patch.Result.PASS;

        ClassReader reader = new ClassReader(entry.getData());
        ClassNode node = new ClassNode();
        reader.accept(node, 0);

        for (ClassProcessor processor : this.processors) {
            patchResult = patchResult.or(processor.process(node));
        }

        if (patchResult != Patch.Result.PASS) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | (patchResult == Patch.Result.COMPUTE_FRAMES ? ClassWriter.COMPUTE_FRAMES : 0));
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
        Patch.Result process(ClassNode node);

        default ResourceEntry process(ResourceEntry entry) {
            return entry;
        }
    }
}
