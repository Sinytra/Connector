package org.sinytra.connector.transformer.jar;

import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.jar.Attributes;

public record FabricModFileMetadata(
    LoaderModMetadata modMetadata,
    Collection<String> visibleMixinConfigs,
    Collection<String> mixinConfigs,
    Set<String> refmaps,
    Set<String> mixinClasses,
    @Nullable Set<String> transformMixinClasses,
    Attributes manifestAttributes,
    boolean containsAT,
    boolean generated
) {
}
