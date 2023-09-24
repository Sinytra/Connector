package dev.su5ed.sinytra.connector.mod.compat;

import net.minecraft.core.IdMapper;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

public class RedirectingIdMapper<K, T> extends IdMapper<T> {
    IntFunction<K> keyFunction;
    Function<K, Integer> reverseKeyFunction;
    Map<K, T> map;

    public RedirectingIdMapper(IntFunction<K> keyFunction, Function<K, Integer> reverseKeyFunction, Map<K, T> map) {
        this.keyFunction = keyFunction;
        this.reverseKeyFunction = reverseKeyFunction;
        this.map = map;
    }

    @Override
    public void addMapping(@NotNull T value, int id) {
        this.map.put(this.keyFunction.apply(id), value);
    }

    @Override
    public void add(@NotNull T value) {
        this.addMapping(value, this.map.size());
    }

    @Override
    public int getId(@NotNull T value) {
        for (Map.Entry<K, T> ktEntry : this.map.entrySet()) {
            if (Objects.equals(ktEntry.getValue(), value)) {
                return this.reverseKeyFunction.apply(ktEntry.getKey());
            }
        }
        return -1;
    }

    @Override
    public Iterator<T> iterator() {
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
