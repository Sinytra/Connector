package dev.su5ed.connector.service;

import com.mojang.logging.LogUtils;
import dev.su5ed.connector.ConnectorUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.launch.platform.MixinPlatformAgentAbstract;
import org.spongepowered.asm.launch.platform.MixinPlatformManager;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static cpw.mods.modlauncher.api.LamdbaExceptionUtils.uncheck;

@SuppressWarnings("unused") // Referenced in dev.su5ed.connector.service.ConnectorMixinService getPlatformAgents()
public class ConnectorMixinAgent extends MixinPlatformAgentAbstract {
    private static final MethodHandle ADD_CONFIG = uncheck(() -> MethodHandles.privateLookupIn(MixinPlatformManager.class, MethodHandles.lookup()).findVirtual(MixinPlatformManager.class, "addConfig", MethodType.methodType(void.class, String.class)));
    private static final Logger LOGGER = LogUtils.getLogger();
    
    @Override
    public void prepare() {
        String mixinConfigs = this.handle.getAttribute(ConnectorUtil.MIXIN_CONFIGS_ATTRIBUTE);
        int var5;
        if (mixinConfigs != null) {
            String[] var3 = mixinConfigs.split(",");
            int var4 = var3.length;

            for (var5 = 0; var5 < var4; ++var5) {
                String config = var3[var5];
                try {
                    ADD_CONFIG.invoke(this.manager, config.trim());
                } catch (Throwable t) {
                    LOGGER.error("Error loading mixin config", t);
                    throw new RuntimeException(t);
                }
            }
        }
    }
}
