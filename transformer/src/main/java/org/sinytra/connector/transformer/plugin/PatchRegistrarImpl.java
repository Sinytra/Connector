package org.sinytra.connector.transformer.plugin;

import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.connector.transformer.api.PatchRegistrar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PatchRegistrarImpl implements PatchRegistrar {
    private final List<Registration> groups = new ArrayList<>();

    private record Registration(int priority, List<MethodPatch> patches) {
    }

    @Override
    public void add(int priority, MethodPatch patch) {
        add(priority, List.of(patch));
    }

    @Override
    public void add(int priority, List<MethodPatch> patches) {
        this.groups.add(new Registration(priority, patches));
    }

    public List<MethodPatch> getSortedPatches() {
        return this.groups.stream()
            .sorted(Comparator.comparingInt(Registration::priority))
            .flatMap(r -> r.patches().stream())
            .toList();
    }
}
