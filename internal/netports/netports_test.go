package netports

import "testing"

func TestParseEmptyUsesFallback(t *testing.T) {
	got, err := Parse("  ", 2053)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != 1 || got[0] != 2053 {
		t.Fatalf("want [2053], got %v", got)
	}
}

func TestParseEmptyRejectsBadFallback(t *testing.T) {
	if _, err := Parse("", 0); err == nil {
		t.Fatal("want an error for an out-of-range fallback")
	}
}

func TestParseAllExpandsToCloudflareSet(t *testing.T) {
	got, err := Parse("ALL", 443)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(got) != len(CloudflareTLS) {
		t.Fatalf("want %d ports, got %v", len(CloudflareTLS), got)
	}
	// The returned slice must be a copy, or a caller could mutate the package
	// level default for every later scan.
	got[0] = 1
	if CloudflareTLS[0] == 1 {
		t.Fatal("Parse returned an alias of CloudflareTLS")
	}
}

func TestParseListDropsDuplicatesAndKeepsOrder(t *testing.T) {
	got, err := Parse("8443, 443 ,8443,2096", 443)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []int{8443, 443, 2096}
	if len(got) != len(want) {
		t.Fatalf("want %v, got %v", want, got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("want %v, got %v", want, got)
		}
	}
}

func TestParseRejectsGarbage(t *testing.T) {
	for _, in := range []string{"http", "443,abc", "70000", "-1", ",,,"} {
		if _, err := Parse(in, 443); err == nil {
			t.Errorf("want an error for %q", in)
		}
	}
}

func TestNormaliseFallsBackTo443(t *testing.T) {
	got := Normalise([]int{0, 70000, -5})
	if len(got) != 1 || got[0] != 443 {
		t.Fatalf("want [443], got %v", got)
	}
}

func TestNormaliseDropsDuplicates(t *testing.T) {
	got := Normalise([]int{443, 443, 2053, 0})
	want := []int{443, 2053}
	if len(got) != len(want) || got[0] != want[0] || got[1] != want[1] {
		t.Fatalf("want %v, got %v", want, got)
	}
}

func TestCloudflareTLSCSV(t *testing.T) {
	if CloudflareTLSCSV() != "443,2053,2083,2087,2096,8443" {
		t.Fatalf("unexpected CSV: %q", CloudflareTLSCSV())
	}
}
