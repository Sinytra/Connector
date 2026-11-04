package org.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.Launcher;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Runtime detection for Mohist, which forks securejarhandler and ModLauncher internals.
 */
public final class MohistCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Launch plugin service id that only Mohist's ModLauncher fork registers.
     */
    private static final String MOHIST_PLUGIN_ID = "mohist_implementer";

    private static final String[] MOHIST_CLASSES = {"com.mohistmc.MohistMCStart", "com.mohistmc.MohistMC"};

    private static Boolean mohistRuntime;

    private MohistCompat() {
    }

    public static boolean isMohistRuntime() {
        if (mohistRuntime == null) {
            mohistRuntime = detect();
            LOGGER.debug("Mohist runtime detection: {}", mohistRuntime);
        }
        return mohistRuntime;
    }

    private static boolean detect() {
        return hasMohistLaunchPlugin() || hasMohistOnClasspath();
    }

    /**
     * Reflective so we do not need ILaunchPluginService, which is not on the compile classpath.
     */
    private static boolean hasMohistLaunchPlugin() {
        try {
            Field launchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launchPluginsField.setAccessible(true);
            Object raw = launchPluginsField.get(Launcher.INSTANCE);
            if (!(raw instanceof List<?> plugins)) {
                return false;
            }
            for (Object plugin : plugins) {
                if (plugin == null) {
                    continue;
                }
                Method idMethod;
                try {
                    idMethod = plugin.getClass().getMethod("id");
                    idMethod.setAccessible(true);
                } catch (Throwable ignored) {
                    continue;
                }
                Object id;
                try {
                    id = idMethod.invoke(plugin);
                } catch (Throwable ignored) {
                    continue;
                }
                if (MOHIST_PLUGIN_ID.equals(id)) {
                    return true;
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Mohist launch-plugin probe failed", t);
        }
        return false;
    }

    private static boolean hasMohistOnClasspath() {
        // The Mohist server jar is on the application classpath, not on Connector's module layer,
        // so the system classloader has to be probed explicitly.
        List<ClassLoader> loaders = List.of(
                MohistCompat.class.getClassLoader(),
                ClassLoader.getSystemClassLoader(),
                ClassLoader.getPlatformClassLoader()
        );
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            for (String name : MOHIST_CLASSES) {
                try {
                    Class.forName(name, false, loader);
                    return true;
                } catch (Throwable ignored) {
                    // try the next probe
                }
            }
        }
        return false;
    }
}
