//go:build windows

package main

import (
	"syscall"
	"unsafe"
)

// tuneGameProcess makes Minecraft run smoother while it is open. It only changes the game
// process the launcher just started, for as long as that process lives: nothing is saved
// on the PC and nothing else is touched.
//
//   - Windows "efficiency mode" (power throttling) is switched off for the game, so Windows
//     doesn't slow it down while it's in the background. That slow-down is what makes the
//     game hang for a few seconds when you tab back in after a while.
//   - A soft minimum working set: Windows tries to keep the game's memory in RAM instead
//     of paging it out while you're tabbed out. "Soft" means Windows can still take it
//     back if the PC runs low on memory.
//   - Above-normal priority (Settings > Performance), so background apps get in the way less.
func tuneGameProcess(pid int, ramMB int, priority string) {
	const (
		processSetInformation   = 0x0200
		processSetQuota         = 0x0100
		processQueryLimitedInfo = 0x1000
		aboveNormal             = 0x00008000
		normal                  = 0x00000020
		processPowerThrottling  = 4
		throttleExecutionSpeed  = 0x1
		throttleIgnoreTimerRes  = 0x4
		hardMinDisable          = 0x2
		hardMaxDisable          = 0x8
	)
	h, _, err := kernel32.NewProc("OpenProcess").Call(processSetInformation|processSetQuota|processQueryLimitedInfo, 0, uintptr(pid))
	if h == 0 {
		logf("game tune: open process: %v", err)
		return
	}
	defer syscall.CloseHandle(syscall.Handle(h))

	// 1. no power throttling / efficiency mode for the game
	state := struct{ Version, ControlMask, StateMask uint32 }{1, throttleExecutionSpeed | throttleIgnoreTimerRes, 0}
	if p := kernel32.NewProc("SetProcessInformation"); p.Find() == nil {
		if r, _, err := p.Call(h, processPowerThrottling, uintptr(unsafe.Pointer(&state)), unsafe.Sizeof(state)); r == 0 {
			logf("game tune: power throttling: %v", err)
		}
	}

	// 2. priority
	cls := uintptr(normal)
	if priority != "normal" {
		cls = aboveNormal
	}
	if r, _, err := kernel32.NewProc("SetPriorityClass").Call(h, cls); r == 0 {
		logf("game tune: priority: %v", err)
	}

	// 3. soft minimum working set (keep the game in RAM while tabbed out)
	total := totalRAMMB()
	minMB := ramMB * 6 / 10
	if lim := total * 3 / 10; minMB > lim {
		minMB = lim
	}
	if minMB >= 512 {
		minB := uintptr(minMB) << 20
		maxB := uintptr(total) << 20
		if p := kernel32.NewProc("SetProcessWorkingSetSizeEx"); p.Find() == nil {
			if r, _, err := p.Call(h, minB, maxB, hardMinDisable|hardMaxDisable); r == 0 {
				logf("game tune: working set: %v", err)
			}
		}
	}
}
