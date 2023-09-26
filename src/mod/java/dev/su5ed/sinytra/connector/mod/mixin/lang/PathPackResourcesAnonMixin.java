package dev.su5ed.sinytra.connector.mod.mixin.lang;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.sinytra.connector.mod.compat.PathPackResourcesExtensions;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.List;

@Mixin(targets = "net/minecraftforge/resource/ResourcePackLoader$1")
public class PathPackResourcesAnonMixin implements PathPackResourcesExtensions {
    @Unique
    private boolean connector_isFabricMod;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(String packId, boolean isBuiltin, Path source, IModFileInfo modFile, CallbackInfo ci) {
        List<IModInfo> mods = modFile.getMods();
        if (!mods.isEmpty()) {
            connector_isFabricMod = ConnectorEarlyLoader.isConnectorMod(mods.get(0).getModId());
        }
    }

    @Override
    public boolean connector_isFabricMod() {
        return this.connector_isFabricMod;
    }
}
