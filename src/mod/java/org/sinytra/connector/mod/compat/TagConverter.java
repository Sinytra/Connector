package org.sinytra.connector.mod.compat;

import com.google.common.base.Stopwatch;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TagConverter {
    private static final String FABRIC_NAMESPACE = "c";
    private static final Pattern RAW_ORES_PATTERN = Pattern.compile("^raw_(.+?)_ores$");
    private static final String TAG_ENTRY_SPLITTER = "_(?!.*_)";
    private static final Collection<String> COMMON_TYPES = Set.of("small_dusts");
    private static final Collection<String> COMMON_GROUP_PREFIXES = Set.of("tools");
    private static final Collection<String> COMMON_GROUP_PREFIXES_NO_ENTRYPATH = Set.of("gems");
    private static final Map<String, String> ALIASES = Map.of(
        "blocks", "storage_blocks",
        "raw_ores", "raw_materials"
    );
    private static final Map<String, Pair<String, @Nullable String>> FORGE_TAG_CACHE = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void postProcessTags(Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {
        int counter = 0;
        Stopwatch stopwatch = Stopwatch.createStarted();
        Collection<ResourceLocation> existing = tags.keySet();
        Map<ResourceLocation, List<TagLoader.EntryWithSource>> copy = new HashMap<>(tags);
        for (Map.Entry<ResourceLocation, List<TagLoader.EntryWithSource>> entry : copy.entrySet()) {
            ResourceLocation name = entry.getKey();
            if (isFabricTag(name)) {
                ResourceLocation newName = getNormalizedTagName(name.getPath(), existing);
                LOGGER.trace("Converting tag {} to tag {}", name, newName);
                List<TagLoader.EntryWithSource> newEntries = tags.computeIfAbsent(newName, loc -> new ArrayList<>());
                for (TagLoader.EntryWithSource tagEntry : entry.getValue()) {
                    ResourceLocation entryName = tagEntry.entry().getId();
                    if (isFabricTag(entryName)) {
                        ResourceLocation newEntryName = getNormalizedTagName(entryName.getPath(), existing);
                        if (!newName.equals(newEntryName)) {
                            TagEntry newEntry = new TagEntry(newEntryName, tagEntry.entry().isTag(), tagEntry.entry().isRequired());
                            newEntries.add(new TagLoader.EntryWithSource(newEntry, tagEntry.source()));
                        }
                    }
                    else if (!newName.equals(entryName)) {
                        newEntries.add(tagEntry);
                    }
                }
                List<TagLoader.EntryWithSource> entries = tags.get(name);
                // Remove existing entries
                entries.clear();
                // Add the forge tag we just created
                entries.add(new TagLoader.EntryWithSource(TagEntry.tag(newName), "connector"));
                counter++;
            }
        }
        stopwatch.stop();
        if (counter > 0) {
            LOGGER.debug("Converted {} tags in {} ms", counter, stopwatch.elapsed(TimeUnit.MILLISECONDS));
        }
    }

    public static ResourceLocation getNormalizedTagName(String path, Collection<ResourceLocation> existing) {
        Pair<String, @Nullable String> newPath = getForgeTagName(path);
        String group = newPath.getFirst();
        String entryPath = newPath.getSecond();
        // Check for common groups (c:bows -> forge:tools/bows)
        if (entryPath == null) {
            for (String prefix : COMMON_GROUP_PREFIXES) {
                ResourceLocation tag = new ResourceLocation("forge", prefix + "/" + group);
                if (existing.contains(tag)) {
                    LOGGER.debug("Found existing prefixed forge tag {}", tag);
                    return tag;
                }
            }
            for (String prefix : COMMON_GROUP_PREFIXES_NO_ENTRYPATH) {
                // Handle plural to singular tag names (c:diamonds -> forge:gems/diamond)
                // This will handle existing tags, but won't be able to detect nonexisted tag names like c:rubies -> forge:gems/ruby
                // Well, it's better than nothing I guess
                if (group.endsWith("s")) {
                    ResourceLocation singularTag = new ResourceLocation("forge", prefix + "/" + group.substring(0, group.length() - 1));
                    if (existing.contains(singularTag)) {
                        LOGGER.debug("Found existing singular prefixed forge tag {}", singularTag);
                        return singularTag;
                    }
                }
            }
        }
        String tagPath = group + (entryPath != null ? "/" + entryPath : "");
        // Prefer vanilla tags if they exist (c:axes -> vanilla:axes)
        ResourceLocation vanillaTag = new ResourceLocation(tagPath);
        if (existing.contains(vanillaTag)) {
            LOGGER.debug("Found existing vanilla tag {}", vanillaTag);
            return vanillaTag;
        }
        // Fallback
        ResourceLocation forgeTag = new ResourceLocation("forge", tagPath);
        if (!existing.contains(forgeTag)) {
            LOGGER.debug("Creating new forge tag {}", forgeTag);
        }
        return forgeTag;
    }

    public static Pair<String, @Nullable String> getForgeTagName(String path) {
        return FORGE_TAG_CACHE.computeIfAbsent(path, TagConverter::computeForgeTagName);
    }

    private static Pair<String, @Nullable String> computeForgeTagName(String path) {
        // Group aliases
        for (Map.Entry<String, String> entry : ALIASES.entrySet()) {
            if (entry.getKey().equals(path)) {
                return Pair.of(entry.getValue(), null);
            }
        }
        // Special cases
        Matcher matcher = RAW_ORES_PATTERN.matcher(path);
        if (matcher.matches()) {
            return Pair.of("raw_materials", matcher.group(1));
        }
        // Generic conversion
        else if (path.contains("_")) {
            // Find common types that consist of multiple words
            for (String common : COMMON_TYPES) {
                if (path.endsWith(common)) {
                    return Pair.of(common, path.replace("_" + common, ""));
                }
            }
            // Split on last occurence of '_'
            String[] parts = path.split(TAG_ENTRY_SPLITTER);
            // Find alias for group
            String group = ALIASES.getOrDefault(parts[1], parts[1]);
            // Convert to forge naming (raw
            return Pair.of(group, parts[0]);
        }
        // Group tag (c:ingots -> forge:ingots)
        return Pair.of(ALIASES.getOrDefault(path, path), null);
    }

    private static boolean isFabricTag(ResourceLocation location) {
        return location.getNamespace().equals(FABRIC_NAMESPACE);
    }

    private TagConverter() {}
}
