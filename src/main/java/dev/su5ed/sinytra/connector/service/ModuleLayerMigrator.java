package dev.su5ed.sinytra.connector.service;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import org.slf4j.Logger;
import sun.misc.Unsafe;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ModuleLayerMigrator {
    private static final MethodHandles.Lookup TRUSTED_LOOKUP = uncheck(() -> {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Unsafe unsafe = (Unsafe) theUnsafe.get(null);
        Field hackfield = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        return (MethodHandles.Lookup) unsafe.getObject(unsafe.staticFieldBase(hackfield), unsafe.staticFieldOffset(hackfield));
    });
    private static final Class<?> JAR_MODULE_REF_CLASS = uncheck(() -> Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference"));
    private static final VarHandle REF_MODULE_PROVIDER_FIELD = uncheck(() -> TRUSTED_LOOKUP.findVarHandle(JAR_MODULE_REF_CLASS, "jar", SecureJar.ModuleDataProvider.class));
    private static final VarHandle DESCRIPTOR_PACKAGES_FIELD = uncheck(() -> TRUSTED_LOOKUP.findVarHandle(ModuleDescriptor.class, "packages", Set.class));
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * "Moves" a module from the {@link cpw.mods.modlauncher.api.IModuleLayerManager.Layer#BOOT BOOT} layer to {@link cpw.mods.modlauncher.api.IModuleLayerManager.Layer#GAME GAME}
     * in order to make it transformable. This is achieved by disabling the old module and preventing it from loading its classes, then adding a new one to the upper layer
     * to load classes from instead.
     * <p/>
     * Libraries transferred by this method are only used by minecraft after the {@link cpw.mods.modlauncher.api.IModuleLayerManager.Layer#GAME GAME} layer becomes available.
     * In theory, all of this <i>should</i> work just fine without breaking other mods.
     *
     * @param moduleName name of the module to transfer
     * @return the new jar to be placed on the {@link cpw.mods.modlauncher.api.IModuleLayerManager.Layer#GAME GAME} layer
     */
    public static SecureJar moveModule(String moduleName) {
        try {
            LOGGER.debug("Attempting to make module {} transformable", moduleName);
            ModuleLayer layer = Launcher.INSTANCE.findLayerManager().orElseThrow().getLayer(IModuleLayerManager.Layer.BOOT).orElseThrow();
            ResolvedModule module = layer.configuration().findModule(moduleName).orElseThrow(() -> new RuntimeException("Module %s not found".formatted(moduleName)));
            Module actualModule = layer.findModule(moduleName).orElseThrow(() -> new RuntimeException("Module %s not found".formatted(moduleName)));

            ModuleReference reference = module.reference();
            if (!JAR_MODULE_REF_CLASS.isInstance(reference)) {
                throw new RuntimeException("Module %s does not contain a jar module reference".formatted(moduleName));
            }

            // Replace module provider with an empty one to avoid loading classes twice
            SecureJar.ModuleDataProvider originalProvider = (SecureJar.ModuleDataProvider) REF_MODULE_PROVIDER_FIELD.get(reference);
            SecureJar.ModuleDataProvider wrappedProvider = new EmptyModuleDataProvider(originalProvider.name());
            REF_MODULE_PROVIDER_FIELD.set(reference, wrappedProvider);

            // Remove all packages from the original module to avoid split package conflicts
            ModuleDescriptor desc = actualModule.getDescriptor();
            DESCRIPTOR_PACKAGES_FIELD.set(desc, Set.of());

            LOGGER.info("Successfully made module {} transformable", moduleName);
            SecureJar.ModuleDataProvider provider = new ModuleDataProviderWrapper(originalProvider, "connector$" + moduleName);
            return new SimpleSecureJar(provider);
        } catch (Throwable t) {
            throw new RuntimeException("Error making module %s transformable".formatted(moduleName), t);
        }
    }

    private static class EmptyModuleDataProvider implements SecureJar.ModuleDataProvider {
        private final String name;
        private ModuleDescriptor descriptor;

        public EmptyModuleDataProvider(String name) {
            this.name = name;
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                descriptor = ModuleDescriptor.newAutomaticModule(name()).build();
            }
            return descriptor;
        }

        //@formatter:off
        @Override public String name() {return this.name;}
        @Override public URI uri() {return uncheck(() -> new URI("file:///~nonexistent"));}
        @Override public Optional<URI> findFile(String name) {return Optional.empty();}
        @Override public Optional<InputStream> open(String name) {return Optional.empty();}
        @Override public Manifest getManifest() {return new Manifest();}
        @Override public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {return new CodeSigner[0];}
        //@formatter:on
    }

    private record SimpleSecureJar(ModuleDataProvider moduleDataProvider) implements SecureJar {
        //@formatter:off
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

    private static class ModuleDataProviderWrapper implements SecureJar.ModuleDataProvider {
        private final SecureJar.ModuleDataProvider provider;
        private final String name;
        private ModuleDescriptor descriptor;

        public ModuleDataProviderWrapper(SecureJar.ModuleDataProvider provider, String name) {
            this.provider = provider;
            this.name = name;
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                ModuleDescriptor desc = this.provider.descriptor();
                var builder = ModuleDescriptor.newModule(this.name, desc.modifiers());
                builder.packages(desc.packages());
                if (!desc.isAutomatic()) {
                    desc.version().ifPresent(builder::version);
                    desc.requires().forEach(builder::requires);
                    desc.exports().forEach(builder::exports);
                    desc.opens().forEach(builder::opens);
                    desc.uses().forEach(builder::uses);
                    desc.provides().forEach(builder::provides);
                }
                descriptor = builder.build();
            }
            return descriptor;
        }

        //@formatter:off
        @Override public String name(){return name;}
        @Override public URI uri() {return provider.uri();}
        @Override public Optional<URI> findFile(String name) {return provider.findFile(name);}
        @Override public Optional<InputStream> open(String name) {return provider.open(name);}
        @Override public Manifest getManifest() {return provider.getManifest();}
        @Override public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {return provider.verifyAndGetSigners(cname, bytes);}
        //@formatter:on
    }
}
