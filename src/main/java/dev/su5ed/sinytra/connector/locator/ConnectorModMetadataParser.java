package dev.su5ed.sinytra.connector.locator;

import com.electronwill.nightconfig.core.Config;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import dev.su5ed.sinytra.connector.loader.ConnectorLoaderModMetadata;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ConnectorModMetadataParser {
    private static final String DEFAULT_LICENSE = "All Rights Reserved";

    public static IModFileInfo createForgeMetadata(IModFile modFile, ConnectorLoaderModMetadata metadata) {
        String modid = metadata.getId();

        Config config = Config.inMemory();
        config.add("modLoader", ConnectorUtil.CONNECTOR_LANGUAGE);
        config.add("loaderVersion", "[0, )"); // TODO
        Collection<String> licenses = metadata.getLicense();
        config.add("license", licenses.isEmpty() ? DEFAULT_LICENSE : String.join(", ", metadata.getLicense()));

        config.add("properties", Map.of("metadata", metadata));

        Config modListConfig = config.createSubConfig();
        modListConfig.add("modId", modid);
        modListConfig.add("version", metadata.getNormalizedVersion());
        modListConfig.add("displayName", metadata.getName());
        modListConfig.add("description", metadata.getDescription());
        metadata.getIconPath(-1).ifPresent(icon -> modListConfig.add("logoFile", icon));
        ContactInformation contact = metadata.getContact();
        contact.get("homepage")
            .or(() -> contact.get("source"))
            .or(() -> Optional.of(contact.asMap())
                .filter(m -> !m.isEmpty())
                .map(m -> m.entrySet().iterator().next().getValue()))
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
        // TODO Dependencies, Environment
        config.add("mods", List.of(modListConfig));

        IConfigurable configurable = new NightConfigWrapper(config);
        return new ModFileInfo((ModFile) modFile, configurable, List.of());
    }

    private ConnectorModMetadataParser() {}
}
