package org.sinytra.connector.locator.transform;

import com.mojang.logging.LogUtils;
import net.neoforged.art.api.ClassProvider;
import net.neoforged.fml.classloading.transformation.ClassProcessorSet;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessor.AfterProcessingContext;
import net.neoforged.neoforgespi.transformation.ClassProcessor.ComputeFlags;
import net.neoforged.neoforgespi.transformation.ClassProcessorProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Supplier;

import static org.sinytra.connector.transformer.transform.TransformerUtil.uncheck;

@SuppressWarnings({"UnstableApiUsage", "Java9UndeclaredServiceUsage"})
public class EarlyCoremodTransformer implements ClassProvider {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClassProvider provider;
    private final ClassProcessorSet processors;

    public static EarlyCoremodTransformer create(ClassProvider classProvider, IModFile library) {
        ClassLoader parent = FMLLoader.getCurrent().getCurrentClassLoader();
        URL[] sources = new URL[]{ uncheck(() -> library.getFilePath().toUri().toURL()) };
        ClassLoader loader = new URLClassLoader("Connector Early Coremods", sources, parent);
        
        List<ClassProcessorProvider> providers = ServiceLoader.load(ClassProcessorProvider.class, loader).stream()
            .filter(p -> p.type().getPackage().getName().equals("net.neoforged.neoforge.coremods"))
            .map(Provider::get)
            .toList();
        ClassProcessorSet processors = ClassProcessorSet.builder().addProcessorProviders(providers).build();

        return new EarlyCoremodTransformer(classProvider, processors);
    }

    public EarlyCoremodTransformer(ClassProvider provider, ClassProcessorSet processors) {
        this.provider = provider;
        this.processors = processors;
    }

    @Override
    public Optional<? extends IClassInfo> getClass(String s) {
        return this.provider.getClass(s);
    }

    @Override
    public Optional<byte[]> getClassBytes(String s) {
        return this.provider.getClassBytes(s)
            .map(bytes -> {
                try {
                    return transformClass(s, bytes);
                } catch (Throwable t) {
                    LOGGER.error("Error transforming class {}", s, t);
                    return bytes;
                }
            });
    }

    @Override
    public void close() throws IOException {
        this.provider.close();
    }

    private byte[] transformClass(String internalName, byte[] inputClass) {
        if (inputClass.length == 0) {
            throw new IllegalStateException("Empty input class bytes");
        }

        Type classDesc = Type.getObjectType(internalName);

        List<ClassProcessor> transformersToUse = this.processors.transformersFor(classDesc, false, null);
        if (transformersToUse.isEmpty()) {
            return inputClass;
        }

        ClassNode clazz = new ClassNode(Opcodes.ASM9);
        ClassReader classReader = new ClassReader(inputClass);
        classReader.accept(clazz, ClassReader.EXPAND_FRAMES);
        Supplier<byte[]> digest = () -> {
            throw new UnsupportedOperationException();
        };

        ComputeFlags flags = ClassProcessor.ComputeFlags.NO_REWRITE;
        for (var transformer : transformersToUse) {
            var context = new ClassProcessor.TransformationContext(
                classDesc,
                clazz,
                false,
                (a, b) -> {
                },
                digest);
            var newFlags = transformer.processClass(context);
            flags = flags.max(newFlags);
        }
        // run post-result callbacks
        AfterProcessingContext context = new ClassProcessor.AfterProcessingContext(classDesc);
        for (var transformer : transformersToUse) {
            transformer.afterProcessing(context);
        }

        if (flags == ClassProcessor.ComputeFlags.NO_REWRITE) {
            return inputClass;
        }

        ClassWriter cw = createClassWriter(flags);
        clazz.accept(cw);
        return cw.toByteArray();
    }

    private static ClassWriter createClassWriter(ClassProcessor.ComputeFlags flags) {
        int writerFlag = switch (flags) {
            case COMPUTE_MAXS -> ClassWriter.COMPUTE_MAXS;
            case COMPUTE_FRAMES -> ClassWriter.COMPUTE_FRAMES;
            default -> 0;
        };

        // TODO Parents context
        return new ClassWriter(writerFlag);
    }
}
