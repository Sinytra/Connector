package dev.su5ed.sinytra.connector.mod.mixin.tags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TagEntry.class)
public interface TagEntryAccessor {
    @Invoker("<init>")
    static TagEntry create(ResourceLocation id, boolean tag, boolean required) {
        throw new UnsupportedOperationException();
    }
}
