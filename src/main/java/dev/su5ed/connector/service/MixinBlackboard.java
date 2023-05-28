package dev.su5ed.connector.service;

import cpw.mods.modlauncher.api.TypesafeMap;
import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class MixinBlackboard implements IGlobalPropertyService {
    private final Map<String, IPropertyKey> keys = new HashMap<>();
    private final TypesafeMap blackboard;

    public MixinBlackboard() {
        this.blackboard = new TypesafeMap();
    }

    public IPropertyKey resolveKey(String name) {
        return this.keys.computeIfAbsent(name, key -> new Key<>(this.blackboard, key, Object.class));
    }

    public <T> T getProperty(IPropertyKey key) {
        return (T) this.getProperty(key, null);
    }

    public void setProperty(IPropertyKey key, Object value) {
        this.blackboard.computeIfAbsent(((Key )key).key, k -> value);
    }

    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return this.getProperty(key, defaultValue);
    }

    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) this.blackboard.get(((Key)key).key).orElse(defaultValue);
    }

    static class Key<V> implements IPropertyKey {
        final TypesafeMap.Key<V> key;

        public Key(TypesafeMap owner, String name, Class<V> clazz) {
            this.key = cpw.mods.modlauncher.api.TypesafeMap.Key.getOrCreate(owner, name, clazz);
        }
    }
}
