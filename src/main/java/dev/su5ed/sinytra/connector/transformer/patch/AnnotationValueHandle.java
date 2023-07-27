package dev.su5ed.sinytra.connector.transformer.patch;

import java.util.List;

public class AnnotationValueHandle<T> {
    private final List<Object> origin;
    private final int index;

    public AnnotationValueHandle(List<Object> origin, int index) {
        this.origin = origin;
        this.index = index;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        return (T) this.origin.get(index);
    }

    public void set(T value) {
        this.origin.set(index, value);
    }
}
