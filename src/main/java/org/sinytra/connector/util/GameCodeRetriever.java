package org.sinytra.connector.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.sinytra.connector.transformer.TransformerEnvironment;
import org.sinytra.connector.transformer.transform.TransformerUtil;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class GameCodeRetriever {
    private static final String LAUNCHER_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static Path getCleanMinecraftJar(String version, TransformerEnvironment environment) {
        try {
            String side = getSide(FMLEnvironment.getDist());
            Path output = environment.createCachedJarPath("minecraft/minecraft-%s-%s".formatted(version, side));

            TransformerUtil.cache(null, output, () -> fetchVanillaMinecraftJar(version, side, output), environment.getJarCacheVersion());

            return output;
        } catch (Exception e) {
            throw new RuntimeException("Error fetching clean Minecraft jar", e);
        }
    }

    private static void fetchVanillaMinecraftJar(String version, String side, Path destination) throws Exception {
        LOGGER.debug("Fetching Mojang Launcher manifest");
        JsonObject launcherManifest = fetchRemoteJson(LAUNCHER_MANIFEST_URL);

        LOGGER.debug("Fetching version manifest for {}", version);
        String versionManifestUrl = Objects.requireNonNull(findVersionManifest(launcherManifest, version), "Missing manifest entry for version " + version);
        JsonObject versionManifest = fetchRemoteJson(versionManifestUrl);

        JsonObject downloads = Objects.requireNonNull(versionManifest.getAsJsonObject("downloads"), "Missing version manifest 'downloads' value");
        JsonObject sideDownload = Objects.requireNonNull(downloads.getAsJsonObject(side), "Missing sided artifact download");

        String sideDownloadUrl = Objects.requireNonNull(sideDownload.getAsJsonPrimitive("url"), "Missing sided artifact download url").getAsString();

        LOGGER.debug("Fetching clean game code archive");
        Files.createDirectories(destination.getParent());
        fetchRemoteFile(sideDownloadUrl, destination);
    }

    @Nullable
    private static String findVersionManifest(JsonObject body, String version) {
        JsonArray versions = Objects.requireNonNull(body.getAsJsonArray("versions"), "Missing manifest 'versions' value");

        for (JsonElement entry : versions) {
            JsonObject obj = entry.getAsJsonObject();
            String id = Objects.requireNonNull(obj.getAsJsonPrimitive("id"), "Missing version 'id' value").getAsString();

            if (version.equals(id)) {
                return Objects.requireNonNull(obj.getAsJsonPrimitive("url"), "Missing version 'url' value").getAsString();
            }
        }

        return null;
    }

    private static JsonObject fetchRemoteJson(String url) throws IOException, InterruptedException {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStreamReader reader = new InputStreamReader(response.body())) {
                JsonElement json = JsonParser.parseReader(reader);
                return json.getAsJsonObject();
            }
        }
    }

    private static void fetchRemoteFile(String url, Path destination) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        }
    }

    private static String getSide(Dist dist) {
        return switch (dist) {
            case CLIENT -> "client";
            case DEDICATED_SERVER -> "server";
        };
    }
}
