package dev.su5ed.connector;

import java.util.Set;
import java.util.function.BiPredicate;

// From BSL (TODO Credit)
public record PackageTracker(Set<String> packages) implements BiPredicate<String, String> {
    @Override
    public boolean test(final String path, final String basePath) {
        // This method returns true if the given path is allowed within the JAR (filters out 'bad' paths)

        if (this.packages.isEmpty() || // This is the first jar, nothing is claimed yet, so allow everything
            path.startsWith("META-INF/")) // Every module can have their own META-INF
            return true;

        int idx = path.lastIndexOf('/');
        return idx < 0 || // Resources at the root are allowed to co-exist
            idx == path.length() - 1 || // All directories can have a potential to exist without conflict, we only care about real files.
            !this.packages.contains(path.substring(0, idx).replace('/', '.')); // If the package hasn't been used by a previous JAR
    }
}
