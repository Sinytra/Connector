package org.sinytra.connector.transformer.patch;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvironmentInterface;
import net.fabricmc.api.EnvironmentInterfaces;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.PatchContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.transform.ClassTransformer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class EnvironmentStripperTransformer implements ClassTransformer {
    private static final String ENVIRONMENT_ANNOTATION = Type.getDescriptor(Environment.class);
    private static final String ENVIRONMENT_INTERFACE_DESCRIPTOR = Type.getDescriptor(EnvironmentInterface.class);
    private static final String ENVIRONMENT_INTERFACES_DESCRIPTOR = Type.getDescriptor(EnvironmentInterfaces.class);
    private static final String LAMBDA_PREFIX = "lambda$";

    private final EnvType envType;

    public EnvironmentStripperTransformer(EnvType envType) {
        this.envType = envType;
    }

    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        boolean applied = stripEnvironmentInterface(classNode.interfaces, classNode.invisibleAnnotations);

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
        return applied ? PatchResult.APPLY : PatchResult.PASS;
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

    private boolean stripEnvironmentInterface(List<String> interfaces, @Nullable List<AnnotationNode> annotations) {
        if (annotations != null) {
            boolean removed = false;
            for (AnnotationNode annotation : annotations) {
                if (ENVIRONMENT_INTERFACE_DESCRIPTOR.equals(annotation.desc) && stripInterface(interfaces, new AnnotationHandle(annotation)) 
                    || ENVIRONMENT_INTERFACES_DESCRIPTOR.equals(annotation.desc) && stripInterfaces(interfaces, new AnnotationHandle(annotation))
                ) {
                    removed = true;
                }
            }
            return removed;
        }
        return false;
    }

    private boolean stripInterfaces(List<String> interfaces, AnnotationHandle handle) {
        boolean removed = false;
        List<AnnotationNode> annotations = handle.<List<AnnotationNode>>getValue("value").map(AnnotationValueHandle::get).orElse(List.of());
        for (AnnotationNode annotation : annotations) {
            removed |= stripInterface(interfaces, new AnnotationHandle(annotation));
        }
        return removed;
    }

    private boolean stripInterface(List<String> interfaces, AnnotationHandle handle) {
        boolean strip = handle.<String[]>getValue("value").map(h -> readEnvType(h.get()) != this.envType).orElse(false);
        if (strip) {
            Type itf = handle.<Type>getValue("itf").orElseThrow().get();
            interfaces.remove(itf.getInternalName());
            return true;
        }
        return false;
    }

    // We strip annotations ahead of time to avoid class resolution issues leading to CNFEs
    private boolean remove(@Nullable List<AnnotationNode> annotations) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (ENVIRONMENT_ANNOTATION.equals(annotation.desc) && remove(annotation)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean remove(AnnotationNode node) {
        if (node.values.size() != 2) {
            throw new IllegalArgumentException("Unexpected " + node.values.size() + " values for annotation " + node.desc);
        }
        String[] args = (String[]) node.values.get(1);
        EnvType type = readEnvType(args);
        return this.envType != type;
    }

    private static EnvType readEnvType(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Unexpected size of annotation value array" + args.length);
        }
        String side = args[1];
        return EnvType.valueOf(side);
    }
}
