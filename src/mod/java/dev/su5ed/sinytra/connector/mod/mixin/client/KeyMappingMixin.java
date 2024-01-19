package dev.su5ed.sinytra.connector.mod.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyMappingLookup;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mixin(KeyMapping.class)
public class KeyMappingMixin {
    @Final
    @Shadow
    private static KeyMappingLookup MAP;

    @Shadow(remap = false, aliases = "f_90810_")
    private static final Map<InputConstants.Key, KeyMapping> vanillaKeyMapping;
    
    static {
        // Forge changes the signature of f_90810_ (MAP) so, as such, some mods may encounter issues
        // if they use the field in their code or otherwise reflect (see voxelmap)
        // The field is added back through a coremod, and here it is semi-delegated
        final EnumMap<KeyModifier, Map<InputConstants.Key, Collection<KeyMapping>>> actualMap = ObfuscationReflectionHelper
                .getPrivateValue(KeyMappingLookup.class, MAP, "map");
        final var delegate = actualMap.get(KeyModifier.NONE);
        vanillaKeyMapping = new Map<>() {
            @Override
            public int size() {
                return delegate.size();
            }

            @Override
            public boolean isEmpty() {
                return delegate.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return delegate.containsKey(key);
            }

            @Override
            public boolean containsValue(Object value) {
                return delegate.values().stream().anyMatch(v -> v.contains(value));
            }

            @Override
            public KeyMapping get(Object key) {
                final var values = delegate.get(key);
                return values == null || values.isEmpty() ? null : ((ArrayList<KeyMapping>) values).get(0);
            }

            @Nullable
            @Override
            public KeyMapping put(InputConstants.Key key, KeyMapping value) {
                final var old = MAP.get(key);
                MAP.put(key, value);
                return old;
            }

            @Override
            public KeyMapping remove(Object key) {
                final InputConstants.Key actualKey = (InputConstants.Key) key;
                final var old = MAP.get(actualKey);
                delegate.remove(key);
                return old;
            }

            @Override
            public boolean remove(Object key, Object value) {
                Object curValue = get(key);
                if (!Objects.equals(curValue, value) ||
                        (curValue == null && !containsKey(key))) {
                    return false;
                }
                MAP.remove((KeyMapping) value);
                return true;
            }

            @Override
            public void putAll(@NotNull Map<? extends InputConstants.Key, ? extends KeyMapping> m) {
                m.forEach(MAP::put);
            }

            @Override
            public void clear() {
                delegate.clear();
            }

            @NotNull
            @Override
            public Set<InputConstants.Key> keySet() {
                return delegate.keySet();
            }

            @NotNull
            @Override
            public Collection<KeyMapping> values() {
                return delegate.values()
                        .stream().flatMap(Collection::stream)
                        .toList();
            }

            @NotNull
            @Override
            public Set<Entry<InputConstants.Key, KeyMapping>> entrySet() {
                return delegate.entrySet().stream()
                        .filter(e -> !e.getValue().isEmpty())
                        .collect(Collectors.toMap(
                                Entry::getKey,
                                e -> ((ArrayList<KeyMapping>) e.getValue()).get(0)
                        ))
                        .entrySet();
            }
        };
    }
}
