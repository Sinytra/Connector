package org.sinytra.connector.transformer.plugin;

import com.mojang.logging.LogUtils;
import net.neoforged.art.api.Transformer;
import net.neoforged.fml.loading.LogMarkers;
import net.neoforged.fml.util.ServiceLoaderUtil;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.connector.transformer.api.TransformerContext;
import org.sinytra.connector.transformer.api.TransformerPlugin;
import org.sinytra.connector.transformer.api.TransformerRegistrar;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class PluginManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static PluginManager instance;

    private final List<TransformerPlugin> plugins;

    public PluginManager(List<TransformerPlugin> plugins) {
        this.plugins = plugins;
    }

    public static PluginManager getInstance() {
        return Objects.requireNonNull(instance, "Plugin manager not initialized");
    }

    public static void initialize() {
        List<TransformerPlugin> plugins = loadServices(TransformerPlugin.class);

        instance = new PluginManager(plugins);
    }

    public List<MethodPatch> gatherMethodPatches(TransformerContext context) {
        PatchRegistrarImpl registrar = new PatchRegistrarImpl();

        for (TransformerPlugin plugin : this.plugins) {
            plugin.registerPatches(registrar, context);
        }

        return registrar.getSortedPatches();
    }

    public List<Transformer> gatherJarTranformers(TransformerContext context, Consumer<TransformerRegistrar> extras) {
        TransformerRegistrarImpl registrar = new TransformerRegistrarImpl();
        extras.accept(registrar);

        for (TransformerPlugin plugin : this.plugins) {
            plugin.registerJarTransformers(registrar, context);
        }

        return registrar.getSortedTransformers();
    }

    private static <T> List<T> loadServices(Class<T> serviceClass) {
        Stream<T> servicesStream = ServiceLoader.load(serviceClass).stream()
            .map(p -> {
                try {
                    return p.get();
                } catch (ServiceConfigurationError sce) {
                    LOGGER.error("Failed to load implementation for {}", serviceClass, sce);
                    return null;
                }
            })
            .filter(Objects::nonNull);

        List<T> services = servicesStream.toList();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Found {} implementations of {}:", services.size(), serviceClass.getSimpleName());
            for (T service : services) {
                String priorityPrefix = "";

                LOGGER.debug(LogMarkers.CORE, "\t{}{}", priorityPrefix, identifyService(service));
            }
        }

        return services;
    }

    private static String identifyService(Object o) {
        String sourcePath = ServiceLoaderUtil.identifySourcePath(o);
        return o.getClass().getName() + " from " + sourcePath;
    }
}
