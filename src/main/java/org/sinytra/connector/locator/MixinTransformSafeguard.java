package org.sinytra.connector.locator;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingException;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.sinytra.connector.transformer.jar.JarTransformer;
import org.sinytra.connector.util.ConnectorConfig;
import org.sinytra.connector.util.PriorityModLoadingException;
import org.slf4j.Logger;

import java.util.List;

public final class MixinTransformSafeguard {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isEnabled() {
        return ConnectorConfig.INSTANCE.get().enableMixinSafeguard();
    }

    public static void trigger(List<JarTransformer.TransformedFabricModPath> failing) throws ModLoadingException {
        if (!isEnabled()) {
            LOGGER.warn("Ignoring {} found incompatibilities as mixin safeguard is disabled", failing.size());
            return;
        }

        StartupNotificationManager.addModMessage("INCOMPATIBLE FABRIC MOD FOUND");
        StringBuilder builder = new StringBuilder();

        String msg = "Found §e" + failing.size() + " incompatible Fabric " + (failing.size() > 1 ? "mods" : "mod") + "§r. Details are provided below.\n\n" +
                "With the current configuration, Connector §ccannot guarantee§r a stable environment. Should you still want to proceed, please restart the game.\n\n" +
                "§7This one-time safety check can be disabled in Connector's config file under \"enableMixinSafeguard\".§r";
        builder.append(msg).append("\n\n");

        failing.forEach(p -> {
            builder.append("Mod file §e").append(p.input().getFileName().toString()).append("§r has failing mixins:\n");
            for (PatchAuditTrail.Candidate failed : p.auditTrail().getFailingMixins()) {
                String[] parts = failed.classNode().name.split("/");
                builder.append("- §c").append(parts[parts.length - 1]).append("§7#§3").append(failed.methodNode().name).append("§r\n");
            }
            builder.append("\n");
        });

        throw new PriorityModLoadingException(ModLoadingIssue.error(builder.toString()));
    }
}
