package org.sinytra.connector.mod.mixin.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.packs.resources.FallbackResourceManager$PackEntry")
public interface FallbackResourceManagerPackEntryAccessor {
    @Nullable
    @Accessor
    PackResources getResources();

    @Invoker
    boolean invokeIsFiltered(Identifier location);
}
