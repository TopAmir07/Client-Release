# Neues Update veröffentlichen

1. Änderungen committen und pushen.
2. Tag setzen und pushen (die Zahl ist die neue Versionsnummer):
   git tag v1.3.0
   git push origin v1.3.0
3. Auf GitHub unter "Actions" läuft "Release" (ca. 5-10 Minuten). Danach liegt unter "Releases"
   TopClient.exe + TopClient.exe.sha256.
4. Im Launcher: Einstellungen > Erweitert > Update source = "TopAmir07/Client-Release",
   dann "Update launcher". Der Launcher tauscht sich aus und kopiert beim nächsten PLAY
   den neuen Mod in die Profile.

Hinweise:
- Die Versionsnummer muss höher sein als die aktuelle (AppVersion in topclient/main.go).
- Der Mod steckt in der Launcher-Exe. Ein Mod-Fix braucht deshalb immer ein neues Release.
