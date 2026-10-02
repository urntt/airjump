package com.urntt.airjump.gametest;

import static com.urntt.airjump.gametest.GameTestSupport.bindKey;
import static com.urntt.airjump.gametest.GameTestSupport.check;
import static com.urntt.airjump.gametest.GameTestSupport.checkJumps;
import static com.urntt.airjump.gametest.GameTestSupport.configure;
import static com.urntt.airjump.gametest.GameTestSupport.holdJump;
import static com.urntt.airjump.gametest.GameTestSupport.isEnabled;
import static com.urntt.airjump.gametest.GameTestSupport.jump;
import static com.urntt.airjump.gametest.GameTestSupport.loadSavedConfig;
import static com.urntt.airjump.gametest.GameTestSupport.modifierKey;
import static com.urntt.airjump.gametest.GameTestSupport.prepareWorld;
import static com.urntt.airjump.gametest.GameTestSupport.unbindKey;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.airjump.AirJumpClient;
import com.urntt.airjump.config.AirJumpConfigScreen;
import com.urntt.airjump.config.MultiplayerMode;
import com.urntt.airjump.config.ServerListScreen;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

/**
 * Checks air jumps, the toggle key, the reset on world exit, and the settings screens in singleplayer worlds.
 */
@SuppressWarnings("UnstableApiUsage")
public final class AirJumpClientGameTest implements FabricClientGameTest {
	/** Air jump presses in the sequence that checks the number of air jumps is not limited. */
	private static final int CONSECUTIVE_AIR_JUMPS = 3;
	/** Mouse button the jump key is bound to while checking air jumps from mouse button events. */
	private static final String MOUSE_JUMP_KEY = "key.mouse.4";

	@Override
	public void runTest(final ClientGameTestContext context) {
		KeyMapping modifierKey = modifierKey();
		// The run directory is deleted before each run, so this is the binding of a fresh installation.
		check(modifierKey.getDefaultKey().equals(InputConstants.getKey("key.keyboard.r")), "the modifier key should default to R");
		check(context.computeOnClient(client -> modifierKey.isDefault()), "the modifier key should be bound to its default");

		configure(context, config -> {
			config.setEnabled(true);
			config.setSingleplayerDefault(true);
			config.setResetOnWorldExit(false);
			config.setResetOnGameExit(false);
		});
		KeyMapping toggleKey = bindKey(context, AirJumpClient.TOGGLE_KEY_NAME, "key.keyboard.j");
		KeyMapping openSettingsKey = bindKey(context, AirJumpClient.OPEN_SETTINGS_KEY_NAME, "key.keyboard.k");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			prepareWorld(context, singleplayer.getServer());

			double jumpHeight = jump(context, null, 0);
			GameTestSupport.LOGGER.info("Height of a single vanilla jump: {}", jumpHeight);
			check(jumpHeight > 0, "the player should leave the ground when jumping");
			checkAirJumps(context, modifierKey, jumpHeight);

			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable air jumps");
			check(!loadSavedConfig().isEnabled(), "disabled state should be saved to the config file");
			context.takeScreenshot("airjump-toggled-off");
			checkJumps("pressing jump in mid-air with the modifier key held while air jumps are off", jumpHeight,
					jump(context, modifierKey, 1), 1);

			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(isEnabled(context), "toggle key should enable air jumps again");
			check(loadSavedConfig().isEnabled(), "enabled state should be saved to the config file");

			checkSettingsScreens(context, openSettingsKey);

			// Leave this world disabled to check the reset on world exit below.
			configure(context, config -> config.setResetOnWorldExit(true));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable air jumps before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(isEnabled(context), "reset on world exit should restore the singleplayer default");

			configure(context, config -> config.setResetOnWorldExit(false));
			context.getInput().pressKey(toggleKey);
			context.waitTick();
			check(!isEnabled(context), "toggle key should disable air jumps before leaving");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			check(!isEnabled(context), "without reset on world exit the state should carry over");
		}

