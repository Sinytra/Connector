var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
var Opcodes = Java.type('org.objectweb.asm.Opcodes');
var FieldNode = Java.type('org.objectweb.asm.tree.FieldNode');

function initializeCoreMod() {
    return {
        'fixKeyMapping': {
            'target': {
                'type': 'CLASS',
                'name': 'net.minecraft.client.KeyMapping'
            },
            'transformer': function (node) {
                var fields = node.fields;
                var i = 0;
                for (; i < fields.length; i++) {
                    if (fields[i].name === ASMAPI.mapField('f_90812_')) {
                        break
                    }
                }

                // Add the field before the f_90812_ (KeyMapping#CATEGORY_SORT_ORDER) which is the 3rd map
                // See https://github.com/Sinytra/Connector/issues/723
                node.fields.add(i, new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, ASMAPI.mapField('f_90810_'), 'Ljava/util/Map;', null, null));

                ASMAPI.log('DEBUG', 'Added field for KeyMapping#MAP (f_90810_) at index ' + (i + 1));
                return node;
            }
        }
    }
}
