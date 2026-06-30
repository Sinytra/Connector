package org.sinytra.connector.service.coremod;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.connector.api.Constants;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class FabricASMClassGenerator implements ClassProcessor {
    public static final ProcessorName ID = new ProcessorName(Constants.CONNECTOR_MODID, "fabric_asm");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> GEN_PACKAGES = Set.of("com.chocohead.gen.mixin", "me.shedaniel.gen.mixin");
    public static final List<URL> URLS = new ArrayList<>();

    // Called from injected ASM hook, see ConnectorMethodProcessor
    @SuppressWarnings("unused")
    public static Consumer<URL> fishAddURL() {
        return URLS::add;
    }

    // Called from injected ASM hook, see ConnectorMethodProcessor
    @SuppressWarnings("unused")
    public static String flattenMixinClass(String name) {
        return name.replace('/', '_');
    }

    // Called from injected ASM hook, see ConnectorMethodProcessor
    @SuppressWarnings("unused")
    public static void permitEnumSubclass(ClassNode enumNode, String anonymousClassName) {
        if (enumNode.permittedSubclasses != null) {
            enumNode.permittedSubclasses.add(anonymousClassName);
        }
    }

    @Override
    public ProcessorName name() {
        return ID;
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        String name = context.type().getClassName();
        for (String pkg : GEN_PACKAGES) {
            if (name.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        String fileName = context.type().getInternalName() + ".class";
        try (InputStream ins = findGeneratedFile(fileName)) {
            if (ins != null) {
                ClassNode node = context.node();
                ClassReader reader = new ClassReader(ins);
                reader.accept(node, 0);
                return ComputeFlags.COMPUTE_FRAMES;
            }
        } catch (IOException e) {
            LOGGER.error("Error processing class {}", fileName, e);
        }

        return ComputeFlags.NO_REWRITE;
    }

    @Override
    public Set<String> generatesPackages() {
        return GEN_PACKAGES;
    }

    private static InputStream findGeneratedFile(String name) {
        for (URL url : FabricASMClassGenerator.URLS) {
            try {
                URL pathUrl = new URL(url, name);
                URLConnection connection = pathUrl.openConnection();
                if (connection != null) {
                    return connection.getInputStream();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
