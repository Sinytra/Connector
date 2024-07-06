package org.sinytra.connector.mod.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class DummyResourceManager implements ResourceManager {
    public static final ResourceManager INSTANCE = new DummyResourceManager();

    @Override
    public Set<String> getNamespaces() {
        return Set.of();
    }

    @Override
    public List<Resource> getResourceStack(ResourceLocation pLocation) {
        return List.of();
    }

    @Override
    public Map<ResourceLocation, Resource> listResources(String pPath, Predicate<ResourceLocation> pFilter) {
        return Map.of();
    }

    @Override
    public Map<ResourceLocation, List<Resource>> listResourceStacks(String pPath, Predicate<ResourceLocation> pFilter) {
        return Map.of();
    }

    @Override
    public Stream<PackResources> listPacks() {
        return Stream.empty();
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation pLocation) {
        return Optional.empty();
    }
}
