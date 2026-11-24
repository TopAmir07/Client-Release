package main

import "testing"

func TestFabricSupports(t *testing.T) {
	for rt, want := range map[string]bool{
		"2015-12-03T09:24:39+00:00": false, // 1.8.9
		"2017-09-18T08:39:46+00:00": false, // 1.12.2
		"2018-10-24T12:00:00+00:00": true,  // 18w43b
		"2019-04-23T14:52:44+00:00": true,  // 1.14
		"2025-12-01T00:00:00+00:00": true,
		"":                          true,
	} {
		if fabricSupports(rt) != want {
			t.Errorf("%s: want %v", rt, want)
		}
	}
}
