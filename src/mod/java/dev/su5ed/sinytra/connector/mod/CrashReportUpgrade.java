package dev.su5ed.sinytra.connector.mod;

import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorEarlyLoader;
import net.minecraftforge.fml.CrashReportCallables;
import net.minecraftforge.forgespi.language.IModInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

public final class CrashReportUpgrade {

    public static void registerCrashLogInfo() {
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

    public static void addCrashReportHeader(StringBuilder builder) {
        builder.append("\n// Hi. I'm Connector, and I'm a crashaholic");
        builder.append("\n").append(StringUtils.repeat('=', 25));
        builder.append("\nSINYTRA CONNECTOR IS PRESENT!");
        builder.append("\nPlease verify issues are not caused by Connector before reporting them to mod authors.");
        builder.append("\nIf you're unsure, file a report on Connector's issue tracker found at ").append(ConnectorUtil.CONNECTOR_ISSUE_TRACKER_URL).append(".");
        builder.append("\n").append(StringUtils.repeat('=', 25));
        builder.append("\n\n");
    }
}
