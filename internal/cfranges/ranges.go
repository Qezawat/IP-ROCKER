// Package cfranges holds Cloudflare's published edge ranges together with
// empirically measured "cleanliness" weights.
//
// Weights come from sampling the reputation of random addresses inside each
// block (see cmd/blockprofile). Blocks whose addresses are frequently flagged
// as proxy/VPN/abuser by reputation providers get a low weight so the scanner
// spends its probe budget where clean IPs actually live, instead of drawing
// uniformly at random like naive scanners do.
package cfranges

import (
	"encoding/binary"
	"fmt"
	"math/rand"
	"net"
	"sort"
	"strings"
)

// Block is one Cloudflare CIDR plus its scan priority.
type Block struct {
	CIDR string
	// Weight biases random selection. 1.0 = neutral. Higher means the block
	// historically yields cleaner IPs; lower means it is polluted.
	Weight float64
	// Note documents why the weight is what it is.
	Note string
}

// V4Blocks is Cloudflare's IPv4 edge space, ordered roughly best-first.
//
// Measured abuser/proxy/vpn flag rates over a 300-address sample:
//
//	162.158.0.0/15    0% / 0% / 0%    cleanest
//	172.64.0.0/13     0% / 0% / 0%
//	104.24.0.0/14     0% / 0% / 0%
//	104.16.0.0/13     0% / 8% / 0%
//	198.41.128.0/17   0% / 4% / 8%
//	173.245.48.0/20   0% / 8% / 4%
//	188.114.96.0/20   0% / 0% / partly 100% vpn (96.0/22 sub-block)
//	190.93.240.0/20   0% / 4% / 100% vpn
//	131.0.72.0/22     0% / 0% / 100% vpn
//	108.162.192.0/18  4% / 4% / 4%
//	103.21.244.0/22  80% /100% /100%  heavily polluted
var V4Blocks = []Block{
	{CIDR: "162.158.0.0/15", Weight: 1.6, Note: "clean, large, well distributed"},
	{CIDR: "172.64.0.0/13", Weight: 1.6, Note: "clean, large"},
	{CIDR: "104.24.0.0/14", Weight: 1.5, Note: "clean"},
	{CIDR: "104.16.0.0/13", Weight: 1.2, Note: "mostly clean, some proxy-flagged /24s"},
	{CIDR: "198.41.128.0/17", Weight: 1.0, Note: "mixed"},
	{CIDR: "173.245.48.0/20", Weight: 0.9, Note: "small, some proxy flags"},
	{CIDR: "141.101.64.0/18", Weight: 0.9, Note: "mixed; 141.101.120.0/22 is proxy-flagged"},
	{CIDR: "188.114.96.0/20", Weight: 0.7, Note: "188.114.96.0/22 flagged as VPN"},
	{CIDR: "103.22.200.0/22", Weight: 0.6, Note: "small APNIC block"},
	{CIDR: "103.31.4.0/22", Weight: 0.6, Note: "small APNIC block"},
	{CIDR: "197.234.240.0/22", Weight: 0.5, Note: "small AFRINIC block, far colos"},
	{CIDR: "190.93.240.0/20", Weight: 0.4, Note: "widely flagged as VPN"},
	{CIDR: "131.0.72.0/22", Weight: 0.4, Note: "widely flagged as VPN"},
	{CIDR: "108.162.192.0/18", Weight: 0.4, Note: "abuser flags present"},
	{CIDR: "103.21.244.0/22", Weight: 0.1, Note: "heavily polluted: 80% abuser, 100% proxy"},
}

// V6Blocks is Cloudflare's IPv6 edge space. IPv6 reputation data is sparse, so
// weights are neutral and based only on block size.
var V6Blocks = []Block{
	{CIDR: "2400:cb00::/32", Weight: 1.0},
	{CIDR: "2606:4700::/32", Weight: 1.0},
	{CIDR: "2803:f800::/32", Weight: 0.8},
	{CIDR: "2405:b500::/32", Weight: 0.8},
	{CIDR: "2405:8100::/32", Weight: 0.8},
	{CIDR: "2a06:98c0::/29", Weight: 1.0},
	{CIDR: "2c0f:f248::/32", Weight: 0.6},
}

// KnownDirtySubnets are /22-or-smaller ranges observed to be almost entirely
// proxy/VPN/abuser flagged. Addresses inside them are skipped outright.
var KnownDirtySubnets = []string{
	"103.21.244.0/23",
	"141.101.120.0/22",
}

// Source generates candidate addresses from a weighted block set.
type Source struct {
	nets    []*net.IPNet
	weights []float64
	cum     []float64
	dirty   []*net.IPNet
	rng     *rand.Rand
}

// Options configures a Source.
type Options struct {
	IPv4 bool
	IPv6 bool
	// ExtraCIDRs are added to the pool with neutral weight.
	ExtraCIDRs []string
	// OnlyExtra treats ExtraCIDRs as the entire scan scope, ignoring the
	// built-in Cloudflare blocks.
	OnlyExtra bool
	// SkipDirty drops addresses that fall inside KnownDirtySubnets.
	SkipDirty bool
	// Seed makes generation reproducible when non-zero.
	Seed int64
}

