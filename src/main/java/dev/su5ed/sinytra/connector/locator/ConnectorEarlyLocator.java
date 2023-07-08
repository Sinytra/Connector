package dev.su5ed.sinytra.connector.locator;

import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.ModDiscoverer;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConnectorEarlyLocator implements IModLocator {
    private static final String NAME = "connector_early_locator";
    
    @Override
    public List<ModFileOrException> scanMods() {
        // Unfortunately, FML doesn't provide a way to sort mod/dependency locators by priority, so we have to create our own
        try {
            Method method = FMLLoader.class.getDeclaredMethod("getModDiscoverer");
            method.setAccessible(true);
            ModDiscoverer discoverer = (ModDiscoverer) method.invoke(null);
            Field field = ModDiscoverer.class.getDeclaredField("dependencyLocatorList");
            field.setAccessible(true);
            List<IDependencyLocator> dependencyLocatorList = (List<IDependencyLocator>) field.get(discoverer);
            dependencyLocatorList.sort(Comparator.comparingInt(loc -> loc instanceof ConnectorLocator ? 1 : 0));
        } catch (Throwable t) {
            // TODO Set early loader exception
            throw new RuntimeException(t);
        }
        return List.of();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {}

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    @Override
    public boolean isValid(IModFile modFile) {
        return false;
    }
}