		configure(context, config -> config.setEnabled(true));
		unbindKey(context, toggleKey);
		unbindKey(context, openSettingsKey);
		screenshotKeyBinds(context);
	}

	/**
	 * Checks that air jumps need the modifier key, are not limited to one, are not repeated by key repeat events,
	 * and also work with the jump key bound to a mouse button.
	 */
	private static void checkAirJumps(final ClientGameTestContext context, final KeyMapping modifierKey,
			final double jumpHeight) {
		checkJumps("pressing jump in mid-air without the modifier key", jumpHeight,
				jump(context, null, 1), 1);
		checkJumps("pressing jump in mid-air with the modifier key held", jumpHeight,
				jump(context, modifierKey, 1), 2);
		checkJumps("pressing jump " + CONSECUTIVE_AIR_JUMPS + " times in mid-air with the modifier key held",
				jumpHeight, jump(context, modifierKey, CONSECUTIVE_AIR_JUMPS), CONSECUTIVE_AIR_JUMPS + 1);
		checkJumps("holding jump with the modifier key held while the key repeats", jumpHeight,
				holdJump(context, modifierKey), 1);

		KeyMapping jumpKey = context.computeOnClient(client -> client.options.keyJump);
		bindKey(context, jumpKey, MOUSE_JUMP_KEY);
		checkJumps("pressing jump bound to a mouse button in mid-air with the modifier key held", jumpHeight,
				jump(context, modifierKey, 1), 2);
		unbindKey(context, jumpKey);
	}

	/**
	 * Opens the settings with the key binding, then takes screenshots of both settings screens in English and in
	 * Simplified Chinese.
	 */
	private static void checkSettingsScreens(final ClientGameTestContext context, final KeyMapping openSettingsKey) {
		context.getInput().pressKey(openSettingsKey);
		context.waitForScreen(AirJumpConfigScreen.class);
		context.setScreen(() -> null);

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.WHITELIST);
			config.setServers(List.of("mc.example.com", "192.168.1.5"));
		});
		// Keep the cursor away from the widgets so no tooltip covers them.
		context.getInput().setCursorPos(0, 0);
		takeSettingsScreenshots(context, "en_us");
		switchLanguage(context, "zh_cn");
		takeSettingsScreenshots(context, "zh_cn");
		switchLanguage(context, "en_us");

		configure(context, config -> {
			config.setMultiplayerMode(MultiplayerMode.DISABLED);
			config.setServers(List.of());
		});
	}

	private static void takeSettingsScreenshots(final ClientGameTestContext context, final String language) {
		context.setScreen(() -> new AirJumpConfigScreen(null));
		context.takeScreenshot("airjump-config-screen-" + language);
		context.getInput().scroll(-20);
		context.waitTick();
		context.takeScreenshot("airjump-config-screen-bottom-" + language);

		context.setScreen(() -> new ServerListScreen(new AirJumpConfigScreen(null), AirJumpClient.config()));
		context.takeScreenshot("airjump-server-list-" + language);
		context.setScreen(() -> null);
	}

	private static void switchLanguage(final ClientGameTestContext context, final String language) {
		CompletableFuture<Void> reload = context.computeOnClient(client -> {
			client.getLanguageManager().setSelected(language);
			client.options.languageCode = language;
			return client.reloadResourcePacks();
		});
		context.waitFor(client -> reload.isDone() && client.gui.overlay() == null);
	}

	/** Shows the mod's key binding category, which vanilla lists last, so the screenshot shows its translations. */
	private static void screenshotKeyBinds(final ClientGameTestContext context) {
		Options options = context.computeOnClient(client -> client.options);
		context.setScreen(() -> new KeyBindsScreen(null, options));
		int width = context.computeOnClient(client -> client.getWindow().getScreenWidth());
		int height = context.computeOnClient(client -> client.getWindow().getScreenHeight());
		context.getInput().setCursorPos(width / 2.0, height / 2.0);
		context.getInput().scroll(-Integer.MAX_VALUE);
		context.takeScreenshot("airjump-key-binds");
		context.setScreen(() -> null);
	}
}
