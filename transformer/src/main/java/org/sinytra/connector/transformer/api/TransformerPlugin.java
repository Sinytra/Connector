package org.sinytra.connector.transformer.api;

import org.sinytra.connector.transformer.transform.MixinPatchTransformer;

/**
 * Service class for providing Connector transformer plugins.
 */
public interface TransformerPlugin {
    /**
     * Globally unique plugin identifier.
     */
    String name();

    /**
     * Register additional mixin patches to the {@link MixinPatchTransformer}.
     *
     * @param registrar registration interface
     * @param context   current transformer context
     */
    default void registerPatches(PatchRegistrar registrar, TransformerContext context) {
    }

    /**
     * Register additional ART transformers to the jar transformation chain.
     *
     * @param registrar registration interface
     * @param context   current transformer context
     */
    default void registerJarTransformers(TransformerRegistrar registrar, TransformerContext context) {
    }
}
