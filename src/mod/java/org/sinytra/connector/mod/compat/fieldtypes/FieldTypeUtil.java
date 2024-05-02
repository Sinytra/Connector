package org.sinytra.connector.mod.compat.fieldtypes;

import net.minecraft.core.IdMapper;

import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Prevent crashes when java tries to resolve classes that consume {@link RedirectingIdMapper}.
 * This class overrides a method that is definalized using an AT. In case the mod loading state is invalidated
 * and ATs don't load, trying to resolve {@link RedirectingIdMapper} directly would lead to a hard crash.
 */
public final class FieldTypeUtil {
    public static <K, V> IdMapper<V> createRedirectingMapperSafely(IntFunction<K> keyFunction, Function<K, Integer> reverseKeyFunction, Map<K, V> map) {
        return new RedirectingIdMapper<>(keyFunction, reverseKeyFunction, map);
    }
}
