package org.sinytra.connector.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.util.ConnectorConfig;
import org.slf4j.Logger;

import java.util.List;

public final class MixinTransformSafeguard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<JarTransformer.TransformedFabricModPath> failing;

    public static boolean isEnabled() {
        return ConnectorConfig.INSTANCE.get().enableMixinSafeguard();
    }

    public static void prepare(List<JarTransformer.TransformedFabricModPath> mods) {
        if (!isEnabled()) {
            LOGGER.warn("Ignoring {} found incompatibilities as mixin safeguard is disabled", mods.size());
            return;
        }
        if (!mods.isEmpty()) {
            failing = mods;
        }
    }

    public static void trigger() {
        if (failing == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();

        String msg = "Found §e" + failing.size() + " incompatible Fabric " + (failing.size() > 1 ? "mods" : "mod") + "§r. Details are provided below.\n\n" +
            "With the current configuration, Connector §ccannot guarantee§r a stable environment.\n\n" +
            "§7This one-time safety check can be disabled in Connector's config file under \"enableMixinSafeguard\".§r";
        builder.append(msg).append("\n\n");

        failing.forEach(p -> {
            builder.append("Mod file §e").append(p.input().getFileName().toString()).append("§r has failing mixins:\n");
            for (AuditTrail.Candidate failed : p.auditTrail().getFailingMixins()) {
                String[] parts = failed.classNode().name.split("/");
                builder.append("- §c").append(parts[parts.length - 1]).append("§7#§3").append(failed.methodNode().name).append("§r\n");
            }
            builder.append("\n");
        });

        throw new ModLoadingException(ModLoadingIssue.error(builder.toString()));
    }
}
