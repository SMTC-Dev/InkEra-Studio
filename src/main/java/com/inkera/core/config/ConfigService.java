package com.inkera.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigService {

    private static ConfigService instance;

    private final Gson gson =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private final Path configDir;
    private final Path configFile;

    private ConfigData config;

    private ConfigService() {

        final String userHome =
                System.getProperty("user.home");

        String configHome =
                System.getenv("XDG_CONFIG_HOME");

        if (configHome == null ||
            configHome.isBlank()) {

            configHome =
                    Paths.get(
                            userHome,
                            ".config"
                    ).toString();
        }

        configDir =
                Paths.get(
                        configHome,
                        "inkera"
                );

        configFile =
                configDir.resolve("config.json");

        loadOrCreateConfig();
    }

    public static synchronized ConfigService getInstance() {
        if (instance == null) {
            instance = new ConfigService();
        }

        return instance;
    }

    // ---------------------------------------------------------
    // DATA MODEL
    // ---------------------------------------------------------

    public static class ConfigData {

        public int schemaVersion = 1;

        public PathsConfig paths =
                new PathsConfig();

        public SettingsConfig settings =
                new SettingsConfig();

        public LoggingConfig logging =
                new LoggingConfig();
    }

    public static class PathsConfig {

        @SerializedName("userData")
        public String userData = "";
    }

    public static class SettingsConfig {

        public String language = "tr";

        public boolean useCustomTitleBar = true;
    }

    public static class LoggingConfig {

        public boolean enabled = true;

        public String level = "DEBUG";
    }

    // ---------------------------------------------------------
    // LOAD / CREATE
    // ---------------------------------------------------------

    private void loadOrCreateConfig() {

        try {
            Files.createDirectories(configDir);

            if (Files.exists(configFile)) {

                try (Reader reader =
                             Files.newBufferedReader(
                                     configFile)) {

                    ConfigData loaded =
                            gson.fromJson(
                                    reader,
                                    ConfigData.class
                            );

                    if (loaded != null) {
                        config = loaded;
                    } else {
                        config = createDefaultConfig();
                        saveConfig();
                    }
                }

            } else {

                config = createDefaultConfig();

                saveConfig();
            }

        } catch (IOException e) {

            e.printStackTrace();

            config =
                    createDefaultConfig();
        }

        normalizeConfig();
    }

    private ConfigData createDefaultConfig() {

        ConfigData data =
                new ConfigData();

        Path legacyUserData =
                Paths.get(
                        System.getProperty("user.dir"),
                        "user-data"
                ).toAbsolutePath();

        if (Files.isDirectory(legacyUserData)) {

            // Mevcut geliştirme verisini koru.
            data.paths.userData =
                    legacyUserData.toString();

        } else {

            data.paths.userData =
                    getDefaultDataDirectory()
                            .toString();
        }

        return data;
    }

    private Path getDefaultDataDirectory() {

        final String userHome =
                System.getProperty("user.home");

        String dataHome =
                System.getenv("XDG_DATA_HOME");

        if (dataHome == null ||
            dataHome.isBlank()) {

            dataHome =
                    Paths.get(
                            userHome,
                            ".local",
                            "share"
                    ).toString();
        }

        return Paths.get(
                dataHome,
                "InkEra"
        ).toAbsolutePath();
    }

    private void normalizeConfig() {

        if (config == null) {
            config = createDefaultConfig();
        }

        if (config.paths == null) {
            config.paths =
                    new PathsConfig();
        }

        if (config.settings == null) {
            config.settings =
                    new SettingsConfig();
        }

        if (config.logging == null) {
            config.logging =
                    new LoggingConfig();
        }

        if (config.paths.userData == null ||
            config.paths.userData.isBlank()) {

            config.paths.userData =
                    getDefaultDataDirectory()
                            .toString();
        }

        if (config.settings.language == null ||
            config.settings.language.isBlank()) {

            config.settings.language = "tr";
        }

        if (config.logging.level == null ||
            config.logging.level.isBlank()) {

            config.logging.level = "DEBUG";
        }
    }

    // ---------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------

    public synchronized void saveConfig() {

        normalizeConfig();

        try {

            Files.createDirectories(configDir);

            try (Writer writer =
                         Files.newBufferedWriter(
                                 configFile)) {

                gson.toJson(
                        config,
                        writer
                );
            }

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    // Eski isimle çağrılabilecek yerler için
    public void saveProperties() {
        saveConfig();
    }

    // ---------------------------------------------------------
    // LANGUAGE
    // ---------------------------------------------------------

    public String getLanguage() {
        return config.settings.language;
    }

    public void setLanguage(String language) {

        if (language == null ||
            language.isBlank()) {

            return;
        }

        config.settings.language =
                language;

        saveConfig();
    }

    // ---------------------------------------------------------
    // TITLE BAR
    // ---------------------------------------------------------

    public boolean isCustomTitleBar() {

        return config.settings
                .useCustomTitleBar;
    }

    public void setCustomTitleBar(
            boolean use
    ) {

        config.settings
                .useCustomTitleBar =
                use;

        saveConfig();
    }

    // ---------------------------------------------------------
    // USER DATA & SUBDIRECTORIES
    // ---------------------------------------------------------

    public Path getUserDataDir() {

        Path path =
                Paths.get(
                        config.paths.userData
                ).toAbsolutePath().normalize();

        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return path;
    }

    public Path getProjectsDataDir() {
        Path projectsDir = getUserDataDir().resolve("projects");
        try {
            Files.createDirectories(projectsDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return projectsDir;
    }

    public Path getImagesDataDir() {
        Path imagesDir = getUserDataDir().resolve("images");
        try {
            Files.createDirectories(imagesDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imagesDir;
    }

    public void setUserDataDir(
            Path path
    ) {

        if (path == null) {
            return;
        }

        config.paths.userData =
                path.toAbsolutePath()
                        .normalize()
                        .toString();

        saveConfig();
    }

    // ---------------------------------------------------------
    // LOGGING
    // ---------------------------------------------------------

    public boolean isLoggingEnabled() {

        return config.logging.enabled;
    }

    public String getLogLevel() {

        return config.logging.level;
    }

    public void setLoggingEnabled(
            boolean enabled
    ) {

        config.logging.enabled =
                enabled;

        saveConfig();
    }

    public void setLogLevel(
            String level
    ) {

        if (level == null ||
            level.isBlank()) {

            return;
        }

        config.logging.level =
                level.toUpperCase();

        saveConfig();
    }

    // ---------------------------------------------------------
    // DEBUG
    // ---------------------------------------------------------

    public Path getConfigFile() {
        return configFile;
    }
}
