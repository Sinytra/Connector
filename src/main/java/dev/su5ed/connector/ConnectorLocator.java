package dev.su5ed.connector;

import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LogMarkers;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.StringUtils;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModJarMetadata;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

public class ConnectorLocator extends AbstractModProvider implements IModLocator {
    public static final String FABRIC_MOD_JSON = "fabric.mod.json";
    public static final String CONNECTOR_LANGUAGE = "connector";

    private static final String SUFFIX = ".jar";
    private static final String JIJ_ATTRIBUTE_PREFIX = "Additional-Dependencies-";
    private static final String LANGUAGE_JIJ_DEP = "Language";

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Path modFolder = FMLPaths.MODSDIR.get();

    @Override
    public List<IModLocator.ModFileOrException> scanMods() {
        LOGGER.debug(LogMarkers.SCAN, "Scanning mods dir {} for mods", this.modFolder);
        List<Path> excluded = ModDirTransformerDiscoverer.allExcluded();

        Stream<IModLocator.ModFileOrException> fabricMods = uncheck(() -> Files.list(this.modFolder))
            .filter(p -> !excluded.contains(p) && StringUtils.toLowerCase(p.getFileName().toString()).endsWith(SUFFIX))
            .sorted(Comparator.comparing(path -> StringUtils.toLowerCase(path.getFileName().toString())))
            .map(this::createConnectorMod);
        Stream<IModLocator.ModFileOrException> additionalDeps = getAdditionalDependencies()
            .map(this::createMod);
        
        return Stream.concat(fabricMods, additionalDeps).toList();
    }

    protected IModLocator.ModFileOrException createConnectorMod(Path path) {
        ModJarMetadata mjm = uncheck(() -> {
            Constructor<ModJarMetadata> cst = ModJarMetadata.class.getDeclaredConstructor();
            cst.setAccessible(true);
            return cst.newInstance();
        });
        SecureJar sj = SecureJar.from(
            Manifest::new,
            jar -> jar.moduleDataProvider().findFile(FABRIC_MOD_JSON).isPresent() ? mjm : JarMetadata.from(jar, path),
            (root, p) -> true,
            path
        );

        IModFile mod;
        if (sj.moduleDataProvider().findFile(FABRIC_MOD_JSON).isPresent()) {
            LOGGER.debug(LogMarkers.SCAN, "Found {} mod: {}", FABRIC_MOD_JSON, path);
            mod = new ModFile(sj, this, ConnectorModMetadataParser::fabricModJsonParser);
        } else {
            return new IModLocator.ModFileOrException(null, new ModFileLoadingException("Invalid mod file found " + path));
        }

        mjm.setModFile(mod);
        return new IModLocator.ModFileOrException(mod, null);
    }

    private Stream<Path> getAdditionalDependencies() {
        try {
            URL jarLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(jarLocation.toURI());
            Manifest manifest = new Manifest();
            Path manifestPath = path.resolve("META-INF/MANIFEST.MF");
            manifest.read(Files.newInputStream(manifestPath));
            Attributes attributes = manifest.getMainAttributes();

            Path languageJij = getJarInJar(path, attributes, LANGUAGE_JIJ_DEP);
            return Stream.of(languageJij);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path getJarInJar(Path path, Attributes attributes, String name) {
        String depName = attributes.getValue(JIJ_ATTRIBUTE_PREFIX + name);
        if (depName == null) {
            throw new IllegalArgumentException("Required " + name + " embedded jar not found");
        }
        return path.resolve(depName);
    }

    @Override
    public String name() {
        return "connector";
    }

    @Override
    public void scanFile(IModFile modFile, Consumer<Path> pathConsumer) {}

    @Override
    public void initArguments(Map<String, ?> arguments) {}

    @Override
    public boolean isValid(IModFile modFile) {
        return true;
    }
}
