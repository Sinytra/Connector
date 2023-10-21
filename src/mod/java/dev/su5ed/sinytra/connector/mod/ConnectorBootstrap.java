package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.service.FabricASMFixer;
import dev.su5ed.sinytra.connector.service.FabricMixinBootstrap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Serves purely as a pre-launch entrypoint, allowing us to conveniently prepare Fabric Loader and boostrap Mixin config decorations before game classes are loaded
 */
public class ConnectorBootstrap implements IMixinConfigPlugin {

    static {
        ConnectorEarlyLoader.setup();
        FabricMixinBootstrap.init();
        FabricASMFixer.injectMinecraftModuleReader();
    }

    // We don't need any of the mixin stuff
    //@formatter:off
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return null;}
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return true;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {return null;}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    //@formatter:on
}
