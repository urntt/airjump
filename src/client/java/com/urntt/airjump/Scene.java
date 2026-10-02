package com.urntt.airjump;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/**
 * Where the player currently is, as far as the multiplayer rules are concerned.
 */
public sealed interface Scene {
	/** Not in a world, for example on the title screen. */
	Scene NONE = new None();
	/** A world hosted by this client, including one opened to LAN. */
	Scene SINGLEPLAYER = new Singleplayer();

	static Scene multiplayer(final @Nullable String address) {
		return new Multiplayer(address);
	}

	/**
	 * Determines the scene of the world {@code minecraft} has just joined.
	 */
	static Scene of(final Minecraft minecraft) {
		if (minecraft.isLocalServer()) {
			return SINGLEPLAYER;
		}
		ServerData server = minecraft.getCurrentServer();
		return multiplayer(server != null ? server.ip : null);
	}

	record None() implements Scene {
	}

	record Singleplayer() implements Scene {
	}

	/**
	 * A remote server: a dedicated server, another player's LAN world, or a Realm.
	 *
	 * @param address the address the player connected to, or {@code null} if it is unknown
	 */
	record Multiplayer(@Nullable String address) implements Scene {
	}
}
