// Package netports parses and validates the edge ports a scan should probe.
//
// It lives in its own package so the CLI, the gomobile bridge and the scanner
// all share one parser and one definition of which ports are worth trying.
package netports

import (
	"fmt"
	"strconv"
	"strings"
)

// CloudflareTLS lists the ports Cloudflare terminates TLS on for a proxied
// hostname. Probing all of them multiplies the work, so callers opt in.
var CloudflareTLS = []int{443, 2053, 2083, 2087, 2096, 8443}

// CloudflareTLSCSV renders the default set as a comma-separated string, so a UI
// can build its port chips without duplicating the list.
func CloudflareTLSCSV() string {
	parts := make([]string, len(CloudflareTLS))
	for i, p := range CloudflareTLS {
		parts[i] = strconv.Itoa(p)
	}
	return strings.Join(parts, ",")
}

// Parse turns a comma-separated list into a port slice.
//
// An empty list falls back to the single port given, which is the common case
// when a config link names one port. The literal "all" expands to every
// Cloudflare TLS port. Duplicates are dropped while preserving order.
func Parse(list string, fallback int) ([]int, error) {
	list = strings.TrimSpace(list)
	if list == "" {
		if !valid(fallback) {
			return nil, fmt.Errorf("port %d is out of range", fallback)
		}
		return []int{fallback}, nil
	}
	if strings.EqualFold(list, "all") {
		out := make([]int, len(CloudflareTLS))
		copy(out, CloudflareTLS)
		return out, nil
	}

	seen := make(map[int]struct{})
	var out []int
	for _, field := range strings.Split(list, ",") {
		field = strings.TrimSpace(field)
		if field == "" {
			continue
		}
		p, err := strconv.Atoi(field)
		if err != nil {
			return nil, fmt.Errorf("%q is not a valid port number", field)
		}
		if !valid(p) {
			return nil, fmt.Errorf("port %d is out of range", p)
		}
		if _, dup := seen[p]; dup {
			continue
		}
		seen[p] = struct{}{}
		out = append(out, p)
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("no usable ports were given")
	}
	return out, nil
}

// Normalise drops invalid and duplicate ports, falling back to 443 when nothing
// usable remains. Used by the scanner to sanitise whatever it is handed.
func Normalise(in []int) []int {
	seen := make(map[int]struct{}, len(in))
	out := make([]int, 0, len(in))
	for _, p := range in {
		if !valid(p) {
			continue
		}
		if _, dup := seen[p]; dup {
			continue
		}
		seen[p] = struct{}{}
		out = append(out, p)
	}
	if len(out) == 0 {
		return []int{443}
	}
	return out
}

func valid(p int) bool { return p > 0 && p <= 65535 }
