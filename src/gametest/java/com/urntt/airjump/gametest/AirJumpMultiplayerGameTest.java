package com.urntt.airjump.gametest;

import static com.urntt.airjump.gametest.GameTestSupport.bindKey;
import static com.urntt.airjump.gametest.GameTestSupport.check;
import static com.urntt.airjump.gametest.GameTestSupport.checkJumps;
import static com.urntt.airjump.gametest.GameTestSupport.configure;
import static com.urntt.airjump.gametest.GameTestSupport.isEnabled;
import static com.urntt.airjump.gametest.GameTestSupport.jump;
import static com.urntt.airjump.gametest.GameTestSupport.modifierKey;
import static com.urntt.airjump.gametest.GameTestSupport.prepareWorld;
import static com.urntt.airjump.gametest.GameTestSupport.unbindKey;

import com.urntt.airjump.AirJumpClient;
import com.urntt.airjump.Scene;
import com.urntt.airjump.config.MultiplayerMode;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.KeyMapping;

/**
 * Checks the multiplayer modes on a local dedicated server, which the client reaches as {@code localhost}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AirJumpMultiplayerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(final ClientGameTestContext context) {
		configure(context, config -> {
			config.setEnabled(true);
			config.setMultiplayerDefault(true);
			config.setResetOnWorldExit(false);
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		KeyMapping modifierKey = modifierKey();
		KeyMapping toggleKey = bindKey(context, AirJumpClient.TOGGLE_KEY_NAME, "key.keyboard.j");

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			prepareWorld(context, server);
			Scene scene = context.computeOnClient(client -> AirJumpClient.controller().scene());
			GameTestSupport.LOGGER.info("Connected to {}", scene);
			check(scene instanceof Scene.Multiplayer, "a dedicated server should count as multiplayer, got " + scene);

			double jumpHeight = jump(context, null, 0);
			GameTestSupport.LOGGER.info("Height of a single vanilla jump on the server: {}", jumpHeight);
			check(jumpHeight > 0, "the player should leave the ground when jumping");

			checkJumps("pressing jump in mid-air on a server with multiplayer disabled", jumpHeight,
					jump(context, modifierKey, 1), 1);
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context), "a blocked toggle should leave the state unchanged");
			context.takeScreenshot("airjump-blocked-on-server");

			configure(context, config -> {
				config.setServers(List.of("localhost"));
				config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			});
			checkJumps("pressing jump in mid-air on a whitelisted server", jumpHeight,
					jump(context, modifierKey, 1), 2);

			configure(context, config -> config.setMultiplayerMode(MultiplayerMode.BLACKLIST));
			checkJumps("pressing jump in mid-air on a blacklisted server", jumpHeight,
					jump(context, modifierKey, 1), 1);
		}

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
		unbindKey(context, toggleKey);
	}
}
