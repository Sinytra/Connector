package org.sinytra.connector.mod.mixin.registries;

import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.mod.ConnectorMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explains the failure Fabric mods hit most often on this loader: writing into a registry that NeoForge already
 * froze. Nothing is cancelled, deferred or relaxed here - the exception is still thrown exactly as before, this
 * only adds a log line that says which mod did it and why the same code works on Fabric.
 * <p>
 * Fabric's fabric-registry-sync redirects {@code BuiltInRegistries.bootStrap()} inside {@code Bootstrap} to
 * {@code createContents()} and calls the real bootstrap later from {@code Main}, i.e. after mod initializers have
 * run. NeoForge keeps the vanilla order and freezes during bootstrap, before mods are constructed. A mod that
 * registers from a {@code BuiltInRegistries.freeze()} hook, or from a late mod-init path, therefore fails here.
 */
@Mixin(MappedRegistry.class)
public abstract class FrozenRegistryWriteMixin {
    // Only known reliable attribution at this point: the namespace of the key being registered is the mod id for
    // essentially every Fabric mod. Resolving the calling class instead would need a class load or a stack walk,
    // and the entry point can register on behalf of another mod anyway.
    @Inject(method = "validateWrite(Lnet/minecraft/resources/ResourceKey;)V", at = @At("HEAD"))
    private void connector$explainFrozenWrite(ResourceKey<?> key, CallbackInfo ci) {
        if (!((MappedRegistryAccessor) (Object) this).getFrozen()) return;

        Identifier id = key.identifier();
        if (!ConnectorEarlyLoader.isConnectorMod(id.getNamespace())) return;

        ConnectorMod.LOGGER.error("""
            [Connector] The Fabric mod '{}' is registering '{}' into '{}' after that registry was frozen.
            Fabric freezes BuiltInRegistries only after mod initializers have run (fabric-registry-sync defers the \
            freeze from Bootstrap to Main), while NeoForge freezes them during Bootstrap, i.e. before mods are \
            constructed. Registering from a BuiltInRegistries.freeze() hook, or from a late mod-init path, \
            therefore fails here even though the same code works on Fabric.
            Supported alternatives: FabricRegistryBuilder.buildAndRegister() - Forgified Fabric API implements the \
            unfreeze -> register -> freeze sequence and the modded-registry bookkeeping for it - or NeoForge's \
            NewRegistryEvent / RegisterEvent.""",
            id.getNamespace(), id, key.registry());
    }
}
