# whatsbot Android app — ready to build

## WHAT THIS IS
Personal WhatsApp automation app for Android: scheduling outbound messages, auto-reply rules.
Built on whatsmeow (Go) compiled via gomobile → .aar → Kotlin Android app.
Exit condition: APK installed on user's device, paired to their WA account, scheduling + auto-reply working.

## WHERE WE ARE
**Phase 1 complete: foundation.**
- whatsmeow fork: hardened, tested, on `main` at `github.com/caseyng/whatsmeow`
- gomobile wrapper: complete, tested, at `github.com/caseyng/whatsbot-go`
- SQLite schema: messages, chats, group_members, reactions — all populated from history sync

**Phase 2: Android app. Not started.**
Next immediate steps (user's words): "scope it" + "setup ecosystem such as skills best practices".
User wants to scope app features before writing any code. Do not skip scoping.

## CONSTRAINTS
- HARD: personal-use, best-effort service. Never over-engineer reliability or add complexity for edge cases the owner won't hit.
- HARD: no stdout, os.Exit, panic in Go library code. Android process would die.
- HARD: waLog.Logger must be implemented for logcat, not waLog.Stdout/Noop.
- HARD: gomobile boundary — no interface{}, func, channels, maps across. Concrete types only.
- SOFT: configurable over hardcoded. Policy questions (retry counts, timeouts, feature flags) → config fields with simplest MVP defaults. Only escalate on genuine contract decisions.
- SOFT: simpler over more rigorous on every tradeoff (personal convenience app, not production SaaS).

## DECISIONS MADE

**Go library (whatsbot-go):**
- Single shared `*sql.DB` for both whatsmeow internal tables and wa_* tables → avoids WAL conflicts
- DSN: `file:<path>?_foreign_keys=on&_journal_mode=WAL&_busy_timeout=5000` — WAL for concurrent reads, busy_timeout prevents lock errors during history sync burst
- INSERT OR IGNORE on message ID → idempotent re-sync after re-pair
- `Listener` interface + `Message` struct = gomobile API boundary; whatsmeow's `func(evt any)` stays inside wrapper
- `emit(func(Listener))` protected by sync.RWMutex

**whatsmeow fork hardening (all on `main`):**
- uTLS HelloChrome_Auto + patch ALPNExtension (not HandshakeState.Hello) to remove h2
- Both SetWebsocketHTTPClient + SetPreLoginHTTPClient must use Chrome client
- Prekeys: WantedPreKeyCount 50→812, MinPreKeyCount 5→10, signed prekey rotation monthly
- DeviceProps: Os=Chrome, PlatformType=CHROME, version=124.0.0
- newsletter.go: log.Fatalf → return error

**test-connect CLI:**
- `--backfill-chat <jid>`: on-demand history fetch loop (BuildHistorySyncRequest + SendPeerMessage)
- ON_DEMAND responses bypass --chat filter (we requested it explicitly)
- Stop backfill on `returned == 0`, not `stored == 0` (stored=0 can mean duplicates, not end of history)
- upsertChat before wantChat filter → always capture chat names even for filtered chats

## WHAT FAILED / REJECTED

| Tried | Failed because | Correct |
|---|---|---|
| Patch HandshakeState.Hello.AlpnProtocols | ApplyConfig() overwrites it during handshake | Patch ALPNExtension object in uconn.Extensions |
| Two separate sql.Open calls | database is locked under history sync load | One shared *sql.DB via sqlstore.NewWithDB |
| Stop backfill on stored==0 | Terminates early if DB already has those messages | Stop on returned==0 |
| `go build ./...` to rebuild binary | No binary emitted when multiple packages match | `go build -o ./cmd/test-connect/test-connect ./cmd/test-connect/` |
| Push `main` of fork | non-fast-forward; remote main was ahead | Pull first, then merge feature branch, then push |
| WebView approach for WA automation | Abandoned entirely — too fragile, no real API | whatsmeow + gomobile (current approach) |

## REASONING PATTERNS

**SITUATION**: Any design decision about behavior, defaults, retry counts, error handling.
**WRONG INSTINCT**: Ask user, or default to robust/defensive.
**CORRECT FRAMING**: It's their personal app; they decide via config. Pick the simplest MVP default and make it configurable. Only escalate if it's a genuine architectural contract (not a policy question).
**IMPLICATION**: Most "should we...?" questions answer themselves: yes if trivial to add as config, no if it adds structural complexity.

**SITUATION**: Tempted to implement something before being asked (saw a need, added it proactively).
**WRONG INSTINCT**: Ship the feature, mention it in the response.
**CORRECT FRAMING**: User said "query the DB" → just query the DB. Don't build a backfill system when asked for a SQLite command. Match the scope of the request exactly.
**IMPLICATION**: Read the request literally first. If scope expansion is warranted, ask.

**SITUATION**: WhatsApp history sync has fewer messages than expected.
**CORRECT FRAMING**: Two-tier strategy: active chats get ~25-30 day rolling window; inactive chats get exactly 1 stub message (most recent, however old). No cutoff date — it's activity-based. Full history requires Transfer Chat History at pairing time (local WiFi, can't trigger programmatically).
**IMPLICATION**: App should offer "load more" (on-demand backfill) for any chat. Never treat 1-message chats as empty.

