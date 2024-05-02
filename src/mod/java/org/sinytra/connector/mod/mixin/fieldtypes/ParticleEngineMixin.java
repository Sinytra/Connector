package org.sinytra.connector.mod.mixin.fieldtypes;

import org.sinytra.connector.mod.compat.fieldtypes.RedirectingInt2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@SuppressWarnings("unused")
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ParticleProvider<?>> providers;
    @Unique
    private Int2ObjectMap<ParticleProvider<?>> connector$providers;

    @Unique
    public Int2ObjectMap<ParticleProvider<?>> connector$getProviders() {
        if (this.connector$providers == null) {
            this.connector$providers = new RedirectingInt2ObjectMap<>(
                    i -> BuiltInRegistries.PARTICLE_TYPE.getKey(BuiltInRegistries.PARTICLE_TYPE.byId(i)),
                    key -> BuiltInRegistries.PARTICLE_TYPE.getId(BuiltInRegistries.PARTICLE_TYPE.get(key)),
                    this.providers
            );
        }
        return this.connector$providers;
    }
}
