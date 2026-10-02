package com.urntt.airjump.gametest;

import static com.urntt.airjump.gametest.GameTestSupport.check;

import com.urntt.airjump.AirJumpController;
import com.urntt.airjump.Scene;
import com.urntt.airjump.ServerAddresses;
import com.urntt.airjump.config.MultiplayerMode;
import com.urntt.airjump.config.AirJumpConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Checks the address matching, the configuration defaults, and the controller's rules without a world. Each check
 * uses its own configuration file, so the game's configuration is left alone. Restarting the game is not possible
 * in a game test, so this is also where "reset on game exit" is covered.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AirJumpLogicGameTest implements FabricClientGameTest {
	private static final Scene SERVER_A = Scene.multiplayer("a.example.com");
	private static final Scene SERVER_B = Scene.multiplayer("b.example.com:25570");
	private static final Scene UNKNOWN_SERVER = Scene.multiplayer(null);

	@Override
	public void runTest(final ClientGameTestContext context) {
		Path directory = createTempDirectory();
		checkAddressMatching();
		checkDefaults(directory);
		checkUpgradeFromOlderConfig(directory);
		checkMultiplayerRules(directory);
		checkResetRules(directory);
		GameTestSupport.LOGGER.info("Logic checks passed");
	}

	private static void checkAddressMatching() {
		checkMatch("mc.example.com", "mc.example.com", true);
		checkMatch("MC.Example.com", "mc.example.COM:25565", true);
		checkMatch("mc.example.com", "mc.example.com:25566", true);
		checkMatch("mc.example.com:25565", "mc.example.com", true);
		checkMatch("mc.example.com:25566", "mc.example.com", false);
		checkMatch("mc.example.com", "play.example.com", false);
		checkMatch("  mc.example.com  ", "mc.example.com", true);
		checkMatch("192.168.1.5", "192.168.1.5:51234", true);
		checkMatch("[::1]:25565", "[::1]", true);
		checkMatch("::1", "[::1]:25570", true);
		checkMatch("bücher.example", "xn--bcher-kva.example", true);
		checkMatch("localhost", "localhost:41234", true);

		for (String valid : List.of("mc.example.com:25566", "localhost", "192.168.1.5", "[::1]:25565", "bücher.example")) {
			check(ServerAddresses.isValid(valid), "'" + valid + "' should be valid");
		}
		for (String invalid : List.of("", "   ", "mc.example.com:abc", "mc.example.com:99999", "[::1", "mc example.com",
				"not a host:port:x")) {
			check(!ServerAddresses.isValid(invalid), "'" + invalid + "' should be invalid");
		}
	}

	private static void checkMatch(final String entry, final String address, final boolean expected) {
		check(ServerAddresses.matches(entry, address) == expected,
				"'" + entry + "' should " + (expected ? "" : "not ") + "match '" + address + "'");
	}

	private static void checkDefaults(final Path directory) {
		AirJumpConfig config = AirJumpConfig.load(directory.resolve("defaults.json"));
		check(config.isEnabled(), "air jumps should be enabled by default");
		check(config.singleplayerDefault(), "the singleplayer default should be on");
		check(config.multiplayerDefault(), "the server default should be on");
		check(!config.resetOnWorldExit(), "reset on world exit should be off by default");
		check(!config.resetOnGameExit(), "reset on game exit should be off by default");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "multiplayer should be disabled by default");
		check(config.servers().isEmpty(), "the server list should be empty by default");
		check(Files.exists(directory.resolve("defaults.json")), "a missing config file should be created");
	}

	private static void checkUpgradeFromOlderConfig(final Path directory) {
		Path path = directory.resolve("upgrade.json");
		write(path, "{\"enabled\": false, \"multiplayerMode\": \"bogus\", \"servers\": [\" a.example.com \", \"\"]}");

		AirJumpConfig config = AirJumpConfig.load(path);
		check(!config.isEnabled(), "existing settings should be kept");
		check(config.multiplayerMode() == MultiplayerMode.DISABLED, "an unknown mode should fall back to disabled");
		check(config.servers().equals(List.of("a.example.com")), "entries should be trimmed and blanks dropped");
		check(read(path).contains("\"resetOnWorldExit\""), "missing settings should be written to the file");
	}

	private static void checkMultiplayerRules(final Path directory) {
		AirJumpConfig config = AirJumpConfig.load(directory.resolve("rules.json"));
		AirJumpController controller = new AirJumpController(config);

		check(!controller.isActive(), "nothing should be active outside a world");
		controller.onJoin(Scene.SINGLEPLAYER);
		check(controller.isActive(), "singleplayer should always be allowed");

		controller.onJoin(SERVER_A);
		check(!controller.isAllowed(), "the disabled mode should rule out every server");
		check(controller.toggle() == AirJumpController.ToggleResult.BLOCKED, "toggling should be blocked");
		check(config.isEnabled(), "a blocked toggle should leave the state unchanged");

		config.setServers(List.of("a.example.com"));
		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		check(controller.isActive(), "a whitelisted server should be allowed");
		controller.onJoin(SERVER_B);
		check(!controller.isAllowed(), "a server missing from the whitelist should be ruled out");
		controller.onJoin(UNKNOWN_SERVER);
		check(!controller.isAllowed(), "an unknown address should not count as whitelisted");

		config.setMultiplayerMode(MultiplayerMode.BLACKLIST);
		check(controller.isAllowed(), "an unknown address should not count as blacklisted");
		controller.onJoin(SERVER_B);
		check(controller.isAllowed(), "a server missing from the blacklist should be allowed");
		controller.onJoin(SERVER_A);
		check(!controller.isAllowed(), "a blacklisted server should be ruled out");

		controller.onDisconnect();
		check(!controller.isActive(), "nothing should be active after disconnecting");
	}

	private static void checkResetRules(final Path directory) {
		AirJumpConfig config = AirJumpConfig.load(directory.resolve("reset.json"));
		config.setResetOnGameExit(true);
		config.setEnabled(false);

		// A fresh controller stands for a game that has just started.
		AirJumpController controller = new AirJumpController(config);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(), "a ruled-out server should not apply the game exit reset");
		controller.onDisconnect();
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(), "the first allowed world after starting should restore the default");
		controller.onDisconnect();
		config.setEnabled(false);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(!config.isEnabled(), "later worlds should keep the state without reset on world exit");
		controller.onDisconnect();

		config.setResetOnWorldExit(true);
		controller.onJoin(Scene.SINGLEPLAYER);
		check(config.isEnabled(), "reset on world exit should restore the singleplayer default");
		controller.onDisconnect();

		config.setMultiplayerMode(MultiplayerMode.WHITELIST);
		config.setServers(List.of("a.example.com"));
		config.setMultiplayerDefault(false);
		controller.onJoin(SERVER_A);
		check(!config.isEnabled(), "an allowed server should restore the server default");
		controller.onDisconnect();
	}

	private static Path createTempDirectory() {
		try {
			return Files.createTempDirectory("airjump-gametest");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(final Path path, final String content) {
		try {
			Files.writeString(path, content);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(final Path path) {
		try {
			return Files.readString(path);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
