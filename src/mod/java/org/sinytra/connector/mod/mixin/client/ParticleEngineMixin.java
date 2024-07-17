package org.sinytra.connector.mod.mixin.client;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.sinytra.connector.mod.compat.fieldtypes.RedirectingInt2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ParticleProvider<?>> providers;

    // Added via coremod
    @Shadow(aliases = { "providers" })
    private Int2ObjectMap<ParticleProvider<?>> connector$providers;

    @Inject(method = "<init>", at = @At(value = "FIELD", target = "Lnet/minecraft/client/particle/ParticleEngine;providers:Ljava/util/Map;", shift = At.Shift.AFTER))
    private void onInit(ClientLevel level, TextureManager textureManager, CallbackInfo ci) {
        connector$providers = new RedirectingInt2ObjectMap<>(
            id -> BuiltInRegistries.PARTICLE_TYPE.getKey(BuiltInRegistries.PARTICLE_TYPE.byId(id)),
            key -> BuiltInRegistries.PARTICLE_TYPE.getId(BuiltInRegistries.PARTICLE_TYPE.get(key)),
            this.providers
        );
    }
}
