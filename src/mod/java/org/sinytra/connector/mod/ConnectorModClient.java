package org.sinytra.connector.mod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter;
import org.sinytra.connector.api.Constants;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

@Mod(value = Constants.CONNECTOR_MODID, dist = Dist.CLIENT)
public class ConnectorModClient {
    public static final VarHandle GET_CALCULATOR;

    static {
        try {
            Class<?> cls = Class.forName("net.neoforged.neoforge.client.model.ao.FullFaceCalculator");
            GET_CALCULATOR = MethodHandles.privateLookupIn(EnhancedBlockModelLighter.class, MethodHandles.lookup())
                .findVarHandle(EnhancedBlockModelLighter.class, "calculator", cls);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
