package dev.su5ed.connector.fart;

import com.mojang.datafixers.util.Pair;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SimpleRenamingTransformer implements Transformer {
    private final Remapper remapper;

    public SimpleRenamingTransformer(IMappingFile mappingFile) {
        Map<String, String> mapping = mappingFile.getClasses().stream()
            .flatMap(cls -> {
                Pair<String, String> clsRename = Pair.of(cls.getOriginal(), cls.getMapped());
                Stream<Pair<String, String>> fieldRenames = cls.getFields().stream().map(field -> Pair.of(field.getOriginal(), field.getMapped()));
                Stream<Pair<String, String>> methodRenames = cls.getMethods().stream().map(method -> Pair.of(method.getOriginal(), method.getMapped()));
                return Stream.concat(Stream.of(clsRename), Stream.concat(fieldRenames, methodRenames));
            })
            .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (a, b) -> a));
        this.remapper = new DeadSimpleRemapper(mapping, Map.of("org/spongepowered/", "org/spongepowered/reloc/"));
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        ClassReader reader = new ClassReader(entry.getData());
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper remapper = new ClassRemapper(writer, this.remapper);

        reader.accept(remapper, 0);

        byte[] data = writer.toByteArray();

        return entry.isMultiRelease()
            ? ClassEntry.create(entry.getName(), entry.getTime(), data, entry.getVersion())
            : ClassEntry.create(entry.getName(), entry.getTime(), data);
    }

    public static class DeadSimpleRemapper extends Remapper {
        private final Map<String, String> mapping;
        private final Map<String, String> relocation;

        public DeadSimpleRemapper(Map<String, String> mapping, Map<String, String> relocation) {
            this.mapping = mapping;
            this.relocation = relocation;
        }

        @Override
        public String mapMethodName(final String owner, final String name, final String descriptor) {
            String remappedName = map(name);
            return remappedName == null ? name : remappedName;
        }

        @Override
        public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
            String remappedName = map(name);
            return remappedName == null ? name : remappedName;
        }

        @Override
        public String mapAnnotationAttributeName(final String descriptor, final String name) {
            String remappedName = map(name);
            return remappedName == null ? name : remappedName;
        }

        @Override
        public String mapFieldName(final String owner, final String name, final String descriptor) {
            String remappedName = map(name);
            return remappedName == null ? name : remappedName;
        }

        @Override
        public String map(final String key) {
            String remapped = this.mapping.get(key);
            if (remapped == null) {
                for (Map.Entry<String, String> entry : this.relocation.entrySet()) {
                    String pKey = entry.getKey();
                    if (key.startsWith(pKey)) {
                        return key.replace(pKey, entry.getValue());
                    }
                }
            }
            return remapped;
        }
    }
}
