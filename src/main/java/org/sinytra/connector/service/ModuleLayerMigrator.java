package org.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.IModFile.Type;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.*;

public class ModuleLayerMigrator implements IModFileCandidateLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static VarHandle BCL_UCP;
    private static VarHandle UCP_PATH;
    private static VarHandle UCP_UNOPENED_URLS;
    private static VarHandle UCP_LOADERS;
    private static VarHandle UCP_LMAP;
    private static MethodHandle LOADER_GET_BASE_URL;
    private static MethodHandle LOADER_CLOSE;

    private static boolean VALID;

    static {
        try {
            Class<?> builtinClClass = Class.forName("jdk.internal.loader.BuiltinClassLoader");
            Class<?> ucpClass = Class.forName("jdk.internal.loader.URLClassPath");
            BCL_UCP = ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(builtinClClass, "ucp", ucpClass);

            UCP_PATH = ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(ucpClass, "path", ArrayList.class);
            UCP_UNOPENED_URLS = ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(ucpClass, "unopenedUrls", ArrayDeque.class);
            UCP_LOADERS = ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(ucpClass, "loaders", ArrayList.class);
            UCP_LMAP = ConnectorUtil.TRUSTED_LOOKUP.findVarHandle(ucpClass, "lmap", HashMap.class);

            Class<?> loaderClass = Class.forName("jdk.internal.loader.URLClassPath$Loader");
            LOADER_GET_BASE_URL = ConnectorUtil.TRUSTED_LOOKUP.findVirtual(loaderClass, "getBaseURL", MethodType.methodType(URL.class));
            LOADER_CLOSE = ConnectorUtil.TRUSTED_LOOKUP.findVirtual(loaderClass, "close", MethodType.methodType(void.class));

            VALID = true;
        } catch (Exception e) {
            VALID = false;
            LOGGER.error("Error preparing module layer migrator", e);
        }
    }

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        if (!VALID) {
            return;
        }

        try {
            ClassLoader target = ClassLoader.getSystemClassLoader();
            // Brigadier
            migrateJar("brigadier", "com.mojang.brigadier.Command", target, pipeline);
            // Authlib
            migrateJar("authlib", "com.mojang.authlib.GameProfile", target, pipeline);
        } catch (Exception e) {
            LOGGER.error("Error migrating libraries", e);
            throw new RuntimeException(e); // Must hard fail now that libraries were removed
        }
    }

    private void migrateJar(String name, String cls, ClassLoader target, IDiscoveryPipeline pipeline) throws Exception {
        URL source = Objects.requireNonNull(findJarContainingClass(cls, target));

        try {
            removeURL(target, source);
        } catch (Exception e) {
            LOGGER.error("Error making library {} transformable", name, e);
            return;
        }

        IModFile modFile = IModFile.create(
            JarContents.ofPath(Path.of(source.toURI())),
            JarModsDotTomlModFileReader::manifestParser,
            Type.GAMELIBRARY,
            ModFileDiscoveryAttributes.DEFAULT.withLocator(this)
        );

        pipeline.addModFile(Objects.requireNonNull(modFile, "Invalid library mod file"));
    }

    public static URL findJarContainingClass(String className, ClassLoader loader) {
        String resourcePath = className.replace('.', '/') + ".class";
        URL resource = loader.getResource(resourcePath);
        if (resource == null) return null;

        try {
            URLConnection conn = resource.openConnection();
            if (conn instanceof JarURLConnection jarConn) {
                return jarConn.getJarFileURL();
            }
            return resource;
        } catch (IOException e) {
            return resource;
        }
    }

    public static void removeURL(ClassLoader loader, URL url) {
        try {
            Object ucp = BCL_UCP.get(loader);
            if (ucp == null) return;

            String target = normalize(url);

            synchronized (ucp) {
                removeUrls(UCP_PATH, ucp, target);
                removeUrls(UCP_UNOPENED_URLS, ucp, target);
                evictLoaders(ucp, target);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to remove URL from classloader", e);
        }
    }

    private static void removeUrls(VarHandle handle, Object ucp, String target) {
        Object value = handle.get(ucp);
        if (value instanceof Collection<?> col) {
            col.removeIf(o -> o instanceof URL u && normalize(u).equals(target));
        }
    }

    private static void evictLoaders(Object ucp, String target) {
        Object loadersObj = UCP_LOADERS.get(ucp);
        if (loadersObj instanceof Collection<?> loaders) {
            for (Iterator<?> it = loaders.iterator(); it.hasNext(); ) {
                Object l = it.next();
                if (l != null && matchesBase(l, target)) {
                    closeLoader(l);
                    it.remove();
                }
            }
        }

        Object lmapObj = UCP_LMAP.get(ucp);
        if (lmapObj instanceof Map<?, ?> lmap) {
            for (Iterator<? extends Map.Entry<?, ?>> it = lmap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<?, ?> e = it.next();
                Object l = e.getValue();
                if (l != null && matchesBase(l, target)) {
                    it.remove();
                }
            }
        }
    }

    private static boolean matchesBase(Object loader, String target) {
        try {
            Object base = LOADER_GET_BASE_URL.invoke(loader);
            return base instanceof URL url && normalize(unwrapJar(url)).equals(target);
        } catch (Throwable e) {
            return false;
        }
    }

    private static URL unwrapJar(URL url) {
        if (!"jar".equals(url.getProtocol())) {
            return url;
        }
        try {
            if (url.openConnection() instanceof JarURLConnection jc) {
                return jc.getJarFileURL();
            }
        } catch (IOException ignored) {
        }
        String s = url.toString();
        int bang = s.indexOf("!/");
        String inner = bang >= 0 ? s.substring(0, bang) : s;
        if (inner.startsWith("jar:")) inner = inner.substring(4);
        try {
            return new URL(inner);
        } catch (MalformedURLException e) {
            return url;
        }
    }

    private static void closeLoader(Object loader) {
        try {
            LOADER_CLOSE.invoke(loader);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static String normalize(URL url) {
        String s = url.toString();
        int hash = s.indexOf('#');
        return hash >= 0 ? s.substring(0, hash) : s;
    }
}