package dev.su5ed.sinytra.connector.service;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.INameMappingService;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.extensibility.IRemapper;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiFunction;

public class MixinModlauncherRemapper implements IRemapper {
    private final BiFunction<INameMappingService.Domain, String, String> mapping;

    public MixinModlauncherRemapper() {
        this.mapping = Optional.ofNullable(Launcher.INSTANCE)
            .map(Launcher::environment)
            .flatMap(env -> env.findNameMapping("srg"))
            .orElseThrow();
    }

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        String mapped = this.mapping.apply(INameMappingService.Domain.METHOD, name);
        // Record method names issue workaround
        // See: https://github.com/MinecraftForge/ForgeGradle/issues/922
        if (mapped != null && mapped.equals(name) && name.startsWith("f_")) {
            return this.mapping.apply(INameMappingService.Domain.FIELD, name);
        }
        return mapped;
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        return this.mapping.apply(INameMappingService.Domain.FIELD, name);
    }

    @Override
    public String map(String typeName) {
        return this.mapping.apply(INameMappingService.Domain.CLASS, typeName);
    }

    @Override
    public String unmap(String typeName) {
        return typeName;
    }

    @Override
    public String mapDesc(String desc) {
        if (desc.startsWith("(")) {
            StringBuilder stringBuilder = new StringBuilder("(");
            Arrays.stream(Type.getArgumentTypes(desc))
                .map(this::mapType)
                .forEach(stringBuilder::append);
            Type returnType = Type.getReturnType(desc);
            stringBuilder.append(")").append(mapType(returnType));
            return stringBuilder.toString();
        }
        if (desc.startsWith("L")) {
            String cls = desc.substring(1, desc.length() - 1);
            return "L" + map(cls) + ";";
        }
        return desc;
    }

    @Override
    public String unmapDesc(String desc) {
        return desc;
    }

    private Type mapType(Type type) {
        return type.getSort() == Type.OBJECT ? Type.getObjectType(map(type.getClassName()).replace('.', '/')) : type;
    }
}
