package org.sinytra.connector.service;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;
import org.sinytra.connector.ConnectorEarlyLoader;
import org.sinytra.connector.locator.MixinTransformSafeguard;

public class ConnectorPrelaunch implements IModLanguageLoader {
    private static boolean initialized;

    @Override
    public String name() {
        return "connector:prelaunch";
    }

    @Override
    public String version() {
        if (!initialized) {
            MixinTransformSafeguard.trigger();
            ConnectorEarlyLoader.finalizeCache();
            initialized = true;
        }
        return "1.0.0";
    }

    @Override
    public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) {
        throw new UnsupportedOperationException();
    }
}
