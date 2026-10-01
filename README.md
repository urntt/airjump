# airjump

A simple client-side Fabric mod for Minecraft: Java Edition that lets you jump in mid-air.

Hold the **Air Jump Modifier** key and press jump while you are in the air to jump again, as high as a normal jump. There is no limit on how many times you can jump before landing. Only your own player is affected.

## Multiplayer warning

This mod changes player movement. Servers that run anti-cheat systems may detect it, which can get your movement set back, get you kicked, or get you banned, and using it may break a server's rules. Check each server's rules before joining with this mod installed. Use it in multiplayer at your own risk.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/airjump/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

## Usage

- While holding **Air Jump Modifier** (`R` by default), press the jump key while you are off the ground to jump again right away. Each press is one air jump; holding the jump key down does not repeat it.
- An air jump is a normal vanilla jump, so Jump Boost and sprint jumping apply as usual.
- Air jumps react to the jump key on the keyboard. If the jump key is bound to a mouse button, air jumps do not work.
- Air jumps do not reset fall damage. Every block you fall counts toward the damage you take when you land, including the distance you fell before each air jump.

To change the modifier key, open **Options → Controls → Key Binds** and look for the **airjump** category. Unbinding the key turns air jumps off.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft, create a test world, and check how high the player gets with and without air jumps:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

## License

[MIT](LICENSE)
