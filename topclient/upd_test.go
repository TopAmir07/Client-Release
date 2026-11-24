package main

import "testing"

func TestNewer(t *testing.T) {
	cases := []struct {
		a, b string
		want bool
	}{{"2.4.0", "2.3.0", true}, {"v2.10.0", "2.9.9", true}, {"2.3.0", "2.3.0", false}, {"2.3", "2.3.1", false}, {"3.0.0-beta", "2.9", true}}
	for _, c := range cases {
		if newerThan(c.a, c.b) != c.want {
			t.Fatalf("%s > %s should be %v", c.a, c.b, c.want)
		}
	}
	settings = map[string]any{"updateRepo": "https://github.com/bloom/TopClient/"}
	if updateRepo() != "bloom/TopClient" {
		t.Fatal(updateRepo())
	}
	settings["updateRepo"] = "../evil"
	if updateRepo() != "" {
		t.Fatal("bad repo accepted")
	}
}

func TestTagFromURL(t *testing.T) {
	m := tagFromURL.FindStringSubmatch("https://github.com/BestOfAfrika/TopClient/releases/tag/v4.3.2")
	if m == nil || m[1] != "v4.3.2" {
		t.Fatal(m)
	}
}
