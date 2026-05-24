# Changelog

All notable changes to the Corp script. Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

Inline `// 1.9.X:` comments throughout `Corp.java` provide per-change-site rationale; this file is the cross-cutting summary.

## [Unreleased] — 2026-05-23 (positioning, dark core, spec-mode toggle)

### Added
- **`autoDetectTeamSpecs` toggle (1.9.99.226)** — new checkbox in the Spec tab. ON (default) preserves the team-aggregate auto-detect with the 1/(1+real teammates) divisor. OFF puts each bot on its own per-kill counts vs its own GUI target spinners, completely bypassing the team aggregate and the human-teammate multiplier. Use OFF when running with humans whose specs the coordinator can't see — set each bot's targets to what THAT account should personally do per kill.
- **`preWalkAroundCorp` helper (1.9.99.227-228)** — L-shape detour to a safe corner when a straight-line walk would cross Corp's 5×5 hitbox. Used by the encroachment-relocate path in `walkInChunksTo`. Wait predicate caps at 1500 ms with a 15-HP-drop bail so main-loop emergency-eat / panic-tele can fire during long walks.
- **`walkToSafeCoreMeleeTile` "force-tile then interact" (1.9.99.229-231)** — addresses OSRS's deterministic W>E>S>N BFS preference: when the dark core is touching Corp, the engine picks the W melee tile of the core which lies INSIDE Corp's hitbox, causing a guaranteed 60+ stomp. The helper picks a safe melee tile (filters out under-Corp candidates), scores by teammate separation (1.9.99.230) so we don't walk onto a tile someone else is on, walks there manually, then returns true so the caller's `core.interact("Attack")` fires from an already-adjacent tile (no BFS, no surprise).
- **Force-tile arrival verification (1.9.99.231)** — `walkToSafeCoreMeleeTile` now returns false if the bot didn't actually land on a safe melee tile (wall blocking path, core moved, etc.). Both core-attack callers honor the return value and skip the click instead of firing from the wrong tile. Trades a missed core hit for a guaranteed-no-stomp.

### Changed
- **Quadrant preference seeding (1.9.99.225)** — replaced hash-based RSN-to-angle with deterministic-by-sorted-index. For 2 bots: index 0 → 0° (east), index 1 → 180° (west); zero chance of collision regardless of RSN spelling. Bumped quadrant weight 6 → 10 so the assigned side beats marginal separation differences on equivalent candidates. Fixes "<bot-a>" + "<bot-b>" both north-favoring and ping-ponging between the same two corners.

### Fixed
- **Low-energy → infinite re-prep loop (1.9.99.224)** — `prepareSpecWeaponForCorp` gated the entire equip block on `Combat.getSpecialAttackPercent() >= getMinSpecEnergy()`. With energy = 10 and Arclight requiring 50, the gate was false → function returned with nothing equipped → state moved to `FIGHTING_CORP` → the 1.9.99.196 stuck-state recovery saw no weapon equipped → bounced back to `ENTERING_COMBAT` → ~10 iterations/sec forever. Now an `else if (isCorpAlive(corp))` branch equips the main weapon as the low-energy fallback.

---

## 1.9.25 — 1.9.99.223 (gap — see git history + inline comments)

Approximately 142 versions of accumulated coordinator (TCP + file shards, per-bot files, heartbeat thread, audit fixes), positioning (24-tile perimeter, teammate-claim penalty, encroachment relocate), spec-state (per-phase weapon prep, pre-activation, BGS damage tracking via hitsplats), death-detection (HP up-jump respawn, teammate-confirm bypass), and supply-management improvements. Per-change-site rationale lives in `// 1.9.99.X:` inline comments throughout `Corp.java`. See commit `c4138e3` for the bundled diff.

---

## [1.9.24] — 2026-05-16

Three fixes:

- **(a)** Friend-widget shortcut matches ANY descendant of [162, 39] (length>=2, path[1]==39). User saw shortcut on screen but the length==3 && path[2]==0 filter missed it — the dialog's child slot isn't always 0. Now match anywhere under [162, 39, *].
- **(b)** Corp-death detection requires EXPLICIT HP-at-zero observation. Pre-1.9.24 said "Corp dead" whenever health bar invisible AND not in combat — but the bar can be invisible 1-2 ticks during transitions while Corp is alive. Bot wasted kills transitioning to LOOTING prematurely. Now: LOOTING only fires when (a) we observe corp.getHealthBarPercent() <= 1.0, OR (b) corp NPC is gone AND we previously flipped the corpSeenAtZeroHp flag (per-kill reset). Real-player teams rely on direct observation; all-bot teams via coordinator would also flip this flag on kill signal.
- **(c)** "Poking with Arclight" fix. When the bot reaches kill phase (Corp HP < floor) with a spec weapon still equipped, queue Fang swap immediately. Pre-1.9.24 the swap happened only after a spec FIRE; if shouldUseSpecialAttack gated the spec (HP < floor), no swap queued and the bot poked with Arclight rest of the kill.

## [1.9.23] — 2026-05-16

Three fixes after the 1.9.22 test:

- **(a)** Bot stopped attacking Corp after the Elder maul -> Arclight phase rotation. Pre-1.9.23 the re-engage gate was "if (!isPlayerInCombat()) attack" — but isPlayerInCombat is true whenever Corp hits us, even if we aren't attacking back. With Arclight equipped (1H), the bot ended up in combat (taking hits) but not actually swinging at Corp; spec never fired and Corp died of teammate damage. Now uses isPlayerAttackingCorp which checks our actual interaction target, not just combat tag.
- **(b)** equipSpecWeapon now also equips a defender after wielding a 1H spec weapon (Arclight, Darklight, Emberlight, Dragon halberd). 2H weapons (Elder maul, DWH, BGS, Crystal halberd) take the offhand slot themselves so we skip defender for those.
- **(c)** Close-bank-before-tele extended to teleportToFeroxEnclave and the house tab tele (in addition to handleTravelingToCorp which got the fix in 1.9.22). All inventory-item tele clicks now close the bank first.

## [1.9.22] — 2026-05-16

Three behavior fixes:

- **(a)** No more emergency-escape just because the teammate isn't visible. Teammates can walk out of render or be obscured mid-fight; that's not an emergency. Spec dumping continues regardless of teammate visibility. Emergency triggers reduced to actual danger: low HP, no food, no prayer.
- **(b)** withdrawFood now skips a food type if the inventory already has the target count. Pre-1.9.22 we always tried to withdraw at least the target amount, even when the deposit step had left full counts behind — "cannot fit" errors at the bank.
- **(c)** handleTravelingToCorp closes the bank if still open before clicking the Games necklace. Bank intercepts inventory item-clicks while open, turning a "Corporeal Beast" tele into a quantity prompt — the bot would silently fail to tele and get stuck at Ferox.

## [1.9.21] — 2026-05-16

CRITICAL safety fix + walk-around-Corp fix.

