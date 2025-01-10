package org.sinytra.connector.locator;

import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.sinytra.connector.service.DummyVirtualJar;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * An ugly hack to sort FML dependency providers and make sure {@link ConnectorLocator ours} comes last.
 */
public class ConnectorEarlyLocatorBootstrap implements IModFileCandidateLocator {
    private static ILaunchContext launchContext;

    public ConnectorEarlyLocatorBootstrap() {
        injectLogMarkers();
    }

    public static ILaunchContext getLaunchContext() {
        return launchContext;
    }

    private static void injectLogMarkers() {
        // Deconstruct grouped log markers system property
        String markerselection = System.getProperty("connector.logging.markers", "");
        Arrays.stream(markerselection.split(",")).forEach(marker -> System.setProperty("connector.logging.marker." + marker.toLowerCase(Locale.ROOT), "ACCEPT"));

        // Obtain a reference to the logger's Configuration object
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ConnectorEarlyLocatorBootstrap.class);
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

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        launchContext = context;
        pipeline.addModFile(overrideFabricLoaderMod());
    }

    private static IModFile overrideFabricLoaderMod() {
        SecureJar secureJar = new DummyVirtualJar("net.fabricmc.loader", "dummy_forgified_fabric_loader", Set.of(), () -> {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, "999.999.999");
            return manifest;
        }, s -> Optional.empty());
        return IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser, IModFile.Type.LIBRARY, ModFileDiscoveryAttributes.DEFAULT);
    }
}
