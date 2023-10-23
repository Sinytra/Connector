/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.su5ed.sinytra.connector.service;

import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.version.VersionInterval;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.util.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

// Source: https://github.com/FabricMC/fabric-loader/blob/cfb300f6c7271ff161b7a4de321b5de6d9f1d328/src/main/java/net/fabricmc/loader/impl/launch/FabricMixinBootstrap.java
public final class FabricMixinBootstrap {
    private FabricMixinBootstrap() {}

    public static void init() {
        Map<String, ModFileInfo> configToModMap = new HashMap<>();

        for (ModFileInfo modFile : LoadingModList.get().getModFiles()) {
            Manifest manifest = modFile.getFile().getSecureJar().moduleDataProvider().getManifest();
            String configsValue = manifest.getMainAttributes().getValue(Constants.ManifestAttributes.MIXINCONFIGS);
            if (configsValue != null) {
                for (String config : configsValue.split(",")) {
                    ModFileInfo prev = configToModMap.putIfAbsent(config, modFile);
                    if (prev != null)
                        throw new RuntimeException(String.format("Non-unique Mixin config name %s used by the mods %s and %s", config, prev.moduleName(), modFile.moduleName()));
                }
            }
        }

        try {
            IMixinConfig.class.getMethod("decorate", String.class, Object.class);
            MixinConfigDecorator.apply(configToModMap);
        } catch (NoSuchMethodException e) {
            Log.info(LogCategory.MIXIN, "Detected old Mixin version without config decoration support");
        }
    }

    private static final class MixinConfigDecorator {
        private static final List<LoaderMixinVersionEntry> VERSIONS = List.of(
            LoaderMixinVersionEntry.create("0.12.0-", FabricUtil.COMPATIBILITY_0_10_0)
        );

        static void apply(Map<String, ModFileInfo> configToModMap) {
            for (Config rawConfig : Mixins.getConfigs()) {
                ModFileInfo mod = configToModMap.get(rawConfig.getName());
                if (mod == null) continue;

                IMixinConfig config = rawConfig.getConfig();
                config.decorate(FabricUtil.KEY_MOD_ID, mod.moduleName());
                if (!mod.getMods().isEmpty()) {
                    String modid = mod.getMods().get(0).getModId();
                    int compat = ConnectorEarlyLoader.isConnectorMod(modid) ? FabricLoaderImpl.INSTANCE.getModContainer(modid)
                        .map(MixinConfigDecorator::getMixinCompat)
                        .orElse(FabricUtil.COMPATIBILITY_0_10_0)
                        : FabricUtil.COMPATIBILITY_0_10_0;
                    config.decorate(FabricUtil.KEY_COMPATIBILITY, compat);
                }
            }
        }

        private static int getMixinCompat(ModContainer mod) {
            // infer from loader dependency by determining the least relevant loader version the mod accepts
            // AND any loader deps

            List<VersionInterval> reqIntervals = List.of(VersionInterval.INFINITE);

            for (ModDependency dep : mod.getMetadata().getDependencies()) {
                if (dep.getModId().equals("fabricloader") || dep.getModId().equals("fabric-loader")) {
                    if (dep.getKind() == ModDependency.Kind.DEPENDS) {
                        reqIntervals = VersionInterval.and(reqIntervals, dep.getVersionIntervals());
                    }
                    else if (dep.getKind() == ModDependency.Kind.BREAKS) {
                        reqIntervals = VersionInterval.and(reqIntervals, VersionInterval.not(dep.getVersionIntervals()));
                    }
                }
            }

            if (reqIntervals.isEmpty()) throw new IllegalStateException("mod " + mod + " is incompatible with every loader version?"); // shouldn't get there

            Version minLoaderVersion = reqIntervals.get(0).getMin(); // it is sorted, to 0 has the absolute lower bound

            if (minLoaderVersion != null) { // has a lower bound
                for (LoaderMixinVersionEntry version : VERSIONS) {
                    if (minLoaderVersion.compareTo(version.loaderVersion) >= 0) { // lower bound is >= current version
                        return version.mixinVersion;
                    }
                    else {
                        break;
                    }
                }
            }

            return FabricUtil.COMPATIBILITY_0_9_2;
        }

        private record LoaderMixinVersionEntry(SemanticVersion loaderVersion, int mixinVersion) {
            public static LoaderMixinVersionEntry create(String loaderVersion, int mixinVersion) {
                return new LoaderMixinVersionEntry(uncheck(() -> SemanticVersion.parse(loaderVersion)), mixinVersion);
            }
        }
    }
}
