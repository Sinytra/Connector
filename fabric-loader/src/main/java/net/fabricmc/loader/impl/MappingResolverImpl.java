package net.fabricmc.loader.impl;

import net.fabricmc.loader.api.MappingResolver;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.INamedMappingFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collection;
import java.util.Optional;

public class MappingResolverImpl implements MappingResolver {
    private static final String FML_NAMESPACE = FMLEnvironment.naming;

    private final INamedMappingFile mappings;

    public MappingResolverImpl() {
        URL path = ClassLoader.getSystemResource("mappings.tsrg");
        if (path == null && !FMLEnvironment.production)
            throw new RuntimeException("Mappings file not found in dev, bug?");

        try (InputStream is = path.openStream()) {
            this.mappings = INamedMappingFile.load(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public IMappingFile getCurrentMap(String from) {
        return getMap(from, FML_NAMESPACE);
    }

    public IMappingFile getMap(String from, String to) {
        return this.mappings.getMap(from, to);
    }

    public String mapDescriptor(String namespace, String descriptor) {
        return getCurrentMap(namespace).remapDescriptor(descriptor);
    }

    @Override
    public Collection<String> getNamespaces() {
        return this.mappings.getNames();
    }

    @Override
    public String getCurrentRuntimeNamespace() {
        return FMLEnvironment.naming;
    }

    @Override
    public String mapClassName(String namespace, String className) {
        return toBinaryName(getCurrentMap(namespace).remapClass(toInternalName(className)));
    }

    @Override
    public String unmapClassName(String targetNamespace, String className) {
        return toBinaryName(getMap(FML_NAMESPACE, targetNamespace).remapClass(toInternalName(className)));
    }

    @Override
    public String mapFieldName(String namespace, String owner, String name, String descriptor) {
        return Optional.ofNullable(getCurrentMap(namespace).getClass(toInternalName(owner)))
                .map(cls -> cls.remapField(name))
                .orElse(name);
    }

    @Override
    public String mapMethodName(String namespace, String owner, String name, String descriptor) {
        return Optional.ofNullable(getCurrentMap(namespace).getClass(toInternalName(owner)))
                .map(cls -> cls.remapMethod(name, descriptor))
                .orElse(name);
    }
    
    private static String toBinaryName(String className) {
        return className.replace('/', '.');
    }
    
    private static String toInternalName(String className) {
        return className.replace('.', '/');
    }
}
