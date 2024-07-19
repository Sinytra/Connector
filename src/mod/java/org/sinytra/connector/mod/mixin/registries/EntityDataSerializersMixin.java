package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import org.sinytra.connector.mod.ConnectorLoader;
import org.sinytra.connector.mod.compat.EntityDataSerializersRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDataSerializers.class)
public abstract class EntityDataSerializersMixin {

    @Inject(method = "registerSerializer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;)V"), cancellable = true)
    private static void onRegisterModdedSerializer(EntityDataSerializer<?> serializer, CallbackInfo ci) {
        if (ConnectorLoader.isLoading()) {
            EntityDataSerializersRegistry.register(serializer);
            ci.cancel();
        }
    }
}
