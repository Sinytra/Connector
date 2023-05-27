package dev.su5ed.connector.fart;

import net.minecraftforge.fart.api.Transformer;
import net.minecraftforge.fart.internal.RenamingTransformer;
import net.minecraftforge.srgutils.IMappingBuilder;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MixinTargetTransformer implements Transformer {
    private final Context context;
    private final Set<String> mixins;
    private final IMappingFile mappingFile;

    public static Factory factory(Set<String> mixins, IMappingFile mappingFile) {
        return ctx -> new MixinTargetTransformer(ctx, mixins, mappingFile);
    }

    public MixinTargetTransformer(Context context, Set<String> mixins, IMappingFile mappingFile) {
        this.context = context;
        this.mixins = mixins;
        this.mappingFile = mappingFile;
    }

    @Override
    public ClassEntry process(ClassEntry entry) {
        String className = entry.getClassName();
        if (this.mixins.contains(className)) {
            ClassReader reader = new ClassReader(entry.getData());
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            String target = node.invisibleAnnotations.stream()
                .filter(ann -> ann.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .map(ann -> {
                    Object value = ((List<Object>) ann.values.get(1)).get(0);
                    return value instanceof String str ? str : value instanceof Type type ? type.getClassName().replace('.', '/') : null;
                })
                .findFirst()
                .orElse(null);
            if (target != null) {
                IMappingBuilder builder = IMappingBuilder.create();
                IMappingFile.IClass cls = this.mappingFile.getClass(target);
                if (cls != null) {
                    IMappingBuilder.IClass mapCls = builder.addClass(className, className);
                    cls.getMethods().forEach(mtd -> mapCls.method(mtd.getDescriptor(), mtd.getOriginal(), mtd.getMapped()));
                    cls.getFields().forEach(fd -> mapCls.field(fd.getOriginal(), fd.getMapped()));
                    
                    IMappingFile mixinMap = builder.build().getMap("left", "right");
                    Transformer renamer = new RenamingTransformer(context.getClassProvider(), mixinMap, s -> {}, false);
                    return renamer.process(entry);
                }
            }
            return entry;
        }
        return entry;
    }
}
