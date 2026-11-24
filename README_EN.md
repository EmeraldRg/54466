# MiniAssist 1.4.1 — Fabric 1.21.4

Client-side Minecraft mod with a modern dark ClickGUI, persistent settings, editable module binds, HUD, Aim Assist, TriggerBot and FreeCam.

## Modules

- **Aim Assist** — automatically selects a valid visible target and smoothly rotates the local view toward it without requiring a mouse click. Target locks reset on world/player changes, removed and cross-world entities are rejected, and movement prediction is capped to avoid knockback overshoot. F6 toggles it.
- **TriggerBot** — checks the crosshair target and vanilla attack-strength cooldown once per client tick. It supports an extra delay in game ticks, eye-to-target range checking and world/respawn resets. Critical-only mode also waits for the vanilla >90% attack-strength threshold and genuine fall-state conditions; no movement or attack packets are spoofed. F7 toggles it.
- **FreeCam** — separates the local camera from the player and uses configured Minecraft movement bindings. It samples/restores pressed states each tick, restores captured velocity/fall distance when disabled and turns itself off safely on world changes or disconnections, returning the camera and captured keys. F8 toggles it; Sprint uses a configurable speed multiplier.
- **HUD** — separate dark translucent panels with violet/cyan accents, inspired by the compact Nursultan 1.16.5 HUD aesthetic: watermark/FPS/ping, active modules and binds, coordinates/speed/direction, armor durability, potion effects, Target HUD and live WASD/space/mouse Keystrokes. Each panel can be toggled separately. Text is clipped safely, the watermark adapts to narrow windows, and panels are repositioned or hidden to avoid overlap.

Target categories default to players. Hostile and passive entities can be enabled in the module settings.

## HUD

Open Right Shift → Render → HUD to toggle each panel. The layout uses original dark translucent panels with violet/cyan accents and does not include Nursultan code or assets. G hides/shows the complete custom HUD.

## ClickGUI

- **Right Shift** opens/closes the menu. **Esc** closes it.
- Dark violet/blue modular layout inspired by modern client ClickGUIs, with category navigation, module cards, sliders, switches, target chips, scroll indicator and scrolling.
- Click the module **BIND** chip, then press a key to assign it. **Delete/Backspace** clears the bind; **Esc** cancels capture.
- Module binds are saved in `config/miniassist.properties` and survive restarts. Duplicate module binds are cleared automatically when a key is reassigned.
- **F6 / F7 / F8** are the default binds for Aim Assist / TriggerBot / FreeCam and can all be changed in the GUI.
- **G** hides/shows the HUD and is reserved; **Right Shift** opens the menu and can be changed in Minecraft Controls.

## GitHub Actions build

The repository contains `.github/workflows/build.yml`. It builds on every push (any branch) and supports manual runs:

1. Upload the contents of this project folder to the repository root. The root must contain `build.gradle`, `gradle.properties`, `settings.gradle`, `src/` and `.github/workflows/build.yml`.
2. Commit the files to `main` or run **Actions → build → Run workflow**.
3. Wait for **MiniAssist Build → Build Fabric mod** to finish.
4. Download **Artifacts → MiniAssist-1.4.1** from the completed run.
5. Extract the downloaded artifact ZIP and put `zenith-client-1.4.1.jar` into the Minecraft `mods` folder alongside Fabric API. If compilation fails, the workflow attempts to upload a `MiniAssist-build-log` diagnostic artifact.

The workflow validates the repository root, provisions Temurin Java 21 and Gradle 8.12 through GitHub Actions, verifies the Fabric mod metadata in the output JAR, and uploads the compiler log when a build fails. A local Gradle Wrapper is not required. Build environment: Temurin Java 21, Gradle 8.12, Fabric Loom 1.10.5, Minecraft 1.21.4, Yarn `1.21.4+build.8`, Fabric Loader 0.16.9 and Fabric API 0.112.2+1.21.4. The output mod version is `1.4.1`.

## Notes

This project targets Minecraft **1.21.4**. Nursultan 1.16.5 is only a visual reference for the HUD panel style; this is not a Minecraft 1.16.5 port and does not copy Nursultan code or assets. A module runtime error is logged and disables only that module instead of interrupting the client tick. FreeCam is also safely disabled on disconnect so the detached camera and captured input are restored. Config load/save errors are logged instead of silently ignored. Multiplayer servers may restrict combat-assistance mods; follow each server's rules. Critical-only mode waits for vanilla critical-hit conditions and does not attempt anti-cheat bypasses.
