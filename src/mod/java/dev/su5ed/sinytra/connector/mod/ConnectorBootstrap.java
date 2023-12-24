package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prepare Fabric Loader before game classes and mixins are loaded
 */
public class ConnectorBootstrap implements IMixinConfigPlugin {

    static {
        registerCrashLogInfo();
    }

    private static void registerCrashLogInfo() {
        CrashReportCallables.registerCrashCallable("Sinytra Connector", () -> {
            String format = "| %-50.50s | %-30.30s | %-30.30s | %-20.20s |";
            String version = ConnectorBootstrap.class.getModule().getDescriptor().rawVersion().orElse("<unknown>");
            StringBuilder builder = new StringBuilder();
            builder.append(version);
            builder.append("\n\t\tSINYTRA CONNECTOR IS PRESENT!");
            builder.append("\n\t\tPlease verify issues are not caused by Connector before reporting them to mod authors. If you're unsure, file a report on Connector's issue tracker.");
            builder.append("\n\t\tConnector's issue tracker can be found at ").append(ConnectorUtil.CONNECTOR_ISSUE_TRACKER_URL).append(".");
            List<IModInfo> mods = ConnectorEarlyLoader.getConnectorMods();
            if (!mods.isEmpty()) {
                builder.append("\n\t\tInstalled Fabric mods:");
                builder.append("\n\t\t").append(String.format(Locale.ENGLISH, format,
                    StringUtils.repeat('=', 50),
                    StringUtils.repeat('=', 30),
                    StringUtils.repeat('=', 30),
                    StringUtils.repeat('=', 20)
                ));
                mods.stream()
                    .map(m -> String.format(Locale.ENGLISH, format,
                        m.getOwningFile().getFile().getFileName(),
                        m.getDisplayName(),
                        m.getModId(),
                        m.getVersion().toString()))
                    .forEach(s -> builder.append("\n\t\t").append(s));
            }
            return builder.toString();
        });
    }

    // We don't need any of the mixin stuff
    //@formatter:off
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return null;}
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return true;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {return null;}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    //@formatter:on
}
