package com.urntt.airjump;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.airjump.config.AirJumpConfig;
import com.urntt.airjump.config.AirJumpConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class AirJumpClient implements ClientModInitializer {
	public static final String MOD_ID = "airjump";
	public static final String MODIFIER_KEY_NAME = "key.airjump.modifier";
	public static final String TOGGLE_KEY_NAME = "key.airjump.toggle";
	public static final String OPEN_SETTINGS_KEY_NAME = "key.airjump.open_settings";

	/** Translation key of the feature's name, shared by the toggle message and the configuration screen. */
	public static final String FEATURE_NAME_KEY = "options.airjump.enabled";
	public static final Component FEATURE_NAME = Component.translatable(FEATURE_NAME_KEY);
	/** Action bar message shown when the toggle key is pressed on a server the multiplayer rules rule out. */
	public static final Component BLOCKED_MESSAGE = Component.translatable("message.airjump.blocked");

	private static AirJumpConfig config;
	private static AirJumpController controller;
	private static KeyMapping modifierKey;

	@Override
	public void onInitializeClient() {
		config = AirJumpConfig.load(AirJumpConfig.defaultPath());
		controller = new AirJumpController(config);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		modifierKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(MODIFIER_KEY_NAME, InputConstants.KEY_R, category));
		KeyMapping toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(TOGGLE_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));
		KeyMapping openSettingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping(OPEN_SETTINGS_KEY_NAME, InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				toggle(client);
			}
			while (openSettingsKey.consumeClick()) {
				client.gui.setScreen(new AirJumpConfigScreen(client.gui.screen()));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> controller.onJoin(Scene.of(client)));
		// The disconnect event may arrive on the network thread; the controller is only used on the client thread.
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(controller::onDisconnect));
	}

	public static AirJumpConfig config() {
		return config;
	}

	public static AirJumpController controller() {
		return controller;
	}

	/**
	 * Handles a keyboard event before vanilla does. Only a press of the jump key can perform an air jump; key repeats
	 * and releases are ignored, so holding the jump key performs at most one air jump.
	 */
	public static void handleKeyPress(final Minecraft client, final int action, final KeyEvent event) {
		if (action == InputConstants.PRESS && client.options.keyJump.matches(event)) {
			airJump(client);
		}
	}

	/**
	 * Handles a mouse button event before vanilla does, so a jump key bound to a mouse button works like one bound to
	 * a keyboard key. Mouse buttons send no repeat events, so each press can perform an air jump.
	 */
	public static void handleMouseButton(final Minecraft client, final int action, final MouseButtonInfo button) {
		if (action == InputConstants.PRESS
				&& client.options.keyJump.matches(InputConstants.Type.MOUSE.getOrCreate(button.button()))) {
			airJump(client);
		}
	}

	/**
	 * Jumps right away when the feature is active, the modifier key is held, no screen is open and the player is off
	 * the ground.
	 */
	private static void airJump(final Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null && client.gui.screen() == null && !player.onGround() && modifierKey.isDown()
				&& controller.isActive()) {
			player.jumpFromGround();
		}
	}

	private static void toggle(final Minecraft client) {
		AirJumpController.ToggleResult result = controller.toggle();
		if (client.player == null) {
			return;
		}

		Component message = switch (result) {
			case ENABLED -> CommonComponents.optionStatus(FEATURE_NAME, true);
			case DISABLED -> CommonComponents.optionStatus(FEATURE_NAME, false);
			case BLOCKED -> BLOCKED_MESSAGE;
		};
		client.player.sendOverlayMessage(message);
	}
}
