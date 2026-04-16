package me.martinkr;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EmptyServerStopperConfigManager {
    public static final String MOD_ID = EmptyServerStopper.MOD_ID;
    public static final Logger LOGGER = EmptyServerStopper.LOGGER;
    public static final String MOD_NAME = EmptyServerStopper.MOD_NAME;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path configDir = FabricLoader.getInstance().getConfigDir();
    private static final Path modConfigFolder = Paths.get(configDir.toString(), MOD_ID);
    private static final Path modConfigFile = Paths.get(modConfigFolder.toString(), "config.json");
    private static EmptyServerStopperConfigObject currentConfig;

    public static void init() {
        try {
            if (!Files.exists(modConfigFolder)) {
                LOGGER.info("[{}] config/{} folder not found.", MOD_NAME, MOD_ID);
                Files.createDirectories(modConfigFolder);
                LOGGER.info("[{}] config/{} folder created.", MOD_NAME, MOD_ID);
            }
            if (!Files.exists(modConfigFile)) {
                LOGGER.info("[{}] config.json not found.", MOD_NAME);
                Files.createFile(modConfigFile);
                currentConfig = new EmptyServerStopperConfigObject();
                saveConfig();
                LOGGER.info("[{}] config.json created.", MOD_NAME);
            } else {
                loadConfig(true);
            }
        } catch (IOException e) {
            LOGGER.error("[{}] error during initialization process. {}", MOD_NAME, e.getMessage());
        }
    }

    public static void loadConfig(boolean init) {
        try (FileReader reader = new FileReader(modConfigFile.toFile())) {
            EmptyServerStopperConfigObject loaded = GSON.fromJson(reader, EmptyServerStopperConfigObject.class);
            if (loaded != null) {
                currentConfig = loaded;
                if (init) LOGGER.info("[{}] Config loaded successfully.", MOD_NAME);
            } else {
                if (init) LOGGER.warn("[{}] Config file was empty, using defaults.", MOD_NAME);
                currentConfig = new EmptyServerStopperConfigObject();
            }
        } catch (IOException e) {
            LOGGER.error("[{}] Error loading config: {}", MOD_NAME, e.getMessage());
            currentConfig = new EmptyServerStopperConfigObject();
        }        saveConfig();
    }

    public static void saveConfig() {
        try (FileWriter writer = new FileWriter(modConfigFile.toFile())) {
            GSON.toJson(currentConfig, writer);
        } catch (IOException e) {
            LOGGER.error("[{}] error when trying to save the config file. {}", MOD_NAME, e.getMessage());
        }
    }

    public static EmptyServerStopperConfigObject getConfig() {
        return currentConfig;
    }
}