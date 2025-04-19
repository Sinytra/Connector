package org.sinytra.connector.transformer.runner.runtime;

import com.google.common.io.Resources;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.transformers.MixinClassReader;

import java.io.IOException;
import java.net.URL;

public class ProbeClassBytecodeProvider implements IClassBytecodeProvider {
    private final ILaunchPluginService.ITransformerLoader transformerLoader;

    public ProbeClassBytecodeProvider(ILaunchPluginService.ITransformerLoader transformerLoader) {
        this.transformerLoader = transformerLoader;
    }

    @Override
    public ClassNode getClassNode(String name) throws ClassNotFoundException {
        return getClassNode(name, true);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException {
        return getClassNode(name, true, ClassReader.EXPAND_FRAMES);
    }

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers, int readerFlags) throws ClassNotFoundException {
        if (!runTransformers) {
            throw new IllegalArgumentException("Unsupported");
        }

        String canonicalName = name.replace('/', '.');
        String internalName = name.replace('.', '/');

        byte[] classBytes;

        try {
            classBytes = this.transformerLoader.buildTransformedClassNodeFor(canonicalName);
        } catch (ClassNotFoundException ex) {
            URL url = Thread.currentThread().getContextClassLoader().getResource(internalName + ".class");
            if (url == null) {
                throw ex;
            }
            try {
                classBytes = Resources.asByteSource(url).read();
            } catch (IOException ioex) {
                throw ex;
            }
        }

        if (classBytes != null && classBytes.length != 0) {
            ClassNode classNode = new ClassNode();
            ClassReader classReader = new MixinClassReader(classBytes, canonicalName);
            classReader.accept(classNode, readerFlags);
            return classNode;
        }

        throw new ClassNotFoundException(canonicalName);
    }
}
