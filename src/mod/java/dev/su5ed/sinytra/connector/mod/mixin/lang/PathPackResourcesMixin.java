package dev.su5ed.sinytra.connector.mod.mixin.lang;

import dev.su5ed.sinytra.connector.mod.compat.PathPackResourcesExtensions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraftforge.resource.PathPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {
    @Shadow
    private static String[] getPathFromLocation(PackType type, ResourceLocation location) {
        throw new UnsupportedOperationException();
    }

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void getFabricLangResources(PackType type, ResourceLocation location, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (this instanceof PathPackResourcesExtensions ext && ext.connector_isFabricMod() && location.getPath().startsWith("lang/")) {
            IoSupplier<InputStream> serverLang = ((PackResources) this).getRootResource(getPathFromLocation(PackType.SERVER_DATA, location));
            if (serverLang != null) {
                cir.setReturnValue(serverLang);
            }
        }
    }
}
