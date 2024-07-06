package org.sinytra.connector.mod.compat.fieldtypes;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

public class RedirectingInt2ObjectMap<K, V> implements Int2ObjectMap<V> {
    private final IntFunction<K> keyFunction;
    private final Function<K, Integer> reverseKeyFunction;
    private final Map<K, V> map;
    private V defaultReturnValue;

    public RedirectingInt2ObjectMap(IntFunction<K> keyFunction, Function<K, Integer> reverseKeyFunction, Map<K, V> map) {
        this.keyFunction = keyFunction;
        this.reverseKeyFunction = reverseKeyFunction;
        this.map = map;
    }

    @Override
    public int size() {
        return this.map.size();
    }

    @Override
    public void clear() {
        this.map.clear();
    }

    @Override
    public boolean isEmpty() {
        return this.map.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return this.map.containsValue(value);
    }

    @Override
    public void putAll(@NotNull Map<? extends Integer, ? extends V> m) {
        m.forEach((key, value) -> this.map.put(this.keyFunction.apply(key), value));
    }

    @Override
    public void defaultReturnValue(V rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public V defaultReturnValue() {
        return this.defaultReturnValue;
    }

    @Override
    public ObjectSet<Entry<V>> int2ObjectEntrySet() {
        throw new UnsupportedOperationException("int2ObjectEntrySet is not redirected yet!");
    }

    @Override
    public IntSet keySet() {
        throw new UnsupportedOperationException("keySet is not redirected yet!");
    }

    @Override
    public ObjectCollection<V> values() {
        throw new UnsupportedOperationException("values is not redirected yet!");
    }

    @Override
    public V put(int key, V value) {
        return this.map.put(this.keyFunction.apply(key), value);
    }

    @Override
    public V get(int key) {
        return this.map.get(this.keyFunction.apply(key));
    }

    @Override
    public V remove(int key) {
        return this.map.remove(this.keyFunction.apply(key));
    }

    @Override
    public boolean containsKey(int key) {
        return this.map.containsKey(this.keyFunction.apply(key));
    }

    @Override
    public void forEach(BiConsumer<? super Integer, ? super V> consumer) {
        this.map.forEach((key, value) -> consumer.accept(this.reverseKeyFunction.apply(key), value));
    }

    @Override
    public boolean remove(int key, Object value) {
        return this.map.remove(this.keyFunction.apply(key), value);
    }

    @Override
    public boolean replace(int key, V oldValue, V newValue) {
        return this.map.replace(this.keyFunction.apply(key), oldValue, newValue);
    }

    @Override
    public V replace(int key, V value) {
        return this.map.replace(this.keyFunction.apply(key), value);
    }
}
