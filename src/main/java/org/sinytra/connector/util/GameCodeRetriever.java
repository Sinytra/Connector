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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;

public class GameCodeRetriever {
    private static final String LAUNCHER_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
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
        try (HttpClient client = createIsolatedHttpClient()) {
            LOGGER.debug("Fetching Mojang Launcher manifest");
            JsonObject launcherManifest = fetchRemoteJson(client, LAUNCHER_MANIFEST_URL);
            LOGGER.debug("Fetching version manifest for {}", version);
            String versionManifestUrl = Objects.requireNonNull(findVersionManifest(launcherManifest, version), "Missing manifest entry for version " + version);
            JsonObject versionManifest = fetchRemoteJson(client, versionManifestUrl);
            JsonObject downloads = Objects.requireNonNull(versionManifest.getAsJsonObject("downloads"), "Missing version manifest 'downloads' value");
            JsonObject sideDownload = Objects.requireNonNull(downloads.getAsJsonObject(side), "Missing sided artifact download");
            String sideDownloadUrl = Objects.requireNonNull(sideDownload.getAsJsonPrimitive("url"), "Missing sided artifact download url").getAsString();
            LOGGER.debug("Fetching clean game code archive");
            Files.createDirectories(destination.getParent());
            fetchRemoteFile(client, sideDownloadUrl, destination);
        }
    }

    private static HttpClient createIsolatedHttpClient() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        try {
            // Isolate TCCL to prevent ServiceLoader conflicts with owo-lib / ModLauncher during early loading
            currentThread.setContextClassLoader(ClassLoader.getSystemClassLoader());
            return HttpClient.newBuilder()
                .executor(Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "Connector-HttpClient");
                    t.setDaemon(true);
                    t.setContextClassLoader(ClassLoader.getSystemClassLoader());
                    return t;
                }))
                .build();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
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

    private static JsonObject fetchRemoteJson(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected HTTP status " + response.statusCode() + " when fetching " + url);
        }
        try (InputStreamReader reader = new InputStreamReader(response.body(), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.getAsJsonObject();
        }
    }

    private static void fetchRemoteFile(HttpClient client, String url, Path destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected HTTP status " + response.statusCode() + " when downloading " + url);
        }
    }

    private static String getSide(Dist dist) {
        return switch (dist) {
            case CLIENT -> "client";
            case DEDICATED_SERVER -> "server";
        };
    }
}
