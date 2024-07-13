package org.sinytra.connector.service;

import cpw.mods.jarhandling.SecureJar;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class DummyVirtualJar implements SecureJar {
    private final ModuleDataProvider moduleDataProvider;

    public DummyVirtualJar(String name, Set<String> packages, Supplier<Manifest> manifest, Function<String, Optional<InputStream>> fileLookup) {
        this(name, name, packages, manifest, fileLookup);
    }
    
    public DummyVirtualJar(String name, String moduleName, Set<String> packages, Supplier<Manifest> manifest, Function<String, Optional<InputStream>> fileLookup) {
        this.moduleDataProvider = new VirtualModuleDataProvider(name, moduleName, packages, manifest, fileLookup);
    }

    //@formatter:off
    @Override public ModuleDataProvider moduleDataProvider() {return moduleDataProvider;}
    @Override public Path getPrimaryPath() {return Path.of(moduleDataProvider().uri());}
    @Override public CodeSigner[] getManifestSigners() {return null;}
    @Override public Status verifyPath(Path path) {return Status.NONE;}
    @Override public Status getFileStatus(String name) {return Status.NONE;}
    @Override public Attributes getTrustedManifestEntries(String name) {return null;}
    @Override public boolean hasSecurityData() {return false;}
    @Override public void close() {}
    @Override public String name() {return moduleDataProvider().name();}
    @Override public Path getPath(String first, String... rest) {return getPrimaryPath();}
    @Override public Path getRootPath() {return getPrimaryPath();}
    //@formatter:on

    public static class VirtualModuleDataProvider implements SecureJar.ModuleDataProvider {
        private final String name;
        private final String moduleName;
        private final Set<String> packages;
        private final Supplier<Manifest> manifestFactory;
        private final Function<String, Optional<InputStream>> fileLookup;

        private ModuleDescriptor descriptor;
        private Manifest manifest;

        public VirtualModuleDataProvider(String name, String moduleName, Set<String> packages, Supplier<Manifest> manifest, Function<String, Optional<InputStream>> fileLookup) {
            this.name = name;
            this.moduleName = moduleName;
            this.packages = packages;
            this.manifestFactory = manifest;
            this.fileLookup = fileLookup;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                descriptor = ModuleDescriptor.newAutomaticModule(moduleName).packages(packages).build();
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
            return this.fileLookup.apply(name);
        }

        @Override
        public Manifest getManifest() {
            if (manifest == null) {
                manifest = manifestFactory.get();
            }
            return manifest;
        }

        @Override
        public CodeSigner[] verifyAndGetSigners(String cname, byte[] bytes) {
            return null;
        }
    }
}