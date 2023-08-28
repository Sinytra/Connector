var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');

function initializeCoreMod() {
    return {
        'injectCreativeModeTabCtr': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.world.item.CreativeModeTab',
            },
            'transformer': function (node) {
                var method = node.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;)V", null, null);
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
                // backgroundLocation
                method.visitTypeInsn(Opcodes.NEW, "net/minecraft/resources/ResourceLocation");
                method.visitInsn(Opcodes.DUP);
                method.visitLdcInsn("textures/gui/container/creative_inventory/tab_items.png");
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/resources/ResourceLocation", "<init>", "(Ljava/lang/String;)V");
                // hasSearchBar
                method.visitInsn(Opcodes.ICONST_0);
                // searchBarWidth
                method.visitLdcInsn(89);
                method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/CreativeModeTab$Builder", "CREATIVE_INVENTORY_TABS_IMAGE", "Lnet/minecraft/resources/ResourceLocation;"); // tabsImage
                // labelColor
                method.visitLdcInsn(4210752);
                // slotColor
                method.visitLdcInsn(-2130706433);
                // tabsBefore
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
                method.visitInsn(Opcodes.DUP)
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V")
                // tabsAfter
                method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
                method.visitInsn(Opcodes.DUP)
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V")
                // invoke ctr
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/item/CreativeModeTab", "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;Lnet/minecraft/resources/ResourceLocation;ZILnet/minecraft/resources/ResourceLocation;IILjava/util/List;Ljava/util/List;)V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitEnd();

                ASMAPI.log('DEBUG', 'Injected vanilla CreativeModeTab constructor');
                return node;
            }
        }
    }
}