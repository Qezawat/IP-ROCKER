package mobile

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"net/url"
	"strconv"
	"strings"
)

// ConfigLink holds the transport settings extracted from a proxy config URL.
//
// Only the fields that affect how an edge address is probed are kept. Secrets
// such as the UUID or password are deliberately not retained, because the
// scanner never needs them and keeping them out of memory avoids leaking them
// into logs or crash reports.
type ConfigLink struct {
	Protocol  string `json:"protocol"`
	SNI       string `json:"sni"`
	Host      string `json:"host"`
	Path      string `json:"path"`
	Port      int    `json:"port"`
	Transport string `json:"transport"`
	Security  string `json:"security"`
	// Address is the original hostname or address from the link, used only to
	// fall back to when no SNI or Host header is specified.
	Address string `json:"address"`
}

// ParseConfigLink accepts vless://, trojan:// or a base64 vmess:// link and
// extracts the settings that determine how a Cloudflare edge should be probed.
func ParseConfigLink(raw string) (*ConfigLink, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, fmt.Errorf("config link was empty")
	}
	// Some panels wrap links in quotes or prepend a label.
	raw = strings.Trim(raw, "\"' ")

	switch {
	case strings.HasPrefix(raw, "vmess://"):
		return parseVMess(raw)
	case strings.HasPrefix(raw, "vless://"), strings.HasPrefix(raw, "trojan://"):
		return parseURLStyle(raw)
	default:
		return nil, fmt.Errorf("unsupported link: expected vless://, trojan:// or vmess://")
	}
}

// parseURLStyle handles vless:// and trojan://, which share a URL layout of
// scheme://credential@host:port?params#label.
func parseURLStyle(raw string) (*ConfigLink, error) {
	u, err := url.Parse(raw)
	if err != nil {
		return nil, fmt.Errorf("malformed link: %w", err)
	}

	out := &ConfigLink{Protocol: u.Scheme}

	host := u.Hostname()
	out.Address = host
	if p := u.Port(); p != "" {
		port, err := strconv.Atoi(p)
		if err != nil || port <= 0 || port > 65535 {
			return nil, fmt.Errorf("link contained an invalid port %q", p)
		}
		out.Port = port
	}

	q := u.Query()
	out.Transport = firstNonEmpty(q.Get("type"), q.Get("network"), q.Get("net"))
	out.Security = firstNonEmpty(q.Get("security"), q.Get("tls"))
	out.SNI = firstNonEmpty(q.Get("sni"), q.Get("peer"), q.Get("servername"))
	out.Host = firstNonEmpty(q.Get("host"), q.Get("obfsParam"))
	out.Path = normalisePath(q.Get("path"))

	// Reality links carry pbk/sid parameters. They cannot work behind a
	// Cloudflare proxy, because the proxy terminates TLS and Reality requires
	// the handshake to reach the origin untouched. Saying so explicitly is more
	// useful than silently producing a scan that cannot help.
	if q.Get("pbk") != "" || strings.EqualFold(out.Security, "reality") {
		return nil, fmt.Errorf("this is a REALITY config, which cannot be fronted by Cloudflare: " +
			"the CDN terminates TLS, so the REALITY handshake never reaches your server. " +
			"Use a WebSocket or XHTTP config over Cloudflare instead")
	}

	applyFallbacks(out)
	if out.SNI == "" && out.Host == "" {
		return nil, fmt.Errorf("link has no SNI, Host or hostname to probe with")
	}
	return out, nil
}

