package dev.su5ed.sinytra.connector.service;

import cpw.mods.jarhandling.SecureJar;
import org.spongepowered.asm.util.Constants;

import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

public class GeneratedMixinClassesSecureJar implements SecureJar {
    private static class GeneratedMixinClassesModuleDataProvider implements ModuleDataProvider {
        private ModuleDescriptor descriptor;

        @Override
        public String name() {
            return "mixin_generated_classes";
        }

        @Override
        public ModuleDescriptor descriptor() {
            if (descriptor == null) {
                String argsPkg = Constants.SYNTHETIC_PACKAGE + ".args";
                descriptor = ModuleDescriptor.newAutomaticModule(name())
                    .packages(Set.of(Constants.SYNTHETIC_PACKAGE, argsPkg))
                    .build();
            }
            return descriptor;
        }

        @Override
        public URI uri() {
            try {
                return new URI("file:///~nonexistent");
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<URI> findFile(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> open(String name) {
            return Optional.empty();
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

    private final ModuleDataProvider moduleDataProvider = new GeneratedMixinClassesModuleDataProvider();

    @Override
    public ModuleDataProvider moduleDataProvider() {
        return moduleDataProvider;
    }

    @Override
    public Path getPrimaryPath() {
        return Path.of(moduleDataProvider().uri());
    }

    @Override
    public CodeSigner[] getManifestSigners() {
        return new CodeSigner[0];
    }

    @Override
    public Status verifyPath(Path path) {
        return Status.NONE;
    }

    @Override
    public Status getFileStatus(String name) {
        return Status.NONE;
    }

    @Override
    public Attributes getTrustedManifestEntries(String name) {
        return new Attributes();
    }

    @Override
    public boolean hasSecurityData() {
        return false;
    }

    @Override
    public Set<String> getPackages() {
        return moduleDataProvider().descriptor().packages();
    }

    @Override
    public List<Provider> getProviders() {
        return List.of();
    }

    @Override
    public String name() {
        return moduleDataProvider().name();
    }

    @Override
    public Path getPath(String first, String... rest) {
        return getPrimaryPath();
    }

    @Override
    public Path getRootPath() {
        return getPrimaryPath();
    }
}
