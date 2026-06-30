package org.sinytra.connector.transformer.jar;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.types.SimpleTypeAdapter;
import org.sinytra.adapter.types.TypeAdapter;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;

import static org.sinytra.adapter.util.AdapterUtil.insnList;

public class BytecodeFixerUpperFrontend {
    // TODO 26.1 update
    private static final List<TypeAdapter> FIELD_TYPE_ADAPTERS = List.of(
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/core/Holder$Reference"), Type.getObjectType("java/lang/Object"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/core/Holder$Reference", "value", "()Ljava/lang/Object;"))),
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/resources/ResourceLocation"), Type.getObjectType("java/lang/String"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "toString", "()Ljava/lang/String;"))),
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/world/item/ItemStack"), Type.getObjectType("net/minecraft/world/item/Item"), (list, insn) ->
            list.insert(insn, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/ItemStack", "getItem", "()Lnet/minecraft/world/item/Item;"))),
        new SimpleTypeAdapter(Type.getObjectType("net/minecraft/world/entity/Mob"), Type.getObjectType("net/minecraft/world/entity/monster/Monster"), (list, insn) -> {}),
        new SimpleTypeAdapter(
            Type.getObjectType("java/util/function/Consumer"),
            Type.getObjectType("net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
            (list, insn) ->
                list.insert(insn, insnList(
                    new TypeInsnNode(Opcodes.NEW, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
                    new InsnNode(Opcodes.DUP_X1),
                    new InsnNode(Opcodes.SWAP),
                    new MethodInsnNode(Opcodes.INVOKESPECIAL, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor", "<init>", "(Ljava/util/function/Consumer;)V")
                ))),
        new SimpleTypeAdapter(
            Type.getObjectType("net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor"),
            Type.getObjectType("java/util/function/Consumer"),
            (list, insn) ->
                list.insert(insn, new FieldInsnNode(Opcodes.GETFIELD, "net/neoforged/neoforge/network/bundle/PacketAndPayloadAcceptor", "consumer", "Ljava/util/function/Consumer;")))
    );

    private final BytecodeFixerUpper bfu;
    private final TransformerUtil.CacheFile cacheFile;
    private final Path generatedJarPath;

    public BytecodeFixerUpperFrontend(ClassLookup cleanLookup, ClassLookup dirtyLookup, TransformerEnvironment environment) {
        this.bfu = new BytecodeFixerUpper(cleanLookup, dirtyLookup, FIELD_TYPE_ADAPTERS);

        this.generatedJarPath = environment.getGeneratedJarPath();
        this.cacheFile = TransformerUtil.getCached(null, this.generatedJarPath, environment.getJarCacheVersion());
        if (this.cacheFile.isUpToDate()) {
            this.bfu.getGenerator().loadExisting(this.generatedJarPath);
        }
    }

    public BytecodeFixerUpper unwrap() {
        return this.bfu;
    }

    public void saveGeneratedAdapterJar() throws IOException {
        Files.createDirectories(this.generatedJarPath.getParent());

        Files.deleteIfExists(this.generatedJarPath);
        Attributes attributes = new Attributes();
        attributes.putValue("FMLModType", "GAMELIBRARY");
        if (this.bfu.getGenerator().save(this.generatedJarPath, attributes)) {
            this.cacheFile.save();
        }
    }
}
