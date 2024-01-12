package dev.su5ed.sinytra.connector.service;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

public class ConnectorForkJoinThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
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
}
