package org.sinytra.connector.transformer;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.art.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface TransformerEnvironment {
    Path getAuditReportPath();

    Path getGeneratedJarPath();

    ClassLookup getCleanClassLookup();

    ClassProvider getRuntimeClassProvider(List<Path> libraries);

    TransformProgressMeter createProgressMeter(String msg, int steps);

    Path createCachedJarPath(String name) throws IOException;

    EnvType getEnvType();

    void setGlobalBytecodeLoader(@Nullable TransformerBytecodeProvider loader);

    RuntimeException onTransformationError(String msg, Throwable throwable);

    int getFabricMixinCompatibility(LoaderModMetadata metadata);

    default Map<String, String> getModIdAliases() {
        return Map.of();
    }

    String getJarCacheVersion();
}
