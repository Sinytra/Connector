package dev.su5ed.sinytra.connector.mod.compat;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Patched into mods using {@link dev.su5ed.sinytra.connector.transformer.MixinPatchTransformer}
 */
@SuppressWarnings("unused")
public final class CompatUtil {
    private static final String LAYER_SEPARATOR = "_layer_";

    @Nullable
    public static String getArmorBasePath(ResourceLocation location) {
        String path = location.getPath();
        int idx = path.indexOf(LAYER_SEPARATOR);
        if (idx != -1 && path.endsWith(".png")) {
            String original = path.substring(idx + LAYER_SEPARATOR.length() + 1, path.length() - 4);
            return original.isEmpty() ? null : original;
        }
        return path;
    }
    
    private CompatUtil() {}
}