// parseVMess handles the base64-encoded JSON payload used by vmess:// links.
func parseVMess(raw string) (*ConfigLink, error) {
	payload := strings.TrimPrefix(raw, "vmess://")
	if idx := strings.IndexByte(payload, '#'); idx >= 0 {
		payload = payload[:idx]
	}
	decoded, err := decodeBase64Flexible(payload)
	if err != nil {
		return nil, fmt.Errorf("vmess payload is not valid base64: %w", err)
	}

	// vmess JSON uses string-or-number for port, so decode loosely.
	var m map[string]any
	if err := json.Unmarshal(decoded, &m); err != nil {
		return nil, fmt.Errorf("vmess payload is not valid JSON: %w", err)
	}

	out := &ConfigLink{Protocol: "vmess"}
	out.Address = asString(m["add"])
	out.SNI = firstNonEmpty(asString(m["sni"]), asString(m["peer"]))
	out.Host = asString(m["host"])
	out.Path = normalisePath(asString(m["path"]))
	out.Transport = asString(m["net"])
	out.Security = asString(m["tls"])
	if p := asString(m["port"]); p != "" {
		if port, err := strconv.Atoi(p); err == nil && port > 0 && port <= 65535 {
			out.Port = port
		}
	}

	applyFallbacks(out)
	if out.SNI == "" && out.Host == "" {
		return nil, fmt.Errorf("vmess link has no SNI, Host or address to probe with")
	}
	return out, nil
}

// applyFallbacks fills SNI from Host and vice versa, and defaults the port.
// A CDN-fronted config normally uses the same hostname for both, so deriving
// one from the other produces a probe that matches real traffic.
func applyFallbacks(c *ConfigLink) {
	if c.SNI == "" {
		c.SNI = firstNonEmpty(c.Host, hostnameOnly(c.Address))
	}
	if c.Host == "" {
		c.Host = firstNonEmpty(c.SNI, hostnameOnly(c.Address))
	}
	if c.Port == 0 {
		c.Port = 443
	}
	if c.Path == "" && isWebSocket(c.Transport) {
		// A WebSocket config with no path uses the root path.
		c.Path = "/"
	}
}

func isWebSocket(t string) bool {
	switch strings.ToLower(strings.TrimSpace(t)) {
	case "ws", "websocket", "httpupgrade":
		return true
	}
	return false
}

// hostnameOnly drops a literal IP, because probing many edges with an IP as the
// SNI is meaningless; only a hostname is useful as a TLS server name.
func hostnameOnly(addr string) string {
	addr = strings.TrimSpace(addr)
	if addr == "" {
		return ""
	}
	if net.ParseIP(strings.Trim(addr, "[]")) != nil {
		return ""
	}
	return addr
}

func normalisePath(p string) string {
	p = strings.TrimSpace(p)
	if p == "" {
		return ""
	}
	// Panels frequently URL-encode the path, sometimes twice.
	for i := 0; i < 2; i++ {
		if dec, err := url.QueryUnescape(p); err == nil && dec != p {
			p = dec
			continue
		}
		break
	}
	if !strings.HasPrefix(p, "/") {
		p = "/" + p
	}
	return p
}

// decodeBase64Flexible copes with URL-safe alphabets and missing padding, both
// of which appear in links copied out of panels and chat apps.
func decodeBase64Flexible(s string) ([]byte, error) {
	s = strings.TrimSpace(s)
	s = strings.ReplaceAll(s, "-", "+")
	s = strings.ReplaceAll(s, "_", "/")
	if pad := len(s) % 4; pad != 0 {
		s += strings.Repeat("=", 4-pad)
	}
	return base64.StdEncoding.DecodeString(s)
}

func asString(v any) string {
	switch t := v.(type) {
	case string:
		return strings.TrimSpace(t)
	case float64:
		// JSON numbers decode as float64; ports arrive this way.
		return strconv.FormatInt(int64(t), 10)
	case bool:
		if t {
			return "true"
		}
		return "false"
	default:
		return ""
	}
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if s := strings.TrimSpace(v); s != "" {
			return s
		}
	}
	return ""
}

func parseIP(s string) net.IP {
	return net.ParseIP(strings.TrimSpace(s))
}

// ParseConfigLinkJSON is the gomobile-friendly wrapper: it returns the parsed
// settings as JSON so Kotlin can display what was detected.
func ParseConfigLinkJSON(raw string) (string, error) {
	cfg, err := ParseConfigLink(raw)
	if err != nil {
		return "", err
	}
	b, err := json.Marshal(cfg)
	if err != nil {
		return "", err
	}
	return string(b), nil
}
