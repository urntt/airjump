# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

### Added

- Add a configuration screen built from vanilla widgets, with a setting for whether air jumps are enabled, separate defaults for singleplayer worlds and allowed servers, and options to reset to the default on world exit or game exit. Settings are saved to `config/airjump.json`.
- Add multiplayer modes (disabled, whitelist, blacklist) and a server list screen for editing the addresses they use.
- Add "Toggle Air Jump" and "Open airjump Settings" key bindings, both unbound by default. The toggle key shows the new state on the action bar.
- Open the configuration screen from Mod Menu, which is optional.

### Changed

- Air jumps are now disabled on multiplayer servers by default. To use them on a server, choose the whitelist or blacklist mode on the configuration screen.

## [1.0.0+26.3] - 2026-10-02

### Added

- Jump in mid-air by pressing the jump key while holding the Air Jump Modifier key, bound to `R` by default. Air jumps are unlimited, each key press performs one, and holding the jump key does not repeat them. The jump key can be bound to a keyboard key or a mouse button.
- Add English and Simplified Chinese translations.
