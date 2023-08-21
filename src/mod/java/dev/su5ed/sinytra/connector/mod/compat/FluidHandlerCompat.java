package dev.su5ed.sinytra.connector.mod.compat;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.impl.client.rendering.fluid.FluidRenderHandlerRegistryImpl;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class FluidHandlerCompat {

    public static void onClientSetup(FMLClientSetupEvent event) {
        // Use reflection to register forge handlers only to the "handlers" map and not "modHandlers"
        // This allows fabric mods to access render handlers for forge mods' fluids without them being
        // used for rendering fluids, as that should remain handled by forge
        Map<Fluid, FluidRenderHandler> registryHandlers = ObfuscationReflectionHelper.getPrivateValue(FluidRenderHandlerRegistryImpl.class, (FluidRenderHandlerRegistryImpl) FluidRenderHandlerRegistry.INSTANCE, "handlers");
        Map<FluidType, FluidRenderHandler> forgeHandlers = new HashMap<>();
        for (Map.Entry<ResourceKey<Fluid>, Fluid> entry : ForgeRegistries.FLUIDS.getEntries()) {
            Fluid fluid = entry.getValue();
            if (fluid != Fluids.EMPTY) {
                ResourceKey<Fluid> key = entry.getKey();
                if (!ConnectorEarlyLoader.isConnectorMod(key.location().getNamespace())) {
                    FluidRenderHandler handler = forgeHandlers.computeIfAbsent(fluid.getFluidType(), ForgeFluidRenderHandler::new);
                    registryHandlers.put(fluid, handler);
                }
            }
        }
    }

    private record ForgeFluidRenderHandler(FluidType fluidType) implements FluidRenderHandler {
        @Override
        public TextureAtlasSprite[] getFluidSprites(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            TextureAtlasSprite[] forgeSprites = ForgeHooksClient.getFluidSprites(view, pos, state);
            return forgeSprites[2] == null ? Arrays.copyOfRange(forgeSprites, 0, 2) : forgeSprites;
        }

        @Override
        public int getFluidColor(@Nullable BlockAndTintGetter view, @Nullable BlockPos pos, FluidState state) {
            int color = IClientFluidTypeExtensions.of(this.fluidType).getTintColor(state, view, pos);
            return 0x00FFFFFF & color;
        }
    }

    private FluidHandlerCompat() {}
}
