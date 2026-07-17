package org.sinytra.connector.transformer.plugin;

import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.connector.transformer.api.*;
import org.sinytra.connector.transformer.patch.ClassAnalysingTransformer;
import org.sinytra.connector.transformer.patch.ClassNodeTransformer;
import org.sinytra.connector.transformer.transform.*;

import java.util.List;
import java.util.Set;

public class BuiltinConnectorPlugin implements TransformerPlugin {
    private static final List<MethodPatch> PRIORITY_PATCHES = MixinPatches.getPriorityPatches();
    private static final List<MethodPatch> PATCHES = MixinPatches.getPatches();

    private final AccessorRedirectTransformer redirectTransformer = new AccessorRedirectTransformer();

    @Override
    public String name() {
        return "connector:builtin";
    }

    @Override
    public void registerPatches(PatchRegistrar registrar, TransformerContext context) {
        registrar.add(PatchRegistrar.HIGHEST_SYSTEM_PRIORITY, PRIORITY_PATCHES);
        registrar.add(PatchRegistrar.HIGHER_SYSTEM_PRIORITY, redirectTransformer.getPatches());
        registrar.add(PatchRegistrar.DEFAULT_PRIORITY, PATCHES);
    }

    @Override
    public void registerJarTransformers(TransformerRegistrar registrar, TransformerContext context) {
        registrar.register(TransformerIds.SIGNATURE_STRIPPER, new JarSignatureStripper());
        registrar.register(TransformerIds.MOD_METADATA, FabricMetadataTransformer.INSTANCE);

        registrar.registerBefore(TransformerIds.CLASS_ANALYSIS,
            Set.of(TransformerIds.METHOD_PATCHES),
            new ClassNodeTransformer(
                new FieldToMethodTransformer(context.candidateJar().modMetadata().getClassTweaker()),
                new ClassAnalysingTransformer()
            ));

        registrar.registerAfter(
            TransformerIds.ACCESS_REDIRECT,
            Set.of(TransformerIds.METHOD_PATCHES),
            new ClassNodeTransformer(this.redirectTransformer)
        );
    }
}
