package org.sinytra.connector.transformer.api;

import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.connector.transformer.TransformerEnvironment;

/**
 * Provides jar transformation context for plugins
 */
public interface TransformerContext {
    /**
     * Provides information about the jar being transformed.
     *
     * @return jar candidate metadata
     */
    CandidateJarMetadata candidateJar();

    /**
     * Get the current patch environment used to apply Adapter patches.
     *
     * @return the current Adapter patch environment
     */
    PatchEnvironment patchEnvironment();

    /**
     * Get the current transformer environment.
     *
     * @return the current transformer environment
     */
    TransformerEnvironment environment();
}