- **(a)** Hard gate against typing into public chat. Production log showed the bot typing "<friend-host>" into PUBLIC CHAT after a state transition closed the friend-house dialog mid-sequence. Chatbox.isOpen() returns true for any chat window incl. public chat input, so the old gate didn't catch it. Now we require widget [162, 44] (the friend-house input field) BEFORE typing AND re-verify it BETWEEN typing and Enter. If either check fails, abort without typing.
- **(b)** Multi-step walk when straight line from player to chosen Corp position crosses Corp's hitbox. lineCrossesCorp samples points along the line; if any falls in corpArea, walk first to a waypoint on the player's side (5 tiles out from Corp center along the dominant approach axis), THEN to the target. Pre-1.9.21 RuneScape's A-star / L-shape pathing took the bot straight under Corp and we took stomp damage.

## [1.9.20] — 2026-05-16

Two fixes:

- **(a)** Friend-widget shortcut now targets the exact path [162, 39, 0] (first child of [162, 39] where the host name text lives — empty parent matched but clicking it ground-clicked). Filter requires path length 3 AND path[1]==39 AND path[2]==0.
- **(b)** Corp position picker: "same-side" bonus (+5) for tiles on the player's side of Corp's center. Pre-1.9.20 the picker only optimized self-distance, which could pick a tile that's close in Euclidean terms but on the OPPOSITE side of Corp — walker then routes through Corp's hitbox. Same-side bonus pulls the chosen tile toward the approach side. Caveat: if all safe tiles ARE on the far side, the walker can still cross Corp; would need a multi-step walk to fully fix.

## [1.9.19] — 2026-05-16

Earlier widget targeting attempt at [162, 39] (parent box). Diagnostic logging showed parent text was empty; superseded by 1.9.20 which targets the [..., 0] child instead.

## [1.9.18] — 2026-05-16

Fix vengeance trigger logic. 1.9.17 made handleReadyForFirstCast a no-op and relied on ACTIVE_CASTING (which fires after damage taken). But ACTIVE_CASTING triggers on ANY damage, including the damage we take during spec dump — so veng would still fire mid-spec-dump and the heal would be wasted before the kill. User clarified the real intent: cast vengeance only during the KILL PHASE — defined as (teamPhaseNeeded() == 0) OR (Corp HP < corpMinHpForSpec). New isInKillPhase() helper; handleVengeanceLogic returns early if not in kill phase. handleReadyForFirstCast restored to its original logic — gate is now the top-level kill-phase check.

## [1.9.17] — 2026-05-16

Three fixes after the 1.9.16 test:

