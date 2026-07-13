package org.sinytra.connector.transformer.runner;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.art.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.connector.transformer.TransformerBytecodeProvider;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.jar.SimpleClassLookup;
import org.sinytra.connector.transformer.runner.runtime.MixinServiceProbe;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.service.MixinService;

import java.nio.file.Path;
import java.util.List;

public class PortableRuntimeEnvironment implements TransformerEnvironment {
    private final Path outputDir;
    private final Path auditLogPath;
    private final Path cleanPath;
    private final Path generatedJarPath;
    private final String mappedSuffix;

    public PortableRuntimeEnvironment(Path outputDir, Path auditLogPath, Path cleanPath, Path generatedJarPath, String gameVersion) {
        this.outputDir = outputDir;
        this.auditLogPath = auditLogPath;
        this.cleanPath = cleanPath;
        this.generatedJarPath = generatedJarPath;
        this.mappedSuffix = "_tx_" + gameVersion;
    }

    @Override
    public EnvType getEnvType() {
        return EnvType.CLIENT;
    }

    @Override
    public Path getGeneratedJarPath() {
        return this.generatedJarPath;
    }

    @Override
    public ClassLookup getCleanClassLookup() {
        return new SimpleClassLookup(ClassProvider.fromPaths(this.cleanPath));
    }

    @Override
    public ClassProvider getRuntimeClassProvider(List<Path> libraries) {
        return ClassProvider.fromPaths(libraries.toArray(Path[]::new));
    }

    @Override
    public TransformProgressMeter createProgressMeter(String msg, int steps) {
        return new DummyProgressMeter();
    }

    @Override
    public Path createCachedJarPath(String name) {
        return this.outputDir.resolve(name + this.mappedSuffix + ".jar");
    }

    @Override
    public Path getAuditReportPath() {
        return this.auditLogPath;
    }

    @Override
    public void setGlobalBytecodeLoader(@Nullable TransformerBytecodeProvider loader) {
        MixinServiceProbe service = (MixinServiceProbe) MixinService.getService();
        service.setLoader(loader);
    }

    @Override
    public RuntimeException onTransformationError(String msg, Throwable throwable) {
        return new RuntimeException(msg, throwable);
    }

    @Override
    public int getFabricMixinCompatibility(LoaderModMetadata metadata) {
        return FabricUtil.COMPATIBILITY_0_10_0;
    }

    @Override
    public String getJarCacheVersion() {
        return "1.0";
    }

    //@formatter:off
    private static class DummyProgressMeter implements TransformProgressMeter {
        @Override public void increment() {}
        @Override public void complete() {}
    }
    //@formatter:on
}
