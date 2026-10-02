package com.urntt.airjump.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.urntt.airjump.AirJumpClient;
import com.urntt.airjump.config.AirJumpConfig;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the client game tests.
 *
 * <p>Air jumps are measured by height: each jump, from the ground or in mid-air, raises the player by about one
 * vanilla jump height, so the highest point a sequence reaches divided by that height, rounded, is the number of
 * jumps it performed.
 */
@SuppressWarnings("UnstableApiUsage")
final class GameTestSupport {
	static final Logger LOGGER = LoggerFactory.getLogger("airjump-gametest");

	/** Upper bound on the ticks a single jump sequence may take before the test gives up. */
	private static final int MAX_JUMP_TICKS = 200;
	/**
	 * Ticks the jump key is held without being released, long enough for vanilla to jump more than once. A key repeat
	 * event is sent on each of them.
	 */
	private static final int HOLD_TICKS = 40;

	private GameTestSupport() {
	}

	/**
	 * Puts the player in survival mode on the ground. Fall damage is turned off because a damaging landing makes the
	 * server resend the player's velocity a tick later, which would add to the next jump and skew its height.
	 */
	static void prepareWorld(final ClientGameTestContext context, final TestServerContext server) {
		server.runCommand("gamemode survival @a");
		server.runCommand("gamerule fall_damage false");
		context.waitFor(client -> client.player != null && client.player.onGround());
	}

	static KeyMapping modifierKey() {
		return Objects.requireNonNull(KeyMapping.get(AirJumpClient.MODIFIER_KEY_NAME), "modifier key mapping");
	}

	/**
	 * Jumps from the ground, then presses the jump key {@code airPresses} more times, each time once the player has
	 * started to fall. Returns the highest point reached above the starting position.
	 *
	 * @param modifierKey the key held during the whole sequence, or {@code null} to hold none
	 */
	static double jump(final ClientGameTestContext context, final KeyMapping modifierKey, final int airPresses) {
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
	static double holdJump(final ClientGameTestContext context, final KeyMapping modifierKey) {
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
	 * Checks how many jumps a sequence performed, given the height of a single vanilla jump.
	 */
	static void checkJumps(final String action, final double jumpHeight, final double height, final int expectedJumps) {
		long jumps = Math.round(height / jumpHeight);
		LOGGER.info("{}: reached {} blocks, {} jump(s)", action, height, jumps);
		check(jumps == expectedJumps, action + " should perform " + expectedJumps + " jump(s), but reached "
				+ height + " blocks, which is " + jumps + " jump(s)");
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
	 * Binds {@code mapping} to the key named {@code key} for the test.
	 */
	static void bindKey(final ClientGameTestContext context, final KeyMapping mapping, final String key) {
		context.runOnClient(client -> {
			mapping.setKey(InputConstants.getKey(key));
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Returns the mod's key mapping registered under {@code name}, bound to {@code key} for the test.
	 */
	static KeyMapping bindKey(final ClientGameTestContext context, final String name, final String key) {
		KeyMapping mapping = Objects.requireNonNull(KeyMapping.get(name), name);
		bindKey(context, mapping, key);
		return mapping;
	}

	static void unbindKey(final ClientGameTestContext context, final KeyMapping mapping) {
		context.runOnClient(client -> {
			mapping.setKey(mapping.getDefaultKey());
			KeyMapping.resetMapping();
		});
	}

	/**
	 * Changes the mod's configuration on the client thread.
	 */
	static void configure(final ClientGameTestContext context, final Consumer<AirJumpConfig> change) {
		context.runOnClient(client -> change.accept(AirJumpClient.config()));
	}

	static boolean isEnabled(final ClientGameTestContext context) {
		return context.computeOnClient(client -> AirJumpClient.config().isEnabled());
	}

	static AirJumpConfig loadSavedConfig() {
		return AirJumpConfig.load(AirJumpConfig.defaultPath());
	}

	static void check(final boolean condition, final String message) {
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