- **(a)** Phase rotation timing. The in-line detector was calling refreshSpecWeaponForPhase BEFORE recordSpecUsed (deferred to next tick via pending XP check). Rotation saw the OLD phase count and missed the 4th-spec trigger. Moved rotation into processPendingSpecHit, AFTER recordSpecUsed runs. Also now equips the new weapon immediately on rotation so the next bar fires with the right weapon instead of waiting for handleSpecialAttack (which may not run if bot's stuck eating).
- **(b)** Vengeance no-op during pre-engagement and spec-dump phases. User: "we still veng and do things we dont want to do until we actually start killing it outside of spec dumping." handleReadyForFirstCast now returns immediately; vengeance only fires during ACTIVE_CASTING (after we've taken damage in real melee), which is what updateHealthTracking switches us to once HP drops.
- **(c)** Friend-widget shortcut now targets the user's exact path [162, 39] (parent widget), not just any widget with text + actions. Verifies host text in the widget or any child.

## [1.9.16] — 2026-05-16

Two flow fixes after the 1.9.15 test:

- **(a)** Prep now runs IN PARALLEL with the walk to the boss room. Pre-1.9.16 handleEnteringCombat ran all prep (pot drink, slot eat, equip, pre-activate spec, veng cast) FIRST, then called moveToCorpBossRoom. User saw 11s of prep in the lobby before any walking started. OSRS walks are server-side — once we click the passage, the player keeps moving while we open inventory and click items. Now: kick off the walk first (fire-and-forget), then run prep clicks concurrently, finally wait for arrival.
- **(b)** Friend-widget shortcut now requires an action, not just text containing the host name. Pre-1.9.16 the filter matched the static text-label widget which has no actions; widget.click() on it fell through to a "click ground" and the bot walked off into the distance instead of teleing to the friend's house.

## [1.9.15] — 2026-05-16

Flow restructuring after the user pointed out several order-of-operations issues:

- **(a)** Ferox banking now uses GlobalWalking.walkToBank instead of LocalWalking-walking to a fixed tile. Ring-of-Dueling teleport lands the player outside the bank's render range, so LocalWalking can't path. The bot was stuck "looking for restoration pool" forever.
- **(b)** Removed vengeance casting and spec prep from handleWaitingForTeam. Waiting for teammates in the lobby should be a true idle — no clicks, no casts. Veng gets healed off before we engage anyway, and lobby-prep was firing the failing Ice-Plateau-instead-of-Vengeance cast.
- **(c)** Both moved into handleEnteringCombat which runs WHILE we walk into the boss room. Prayer + spec-button + veng all activate as instant clicks during the walk, so by the time we see Corp everything is queued.
- **(d)** Friend-portal widget shortcut now searches root 162 by name instead of by hardcoded path [162, 39, 0] — same fragility we hit with the Vengeance widget at [218, 142]. Path-agnostic match should resolve "Last name: <friend-host>" reliably.

## [1.9.14] — 2026-05-16

Three fixes:

- **(a)** Vengeance now identified by NAME, not by fixed widget path. Pre-1.9.14 we trusted indexPath[1] == 142 to be Vengeance Self, but the spellbook widget tree shifts between game versions/pages; on this user's client [218, 142] is Ice Plateau Teleport. Bot was teleporting to Ice Plateau instead of casting Vengeance. Now samples getName / getComponentName / getText and requires "vengeance" in at least one of them, while still excluding "other".
- **(b)** 700±200ms settle delay before clicking the pool. Just-arrived players sometimes see the pool object on the next render tick after the player tile updates; clicking too early can miss the object and hit empty ground (walk-here).
- **(c)** Same settle delay before clicking the Ferox bank chest (both in the regular banking path and in death-recovery WITHDRAW step).

## [1.9.13] — 2026-05-16

Four fixes after the 1.9.12 test:

- **(a)** Pool drink: left-click instead of right-click menu. interact("Drink") opens the right-click menu and selects "Drink"; if the menu rendering races against the click we could end up clicking the wrong option. The pool's default left-click IS "Drink" — pool.click() is simpler and less prone to misclicks.
- **(b)** hasAcceptableTeammatesInBossRoom required the bot to BE in the boss room before it would check for teammates there. Result: bot tele'd back from POH into the lobby, saw "no teammates in boss room" (because itself wasn't there), waited in lobby forever while teammates were already fighting Corp. Now checks each player's tile against the corpCave area regardless of where the bot itself is.
- **(c)** Removed Pool object name and Jewellery box name GUI fields. Detection is action-based ("Drink" / "Corporeal Beast") and works across all tiers — name was unused. Fields kept @Deprecated in settings for backward compatibility with old saved profiles.
- **(d)** W330 VALIDATE_POOL and TELE_TO_CORP cases also switched to action-based detection. isInOwnHouse() too.

## [1.9.12] — 2026-05-16

After tele back from POH, ALWAYS go to WAITING_FOR_TEAM. Pre-1.9.12 handleTeleportingBackToCorp would loop straight back into PREPARING_RESTORATION_CYCLE if more cycles were available — but we just refilled spec and should USE it on Corp first. Production log: bot landed in Corp's lobby with spec=100%, immediately ran "Using initial special attacks (0/4)" — Corp isn't visible from the lobby tile, state errored with "Corp not found", fell through to emergency Ferox tele. The mid-fight restoration trigger fires the NEXT POH cycle naturally once the spec bar drains again, so always-WAITING_FOR_TEAM is correct.

## [1.9.11.1] — 2026-05-16

Fix 1.9.11 compile error: Mouse.getPosition() and InventoryItem.getStackRectangle() aren't exposed in the public TRiBot SDK. Replaced "closest to mouse" with "closest by slot index" — InventoryItem DOES expose getIndex(), and the inventory is a 4-wide grid so slot proximity correlates with on-screen distance. Captures the shark's slot index before clicking, then picks the karambwan with the closest abs(index - sharkSlot).

## [1.9.11] — 2026-05-16

Two fixes after diagnosing the 1.9.10 typing bug:

- **(a)** Friend-name typing dropped the first 4 characters. The chatbox dialog opens visually but the input field isn't always focused for keyboard input in the same tick. Production evidence: bot typed "<friend-host>" but the remembered last-typed buffer was "ToAfK" (first 4 chars eaten by focus transition). Added a 400-700ms settle wait between dialog-open and typing.
- **(b)** Combo-eat picks the karambwan slot CLOSEST to the current mouse position. After the shark click the cursor sits on the shark slot; the nearest karambwan minimizes mouse-travel time so both eats land in the same game tick. Pre-1.9.11 took the first karambwan returned by Query.inventory which is typically the top-left slot — often far from the shark we just clicked.

## [1.9.10] — 2026-05-16

Two fixes after the 1.9.9 test:

- **(a)** Restoration failure fallback. When house entry fails MAX_HOUSE_ENTRY_ATTEMPTS times (e.g. the POH host is offline), the bot was left standing at the friend's-house portal area and transitioning to WAITING_FOR_TEAM — which expects to be in Corp's lobby, not outside the cave. Bot waited forever for teammates. Now falls back to a Games-necklace tele back to Corp; if no necklace, banks.
- **(b)** Combo-eat timing tightened (100-300ms → 40-80ms). Karambwan in OSRS bypasses the standard eat cooldown so both heals can land in the SAME game tick — but only if the karambwan click is fast. Pre-1.9.10 wait was long enough to push karambwan into the next tick, halving the burst-heal speed. Combat-log evidence: bot was emergency-combo-eating twice between specs because single-tick burst wasn't outpacing Corp damage.

## [1.9.9] — 2026-05-16

XP-based spec hit detection. Pre-1.9.9 every spec fire (hit OR miss) bumped the phase counter, so the bot would rotate weapons after 4 spec ATTEMPTS even if half missed. Now we snapshot melee XP (Attack + Strength + Defence + Hitpoints, NOT Magic — Magic XP is vengeance) at every spec activation. After the spec fires (energy drop), we wait one tick for XP to register and check the delta: XP increased → spec HIT → recordSpecUsed XP unchanged after 2s → spec MISS → don't record Real-teammate multiplier is unchanged: each confirmed hit still counts × (1 + realTeammates) in the team aggregate per the user's spec.

## [1.9.8] — 2026-05-16

Three follow-up fixes after the 1.9.7.1 success:

- **(a)** Defer phase-rotation equip. Rotating Elder maul → Arclight always happens at energy 0 (just spent the last spec of the bar), so equipping Arclight this tick is wasted — we can't fire on it anyway. Pre-1.9.8 the equip ran in the boss room (2 seconds of swap animation while Corp keeps hitting). Now we update chosenSpecWeapon but defer the equip until the next bar (handleSpecialAttack already equips the chosen weapon if it isn't on). Saves the in-room exposure.
- **(b)** Pool restoration actually verifies. Pre-1.9.8 useOrnatePool said "Pool restoration timed out, but continuing" — bot teleported back with 0% spec and low HP and died on Corp. Now retries up to 3 drinks, then refuses to tele if stats didn't actually restore.
- **(c)** Two more coordinator-gated refreshSpecWeaponForPhase calls (handleSpecialAttack + handleUsingInitialSpecs). Both gates dropped — phase rotation works for solo via buildSoloAggregate. handleUsingInitialSpecs also no longer calls equipMainWeaponFast before tele (mirrors the 1.9.7 fix elsewhere).

## [1.9.7.1] — 2026-05-16

Hotfix follow-up to 1.9.7:

