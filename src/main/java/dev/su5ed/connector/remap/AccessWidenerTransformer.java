package dev.su5ed.connector.remap;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AccessWidenerTransformer implements Transformer {
    private static final String AT_PATH = "META-INF/accesstransformer.cfg";

    private final String resource;
    private final INamedMappingFile namedMappingFile;

    public AccessWidenerTransformer(String resource, INamedMappingFile namedMappingFile) {
        this.resource = resource;
        this.namedMappingFile = namedMappingFile;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (entry.getName().equals(this.resource)) {
            String content = mapAccessWidener(entry.getData());
            return ResourceEntry.create(AT_PATH, entry.getTime(), content.getBytes(StandardCharsets.UTF_8));
        }
        return entry;
    }

    public String mapAccessWidener(byte[] content) {
        AccessWidenerReader.Header header = AccessWidenerReader.readHeader(content);
        String namespace = header.getNamespace();
        IMappingFile mappingFile = this.namedMappingFile.getMap(namespace.equals("named") ? "yarn" : namespace, "srg");
        IMappingFile nameMappingFile = this.namedMappingFile.getMap("srg", "official");
        RemappingAccessWidenerVisitor visitor = new RemappingAccessWidenerVisitor(mappingFile, nameMappingFile);
        visitor.finish();
        AccessWidenerReader reader = new AccessWidenerReader(visitor);
        reader.read(content);
        return visitor.builder.toString();
    }

    public static class RemappingAccessWidenerVisitor implements AccessWidenerVisitor {
        private final IMappingFile mappingFile;
        private final IMappingFile nameMappingFile;
        private final StringBuilder builder = new StringBuilder();

        private final Map<String, Map<String, AccessWidenerReader.AccessType>> classFields = new HashMap<>();

        public RemappingAccessWidenerVisitor(IMappingFile mappingFile, IMappingFile nameMappingFile) {
            this.mappingFile = mappingFile;
            this.nameMappingFile = nameMappingFile;

            // TODO Transitive AW entries
            this.builder.append("# Access Transformer file converted by Connector\n");
        }

        public void finish() {
            this.classFields.forEach((owner, fields) -> fields.forEach((name, access) -> {
                String modifier = switch (access) {
                    case ACCESSIBLE -> "public";
                    case MUTABLE -> "public-f";
                    default -> throw new IllegalArgumentException("Invalid access type " + access + " for field");
                };
                IMappingFile.IClass cls = this.mappingFile.getClass(owner);
                String mappedOwner = cls != null ? cls.getMapped() : owner;
                String mappedName = cls != null ? cls.remapField(name) : name;
                this.builder.append(modifier).append(" ")
                    .append(mappedOwner.replace('/', '.')).append(" ")
                    .append(mappedName)
                    .append(cls != null ? " # " + this.nameMappingFile.getClass(mappedOwner).remapField(mappedName) : "")
                    .append("\n");
            }));
        }

        @Override
        public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {
            String modifier = switch (access) {
                case ACCESSIBLE -> "public";
                case EXTENDABLE -> "public-f";
                default -> throw new IllegalArgumentException("Invalid access type " + access + " for class");
            };
            String mappedName = this.mappingFile.remapClass(name).replace('/', '.');
            this.builder.append(modifier).append(" ").append(mappedName).append("\n");
        }

        @Override
        public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
            String modifier = switch (access) {
                case ACCESSIBLE -> "public";
                case EXTENDABLE -> "protected-f";
                default -> throw new IllegalArgumentException("Invalid access type " + access + " for method");
            };
            IMappingFile.IClass cls = this.mappingFile.getClass(owner);
            String mappedOwner = cls != null ? cls.getMapped() : owner;
            IMappingFile.IMethod mtd = cls != null ? cls.getMethod(name, descriptor) : null;
            this.builder.append(modifier).append(" ")
                .append(mappedOwner.replace('/', '.')).append(" ")
                .append(mtd != null ? mtd.getMapped() : name)
                .append(mtd != null ? mtd.getMappedDescriptor() : descriptor)
                .append(mtd != null ? " # " + this.nameMappingFile.getClass(mappedOwner).remapMethod(mtd.getMapped(), mtd.getMappedDescriptor()) : "")
                .append("\n");
        }

        @Override
        public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
            this.classFields.computeIfAbsent(owner, n -> new HashMap<>())
                .compute(name, (value, existing) -> existing == null || access.ordinal() > existing.ordinal() ? access : existing);
        }
    }
}
