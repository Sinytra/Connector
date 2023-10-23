package dev.su5ed.sinytra.connector.service;

import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;

import java.util.EnumSet;

public class ConnectorPreLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "connector_pre_launch";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return EnumSet.noneOf(Phase.class);
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        FabricMixinBootstrap.init();
    }
}
