package com.urntt.airjump;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

public final class AirJumpClient implements ClientModInitializer {
	public static final String MOD_ID = "airjump";
	public static final String MODIFIER_KEY_NAME = "key.airjump.modifier";

	private static KeyMapping modifierKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		modifierKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(MODIFIER_KEY_NAME, InputConstants.KEY_R, category));
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

	/** Jumps right away when the modifier key is held, no screen is open and the player is off the ground. */
	private static void airJump(final Minecraft client) {
		LocalPlayer player = client.player;
		if (player != null && client.gui.screen() == null && !player.onGround() && modifierKey.isDown()) {
			player.jumpFromGround();
		}
	}
}
