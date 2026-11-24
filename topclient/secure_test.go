package main

import (
	"os"
	"strings"
	"testing"
)

func TestRedactSecrets(t *testing.T) {
	jwt := "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
	for _, in := range []string{
		"token " + jwt + " end",
		"refresh=M.C540_BAY.0.U.-Cq7fPmmQyGq8pHn7r8x1f2rXxLmQ3uVwZq",
		`{"accessToken":"abcdefghijklmnopqrstuvwxyz0123456789"}`,
		"--accessToken " + jwt,
	} {
		out := redactSecrets(in)
		if strings.Contains(out, "dozjgNry") || strings.Contains(out, "Cq7fPmm") || strings.Contains(out, "abcdefghijklmnop") {
			t.Fatalf("not hidden: %s", out)
		}
	}
	if s := "[Render thread/INFO]: Setting user: BestOfAfrika"; redactSecrets(s) != s {
		t.Fatal("normal log line changed")
	}
}

func TestArgFile(t *testing.T) {
	dataDir = t.TempDir()
	f, err := writeArgFile([]string{"-Xmx4096M", `C:\Users\A B\x.jar`, `say "hi"`})
	if err != nil {
		t.Fatal(err)
	}
	b, _ := os.ReadFile(f)
	want := "\"-Xmx4096M\"\n\"C:\\\\Users\\\\A B\\\\x.jar\"\n\"say \\\"hi\\\"\"\n"
	if string(b) != want {
		t.Fatalf("got %q", b)
	}
}
