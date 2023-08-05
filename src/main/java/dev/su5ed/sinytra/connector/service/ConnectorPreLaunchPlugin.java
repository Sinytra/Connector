package dev.su5ed.sinytra.connector.service;

import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.su5ed.sinytra.connector.loader.ConnectorExceptionHandler;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.EnumSet;

public class ConnectorPreLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "connector_pre_launch";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        try {
            // Get GAME class loader
            ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
            // Find ConnectorLoader class on GAME layer
            Class<?> cls = gameLoader.loadClass("dev.su5ed.sinytra.connector.mod.ConnectorLoader");
            // Get static setup() method
            Method setup = cls.getDeclaredMethod("setup");
            // Invoke setup
            setup.invoke(null);
        } catch (Throwable t) {
            ConnectorExceptionHandler.addSuppressed(t);
        }
    }
}
