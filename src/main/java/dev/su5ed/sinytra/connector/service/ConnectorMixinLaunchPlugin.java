package dev.su5ed.sinytra.connector.service;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinLaunchPlugin;
import org.spongepowered.asm.launch.Phases;

import java.util.EnumSet;

public class ConnectorMixinLaunchPlugin extends MixinLaunchPlugin {
    public static final String NAME = "connector_mixin_plugin";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty, String reason) {
        if (ConnectorMixinLaunchPlugin.NAME.equals(reason)) {
            return Phases.NONE;
        }
        return super.handlesClass(classType, isEmpty, reason);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        return !reason.equals(ConnectorMixinLaunchPlugin.NAME) && super.processClass(phase, classNode, classType, reason);
    }
}