- **(a)** Pool/jewellery-box matching is now action-based, not name-based. The actual object names are "Ornate pool of Rejuvenation" (different word order from 1.9.7's "rejuvenation pool" nameContains) and "Ornate Jewellery Box" (case-mismatched with nameEquals("Ornate jewellery box")). Both expose stable actions ("Drink" / "Corporeal Beast") that work across all tiers.
- **(b)** Two more portal double-click bugs of the same shape as enterFriendHouse: isAtHousePortal and the W330 path both had .filter(p -> p.interact(...)) which clicks as a side-effect of filtering. Fixed both with .filter(p -> p.getActions()...)
- **(c)** Another instant-true Waiting.waitUntil(1000, () -> true) in the Ferox-pool retry path — replaced with a real waitNormal settle delay.

## [1.9.7] — 2026-05-16

Four fixes after the 1.9.6 test:

- **(a)** enterFriendHouse double-clicked the portal. The filter lambda called portal.interact(...) which actually clicks as a side-effect of filtering, and then the code called interact() again outside the filter. Use .getActions().contains() for filtering, interact exactly once.
- **(b)** handleFriendNameDialog typed the host name then pressed Enter inside the same tick — the typing- then-wait used Waiting.waitUntil(2000, () -> true) which returns instantly. Replaced with a real 900±200ms wait so the game-side typing buffer actually registers before Enter.
- **(c)** useOrnatePool() required an exact match against settings.poolName ("Ornate rejuvenation pool" default). Friend's house has a different pool tier → "pool not found" → emergency Ferox tele even though we'd successfully entered the house. Now uses the same broad nameContains check as isInFriendHouse — any rejuvenation / restoration / revitalisation pool works.
- **(d)** handleUsingInitialSpecs called equipMainWeaponFast ("Switching back to main weapon before house teleport") right before tele. The spec weapon should stay equipped through the POH cycle. User: "we want to spec twice and tele out and that's essentially it. Anything else is wasted input." Removed the swap; Fang swap reserved for the kill-phase transition only. Plus log spam fix: handleEnteringFriendHouse no longer logs "Attempting to enter..." every 50ms tick while throttled — only on the actual attempt.

## [1.9.6] — 2026-05-16

Three fixes after the user's 1.9.5 test:

- **(a)** In-line spec detector swapped to Fang when shouldStartRestorationCycle returned false at the bar boundary, but the same check passed 6 seconds later in the mid-fight handler — bot wasted a Fang-swap that immediately got un-done by the POH tele. Dropped the Corp- HP-above-floor gate from shouldStartRestorationCycle. The HP gate is for SPEC-FIRE decisions (don't waste a spec on a near-dead Corp); the tele decision should be "always tele if we want more specs". After POH, if Corp HP is below floor we fall through to kill-phase melee via existing spec-fire gates.
- **(b)** isInFriendHouse() only checked for one specific pool object. The friend's house has both a portal (also in entry lobby) and POH furniture; if the pool wasn't loaded in render yet OR was a different tier, the bot would re-click the portal endlessly. Now matches any pool / jewellery box / spirit tree.
- **(c)** Per-kill reset (in handleLooting) now also clears the queued spec-weapon switch-back — previously a queue scheduled mid-bar could survive into the next kill's prep and fire at the wrong moment.

## [1.9.5] — 2026-05-16

Phase rotation now happens mid-bar. The full Corp meta is Phase 1 → Phase 2 → Phase 3: Phase 1 (defense reducers): 4 Elder maul / DWH Phase 2 (combat-level reducers): 20 Arclight / Darklight / Emberlight Phase 3 (HP drain): 200 BGS damage Pre-1.9.5 the in-line spec detector never called refreshSpecWeaponForPhase(), so the bot stayed on Elder maul forever even after the 4 phase-1 specs landed. Also refreshSpecWeaponForPhase itself was gated on settings.coordinatorEnabled, so solo bots wouldn't rotate even if the call was added. Fixes: dropped the coordinator gate (rotation works for solo via buildSoloAggregate); the detector now calls refreshSpecWeaponForPhase after every recordSpecUsed and equips the new weapon if the choice changed.

## [1.9.4] — 2026-05-16

Strategy + survival fixes after a death log. Insight: during spec-dump phase the spec weapon IS the main weapon. Fang only comes out at the kill-phase transition. Pre-1.9.4 the bot swapped to Fang after every spec bar — extra weapon-swap animation lock during which Corp kept hitting.

- **(a)** In-line spec detector: on bar exhaust, route INSTA to PREPARING_RESTORATION_CYCLE when shouldStartRestorationCycle says we still want more specs. Fang swap only fires when restoration is no longer wanted (kill phase). The spec weapon stays equipped through the POH cycle and into the next bar.
- **(b)** Removed isOnLunarSpellbook() probe gate from handleVengeanceLogic. The probe returned false positives on the user's client even after 1.9.3 text-filter relaxation, locking out vengeance for the whole session. castVengeance now just attempts the cast; widget-not-found logs sufficient diagnosis if not on Lunars.
- **(c)** New INTERNAL_PANIC_TELE_HP = 8. At HP <= 8 (one Corp hit from death) we skip eating and bail straight to EMERGENCY_ESCAPE — Ferox tele / Games necklace / run / logout.
- **(d)** Emergency HP check: if emergencyComboEat returns false (no food available), tele out via EMERGENCY_ESCAPE instead of standing and dying. Also dropped the spec-cancel mouse click during the emergency tick — wasted motion when we should be eating/teling.
- **(e)** HP guard on handleSpecWeaponSwitchTiming: postpone the Fang-swap animation if HP <= INTERNAL_COMBO_EAT_HP. Production log showed the bot dying mid-swap because equipMainWeaponFast blocks the thread.

## [1.9.3] — 2026-05-16

Fix vengeance not casting. The isVengeanceSelfWidget filter (added in 1.7.4 to guard against Vengeance Other) required widget text to contain "vengeance" — but Lunar spellbook widgets are sprite-based and may return empty text via getText().orElse(""). The path filter (root 218, child 142) already uniquely identifies Vengeance Self; the text-contains check rejected the real widget when text was empty, causing isOnLunarSpellbook() to return false and the entire vengeance system to silently skip every cast for the session. Dropped the text-contains- vengeance requirement; kept the "other" guard.

## [1.9.2] — 2026-05-16

Fix spec-button spam-click. The in-line spec detector triggered on "energy < 100" which is true continuously after spec #1 — every loop iteration fired the detector, called recordSpecUsed(), and toggled the spec button. Result: 30+ "Pre-activated spec fired" logs per kill while the energy bar stayed stuck at 50%, only 2 real specs fired but phase counter inflated to 30+ (which then wrongly satisfied teamPhaseNeeded() and blocked the POH restoration tele). Now the detector triggers on real energy DROPS via a new lastSeenSpecEnergy field. The field is updated after every Combat.activateSpecialAttack success (lobby prep, in-room prep, mid-fight pre- activate, mid-bar re-activate) so each "fire" corresponds to actual energy consumption.

## [1.9.1] — 2026-05-16

Fix mid-bar spec bail. 1.9.0 added multi-spec-per-bar but the canFireAnotherSpecOnThisBar gate also checked Corp HP floor + phase targets. In a real team kill, Corp's HP can drop below 1700 between the bot's first spec firing and the gate's post-spec evaluation — wrongly cancelling spec #2 on a bar already committed to. Those gates belong at the BAR boundary (shouldStartRestorationCycle before the next bar), not mid-bar. Once we commit to a bar, drain it. canFireAnotherSpec now only checks energy + Corp alive, and logs the result so we can diagnose if it ever bails unexpectedly.

## [1.9.0] — 2026-05-16

Three fixes from a 1.8.9 production log:

- **(a)** Multi-spec per bar. The in-line spec detector unconditionally queued switch-back-to-main after the first spec. With Elder maul (50%) and 50% energy left in the bar, the bot should fire a second spec before switching off — but it didn't, so 1.8.8's mid-fight restoration trigger never saw a depleted bar. Now after each in-line spec fire we check canFireAnotherSpecOnThisBar() (energy >= cost AND phase targets not met AND Corp HP above floor) and re-activate spec instead of switching back. Switch-back only fires when the bar can't support another spec.
- **(b)** Lobby pre-spec. handleWaitingForTeam now calls prepareSpecWeaponInLobby() before transitioning to ENTERING_COMBAT, so the bot drinks super combat, eats karambwan for slot, equips spec weapon, and pre-activates spec WHILE STILL IN THE LOBBY. Pre-1.9.0 the entire prep sequence ran under Corp's nose — ~10 seconds of stomp damage absorbed while drinking/equipping. The in-room prepareSpecWeaponForCorp becomes idempotent: already-prepped → no-op.
- **(c)** Skip prep when HP critical. prepareSpecWeaponForCorp now bails to emergencyComboEat if HP <= INTERNAL_COMBO_EAT_HP (50). Drinking a potion at 50 HP under sustained Corp damage was getting the bot killed; it's better to start combat un-prepped and let shouldUseSpecialAttack pick up the spec later once HP recovers.

## [1.8.9] — 2026-05-16

Survival fixes after a death log: bot was firing pre-activated spec and swapping weapons at single- digit HP instead of eating.

- **(a)** New top-of-handleFightingCorp short-circuit at INTERNAL_EMERGENCY_HP: eat (combo if possible), cancel pre-activated spec so the next attack doesn't waste it, skip swap/vengeance/positioning this tick, return. Dark-core check still runs (being inside the core is what's killing us; dodging out matters more than eating in place).
- **(b)** New INTERNAL_COMBO_EAT_HP = 50. Pre-1.8.9 combo-eat only fired at <= 15 HP, which let the bot fall into one-shot range before reacting. handleHealthAndPrayer now combo-eats (38 HP from Shark+Karambwan) whenever HP drops below 50, outpacing Corp's damage.

## [1.8.8] — 2026-05-16

Restoration model rebuilt to match real Corp meta: spec → POH → spec → POH until phase targets met or Corp HP drops below the floor (a teammate is actively damaging it). Pre-1.8.8 the bot would spec once per kill and never tele to POH for more. Changes:

- **(a)** Restoration is now PER-KILL, not per-trip. resetRestorationTracking() runs at the end of handleLooting() so every new kill starts with a fresh cycle budget and zero spec counters. Phase aggregator counters were already per-kill via coordinatorOnKillEnded().
- **(b)** shouldStartRestorationCycle() gate rewritten: triggers when spec is DEPLETED (was inverted — pre-1.8.8 required percent >= minSpecEnergy, backwards) AND phase targets not met AND Corp HP above floor. totalRestorationCycles is now just a safety upper bound (default 10), not the real loop driver.
- **(c)** Mid-fight restoration trigger added to handleFightingCorp — once spec is dry in combat, the bot breaks out to POH instead of standing there meleeing with no spec. Loop naturally terminates when teamPhaseNeeded()==0 or Corp HP drops below corpMinHpForSpec (a real teammate is killing it — go melee and help).
- **(d)** corpMinHpForSpec default 600 → 1700. This is the restoration-loop termination floor, not a per-spec cooldown. Stats stay reduced; only HP regens. 1700 ≈ Corp lost ~15% HP → time to stop dumping defense-reducers and join melee.
- **(e)** INTERNAL_SPECS_PER_CYCLE (hardcoded 2) replaced with specsPerFullBar() which derives from the cheapest owned spec weapon's cost. Arclight (25%) now correctly fits 4 specs per cycle instead of being clipped at 2.
- **(f)** assignUniqueCorpPosition() now factors in self- distance. Pre-1.8.8 it only weighted "max separation from other players", so when no teammates were nearby it just picked the first safe offset in iteration order (East). With a NW approach to Corp, picking East routed the player under Corp's hitbox. Now picks the closest safe offset on tie.
- **(g)** Mid-fight repositioning. handleCorpPositioning() was a defined-but-never-called method, so the bot picked one tile at engage and stayed there for the entire kill. When Corp roamed (esp. through narrow corners) the player ended up inside Corp's 5x5 hitbox taking free stomp damage. handleFightingCorp now has two checks at the top: (i) emergency reposition if corpArea.contains(myTile), (ii) periodic 3s reposition if isInGoodCorpPosition returns false (drifted from assigned offset).

## [1.8.7] — 2026-05-15

Spec rotation finally works when joining an in-progress teammate kill. Three coupled bugs in the entering- combat path:

- **(a)** handleEnteringCombat "Joining existing combat" branch only set state=FIGHTING_CORP without ever calling corp.interact("Attack") — the bot stood there while the pre-activated spec sat queued. The branch is gone; we always attack now and just log differently based on whether Corp was already in combat.
- **(b)** When the pre-activated spec eventually fired (via the FIGHTING_CORP tail re-engage on the next eat), it consumed energy outside the USING_SPECIAL_ATTACK state, so queueSpecWeaponSwitchBack never ran — bot stayed on Elder maul the rest of the kill. New detector at the top of handleFightingCorp notices: specWeaponReadyForUse=true + !Combat.isSpecialAttackEnabled() + spec weapon equipped + spec% < 100 → records the spec for team phase tracking and queues switch back to Fang.
- **(c)** corpMinHpForSpec default 1200 was the historical value before 1.7.3 actually wired the gate. With the gate enforced, a 1200 floor blocks Phase 2/3 specs once the team drops Corp past 60%. Lowered default to 600 — keeps "don't spec a near-dead Corp" intent while letting team rotations finish.

## [1.8.6] — 2026-05-15

Two more bugs surfaced in production: Ferox-tele loop and spec-weapon stuck-on after the first spec. - isAtFeroxEnclave: tile-coord check is now primary (x:3120-3160, y:3620-3650, plane 0). Old check relied on "Ferox" NPC / "Pool of Refreshment" / "Bank chest" being in render distance, but the Ring of Dueling drop point doesn't see any of those until the player walks a few tiles. The banking flow's "if (!isAtFeroxEnclave()) tele" loop then burned through every ring charge in inventory in a few ticks (the bug report said "teleported to ferrox using all the rings over and over without walking to the bank"). Object detection retained as fallback. - equipMainWeaponFast: now actually verifies the wield. The old `success = true` initial was never overwritten — the function always returned true even when getAvailableMainWeapon() returned null or the click failed silently. handleSpecWeaponSwitchTiming cleared the switch-back queue on that bogus "success", so a failed Fang re-wield after a spec would leave the bot stuck on the spec weapon for the rest of the kill. Now returns false on any failure path with a specific log line; defender wield remains best-effort so accounts that don't carry one aren't penalised.

## [1.8.5] — 2026-05-15

Two combat-loop bug fixes surfaced in production logs. - comboEatToFreeSlot / ensureInventorySlotsFree: The old "Inventory.isFull is now false" success check returned immediately whenever the inventory wasn't full to begin with — so we logged "Combo- eating Cooked karambwan" but never actually ate anything, came back 1 slot short, and bailed with "Cannot free 2 slots for 2H swap". Net effect: bot never executed its initial Elder maul / DWH spec when starting a kill with a near-full inventory. Both helpers now compare inventory count before and after eating. ensureInventorySlotsFree also loops until the target is reached (or food runs out, capped at 28 attempts) instead of running only `toFree` iterations and stopping. - stepAwayFromCore: the 8 candidate offsets were all 1-tile neighbours, which land inside Corp's 5x5 hitbox when the bot is adjacent to Corp — every tile got rejected and the bot stayed on the core's spawn tile while ESC-eating in a loop ("STEP-AWAY: no walkable target away from core" repeated in the log). Now we try 2/3/4-tile offsets in the away direction first, fall back to perpendicular/short steps last, and explicitly skip any tile that intersects Corp's hitbox (corp.getArea().contains).

## [1.8.4] — 2026-05-15

Friend-house dialog: corrected widget path for the "Last name: <rsn>" shortcut from [162, 38, 0] to [162, 39, 0]. Old path silently fell through to the typed-name path on every entry — slightly slower but worked. Now the one-click shortcut fires when we've visited the host before. Text comparison is now case-insensitive + color-tag-stripped so "<friend-host>" matches the widget's "<friend-host>" text. (Reference: typed-name input field is at [162, 44] — Keyboard.typeString routes to it via chat focus.)

## [1.8.3] — 2026-05-15

Friends-house entry timing tightened to match the new in-game mechanic. Entering a friend's house no longer requires the host to have their house "open" — same- world presence is enough. Failure modes are now real (typo, offline, wrong world) rather than timing, so retries should fail fast: MAX_HOUSE_ENTRY_ATTEMPTS: 5 -> 3 HOUSE_ENTRY_RETRY_DELAY:  5000-7000ms -> 1500-3000ms Worst-case retry window drops from ~30s to ~9s. Operationally: bot teammates / friend partners no longer need to coordinate "open my house" calls.

## [1.8.2] — 2026-05-15

Bug fix bundle: jewelry charge floor + hard-stop. - Charged-jewelry top-up: hasChargedRingOfDueling / hasChargedGamesNecklace returned true even for a (1)-charge variant, so the bot would leave the bank with a 1-charge ring, burn it on the next Ferox trip, and strand itself (the log showed exactly this chain ending in "No Ring of Dueling found"). New ringOfDuelingNeedsTopUp / gamesNecklaceNeedsTopUp checks return true when the highest dose in inventory is below JEWELRY_TOP_UP_THRESHOLD (4). withdrawEssentialItems now uses these — a low-dose ring/necklace triggers a fresh (8) withdraw. - Hard-stop on supply exhaustion: the "STOPPING SCRIPT" path used to call Login.logout() and return without flipping the `running` flag. The main while-loop would continue and the bot would try to bank again, fail again, and so on. Now we signalSessionEnd() to notify coordinator-aware teammates, set running=false, then logout. Clean exit.

## [1.8.1] — 2026-05-15

Real-teammate awareness for mixed bot + human teams. - teamPhaseNeeded() now works with coordinator off: buildSoloAggregate() constructs an aggregate from mySnapshot when no coordinator file exists, so the bot's own per-kill spec counts feed back into the phase logic instead of being ignored. - Real-teammate boost: getRealTeammateRSNs() derives "human partner" RSNs as acceptableTeammates MINUS botTeammates MINUS self. countRealTeammatesNearby() checks which of those are visible. teamPhaseNeeded then multiplies phase1Specs / phase2Specs / phase3BgsDamage by (1 + nearbyHumans) so a 1 bot + 1 human pair stops getting stuck on Phase 1 forever. - No new GUI fields. The derivation means users who want a bot teammate to count as a bot put it in BOTH acceptableTeammates AND botTeammates; humans go only in acceptableTeammates. - Approximation caveat: the multiplier assumes each human contributes proportionally to bot output. Fine for partners doing similar specs; off for partners using a wildly different rotation. Users who want precise control can leave the human off acceptableTeammates so no boost is applied.

## [1.8.0] — 2026-05-15

Big customization sweep. Strips out fake-customization settings and replaces hand-maintained checkboxes with auto-detection. Only the truly user-facing levers remain in the GUI. - Spec-weapon auto-detection: getOwnedSpecWeapons() scans Equipment + Inventory + Bank (when open) for every name in ALL_SPEC_WEAPONS and caches the result. Cache is invalidated after each bank trip. Replaces the Per-account tab's checkbox map; all consumers (shouldKeepItem, pickSpecWeaponForCurrentPhase, detectAndSetSpecWeapon, mySnapshot.availableWeapons) now read from the detected list. - Mode-aware spec budget: getTripSpecBudget() returns per-trip spec capacity. FEROX_ONLY = current bar + 1 (regen approximation); POH modes = current + cyclesRemaining * fullBar. shouldDumpSpecsAggressively() is true in FEROX_ONLY and flips shouldSpecNowConsideringTeam into "every spec is worthwhile DPS" mode when team phases are done or our weapon doesn't match the current phase. - getMinSpecEnergy() now derives from the cheapest owned spec weapon's cost instead of a user spinner. - Moved to internal constants (no longer user-tweakable): INTERNAL_PHASE1_TARGET (4), INTERNAL_PHASE2_TARGET (20), INTERNAL_PHASE3_BGS_DAMAGE (200), INTERNAL_EAT_BELOW_MAX_HP (21), INTERNAL_EMERGENCY_HP (15), INTERNAL_DRINK_PRAYER_THRESHOLD (20), INTERNAL_CORP_LOW_HP_VENG_STOP_RAW_HP (85), INTERNAL_COORD_WRITE_INTERVAL_TICKS (5), INTERNAL_COORD_STALE_THRESHOLD_MS (10000), INTERNAL_SPECS_PER_CYCLE (2), INTERNAL_TARGET_SHARKS (10), INTERNAL_TARGET_KARAMBWANS (9), INTERNAL_TARGET_SUPER_RESTORES (2), INTERNAL_TARGET_SUPER_COMBAT (1), INTERNAL_MIN_FOOD_COUNT (10), INTERNAL_MIN_PRAYER_DOSES (4). These are derived from Corp game mechanics, not user preference. - accountRole is now forced to "auto" — the role dropdown was non-functional anyway. Existing roles in saved profiles are ignored. - GUI trimmed to four tabs and the truly user-facing levers only: Combat:     mainWeapon, food1, food2, useVengeance, combatPotion, showOverlay Spec:       corpMinHpForSpec, totalRestorationCycles, useLegacyDarkCoreLogic POH / Team: pohSource, friendName, isPohHost, poolName, jewelleryBoxName, coordinatorEnabled, waitForTeammateSpec, designatedWorld, w330MaxHostAttempts, acceptableTeammates, botTeammates Loot:       valuableLoot - Removed tabs: Inventory targets, Per-account. Removed controls: minSpec spinner, specsPerCycle, phase target spinners, HP threshold spinners, low-HP veng-stop spinner, role dropdown, spec-weapon checkboxes. - CorpSettings keeps the deprecated fields (availableSpecWeapons, accountRole, eatBelowMaxHp, etc.) for back-compat profile loading — Gson tolerates extra fields. They're never read by runtime code now.

## [1.7.4] — 2026-05-15

Vengeance robustness: widget guard + rune pouch gate. - isVengeanceSelfWidget() filter requires the widget's display text to contain "Vengeance" but NOT "Other", plus the "Cast" action. Applied to both the cast path and the spellbook probe so a stray Vengeance Other widget can't satisfy either. - hasVengeanceRunes() gates handleVengeanceLogic on Rune pouch / Divine rune pouch / all loose runes (Astral + Death + Earth) being present. One-shot warning if missing; auto-recovers if the pouch is re-acquired mid-session via a bank trip.

## [1.7.3] — 2026-05-15

isCorpHealthAboveSpecThreshold now actually honors settings.corpMinHpForSpec. Original implementation just returned isHealthBarVisible() — the GUI setting was dead. Maps Corp's visible health-bar % to absolute HP (Corp has 2000 max) and compares against the setting. Removes the long-standing OPEN-block note about the 1200/1700 value/comment mismatch.

## [1.7.2] — 2026-05-15

Robustness pass: spellbook gate, inventory-full handling, status overlay. - Spellbook check: handleVengeanceLogic now probes for the Vengeance widget at [218, 142] before casting. If the player isn't on Lunars the cast widget won't exist; we cache the result, log a one-shot warning, and skip vengeance for the rest of the session instead of spamming silent failures. - Inventory-full handling: new ensureInventorySlotsFree(n) helper combo-eats Cooked karambwan (falls back to a primary food) until n slots are free. * equipSpecWeapon now calls it before wielding a 2H spec weapon (Elder maul / DWH / BGS / Crystal halberd / Dragon halberd). Stops the silent "wield failed because inventory is full" case where the defender + previous main weapon both need to come back to inventory. * handleLooting calls it on Inventory.isFull() so a Corp drop landing during a full inventory still gets picked up. - Status overlay: small always-on-top Swing window (settings.showOverlay) showing state / spec weapon / kills / deaths / runtime / coordinator status / team phase needed / session-end pending flag. killCount increments in coordinatorOnKillEnded(); deathCount increments at DeathRecovery DONE. Toggled via the Combat tab's "Show live status overlay window".

## [1.7.1] — 2026-05-15

Session-end signaling on supply exhaustion. - AccountSnapshot gains sessionEndRequested + sessionEndReason so a bot that can't recover can broadcast through the coordinator that the whole team should wrap up. - Death recovery now checks bankHasGamesNecklace() before the withdraw attempt. If no necklaces of any dose are present, the bot calls signalSessionEnd("Out of Games necklaces ..."), force-publishes the snapshot, and transitions to EMERGENCY_ESCAPE for a clean logout instead of looping forever between WITHDRAW and TELE_TO_CORP. - Pre-dispatch in executeCurrentState: when any live teammate's snapshot has sessionEndRequested=true, set local sessionEndPending. handleLooting checks this flag and routes the bot to EMERGENCY_ESCAPE after the current kill rather than starting a new one. Teammate filter (botTeammates) is honored so unrelated players in the same coordinator file don't trigger a cascade.

## [1.7.0] — 2026-05-15

W330 random POH mode (Phase I, complete). - pohSource=W330_RANDOM now operational. The bot: captures the current world, hops to W330, uses the standard "Teleport to house" tab "Outside" option (the bot's own POH is set to Rimmington as part of its gear setup, so this lands at the Rimmington portal — no walking), picks a random nearby player as a host candidate, enters their house via the friend's-house portal dialog, validates that settings.poolName is present, uses the pool, then teleports back to Corp (host's jewellery box preferred, Games necklace fallback) and hops back to the captured world. - New BotState.W330_RESTORATION with an inner FSM: CAPTURE_HOME -> HOP_TO_W330 -> TELE_TO_HOUSE_OUTSIDE -> ENTER_HOUSE -> VALIDATE_POOL -> USE_POOL -> TELE_TO_CORP -> HOP_HOME -> DONE. - Bad-host retry: settings.w330MaxHostAttempts (default 3) caps how many random advertisers to try in a cycle. After the cap we bail and resume Corp DPS without restoration (we'll try again next cycle). - settings.designatedWorld: explicit return world. 0 means "remember whichever world we were on when restoration started" — works out of the box for most setups. - detectDeath() ignores W330_RESTORATION so death recovery doesn't trigger while we're hopping worlds. - GUI: POH/Team tab gains "W330 return world" and "W330 max host tries" spinners.

## [1.6.0] — 2026-05-15

POH-less modes (Phase I, partial). - New settings.pohSource enum-as-string with five values: * OWN_HOUSE     - this account's own ornate pool. * FRIEND_HOUSE  - friend's house by manual RSN. * BOT_HOST      - resolve teammate-bot host via coordinator (publishes isPohHost flag). * W330_RANDOM   - reserved for 1.7.0 (random POH from the public-house world, Rimmington portal entry). * FEROX_ONLY    - skip POH entirely. HP/prayer get restored via Ferox Pool of Refreshment during normal bank trips; spec only refills from natural regen. - settings.useOwnHouse (1.5.x) is now legacy; migration path in migrateLegacySettings() maps true -> OWN_HOUSE, false -> FRIEND_HOUSE so existing profiles keep working. - settings.isPohHost: when set on the host bot, other teammates configured pohSource=BOT_HOST will discover this account's RSN from the coordinator and use it as their friend's-house entry name. - resolveBotHostName() reads the coordinator's TeamState, filters by live + isPohHost + (optional) botTeammates allow-list, and returns the host's RSN. Falls back to settings.friendName if no host is found. - getEffectiveFriendName() now drives enterFriendHouse() and handleFriendNameDialog() — bot-host mode types the dynamically-resolved RSN into the portal dialog. - shouldStartRestorationCycle() short-circuits to false for FEROX_ONLY and W330_RANDOM (no crash on unimplemented). - GUI: POH/Team tab replaces the useOwnHouse checkbox with a pohSource dropdown + a "POH host role" checkbox.

## [1.5.2] — 2026-05-15

Customization audit + bug fixes for public release. - Spec-weapon initial detection rewritten: iterates settings.availableSpecWeapons in phase order (1->2->3) instead of the hardcoded Elder maul / Darklight check. DWH-only and BGS-only setups now work out of the box. - hasRequiredItemsWithPOH / hasRequiredItems use hasAnyOwnedSpecWeapon() + hasMinimumFood() instead of the hardcoded Elder maul + Shark + Karambwan checks. - Defender selection: equipAnyDefender now iterates a tier priority list (Avernic > Dragon > Rune > Adamant > ... > Bronze) so it picks the best defender carried, with a name-contains fallback for custom servers. - Combat potion configurable: new settings.combatPotionType + COMBAT_POTION_OPTIONS dropdown (Divine super combat / Super combat / Crystalised super combat / custom). getCombatPotionNames() builds (4)/(3)/(2)/(1) variants; SUPER_COMBAT_NAMES static constant is gone. - Pre-trip gear verification: verifyTripGear() blocks leaving the bank without main weapon + defender + spec weapon + necklace + ring + minimum food. Stays at the bank to retry instead of tripping out under-geared. - PoH ownership: new settings.useOwnHouse toggle. Own house uses "Inside" teleport + skips ENTERING_FRIEND_HOUSE. settings.poolName / settings.jewelleryBoxName let users point at fancy/teak/marble pools or other jewellery boxes. - Coordinator: new settings.waitForTeammateSpec. After using the pool, the bot holds at the pool while bot teammates with specPct < 100 are still in restoration states (90s hard cap). Lets a single-POH party share access in turn. - Death detection: now gated on observing HP=0 within the last 60s instead of "gravestone exists anywhere". Rules out false positives from stray gravestones at random banks. Gravestone confirmation moves to the LOOT_GRAVE step where it's the actual loot target.

## [1.5.1] — 2026-05-15

Phase G follow-ons + Phase H (death recovery). - Death recovery: new BotState.DEATH_RECOVERY plus a 6-step inner FSM (TO_BANK -> WITHDRAW -> TELE_TO_CORP -> LOOT_GRAVE -> REEQUIP -> DONE). detectDeath() fires the transition when we find ourselves outside Corp/Ferox with a gravestone visible. Recovery walks to Ferox, withdraws Games necklace + 10 food + 1 super restore + 1 super combat, tele's back, loots the gravestone, re-wields main weapon + defender, and resumes FIGHTING_CORP. Protect-from-magic is kept on throughout. - Vengeance: new settings.useVengeance toggle (some accounts don't have Lunars). Stop-vengeance threshold is now a configurable absolute-HP value (INTERNAL_CORP_LOW_HP_VENG_STOP_RAW_HP, default 85) instead of the hardcoded < 10% (= 200 HP). Removed unused VENGEANCE_COOLDOWN_MS and BOSS_LOW_HEALTH_THRESHOLD dead constants. - Main weapon: Combat-tab field is now a JComboBox sourced from MAIN_WEAPON_OPTIONS (editable for future weapons). New getMainWeaponVariants() expands "Osmumten's fang" to match both the regular and (or) ornament variants. The static MAIN_WEAPON_NAMES array is gone; settings.mainWeapon is the single source of truth. - Banking keep-list: dead ITEMS_TO_KEEP constant removed. shouldKeepItem() now keeps every owned spec weapon (not just the currently chosen one), the main weapon's variants, any defender, and the configured food list — so a banking run can't accidentally deposit our Fang or any spec gear.

## [1.5.0] — 2026-05-14

Phase G: modern dark-core "attack-and-step-away" meta. - Legacy on-tile sidestep dodge moved into handleAdvancedDarkCoreLegacy() and kept behind a toggle (settings.useLegacyDarkCoreLogic, default false). - handleAdvancedDarkCoreModern() now drives core handling by default: bot equips Elder maul / DWH on core spawn, the bot the core jumped to attacks it, then steps 2-3 tiles away so the core dies mid-air (no respawn). Other bots hold the kill weapon ready while watching. On core despawn, equipMainWeaponFast() re-wields Fang and state returns to FIGHTING_CORP. - GUI: Spec tab gets a "Use legacy dodge logic (fallback)" checkbox so we can A/B the two strategies.

## [1.4.0] — 2026-05-14

Phase D: dynamic spec-weapon rotation by team phase, plus real BGS damage tracking via Hitsplat.isMine(). - pickSpecWeaponForCurrentPhase() chooses the best owned weapon for whichever phase the team currently needs (1: Elder maul/DWH, 2: Emberlight/Arclight/Darklight, 3: BGS). - refreshSpecWeaponForPhase() updates chosenSpecWeapon at the top of each spec handler. equipSpecWeapon() handles the actual gear swap. Skips spec entirely if no usable weapon. - getMyLargestRecentHitOnCorp() reads corp.getHitsplats() and filters by isMine() to get accurate BGS damage. The +30 approximation is now only the fallback when no hitsplat is visible.

## [1.3.0] — 2026-05-14

Phase C: wire coordinator into spec-decision logic. - teamPhaseNeeded() returns 1/2/3/0 based on team's aggregate. - shouldSpecNowConsideringTeam() gates spec attempts: if my weapon's phase is already complete team-wide, skip and DPS. - recordSpecUsed() updates mySnapshot.specsThisKill per spec. - coordinatorOnKillEnded() called from handleLooting() to advance kill_id and reset per-kill counters.

## [1.2.0] — 2026-05-14

Phase B: team coordinator plumbing. - CorpCoordinator class: read/write/aggregate of a shared JSON file in the ScriptSettings directory. Atomic-rename writes for crash safety. Peer model — each bot publishes its own snapshot, reads team aggregate. - AccountSnapshot / TeamState / TeamAggregate data classes. - SPEC_COST and SPEC_PHASE static maps for weapon metadata. - GUI: phase target spinners on the Spec tab. - Tick integration: coordinatorPublish() at top of main loop, throttled by coordinatorWriteIntervalTicks.

## [1.1.0] — 2026-05-14

Bulk refactor (clone from F drive): * Bug fix: EAT_HEALTH_THRESHOLD was a static final evaluated at class-load time (Skill.HITPOINTS.getActualLevel() - 21), which read 1 or 0 when the player wasn't logged in. Replaced with eatHealthThreshold() method called at runtime. * Bug fix: while(true) main loop -> while(running). Adds clean shutdown path (no behavior change beyond the loop guard). * Configurability: 17 hardcoded constants moved into a CorpSettings class -- friendName, acceptableTeammates list, mainWeapon, foodNames, target inventory counts, HP/prayer thresholds, spec settings, valuable loot list. * GUI: 5-tab Swing settings dialog (Combat / Spec / POH+Team / Inventory / Loot) with profile row at top (Load/Save as/ Delete). Profiles namespaced under "corp_<name>". * Args: profile name; if non-empty and matches a saved profile, loads it and skips the dialog. See FUNDAMENTALS section 17.

## [1.0.0] — n.d.

Original script as imported from F:\Corp.java. Existing logic preserved verbatim except for the bug fixes above.

---

## Known fixes (historical)

- EAT_HEALTH_THRESHOLD class-load-time bug: see 1.1.0.
- while(true) without running flag: see 1.1.0.

---

## Open items (historical)

- CORP_SPAWN_LOCATION = WorldTile(2978, 4384, 2) carries the original author's
"TODO: Update with actual coordinates" comment. Verify in-game and bake the
correct value (or move it to CorpSettings if it should be user-configurable).
- Single FRIEND_NAME for POH entry (now settings.friendName). To support
multiple friends with fallback, change to List<String> and iterate.
- Many tuning constants (camera angles, core dodge distances, state timeouts,
vengeance cooldowns, valuableLoot, items-to-keep) NOT yet in CorpSettings.
Add to GUI on demand.
- SUPER_RESTORE_NAMES/SUPER_COMBAT_NAMES are dose-suffixed (4)(3)(2)(1) variants
hardcoded as constants. If you switch to a different prayer/combat potion in
the future, those need updating.
/
