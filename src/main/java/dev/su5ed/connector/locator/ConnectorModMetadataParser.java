package dev.su5ed.connector.locator;

import com.electronwill.nightconfig.core.Config;
import com.mojang.logging.LogUtils;
import dev.su5ed.connector.ConnectorUtil;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ConnectorModMetadataParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo fabricModJsonParser(IModFile modFile, LoaderModMetadata metadata) {
        String modid = metadata.getId();
        LOGGER.info("Found mod " + modid + " " + metadata.getName());

        Config config = Config.inMemory();
        config.add("modLoader", ConnectorUtil.CONNECTOR_LANGUAGE);
        config.add("loaderVersion", "[0, )");
        Collection<String> licenses = metadata.getLicense();
        config.add("license", licenses.isEmpty() ? "Unknown" : String.join(", ", metadata.getLicense()));
        Map<String, List<EntrypointMetadata>> entryPoints = metadata.getEntrypointKeys().stream()
            .collect(Collectors.toMap(Function.identity(), metadata::getEntrypoints, (a, b) -> a));
        config.add(List.of("modproperties", modid), Map.of("entrypoints", entryPoints));
        config.add("properties", Map.of("jars", metadata.getJars()));

        Config modListConfig = config.createSubConfig();
        modListConfig.add("modId", modid);
        modListConfig.add("version", normalizeVersion(metadata.getVersion().getFriendlyString()));
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
        modListConfig.add("authors", metadata.getAuthors()
            .stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        modListConfig.add("credits", metadata.getContributors()
            .stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        // TODO Dependencies, Environment
        config.add("mods", List.of(modListConfig));

        IConfigurable configurable = new NightConfigWrapper(config);
        return new ModFileInfo((ModFile) modFile, configurable, List.of());
    }

    public static String normalizeVersion(String version) {
        return version.replace("+", "");
    }

    private ConnectorModMetadataParser() {}
}
