// Package score turns raw probe measurements plus reputation data into a single
// ranking figure.
//
// The ordering rule is deliberate: an address that is fast but flagged as an
// abuse source ranks below an address that is clean and merely adequate,
// because a flagged address produces captchas and blocks at the destination
// even when the tunnel itself is quick.
package score

import (
	"math"
	"sort"
	"time"

	"github.com/Qezawat/IP-ROCKER/internal/probe"
	"github.com/Qezawat/IP-ROCKER/internal/reputation"
)

// Weights control how much each dimension contributes. They sum to 1.0.
type Weights struct {
	Reputation float64
	Latency    float64
	Stability  float64
	Download   float64
	Upload     float64
}

// DefaultWeights favour cleanliness first, then responsiveness, then raw speed.
func DefaultWeights() Weights {
	return Weights{
		Reputation: 0.35,
		Latency:    0.25,
		Stability:  0.20,
		Download:   0.15,
		Upload:     0.05,
	}
}

// Candidate is one scored address, ready for display or export.
type Candidate struct {
	IP   string `json:"ip"`
	Port int    `json:"port"`

	AvgLatency  time.Duration `json:"-"`
	AvgLatencyMs float64      `json:"avg_latency_ms"`
	MinLatencyMs float64      `json:"min_latency_ms"`
	JitterMs     float64      `json:"jitter_ms"`
	LossPercent  float64      `json:"loss_percent"`

	DownloadKBps float64 `json:"download_kbps"`
	UploadKBps   float64 `json:"upload_kbps"`

	Colo     string `json:"colo,omitempty"`
	HeldOpen bool   `json:"held_open"`
	WSOk     bool   `json:"websocket_ok"`
	TLSOk    bool   `json:"tls_ok"`

	Reputation *reputation.Info `json:"reputation,omitempty"`

	// Total is the composite 0..100 ranking figure.
	Total float64 `json:"score"`
	// Healthy is false when the address failed a hard requirement, in which
	// case it is reported but never recommended.
	Healthy bool `json:"healthy"`
	// Verdict mirrors the reputation traffic light, or unknown when reputation
	// was unavailable.
	Verdict string `json:"verdict"`
	// Notes explains the outcome to the user.
	Notes []string `json:"notes,omitempty"`
}

// Criteria defines what counts as a usable address.
type Criteria struct {
	// RequireHold disqualifies addresses that were reset during the idle hold.
	RequireHold bool
	// RequireWebSocket disqualifies addresses that refused a WebSocket upgrade.
	RequireWebSocket bool
	// RequireClean disqualifies anything the reputation provider did not mark
	// clean. When false, "caution" addresses are still allowed through.
	RequireClean bool
	// MinDownloadKBps disqualifies addresses slower than this. Zero disables.
	MinDownloadKBps float64
	// MaxLossPercent disqualifies addresses above this loss level.
	MaxLossPercent float64
	// MaxLatency disqualifies addresses slower than this. Zero disables.
	MaxLatency time.Duration
	Weights    Weights
}

// DefaultCriteria is a balanced profile: proven-carrying, clean-or-caution.
func DefaultCriteria() Criteria {
	return Criteria{
		RequireHold:      true,
		RequireWebSocket: false,
		RequireClean:     false,
		MaxLossPercent:   50,
		Weights:          DefaultWeights(),
	}
}

// StrictCriteria only accepts addresses that are green on every axis.
func StrictCriteria() Criteria {
	c := DefaultCriteria()
	c.RequireClean = true
	c.RequireWebSocket = true
	c.MinDownloadKBps = 200
	c.MaxLossPercent = 34
	c.MaxLatency = 900 * time.Millisecond
	return c
}

// Evaluate combines a probe result and its reputation record into a Candidate.
func Evaluate(r *probe.Result, rep *reputation.Info, c Criteria) *Candidate {
	if c.Weights == (Weights{}) {
		c.Weights = DefaultWeights()
	}

	cand := &Candidate{
		IP:         r.IP.String(),
		Port:       r.Port,
		Reputation: rep,
		Verdict:    reputation.VerdictUnknown.String(),
	}
	if rep != nil {
		cand.Verdict = rep.Verdict.String()
	}

	stats := summarise(r)
	cand.AvgLatency = stats.avg
	cand.AvgLatencyMs = ms(stats.avg)
	cand.MinLatencyMs = ms(stats.min)
	cand.JitterMs = ms(stats.jitter)
	cand.LossPercent = stats.loss
	cand.DownloadKBps = stats.downloadBps / 1024
	cand.UploadKBps = stats.uploadBps / 1024
	cand.Colo = stats.colo
	cand.HeldOpen = stats.held
	cand.WSOk = stats.wsOk
	cand.TLSOk = stats.tlsOk

	cand.Healthy = true

	if stats.successes < 1 {
		cand.Healthy = false
		if stats.lastErr != "" {
			cand.Notes = append(cand.Notes, stats.lastErr)
		} else {
			cand.Notes = append(cand.Notes, "no successful attempt")
		}
	}
	if r.Mode != "tcp" && !stats.tlsOk && r.Port != 80 {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "TLS handshake never completed")
	}
	if stats.loss > c.MaxLossPercent && c.MaxLossPercent > 0 {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "packet loss above threshold")
	}
	if c.MaxLatency > 0 && stats.avg > c.MaxLatency {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "latency above threshold")
	}
	if c.RequireHold && stats.holdTested && !stats.held {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "connection reset during idle hold")
	}
	if c.RequireWebSocket && !stats.wsOk {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "WebSocket upgrade unavailable")
	}
	if c.MinDownloadKBps > 0 && cand.DownloadKBps < c.MinDownloadKBps {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "download below threshold")
	}
	if c.RequireClean {
		if rep == nil || rep.Err != "" {
			cand.Healthy = false
			cand.Notes = append(cand.Notes, "reputation unverified")
		} else if rep.Verdict != reputation.VerdictClean {
			cand.Healthy = false
			cand.Notes = append(cand.Notes, "reputation not clean")
		}
	} else if rep != nil && rep.Verdict == reputation.VerdictDirty {
		cand.Healthy = false
		cand.Notes = append(cand.Notes, "reputation flagged as high risk")
	}

	cand.Total = composite(stats, rep, c.Weights)
	if rep != nil {
		cand.Notes = append(cand.Notes, rep.Reasons...)
	}
	return cand
}

