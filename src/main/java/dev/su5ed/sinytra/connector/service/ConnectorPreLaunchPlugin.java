package dev.su5ed.sinytra.connector.service;

import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.EnumSet;

public class ConnectorPreLaunchPlugin implements ILaunchPluginService {
    public static final String NAME = "connector_pre_launch";
    private static final EnumSet<Phase> YAY = EnumSet.of(Phase.AFTER);
    private static final EnumSet<Phase> NAY = EnumSet.noneOf(Phase.class);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initializeLaunch(ITransformerLoader transformerLoader, NamedPath[] specialPaths) {
        // Apply provider inheritance fix from newer SJH versions
        ServiceProviderInheritanceWorkaround.apply();
        // Decorate mixin config's with mod IDs, enabling method prefix functionality
        FabricMixinBootstrap.init();
        // Setup Fabric Loader
        ConnectorEarlyLoader.init();
        // Apply Fabric ASM fix
        FabricASMFixer.injectMinecraftModuleReader();
    }

    @Override
    public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
        return classType.getInternalName().startsWith("net/minecraft/") ? YAY : NAY;
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        // Widen access of all package-private and protected class members to be public
        // This is required due to package differences between yarn/intermediary/mojmap
        // Changing mapping might move accessors of a field into a different package, leading to a crash
        boolean rewrite = false;
        for (FieldNode field : classNode.fields) {
            if ((field.access & 0x7) != Opcodes.ACC_PRIVATE) {
                field.access = field.access & ~0x7 | Opcodes.ACC_PUBLIC;
                rewrite = true;
            }
        }
        for (MethodNode method : classNode.methods) {
            if ((method.access & 0x7) != Opcodes.ACC_PRIVATE) {
                method.access = method.access & ~0x7 | Opcodes.ACC_PUBLIC;
                rewrite = true;
            }
        }
        return rewrite ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }
}
