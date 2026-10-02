package com.urntt.airjump.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.urntt.airjump.AirJumpClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's settings, persisted as JSON. Every change is written to disk immediately.
 */
public final class AirJumpConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger(AirJumpClient.MOD_ID);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path path;
	private final Settings settings;

	private AirJumpConfig(final Path path, final Settings settings) {
		this.path = path;
		this.settings = settings;
	}

	/**
	 * Returns the location of the config file in the Fabric config directory.
	 */
	public static Path defaultPath() {
		return FabricLoader.getInstance().getConfigDir().resolve(AirJumpClient.MOD_ID + ".json");
	}

	/**
	 * Loads the configuration from {@code path}. A missing file is created with the defaults. A readable file is
	 * rewritten with any settings it lacks. An unreadable file falls back to the defaults and is left untouched
	 * until the next change is saved.
	 */
	public static AirJumpConfig load(final Path path) {
		if (Files.notExists(path)) {
			AirJumpConfig config = new AirJumpConfig(path, new Settings());
			config.save();
			return config;
		}

		Settings settings;
		try {
			settings = GSON.fromJson(Files.readString(path), Settings.class);
		} catch (IOException | JsonParseException e) {
			LOGGER.warn("Failed to read config file {}, using defaults", path, e);
			return new AirJumpConfig(path, new Settings());
		}

		if (settings == null) {
			settings = new Settings();
		}
		settings.normalize();
		AirJumpConfig config = new AirJumpConfig(path, settings);
		config.save();
		return config;
	}

	/** The current state of the feature, as toggled by the key binding. */
	public boolean isEnabled() {
		return this.settings.enabled;
	}

	public void setEnabled(final boolean enabled) {
		this.settings.enabled = enabled;
		this.save();
	}

	/** The state a reset restores in singleplayer worlds. */
	public boolean singleplayerDefault() {
		return this.settings.singleplayerDefault;
	}

	public void setSingleplayerDefault(final boolean singleplayerDefault) {
		this.settings.singleplayerDefault = singleplayerDefault;
		this.save();
	}

	/** The state a reset restores on multiplayer servers that the multiplayer mode allows. */
	public boolean multiplayerDefault() {
		return this.settings.multiplayerDefault;
	}

	public void setMultiplayerDefault(final boolean multiplayerDefault) {
		this.settings.multiplayerDefault = multiplayerDefault;
		this.save();
	}

	/** Whether every world starts from its default state. */
	public boolean resetOnWorldExit() {
		return this.settings.resetOnWorldExit;
	}

	public void setResetOnWorldExit(final boolean resetOnWorldExit) {
		this.settings.resetOnWorldExit = resetOnWorldExit;
		this.save();
	}

	/** Whether the first world after starting the game starts from its default state. */
	public boolean resetOnGameExit() {
		return this.settings.resetOnGameExit;
	}

	public void setResetOnGameExit(final boolean resetOnGameExit) {
		this.settings.resetOnGameExit = resetOnGameExit;
		this.save();
	}

	public MultiplayerMode multiplayerMode() {
		return this.settings.multiplayerMode;
	}

	public void setMultiplayerMode(final MultiplayerMode multiplayerMode) {
		this.settings.multiplayerMode = multiplayerMode;
		this.save();
	}

	/** The server list used by the whitelist and blacklist modes. */
	public List<String> servers() {
		return List.copyOf(this.settings.servers);
	}

	public void setServers(final List<String> servers) {
		this.settings.servers = new ArrayList<>(servers);
		this.settings.normalize();
		this.save();
	}

	private void save() {
		try {
			Files.createDirectories(this.path.getParent());
			Files.writeString(this.path, GSON.toJson(this.settings));
		} catch (IOException e) {
			LOGGER.error("Failed to write config file {}", this.path, e);
		}
	}

	/**
	 * The serialized form. Field initializers are the defaults, which also apply to keys missing from the file.
	 */
	private static final class Settings {
		private boolean enabled = true;
		private boolean singleplayerDefault = true;
		private boolean multiplayerDefault = true;
		private boolean resetOnWorldExit = false;
		private boolean resetOnGameExit = false;
		private MultiplayerMode multiplayerMode = MultiplayerMode.DISABLED;
		private List<String> servers = new ArrayList<>();

		/**
		 * Replaces values Gson could not map (unknown mode names, a missing list) and tidies the server list.
		 */
		private void normalize() {
			if (this.multiplayerMode == null) {
				this.multiplayerMode = MultiplayerMode.DISABLED;
			}
			List<String> entries = new ArrayList<>();
			if (this.servers != null) {
				for (String entry : this.servers) {
					if (entry != null && !entry.isBlank()) {
						entries.add(entry.trim());
					}
				}
			}
			this.servers = entries;
		}
	}
}
