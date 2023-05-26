package dev.su5ed.connector;

import net.fabricmc.accesswidener.AccessWidenerReader;
import net.fabricmc.accesswidener.AccessWidenerVisitor;

public final class AccessWidenerMapper {

    public static String mapAccessWidener(byte[] content) {
        RemappingAccessWidenerVisitor visitor = new RemappingAccessWidenerVisitor();
        AccessWidenerReader reader = new AccessWidenerReader(visitor);
        reader.read(content);
        return visitor.builder.toString();
    }

    private AccessWidenerMapper() {}

    public static class RemappingAccessWidenerVisitor implements AccessWidenerVisitor {
        private final StringBuilder builder = new StringBuilder();

        @Override
        public void visitClass(String name, AccessWidenerReader.AccessType access, boolean transitive) {

        }

        @Override
        public void visitMethod(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {

        }

        @Override
        public void visitField(String owner, String name, String descriptor, AccessWidenerReader.AccessType access, boolean transitive) {

        }
    }
}
