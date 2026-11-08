# QA and release sign-off checklist

## Automated verification

- [ ] Debug APK assembles.
- [ ] Optimized release APK assembles with R8 and resource shrinking.
- [ ] All JVM tests pass.
- [ ] API 36 emulator instrumentation tests pass.
- [ ] Bundled SQLite source decodes and imports into Room on a clean install.
- [ ] Imported card and printing counts are non-zero and match the expected catalogue snapshot.
- [ ] Playable and catalogue-only classifications are checked against a reviewed rules-text fixture.
- [ ] The CI rules-coverage CSV is reviewed; variable/missing stats and unsupported wording are triaged.
- [ ] Manifest check confirms no INTERNET permission and backup remains disabled.
- [ ] Release artifact is uploaded and its checksum is retained.

## Rules and match regression

- [ ] Deck size minimum is 40, with no maximum.
- [ ] Four-copy limit is enforced by card name across printings.
- [ ] Mana charging, multicolored mana entering tapped, per-civilization payment, and turn progression.
- [ ] Five-civilization mana has zero numeric value and can satisfy only one civilization requirement per tapped card.
- [ ] Opening hands, shields, draw and deck-out.
- [ ] Summoning sickness, Speed Attacker and evolution race matching.
- [ ] Evolution stack destruction and bounce behavior.
- [ ] Shield attacks, direct victory and Double/Triple Breaker.
- [ ] Blocker interception, decline and multiple-blocker selection.
- [ ] Shield Trigger use/decline and multi-shield continuation.
- [ ] Targeted destroy/return/tap decisions and illegal target rejection.
- [ ] Temporary power modifiers and expiry timing.
- [ ] AI vs AI full-match simulation for all difficulty levels.
- [ ] Local pass-and-play private hand transitions and decision handoffs.
- [ ] Local player 1 and player 2 can choose separate saved decks.
- [ ] A saved deck with missing/corrupt card references is blocked rather than silently shortened.

## Device and UX verification

- [ ] Install and launch on a physical Android 8.0+ device.
- [ ] Complete first-run import with airplane mode enabled.
- [ ] Browse/search the full catalogue and inspect printings.
- [ ] Create, edit, save, select and delete decks.
- [ ] Complete solo matches at all AI difficulty levels.
- [ ] Complete local pass-and-play matches without private-hand leakage.
- [ ] Complete local matches with two different saved decks and verify the displayed deck identities.
- [ ] Verify portrait/landscape and small-screen layouts if supported.
- [ ] Verify accessibility labels, touch target sizes, text scaling and contrast.
- [ ] Verify process recreation/backgrounding does not corrupt local data.
- [ ] Verify low-storage and interrupted-import recovery.

## Distribution readiness

- [ ] Confirm owner-controlled release keystore is securely backed up.
- [ ] Confirm the same signing identity will be retained for every future update.
- [ ] Configure GitHub Actions signing secrets without committing secrets.
- [ ] Verify APK signature and package ID/version.
- [ ] Review data-source provenance and distribution permissions.
- [ ] Add final app icon, screenshots, store listing and support contact.
- [ ] Review trademark presentation and required disclaimers.
- [ ] Record release commit, CI run, APK checksum and test evidence.
- [ ] Obtain explicit owner sign-off.

## Current status

The checklist is a release gate, not a statement that all items are complete. The repository documentation must report the latest CI result accurately. Physical-device QA and a production-signed release remain separate from a successful compile.
