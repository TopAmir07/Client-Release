package main

import (
	"encoding/json"
	"strings"
	"testing"
)

const vanillaSample = `{
 "id":"1.21.4","mainClass":"net.minecraft.client.main.Main","assets":"19","releaseTime":"2024-12-03T10:12:57+00:00",
 "javaVersion":{"component":"java-runtime-delta","majorVersion":21},
 "arguments":{"game":["--username","${auth_player_name}","--version","${version_name}","--gameDir","${game_directory}","--assetsDir","${assets_root}","--assetIndex","${assets_index_name}","--uuid","${auth_uuid}","--accessToken","${auth_access_token}","--clientId","${clientid}","--xuid","${auth_xuid}","--userType","${user_type}","--versionType","${version_type}",
   {"rules":[{"action":"allow","features":{"is_demo_user":true}}],"value":"--demo"},
   {"rules":[{"action":"allow","features":{"has_custom_resolution":true}}],"value":["--width","${resolution_width}","--height","${resolution_height}"]},
   {"rules":[{"action":"allow","features":{"has_quick_plays_support":true}}],"value":["--quickPlayPath","${quickPlayPath}"]}],
  "jvm":[{"rules":[{"action":"allow","os":{"name":"osx"}}],"value":["-XstartOnFirstThread"]},
   {"rules":[{"action":"allow","os":{"name":"windows"}}],"value":"-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump"},
   {"rules":[{"action":"allow","os":{"arch":"x86"}}],"value":"-Xss1M"},
   "-Djava.library.path=${natives_directory}","-Djna.tmpdir=${natives_directory}","-Dminecraft.launcher.brand=${launcher_name}","-cp","${classpath}"]},
 "libraries":[
  {"name":"org.ow2.asm:asm:9.3","downloads":{"artifact":{"path":"org/ow2/asm/asm/9.3/asm-9.3.jar","url":"https://libraries.minecraft.net/x","sha1":"a","size":1}}},
  {"name":"org.lwjgl:lwjgl:3.3.3:natives-windows","downloads":{"artifact":{"path":"org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar","url":"https://libraries.minecraft.net/y","sha1":"b","size":1}},"rules":[{"action":"allow","os":{"name":"windows"}}]},
  {"name":"org.lwjgl:lwjgl:3.3.3:natives-macos","downloads":{"artifact":{"path":"m.jar","url":"https://libraries.minecraft.net/z","sha1":"c","size":1}},"rules":[{"action":"allow","os":{"name":"osx"}}]}
 ]}`

const fabricSample = `{"id":"fabric-loader-0.16.9-1.21.4","inheritsFrom":"1.21.4","mainClass":"net.fabricmc.loader.impl.launch.knot.KnotClient",
 "arguments":{"game":[],"jvm":["-DFabricMcEmu= net.minecraft.client.main.Main "]},
 "libraries":[{"name":"org.ow2.asm:asm:9.7.1","url":"https://maven.fabricmc.net/","sha1":"f8","size":2},{"name":"net.fabricmc:fabric-loader:0.16.9","url":"https://maven.fabricmc.net/"}]}`

func TestArgsAndMerge(t *testing.T) {
	var v, f VersionJSON
	if err := json.Unmarshal([]byte(vanillaSample), &v); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal([]byte(fabricSample), &f); err != nil {
		t.Fatal(err)
	}
	m := merge(&f, &v)
	if m.MainClass != f.MainClass || m.ID != f.ID || m.AssetIndex != v.AssetIndex {
		t.Fatal("merge basics")
	}
	asm := 0
	for _, l := range m.Libraries {
		if strings.HasPrefix(l.Name, "org.ow2.asm:asm:") {
			asm++
			if l.Name != "org.ow2.asm:asm:9.7.1" {
				t.Fatal("fabric asm should win")
			}
		}
	}
	if asm != 1 {
		t.Fatalf("asm count %d", asm)
	}
	if mavenPath("net.fabricmc:fabric-loader:0.16.9") != "net/fabricmc/fabric-loader/0.16.9/fabric-loader-0.16.9.jar" {
		t.Fatal("maven path")
	}
	if mavenPath("a.b:c:1:natives-windows@zip") != "a/b/c/1/c-1-natives-windows.zip" {
		t.Fatal("maven classifier")
	}
	vars := map[string]string{"auth_player_name": "Steve", "version_name": m.ID, "classpath": "A;B", "natives_directory": "N", "resolution_width": "800", "resolution_height": "600", "launcher_name": "TopClient"}
	game := expandArgs(m.Arguments.Game, map[string]bool{"has_custom_resolution": true}, vars)
	jvm := expandArgs(m.Arguments.JVM, nil, vars)
	gs, js := strings.Join(game, " "), strings.Join(jvm, " ")
	t.Log("GAME:", gs)
	t.Log("JVM:", js)
	if strings.Contains(gs, "--demo") || strings.Contains(gs, "quickPlay") {
		t.Fatal("demo/quickplay leaked")
	}
	if !strings.Contains(gs, "--width 800 --height 600") || !strings.Contains(gs, "--username Steve") {
		t.Fatal("game args")
	}
	if strings.Contains(js, "-Xss1M") || strings.Contains(js, "XstartOnFirstThread") {
		t.Fatal("wrong-os jvm args")
	}
	if !strings.Contains(js, "-cp A;B") || !strings.Contains(js, "FabricMcEmu") {
		t.Fatal("jvm args")
	}
	// natives classifier filter
	if !nativeClassifierOK("natives-"+osName()) || nativeClassifierOK("natives-"+osName()+"-x86") {
		t.Fatal("natives filter")
	}
}

func TestSandbox(t *testing.T) {
	dataDir = t.TempDir()
	for _, bad := range []string{"../x", "a/../../x", "/etc/passwd", "C:/Windows", "..\\x"} {
		if _, err := safeJoin(dataDir, bad); err == nil {
			t.Fatalf("safeJoin allowed %q", bad)
		}
	}
	if _, err := safeJoin(dataDir, "mods/ok.jar"); err != nil {
		t.Fatal(err)
	}
	if insideData("/tmp") {
		t.Fatal("insideData")
	}
	if checkTrusted("https", "evil.com") == nil || checkTrusted("http", "cdn.modrinth.com") == nil || checkTrusted("https", "cdn.modrinth.com") != nil {
		t.Fatal("trust list")
	}
}

func TestMCMatches(t *testing.T) {
	cases := map[[2]string]bool{
		{"~1.21.11", "1.21.11"}: true, {"~1.21.11", "1.21.12"}: true, {"~1.21.11", "1.21.10"}: false,
		{"~1.21.11", "1.22"}: false, {"~1.21.11", "26.1"}: false, {"*", "1.8.9"}: true, {">=1.20 <1.21", "1.20.4"}: true,
	}
	for k, want := range cases {
		if mcMatches(k[0], k[1]) != want {
			t.Errorf("%v want %v", k, want)
		}
	}
}
