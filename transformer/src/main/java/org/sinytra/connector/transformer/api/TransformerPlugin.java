package org.sinytra.connector.transformer.api;

public interface TransformerPlugin {
    String name();

    default void registerPatches(PatchRegistrar registrar, TransformerContext context) {
    }

    default void registerJarTransformers(TransformerRegistrar registrar, TransformerContext context) {
    }
}
