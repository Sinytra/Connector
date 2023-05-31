package dev.su5ed.connector.mock;

import dev.su5ed.connector.ConnectorUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.jar.Manifest;

public class ConnectorFabricLauncher extends FabricLauncherBase {

    public static void inject() {
        new ConnectorFabricLauncher();
    }

    public ConnectorFabricLauncher() {
        super();
    }

    @Override
    public void addToClassPath(Path path, String... allowedPrefixes) {}

    @Override
    public void setAllowedPrefixes(Path path, String... prefixes) {}

    @Override
    public void setValidParentClassPath(Collection<Path> paths) {}

    @Override
    public EnvType getEnvironmentType() {
        return ConnectorUtil.getEnvType();
    }

    @Override
    public boolean isClassLoaded(String name) {
        return false;
    }

    @Override
    public Class<?> loadIntoTarget(String name) throws ClassNotFoundException {
        return null;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return null;
    }

    @Override
    public ClassLoader getTargetClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    @Override
    public byte[] getClassByteArray(String name, boolean runTransformers) throws IOException {
        return new byte[0];
    }

    @Override
    public Manifest getManifest(Path originPath) {
        return null;
    }

    @Override
    public boolean isDevelopment() {
        return !FMLEnvironment.production;
    }

    @Override
    public String getEntrypoint() {
        return null;
    }

    @Override
    public String getTargetNamespace() {
        return null;
    }

    @Override
    public List<Path> getClassPath() {
        return List.of();
    }
}
