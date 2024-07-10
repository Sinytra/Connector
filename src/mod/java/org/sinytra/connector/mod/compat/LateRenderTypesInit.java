package org.sinytra.connector.mod.compat;

import net.minecraft.client.renderer.RenderType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class LateRenderTypesInit {
    private static final VarHandle CHUNK_LAYER_ID = uncheck(() -> MethodHandles.privateLookupIn(RenderType.class, MethodHandles.lookup()).findVarHandle(RenderType.class, "chunkLayerId", int.class));

    public static void regenerateRenderTypeIds() {
        int i = 0;
        for (var layer : RenderType.chunkBufferLayers()) {
            int id = i++;
            uncheck(() -> CHUNK_LAYER_ID.set(layer, id));
        }
    }
}
