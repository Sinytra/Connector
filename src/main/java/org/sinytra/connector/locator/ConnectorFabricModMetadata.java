package org.sinytra.connector.locator;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ContactInformation;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.Person;
import net.fabricmc.loader.impl.metadata.EntrypointMetadata;
import net.fabricmc.loader.impl.metadata.LoaderModMetadata;
import net.fabricmc.loader.impl.metadata.NestedJarEntry;
import org.sinytra.connector.util.ConnectorUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A wrapper around {@link LoaderModMetadata} allowing us to tweak certain properties
 * to accomodate FML constraints, such as the modid and mod version.
 */
public class ConnectorFabricModMetadata implements LoaderModMetadata {
    private static final String NORMALIZER_SUFFIX = "_nojpms";

    private final LoaderModMetadata wrapped;
    private final String normalModid;

    public ConnectorFabricModMetadata(LoaderModMetadata wrapped) {
        this.wrapped = wrapped;
        // Adjust modid to accomodate Java Module System requirements
        String replaced = this.wrapped.getId().replace('-', '_');
        // If modid is amongst reserved keywords, add a suffix
        this.normalModid = ConnectorUtil.isJavaReservedKeyword(replaced) ? replaced + NORMALIZER_SUFFIX : replaced;
    }

    /**
     * Adjust version to accomodate Java Module System requirements
     */
    private static String normalizeVersion(String version) {
        return version.replace("+", "");
    }

    public String getNormalizedVersion() {
        return normalizeVersion(getVersion().getFriendlyString());
    }

    @Override
    public String getId() {
        return this.normalModid;
    }

    @Override
    public int getSchemaVersion() {
        return this.wrapped.getSchemaVersion();
    }

    @Override
    public Map<String, String> getLanguageAdapterDefinitions() {
        return this.wrapped.getLanguageAdapterDefinitions();
    }

    @Override
    public Collection<NestedJarEntry> getJars() {
        return this.wrapped.getJars();
    }

    @Override
    public Collection<String> getMixinConfigs(EnvType type) {
        return this.wrapped.getMixinConfigs(type);
    }

    @Override
    public String getAccessWidener() {
        return this.wrapped.getAccessWidener();
    }

    @Override
    public boolean loadsInEnvironment(EnvType type) {
        return this.wrapped.loadsInEnvironment(type);
    }

    @Override
    public Collection<String> getOldInitializers() {
        return this.wrapped.getOldInitializers();
    }

    @Override
    public List<EntrypointMetadata> getEntrypoints(String type) {
        return ConnectorUtil.filterMixinExtrasEntrypoints(this.wrapped.getEntrypoints(type));
    }

    @Override
    public Collection<String> getEntrypointKeys() {
        return this.wrapped.getEntrypointKeys();
    }

    @Override
    public void emitFormatWarnings() {
        this.wrapped.emitFormatWarnings();
    }

    @Override
    public void setVersion(Version version) {
        this.wrapped.setVersion(version);
    }

    @Override
    public void setDependencies(Collection<ModDependency> dependencies) {
        this.wrapped.setDependencies(dependencies);
    }

    @Override
    public String getType() {
        return this.wrapped.getType();
    }

    @Override
    public Collection<String> getProvides() {
        Set<String> provides = new HashSet<>(this.wrapped.getProvides());
        String normalized = getId();
        // Remove normalized modid from provided ids to prevent duplicates
        provides.remove(normalized);
        // If we modified the modid, provide the original
        String original = this.wrapped.getId();
        if (!normalized.equals(original)) {
            // Add original modid to mod lookup
            provides.add(original);
        }
        return provides;
    }

    @Override
    public Version getVersion() {
        return this.wrapped.getVersion();
    }

    @Override
    public ModEnvironment getEnvironment() {
        return this.wrapped.getEnvironment();
    }

    @Override
    public Collection<ModDependency> getDependencies() {
        return this.wrapped.getDependencies();
    }

    @Override
    public String getName() {
        return this.wrapped.getName();
    }

    @Override
    public String getDescription() {
        return this.wrapped.getDescription();
    }

    @Override
    public Collection<Person> getAuthors() {
        return this.wrapped.getAuthors();
    }

    @Override
    public Collection<Person> getContributors() {
        return this.wrapped.getContributors();
    }

    @Override
    public ContactInformation getContact() {
        return this.wrapped.getContact();
    }

    @Override
    public Collection<String> getLicense() {
        return this.wrapped.getLicense();
    }

    @Override
    public Optional<String> getIconPath(int size) {
        return this.wrapped.getIconPath(size);
    }

    @Override
    public boolean containsCustomValue(String key) {
        return this.wrapped.containsCustomValue(key);
    }

    @Override
    public CustomValue getCustomValue(String key) {
        return this.wrapped.getCustomValue(key);
    }

    @Override
    public Map<String, CustomValue> getCustomValues() {
        return this.wrapped.getCustomValues();
    }

    @Override
    public boolean containsCustomElement(String key) {
        return this.wrapped.containsCustomValue(key);
    }
}
