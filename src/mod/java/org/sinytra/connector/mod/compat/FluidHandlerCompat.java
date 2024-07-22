package org.sinytra.connector.mod.compat;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class FluidHandlerCompat {
    private static final Map<Fluid, FluidType> FABRIC_FLUID_TYPES = new HashMap<>();
    private static final Map<ResourceLocation, FluidType> FABRIC_FLUID_TYPES_BY_NAME = new HashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init(IEventBus bus) {
        initFabricFluidTypes();
        bus.addListener(FluidHandlerCompat::onRegisterFluids);
    }

    static Map<Fluid, FluidType> getFabricFluidTypes() {
        return FABRIC_FLUID_TYPES;
    }

    public static FluidType getFabricFluidType(Fluid fluid) {
        FluidType type = FABRIC_FLUID_TYPES.get(fluid);
        if (type == null) {
            LOGGER.warn("Missing FluidType for fluid {}", fluid);
        }
        return type;
    }

    private static void initFabricFluidTypes() {
        for (Map.Entry<ResourceKey<Fluid>, Fluid> entry : BuiltInRegistries.FLUID.entrySet()) {
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
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> FABRIC_FLUID_TYPES_BY_NAME.forEach(helper::register));
    }

    static class FabricFluidType extends FluidType {
        @Nullable
        private final FluidRenderHandler renderHandler;
        private final Component name;

        public FabricFluidType(Properties properties, Fluid fluid, @Nullable FluidRenderHandler renderHandler) {
            super(properties);
            this.renderHandler = renderHandler;
            this.name = FluidVariantAttributes.getName(FluidVariant.of(fluid));
        }

        public FluidRenderHandler getRenderHandler() {
            return renderHandler;
        }

        @Override
        public Component getDescription() {
            return this.name.copy();
        }

        @Override
        public Component getDescription(FluidStack stack) {
            return FluidVariantAttributes.getName(FluidVariant.of(stack.getFluid(), stack.getComponentsPatch()));
        }
    }

    private FluidHandlerCompat() {}
}
