package org.sinytra.connector.transformer.runner.cli;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.plugin.PluginManager;
import org.sinytra.connector.transformer.runner.PortableTransformerFrontend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
    name = "probe",
    subcommands = {
        HelpCommand.class
    },
    mixinStandardHelpOptions = true
)
public class Main implements Callable<Integer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    @Option(
        names = "--source",
        arity = "*",
        scope = ScopeType.INHERIT,
        description = "Specifies one or more jar paths to transform",
        required = true
    )
    List<Path> sources = new ArrayList<>();

    @Option(
        names = "--clean",
        scope = ScopeType.INHERIT,
        description = "Compiled unpatched vanilla jar",
        required = true
    )
    Path cleanPath;

    @Option(
        names = "--classpath",
        arity = "*",
        scope = ScopeType.INHERIT,
        description = "Specifies transformation classpath"
    )
    List<Path> classPath;

    @Option(
        names = "--game-version",
        scope = ScopeType.INHERIT,
        description = "Specifies game version used in cache file names",
        required = true
    )
    @Nullable
    String gameVersion;
    
    @Option(
        names = "--work-dir",
        scope = ScopeType.INHERIT,
        description = "Where temporary working directories are stored.",
        required = true
    )
    Path workDir;

    public static void main(String[] args) {
        Main main = new Main();
        CommandLine commandLine = new CommandLine(main);
        commandLine.parseArgs(args);

        LOGGER.info("Running Probe Transformer version {}", getVersion());

        int exitCode = new CommandLine(main).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        Path primarySource = sources.getFirst();
        PluginManager.initialize();
        PortableTransformerFrontend.TransformOutput result = new PortableTransformerFrontend().transform(sources, workDir, primarySource, cleanPath, classPath, gameVersion);

        Path output = workDir.resolve("output.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (Writer writer = Files.newBufferedWriter(output)) {
            gson.toJson(result, writer);
        }

        return 0;
    }

    private static String getVersion() {
        String ver = Main.class.getPackage().getImplementationVersion();
        return ver == null ? "(unknown)" : ver;
    }
}
