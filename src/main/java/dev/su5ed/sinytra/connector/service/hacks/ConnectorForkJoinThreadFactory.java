package dev.su5ed.sinytra.connector.service.hacks;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

import static dev.su5ed.sinytra.connector.service.hacks.ModuleLayerMigrator.TRUSTED_LOOKUP;

public class ConnectorForkJoinThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ForkJoinPool.ForkJoinWorkerThreadFactory factory;

    public ConnectorForkJoinThreadFactory(ForkJoinPool.ForkJoinWorkerThreadFactory factory) {
        this.factory = factory;
    }

    @Override
    public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
        ForkJoinWorkerThread thread = this.factory.newThread(pool);
        thread.setContextClassLoader(Thread.currentThread().getContextClassLoader());
        return thread;
    }

    public static void install() {
        // Replace the default FJP thread factory to pass the context classloader from modlauncher
        // Must use reflection over the system property, as it only supports loading from the system CL
        try {
            ForkJoinPool.ForkJoinWorkerThreadFactory factory = new ConnectorForkJoinThreadFactory(ForkJoinPool.defaultForkJoinWorkerThreadFactory);
            MethodHandle handle = TRUSTED_LOOKUP.findStaticSetter(ForkJoinPool.class, "defaultForkJoinWorkerThreadFactory", ForkJoinPool.ForkJoinWorkerThreadFactory.class);
            handle.invoke(factory);

            VarHandle commonHandle = TRUSTED_LOOKUP.findVarHandle(ForkJoinPool.class, "factory", ForkJoinPool.ForkJoinWorkerThreadFactory.class);
            commonHandle.set(ForkJoinPool.commonPool(), factory);
        } catch (Throwable t) {
            LOGGER.error("Error injecting default thread pool factory", t);
        }
    }
}
