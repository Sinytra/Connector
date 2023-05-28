package dev.su5ed.connector.service;

import com.google.common.collect.ImmutableList;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;

import java.util.Collection;

public class ConnectorMixinService extends MixinServiceModLauncher {

    @Override
    public Collection<String> getPlatformAgents() {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.addAll(super.getPlatformAgents());
        builder.add("dev.su5ed.connector.service.ConnectorMixinAgent");
        return builder.build();
    }
}
