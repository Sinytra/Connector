package org.sinytra.connector.mod.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

@Mixin(ItemOverrides.class)
public abstract class ItemOverridesMixin {
    // See: https://github.com/Sinytra/Connector/issues/72
    // https://github.com/Parzivail-Modding-Team/GalaxiesParzisStarWarsMod/blob/3dd6b1b39b782ccb5085331545f0bd760b356f4c/projects/pswg/src/main/java/com/parzivail/util/client/model/DynamicBakedModel.java#L157
    @Redirect(method = "<init>(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelBaker;getModelTextureGetter()Ljava/util/function/Function;"))
    private static Function<Material, TextureAtlasSprite> redirectNullBakerModelTextureGetter(ModelBaker instance) {
        if (instance == null) {
            return material -> Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(MissingTextureAtlasSprite.getLocation());
        }
        return instance.getModelTextureGetter();
    }
}
