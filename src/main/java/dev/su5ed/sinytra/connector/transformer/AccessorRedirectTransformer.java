package dev.su5ed.sinytra.connector.transformer;

import dev.su5ed.sinytra.adapter.patch.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import dev.su5ed.sinytra.connector.transformer.patch.RedirectAccessorToMethod;
import net.minecraftforge.coremod.api.ASMAPI;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.rethrowConsumer;

public class AccessorRedirectTransformer implements Transformer {
    private static final String PREFIX = "connector$redirect$";
    public static final List<? extends Patch> PATCHES = FieldToMethodTransformer.REPLACEMENTS.entrySet().stream()
        .flatMap(entry -> entry.getValue().entrySet().stream()
            .map(redirect -> Patch.interfaceBuilder()
                .targetClass(entry.getKey().replace('.', '/'))
                .targetField(ASMAPI.mapField(redirect.getKey()))
                .transform(new RedirectAccessorToMethod(redirect.getValue()))
                .transform((classNode, methodNode, annotationNode, map, patchContext) -> {
                    methodNode.name = PREFIX + methodNode.name;
                    return Patch.Result.APPLY;
                })
                .build()))
        .toList();

    private final IMappingFile mappings;
    private final Map<String, Map<String, String>> methodRenames = new HashMap<>();

    public AccessorRedirectTransformer(IMappingFile mappings) {
        this.mappings = mappings;
    }

    public void analyze(File input, Set<String> mixinPackages, PatchEnvironment environment) throws IOException {
        List<? extends Patch> accessorAnalysisPatches = FieldToMethodTransformer.REPLACEMENTS.entrySet().stream()
            .flatMap(entry -> entry.getValue().keySet().stream()
                .map(s -> Patch.interfaceBuilder()
                    .targetClass(this.mappings.remapClass(entry.getKey().replace('.', '/')))
                    .targetField(ASMAPI.mapField(s))
                    .transform(this::analyzeAccessor)
                    .build()))
            .toList();

        try (FileSystem fs = FileSystems.newFileSystem(input.toPath(), Map.of())) {
            for (String pkg : mixinPackages) {
                Path packageRoot = fs.getPath(pkg);
                if (Files.notExists(packageRoot)) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(packageRoot)) {
                    stream
                        .filter(path -> path.getFileName().toString().endsWith(".class"))
                        .forEach(rethrowConsumer(path -> analyzeClass(path, accessorAnalysisPatches, environment)));
                }
            }
        }
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean applied = false;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    Map<String, String> renames = this.methodRenames.get(minsn.owner);
                    if (renames != null) {
                        String newName = renames.get(minsn.name + minsn.desc);
                        if (newName != null) {
                            minsn.name = newName;
                            applied = true;
                        }
                    }
                }
            }
        }

        if (applied) {
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            byte[] data = writer.toByteArray();
            return ClassEntry.create(entry.getName(), entry.getTime(), data);
        }
        return entry;
    }

    private void analyzeClass(Path path, List<? extends Patch> accessorAnalysisPatches, PatchEnvironment environment) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ClassReader reader = new ClassReader(bytes);
        ClassNode node = new ClassNode();
        reader.accept(node, ClassReader.SKIP_CODE);

        for (Patch patch : accessorAnalysisPatches) {
            patch.apply(node, environment);
        }
    }

    private Patch.Result analyzeAccessor(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        this.methodRenames.computeIfAbsent(classNode.name, s -> new HashMap<>())
            .put(methodNode.name + methodNode.desc, PREFIX + methodNode.name);
        return Patch.Result.PASS;
    }
}
