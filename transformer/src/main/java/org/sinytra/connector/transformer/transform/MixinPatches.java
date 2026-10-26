package org.sinytra.connector.transformer.transform;

import org.sinytra.adapter.api.MethodPatch;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MixinPatches {
    public static List<MethodPatch> getPriorityPatches() {
        return List.of(
            MethodPatch.builder()
                .targetClass("net/minecraft/world/item/ItemStack")
                .targetMethod("useOn")
                .targetInjectionPoint("INVOKE", "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;")
                .modifyTarget("connector_useOn")
                .replaceInjectionPoint("RETURN", null)
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/client/Minecraft")
                .targetMethod("<init>")
                .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startClient(Ljava/io/File;Ljava/lang/Object;)V")
                .modifyInjectionPoint("Ljava/lang/Thread;currentThread()Ljava/lang/Thread;")
                .build(),
            MethodPatch.builder()
                .targetClass("net/minecraft/server/Main")
                .targetMethod("main([Ljava/lang/String;)V")
                .targetInjectionPoint("Lnet/fabricmc/loader/impl/game/minecraft/Hooks;startServer(Ljava/io/File;Ljava/lang/Object;)V")
                .modifyInjectionPoint("Lnet/neoforged/neoforge/server/loading/ServerModLoader;load()V")
                .build()
        );
    }

    public static List<MethodPatch> getPatches() {
        final List<Object> patches = List.of(
            // NeoForge moves shearing to IShearable with no viable replacement, so we disable it for now
            MethodPatch.builder()
                .targetClass("net/minecraft/world/entity/animal/golem/SnowGolem", "net/minecraft/world/entity/animal/sheep/Sheep")
                .targetMethod("mobInteract")
                .targetInjectionPoint("Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z")
                .disable()
                .build()
        );

        return patches.stream()
            .flatMap(p -> p instanceof List<?> lst ? lst.stream() : Stream.of(p))
            .map(MethodPatch.class::cast)
            .collect(Collectors.toList()); // Mutable list
    }
}
