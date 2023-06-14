package dev.su5ed.connector.mod.mixin;

import dev.su5ed.connector.loader.ConnectorEarlyLoader;
import dev.su5ed.connector.mod.DelayedRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.GameData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"), remap = false)
    private void earlyInit(GameConfig gameConfig, CallbackInfo ci) {
        // These seemingly pointless accesses are done to make sure each
        // static initializer is called, to register vanilla-provided blocks
        // and items from the respective classes - otherwise, they would
        // duplicate our calls from below.
        Object oBlock = Blocks.AIR;
        Object oFluid = Fluids.EMPTY;
        Object oItem = Items.AIR;

        ConnectorEarlyLoader.load();

        DelayedRegistrar.finishRegistering();
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/client/loading/ClientModLoader;begin(Lnet/minecraft/client/Minecraft;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void onFinishInitClient(CallbackInfo ci) {
        // Lock the registries now
        BuiltInRegistries.bootStrap();
        GameData.vanillaSnapshot();
    }
}
