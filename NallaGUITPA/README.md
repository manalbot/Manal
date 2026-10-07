# NallaGUITPA

GUI-only TPA system for Paper 1.21.11 / Java 21.

Features:
- Dynamic online-player heads (up to 45 per page)
- TPA mode and TPA-HERE mode
- Accept/Deny request GUI
- 60-second request timeout
- 5-second sender cooldown
- Previous/Next page
- No chat argument is needed to choose a player
- Works with DeluxeMenus as a phone button

## Build
Install Maven, then run:
`mvn clean package`

JAR output:
`target/NallaGUITPA-1.0.0.jar`

## Install
1. Stop the server.
2. Copy the JAR into `plugins/`.
3. Start the server.
4. Copy/edit `plugins/NallaGUITPA/config.yml` if desired.
5. In DeluxeMenus, add the supplied phone item:
   `[player] guitpa`
6. If you are replacing DonutTPA, remove/disable DonutTPA to avoid duplicate TPA systems.

The player-facing flow is Phone -> TPA -> player head -> request -> Accept/Deny.
