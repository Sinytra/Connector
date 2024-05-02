package org.sinytra.connector.mod.compat;

import com.mojang.logging.LogUtils;
import org.sinytra.connector.loader.ConnectorEarlyLoader;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class FluidHandlerCompat {
    private static final Map<Fluid, FluidType> FABRIC_FLUID_TYPES = new HashMap<>();
    private static final Map<ResourceLocation, FluidType> FABRIC_FLUID_TYPES_BY_NAME = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init(IEventBus bus) {
        initFabricFluidTypes();
        bus.addListener(FluidHandlerCompat::onRegisterFluids);
    }

    public static FluidType getFabricFluidType(Fluid fluid) {
        FluidType type = FABRIC_FLUID_TYPES.get(fluid);
        if (type == null) {
            LOGGER.warn("Missing FluidType for fluid {}", fluid);
        }
        return type;
    }

    private static void initFabricFluidTypes() {
        for (Map.Entry<ResourceKey<Fluid>, Fluid> entry : ForgeRegistries.FLUIDS.getEntries()) {
            // Allow Forge mods to access Fabric fluid properties
            ResourceKey<Fluid> key = entry.getKey();
            Fluid fluid = entry.getValue();
            if (ModList.get().getModContainerById(key.location().getNamespace()).map(c -> ConnectorEarlyLoader.isConnectorMod(c.getModId())).orElse(false)) {
                FluidRenderHandler renderHandler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
                FluidType type = new FabricFluidType(FluidType.Properties.create(), fluid, renderHandler);
                FABRIC_FLUID_TYPES.put(fluid, type);
                FABRIC_FLUID_TYPES_BY_NAME.put(key.location(), type);
            }
        }
    }

    private static void onRegisterFluids(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.FLUID_TYPES, helper -> FABRIC_FLUID_TYPES_BY_NAME.forEach(helper::register));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static class FabricFluidType extends FluidType {
        private final Fluid fluid;
        @Nullable
        private final FluidRenderHandler renderHandler;
        private final Component name;

        public FabricFluidType(Properties properties, Fluid fluid, @Nullable FluidRenderHandler renderHandler) {
            super(properties);
            this.fluid = fluid;
            this.renderHandler = renderHandler;
            this.name = FluidVariantAttributes.getName(FluidVariant.of(fluid));
        }

        @Override
        public Component getDescription() {
            return this.name.copy();
        }

        @Override
        public Component getDescription(FluidStack stack) {
            return FluidVariantAttributes.getName(FluidVariant.of(stack.getFluid(), stack.getTag()));
        }

        @Override
        public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
            consumer.accept(new IClientFluidTypeExtensions() {
                private TextureAtlasSprite[] getSprites() {
                    return renderHandler.getFluidSprites(null, null, fluid.defaultFluidState());
                }

                @Override
                public ResourceLocation getStillTexture() {
                    TextureAtlasSprite[] sprites = getSprites();
                    return sprites[0].contents().name();
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    TextureAtlasSprite[] sprites = getSprites();
                    return sprites[1].contents().name();
                }

                @Nullable
                @Override
                public ResourceLocation getOverlayTexture() {
                    TextureAtlasSprite[] sprites = getSprites();
                    return sprites.length > 2 ? sprites[2].contents().name() : null;
                }

                @Override
                public int getTintColor() {
                    int baseColor = renderHandler.getFluidColor(null, null, fluid.defaultFluidState());
                    return 0xFF000000 | baseColor;
                }

                @Override
                public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                    int baseColor = renderHandler.getFluidColor(getter, pos, state);
                    return 0xFF000000 | baseColor;
                }
            });
        }
    }

    private FluidHandlerCompat() {}
}
