package dev.su5ed.sinytra.connector.transformer.patch;

import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EnvironmentStripperTransformer implements ClassTransform {
    private static final String ENVIRONMENT_ANNOTATION = Type.getDescriptor(Environment.class);
    private static final EnvType CURRENT_ENV = FabricLoader.getInstance().getEnvironmentType();
    private static final String LAMBDA_PREFIX = "lambda$";

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        boolean applied = false;
        List<MethodNode> removeMethods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            if (remove(method.invisibleAnnotations)) {
                removeMethods.add(method);
                removeMethods.addAll(getMethodLambdas(classNode, method));
                applied = true;
            }
        }
        classNode.methods.removeAll(removeMethods);
        for (Iterator<FieldNode> it = classNode.fields.iterator(); it.hasNext(); ) {
            FieldNode field = it.next();
            if (remove(field.invisibleAnnotations)) {
                it.remove();
                applied = true;
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static List<MethodNode> getMethodLambdas(ClassNode cls, MethodNode method) {
        List<MethodNode> list = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length >= 3) {
                for (Object bsmArg : indy.bsmArgs) {
                    if (bsmArg instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        String name = handle.getName();
                        if (name.startsWith(LAMBDA_PREFIX)) {
                            cls.methods.stream()
                                .filter(m -> m.name.equals(name) && m.desc.equals(handle.getDesc()))
                                .findFirst()
                                .ifPresent(m -> {
                                    list.add(m);
                                    list.addAll(getMethodLambdas(cls, m));
                                });
                        }
                    }
                }
            }
        }
        return list;
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
