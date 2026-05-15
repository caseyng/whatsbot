# whatsbot Android app — ready to build

## WHAT THIS IS
Context-aware WhatsApp communication proxy. Acts on Casey's behalf when physically/temporally unavailable.
Built on whatsmeow (Go) compiled via gomobile → .aar → Kotlin Android app (monorepo: `go/` + `android/`).
Exit condition: APK installed on device, paired, auto-reply on activity detection + scheduled messages working.

## WHERE WE ARE
**Go foundation complete.**
- whatsmeow fork: hardened, tested, on `main` at `github.com/caseyng/whatsmeow`
- Go library: complete, tested, at `github.com/caseyng/whatsbot` in `go/` (module: `github.com/caseyng/whatsbot/go`)
- SQLite schema: messages, chats, group_members, reactions populated from history sync
- Repo restructured: monorepo at `github.com/caseyng/whatsbot`, Android goes in `android/`

**Android dev ecosystem complete.**
- `android-engineering` skill at `~/.claude/skills/android-engineering/`
- Kotlin guardrail binding at `~/.claude/skills/code-integrity-guardrail/references/bindings/kotlin.md`
- Phase 1 scope approved: see `/root/.claude/plans/sequential-enchanting-pelican.md`

**Android app: not started. Next step: create project structure.**

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

**Rules engine design decisions:**
- Multiple rules match same message → first matching rule fires only (simpler, less noise, lower ban risk)
- "First" is user-controlled: rules list is sortable; default order is insertion order; UI supports grouping by trigger type or contact
- ActivityTrigger fires on transition into activity (entering IN_VEHICLE), not on every message while active — configurable per rule, but transition-only is default
- Contact filter matches against chat JID, not sender JID — "Alice" means Alice's individual chat, not Alice-the-person-anywhere. Rationale: in a group, Alice's message is directed at the group, not at you; auto-replying would be noisy and a ban risk

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
- MUST: Create Android project in `android/` — Kotlin, Jetpack Compose, Gradle Kotlin DSL, version catalog
- MUST: Wire gomobile `.aar` build pipeline (`scripts/build-go.sh`)
- MUST: Implement foreground service skeleton (WhatsAppForegroundService with Go Client)
- SHOULD: Implement pairing flow (pair code UI → Go library → connection status)
- SHOULD: Define Room schema for app-owned tables (rules, schedules, log entries)

## OPEN QUESTIONS
- Application ID / package name for the Android app (e.g. `com.caseyng.whatsbot`)
- Min SDK: plan says API 26, confirm before creating project

## CONTEXT NEW SESSION CANNOT INFER
- User is Casey, personal project, personal WA account automation. Not a product for others.
- "Best-effort service" is a first-class constraint, not a cop-out. Means: don't add retry logic, circuit breakers, dead letter queues etc. unless the feature obviously needs it.
- User is new to git/GitHub workflows — explain SSH vs HTTPS, branching, remotes from first principles when these come up. Don't assume.
- User learns by doing and asking. Prefers short direct answers. Gets frustrated when Claude does more than asked.
- Go module path: `github.com/caseyng/whatsbot/go` (was `whatsbot-go`, renamed 2026-05-15)
- whatsmeow fork module path: `go.mau.fi/whatsmeow` (replace → `github.com/caseyng/whatsmeow v0.1.0-whatsbot`)
- Local dir: `/root/whatsbot/` — monorepo. Go library in `go/`, Android app will go in `android/`

## ARTIFACTS

| File | Location | State |
|---|---|---|
| `utls.go` | `whatsmeow-fork/` | Chrome TLS + ALPN fix. Done. |
| `prekeys.go` | `whatsmeow-fork/` | Hardened counts + rotation. Done. |
| `newsletter.go` | `whatsmeow-fork/` | log.Fatalf → error. Done. |
| `cmd/test-connect/main.go` | `whatsmeow-fork/` | Full CLI with backfill. Done. |
| `EXPERIENCE.md` | `whatsmeow-fork/` | All protocol learnings + decisions. Read this first. |
| `client.go` | `whatsbot/go/` | gomobile Client wrapper. Done. |
| `storage.go` | `whatsbot/go/` | All SQLite functions. Done. |
| `listener.go` | `whatsbot/go/` | Listener interface. Done. |
| `message.go` | `whatsbot/go/` | Message struct + extractBody. Done. |
| `storage_test.go` + `message_test.go` | `whatsbot/go/` | 20 unit tests, all passing. |

**Repos:**
- `github.com/caseyng/whatsmeow` — fork, `main` branch, SSH working
- `github.com/caseyng/whatsbot` — monorepo (`go/` + `android/` to come), `main` branch

## RESUME INSTRUCTIONS
1. Read `/root/whatsmeow-fork/EXPERIENCE.md` — full protocol and architecture context
2. Read `/root/whatsbot/go/client.go` + `go/storage.go` — current Go API surface
3. Read this file
4. Then: help user scope the Android app and set up the dev ecosystem

---
ORIENTATION: You're picking up a WhatsApp automation Android app project. The monorepo is `github.com/caseyng/whatsbot` (local: `/root/whatsbot/`). Go library is in `go/` (module `github.com/caseyng/whatsbot/go`), Android app will go in `android/` (not yet created). The whatsmeow fork (fingerprint-hardened) and Go library are both done, tested, and on GitHub. Android dev ecosystem is set up: `android-engineering` skill at `~/.claude/skills/android-engineering/`, Kotlin guardrail binding at `~/.claude/skills/code-integrity-guardrail/references/bindings/kotlin.md`. Next phase is scoping and building the Android app. Dominant reasoning pattern: personal convenience app for one person — every design question resolves toward "simplest thing that works." Highest-risk mistake: doing more than asked. Match request scope exactly; ask before expanding.
