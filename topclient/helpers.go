package main

import (
	"regexp"
	"errors"
	"strings"
	"unicode/utf8"
)

func settingBool(k string, def bool) bool {
	stateMu.Lock()
	defer stateMu.Unlock()
	if v, ok := settings[k].(bool); ok {
		return v
	}
	return def
}

func cleanText(s string, max int) string {
	s = strings.Map(func(r rune) rune {
		if r < 32 || r == 0x7f || (r >= 0x202a && r <= 0x202e) || (r >= 0x2066 && r <= 0x2069) {
			return -1 // no control or text-direction tricks
		}
		return r
	}, strings.TrimSpace(s))
	for utf8.RuneCountInString(s) > max {
		r := []rune(s)
		s = string(r[:max])
	}
	return s
}

// TopClient keeps no usage statistics.
func recordStat(key string, n int64) {}

// TopClient is Java Edition only.
var errBedrockMissing = errors.New("Bedrock isn't supported")

func isBedrock(p *Profile) bool { return false }


var hostRe = regexp.MustCompile(`^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$`)

// TopClient has no built-in background video: the design has its own.
func installDefaultBackground() {}
