package dev.su5ed.sinytra.connector.locator;

import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.connector.loader.ConnectorExceptionHandler;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModDiscoverer;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.filter.MarkerFilter;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * An ugly hack to sort FML dependency providers and make sure {@link ConnectorLocator ours} comes last.
 */
public class ConnectorEarlyLocator extends AbstractJarFileModLocator {
    private static final String NAME = "connector_early_locator";
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Stream<Path> scanCandidates() {
        // Unfortunately, FML doesn't provide a way to sort mod/dependency locators by priority, so we have to create our own
        try {
            Method method = FMLLoader.class.getDeclaredMethod("getModDiscoverer");
            method.setAccessible(true);
            ModDiscoverer discoverer = (ModDiscoverer) method.invoke(null);
            Field field = ModDiscoverer.class.getDeclaredField("dependencyLocatorList");
            field.setAccessible(true);
            List<IDependencyLocator> dependencyLocatorList = (List<IDependencyLocator>) field.get(discoverer);
            // 1 - move under; 0 - preserve original order
            dependencyLocatorList.sort(Comparator.comparingInt(loc -> loc instanceof ConnectorLocator ? 1 : 0));
        } catch (Throwable t) {
            LOGGER.error("Error sorting FML dependency locators", t);
            ConnectorExceptionHandler.addSuppressed(t);
        }
        injectLogMarkers();

        // Locate our own embedded jars
        return EmbeddedDependencies.locateEmbeddedJars();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    private static void injectLogMarkers() {
        // Deconstruct grouped log markers system property
        String markerselection = System.getProperty("connector.logging.markers", "");
        Arrays.stream(markerselection.split(",")).forEach(marker -> System.setProperty("connector.logging.marker." + marker.toLowerCase(Locale.ROOT), "ACCEPT"));

        // Obtain a reference to the logger's Configuration object
        org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ConnectorEarlyLocator.class);
        Configuration config = logger.getContext().getConfiguration();

        // Add a marker filter to the logger's configuration
        config.addFilter(MarkerFilter.createFilter("MIXINPATCH", parseLogMarker("connector.logging.marker.mixinpatch"), Filter.Result.NEUTRAL));
        config.addFilter(MarkerFilter.createFilter("MERGER", parseLogMarker("connector.logging.marker.merger"), Filter.Result.NEUTRAL));

        // Reconfigure the logger with the updated configuration
        logger.getContext().updateLoggers();
    }

    private static Filter.Result parseLogMarker(String propertyName) {
        String value = System.getProperty(propertyName, "DENY");
        return Filter.Result.valueOf(value);
    }
}
