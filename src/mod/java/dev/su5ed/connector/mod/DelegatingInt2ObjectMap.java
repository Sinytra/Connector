package dev.su5ed.connector.mod;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings("unused")
public class DelegatingInt2ObjectMap<V> implements Int2ObjectMap<V> {
    private final Registry<V> registry;
    private final Map<ResourceLocation, V> delegate;

    public DelegatingInt2ObjectMap(Map<ResourceLocation, V> delegate, Registry<V> registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsValue(Object value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V put(int key, V value) {
        return this.delegate.put(this.registry.getKey(value), value);
    }

    @Override
    public void putAll(@NotNull Map<? extends Integer, ? extends V> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void defaultReturnValue(V rv) {}

    @Override
    public V defaultReturnValue() {
        return null;
    }

    @Override
    public ObjectSet<Entry<V>> int2ObjectEntrySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IntSet keySet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ObjectCollection<V> values() {
        throw new UnsupportedOperationException();
    }

    @Override
    public V get(int key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(int key) {
        throw new UnsupportedOperationException();
    }
}
