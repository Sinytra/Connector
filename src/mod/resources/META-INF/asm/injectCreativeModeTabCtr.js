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
                method.visitVarInsn(Opcodes.ALOAD, 1);
                method.visitVarInsn(Opcodes.ILOAD, 2);
                method.visitVarInsn(Opcodes.ALOAD, 3);
                method.visitVarInsn(Opcodes.ALOAD, 4);
                method.visitVarInsn(Opcodes.ALOAD, 5);
                method.visitVarInsn(Opcodes.ALOAD, 6);
                method.visitInsn(Opcodes.ACONST_NULL); // backgroundLocation
                method.visitInsn(Opcodes.ICONST_0); // hasSearchBar
                method.visitLdcInsn(89); // searchBarWidth
                method.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/item/CreativeModeTab$Builder", "CREATIVE_INVENTORY_TABS_IMAGE", "Lnet/minecraft/resources/ResourceLocation;"); // tabsImage
                method.visitLdcInsn(4210752); // labelColor
                method.visitLdcInsn(-2130706433); // slotColor
                method.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/minecraft/world/item/CreativeModeTab", "<init>", "(Lnet/minecraft/world/item/CreativeModeTab$Row;ILnet/minecraft/world/item/CreativeModeTab$Type;Lnet/minecraft/network/chat/Component;Ljava/util/function/Supplier;Lnet/minecraft/world/item/CreativeModeTab$DisplayItemsGenerator;Lnet/minecraft/resources/ResourceLocation;ZILnet/minecraft/resources/ResourceLocation;II)V", false);
                method.visitInsn(Opcodes.RETURN);
                method.visitEnd();

                ASMAPI.log('DEBUG', 'Injected vanilla CreativeModeTab constructor');
                return node;
            }
        }
    }
}