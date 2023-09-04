package dev.su5ed.sinytra.connector.service;

import com.google.common.io.Resources;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinLaunchPlugin;
import org.spongepowered.asm.launch.Phases;
import org.spongepowered.asm.transformers.MixinClassReader;

import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

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

    @Override
    public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
        // Support retrieval of untransformed bytecode
        if (!runTransformers) {
            String internalName = name.replace('.', '/');
            String pathToClass = internalName + ".class";
            URL url = Thread.currentThread().getContextClassLoader().getResource(pathToClass);
            byte[] classBytes = uncheck(() -> Resources.asByteSource(url).read());
            if (classBytes != null && classBytes.length != 0) {
                ClassNode classNode = new ClassNode();
                String canonicalName = name.replace('/', '.');
                ClassReader classReader = new MixinClassReader(classBytes, canonicalName);
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                return classNode;
            }
        }
        return super.getClassNode(name, runTransformers);
    }
}
