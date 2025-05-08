package org.sinytra.connector.transformer.transform;

public interface TransformProgressMeter {
    void increment();

    void complete();
}
