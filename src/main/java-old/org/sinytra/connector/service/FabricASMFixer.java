package org.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraftforge.fml.unsafe.UnsafeHacks;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class FabricASMFixer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> FABRIC_ASM_MODIDS = Set.of("mm", "mm_shedaniel");
    private static final String MINECRAFT_MODULE = "minecraft";
    public static final List<URL> URLS = new ArrayList<>();

    // Called from injected ASM hook, see injectFabricASM.js
    @SuppressWarnings("unused")
    public static Consumer<URL> fishAddURL() {
        return URLS::add;
    }

    // Called from injected ASM hook, see injectFabricASM.js
    @SuppressWarnings("unused")
    public static String flattenMixinClass(String name) {
        return name.replace('/', '_');
    }

    // Called from injected ASM hook, see injectFabricASM.js
    @SuppressWarnings("unused")
    public static void permitEnumSubclass(ClassNode enumNode, String anonymousClassName) {
        if (enumNode.permittedSubclasses != null) {
            enumNode.permittedSubclasses.add(anonymousClassName);
        }
    }

    public static void injectMinecraftModuleReader() {
        try {
            if (FABRIC_ASM_MODIDS.stream().noneMatch(FabricLoader.getInstance()::isModLoaded)) {
                return;
            }
            ModuleLayer layer = Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(IModuleLayerManager.Layer.GAME).orElseThrow();
            ResolvedModule resolvedModule = layer.configuration().findModule(MINECRAFT_MODULE).orElseThrow();

            Class<?> jarModuleReference = Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference");
            ModuleReference reference = resolvedModule.reference();
            if (!jarModuleReference.isInstance(reference)) {
                LOGGER.error("Minecraft module does not contain a jar module reference");
                return;
            }

            Field jarField = jarModuleReference.getDeclaredField("jar");
            SecureJar.ModuleDataProvider originalProvider = UnsafeHacks.getField(jarField, reference);
            SecureJar.ModuleDataProvider wrappedProvider = new ModuleDataProviderWrapper(originalProvider);
            UnsafeHacks.setField(jarField, reference, wrappedProvider);

            LOGGER.debug("Successfully replaced minecraft module reference jar");
        } catch (Throwable t) {
            LOGGER.error("Error injecting Fabric ASM minecraft module reader", t);
        }
    }

    private static Optional<InputStream> findGeneratedFile(String name) {
        for (URL url : FabricASMFixer.URLS) {
            try {
                URL pathUrl = new URL(url, name);
                URLConnection connection = pathUrl.openConnection();
                if (connection != null) {
                    return Optional.of(connection.getInputStream());
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    public static class FabricASMGeneratedClassesSecureJar implements SecureJar {
        private final ModuleDataProvider moduleDataProvider = new FabricASMGeneratedClassesProvider();

        //@formatter:off
        @Override public ModuleDataProvider moduleDataProvider() {return moduleDataProvider;}
        @Override public Path getPrimaryPath() {return Path.of(moduleDataProvider().uri());}
        @Override public CodeSigner[] getManifestSigners() {return new CodeSigner[0];}
        @Override public Status verifyPath(Path path) {return Status.NONE;}
        @Override public Status getFileStatus(String name) {return Status.NONE;}
        @Override public Attributes getTrustedManifestEntries(String name) {return new Attributes();}
        @Override public boolean hasSecurityData() {return false;}
        @Override public Set<String> getPackages() {return moduleDataProvider().descriptor().packages();}
        @Override public List<Provider> getProviders() {return List.of();}
        @Override public String name() {return moduleDataProvider().name();}
        @Override public Path getPath(String first, String... rest) {return getPrimaryPath();}
        @Override public Path getRootPath() {return getPrimaryPath();}
        //@formatter:on
    }

    private static class FabricASMGeneratedClassesProvider implements SecureJar.ModuleDataProvider {
        private static final Set<String> GEN_PACKAGES = Set.of("com.chocohead.gen.mixin", "me.shedaniel.gen.mixin");
        private ModuleDescriptor descriptor;

        @Override
        public String name() {
            return "fabric_asm_generated_classes";
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                descriptor = ModuleDescriptor.newAutomaticModule(name())
                    .packages(GEN_PACKAGES)
                    .build();
            }
            return descriptor;
        }

        @Override
        public URI uri() {
            return uncheck(() -> new URI("file:///~nonexistent"));
        }

        @Override
        public Optional<URI> findFile(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            return FabricASMFixer.findGeneratedFile(name);
        }

        @Override
        public Manifest getManifest() {
            return new Manifest();
        }

        @Override
        public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {
            return new CodeSigner[0];
        }
    }

    public record ModuleDataProviderWrapper(SecureJar.ModuleDataProvider provider) implements SecureJar.ModuleDataProvider {
        @Override
        public Optional<InputStream> open(String name) {
            Optional<InputStream> file = provider.open(name);
            return file.isEmpty() ? FabricASMFixer.findGeneratedFile(name) : file;
        }

        //@formatter:off
        @Override public String name() {return provider.name();}
        @Override public ModuleDescriptor descriptor() {return provider.descriptor();}
        @Override public URI uri() {return provider.uri();}
        @Override public Optional<URI> findFile(String name) {return provider.findFile(name);}
        @Override public Manifest getManifest() {return provider.getManifest();}
        @Override public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {return provider.verifyAndGetSigners(cname, bytes);}
        //@formatter:on
    }
}
