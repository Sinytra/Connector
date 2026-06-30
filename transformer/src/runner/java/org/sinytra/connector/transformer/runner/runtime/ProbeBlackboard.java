/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.sinytra.connector.transformer.runner.runtime;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProbeBlackboard implements IGlobalPropertyService {
    private static final Map<String, Object> PROPERTIES = new HashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new StringKey(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key) {
        synchronized (PROPERTIES) {
            return (T) PROPERTIES.get(getKeyName(key));
        }
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        synchronized (PROPERTIES) {
            PROPERTIES.put(getKeyName(key), value);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        synchronized (PROPERTIES) {
            return (T) PROPERTIES.getOrDefault(getKeyName(key), defaultValue);
        }
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return Objects.requireNonNullElse((String) PROPERTIES.get(getKeyName(key)), defaultValue);
    }

    private String getKeyName(IPropertyKey key) {
        return ((StringKey) key).name();
    }

    record StringKey(String name) implements IPropertyKey {
    }

}

