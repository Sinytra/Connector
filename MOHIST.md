# Mohist

[Mohist](https://github.com/MohistMC/Mohist) implements the Bukkit API on top of Forge, and forks
ModLauncher and securejarhandler in the process. This branch contains the changes needed to load
Fabric mods through Connector on it, tested with Middle-earth 1.4.2 (2033 classes) on Mohist
1.20.1-ecf8d5ad / Forge 47.4.13.

## Symptoms

Without these changes the server dies during `Bootstrap`, naming a vanilla class the mod never
touches:

    java.lang.NoClassDefFoundError: net/minecraft/world/item/DyeableArmorItem
        at net.minecraft.world.level.block.ComposterBlock.m_51988_(ComposterBlock.java:70)
        at net.minecraft.server.Bootstrap.m_135870_(Bootstrap.java:47)

The class exists in the runtime jar, and `ComposterBlock` does not reference it. What happens is
that a mixin failed to apply, Mixin aborted the transformation of its target, and
`ModuleClassLoader.loadClass` turned the resulting `MixinTransformerError` into a
`ClassNotFoundException`. `ComposterBlock.bootStrap()` resolves `Items` first, so the failure is
reported at that frame instead of at its cause.

Look for the earlier `Mixin apply failed` line before chasing the class name.

## Mixin 0.8.x

Fabric ships Mixin 0.12, Forge ships 0.8.5. Three things that work on Fabric do not exist there:

- `Locals.getLocalsAt(ClassNode, MethodNode, AbstractInsnNode, int)`, used by `DynamicLVTPatch`,
  was added in 0.12; on 0.8.x the four argument overload takes `Locals$Settings`. The patch is now
  applied only when the running Mixin exposes the overload.
- `MixinPreProcessorInterface` rejects private and protected methods in interface mixins
  (`Interface mixin contains a non-public method`). `InterfaceMixinAccessWidener` promotes them to
  public, turning them into default methods with the same body.
- `MixinApplicatorInterface` rejects every injector declared by an interface mixin, and a class
  mixin may not target an interface, so the handler cannot be kept. `InterfaceMixinInjectorStripper`
  removes it and logs a warning.

`JarTransformer.cleanupEnvironment` read `ClassInfo.nonExpandedCache`, which only exists in 0.12.
The lookup threw, which skipped the rest of the cleanup, including restoring `java/lang/Object` in
the class cache. The field is now optional.

## Mohist specifics

Mohist rewrites vanilla classes, so injection points that exist on Forge often do not exist there.
Mixin treats a missing injection point as fatal for the whole target class. On Mohist,
`MixinPatchTransformer` writes `injectors.defaultRequire = 0` into the mixin configs of the
transformed mod, which downgrades those failures to warnings.

`IForgeItemMixin` targets an interface, which hits the second Mixin limitation above, so it is
skipped on Mohist.

Mohist's forked securejarhandler does not always accept the authlib module move.
`ConnectorLoaderService` now continues without it rather than failing the launch.

## Limitations

- Injectors Mixin cannot apply are dropped. The mod loads and the hook stays inert; the log lists
  every affected mixin and injection point.
- `@At("STORE")` with `@Inject` is rejected by 0.8.5. Only `@ModifyVariable` may use it.
- Mixins that Mohist itself applies are not part of Connector's transformation, so a Fabric mod that
  expects one of Mohist's changes to a vanilla method will not see it.

## Running

Put Connector, Fabric API and the mod in `mods/`, then:

    java -Xms2G -Xmx4G -jar mohist-1.20.1-47.4.13.jar nogui

2G is not enough for Middle-earth: world generation runs out of memory while preparing the nether.

Delete `mods/.connector` after replacing the Connector jar, otherwise the cached transformation of
the mod is reused.
