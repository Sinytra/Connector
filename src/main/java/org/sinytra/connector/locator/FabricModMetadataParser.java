package org.sinytra.connector.locator;

import com.electronwill.nightconfig.core.Config;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.NightConfigWrapper;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.sinytra.connector.util.ConnectorUtil;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses Fabric mod JSON metadata into TOML format at runtime.
 */
public final class FabricModMetadataParser {
    private static final String DEFAULT_LICENSE = "All Rights Reserved";
    // From ModInfo
    private static final Pattern VALID_VERSION = Pattern.compile("^\\d+.*");
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo createForgeMetadata(IModFile modFile, ConnectorFabricModMetadata metadata, boolean lowCode) {
        String modid = metadata.getId();

        Config config = Config.inMemory();
        config.add("modLoader", lowCode ? "lowcodefml" : "javafml");
        config.add("loaderVersion", "[0, )");
        Collection<String> licenses = metadata.getLicense()
            .stream().map(String::trim).filter(l -> !l.isBlank())
            .toList();
        config.add("license", licenses.isEmpty() ? DEFAULT_LICENSE : String.join(", ", metadata.getLicense()));

        config.add("properties", Map.of(
            "metadata", metadata,
            ConnectorUtil.CONNECTOR_MARKER, true
        ));
        config.add(List.of("modproperties", modid), metadata.getCustomValues());

        Config modListConfig = config.createSubConfig();
        modListConfig.add("modId", modid);
        String version = metadata.getNormalizedVersion();
        // Validate version string. If it's invalid, we'll let FML assign a default version instead
        if (VALID_VERSION.matcher(version).matches()) {
            modListConfig.add("version", version);
        }
        else {
            LOGGER.warn("Ignoring invalid version for mod {} in file {}", modid, modFile.getFilePath());
        }
        modListConfig.add("displayName", metadata.getName());
        modListConfig.add("description", metadata.getDescription());
        metadata.getIconPath(-1).ifPresent(icon -> modListConfig.add("logoFile", icon));
        ContactInformation contact = metadata.getContact();
        contact.get("homepage")
            .or(() -> contact.get("source"))
            .or(() -> Optional.of(contact.asMap())
                .filter(m -> !m.isEmpty())
                .map(m -> m.entrySet().iterator().next().getValue()))
            // Ensure string is valid url
            .filter(str -> {
                try {
                    new URL(str);
                    return true;
                } catch (MalformedURLException e) {
                    return false;
                }
            })
            .ifPresent(url -> {
                modListConfig.add("modUrl", url);
                modListConfig.add("displayURL", url);
            });
        modListConfig.add("authors", metadata.getAuthors().stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        modListConfig.add("credits", metadata.getContributors().stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        config.add("mods", List.of(modListConfig));
        switch (metadata.getEnvironment()) {
            case CLIENT -> config.add("displayTest", "IGNORE_ALL_VERSION");
            case SERVER -> config.add("displayTest", "IGNORE_SERVER_VERSION");
        }

        IConfigurable configurable = new NightConfigWrapper(config);
        return new ModFileInfo((ModFile) modFile, configurable, f -> {}, List.of());
    }
}
