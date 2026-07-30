package tui

import (
	"fmt"
	"os"
	"testing"
)

// TestDumpFrames is a visual check, not an assertion: run with -run DumpFrames -v
// to see the real frames the terminal will show.
func TestDumpFrames(t *testing.T) {
	if os.Getenv("DUMP_FRAMES") == "" {
		t.Skip("set DUMP_FRAMES=1 to print frames")
	}
	m := New("v1.6.0")
	fmt.Println("========== HOME ==========")
	fmt.Println(m.View())

	m.page = pageSetup
	m.rowIdx = 5
	fmt.Println("========== SETUP ==========")
	fmt.Println(m.View())

	m.set.dlIdx = 8
	m.set.countIdx = 5
	m.set.portIdx = 3
	m.rowIdx = 0
	fmt.Println("========== SETUP (wide sweep, 20MB) ==========")
	fmt.Println(m.View())

	m2 := New("v1.6.0")
	m2.page = pageResults
	m2.report = fakeReport()
	fmt.Println("========== RESULTS ==========")
	fmt.Println(m2.View())

	m2.page = pageDetail
	m2.detail = m2.report.Candidates[0]
	fmt.Println("========== DETAIL ==========")
	fmt.Println(m2.View())

	m2.page = pageAbout
	fmt.Println("========== ABOUT ==========")
	fmt.Println(m2.View())
}
