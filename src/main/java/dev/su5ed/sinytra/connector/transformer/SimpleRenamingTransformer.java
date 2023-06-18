package dev.su5ed.sinytra.connector.transformer;

import net.minecraftforge.fart.api.Transformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.util.Map;

public class SimpleRenamingTransformer implements Transformer {
    private final Remapper remapper;

    public SimpleRenamingTransformer(Map<String, String> mappings) {
        this.remapper = new DeadSimpleRemapper(mappings, Map.of("org/spongepowered/", "org/spongepowered/reloc/"));
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

        // An attempt at remapping reflection calls
        @Override
        public Object mapValue(Object value) {
            if (value instanceof String str) {
                for (Map.Entry<String, String> entry : this.relocation.entrySet()) {
                    String pKey = entry.getKey().replace('/', '.');
                    if (str.startsWith(pKey)) {
                        return str.replace(pKey, entry.getValue().replace('/', '.'));
                    }
                }
            }
            return super.mapValue(value);
        }
    }
}
