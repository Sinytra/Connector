package org.sinytra.connector.transformer.api;

import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

import java.util.Collection;
import java.util.Set;
import java.util.jar.Attributes;

public interface CandidateJarMetadata {
    LoaderModMetadata modMetadata();

    Collection<String> visibleMixinConfigs();

    Collection<String> mixinConfigs();

    Set<String> refmaps();

    Set<String> mixinPackages();

    Set<String> mixinClasses();

    Attributes manifestAttributes();

    boolean generated();
}
