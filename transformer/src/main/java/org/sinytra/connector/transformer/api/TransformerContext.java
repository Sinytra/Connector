package org.sinytra.connector.transformer.api;

import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.connector.transformer.TransformerEnvironment;

public interface TransformerContext {
    CandidateJarMetadata candidateJar();

    PatchEnvironment patchEnvironment();

    TransformerEnvironment environment();
}
