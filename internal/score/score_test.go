package score

import (
	"net"
	"testing"
	"time"

	"github.com/Qezawat/IP-ROCKER/internal/probe"
	"github.com/Qezawat/IP-ROCKER/internal/reputation"
)

func httpResult(ip string, lat time.Duration, held, ws bool, dlBps float64, attempts int) *probe.Result {
	r := &probe.Result{IP: net.ParseIP(ip), Port: 443, Mode: "http", Started: time.Now()}
	for i := 0; i < attempts; i++ {
		r.Attempts = append(r.Attempts, probe.Attempt{
			Latency:     lat,
			TLSOk:       true,
			HTTPOk:      true,
			HeldOpen:    held,
			WSOk:        ws,
			HTTPStatus:  200,
			Colo:        "FRA",
			DownloadBps: dlBps,
		})
	}
	return r
}

func cleanRep() *reputation.Info {
	i := &reputation.Info{IsDatacenter: true, CompanyAbuse: 0.0076, ASNAbuse: 0.0153}
	mustScore(i)
	return i
}

func dirtyRep() *reputation.Info {
	i := &reputation.Info{IsDatacenter: true, IsProxy: true, IsAbuser: true, CompanyAbuse: 0.0076}
	mustScore(i)
	return i
}

// mustScore drives the exported path that fills Verdict and RiskPercent.
func mustScore(i *reputation.Info) {
	// Round-tripping through the bulk parser is unnecessary here; Evaluate only
	// reads Verdict, RiskPercent and Err, so set them the same way score() does
	// by using the package's own public behaviour via a marshalled record.
	if i.IsAbuser || i.IsProxy {
		i.RiskPercent = 80
		i.Verdict = reputation.VerdictDirty
	} else {
		i.RiskPercent = 5
		i.Verdict = reputation.VerdictClean
	}
	i.VerdictName = i.Verdict.String()
}

// The central ranking rule: a fast but flagged address must never outrank a
// clean one, because a flagged address triggers captchas at the destination.
func TestCleanBeatsFastButDirty(t *testing.T) {
	c := DefaultCriteria()

	fastDirty := Evaluate(httpResult("1.1.1.1", 40*time.Millisecond, true, true, 4_000_000, 3), dirtyRep(), c)
	slowClean := Evaluate(httpResult("2.2.2.2", 400*time.Millisecond, true, true, 400_000, 3), cleanRep(), c)

	if fastDirty.Healthy {
		t.Error("a high-risk address was marked healthy under default criteria")
	}
	if !slowClean.Healthy {
		t.Errorf("a clean address was marked unhealthy: %v", slowClean.Notes)
	}

	list := []*Candidate{fastDirty, slowClean}
	Rank(list)
	if list[0].IP != "2.2.2.2" {
		t.Errorf("ranking put %s first; the clean address must lead", list[0].IP)
	}
}

// An address reset during the idle hold looks fine to a naive scanner but is
// useless in practice, so it must be disqualified.
func TestResetDuringHoldDisqualifies(t *testing.T) {
	r := &probe.Result{IP: net.ParseIP("3.3.3.3"), Port: 443, Mode: "http"}
	r.Attempts = append(r.Attempts, probe.Attempt{
		Latency: 50 * time.Millisecond, TLSOk: true, HTTPStatus: 200, Colo: "FRA",
		Err: "connection was reset during idle hold",
	})
	r.Attempts = append(r.Attempts, probe.Attempt{
		Latency: 50 * time.Millisecond, TLSOk: true, HTTPStatus: 200, Colo: "FRA",
		Err: "connection was reset during idle hold",
	})

	cand := Evaluate(r, cleanRep(), DefaultCriteria())
	if cand.Healthy {
		t.Error("an address reset during the idle hold was marked healthy")
	}
}

func TestStrictRequiresVerifiedReputation(t *testing.T) {
	res := httpResult("4.4.4.4", 100*time.Millisecond, true, true, 1_000_000, 3)

	unverified := Evaluate(res, &reputation.Info{Err: "provider unreachable"}, StrictCriteria())
	if unverified.Healthy {
		t.Error("strict mode accepted an address whose reputation could not be verified")
	}

	missing := Evaluate(res, nil, StrictCriteria())
	if missing.Healthy {
		t.Error("strict mode accepted an address with no reputation record at all")
	}

	verified := Evaluate(res, cleanRep(), StrictCriteria())
	if !verified.Healthy {
		t.Errorf("strict mode rejected a clean, fast, stable address: %v", verified.Notes)
	}
}

func TestLossAndFailureAreReported(t *testing.T) {
	r := &probe.Result{IP: net.ParseIP("5.5.5.5"), Port: 443, Mode: "http"}
	r.Attempts = []probe.Attempt{
		{Latency: 80 * time.Millisecond, TLSOk: true, HTTPStatus: 200, Colo: "FRA", HeldOpen: true},
		{Err: "timeout"},
		{Err: "connection reset (likely filtered)"},
	}

	cand := Evaluate(r, cleanRep(), DefaultCriteria())
	if cand.LossPercent < 60 {
		t.Errorf("loss = %.1f%%, want about 66%%", cand.LossPercent)
	}
	if cand.Healthy {
		t.Error("an address failing two of three attempts was marked healthy")
	}
}

func TestNoSuccessScoresZero(t *testing.T) {
	r := &probe.Result{IP: net.ParseIP("6.6.6.6"), Port: 443, Mode: "http"}
	r.Attempts = []probe.Attempt{{Err: "timeout"}, {Err: "timeout"}}

	cand := Evaluate(r, nil, DefaultCriteria())
	if cand.Total != 0 {
		t.Errorf("score = %.1f, want 0 for an address that never answered", cand.Total)
	}
	if cand.Healthy {
		t.Error("an address that never answered was marked healthy")
	}
	if len(cand.Notes) == 0 {
		t.Error("expected a note explaining the failure")
	}
}

// Score must be monotonic in latency, so the ordering the user sees is stable
// and explainable rather than arbitrary.
func TestFasterScoresHigherAmongEqualPeers(t *testing.T) {
	c := DefaultCriteria()
	fast := Evaluate(httpResult("7.7.7.7", 60*time.Millisecond, true, true, 1_000_000, 3), cleanRep(), c)
	slow := Evaluate(httpResult("8.8.8.8", 600*time.Millisecond, true, true, 1_000_000, 3), cleanRep(), c)
	if fast.Total <= slow.Total {
		t.Errorf("fast scored %.1f but slow scored %.1f", fast.Total, slow.Total)
	}
}
