package dev.su5ed.sinytra.connector.mod.mixin.fieldtypes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Map;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, ParticleProvider<?>> providers;
    @Unique
    private final Int2ObjectMap<ParticleProvider<?>> connector$providers = new Int2ObjectOpenHashMap<>();

    @Unique
    public Int2ObjectMap<ParticleProvider<?>> connector$getProviders() {
        if (this.providers.size() != this.connector$providers.size()) {
            if (this.providers.size() < this.connector$providers.size()) throw new IllegalStateException("Tried to register new providers using connector$getProviders()!");
            this.providers.forEach((location, provider) -> {
                //Double conversion, but oh well
                this.connector$providers.putIfAbsent(BuiltInRegistries.PARTICLE_TYPE.getId(BuiltInRegistries.PARTICLE_TYPE.get(location)), provider);
            });
        }
        return connector$providers;
    }
}