## USER CORRECTIONS

1. **"query the existing db... using sqlite. bash command"** — Claude had just built a full backfill feature. User wanted a one-liner SQLite query. Wrong instinct: proactive feature build. Correct: match request scope exactly.

2. **"no no. I already unpaired. I mean whatever is inside the database now"** — Claude offered to reconnect and fetch more data. User wanted to query what's already stored. Same pattern: match what was asked.

3. **"wait, what changed?"** — After user asked about on-demand history loading, Claude immediately implemented the `--backfill-chat` flag. User's intent was to understand the concept / query existing data. Implemented before being asked to.

4. **Git/GitHub confusion** — User didn't know SSH vs HTTPS causes the username prompt; didn't know forks can't change default branch; didn't know what "default branch" actually controls. Explain these from first principles when git/GitHub questions come up — don't assume familiarity.

## NEXT
- MUST: Scope the Android app (what features exactly, MVP boundary)
- MUST: Set up Android dev ecosystem — skills, best practices, project structure decision (Kotlin, Jetpack Compose vs XML, etc.)
- SHOULD: Decide gomobile build pipeline (how .aar gets built and included in Android project)
- SHOULD: Decide on Android Room vs raw SQLite for reading the Go-written DB from Kotlin side
- DEBT: whatsbot-go `go.mod` still uses `replace go.mau.fi/whatsmeow => /root/whatsmeow-fork` (local path). Before any production build, change to `github.com/caseyng/whatsmeow` + tag the fork.

## OPEN QUESTIONS
- App feature scope: what exactly does "scheduling" mean? Time-based? Recurring? Per-contact rules?
- Auto-reply: keyword matching? AI-based? Rule-based? Who decides the rules at runtime?
- UI: how does the user configure rules? Settings screen? File-based config?
- Pairing flow: how does the Android app handle first-time pairing? In-app UI?
- Background service: foreground service (persistent notification) or WorkManager?

## CONTEXT NEW SESSION CANNOT INFER
- User is Casey, personal project, personal WA account automation. Not a product for others.
- "Best-effort service" is a first-class constraint, not a cop-out. Means: don't add retry logic, circuit breakers, dead letter queues etc. unless the feature obviously needs it.
- User is new to git/GitHub workflows — explain SSH vs HTTPS, branching, remotes from first principles when these come up. Don't assume.
- User learns by doing and asking. Prefers short direct answers. Gets frustrated when Claude does more than asked.
- whatsbot-go module path: `github.com/caseyng/whatsbot-go`
- whatsmeow fork module path: `go.mau.fi/whatsmeow` (replaced to local or caseyng/whatsmeow)

## ARTIFACTS

| File | Location | State |
|---|---|---|
| `utls.go` | `whatsmeow-fork/` | Chrome TLS + ALPN fix. Done. |
| `prekeys.go` | `whatsmeow-fork/` | Hardened counts + rotation. Done. |
| `newsletter.go` | `whatsmeow-fork/` | log.Fatalf → error. Done. |
| `cmd/test-connect/main.go` | `whatsmeow-fork/` | Full CLI with backfill. Done. |
| `EXPERIENCE.md` | `whatsmeow-fork/` | All protocol learnings + decisions. Read this first. |
| `client.go` | `whatsbot-go/` | gomobile Client wrapper. Done. |
| `storage.go` | `whatsbot-go/` | All SQLite functions. Done. |
| `listener.go` | `whatsbot-go/` | Listener interface. Done. |
| `message.go` | `whatsbot-go/` | Message struct + extractBody. Done. |
| `storage_test.go` + `message_test.go` | `whatsbot-go/` | 20 unit tests, all passing. |

**Repos:**
- `github.com/caseyng/whatsmeow` — fork, `main` branch, SSH working
- `github.com/caseyng/whatsbot-go` — wrapper lib, `main` branch

## RESUME INSTRUCTIONS
1. Read `/root/whatsmeow-fork/EXPERIENCE.md` — full protocol and architecture context
2. Read `/root/whatsbot-go/client.go` + `storage.go` — current Go API surface
3. Read this file
4. Then: help user scope the Android app and set up the dev ecosystem

---
ORIENTATION: You're picking up a WhatsApp automation Android app project that has its Go foundation complete. The whatsmeow fork (fingerprint-hardened) and gomobile wrapper (whatsbot-go) are both done, tested, and on GitHub. The next phase is the Android app itself — but the user wants to scope features and set up the dev ecosystem (skills, best practices, project structure) before writing any Android code. The dominant reasoning pattern: this is a personal convenience app, owned and used by one person. Every design question resolves toward "simplest thing that works" and "make it configurable." The highest-risk mistake for a new session is doing more than asked — user has corrected this repeatedly. Match request scope exactly; ask before expanding it.
