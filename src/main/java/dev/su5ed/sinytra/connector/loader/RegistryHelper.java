package dev.su5ed.sinytra.connector.loader;

/**
 * Acts as a bridge to the GAME module layer, allowing us to interact with minecraft classes.
 * This is consumed as a {@link java.util.ServiceLoader} interface.
 */
public interface RegistryHelper {
    /**
     * Unfreeze all vanilla registries registered in {@link net.minecraft.core.registries.BuiltInRegistries#REGISTRY},
     * including the root registry.
     */
    void unfreezeRegistries();

    /**
     * Freeze all vanilla registries registered in {@link net.minecraft.core.registries.BuiltInRegistries#REGISTRY},
     * including the root registry.
     */
    void freezeRegistries();
}
