package com.urntt.airjump;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
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
	 * Handles a keyboard event before vanilla does. When the jump key is pressed while the modifier key is held, no
	 * screen is open and the player is off the ground, the player jumps right away. Key repeats and releases are
	 * ignored, so holding the jump key performs at most one air jump.
	 */
	public static void handleKeyPress(final Minecraft client, final int action, final KeyEvent event) {
		if (client.player == null) {
			return;
		}

		if (client.gui.screen() != null) {
			return;
		}

		if (client.player.onGround()) {
			return;
		}

		if (action != InputConstants.PRESS) {
			return;
		}

		if (client.options.keyJump.matches(event) && modifierKey.isDown()) {
			client.player.jumpFromGround();
		}
	}
}
