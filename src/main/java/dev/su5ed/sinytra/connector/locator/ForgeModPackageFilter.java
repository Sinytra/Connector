package dev.su5ed.sinytra.connector.locator;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.niofs.union.UnionFileSystem;
import net.minecraftforge.fml.unsafe.UnsafeHacks;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.util.Set;
import java.util.function.BiPredicate;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

/**
 * Some of the packages we ship (mainly coming from fabric loader) are included by other Forge mods as well, without any relocation.
 * Unfortunately, in our case we can't easily relocate the packages without also mapping all their usages in fabric mods, and possibly risking breaking reflective access
 * or other non-direct references. Our (slightly hacky) solution for this is to find and filter out any packages we ship from other discovered forge mods.
 */
public final class ForgeModPackageFilter {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void filterPackages(Iterable<IModFile> fmlMods) {
        // Get all packages in our jar
        Set<String> existingPackages = ForgeModPackageFilter.class.getModule().getPackages();
        // Filter out found packages from other FML mods
        for (IModFile modFile : fmlMods) {
            filterModFile(modFile, existingPackages);
        }
    }

    private static void filterModFile(IModFile modFile, Set<String> existingPackages) {
        SecureJar jar = modFile.getSecureJar();
        Set<String> packages = jar.getPackages();
        // Get packages contained in both sets
        Set<String> common = Sets.intersection(packages, existingPackages);
        if (!common.isEmpty() && jar instanceof Jar jarImpl) {
            LOGGER.debug("Filtering {} packages from mod file {}", common.size(), jar.getPrimaryPath().getFileName());
            BiPredicate<String, String> filter = new PackageTracker(common);
            // Get FS instance from jar root path
            FileSystem jarFS = jarImpl.getRootPath().getFileSystem();
            // Make sure it's a UFS instance just in case
            if (jarFS instanceof UnionFileSystem ufs) {
                // Add exclusion filter to UFS
                injectUFSFilter(ufs, filter);
                // Force package re-computation
                forceRecomputeJarPackages(jarImpl);
            }
        }
    }

    private static void injectUFSFilter(UnionFileSystem ufs, BiPredicate<String, String> filter) {
        // Merge the existing filter if present
        BiPredicate<String, String> existing = ufs.getFilesystemFilter();
        BiPredicate<String, String> merged = existing != null ? existing.and(filter) : filter;

        // Inject filter into UFS
        Field pathFilterField = uncheck(() -> UnionFileSystem.class.getDeclaredField("pathFilter"));
        UnsafeHacks.setField(pathFilterField, ufs, merged);
    }

    private static void forceRecomputeJarPackages(Jar jar) {
        // Remove computed packages cache to force their re-computation (lazy)
        Field packagesField = uncheck(() -> Jar.class.getDeclaredField("packages"));
        UnsafeHacks.setField(packagesField, jar, null);
    }
}
