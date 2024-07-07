package org.sinytra.connector.transformer.jar;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.ClassTransformer;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.TransformStore;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerActivity;
import net.minecraftforge.fart.api.ClassProvider;
import net.neoforged.fml.loading.FMLServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static cpw.mods.modlauncher.api.LambdaExceptionUtils.uncheck;

public class EarlyJSCoremodTransformer implements ClassProvider {
    private static final MethodHandle TRANSFORM = uncheck(() -> MethodHandles.privateLookupIn(ClassTransformer.class, MethodHandles.lookup()).findVirtual(ClassTransformer.class, "transform", MethodType.methodType(byte[].class, byte[].class, String.class, String.class)));
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ClassProvider provider;
    private final ClassTransformer transformer;

    public static EarlyJSCoremodTransformer create(ClassProvider classProvider) {
        try {
            FMLServiceProvider fmlServiceProvider = ConnectorUtil.allocateInstance(FMLServiceProvider.class);
            List<? extends ITransformer<?>> transformers = fmlServiceProvider.transformers();

            TransformStore transformStore = new TransformStore();
            ITransformationService service = new DummyService(transformers);

            Constructor<TransformationServiceDecorator> cst = TransformationServiceDecorator.class.getDeclaredConstructor(ITransformationService.class);
            cst.setAccessible(true);
            TransformationServiceDecorator decorator = cst.newInstance(service);
            decorator.gatherTransformers(transformStore);

            LaunchPluginHandler plugins = ConnectorUtil.allocateInstance(LaunchPluginHandler.class);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            pluginsField.set(plugins, new HashMap<>());

            Constructor<ClassTransformer> xformCst = ClassTransformer.class.getDeclaredConstructor(TransformStore.class, LaunchPluginHandler.class, TransformingClassLoader.class);
            xformCst.setAccessible(true);
            ClassTransformer classTransformer = xformCst.newInstance(transformStore, plugins, null);
            return new EarlyJSCoremodTransformer(classProvider, classTransformer);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public EarlyJSCoremodTransformer(ClassProvider provider, ClassTransformer transformer) {
        this.provider = provider;
        this.transformer = transformer;
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
                    return (byte[]) TRANSFORM.invoke(this.transformer, bytes, s, ITransformerActivity.COMPUTING_FRAMES_REASON);
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

    @SuppressWarnings("rawtypes")
    private record DummyService(List transformers) implements ITransformationService {
        @Override
        @NotNull
        public String name() {
            return "connector_early_js_coremods";
        }

        @Override
        public void initialize(IEnvironment environment) {}

        @Override
        public void onLoad(IEnvironment env, Set<String> otherServices) {}
    }
}
