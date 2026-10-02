package com.urntt.airjump.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.airjump.AirJumpClient;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("UnstableApiUsage")
public final class AirJumpClientGameTest implements FabricClientGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("airjump-gametest");

	/** Upper bound on the ticks a single jump sequence may take before the test gives up. */
	private static final int MAX_JUMP_TICKS = 200;
	/**
	 * Ticks the jump key is held without being released, long enough for vanilla to jump more than once. A key repeat
	 * event is sent on each of them.
	 */
	private static final int HOLD_TICKS = 40;
	/** Air jump presses in the sequence that checks the number of air jumps is not limited. */
	private static final int CONSECUTIVE_AIR_JUMPS = 3;
	/** Mouse button the jump key is bound to while checking air jumps from mouse button events. */
	private static final String MOUSE_JUMP_KEY = "key.mouse.4";

	@Override
	public void runTest(final ClientGameTestContext context) {
		KeyMapping modifierKey = Objects.requireNonNull(KeyMapping.get(AirJumpClient.MODIFIER_KEY_NAME), "modifier key mapping");

		// The run directory is deleted before each run, so this is the binding of a fresh installation.
		check(modifierKey.getDefaultKey().equals(InputConstants.getKey("key.keyboard.r")), "the modifier key should default to R");
		check(context.computeOnClient(client -> modifierKey.isDefault()), "the modifier key should be bound to its default");

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("gamemode survival @a");
			// A damaging landing makes the server resend the player's velocity a tick later, which would add to the
			// next jump and skew its height.
			singleplayer.getServer().runCommand("gamerule fall_damage false");

			double jumpHeight = jump(context, null, 0);
			LOGGER.info("Height of a single vanilla jump: {}", jumpHeight);
			check(jumpHeight > 0, "the player should leave the ground when jumping");

			checkJumps("pressing jump in mid-air without the modifier key", jumpHeight,
					jump(context, null, 1), 1);
			checkJumps("pressing jump in mid-air with the modifier key held", jumpHeight,
					jump(context, modifierKey, 1), 2);
			checkJumps("pressing jump " + CONSECUTIVE_AIR_JUMPS + " times in mid-air with the modifier key held",
					jumpHeight, jump(context, modifierKey, CONSECUTIVE_AIR_JUMPS), CONSECUTIVE_AIR_JUMPS + 1);
			checkJumps("holding jump with the modifier key held while the key repeats", jumpHeight,
					holdJump(context, modifierKey), 1);

			bindJumpKey(context, InputConstants.getKey(MOUSE_JUMP_KEY));
			checkJumps("pressing jump bound to a mouse button in mid-air with the modifier key held", jumpHeight,
					jump(context, modifierKey, 1), 2);
			bindJumpKey(context, null);
		}

		screenshotKeyBinds(context);
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

	/**
	 * Jumps from the ground, then presses the jump key {@code airPresses} more times, each time once the player has
	 * started to fall. Returns the highest point reached above the starting position.
	 *
	 * @param modifierKey the key held during the whole sequence, or {@code null} to hold none
	 */
	private static double jump(final ClientGameTestContext context, final KeyMapping modifierKey, final int airPresses) {
		context.waitFor(client -> client.player.onGround());
		JumpTracker tracker = new JumpTracker(context);
		if (modifierKey != null) {
			context.getInput().holdKey(modifierKey);
		}

		tracker.pressJump();
		for (int press = 0; press < airPresses; press++) {
			tracker.tickUntil(() -> context.computeOnClient(client -> client.player.getDeltaMovement().y < 0));
			tracker.pressJump();
		}

		tracker.tickUntil(() -> context.computeOnClient(client -> client.player.onGround()));
		if (modifierKey != null) {
			context.getInput().releaseKey(modifierKey);
		}

		return tracker.height();
	}

	/**
	 * Holds the jump key for {@link #HOLD_TICKS} ticks without releasing it, with the modifier key held, and sends the
	 * key repeat events the operating system sends for a held key. Returns the highest point reached above the
	 * starting position.
	 */
	private static double holdJump(final ClientGameTestContext context, final KeyMapping modifierKey) {
		context.waitFor(client -> client.player.onGround());
		JumpTracker tracker = new JumpTracker(context);
		context.getInput().holdKey(modifierKey);
		context.getInput().holdKey(options -> options.keyJump);
		for (int tick = 0; tick < HOLD_TICKS; tick++) {
			repeatJumpKey(context);
			tracker.tick();
		}

		context.getInput().releaseKey(options -> options.keyJump);
		tracker.tickUntil(() -> context.computeOnClient(client -> client.player.onGround()));
		context.getInput().releaseKey(modifierKey);
		return tracker.height();
	}

	/**
	 * Binds the jump key to {@code key}, or back to its default when {@code key} is {@code null}.
	 */
	private static void bindJumpKey(final ClientGameTestContext context, final InputConstants.Key key) {
		context.runOnClient(client -> {
			client.options.keyJump.setKey(key != null ? key : client.options.keyJump.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/** Sends a key repeat event for the jump key the way the game receives one from SDL. */
	private static void repeatJumpKey(final ClientGameTestContext context) {
		context.runOnClient(client -> {
			int scancode = KeyMappingHelper.getBoundKeyOf(client.options.keyJump).getValue();
			KeyEvent event = new KeyEvent(scancode, SDLKeyboard.SDL_GetKeyFromScancode(scancode, (short) 0, false), 0);
			client.keyboardHandler.keyPress(client.getWindow().handle(), InputConstants.REPEAT, event);
		});
	}

	/**
	 * Checks how many jumps a sequence performed. Each jump, from the ground or in mid-air, raises the player by about
	 * one vanilla jump height, so the height reached divided by it, rounded, is the number of jumps.
	 */
	private static void checkJumps(final String action, final double jumpHeight, final double height,
			final int expectedJumps) {
		long jumps = Math.round(height / jumpHeight);
		LOGGER.info("{}: reached {} blocks, {} jump(s)", action, height, jumps);
		check(jumps == expectedJumps, action + " should perform " + expectedJumps + " jump(s), but reached "
				+ height + " blocks, which is " + jumps + " jump(s)");
	}

	private static void check(final boolean condition, final String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/** Records the highest point the player reaches while the test advances tick by tick. */
	private static final class JumpTracker {
		private final ClientGameTestContext context;
		private final double startY;
		private double maxY;
		private int ticks;

		JumpTracker(final ClientGameTestContext context) {
			this.context = context;
			this.startY = this.y();
			this.maxY = this.startY;
		}

		/**
		 * Presses the jump key and holds it for one tick. The mod reacts to the key press itself, and vanilla sees the
		 * key held for a tick, so a press on the ground jumps as usual.
		 */
		void pressJump() {
			this.context.getInput().holdKey(options -> options.keyJump);
			this.tick();
			this.context.getInput().releaseKey(options -> options.keyJump);
		}

		void tickUntil(final BooleanSupplier condition) {
			do {
				this.tick();
			} while (!condition.getAsBoolean());
		}

		void tick() {
			check(++this.ticks <= MAX_JUMP_TICKS, "the jump sequence did not finish within " + MAX_JUMP_TICKS + " ticks");
			this.context.waitTick();
			this.maxY = Math.max(this.maxY, this.y());
		}

		double height() {
			return this.maxY - this.startY;
		}

		private double y() {
			return this.context.computeOnClient(client -> client.player.getY());
		}
	}
}
