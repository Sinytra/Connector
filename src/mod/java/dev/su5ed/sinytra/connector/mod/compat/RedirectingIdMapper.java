package dev.su5ed.sinytra.connector.mod.compat;

import net.minecraft.core.IdMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

public class RedirectingIdMapper<K, V> extends IdMapper<V> {
    IntFunction<K> keyFunction;
    Function<K, Integer> reverseKeyFunction;
    Map<K, V> map;

    public RedirectingIdMapper(IntFunction<K> keyFunction, Function<K, Integer> reverseKeyFunction, Map<K, V> map) {
        this.keyFunction = keyFunction;
        this.reverseKeyFunction = reverseKeyFunction;
        this.map = map;
    }

    @Override
    public void addMapping(@NotNull V value, int id) {
        this.map.put(this.keyFunction.apply(id), value);
    }

    @Override
    public void add(@NotNull V value) {
        this.addMapping(value, this.map.size());
    }

    @Override
    public int getId(@NotNull V value) {
        for (Map.Entry<K, V> ktEntry : this.map.entrySet()) {
            if (Objects.equals(ktEntry.getValue(), value)) {
                return this.reverseKeyFunction.apply(ktEntry.getKey());
            }
        }
        return -1;
    }

    @Override
    public V byId(int id) {
        return this.map.get(this.keyFunction.apply(id));
    }

    @Override
    public Iterator<V> iterator() {
        return this.map.values().iterator();
    }

    @Override
    public boolean contains(int id) {
        return this.map.containsKey(this.keyFunction.apply(id));
    }

    @Override
    public int size() {
        return this.map.size();
    }
}
