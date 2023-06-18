package dev.su5ed.sinytra.connector.loader;

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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConnectorLoaderModMetadata implements LoaderModMetadata {
    private final LoaderModMetadata wrapped;

    public ConnectorLoaderModMetadata(LoaderModMetadata wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Adjust modid to accomodate Java Module System requirements
     */
    private static String normalizeModid(String modid) {
        return modid.replace('-', '_');
    }

    private static String normalizeVersion(String version) {
        return version.replace("+", "");
    }

    public String getNormalizedVersion() {
        return normalizeVersion(getVersion().getFriendlyString());
    }

    @Override
    public String getId() {
        return normalizeModid(this.wrapped.getId());
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
        return this.wrapped.getEntrypoints(type);
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
        return this.wrapped.getProvides();
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
