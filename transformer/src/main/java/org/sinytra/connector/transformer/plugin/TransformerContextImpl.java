package org.sinytra.connector.transformer.plugin;

import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.api.CandidateJarMetadata;
import org.sinytra.connector.transformer.api.TransformerContext;

public record TransformerContextImpl(
    CandidateJarMetadata candidateJar,
    PatchEnvironment patchEnvironment,
    TransformerEnvironment environment
) implements TransformerContext {
}
