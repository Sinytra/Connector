package org.sinytra.connector.mod.mixin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "load(Z)V", at = {
        @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),
        @At(value = "RETURN", ordinal = 0)
    }, remap = false)
    private void onForgeReLoad(boolean limited, CallbackInfo ci) {
        if (limited) {
            // Remove duplicate keybindings
            Set<String> seen = new HashSet<>();
            IntSet toRemove = new IntOpenHashSet();
            for (int i = 0; i < this.keyMappings.length; i++) {
                KeyMapping mapping = this.keyMappings[i];
                if (!seen.add(mapping.getName())) {
                    toRemove.add(i);
                }
            }
            this.keyMappings = ArrayUtils.removeAll(this.keyMappings, toRemove.toIntArray());
        }
    }
}
