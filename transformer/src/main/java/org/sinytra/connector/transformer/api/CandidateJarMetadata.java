package org.sinytra.connector.transformer.api;

import net.fabricmc.loader.impl.metadata.LoaderModMetadata;

import java.util.Collection;
import java.util.Set;
import java.util.jar.Attributes;

/**
 * Provides information about a transformation candidate mod jar file.
 */
public interface CandidateJarMetadata {
    /**
     * Get the mods's Fabric metadata information.
     *
     * @return Fabric mod metadata parsed from the fabric.mod.json file.
     */
    LoaderModMetadata modMetadata();

    /**
     * Get the names of all valid mod mixin configs.
     * <p>
     * In addition to mixin configs specified by mod metadata for the current environment,
     * this also includes config files discovered in the mod jar that are not listed in the
     * mod's metadata.
     *
     * @return set of mixin config paths relative to the jar root
     */
    Collection<String> mixinConfigs();

    /**
     * Get all known refmaps in the mod.
     *
     * @return set of refmap names declared in known mixin configs
     * @see #mixinConfigs()
     */
    Set<String> refmaps();

    /**
     * Get all mixin packages in the mod.
     *
     * @return set of package names declared in known mixin configs
     * @see #mixinConfigs()
     */
    Set<String> mixinPackages();

    /**
     * Get all known mixin class names in binary class name format.
     * These are computed by combinining each config's package name
     * with all class names listed in mixins, client and server fields.
     *
     * @return set of all mixin class names referenced by known mixin configs
     * @see #mixinConfigs()
     */
    Set<String> mixinClasses();

    /**
     * Get the jar's main manifest attributes.
     *
     * @return the jar's main manifest attributes
     */
    Attributes manifestAttributes();

    /**
     * Whether this is a library jar with generated metadata.
     * This is evaluated based on the presence of the custom <code>fabric-loom:generated</code>
     * property in the mod's metadata.
     *
     * @return true if this is a library jar with generated metadata
     */
    boolean generated();
}
