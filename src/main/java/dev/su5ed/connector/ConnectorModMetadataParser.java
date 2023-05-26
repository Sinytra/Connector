package dev.su5ed.connector;

import com.electronwill.nightconfig.core.Config;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.ModMetadataParser;
import net.fabricmc.loader.impl.metadata.ParseMetadataException;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;
import net.minecraftforge.fml.loading.moddiscovery.NightConfigWrapper;
import net.minecraftforge.forgespi.language.IConfigurable;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.su5ed.connector.ConnectorLocator.FABRIC_MOD_JSON;

public final class ConnectorModMetadataParser {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static IModFileInfo fabricModJsonParser(final IModFile modFile) {
        SecureJar jar = modFile.getSecureJar();
        Path path = Path.of(jar.moduleDataProvider().findFile(FABRIC_MOD_JSON).orElseThrow());
        try (InputStream ins = Files.newInputStream(path)) {
            String modPath = jar.getPrimaryPath().toAbsolutePath().normalize().toString();
            LoaderModMetadata metadata = ModMetadataParser.parseMetadata(ins, modPath, Collections.emptyList(), new VersionOverrides(), new DependencyOverrides(Paths.get("randomMissing")), false);
            LOGGER.info("Found mod " + metadata.getId() + " " + metadata.getName() + " from authors " + metadata.getAuthors());

            Config config = Config.inMemory();
            config.add("modLoader", ConnectorLocator.CONNECTOR_LANGUAGE);
            config.add("loaderVersion", "[0, )");
            config.add("license", String.join(", ", metadata.getLicense()));
            Map<String, List<EntrypointMetadata>> entryPoints = metadata.getEntrypointKeys().stream()
                .collect(Collectors.toMap(Function.identity(), metadata::getEntrypoints, (a, b) -> a));
            config.add(List.of("modproperties", metadata.getId()), Map.of("entrypoints", entryPoints));

            Config modListConfig = config.createSubConfig();
            modListConfig.add("modId", metadata.getId());
            modListConfig.add("version", metadata.getVersion().getFriendlyString());
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
        } catch (IOException | ParseMetadataException e) {
            LOGGER.error("Error parsing metadata", e);
            throw new RuntimeException(e);
        }
    }

    private ConnectorModMetadataParser() {}
}
