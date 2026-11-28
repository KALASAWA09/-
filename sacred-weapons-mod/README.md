# Sacred Souls (วิญญาณศักดิ์สิทธิ์)

Fabric mod for Minecraft 1.21.11 (Java 21, Fabric API 0.141.6+1.21.11).

- Press **K** to open the soul screen: 5 devour slots, click **อัป** to upgrade a slot (levels 1-3), **คาย** to eject with Cleansing Water.
- Hold right-click with a soul item to let the soul devour it. 50 soul items, all passive powers.
- Soul energy 0-10 from PvP. At 0 the player becomes a soulless body (no powers, less health) until Summoning Incense is used.
- 9 titles earned by deeds. One holder per server, killing the holder takes the title.

Build: `./gradlew build` (needs access to maven.fabricmc.net), or the GitHub Actions workflow
`.github/workflows/sacred-souls-build.yml`, which uploads the jar as an artifact.

Install the jar plus Fabric API on both the server and every client (the mod has a screen and a key binding).

Not in this version (phase 2): the shrine structure and the awakening ritual, so the "ผู้สืบทอด" title cannot be earned yet.
