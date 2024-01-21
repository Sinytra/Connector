/*
 * Copyright 2019 cpw
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package dev.su5ed.sinytra.connector.service.hacks;

import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

/**
 * Code taken from SecureJarHandler.
 * Applies <a href="https://github.com/McModLauncher/securejarhandler/pull/52">McModLauncher/securejarhandler#52</a>
 * to mitigate <a href="https://github.com/McModLauncher/modlauncher/issues/100">McModLauncher/modlauncher#100</a>
 */
// TODO Remove in 1.20.4
public class ServiceProviderInheritanceWorkaround {
    // Reflect into JVM internals to associate each ModuleClassLoader with all of its parent layers.
    // This is necessary to let ServiceProvider find service implementations in parent module layers.
    // At the moment, this does not work for providers in the bootstrap or platform class loaders,
    // but any other provider (defined by the application class loader or child layers) should work.
    //
    // The only mechanism the JVM has for this is to also look for layers defined by the parent class loader.
    // We don't want to set a parent because we explicitly do not want to delegate to a parent class loader,
    // and that wouldn't even handle the case of multiple parent layers anyway.
    private static final MethodHandle LAYER_BIND_TO_LOADER = LamdbaExceptionUtils.uncheck(() -> ModuleLayerMigrator.TRUSTED_LOOKUP.findSpecial(ModuleLayer.class, "bindToLoader", MethodType.methodType(void.class, ClassLoader.class), ModuleLayer.class));

    public static void apply() {
        Launcher.INSTANCE.findLayerManager()
            .flatMap(manager -> manager.getLayer(IModuleLayerManager.Layer.GAME))
            .ifPresent(moduleLayer -> applyToLoader((ModuleClassLoader) moduleLayer.modules().iterator().next().getClassLoader(), moduleLayer.parents()));
    }

    /**
     * Invokes {@code ModuleLayer.bindToLoader(ClassLoader)}.
     */
    private static void bindToLayer(ModuleClassLoader classLoader, ModuleLayer layer) {
        try {
            LAYER_BIND_TO_LOADER.invokeExact(layer, (ClassLoader) classLoader);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void applyToLoader(ModuleClassLoader classLoader, List<ModuleLayer> parentLayers) {
        // Bind this classloader to all parent layers recursively,
        // to make sure ServiceLoader can find providers defined in parent layers
        Set<ModuleLayer> visitedLayers = new HashSet<>();
        parentLayers.forEach(p -> forLayerAndParents(p, visitedLayers, l -> bindToLayer(classLoader, l)));
    }

    private static void forLayerAndParents(ModuleLayer layer, Set<ModuleLayer> visited, Consumer<ModuleLayer> operation) {
        if (visited.contains(layer)) return;
        visited.add(layer);
        operation.accept(layer);

        if (layer != ModuleLayer.boot()) {
            layer.parents().forEach(l -> forLayerAndParents(l, visited, operation));
        }
    }
}