type stats struct {
	avg, min, jitter time.Duration
	loss             float64
	successes        int
	downloadBps      float64
	uploadBps        float64
	colo             string
	tlsOk            bool
	held             bool
	holdTested       bool
	wsOk             bool
	lastErr          string
}

func summarise(r *probe.Result) stats {
	var s stats
	if r == nil || len(r.Attempts) == 0 {
		s.loss = 100
		return s
	}

	var sum time.Duration
	var lats []time.Duration
	for _, a := range r.Attempts {
		if a.Err != "" {
			s.lastErr = a.Err
		}
		if a.TLSOk {
			s.tlsOk = true
		}
		if a.WSOk {
			s.wsOk = true
		}
		if a.HeldOpen {
			s.held = true
			s.holdTested = true
		}
		// An attempt that reported a reset during hold also counts as tested.
		if a.Err == "connection was reset during idle hold" {
			s.holdTested = true
		}
		if a.Colo != "" {
			s.colo = a.Colo
		}
		if a.DownloadBps > s.downloadBps {
			s.downloadBps = a.DownloadBps
		}
		if a.UploadBps > s.uploadBps {
			s.uploadBps = a.UploadBps
		}
		if !a.Ok() {
			continue
		}
		s.successes++
		sum += a.Latency
		lats = append(lats, a.Latency)
		if s.min == 0 || a.Latency < s.min {
			s.min = a.Latency
		}
	}

	s.loss = float64(len(r.Attempts)-s.successes) / float64(len(r.Attempts)) * 100
	if s.successes == 0 {
		return s
	}
	s.avg = sum / time.Duration(s.successes)

	if len(lats) >= 2 {
		mean := float64(s.avg)
		var variance float64
		for _, l := range lats {
			d := float64(l) - mean
			variance += d * d
		}
		s.jitter = time.Duration(math.Sqrt(variance / float64(len(lats))))
	}
	return s
}

func composite(s stats, rep *reputation.Info, w Weights) float64 {
	if s.successes == 0 {
		return 0
	}

	// Reputation: an unverified address scores mid-range rather than zero, so
	// a provider outage does not erase all ranking information.
	repScore := 50.0
	if rep != nil && rep.Err == "" {
		repScore = 100 - rep.RiskPercent
	}

	// Latency: 30ms or better is full marks, 1500ms is zero.
	latScore := scale(ms(s.avg), 30, 1500, true)

	// Stability blends loss, jitter and the hold/WebSocket outcomes.
	stability := (100 - s.loss) * 0.5
	stability += scale(ms(s.jitter), 5, 400, true) * 0.3
	if s.held {
		stability += 15
	}
	if s.wsOk {
		stability += 5
	}
	stability = clamp(stability, 0, 100)

	// Throughput: 4 MB/s down and 1 MB/s up are treated as full marks.
	dlScore := scale(s.downloadBps/1024, 50, 4096, false)
	ulScore := scale(s.uploadBps/1024, 25, 1024, false)

	total := repScore*w.Reputation +
		latScore*w.Latency +
		stability*w.Stability +
		dlScore*w.Download +
		ulScore*w.Upload

	return math.Round(clamp(total, 0, 100)*10) / 10
}

// scale maps v within [best, worst] onto 0..100. When lowerIsBetter is true,
// best is the smaller bound.
func scale(v, best, worst float64, lowerIsBetter bool) float64 {
	if v <= 0 {
		if lowerIsBetter {
			return 0
		}
		return 0
	}
	if lowerIsBetter {
		if v <= best {
			return 100
		}
		if v >= worst {
			return 0
		}
		return (worst - v) / (worst - best) * 100
	}
	if v >= worst {
		return 100
	}
	if v <= best {
		return v / best * 40
	}
	return 40 + (v-best)/(worst-best)*60
}

func ms(d time.Duration) float64 {
	return math.Round(float64(d.Microseconds())/1000*100) / 100
}

func clamp(v, lo, hi float64) float64 {
	return math.Max(lo, math.Min(hi, v))
}

// Rank sorts candidates best-first: healthy before unhealthy, then by score.
func Rank(cands []*Candidate) {
	sort.SliceStable(cands, func(i, j int) bool {
		a, b := cands[i], cands[j]
		if a.Healthy != b.Healthy {
			return a.Healthy
		}
		if a.Total != b.Total {
			return a.Total > b.Total
		}
		return a.AvgLatency < b.AvgLatency
	})
}
