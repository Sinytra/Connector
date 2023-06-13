package dev.su5ed.connector.remap;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlWriter;
import dev.su5ed.connector.ConnectorUtil;
import dev.su5ed.connector.loader.ConnectorLoaderModMetadata;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fart.internal.EntryImpl;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModMetadataConverter implements Transformer {
    private static final String FABRIC_METADATA = ConnectorUtil.FABRIC_MOD_JSON;
    private static final String FORGE_METADATA = "META-INF/mods.toml";
    private static final String DEFAULT_LICENSE = "All Rights Reserved";

    private final ConnectorLoaderModMetadata metadata;

    public ModMetadataConverter(ConnectorLoaderModMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public ResourceEntry process(ResourceEntry entry) {
        if (entry.getName().equals(FABRIC_METADATA)) {
            Config forgeModMetadata = createForgeMetadata();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new TomlWriter().write(forgeModMetadata, out);
            return new EntryImpl.ResourceEntry(FORGE_METADATA, entry.getTime(), out.toByteArray());
        }
        return entry;
    }

    private Config createForgeMetadata() {
        String modid = this.metadata.getId();

        Config config = Config.inMemory();
        config.add("modLoader", ConnectorUtil.CONNECTOR_LANGUAGE);
        config.add("loaderVersion", "[0, )"); // TODO
        Collection<String> licenses = this.metadata.getLicense();
        config.add("license", licenses.isEmpty() ? DEFAULT_LICENSE : String.join(", ", this.metadata.getLicense()));

        this.metadata.getEntrypointKeys().forEach(key -> {
            List<Config> configs = this.metadata.getEntrypoints(key).stream()
                .map(info -> {
                    Config entryPoint = config.createSubConfig();
                    entryPoint.add("adapter", info.getAdapter());
                    entryPoint.add("value", info.getValue());
                    return entryPoint;
                })
                .toList();
            config.add(List.of("modproperties", modid, "entrypoints", key), configs);
        });
        config.add(List.of("properties", "jars"), this.metadata.getJars().stream().map(NestedJarEntry::getFile).toList());

        Config modListConfig = config.createSubConfig();
        modListConfig.add("modId", modid);
        modListConfig.add("version", this.metadata.getNormalizedVersion());
        modListConfig.add("displayName", this.metadata.getName());
        modListConfig.add("description", this.metadata.getDescription());
        this.metadata.getIconPath(-1).ifPresent(icon -> modListConfig.add("logoFile", icon));
        ContactInformation contact = this.metadata.getContact();
        contact.get("homepage")
            .or(() -> contact.get("source"))
            .or(() -> Optional.of(contact.asMap())
                .filter(m -> !m.isEmpty())
                .map(m -> m.entrySet().iterator().next().getValue()))
            .ifPresent(url -> {
                modListConfig.add("modUrl", url);
                modListConfig.add("displayURL", url);
            });
        modListConfig.add("authors", this.metadata.getAuthors().stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        modListConfig.add("credits", this.metadata.getContributors().stream()
            .map(Person::getName)
            .collect(Collectors.joining(", ")));
        // TODO Dependencies, Environment
        config.add("mods", List.of(modListConfig));
        return config;
    }
}
