package org.sinytra.connector.locator;

import com.electronwill.nightconfig.core.UnmodifiableCommentedConfig;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.mixin.MixinFacade;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.service.ConnectorForkJoinThreadFactory;
import org.sinytra.connector.transformer.jar.MetadataReader;
import org.sinytra.connector.util.ConnectorConfig;
import org.sinytra.connector.util.ConnectorUtil;
import org.sinytra.connector.util.CrashReportHeader;
import org.sinytra.launchpad.api.Constants;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.jar.Manifest;

public class ConnectorModFileReader implements IModFileReader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ConnectorModFileReader() {
        if (!ConnectorUtil.SHOULD_ENABLE.get()) {
            return;
        }

        injectLogMarkers();
        CrashReportHeader.registerCrashLogInfo();
        ConnectorForkJoinThreadFactory.install();
        new MixinFacade();

        FabricLoaderImpl.INSTANCE.ignoreMods(ConnectorConfig.INSTANCE.get().hiddenMods());
        FabricLoaderImpl.INSTANCE.aliasMods(ConnectorConfig.INSTANCE.get().globalModAliases());
    }

    @Override
    public int getPriority() {
        // Run before both Launchpad and builtin readers
        return 500;
    }

    @Override
    @Nullable
    public IModFile read(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (!ConnectorUtil.SHOULD_ENABLE.get()) {
            return null;
        }

        try {
            return readModFile(jar, attributes.withReader(this));
        } catch (ModLoadingException e) {
            throw e;
        } catch (Exception e) {
            StartupNotificationManager.addModMessage("CONNECTOR LOCATOR ERROR");
            throw new ModLoadingException(ConnectorEarlyLoader.createGenericLoadingIssue(e, "Failed to read mod"));
        }
    }

    private IModFile readModFile(JarContents jar, ModFileDiscoveryAttributes attributes) {
        if (!jar.containsFile(MetadataReader.FMJ)) {
            return null;
        }

        if (isNeoForgeMod(jar)) {
            return null;
        }

        LoaderModMetadata metadata = MetadataReader.readModMetadata(jar);
        if (metadata == null) {
            return null;
        }

        // Check if mod is a valid candidate
        if (shouldIgnoreMod(metadata)) {
            LOGGER.debug("Skipping loading mod {}", metadata.getId());
            return null;
        }

        return IModFile.create(jar, m -> manifestParser(m, metadata), IModFile.Type.MOD, attributes.withReader(this));
    }

    private static boolean shouldIgnoreMod(LoaderModMetadata metadata) {
        if (ConnectorUtil.DISABLED_MODS.contains(metadata.getId())) {
            return true;
        }

        boolean launchpadCompatible = Optional.ofNullable(metadata.getCustomValue(Constants.ENABLE_LAUNCHPAD))
            .map(CustomValue::getAsBoolean)
            .orElse(false);
        if (launchpadCompatible) {
            return true;
        }

        return false;
    }

    private static boolean isNeoForgeMod(JarContents jar) {
        Manifest manifest = jar.getManifest();
        if (manifest != null) {
            if (manifest.getMainAttributes().containsKey(ModFile.TYPE)) {
                return true;
            }
        }

        if (jar.containsFile(JarModsDotTomlModFileReader.MODS_TOML)) {
            var modsToml = jar.get(JarModsDotTomlModFileReader.MODS_TOML);
            if (modsToml != null) {
                UnmodifiableCommentedConfig config;
                try (var reader = modsToml.bufferedReader()) {
                    config = TomlFormat.instance().createParser().parse(reader).unmodifiable();
                } catch (IOException e) {
                    LOGGER.error("Failed to read {} from {}", modsToml, jar.getPrimaryPath(), e);
                    return true;
                }

                return !config.contains(List.of("properties", org.sinytra.connector.api.Constants.PLACEHOLDER_PROPERTY));
            }
        }

        return false;
    }

    private static void injectLogMarkers() {
        // Deconstruct grouped log markers system property
        String markerselection = System.getProperty("connector.logging.markers", "");
        Arrays.stream(markerselection.split(",")).forEach(marker -> System.setProperty("connector.logging.marker." + marker.toLowerCase(Locale.ROOT), "ACCEPT"));

        // Obtain a reference to the logger's Configuration object
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ConnectorModFileReader.class);
        Configuration config = logger.getContext().getConfiguration();

        // Add a marker filter to the logger's configuration
        config.addFilter(MarkerFilter.createFilter("MIXINPATCH", parseLogMarker("connector.logging.marker.mixinpatch"), Filter.Result.NEUTRAL));

        // Reconfigure the logger with the updated configuration
        logger.getContext().updateLoggers();
    }

    private static Filter.Result parseLogMarker(String propertyName) {
        String value = System.getProperty(propertyName, "DENY");
        return Filter.Result.valueOf(value);
    }

    private static IModFileInfo manifestParser(IModFile mod, LoaderModMetadata metadata) {
        IConfigurable dummy = new IConfigurable() {
            @Override
            public <T> Optional<T> getConfigElement(String... key) {
                return Optional.empty();
            }

            @Override
            public List<? extends IConfigurable> getConfigList(String... key) {
                return Collections.emptyList();
            }
        };
        return new StubModFileInfo(mod, metadata, dummy);
    }

    public record StubModFileInfo(IModFile mod, LoaderModMetadata metadata, IConfigurable configurable) implements IModFileInfo, IConfigurable {
        @Override
        public <T> Optional<T> getConfigElement(String... strings) {
            return Optional.empty();
        }

        @Override
        public List<? extends IConfigurable> getConfigList(String... strings) {
            return null;
        }

        @Override
        public List<IModInfo> getMods() {
            return Collections.emptyList();
        }

        @Override
        public List<LanguageSpec> requiredLanguageLoaders() {
            return Collections.emptyList();
        }

        @Override
        public boolean showAsResourcePack() {
            return false;
        }

        @Override
        public boolean showAsDataPack() {
            return false;
        }

        @Override
        public Map<String, Object> getFileProperties() {
            return Map.of(ConnectorUtil.FABRIC_METADATA, metadata);
        }

        @Override
        public String getLicense() {
            return "unknown";
        }

        @Override
        public IModFile getFile() {
            return mod;
        }

        @Override
        public IConfigurable getConfig() {
            return configurable;
        }

        // These Should never be called as it's only called from ModJarMetadata.version and we bypass that
        @Override
        public String versionString() {
            return null;
        }

        @Override
        public List<String> usesServices() {
            return null;
        }

        @Override
        public String toString() {
            return "IModFileInfo(" + mod.getFilePath() + ")";
        }
    }
}
