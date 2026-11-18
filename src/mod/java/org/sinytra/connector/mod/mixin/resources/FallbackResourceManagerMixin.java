package org.sinytra.connector.mod.mixin.resources;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.mod.compat.TrimmedArmorItemDefinitions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.io.InputStream;
import java.util.List;

// Use priority 1500 so Connector wraps around or after Sodium's resource manager hooks
@Mixin(value = FallbackResourceManager.class, priority = 1500)
public abstract class FallbackResourceManagerMixin {
    @Shadow
    @Final
    public List<?> fallbacks;
    @Shadow
    @Final
    private PackType type;

    @WrapOperation(
        method = {"getResource", "getResourceStack"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/PackResources;getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;"
        ),
        require = 0
    )
    private IoSupplier<InputStream> wrapPackResource(PackResources pack, PackType type, Identifier location, Operation<IoSupplier<InputStream>> original) {
        return connector$wrapItemDefinition(pack, location, original.call(pack, type, location));
    }

    @WrapOperation(
        method = {"listResources", "listPackResources"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/PackResources;listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V"
        ),
        require = 0
    )
    private void wrapListedPackResources(PackResources pack, PackType type, String namespace, String directory, PackResources.ResourceOutput output, Operation<Void> original) {
        original.call(pack, type, namespace, directory, (PackResources.ResourceOutput) (location, resource) ->
            output.accept(location, connector$wrapItemDefinition(pack, location, resource))
        );
    }

    @Unique
    private IoSupplier<InputStream> connector$wrapItemDefinition(PackResources pack, Identifier location, @Nullable IoSupplier<InputStream> resource) {
        if (resource == null || this.type != PackType.CLIENT_RESOURCES || !TrimmedArmorItemDefinitions.isItemDefinition(location)) {
            return resource;
        }
        List<?> entries = this.fallbacks;
        int packIndex = connector$getPackIndex(entries, pack);
        if (packIndex <= 0) {
            return resource;
        }
        return TrimmedArmorItemDefinitions.wrap(resource, () -> connector$findOverriddenResource(entries, packIndex, location));
    }

    @Unique
    private int connector$getPackIndex(List<?> entries, PackResources pack) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) instanceof FallbackResourceManagerPackEntryAccessor accessor && accessor.getResources() == pack) {
                return i;
            }
        }
        return -1;
    }

    @Unique
    @Nullable
    private IoSupplier<InputStream> connector$findOverriddenResource(List<?> entries, int packIndex, Identifier location) {
        if (packIndex >= entries.size()) {
            return null;
        }
        if (entries.get(packIndex) instanceof FallbackResourceManagerPackEntryAccessor current && current.invokeIsFiltered(location)) {
            return null;
        }
        for (int i = Math.min(packIndex - 1, entries.size() - 1); i >= 0; i--) {
            if (!(entries.get(i) instanceof FallbackResourceManagerPackEntryAccessor entry)) {
                continue;
            }
            PackResources pack = entry.getResources();
            if (pack != null) {
                IoSupplier<InputStream> resource = pack.getResource(this.type, location);
                if (resource != null) {
                    return resource;
                }
            }
            if (entry.invokeIsFiltered(location)) {
                return null;
            }
        }
        return null;
    }
}

