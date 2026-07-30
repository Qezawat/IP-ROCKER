package reputation

import (
	"encoding/json"
	"net"
	"testing"
)

func parseIPs(t *testing.T, addrs ...string) []net.IP {
	t.Helper()
	out := make([]net.IP, 0, len(addrs))
	for _, a := range addrs {
		ip := net.ParseIP(a)
		if ip == nil {
			t.Fatalf("test setup: %q is not a valid IP", a)
		}
		out = append(out, ip)
	}
	return out
}

func TestParseAbuserScore(t *testing.T) {
	cases := map[string]float64{
		"0.0076 (Low)":      0.0076,
		"0.1289 (High)":     0.1289,
		"0.0153 (Elevated)": 0.0153,
		"":                  0,
		"garbage":           0,
		"0.5":               0.5,
	}
	for in, want := range cases {
		if got := parseAbuserScore(in); got != want {
			t.Errorf("parseAbuserScore(%q) = %v, want %v", in, got, want)
		}
	}
}

// The provider returns is_crawler as false for most addresses but as the string
// "CloudflareBot" for Cloudflare's own edges, which must not count as a flag.
func TestCrawlerFlag(t *testing.T) {
	cases := []struct {
		raw  string
		want bool
	}{
		{`false`, false},
		{`true`, true},
		{`"CloudflareBot"`, false},
		{`""`, false},
		{`"SomeScraper"`, true},
	}
	for _, c := range cases {
		if got := crawlerFlag(json.RawMessage(c.raw)); got != c.want {
			t.Errorf("crawlerFlag(%s) = %v, want %v", c.raw, got, c.want)
		}
	}
}

func TestScoreVerdicts(t *testing.T) {
	// A clean Cloudflare edge: datacenter only, negligible owner abuse.
	clean := &Info{IsDatacenter: true, CompanyAbuse: 0.0076, ASNAbuse: 0.0153}
	clean.score()
	if clean.Verdict != VerdictClean {
		t.Errorf("clean edge scored %v (risk %.1f), want clean", clean.Verdict, clean.RiskPercent)
	}

	// Being a datacenter must not by itself penalise an address, since every
	// Cloudflare edge is one.
	if clean.RiskPercent > 12 {
		t.Errorf("datacenter-only address got risk %.1f, expected low", clean.RiskPercent)
	}

	// The address from the user's own screenshot: proxy + abuser flagged.
	dirty := &Info{IsDatacenter: true, IsProxy: true, IsAbuser: true, CompanyAbuse: 0.0076, ASNAbuse: 0.0153}
	dirty.score()
	if dirty.Verdict != VerdictDirty {
		t.Errorf("proxy+abuser address scored %v (risk %.1f), want dirty", dirty.Verdict, dirty.RiskPercent)
	}

	// A VPN-only flag is a downgrade, not a disqualification.
	vpn := &Info{IsDatacenter: true, IsVPN: true, CompanyAbuse: 0.0076}
	vpn.score()
	if vpn.Verdict != VerdictCaution && vpn.Verdict != VerdictClean {
		t.Errorf("vpn-only address scored %v, want clean or caution", vpn.Verdict)
	}
}

func TestParseRecordRealPayload(t *testing.T) {
	// Trimmed copy of an actual provider response.
	raw := `{
	  "ip": "104.28.162.210",
	  "is_bogon": false,
	  "is_datacenter": true,
	  "is_tor": false,
	  "is_proxy": true,
	  "is_vpn": false,
	  "is_abuser": true,
	  "is_crawler": false,
	  "company": {"name": "Cloudflare, Inc.", "abuser_score": "0.0076 (Low)"},
	  "asn": {"asn": 13335, "org": "Cloudflare, Inc.", "abuser_score": "0.0153 (Elevated)", "route": "104.28.162.0/24"},
	  "location": {"country": "Germany", "state": "Hesse", "city": "Dreieich"}
	}`

	info := parseRecord("104.28.162.210", json.RawMessage(raw))
	if info.Err != "" {
		t.Fatalf("unexpected parse error: %s", info.Err)
	}
	if !info.IsProxy || !info.IsAbuser {
		t.Errorf("flags lost: proxy=%v abuser=%v", info.IsProxy, info.IsAbuser)
	}
	if info.ASN != 13335 {
		t.Errorf("ASN = %d, want 13335", info.ASN)
	}
	if info.CompanyAbuse != 0.0076 {
		t.Errorf("CompanyAbuse = %v, want 0.0076", info.CompanyAbuse)
	}
	if info.City != "Dreieich" {
		t.Errorf("City = %q, want Dreieich", info.City)
	}
	if info.Verdict != VerdictDirty {
		t.Errorf("verdict = %v, want dirty for a proxy+abuser address", info.Verdict)
	}
}

// An address that could not be rated must never be reported as clean, because
// treating a provider outage as a pass would hand the user risky addresses.
func TestUnratedIsNeverClean(t *testing.T) {
	info := &Info{IP: "1.2.3.4", Err: "provider unreachable"}
	if info.CleanEnough(false) || info.CleanEnough(true) {
		t.Error("an unrated address was reported as clean")
	}
}

func TestDisabledClientMarksEveryAddress(t *testing.T) {
	c := NewClient()
	c.Disabled = true
	m, err := c.LookupBulk(t.Context(), parseIPs(t, "1.1.1.1", "8.8.8.8"))
	if err != nil {
		t.Fatalf("LookupBulk returned error: %v", err)
	}
	if len(m) != 2 {
		t.Fatalf("got %d records, want 2", len(m))
	}
	for ip, info := range m {
		if info.Err == "" {
			t.Errorf("%s: expected an error marker when lookups are disabled", ip)
		}
		if info.CleanEnough(false) {
			t.Errorf("%s: reported clean while lookups were disabled", ip)
		}
	}
}
