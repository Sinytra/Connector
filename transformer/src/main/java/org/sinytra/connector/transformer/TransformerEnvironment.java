package org.sinytra.connector.transformer;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fart.api.ClassProvider;
import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.connector.transformer.transform.TransformProgressMeter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public interface TransformerEnvironment {
    Path getAuditReportPath();

    Path getGeneratedJarPath();

    ClassLookup getCleanClassLookup();

    ClassProvider getRuntimeClassProvider(List<Path> libraries);

    LoaderModMetadata wrapModMetadata(LoaderModMetadata metadata);

    TransformProgressMeter createProgressMeter(String msg, int steps);

    Path createCachedJarPath(String name) throws IOException;

    EnvType getEnvType();

    VersionOverrides getVersionOverrides();

    Supplier<DependencyOverrides> getDependencyOverrides();

    void setGlobalBytecodeLoader(@Nullable ILaunchPluginService.ITransformerLoader loader);

    RuntimeException onTransformationError(String msg, Throwable throwable);

    int getFabricMixinCompatibility(LoaderModMetadata metadata);

    String getJarCacheVersion();

    void completeSetup();

    URL getAdapterPatchDataURL() throws MalformedURLException;

    URL getAdapterLVTDataURL() throws MalformedURLException;
}
