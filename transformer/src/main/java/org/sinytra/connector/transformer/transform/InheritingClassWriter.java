package org.sinytra.connector.transformer.transform;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ctx.PatchEnvironment;

import java.lang.reflect.Modifier;
import java.util.*;

public class InheritingClassWriter extends ClassWriter {
    private final PatchEnvironment environment;

    public InheritingClassWriter(int flags, PatchEnvironment environment) {
        super(flags);
        this.environment = environment;
    }

    // This method is a relative reimplementation of the super method that relies on reading transformable classes without loading them through the class provider
    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        var t1Info = getClassInfo(type1);
        var t2Info = getClassInfo(type2);

        var t1Inh = getParents(t1Info);
        var t2Inh = getParents(t2Info);

        // First check if type2 inherits from type1
        if (t2Inh.contains(type1)) {
            return type1;
        }
        // Then check if type1 inherits from type2
        else if (t1Inh.contains(type2)) {
            return type2;
        }

        // If either is an interface and one isn't a superinterface of the other, we have to use Object
        if (t1Info.isInterface() || t2Info.isInterface()) {
            return "java/lang/Object";
        } else {
            // So find the most direct parent of type1 that is a parent of type2 too.
            // This vaguely resembles the last set of logic from the super method.
            for (var t1Parent : t1Inh) {
                if (t2Inh.contains(t1Parent)) {
                    return t1Parent;
                }
            }

            // If all fails, fallback to ASM's built-in logic
            return super.getCommonSuperClass(type1, type2);
        }
    }

    private record ClassInfo(boolean isInterface, @Nullable String superClass, List<String> interfaces) {
    }

    private ClassInfo getClassInfo(String className) {
        var asmNode = this.environment.cleanClassLookup().getClass(className);
        if (asmNode.isPresent()) {
            var node = asmNode.get();
            return new ClassInfo(Modifier.isInterface(node.access), node.superName, Objects.requireNonNullElse(node.interfaces, List.of()));
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            var clazz = Class.forName(className.replace('/', '.'), false, classLoader);
            return new ClassInfo(clazz.isInterface(), clazz.getSuperclass() == null ? null : Type.getInternalName(clazz.getSuperclass()), Arrays.stream(clazz.getInterfaces())
                .map(Type::getInternalName).toList());
        } catch (ClassNotFoundException e) {
            throw new TypeNotPresentException(className, e);
        }
    }

    private Set<String> getParents(ClassInfo info) {
        var parents = new LinkedHashSet<String>();
        if (info.superClass() != null) {
            parents.add(info.superClass());
            parents.addAll(getParents(getClassInfo(info.superClass())));
        }
        for (var itf : info.interfaces()) {
            if (parents.add(itf)) {
                parents.addAll(getParents(getClassInfo(itf)));
            }
        }
        return parents;
    }
}