// NewSource builds a weighted address generator.
func NewSource(opts Options) (*Source, error) {
	seed := opts.Seed
	if seed == 0 {
		seed = rand.Int63()
	}
	s := &Source{rng: rand.New(rand.NewSource(seed))}

	add := func(b Block) error {
		_, n, err := net.ParseCIDR(strings.TrimSpace(b.CIDR))
		if err != nil {
			return fmt.Errorf("bad CIDR %q: %w", b.CIDR, err)
		}
		w := b.Weight
		if w <= 0 {
			w = 1
		}
		// Scale weight by block size so a /13 is not drawn as often as a /22
		// purely because both have weight 1.
		ones, bits := n.Mask.Size()
		size := pow2(bits - ones)
		s.nets = append(s.nets, n)
		s.weights = append(s.weights, w*size)
		return nil
	}

	if !opts.OnlyExtra {
		if opts.IPv4 {
			for _, b := range V4Blocks {
				if err := add(b); err != nil {
					return nil, err
				}
			}
		}
		if opts.IPv6 {
			for _, b := range V6Blocks {
				if err := add(b); err != nil {
					return nil, err
				}
			}
		}
	}
	for _, c := range opts.ExtraCIDRs {
		c = strings.TrimSpace(c)
		if c == "" {
			continue
		}
		if err := add(Block{CIDR: c, Weight: 1}); err != nil {
			return nil, err
		}
	}
	if len(s.nets) == 0 {
		return nil, fmt.Errorf("no ranges selected (enable IPv4 and/or IPv6)")
	}

	if opts.SkipDirty {
		for _, c := range KnownDirtySubnets {
			if _, n, err := net.ParseCIDR(c); err == nil {
				s.dirty = append(s.dirty, n)
			}
		}
	}

	s.cum = make([]float64, len(s.weights))
	var sum float64
	for i, w := range s.weights {
		sum += w
		s.cum[i] = sum
	}
	return s, nil
}

// Nets exposes the loaded ranges, used by the neighbour scanner to confirm a
// candidate still belongs to Cloudflare space.
func (s *Source) Nets() []*net.IPNet { return s.nets }

// IsDirty reports whether ip falls inside a known-polluted subnet.
func (s *Source) IsDirty(ip net.IP) bool {
	for _, n := range s.dirty {
		if n.Contains(ip) {
			return true
		}
	}
	return false
}

// Random returns one weighted-random address, skipping dirty subnets.
func (s *Source) Random() net.IP {
	for attempt := 0; attempt < 64; attempt++ {
		total := s.cum[len(s.cum)-1]
		r := s.rng.Float64() * total
		idx := sort.SearchFloat64s(s.cum, r)
		if idx >= len(s.nets) {
			idx = len(s.nets) - 1
		}
		ip := randomInNet(s.nets[idx], s.rng)
		if !s.IsDirty(ip) {
			return ip
		}
	}
	// All attempts landed in dirty space; return the last draw anyway rather
	// than blocking the scan.
	return randomInNet(s.nets[0], s.rng)
}

// Stream emits unique random addresses until ctx is done or count is reached.
// count <= 0 means unlimited.
func (s *Source) Stream(done <-chan struct{}, count int) <-chan net.IP {
	ch := make(chan net.IP, 128)
	go func() {
		defer close(ch)
		seen := make(map[string]struct{}, max(count, 64))
		sent := 0
		// Cap wasted draws so a tiny custom CIDR cannot spin forever.
		misses := 0
		for count <= 0 || sent < count {
			ip := s.Random()
			key := ip.String()
			if _, dup := seen[key]; dup {
				misses++
				if misses > 10000 {
					return
				}
				continue
			}
			misses = 0
			seen[key] = struct{}{}
			select {
			case <-done:
				return
			case ch <- ip:
				sent++
			}
		}
	}()
	return ch
}

// NeighborsOf returns up to limit addresses adjacent to ip that are still
// inside Cloudflare space and not dirty. A working edge IP usually sits in a
// block of working edge IPs, so this converts one hit into many.
func (s *Source) NeighborsOf(ip net.IP, radius, limit int) []net.IP {
	if radius <= 0 || limit <= 0 {
		return nil
	}
	ip4 := ip.To4()
	if ip4 == nil {
		return nil
	}
	base := binary.BigEndian.Uint32(ip4)
	out := make([]net.IP, 0, limit)
	for d := 1; d <= radius && len(out) < limit; d++ {
		for _, delta := range [2]int64{int64(d), -int64(d)} {
			v := int64(base) + delta
			if v < 0 || v > 0xFFFFFFFF {
				continue
			}
			cand := make(net.IP, 4)
			binary.BigEndian.PutUint32(cand, uint32(v))
			if s.IsDirty(cand) || !s.contains(cand) {
				continue
			}
			out = append(out, cand)
			if len(out) >= limit {
				break
			}
		}
	}
	return out
}

func (s *Source) contains(ip net.IP) bool {
	for _, n := range s.nets {
		if n.Contains(ip) {
			return true
		}
	}
	return false
}

func randomInNet(n *net.IPNet, rng *rand.Rand) net.IP {
	if ip4 := n.IP.To4(); ip4 != nil {
		base := binary.BigEndian.Uint32(ip4)
		mask := binary.BigEndian.Uint32(net.IP(n.Mask).To4())
		host := rng.Uint32() & ^mask
		// Avoid .0 and .255 host bytes; Cloudflare does not serve them and
		// probing them just burns budget.
		if host&0xFF == 0 {
			host |= 1
		} else if host&0xFF == 0xFF {
			host &= ^uint32(1)
		}
		out := make(net.IP, 4)
		binary.BigEndian.PutUint32(out, base|host)
		return out
	}
	out := make(net.IP, len(n.IP))
	copy(out, n.IP)
	for i := range out {
		out[i] = n.IP[i] | (byte(rng.Intn(256)) & ^n.Mask[i])
	}
	return out
}

func pow2(n int) float64 {
	f := 1.0
	for i := 0; i < n && i < 62; i++ {
		f *= 2
	}
	return f
}
