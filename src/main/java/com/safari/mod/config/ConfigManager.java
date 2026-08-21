package com.safari.mod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private ConfigManager() {
    }

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Path CONFIG_PATH =
            Path.of(
                    "config",
                    "safari.json"
            );

    private static Config config =
            new Config();

    public static void load() {

        try {

            if (!Files.exists(CONFIG_PATH)) {
                save();
                return;
            }

            String json =
                    Files.readString(
                            CONFIG_PATH
                    );

            Config loaded =
                    GSON.fromJson(
                            json,
                            Config.class
                    );

            if (loaded != null) {
                config = loaded;
            }

        } catch (Exception e) {

            System.err.println(
                    "[Safari] Failed to load config: "
                            + e.getMessage()
            );
        }
    }

    public static void save() {

        try {

            Files.createDirectories(
                    CONFIG_PATH.getParent()
            );

            Files.writeString(
                    CONFIG_PATH,
                    GSON.toJson(config)
            );

        } catch (IOException e) {

            System.err.println(
                    "[Safari] Failed to save config: "
                            + e.getMessage()
            );
        }
    }

    public static int getLocationX() {
        return config.locationX;
    }

    public static int getLocationY() {
        return config.locationY;
    }

    public static void setLocation(
            int x,
            int y) {

        config.locationX = x;
        config.locationY = y;

        save();
    }

    private static class Config {

        int locationX = 10;
        int locationY = 30;
    }
}