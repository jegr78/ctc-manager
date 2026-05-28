# Phase-95 Discussion Inputs

Pre-discussion notes captured during Phase 94 execution / UAT. To be folded into
`/gsd-discuss-phase 95` (or its CONTEXT.md output) before planning starts.

## Open Questions

### Q-95-01 — Auto-post Team Cards on channel creation? (2026-05-22)

**Source:** UAT-04 retro chat, 2026-05-22 (user: "Beim Erstellen des Match Day
Kanals sollte laut Anforderung direkt auch die beiden Team Card Grafiken als
Nachricht gepostet werden. Das ist nicht passiert und ich sehe aktuell auch
keinen Button, welcher diese Aktion ausführt.").

**Current ROADMAP (v1.13) state:**
Phase-95 success criterion 2 defines a **manual** *"Post Team Cards"* button that
issues ONE multipart Webhook-POST with both PNGs as `files[0]+files[1]` and
stores a single `message_id` in the `TEAM_CARDS` row of `discord_post`.

**The question:**
Should "Create Discord Channel" (Phase-94 happy-path) *also* trigger the
Team-Cards-Post automatically, or does the operator keep a separate manual
"Post Team Cards" button (current ROADMAP wording)?

**Trade-off to surface in discussion:**
- **Auto-post**: one-click operator UX, but couples channel-lifecycle to a
  post-type (graphic generation can fail independently of channel creation;
  failure handling becomes a hybrid TRANSIENT/AUTH/NOT_FOUND/CATEGORY_FULL +
  graphic-rendering-failure matrix).
- **Manual button**: matches the rest of the 11-post-type pattern (every other
  post type has its own button per Phase-95/97 success criteria), preserves
  symmetry, lets the operator regenerate Team Cards cheaply if e.g. a logo
  changed between channel-create and lineup-lock.
- **Hybrid**: auto-post on create + manual Re-Post button. UX-cheapest for the
  happy-path, still recoverable on failure.

**Suggested discussion outcome:** decide between (a) keep manual-only as ROADMAP
states, (b) add hybrid auto-on-create with manual Re-Post button, or (c) move
auto-post-on-create out of Phase-95 into Phase-98 polish.

**Owner:** orchestrator at `/gsd-discuss-phase 95`.
