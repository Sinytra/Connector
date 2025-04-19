package org.sinytra.connector.transformer.runner.runtime;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.service.*;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class MixinServiceProbe extends MixinServiceAbstract {
    private final ProbeClassProvider classProvider = new ProbeClassProvider();

    private IClassBytecodeProvider bytecodeProvider;

    public void setLoader(ILaunchPluginService.ITransformerLoader loader) {
        if (loader != null) {
            this.bytecodeProvider = new ProbeClassBytecodeProvider(loader);
        } else {
            this.bytecodeProvider = null;
        }
    }

    @Override
    public String getName() {
        return "Probe";
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public IClassProvider getClassProvider() {
        return this.classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return Objects.requireNonNull(this.bytecodeProvider);
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return null;
    }

    @Override
    public IClassTracker getClassTracker() {
        return null;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return List.of();
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        URI uri;
        try {
            uri = this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            if (uri != null) {
                return new ContainerHandleURI(uri);
            }
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
        }
        return new ContainerHandleVirtual(this.getName());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return null;
    }
}
