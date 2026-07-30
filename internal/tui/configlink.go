package tui

import (
	"fmt"

	"github.com/Qezawat/IP-ROCKER/internal/probe"
	"github.com/Qezawat/IP-ROCKER/mobile"
)

// probeConfigFromLink derives the probe settings from a user's own config link.
//
// The parser is reused from the mobile package rather than reimplemented: a
// second copy would drift, and the link formats panels emit are the fiddliest
// part of this tool. Secrets in the link (UUID, password) are dropped by the
// parser and never reach here.
func probeConfigFromLink(raw string, base probe.Config) (probe.Config, error) {
	cfg, err := mobile.ParseConfigLink(raw)
	if err != nil {
		return base, err
	}
	if cfg.SNI != "" {
		base.SNI = cfg.SNI
	}
	if cfg.Host != "" {
		base.Host = cfg.Host
	}
	if cfg.Path != "" {
		base.WebSocketPath = cfg.Path
		// A config that rides WebSocket is worthless on an edge that refuses the
		// upgrade, so verifying it stops being optional.
		base.RequireWebSocket = true
	}
	if cfg.Port > 0 {
		base.Port = cfg.Port
	}
	if base.SNI == "" && base.Host == "" && base.WebSocketPath == "" {
		return base, fmt.Errorf("config link contained no usable TLS or transport settings")
	}
	return base, nil
}

// linkSummary describes in one line what a link contributed, for the setup page.
func linkSummary(raw string) string {
	cfg, err := mobile.ParseConfigLink(raw)
	if err != nil {
		return "unusable link: " + err.Error()
	}
	out := cfg.Protocol
	if cfg.SNI != "" {
		out += "  SNI " + cfg.SNI
	}
	if cfg.Host != "" && cfg.Host != cfg.SNI {
		out += "  Host " + cfg.Host
	}
	if cfg.Path != "" {
		out += "  path " + cfg.Path
	}
	if cfg.Port > 0 {
		out += fmt.Sprintf("  port %d", cfg.Port)
	}
	return out
}
