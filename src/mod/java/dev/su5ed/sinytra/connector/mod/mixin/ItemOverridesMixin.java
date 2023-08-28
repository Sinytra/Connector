package dev.su5ed.sinytra.connector.mod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

@Mixin(ItemOverrides.class)
public class ItemOverridesMixin {

    @Redirect(method = "<init>(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBaker;getModelTextureGetter()Ljava/util/function/Function;"))
    private static Function<Material, TextureAtlasSprite> redirectNullBakerModelTextureGetter(ModelBaker instance) {
        if (instance == null) {
            return material -> Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(MissingTextureAtlasSprite.getLocation());
        }
        return instance.getModelTextureGetter();
    }
}
