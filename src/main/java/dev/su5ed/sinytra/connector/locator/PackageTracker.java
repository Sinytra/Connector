/*
 * BootstrapLauncher - for launching Java programs with added modular fun!
 *
 *     Copyright (C) 2021 - cpw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package dev.su5ed.sinytra.connector.locator;

import java.util.Set;
import java.util.function.BiPredicate;

// Source
// https://github.com/McModLauncher/bootstraplauncher/blob/09c1f9980369e01724d6e8842c23dcf8a53fb46d/src/main/java/cpw/mods/bootstraplauncher/BootstrapLauncher.java#L165-L179
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
