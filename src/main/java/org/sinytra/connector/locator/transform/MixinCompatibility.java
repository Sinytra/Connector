package org.sinytra.connector.locator.transform;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

import static org.sinytra.connector.transformer.transform.TransformerUtil.uncheck;

public class MixinCompatibility {
    private static final List<LoaderMixinVersionEntry> VERSIONS = List.of(
        LoaderMixinVersionEntry.create("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0)
    );

    public static int getMixinCompat(ModMetadata metadata) {
        // infer from loader dependency by determining the least relevant loader version the mod accepts
        // AND any loader deps

        boolean found = false;
        List<VersionInterval> reqIntervals = List.of(VersionInterval.INFINITE);

        for (ModDependency dep : metadata.getDependencies()) {
            if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                    found = true;
                    reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                } else if (dep.getKind() == ModDependency.Kind.BREAKS) {
                    found = true;
                    reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                }
            }
        }

        if (!found) {
            return FabricUtil.COMPATIBILITY_0_10_0;
        }

        if (reqIntervals.isEmpty()) throw new IllegalStateException("mod " + metadata.getId() + " is incompatible with every loader version?"); // shouldn't get there

        Version minLoaderVersion = reqIntervals.getFirst().getMin(); // it is sorted, to 0 has the absolute lower bound

        if (minLoaderVersion != null) { // has a lower bound
            for (LoaderMixinVersionEntry version : VERSIONS) {
                if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
                    return version.mixinVersion;
                } else {
                    break;
                }
            }
        }

        return FabricUtil.COMPATIBILITY_0_9_2;
    }

    private record LoaderMixinVersionEntry(SemanticVersion loaderVersion, int mixinVersion) {
        public static LoaderMixinVersionEntry create(String loaderVersion, int mixinVersion) {
            return new LoaderMixinVersionEntry(uncheck(() -> SemanticVersion.parse(loaderVersion)), mixinVersion);
        }
    }
}
