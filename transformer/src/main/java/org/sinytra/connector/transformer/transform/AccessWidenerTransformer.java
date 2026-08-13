package org.sinytra.connector.transformer.transform;

import net.fabricmc.classtweaker.api.ClassTweakerReader;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor;
import net.fabricmc.classtweaker.api.visitor.AccessWidenerVisitor.AccessType;
import net.fabricmc.classtweaker.api.visitor.ClassTweakerVisitor;
import net.fabricmc.loader.impl.MappingResolverImpl;
import net.minecraftforge.fart.api.Transformer;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.jar.IntermediateMapping;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class AccessWidenerTransformer implements Transformer {
    private final String resource;
    private final MappingResolverImpl resolver;
    private final IntermediateMapping fastMapping;

    public AccessWidenerTransformer(String resource, MappingResolverImpl namedMappingFile, IntermediateMapping fastMapping) {
        this.resource = resource;
        this.resolver = namedMappingFile;
        this.fastMapping = fastMapping;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (entry.getName().equals(this.resource)) {
            String content = mapAccessWidener(entry.getData());
            return ResourceEntry.create(TransformerUtil.AT_PATH, entry.getTime(), content.getBytes(StandardCharsets.UTF_8));
        }
        return entry;
    }

    public String mapAccessWidener(byte[] content) {
        ClassTweakerReader.Header header = ClassTweakerReader.readHeader(content);
        String namespace = header.getNamespace();
        TransformingClassTweakerVisitor visitor = new TransformingClassTweakerVisitor(namespace);
        ClassTweakerReader.create(visitor).read(content);
        visitor.finish();
        return visitor.builder.toString();
    }

    public class TransformingClassTweakerVisitor implements ClassTweakerVisitor {
        private final String sourceNamespace;
        private final StringBuilder builder = new StringBuilder();

        private final Map<String, AccessType> classAccess = new HashMap<>();
        private final Map<String, Map<String, AccessType>> classFields = new HashMap<>();

        public TransformingClassTweakerVisitor(String sourceNamespace) {
            this.sourceNamespace = sourceNamespace;

            this.builder.append("# Access Transformer file converted by Connector\n");
        }

        @Nullable
        @Override
        public AccessWidenerVisitor visitAccessWidener(String owner) {
            return new RemappingAccessWidenerVisitor(owner);
        }

        public void finish() {
            // Translate class AWs
            this.classAccess.forEach((name, access) -> {
                String modifier = switch (access) {
                    case ACCESSIBLE -> "public";
                    case EXTENDABLE -> "public-f";
                    default -> throw new IllegalArgumentException("Invalid access type " + access + " for class");
                };
                String mappedName = AccessWidenerTransformer.this.resolver.mapClassName(this.sourceNamespace, name).replace('/', '.');
                this.builder.append(modifier).append(" ").append(mappedName).append("\n");
            });
            // Translate field AWs
            this.classFields.forEach((owner, fields) -> fields.forEach((name, access) -> {
                String modifier = switch (access) {
                    case ACCESSIBLE -> "public";
                    case MUTABLE -> "public-f";
                    default -> throw new IllegalArgumentException("Invalid access type " + access + " for field");
                };
                String mappedOwner = AccessWidenerTransformer.this.resolver.mapClassName(this.sourceNamespace, owner);
                String mappedName = AccessWidenerTransformer.this.resolver.mapFieldName(this.sourceNamespace, owner, name, "");
                this.builder.append(modifier).append(" ")
                    .append(mappedOwner.replace('/', '.')).append(" ")
                    .append(mappedName)
                    .append(!name.equals(mappedName) ? " # " + mappedName : "")
                    .append("\n");
            }));
        }

        public class RemappingAccessWidenerVisitor implements AccessWidenerVisitor {
            private final String owner;

            public RemappingAccessWidenerVisitor(String owner) {
                this.owner = owner;
            }

            @Override
            public void visitClass(AccessType access, boolean transitive) {
                // AcessWidener silently also access widens owners of methods that are being AW'd, but never calls visitClass for those entries
                // Therefore, we have to replicate this behavior ourselves in visitMethod below. In addition, we first gather all class AWs in a map
                // and only translate them once all AW entries have been process. This prevents conflicts in case visitMethod generates an AW entry
                // for a class that already has one, except with lower access.
                classAccess.compute(this.owner, (value, existing) -> existing == null || access.ordinal() > existing.ordinal() ? access : existing);
            }

            @Override
            public void visitMethod(String name, String descriptor, AccessType access, boolean transitive) {
                String modifier = switch (access) {
                    case ACCESSIBLE -> "public";
                    case EXTENDABLE -> "protected-f";
                    default -> throw new IllegalArgumentException("Invalid access type " + access + " for method");
                };
                String mappedOwner = AccessWidenerTransformer.this.resolver.mapClassName(sourceNamespace, owner);
                String mappedName = AccessWidenerTransformer.this.resolver.mapMethodName(sourceNamespace, owner, name, descriptor);
                // Mods might target inherited methods that are not part of the mapping file, we'll try to remap them using the flat mapping instead
                if (name.equals(mappedName)) {
                    mappedName = AccessWidenerTransformer.this.fastMapping.mapMethodOrDefault(name, descriptor);
                }
                String mappedDescriptor = AccessWidenerTransformer.this.resolver.mapDescriptor(sourceNamespace, descriptor);
                builder.append(modifier).append(" ")
                    .append(mappedOwner.replace('/', '.')).append(" ")
                    .append(mappedName)
                    .append(mappedDescriptor)
                    .append(!name.equals(mappedName) ? " # " + mappedName : "")
                    .append("\n");
                // Make parent class accessible / extensible if necessary
                visitClass(access, false);
            }

            @Override
            public void visitField(String name, String descriptor, AccessType access, boolean transitive) {
                classFields.computeIfAbsent(owner, n -> new HashMap<>())
                    .compute(name, (value, existing) -> existing == null || access.ordinal() > existing.ordinal() ? access : existing);
                // Make parent class accessible / extensible if necessary
                if (access != AccessType.MUTABLE) {
                    visitClass(access, false);
                }
            }
        }
    }
}
