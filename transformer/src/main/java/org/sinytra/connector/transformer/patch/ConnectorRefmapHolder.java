package org.sinytra.connector.transformer.patch;

import org.sinytra.adapter.env.ctx.RefmapHolder;

import java.util.*;

public class ConnectorRefmapHolder implements RefmapHolder {
    private final SimpleRefmap merged;
    private final Map<String, SimpleRefmap> refmapFiles;
    private final Set<String> dirtyRefmaps = new HashSet<>();

    public ConnectorRefmapHolder(SimpleRefmap merged, Map<String, SimpleRefmap> refmapFiles) {
        this.merged = merged;
        this.refmapFiles = refmapFiles;
    }

    public Set<String> getDirtyRefmaps() {
        return this.dirtyRefmaps;
    }

    @Override
    public String remap(String cls, String reference) {
        String cleanReference = reference.replace(" ", "");
        return Optional.ofNullable(this.merged.mappings.get(cls))
            .or(() -> Optional.ofNullable(this.merged.mappings.get(cls.replace('.', '/'))))
            .map(map -> map.get(cleanReference))
            .orElse(reference);
    }

    @Override
    public void copyEntries(String from, String to) {
        copyMapEntries(this.merged, from, to);
        this.refmapFiles.forEach((name, refmap) -> {
            if (copyMapEntries(refmap, from, to)) {
                this.dirtyRefmaps.add(name);
            }
        });
    }

    private boolean copyMapEntries(SimpleRefmap refmap, String from, String to) {
        boolean dirty = false;
        Map<String, String> mappingsRefs = refmap.mappings.get(from);
        if (mappingsRefs != null) {
            refmap.mappings.put(to, mappingsRefs);
            dirty = true;
        }
        for (Map.Entry<String, Map<String, Map<String, String>>> entry : refmap.data.entrySet()) {
            Map<String, Map<String, String>> map = entry.getValue();
            Map<String, String> dataRefs = map.get(from);
            if (dataRefs != null) {
                map.put(to, dataRefs);
                dirty = true;
            }
        }
        return dirty;
    }

}
