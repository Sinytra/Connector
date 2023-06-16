package dev.su5ed.connector.remap;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.fart.api.Transformer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AccessWidenerTransformer implements Transformer {
    private static final String AT_PATH = "META-INF/accesstransformer.cfg";

    private final String resource;
    private final MappingResolverImpl resolver;

    public AccessWidenerTransformer(String resource, MappingResolverImpl namedMappingFile) {
        this.resource = resource;
        this.resolver = namedMappingFile;
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
        RemappingAccessWidenerVisitor visitor = new RemappingAccessWidenerVisitor(resolver, namespace);
        AccessWidenerReader reader = new AccessWidenerReader(visitor);
        reader.read(content);
        visitor.finish();
        return visitor.builder.toString();
    }

    public static class RemappingAccessWidenerVisitor implements AccessWidenerVisitor {
        private final MappingResolverImpl resolver;
        private final String sourceNamespace;
        private final StringBuilder builder = new StringBuilder();

        private final Map<String, Map<String, AccessWidenerReader.AccessType>> classFields = new HashMap<>();

        public RemappingAccessWidenerVisitor(MappingResolverImpl resolver, String sourceNamespace) {
            this.resolver = resolver;
            this.sourceNamespace = sourceNamespace;

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
                String mappedOwner = this.resolver.mapClassName(this.sourceNamespace, owner);
                String mappedName = this.resolver.mapFieldName(this.sourceNamespace, owner, name, "");
                this.builder.append(modifier).append(" ")
                    .append(mappedOwner.replace('/', '.')).append(" ")
                    .append(mappedName)
                    .append(!name.equals(mappedName) ? " # " + mappedName : "")
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
            String mappedName = this.resolver.mapClassName(this.sourceNamespace, name).replace('/', '.');
            this.builder.append(modifier).append(" ").append(mappedName).append("\n");
        }

        @Override
        public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
            String modifier = switch (access) {
                case ACCESSIBLE -> "public";
                case EXTENDABLE -> "protected-f";
                default -> throw new IllegalArgumentException("Invalid access type " + access + " for method");
            };
            String mappedOwner = this.resolver.mapClassName(this.sourceNamespace, owner);
            String mappedName = this.resolver.mapMethodName(this.sourceNamespace, owner, name, descriptor);
            String mappedDescriptor = this.resolver.mapDescriptor(this.sourceNamespace, descriptor);
            this.builder.append(modifier).append(" ")
                .append(mappedOwner.replace('/', '.')).append(" ")
                .append(mappedName)
                .append(mappedDescriptor)
                .append(!name.equals(mappedName) ? " # " + mappedName : "")
                .append("\n");
        }

        @Override
        public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {
            this.classFields.computeIfAbsent(owner, n -> new HashMap<>())
                .compute(name, (value, existing) -> existing == null || access.ordinal() > existing.ordinal() ? access : existing);
        }
    }
}
