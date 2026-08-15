package org.sinytra.connector.service.coremod;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import net.neoforged.neoforgespi.transformation.SimpleClassProcessor;
import net.neoforged.neoforgespi.transformation.SimpleTransformationContext;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.sinytra.connector.api.Constants;
import org.slf4j.Logger;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class ConnectorClassProcessor extends SimpleClassProcessor {
    public static final ProcessorName ID = new ProcessorName(Constants.CONNECTOR_MODID, "class_coremod");
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Target, Consumer<ClassNode>> SUBPROCESSORS;

    static {
        ImmutableMap.Builder<Target, Consumer<ClassNode>> builder = new Builder<>();

        builder.put(
            new Target("net.minecraft.client.KeyMapping"),
            input -> {
                FieldNode field = input.fields.stream().filter(f -> f.name.equals("MAP")).findFirst().orElse(null);
                if (field != null) {
                    int index = input.fields.indexOf(field);
                    // Add the field before KeyMapping#CATEGORY_SORT_ORDER which is the 3rd map
                    // See https://github.com/Sinytra/Connector/issues/723
                    input.fields.add(index, new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "MAP", "Ljava/util/Map;", null, null));

                    LOGGER.debug("Added field for KeyMapping#MAP at index {}", index + 1);
                }
            }
        );

        builder.put(
            new Target("net.minecraft.world.item.CreativeModeTab"),
            input -> {
                MethodVisitor method = input.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;)V", null, null);
                method.visitCode();

                method.visitVarInsn(Opcodes.ALOAD, 0);
                // row
                method.visitVarInsn(Opcodes.ALOAD, 1);
                // column
                method.visitVarInsn(Opcodes.ILOAD, 2);
                // type
                method.visitVarInsn(Opcodes.ALOAD, 3);
                // displayName
                method.visitVarInsn(Opcodes.ALOAD, 4);
                // iconGenerator
                method.visitVarInsn(Opcodes.ALOAD, 5);
                // displayItemsGenerator
                method.visitVarInsn(Opcodes.ALOAD, 6);
                // scrollerSpriteLocation
                method.visitInsn(Opcodes.ACONST_NULL);
                // hasSearchBar
                method.visitInsn(Opcodes.ICONST_0);
                // searchBarWidth
                method.visitLdcInsn(89);
                // tabsImage
                method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/CreativeModeTab$Builder", "CREATIVE_INVENTORY_TABS_IMAGE", "Lnet/minecraft/resources/Identifier;"); // tabsImage
                // labelColor
                method.visitLdcInsn(4210752);
                // slotColor
                method.visitLdcInsn(-2130706433);
                // tabsBefore
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                method.visitInsn(Opcodes.DUP);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                // tabsAfter
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
                method.visitInsn(Opcodes.DUP);
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
                // invoke ctr
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/item/CreativeModeTab", "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;Lnet/minecraft/resources/Identifier;ZILnet/minecraft/resources/Identifier;IILjava/util/List;Ljava/util/List;)V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitEnd();

                LOGGER.debug("Injected vanilla CreativeModeTab constructor");
            }
        );

        builder.put(
            new Target("net.neoforged.neoforge.network.bundle.PacketAndPayloadAcceptor"),
            input -> {
                FieldNode field = input.fields.stream().filter(f -> f.name.equals("consumer")).findFirst().orElse(null);
                if (field != null) {
                    field.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
                    LOGGER.debug("Made public PacketAndPayloadAcceptor#consumer");
                }
            }
        );
        
        builder.put(addOriginalSyntheticField("net.minecraft.client.particle.ParticleEngine", "providers", "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;"));
        builder.put(addOriginalSyntheticField("net.minecraft.client.color.block.BlockColors", "blockColors", "Lnet/minecraft/core/IdMapper;"));
        builder.put(addOriginalSyntheticField("net.minecraft.client.color.item.ItemColors", "itemColors", "Lnet/minecraft/core/IdMapper;"));

        SUBPROCESSORS = builder.build();
    }

    @Override
    public ProcessorName name() {
        return ID;
    }

    @Override
    public Set<Target> targets() {
        return SUBPROCESSORS.keySet();
    }

    @Override
    public void transform(ClassNode input, SimpleTransformationContext context) {
        Consumer<ClassNode> processor = Objects.requireNonNull(SUBPROCESSORS.get(new Target(context.type().getClassName())));

        processor.accept(input);
    }

    @Override
    public Set<ProcessorName> runsBefore() {
        return Set.of(ClassProcessorIds.MIXIN);
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COMPUTING_FRAMES);
    }

    private static Entry<Target, Consumer<ClassNode>> addOriginalSyntheticField(String cls, String name, String desc) {
        return new SimpleEntry<>(
            new Target(cls),
            input -> {
                // Try to find the original field with the same name and copy its access modifiers
                // (accounting for ATs / AWs, but removing final so it can be assigned within our mixin).
                // If we cannot find it, we will use public non - final so that mods can access it.
                var originalAccess = input.fields.stream()
                    .filter(f -> f.name.equals(name))
                    .findFirst()
                    .map(f -> f.access)
                    .orElse(Opcodes.ACC_PUBLIC);
                input.fields.add(new FieldNode((originalAccess & ~Opcodes.ACC_FINAL) | Opcodes.ACC_SYNTHETIC, name, desc, null, null));

                LOGGER.debug("Added field {} to class {}", name, cls);
            }
        );
    }
}
