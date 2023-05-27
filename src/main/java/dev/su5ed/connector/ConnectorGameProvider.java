package dev.su5ed.connector;

import net.fabricmc.loader.impl.game.GameProvider;
import net.fabricmc.loader.impl.game.patch.GameTransformer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.util.Arguments;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.versions.mcp.MCPVersion;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public class ConnectorGameProvider implements GameProvider {
    @Override
    public String getGameId() {
        return "minecraft";
    }

    @Override
    public String getGameName() {
        return "Minecraft";
    }

    @Override
    public String getRawGameVersion() {
        return MCPVersion.getMCVersion();
    }

    @Override
    public String getNormalizedGameVersion() {
        return getRawGameVersion();
    }

    @Override
    public Collection<BuiltinMod> getBuiltinMods() {
        return List.of();
    }

    @Override
    public String getEntrypoint() {
        return null;
    }

    @Override
    public Path getLaunchDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public boolean isObfuscated() {
        return FMLEnvironment.production;
    }

    @Override
    public boolean requiresUrlClassLoader() {
        return false;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean locateGame(FabricLauncher launcher, String[] args) {
        return false;
    }

    @Override
    public void initialize(FabricLauncher launcher) {}

    @Override
    public GameTransformer getEntrypointTransformer() {
        return null;
    }

    @Override
    public void unlockClassPath(FabricLauncher launcher) {}

    @Override
    public void launch(ClassLoader loader) {}

    @Override
    public Arguments getArguments() {
        return new Arguments();
    }

    @Override
    public String[] getLaunchArguments(boolean sanitize) {
        return new String[0];
    }
}
