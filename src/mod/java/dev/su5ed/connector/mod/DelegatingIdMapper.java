package dev.su5ed.connector.mod;

import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@SuppressWarnings("unused")
public class DelegatingIdMapper<K, V> extends IdMapper<V> {
    private final Map<Holder.Reference<K>, V> blockColors;
    private final Registry<K> registry;

    public DelegatingIdMapper(Map<Holder.Reference<K>, V> blockColors, Registry<K> registry) {
        super(0);

        this.blockColors = blockColors;
        this.registry = registry;
    }

    @Nullable
    @Override
    public V byId(int id) {
        return this.registry.getHolder(id).map(this.blockColors::get).orElse(null);
    }
}
