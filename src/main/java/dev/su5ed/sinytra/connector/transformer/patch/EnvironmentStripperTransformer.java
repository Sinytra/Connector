package dev.su5ed.sinytra.connector.transformer.patch;

import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Iterator;
import java.util.List;

public class EnvironmentStripperTransformer implements ClassTransform {
    private static final String ENVIRONMENT_ANNOTATION = Type.getDescriptor(Environment.class);
    private static final EnvType CURRENT_ENV = FabricLoader.getInstance().getEnvironmentType();

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchEnvironment environment) {
        boolean applied = false;
        for (Iterator<MethodNode> it = classNode.methods.iterator(); it.hasNext(); ) {
            MethodNode method = it.next();
            if (remove(method.invisibleAnnotations)) {
                it.remove();
                applied = true;
            }
        }
        // Field stripping is currently broken in fabric-loader, therefore we skip it until the issue is fixed upstream
        // https://github.com/FabricMC/fabric-loader/issues/833
//        for (Iterator<FieldNode> it = classNode.fields.iterator(); it.hasNext(); ) {
//            FieldNode field = it.next();
//            if (remove(field.invisibleAnnotations)) {
//                it.remove();
//                applied = true;
//            }
//        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    // We strip annotations ahead of time to avoid class resolution issues leading to CNFEs
    private static boolean remove(@Nullable List<AnnotationNode> annotations) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (ENVIRONMENT_ANNOTATION.equals(annotation.desc) && remove(annotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean remove(AnnotationNode node) {
        if (node.values.size() != 2) {
            throw new IllegalArgumentException("Unexpected " + node.values.size() + " values for annotation " + node.desc);
        }
        String[] args = (String[]) node.values.get(1);
        if (args.length != 2) {
            throw new IllegalArgumentException("Unexpected size of annotation value array" + args.length + " for " + node.desc);
        }
        String side = args[1];
        EnvType type = EnvType.valueOf(side);
        return CURRENT_ENV != type;
    }
}
