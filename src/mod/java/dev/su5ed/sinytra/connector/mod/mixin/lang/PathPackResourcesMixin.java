package dev.su5ed.sinytra.connector.mod.mixin.lang;

import dev.su5ed.sinytra.connector.mod.compat.PathPackResourcesExtensions;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.resource.PathPackResources;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PathPackResources.class)
public class PathPackResourcesMixin {
    @Redirect(method = "getResource", at = @At(value = "FIELD", target = "Lnet/minecraft/server/packs/PackType;CLIENT_RESOURCES:Lnet/minecraft/server/packs/PackType;", opcode = Opcodes.GETSTATIC))
    public PackType modifyResourceType(PackType type) {
        if (this instanceof PathPackResourcesExtensions ext && ext.connector_isFabricMod()) {
            return type;
        }
        return PackType.CLIENT_RESOURCES;
    }
}
