package dev.su5ed.sinytra.connector.transformer.jar;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.fixes.FieldTypeFix;
import dev.su5ed.sinytra.connector.ConnectorUtil;
import net.minecraftforge.forgespi.locating.IModFile;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;

public class BytecodeFixerUpperFrontend {
    private static final Map<String, Map<String, Pair<Type, Type>>> FIELD_TYPE_CHANGES = Map.of(
        "net/minecraft/world/item/alchemy/PotionBrewing$Mix",
        Map.of(
            "f_43532_", Pair.of(Type.getObjectType("java/lang/Object"), Type.getObjectType("net/minecraft/core/Holder$Reference")),
            "f_43534_", Pair.of(Type.getObjectType("java/lang/Object"), Type.getObjectType("net/minecraft/core/Holder$Reference")
            ))
    );
    private static final List<FieldTypeFix> FIELD_TYPE_ADAPTERS = List.of(
        new FieldTypeFix(Type.getObjectType("net/minecraft/core/Holder$Reference"), Type.getObjectType("java/lang/Object"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Holder$Reference", "get", "()Ljava/lang/Object;")))
    );

    private final BytecodeFixerUpper bfu;
    private final ConnectorUtil.CacheFile cacheFile;

    public BytecodeFixerUpperFrontend() {
        this.bfu = new BytecodeFixerUpper(FIELD_TYPE_CHANGES, FIELD_TYPE_ADAPTERS);

        Path path = JarTransformer.getGeneratedJarPath();
        this.cacheFile = ConnectorUtil.getCached(null, path);
        if (this.cacheFile.isUpToDate()) {
            this.bfu.getGenerator().loadExisting(path);
        }
    }

    public BytecodeFixerUpper unwrap() {
        return this.bfu;
    }

    public void saveGeneratedAdapterJar() throws IOException {
        Path path = JarTransformer.getGeneratedJarPath();
        Files.createDirectories(path.getParent());

        Files.deleteIfExists(path);
        Attributes attributes = new Attributes();
        attributes.putValue("FMLModType", IModFile.Type.GAMELIBRARY.name());
        if (this.bfu.getGenerator().save(path, attributes)) {
            this.cacheFile.save();
        }
    }
}
