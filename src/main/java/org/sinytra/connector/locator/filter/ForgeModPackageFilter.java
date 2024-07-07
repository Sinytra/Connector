package org.sinytra.connector.locator.filter;

import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.jarhandling.impl.Jar;
import cpw.mods.jarhandling.impl.JarContentsImpl;
import cpw.mods.niofs.union.UnionFileSystem;
import cpw.mods.niofs.union.UnionPathFilter;
import net.neoforged.neoforgespi.locating.IModFile;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.lang.invoke.VarHandle;
import java.nio.file.FileSystem;
import java.util.Set;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

/**
 * Some of the packages we ship (mainly coming from fabric loader) are included by other Forge mods as well, without any relocation.
 * Unfortunately, in our case we can't easily relocate the packages without also mapping all their usages in fabric mods, and possibly risking breaking reflective access
 * or other non-direct references. Our (slightly hacky) solution for this is to find and filter out any packages we ship from other discovered forge mods.
 */
public final class ForgeModPackageFilter {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final VarHandle JAR_CONTENTS = uncheck(() -> ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(Jar.class, "contents", JarContentsImpl.class));
    private static final VarHandle JAR_CONTENTS_PACKAGES = uncheck(() -> ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(JarContentsImpl.class, "packages", Set.class));
    private static final VarHandle UPFS_FILTER = uncheck(() -> ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(UnionFileSystem.class, "pathFilter", UnionPathFilter.class));

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
        Set<String> packages = jar.moduleDataProvider().descriptor().packages();
        // Get packages contained in both sets
        Set<String> common = Sets.intersection(packages, existingPackages);
        if (!common.isEmpty() && jar instanceof Jar jarImpl) {
            LOGGER.debug("Filtering {} packages from mod file {}", common.size(), jar.getPrimaryPath().getFileName());
            UnionPathFilter filter = new PackageTracker(common);
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

    private static void injectUFSFilter(UnionFileSystem ufs, UnionPathFilter filter) {
        // Merge the existing filter if present
        UnionPathFilter existing = ufs.getFilesystemFilter();
        UnionPathFilter merged = existing != null ? (a, b) -> existing.test(a, b) && filter.test(a, b) : filter;

        // Inject filter into UFS
        UPFS_FILTER.set(ufs, merged); // TODO TEST
    }

    private static void forceRecomputeJarPackages(Jar jar) {
        // Remove computed packages cache to force their re-computation (lazy)
        JarContentsImpl jarContents = (JarContentsImpl) uncheck(() -> JAR_CONTENTS.get(jar));
        uncheck(() -> JAR_CONTENTS_PACKAGES.set(jarContents, null));
    }
}
