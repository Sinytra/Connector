/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.su5ed.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Bootstrap.class)
public abstract class BootstrapMixin {
    @Inject(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/registries/BuiltInRegistries;bootStrap()V", shift = At.Shift.AFTER))
    private static void initialize(CallbackInfo ci) {
        // Unfreeze registries
        // We let the get frozen first as some forge mods may rely on registering their contents in the BuiltInRegistries.freeze() method
        ((MappedRegistry<?>) BuiltInRegistries.REGISTRY).unfreeze();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            ((MappedRegistry<?>) registry).unfreeze();
        }
    }

    @Redirect(method = "bootStrap", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/GameData;vanillaSnapshot()V", remap = false))
    private static void skipVanillaSnapshot() {
        // Don't lock registries just yet
    }
}
