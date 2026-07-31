# IP ROCKER

Finds Cloudflare edge addresses that are **clean and fast**, not merely fast.

Most edge scanners rank by latency. That produces addresses which ping well and
then fail in practice: reset mid-session by filtering, unable to sustain a
transfer, refusing WebSocket upgrades, or flagged as proxy and abuse sources by
the reputation databases sites use to decide whether to show you a captcha.

IP ROCKER measures all of that, then ranks cleanliness above speed.

## What it actually checks

| Stage | Check | Why it matters |
|---|---|---|
| 1 | Weighted candidate generation | Cloudflare blocks differ enormously in pollution. Probe budget goes where clean addresses live instead of uniformly at random. |
| 2 | TLS handshake + `/cdn-cgi/trace` | Confirms a real Cloudflare edge answered, not a middlebox impersonating one. |
| 3 | Idle hold | Keeps a connection open with no traffic. Filtering that permits the first request and then sends a reset is caught here — the single most common false positive in latency-only scanners. |
| 4 | Real payload transfer | Downloads (and optionally uploads) actual bytes. Rejects paths that answer but cannot carry traffic. |
| 5 | WebSocket upgrade | Verifies the transport a CDN-fronted config depends on. An edge serving HTTP but refusing upgrades is useless for that purpose. |
| 6 | Neighbour expansion | A working edge usually sits in a run of working edges. Every hit is expanded outward, turning a few lucky draws into a usable list. |
| 7 | Reputation rating | Every answering address is rated for proxy / VPN / Tor / abuse flags plus owner and ASN abuse ratios. |
| 8 | Composite ranking | Reputation 35%, latency 25%, stability 20%, download 15%, upload 5%. |

### Measured block pollution

Sampling the reputation of random addresses across Cloudflare's published
ranges shows the difference is real, and per-address rather than per-ASN:

```
block               n    abuser  proxy   vpn
103.21.244.0/22     5      80%    100%   100%
108.162.192.0/18   25       4%      4%     4%
173.245.48.0/20    25       0%      8%     4%
190.93.240.0/20    25       0%      4%   100%
131.0.72.0/22      25       0%      0%   100%
104.16.0.0/13      25       0%      8%     0%
162.158.0.0/15     25       0%      0%     0%
172.64.0.0/13      25       0%      0%     0%
104.24.0.0/14      25       0%      0%     0%
```

Those figures drive the block weights in `internal/cfranges`. The worst subnets
are skipped outright.

Note that "datacenter address" is *not* penalised. Every Cloudflare edge is one,
so penalising it would rank all candidates equally badly and destroy the signal.

## Reputation data

Provider: [ipapi.is](https://ipapi.is). It accepts **100 addresses per POST with
no API key**, which is what makes reputation filtering practical inside a
scanner — a full scan's worth of hits is rated in a handful of requests.

An address that could not be rated is **never** reported as clean. A provider
outage degrades results to measurement-only and says so, rather than silently
passing risky addresses.

## Install

### Android

Grab the APK from the [releases page](https://github.com/Qezawat/IP-ROCKER/releases/latest):

| File | Use |
|---|---|
| `IP-ROCKER-<version>-universal.apk` | Any device (largest) |
| `IP-ROCKER-<version>-arm64-v8a.apk` | Modern 64-bit phones |
| `IP-ROCKER-<version>-armeabi-v7a.apk` | Older 32-bit phones |

Enable "install from unknown sources" if prompted. The app needs only the
`INTERNET` permission; nothing is stored off-device and there is no account.

### CLI

Download a binary from the releases page, or build it:

```bash
go build -o iprocker ./cmd/iprocker
```

## Usage

```bash
# Balanced scan of 400 addresses
./iprocker

# Use your own config's front, so results match real traffic
./iprocker -sni panel.example.com -host panel.example.com -ws-path '/?ed=2560' -require-ws

# Only accept addresses clean on every axis
./iprocker -strict -count 1500

# Offline: skip reputation lookups entirely
./iprocker -no-reputation

# Export
./iprocker -out clean.txt -json report.json
```

Sample output:

```
Probed 216 addresses in 36.93s — 23 answered, 22 usable

IP               SCORE  LATENCY  JITTER   DOWNLOAD  COLO   RISK     STATUS
----------------------------------------------------------------------------
104.25.228.176   71.3   742ms    21ms     360 KB/s  FRA    6%       🟢 stable
104.25.36.47     70.0   796ms    40ms     313 KB/s  FRA    6%       🟢 stable
104.21.146.166   61.3   770ms    0ms      281 KB/s  FRA    2%       🟢 stable

Best: 104.25.228.176:443 — score 71.3, 🟢 clean risk 5.7%, United States San Francisco
```

### Flags

| Flag | Default | Meaning |
|---|---|---|
| `-count` | 400 | Addresses probed in the first pass |
| `-concurrency` | 64 | Parallel probes |
| `-port` | 443 | Edge port |
| `-mode` | http | `tcp`, `tls` or `http` |
| `-tries` | 3 | Attempts per address |
| `-timeout` | 6s | Per-attempt timeout |
| `-hold` | 3s | Idle hold duration; `0` disables the reset check |
| `-download` | 262144 | Download sample bytes; `0` disables |
| `-upload` | 0 | Upload sample bytes; `0` disables |
| `-ws-path` | | WebSocket path to verify |
| `-require-ws` | false | Reject edges refusing the upgrade |
| `-sni` / `-host` | rotating | TLS server name / HTTP Host header |
| `-strict` | false | Accept only fully clean addresses |
| `-no-reputation` | false | Run fully offline |
| `-cidr` / `-only-cidr` | | Extra ranges / restrict scope to them |
| `-out` / `-json` | | Write `ip:port` lines / full report |

## A note on REALITY

REALITY cannot be fronted by Cloudflare, and the app rejects such links with an
explanation rather than producing a scan that cannot help. REALITY requires the
TLS handshake to reach your origin untouched; a Cloudflare proxy terminates TLS
at the edge, so the handshake never arrives. Use VLESS or Trojan over WebSocket
with TLS instead — that is what this tool is built for.

## Build the Android app

```bash
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>

./android/build_aar.sh            # Go core -> android/app/libs/iprocker.aar
cd android && ./gradlew :app:assembleDebug
```

### Signed release APKs in CI

`.github/workflows/release.yml` builds signed APKs when four repository secrets
are present. Without them it still produces installable debug-signed APKs, so a
release is never blocked on signing setup.

Generate a keystore:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias iprocker \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # paste this as ANDROID_KEYSTORE_BASE64
```

Then add under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | base64 of `release.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | store password you chose |
| `ANDROID_KEY_ALIAS` | `iprocker` |
| `ANDROID_KEY_PASSWORD` | key password you chose |

Keep `release.keystore` safe and backed up. Losing it means future APKs cannot
be installed as an update over existing ones.

## Layout

```
cmd/iprocker/          CLI
internal/cfranges/     Weighted Cloudflare range generation, neighbour expansion
internal/probe/        TLS, trace, hold, transfer and WebSocket measurement
internal/reputation/   Bulk reputation client, risk scoring, verdicts
internal/score/        Composite ranking
internal/scanner/      Phase orchestration
mobile/                gomobile bindings and config-link parsing
android/               Kotlin Compose app
```

## Licence

MIT.
