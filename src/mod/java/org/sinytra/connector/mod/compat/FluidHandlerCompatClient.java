package org.sinytra.connector.mod.compat;

import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;

public final class FluidHandlerCompatClient {

    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        FluidHandlerCompat.getFabricFluidTypes().forEach((fluid, type) -> {
            FluidRenderHandler renderHandler = ((FluidHandlerCompat.FabricFluidType) type).getRenderHandler();
            event.registerFluidType(new IClientFluidTypeExtensions() {
                private TextureAtlasSprite[] getSprites() {
                    return renderHandler.getFluidSprites(null, null, fluid.defaultFluidState());
                }

                @Override
                public ResourceLocation getStillTexture() {
                    TextureAtlasSprite[] sprites = getSprites();
                    return sprites[0] != null ? sprites[0].contents().name() : null;
                }

                @Override
                public ResourceLocation getFlowingTexture() {
                    TextureAtlasSprite[] sprites = getSprites();
                    return sprites[1] != null ? sprites[1].contents().name() : null;
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
            }, type);
        });
    }

    private FluidHandlerCompatClient() {}
}
