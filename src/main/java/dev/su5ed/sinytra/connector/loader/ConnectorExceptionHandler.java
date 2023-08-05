package dev.su5ed.sinytra.connector.loader;

import org.jetbrains.annotations.Nullable;

public class ConnectorExceptionHandler {
    // If we encounter an exception during setup/load, we store it here and throw it later during FML mod loading,
    // so that it is propagated to the forge error screen.
    private static Throwable loadingException;

    /**
     * @return a suppressed exception if one was encountered during setup/load, otherwise {@code null}
     */
    @Nullable
    public static Throwable getLoadingException() {
        return loadingException;
    }

    public static void addSuppressed(Throwable t) {
        if (loadingException == null) {
            loadingException = t;
        }
        else {
            loadingException.addSuppressed(t);
        }
    }
}
