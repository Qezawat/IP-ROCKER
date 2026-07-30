package tui

import "github.com/charmbracelet/lipgloss"

// The palette is deliberately small and high contrast: a terminal on a phone is
// narrow and often on a theme the user did not choose, so colour carries meaning
// (green clean, amber caution, red bad) rather than decoration.
var (
	colAccent = lipgloss.Color("39")  // cyan
	colGood   = lipgloss.Color("42")  // green
	colWarn   = lipgloss.Color("214") // amber
	colBad    = lipgloss.Color("203") // red
	colDim    = lipgloss.Color("244")
	colText   = lipgloss.Color("252")

	styTitle    = lipgloss.NewStyle().Foreground(colAccent).Bold(true)
	styAccent   = lipgloss.NewStyle().Foreground(colAccent)
	styDim      = lipgloss.NewStyle().Foreground(colDim)
	styText     = lipgloss.NewStyle().Foreground(colText)
	stySelected = lipgloss.NewStyle().Foreground(colAccent).Bold(true)
	styPillOn   = lipgloss.NewStyle().Foreground(lipgloss.Color("0")).Background(colAccent).Bold(true)
	styPillOff  = lipgloss.NewStyle().Foreground(colDim)
	styGood     = lipgloss.NewStyle().Foreground(colGood)
	styWarn     = lipgloss.NewStyle().Foreground(colWarn)
	styBad      = lipgloss.NewStyle().Foreground(colBad)
	styHint     = lipgloss.NewStyle().Foreground(colDim).Italic(true)
	styHead     = lipgloss.NewStyle().Foreground(colAccent).Bold(true)
)

// banner is block text rather than figlet output so it stays readable in a
// phone terminal, which is where this tool actually runs.
//
// The wide form needs 64 columns. A 40-column terminal is common on a phone in
// portrait, so below that width the two words stack instead of wrapping, which
// would shear the glyphs in half.
func banner(width int) string {
	if width > 0 && width < 66 {
		return styTitle.Render(
			"\n" +
				"  ██ ██████\n" +
				"  ██ ██   ██\n" +
				"  ██ ██████\n" +
				"  ██ ██\n" +
				"  ██ ██   ─── R O C K E R")
	}
	return styTitle.Render(
		"\n" +
			"  ██ ██████      ██████   ██████  ██████ ██   ██ ███████ ██████\n" +
			"  ██ ██   ██     ██   ██ ██    ██ ██     ██  ██  ██      ██   ██\n" +
			"  ██ ██████  ─── ██████  ██    ██ ██     █████   █████   ██████\n" +
			"  ██ ██          ██   ██ ██    ██ ██     ██  ██  ██      ██   ██\n" +
			"  ██ ██          ██   ██  ██████  ██████ ██   ██ ███████ ██   ██")
}
