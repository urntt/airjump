package com.urntt.airjump;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

public final class AirJumpClient implements ClientModInitializer {
	public static final String MOD_ID = "airjump";
	public static final String MODIFIER_KEY_NAME = "key.airjump.modifier";

	private KeyMapping modifierKey;

	/** Whether the jump key was down at the start of the previous client tick. */
	private boolean wasJumpKeyDown;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"));
		this.modifierKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(MODIFIER_KEY_NAME, InputConstants.KEY_R, category));

		ClientTickEvents.START_CLIENT_TICK.register(this::onStartClientTick);
	}

	/**
	 * Performs an air jump when the jump key goes down while the modifier key is held and the player is in mid-air.
	 *
	 * <p>Like vanilla's own jump handling, a press is the jump key being up at one tick and down at the next, so
	 * holding the jump key never repeats an air jump. This runs before the player's tick, so {@code onGround()} still
	 * describes the position the press was made in: a press on the ground is left to vanilla's jump and does not also
	 * trigger an air jump once the player has left the ground.
	 */
	private void onStartClientTick(final Minecraft client) {
		boolean jumpKeyDown = client.options.keyJump.isDown();
		boolean jumpPressed = jumpKeyDown && !this.wasJumpKeyDown;
		this.wasJumpKeyDown = jumpKeyDown;

		LocalPlayer player = client.player;
		if (jumpPressed && this.modifierKey.isDown() && player != null && canAirJump(player)) {
			player.jumpFromGround();
		}
	}

	/**
	 * Whether the player is in mid-air. Air jumps are left out wherever vanilla already gives the jump key a
	 * meaning: on the ground, in water or lava, on ladders and other climbable blocks, while flying, while gliding with
	 * an elytra, and while riding.
	 */
	private static boolean canAirJump(final LocalPlayer player) {
		return !player.onGround()
				&& !player.isInLiquid()
				&& !player.onClimbable()
				&& !player.getAbilities().flying
				&& !player.isFallFlying()
				&& !player.isPassenger();
	}
}
