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

## 1.9.25 — 1.9.99.240 (extracted from in-source `// X.Y.Z:` block in 1.9.99.241)

108 entries covering the modern era (1.9.90 onwards). Mechanically converted from the inline comment block that lived inside Corp.java for ~150 versions. Sub-items rendered as bullets where `(a)/(b)/(c)` markers exist; otherwise paragraphs.

## [1.9.90]

explicit version constant (was previously only in changelog comments)

## [1.9.91]

friend-house dialog-already-open recovery in enterFriendHouse().

## [1.9.92]

re-arm spec bar each loop iteration in handleSpecialAttack.

## [1.9.93]

portal-first reorder — only run dialog-open probe if portal lookup fails. 1.9.91's pre-portal probe false-positived on stale chatbox widgets and made the bot type RSN into public chat.

## [1.9.94]

tryActivateSpec 250ms settle window — fixes intermittent double-click that toggled spec OFF when pre-activate + handle fired ~50ms apart and SDK lagged the post-click state.

## [1.9.95]

removed isInKillPhase early-return from handleVengeanceLogic (veng now fires lobby + whole kill, not just HP<1700 window); + 500ms debounce on the in-fight corp.interact("Attack") re-engage so multi-tick clicks after eat/spec don't stack.

## [1.9.96]

removed isVengeanceStillActive() block in canCastVengeance. The probe used HP-bar-invisible as proxy for "veng still up", which false-positived whenever the bot ate back to full — blocking re-casts for the entire long kill. 30s cooldown alone is sufficient; OSRS refreshes the buff on re-cast.

## [1.9.97]

deterministic veng-consumed signal via HP delta. New flag tookDamageSinceLastVeng — flipped true in updateHealthTracking on currentHealth<previousHealth, flipped false on successful cast. canCastVengeance now requires it true. Replaces all HP-bar-visibility inference.

## [1.9.98]

re-introduced isInKillPhase early-return in handleVengeanceLogic. Veng now restricted to kill phase (teamPhaseNeeded==0 OR Corp HP <1700) so it doesn't fire during spec dumping or in lobby. Safe to re-enable now that 1.9.97 fixed the underlying cast- blocking issue that motivated 1.9.95's removal.

## [1.9.99]

3 fixes —

- **(a)** friend-house input probe rejects chat scrollback (timestamp prefix, "received a drop", >32 chars) so RSN drop announcements don't false-positive as the input field;
- **(b)** Games-necklace fallback now verifies tele actually landed; if not, transitions to BANKING_AND_HEALING instead of stranding in WAITING_FOR_TEAM at the wrong location;
- **(c)** spec-hit detection bumped from 2s→4s and XP baseline re- captured at energy-drop moment so Elder maul's 6-tick animation no longer false-negatives as "Spec MISS".

## [1.9.99.1]

spec-hit windows bumped further: handleSpecialAttack loop 4s→5s, processPendingSpecHit (in-line detector) 2s→5s. User: "the spec hit twice and it counted as 1" — Elder maul 6-tick animation + SDK getXp() lag was still missing the second hit.

## [1.9.99.2]

prepareSpecWeaponForCorp now sets specPreActivatedThisTrip=true after pre-activating. Without this, stage C re-runs on the first FIGHTING_CORP tick and, if a spec fired between prepare and the tick (player auto-attacked Corp with spec bar on), stage C stomps lastSeenSpecEnergy to the post-fire value — hiding the fire from the L3287 in-line detector. User observed ~5 actual specs fired but only 3 counted; this was the silent-spec leak.

## [1.9.99.3]

in handleSpecialAttack loop, VERIFY spec bar actually ON after tryActivateSpec before firing corp.interact. The 1.9.94 settle window can return true within 250ms of a previous click without verifying SDK state — if a weapon swap (e.g. rotation Elder maul → Arclight) happened in between, the bar was toggled OFF by the swap, settle window lied, and the next swing fired as auto-attack. 3 consecutive "timed out" warnings in user's Arclight rotation log.

## [1.9.99.4]

trimmed 1.9.99.3's verify waits 800→400ms each. SDK propagation of a spec-bar click usually completes within ~300ms; 400ms is enough headroom without adding a second of dead air per spec when the settle window was correct.

## [1.9.99.5]

REMOVED the 5s synchronous hit-confirmation wait in handleSpecialAttack. Bot was standing inside Corp's hitbox for up to 5s per spec waiting for XP/hitsplat while Corp freely dealt 50+ damage per swing. Now spec firing sets pendingHitWeapon and exits immediately; processPendingSpecHit confirms (or marks miss) asynchronously on subsequent FIGHTING_CORP ticks within the same 5s deadline window. Bot can eat / dodge core / continue swinging during confirmation instead of blocking. Multi-spec loop calls processPendingSpecHit between specs to preserve prior baseline before overwriting. BGS damage capture moved to processPendingSpecHit.

## [1.9.99.6]

REMOVED the force-click fallback in handleSpecialAttack's in-loop re-arm. 1.9.99.3-5 sent a second activateSpecialAttack click after 400ms if SDK still reported bar OFF — but if the first click was just in-flight, the second click toggled the bar OFF, then corp.interact swung as auto-attack with no energy drop and 5s timeout. Bot stood in Corp's hitbox for ~8s while this double-toggle played out. Now: single click + 700ms verify (one full game tick). If still OFF, break out and let the outer FIGHTING_CORP loop retry — between attempts bot can eat / dodge / swing instead of double-clicking itself into a stale-bar state.

## [1.9.99.7]

process prior pending hit BEFORE overwriting its baseline in handleSpecialAttack's loop. Spec N's XP arrives at swing END (~3600ms after energy drop), which is exactly when spec N+1's energy drop fires (next swing starts at end of prior anim). Overwriting baseline at spec N+1's fire burned spec N's XP into the new baseline → spec N never confirmed. User: "its hit 3 specs and has counted 0 of them in specs this kill being hit".

## [1.9.99.8]

keep specWeaponReadyForUse=true after handleSpecialAttack exit when energy >= min — so the L3287 in-line detector can pick up future spec fires from natural auto-attacks. Pre- 1.9.99.8 we cleared it on every exit, blocking the detector.

## [1.9.99.9]

detect silent spec fires during handleSpecialAttack's in-loop re-arm verify wait. If tryActivateSpec clicked but the 700ms verify timed out yet energy dropped during the wait, a spec fired silently (click landed late, bar toggled ON briefly, player auto-attack consumed it, bar back OFF). Without detection here the silent fire was completely untracked. User log: "Spec bar didn't toggle ON within 700ms ... Completed 1 special attack(s), final energy: 0%" when only spec 1 was logged firing.

## [1.9.99.10]

tryActivateSpec settle window 250ms→800ms. Two tryActivateSpec callers spaced 300-500ms apart (e.g. L3303 pre-activate then handleSpecialAttack's L7027 re-arm) both saw bar OFF (SDK hadn't reflected the first click) and both clicked → toggle ON then OFF → net DISABLED. User: "is it possible that when it enables spec on the way and then starts speccing the boss its disabling that? i see double clicking occasionally". 800ms = one game tick (600ms) + click-to-SDK-reflect lag (~200ms).

## [1.9.99.11]

TWO false-positive fixes for spec hit attribution. (A) Capture firedWeapon BEFORE processPendingSpecHit's rotation in handleSpecialAttack loop. Pre-1.9.99.11 a mid-bar rotation (Elder maul → Arclight when phase 1 target met) caused the second spec's pending to be stamped with the rotated weapon, attributing Elder maul's swing to Arclight. (B) processPendingSpecHit checks deadline FIRST. Pre-1.9.99.11 a pending hit that survived a state change (e.g. across a 30s restoration cycle) would later get "confirmed" via XP delta from normal attacks during that time — false positive. User: "counted 2 normal attacks with the arclight a with no spec as arclight specs". Now we mark miss when deadline expires regardless of signal state.

## [1.9.99.12]

handleSpecialAttack's 5s energy-drop wait now ticks processPendingSpecHit each ~50ms iteration. Pre-1.9.99.12 the Waiting.waitUntil block prevented processPendingSpecHit from running during the wait — prior specs' XP/hitsplat signals arrived in that window but were never observed. By the time handleSpecialAttack exited and FIGHTING_CORP resumed, hitsplats (~3.6s lifespan) had expired and 1.9.99.11 strict deadline marked the spec as miss. User: "i hit the corp with an elder maul spec 3 times and its only counted one". Now signals get processed in real-time during the wait, catching confirmations as they arrive.

## [1.9.99.13]

stepAwayFromCore SAFETY fixes after death from stomp. (A) Refuse to step if Corp area can't be read — pre-1.9.99.13 null corpArea bypassed the inside-Corp check entirely and accepted tiles literally on Corp's hitbox. User: "i just ran away from the core directly under the corp and got stomped to death with 3 stomps". (B) 2-tile buffer around Corp's 5x5 hitbox. A tile right at the hitbox edge becomes inside it the next tick when Corp roams. 9x9 exclusion gives Corp room to move without stomping the player on arrival.

## [1.9.99.14]

processPendingSpecHit now runs in EVERY state (called before the state dispatcher), not just FIGHTING_CORP. The last spec of a bar fires right before the bot transitions to PREPARING_RESTORATION_CYCLE → ... 30s of non-FIGHTING_CORP states → no spec-hit signal processing → deadline expires → 1.9.99.11 strict deadline marks miss. User: "two of our elder maul specs hit back to back and only the first one counted".

## [1.9.99.15]

handleProtectionPrayers no longer spam-clicks PROTECT_FROM_MAGIC.enable() when prayer points = 0. The click can't succeed without points; bot was logging "Activating Protect from Magic" multiple times per second mid-fight, burning ticks between eat/spec cycles and causing deaths. Now: drink a prayer pot first if doses exist; if still 0, return silently so L3315's bank-trip check fires. User: "tries to enable prayer even though we have no prayer points constantly during the fight".

## [1.9.99.16]

wait for pool-drink animation to finish before clicking the bank chest in handleBankingAndHealing. Pre-1.9.99.16 useRestorePool returned with the player still animation- locked from the drink, and the immediately-following bank chest click failed silently — Bank.isOpen() wait then hit its 10s timeout every single time. User: "we try to click the bank at ferox the same tick we use the pool so we are already locked in an animation so we hit the timeout every single time".

## [1.9.99.17]

handleStarting now routes to PREPARING_RESTORATION_CYCLE when prayer or spec is below full. Pre-1.9.99.17 the bot went straight to Corp regardless of stat state — starting after a disconnect with depleted prayer/spec meant entering combat unprepared. User: "if we start the script outside our friends poh but our prayer or spec isnt already 100 we start the trip without attempting to restore anything and just go straight to the boss".

## [1.9.99.18]

handleTeleportingToHouse short-circuits when already in a usable house. Saves a wasted house tab and ~5-10s of travel when the user starts inside their friend's house or inside their own POH. Three cases handled: in-friend- house+friend-mode → straight to pool; in-own-house+own-mode → straight to pool; in-own-house+friend-mode → skip tab, go straight to the friend-house portal step.

## [1.9.99.19]

settle wait after Prayer.enableQuickPrayer() so the immediately-following PFM check doesn't see stale "off" state and redundantly open the prayer tab to re-enable a prayer that's already on. User: "we enable our prayers with click prayers but then we go to our prayer tab to enable the prayer thats already on. this seems redundant".

## [1.9.99.20]

equipSpecWeapon resets lastSpecActivateAt=0 on success. Weapon swap toggles the spec bar OFF. Pre-1.9.99.20 the settle window (set by a prior pre-activate ~300-500ms ago) caused tryActivateSpec to return true without re-clicking after the swap — bot proceeded with bar OFF, swing fired as auto-attack, 5s energy-drop timeout. Now: invalidate the settle timestamp on weapon swap so the next tryActivateSpec actually fires a click.

## [1.9.99.21]

bumped tryActivateSpec settle window 800ms→1500ms. The L3287 in-line detector + L3303 main pre-activate cycle was ~1000ms apart (mouse + click animation), past the 800ms settle. Second call fell through, SDK reported bar OFF momentarily (lag), clicked AGAIN, net result: toggled ON then OFF → bar OFF for the actual spec fire. User: "STILL DOUBLE SPEC ACTIVATING".

## [1.9.99.22]

pre-swing XP baseline in handleSpecialAttack loop + L3287 in-line detector. Pre-1.9.99.22 baseline was captured AT energy drop — meaning the swing's XP was ALREADY in the baseline (game applies XP at swing impact = same tick as energy drop). For single-hit specs (Elder maul / Arclight- on-Corp), nowXp later = baseline, delta = 0, no XP confirmation. Now: handleSpecialAttack's loop uses the previous-poll XP (~50ms before drop); L3287 in-line detector uses xpAtSpec (set at pre-activate, pre-swing). User: "some xp drops when spec drops doesnt count the specs as successful... mostly arclight".

## [1.9.99.23]

dark core grace-period path now DPSes Corp instead of standing AFK. Pre-1.9.99.23 the 3s grace window between core render-flickers had no fallback — bot held kill weapon and did nothing. With auto-retaliate off (1.9.64), no swings at all. User: "we got stuck in a state of the core being out so we were just standing there waiting for it... kinda afk".

## [1.9.99.24]

stepAwayFromCore Corp buffer 2→3 tiles. Corp can move up to 1 tile per game tick. With a 2-tile buffer + 2-3 tick walk, Corp can roam into the destination tile. 3-tile buffer (5 tiles from Corp center) covers Corp moving up to 3-4 tiles during the walk. User: "one of the moves we made to run away from core after hitting it was 1 tile under the corp".

## [1.9.99.25]

inSpecDump (eat-skip condition) now ALSO requires the state to be USING_SPECIAL_ATTACK. Pre-1.9.99.25 the spec- weapon-equipped check caught the entire approach phase (Elder maul equipped during walk to Corp). Bot took mage + melee hits through prayer for 9 seconds without eating, died on arrival. User: "we just ran up and died instead of panic eating".

## [1.9.99.26]

getDistanceToCorpHitboxEdge now uses corp.getArea(). getCenter() instead of corp.getTile() as the hitbox center. For 5x5 NPCs, corp.getTile() returns the SW CORNER — pre- 1.9.99.26 the hitbox radius (2 tiles) was applied to the SW corner, shifting the perceived hitbox 2 tiles SW of its real position. Half of the 4 cardinal candidates got falsely flagged as "too close to hitbox edge" and dropped from the filter list, then the line-cross check often rejected the remaining 2 → "ALL N cardinals require crossing Corp" fired and the bot fell back to game-pathfinder click-attack (slow approach, took mage+melee for ~9s). User: "theres ALWAYS free spaces open in front of corp but somehow we still are trying to find other positions".

## [1.9.99.27]

status overlay moved from Swing JFrame popout to in-client Painting.addPaint callback; plus tile-debug section showing Corp center, player pos, and each cardinal candidate with its cross/clear classification. Camera angle target lowered 100→75 and the readjust threshold 80→50 — bot no longer pegs the camera at max pitch (a clear bot tell). User: "can we draw the paint in the screen/on the client instead of having the seperate popout window", "real players dont adjust their camera angle all the way to the lowest possible angle, they simply rotate the cameras view".

## [1.9.99.28]

"Spec HIT confirmed" log now includes Corp HP% so we can verify whether two same-XP-delta confirmations correspond to two real hits (corpHP drops twice) or a double-count of one hit (corpHP only drops once). User: "we counted two specs even though only one hit ... its improbable we hit a 45 twice in a row".

## [1.9.99.29]

Add Corp HP delta as a third veto signal in processPendingSpecHit. Root cause of the double-count: when spec 1's XP arrival and spec 2's energy drop land on adjacent poll iterations, lastPollXp holds the pre-spec-1 baseline so spec 2's stamped baseline is stale. The dispatcher then sees the SAME XP delta a second time and credits a phantom hit. User's log showed two "+91 XP delta" confirmations with IDENTICAL corpHP=1.0% — Corp HP didn't change between them, so only one hit truly landed. New field pendingHitCorpHpBaseline is stamped at every spec-fire site; if confirmation arrives without corpHP having dropped > 0.05%, the confirmation is vetoed and logged as a stale-XP miss.

## [1.9.99.30]

Tighten isNearFeroxBank to require physical proximity to the bank/pool tile (3135, 3630) — distanceTo <= 4 — so handleBankingAndHealing actually walks to the tile before clicking the pool. Pre-1.9.99.30 the check returned true whenever Query found a Bank chest in render distance, which happens at the Ferox teleport landing spot even though the pool is still 10+ tiles away. Result: useRestorePool tried to interact with a pool whose bounding rect wasn't on screen → repeated hover-without-click. User: "we are trying to hover over the pool or bank instead of clicking to the designated tile first in ferox". This re-enforces the 1.9.58 walk-first contract.

## [1.9.99.31]

Two fixes from the 01:38 combat log.

- **(a)** MIN_DISTANCE_FROM_CORP_EDGE: 3 → 1. The position generator places cardinals 1 tile from Corp's hitbox edge (correct for melee), but the validator rejected anything under 3 — so all four cardinals failed, the bot bailed to emergency positions (also failed), and attacked from wherever it stood. "EMERGENCY: Combo eating" fired because the bot was getting whacked while indecisively in/near the hitbox. The 3-tile threshold was a leftover from mage/range positioning, not melee.
- **(b)** CAMERA_CHECK_INTERVAL_MS: 5000 → 2000. Camera drifted from 93 to 33 over 11 seconds during combat. User confirmed: TRiBot's built-in camera-rotate-to-target, baked into the SDK's interact() pipeline — fires automatically when we click an NPC/tile and there's no public toggle to disable it (Camera class exposes setAngle/setRotation/setRotationMethod but no disable-auto-rotate flag; AntibanProperties has no camera-rotate setting either). Our only mitigation is catching the drift faster. 2s gives ~4 checks per spec cycle. User: "we also still lowered our camera angle too low ... its the camera rotate to target built into tribot".

## [1.9.99.32]

Suppress the camera-rotate-to-target at its trigger instead of just correcting after the fact. All 12 live corp.interact("Attack") sites now go through attackCorpIfVisible(corp): if Corp.isVisible() returns false, walk one tile toward Corp and skip this tick (the caller's outer loop retries next tick). The SDK only rotates the camera when the click target is off-screen; pre-gating on visibility means rotate-to-target never fires during combat. User: "as long as we are keeping a high camera angle we dont really need to adjust to interact we can just walk towards the boss or attack it IF its ons screen already". Dark-core attack at L4090 left alone (different target, different visibility).

## [1.9.99.33]

walkToFeroxBank had the SAME Query-visibility trap that 1.9.99.30 fixed in isNearFeroxBank — at the Ferox teleport landing spot the bank chest is in render distance, Query found it, walkToFeroxBank logged "Bank chest already visible — no walk needed" and returned without walking. handleBankingAndHealing's next tick re-saw isNearFeroxBank=false (distance gated), called walkToFeroxBank again — infinite loop, bank never opened, bot burned every Games necklace re-teleporting. Replaced the Query check with the same distanceTo(3135,3630) <= 4 guard isNearFeroxBank uses. User: "we ran out of game necklace ... character teleported to the enclave but isnt attempting to run to the tile ive told you to".

## [1.9.99.34]

Removed the pre-combat "Health low after spec prep" karambwan eat (was at L8832). HP 58 was triggering an eat right before combat start, which

- **(a)** wasted a karambwan and
- **(b)** put us in an eat-animation lock at the moment Corp is most likely to drift onto our tile — antiStompTick then had to bail us out of being under Corp. In-combat eat logic handles real HP danger. User: "we dont need to eat right after we drink our potion ... we also stepped under the boss and should have gotten stomped on".

## [1.9.99.35]

Root-cause fix for HP-58-at-trip-start. The startup restoration gate at L2548 was only checking prayer and spec — not HP. So if the user started the script at less than full HP (leftover from earlier play), the bot went straight to Corp at that HP and the in-combat eat fired immediately. Now: if HP < maxHP at startup we also route through the POH ornate pool. User: "why would a pre combat hp low eat be running if we only lose 10 health?" — answered: because the bot inherited the player's pre-script HP and never restored. 1.9.99.34 removed the pre-combat eat itself; 1.9.99.35 removes the reason it was triggering.

## [1.9.99.36]

Gate the lobby inventory-space eat on actually needing the slot. Pre-1.9.99.36 the lobby prep always ate a karambwan whenever Inventory.isFull, even when the spec weapon was already wielded and no equip-swap was needed. The eat animation locked the bot right as it walked into the boss room — Corp landed a free magic hit during the chew. User: "we use a karamwan to top oursleve off, meanwhile the corp notices we are in the room while we are eating and blasts us with another hit of damage so we start the fight attacking already lower hp than if we just ran straight to him and ate while we were running or panic ate later". Now: only eat-for-slot if the spec weapon needs to be equipped from inventory.

## [1.9.99.37]

Fix the Corp-HP-veto firing on real hits. The same timing problem 1.9.99.22 fixed for XP applies to Corp HP: damage and energy-drop happen on the same tick, so a readCorpHpPct() at the in-line / silent-fire / sync detectors returns POST-damage HP — baseline ≡ confirm reading, delta = 0, veto fires on real hits. New corpHpAtSpec field is stamped at every pre-activate (paired with xpAtSpec), and pendingHitCorpHpBaseline now reads from corpHpAtSpec / preReArmCorpHp / hpBeforeDrop instead of fresh reads at the detector. User log: "Spec MISSED (Corp HP didn't drop: 0.99% → 0.99%) ... XP delta +181" — +181 is a real ~45-damage Elder maul spec, but both HP samples were post-damage because the baseline was stamped too late.

## [1.9.99.38]

Draw actual tile polygons on the game world for the four cardinal Corp positions (replaces the text-only tile-debug snapshot in the corner). Uses WorldTile.getBounds() -> Optional<Polygon>. GREEN = picked, YELLOW = valid alternative, RED = rejected (line crosses Corp's hitbox), ORANGE = Corp's 5x5 hitbox outline. User: "we are drawing the tex tlcoations of the corp outter tiles but nto actualyl drawing them on the game worlds physical tiles".

## [1.9.99.39]

Replaced the single global pending-spec slot with a FIFO Deque<PendingSpecAttempt>. The old single-slot design lost spec 1's record whenever spec 2 fired before spec 1 had confirmed — back-to-back specs were coin-flips. processPendingSpecHit now iterates oldest- first; after each confirmation we advance the XP baseline on remaining attempts so the next attempt can't double-credit the same delta. Corp HP delta is now logged for visibility but is no longer a hard veto. User: "the current detector is clever, but it's still built around a single mailbox. Back-to-back specs need a little queue ... Treat Corp HP delta as a debug/tie- breaker only, not as a hard veto when XP or own hitsplat confirms."

## [1.9.99.40]

Two safety fixes.

- **(a)** moveToNearestCorpPosition's walk-arrival wait now polls isUnderCorp every ~80ms and triggers stepOffCorp on detection. Pre-1.9.99.40 the wait was a single distance check — during that wait the main-loop's antiStompTick was BLOCKED, so an SDK A* route through Corp's hitbox would silently stomp us until arrival. User: "i just walked under the boss and got hit for a 70 and died". Even at distance-1 cardinals the SDK can pathfind through the 5x5 hitbox if Corp's tiles register as walkable in the collision map (NPC blocking != wall).
- **(b)** useOrnateJewelryBox now waits for the pool-drink animation lock to clear (MyPlayer.isAnimating false) before clicking the box, and retries up to 3x on miss. Pre-1.9.99.40 the 600ms settle in useOrnatePool wasn't always enough — the box click hit a stale frame or registered on the pool below, leaving the bot frozen at the timeout. User: "i also noticed a timeout at the pool in the house wehre i got stuck watiing for a good couple seconds because i either misclicked on it or clicked on the jewlwery box too quickly after clicking the pool".

## [1.9.99.41]

Two more fixes from the dark-core death log.

- **(a)** Force combo-eat during active dark-core engagement (core seen in the last 5s). The normal-eat tier was giving a single karambwan (+18 HP) when the core is ticking 8-12 damage per game tick on top of Corp's hits — single eats can't keep up so HP bleeds out across multiple ticks. Combo eats (~38 HP) match the damage rate. User: "we are taking a garenteed 8-12 health a tick + whatever corp tiself does to us ... every eat is a combo eat during that duration if possible".
- **(b)** Refresh the live Corp center tile in overlayUpdate every tick so the in-client 5x5 hitbox outline tracks where Corp actually IS, not where the last positioning recompute snapshotted it. User screenshot: red overlay tiles (script's belief) were several tiles off from the green tiles (Corp's real position) — recompute is gated on the drift trigger, so it doesn't update visually while Corp roams.

## [1.9.99.42]

Two root-cause fixes for the under-Corp + core kill.

- **(a)** moveToNearestCorpPosition's straight-to-target step now uses corp.interact("Attack") instead of LocalWalking.walkTo(bestPosition). The OSRS attack-click pathfinder lands the player on a tile adjacent to Corp and explicitly avoids Corp's hitbox; the prior tile-walk pathfinder didn't, which is why we kept ending up under Corp despite "safe" cardinal selection. User: "one thing i dont understand is how i keep standign under it if im clicking attack on the corp. that should always palce me inf ront of it instead of running into and under it" — answered: we weren't actually clicking attack, we were tile-walking. Now we click-attack.
- **(b)** Dark-core attack now waits for the swing animation to commit (~1 game tick = 600ms) before stepping away. Pre-1.9.99.42 we waited only 350-470ms — that's BEFORE OSRS's tick boundary, so the step cancels the attack before it commits and the core takes 0 damage. With no damage the core doesn't despawn on its "jump back" and keeps draining HP. User: "how would we kill the cor ein 1 hit? thats the intended thing anyway. if we kill it in 1 hit and run away it dies while tryign to jump which permantly despawns it".

## [1.9.99.43]

Codex audit pass — five fixes.

- **(a)** handleUsingInitialSpecs now enqueues a PendingSpecAttempt instead of calling recordSpecUsed on energy-drain. The initial-specs path was the last spec-fire path bypassing the queue, counting MISSES as successes.
- **(b)** HIT_CONFIRM_TIMEOUT_MS: 5000 → 4200ms (7 game ticks). The 5s window extended past the spec swing's expected hit tick into the NEXT auto-attack — a missed spec followed by a normal hit could falsely confirm the missed spec via XP/hitsplat from the next swing.
- **(c)** tryActivateSpec debounce: the 1500ms unconditional trust block made the verification block (gated at SPEC_ACTIVATE_DEBOUNCE_MS=1200) unreachable. Now split into <600ms trust / 600-1200ms verify-only-no-reclick / >1200ms verify-and-reclick. Silent click failures surface within ~600ms instead of being masked for 1.5s.
- **(d)** processPendingSpecHit now runs BEFORE antiStompTick's continue, so a chain of stomp-step iterations can't expire a pending attempt's deadline without confirming.
- **(e)** Two-stage tile-walk + click-Attack approach (replaces 1.9.99.42's straight-to-click-attack). Tile-walk to the picked cardinal with the 1.9.99.40 under-Corp watchdog, then click-Attack to finish on a melee-adjacent tile. Preserves the coordinator's claimed-offset spread for team play while still avoiding the under-Corp routing. User: "tile walk and attack click works if we have multiple bots".

## [1.9.99.44]

Codex second audit pass — two fixes.

- **(a)** FEROX_ONLY startup now routes to BANKING_AND_HEALING instead of PREPARING_RESTORATION_CYCLE. The 1.9.99.35 startup HP/prayer/spec gate assumed POH access; for FEROX_ONLY users that meant looping in a POH state that couldn't find a friend's house. Codex audit.
- **(b)** Bank-withdraw failures now track consecutive strikes (INTERNAL_BANK_FAILURE_STRIKES = 4). After 4 consecutive failures with no minimum-supply recovery we signalSessionEnd and stop — replacing the 1.9.88 "TODO future fix" of looping indefinitely on a broken bank state. Codex audit. Deferred: friend/own house disambiguation (needs an entry-tracking flag — edge case), port-coordinator helper for teammatesNeedPoolRestoration / etc. (solo play doesn't hit it).

## [1.9.99.45]

Hitsplat one-shot consumption + start-in-house shortcut.

- **(a)** Codex audit: pre-1.9.99.45 the queue counted corp.getHitsplats().filter(isMine).count() fresh on every processPendingSpecHit call. Hitsplats live ~6 game ticks, so the same hit could confirm multiple attempts across calls. User log: two Elder maul specs both confirmed via hitsplat (own=1) with corpHP Δ=0.00% on both — only one hit actually landed, but both got credited. Fix: monotonicHitsplatCounter that only ever moves up (each new hitsplat adds 1, expirations don't decrement). Each PendingSpecAttempt snapshots the counter at enqueue; confirmation requires the counter to have advanced since. After a hit-confirmation we BUMP remaining attempts' baselines by 1 so the consumed hit can't re-confirm. Also added a 1200ms XP-suppression window after a hit-confirmation — the hit's delayed XP would otherwise confirm a younger pending attempt via the XP path. User (from Codex's draft): "handle delayed XP after a hitsplat confirmation: suppress/consume the next melee XP delta shortly after a hitsplat-confirmed spec so the same hit cannot confirm a younger pending attempt via XP".
- **(b)** handleStarting now routes through USING_ORNATE_POOL when the script starts INSIDE a POH with low resources. Skip the Games-necklace teleport entirely — drink pool + jewellery-box tele to Corp. User: "Also if we star tina house we can use the ornate pool -> jewelery box isntead of using an amulet. This insures we are topped off first".

## [1.9.99.46]

Start-in-house path now fires REGARDLESS of resources. At full HP/spec we route directly to TELEPORTING_BACK_TO_CORP (skip the no-op pool drink, just use the jewellery box). At low resources we still drink the pool first via USING_ORNATE_POOL. Either way the Games necklace stays in the pouch. User: "i started in house with full hp and full spec but it didnt use the ornate jelwlery box and used our jelwery instead".

## [1.9.99.47]

Mid-combat re-pot. Pre-1.9.99.47 the bot only drank a super combat potion when Inventory.isFull() — fine for lobby prep where banking just filled the inventory, but the boost wears off mid-kill and there was no re-trigger. New maybeReDrinkCombatPotion called from handleFightingCorp: if stats aren't boosted and we have ANY dose of the configured combat potion in inventory, drink one. 8s throttle. User: "we didnt repot when our boosted hp ran out". First successful Corp kill — major milestone.

## [1.9.99.48]

Vengeance trap from Codex audit + magic-drain fix.

- **(a)** updateHealthTracking now runs in the main loop before state dispatch — HP drops are detected in EVERY state, not only inside handleFightingCorp. Pre-1.9.99.48 the READY_FOR_FIRST_CAST -> ACTIVE_CASTING transition only fired when HP dropped inside handleVengeanceLogic, which itself only ran from handleFightingCorp — spec-dump and POH-cycle HP drops were invisible to the state machine.
- **(b)** handleVengeanceLogic now FORCE-promotes READY_FOR_FIRST_CAST -> ACTIVE_CASTING when the kill phase is reached AND Corp is alive. READY's own handler only casts when (!bossAlive || isInCorpLobby()) — neither true during a kill-phase fight — so the bot was stuck in READY forever waiting for a transition that couldn't happen.
- **(c)** canCastVengeance now checks Skill.MAGIC.getActualLevel instead of getCurrentLevel — Corp's magic attack can drain stats by 1, dropping current below 94 even on a 99 magic account. ActualLevel is the XP-determined base. User: "we should also check if we have the correct magic level before casting 94 because corp magic attack can occasionalyl drain ur magic level by 1".
- **(d)** handleActiveCasting now logs WHY canCastVengeance blocked when the cooldown is ready (magic level, taken- damage flag, cooldown remaining). Replaces the silent Log.debug.

## [1.9.99.49]

handleTeleportingBackToCorp falls back to Games necklace if useOrnateJewelryBox fails. The 1.9.99.40 box-retry already handles transient misses, but if all 3 tries fail (e.g. starting inside a friend's POH where the box isn't on screen at land time) we previously bailed straight to EMERGENCY_ESCAPE → Ferox tele, even though a Games necklace was in inventory and would have worked. User log: "Failed to interact with jewellery box after 3 attempts ... even though it has 1 games necklace".

## [1.9.99.50]

two spec-counting bugfixes.

- **(a)** advanceHitsplatCounter now filters by getValue() > 0 so 0-damage MISS splats don't increment the monotonic counter. Pre-1.9.99.50 a missed Elder maul still showed up as a hitsplat (OSRS renders misses as the "0" splat) and falsely confirmed the pending attempt.
- **(b)** HIT_CONFIRM_TIMEOUT_MS: 4200ms → 3500ms. The 4200ms window still extended past the 3600ms next-swing tick for 6-tick weapons (Elder maul / DWH / Arclight), allowing the next swing's hitsplat to confirm a missed spec. 3500ms is just under the next-swing window — catches the actual spec swing but excludes the follow-up. User: "i think it counted an elder maul spec that missed as being a succesful spec ... our spec counts are getting all twisted".

## [1.9.99.51]

post-dark-core weapon restore. After killing/avoiding a dark core the bot used to call equipMainWeaponFast() which always swaps to Fang. Fine for the kill phase, but during an active spec-dump phase (e.g. Phase 3 BGS) the bot would auto-attack with Fang for 1-2 ticks before the main loop's shouldUseSpecialAttack noticed and re-equipped the phase spec weapon. Now: if pickSpecWeaponForCurrentPhase returns a weapon AND we have spec energy >= the minimum, equip THAT weapon (BGS / Arclight / whatever phase needs) instead of Fang. Only fall back to Fang if no usable spec weapon exists for the current phase. User: "at one point in the bgs phase it switched to another weapon, and started poking with eitehr the arclight or fang and eventually went back to the bgs ... maybe switchign to the elder maul when core spawne dbroke something?".

## [1.9.99.52]

harden BGS -> Fang transition at kill phase (Codex audit follow-up). Three additions inside handleFightingCorp:

- **(a)** KILL-PHASE-DIAG log fires once per second when isInKillPhase() AND a spec weapon is still equipped — dumps state, chosenSpec, bgsEquipped, specSwitchQueued, needsSwitchBack, Fang inv/equip status, availableMain, and how long the queue has been pending. Surfaces the exact reason the swap isn't completing.
- **(b)** Watchdog: if specWeaponSwitchQueued has been true for > 5s and isMainWeaponEquipped() is still false, force- call equipMainWeaponFast() and clear the queue. Replaces the implicit "wait forever for handleSpecWeaponSwitchTiming to retry" loop.
- **(c)** Fang-spec block now pre-equips Fang if the kill phase is reached and Fang is in inventory but not on. The old block required Equipment.contains("Osmumten's fang") so it silently no-op'd while BGS was still equipped. Bookkeeping: queueSpecWeaponSwitchBack stamps specWeaponSwitchQueuedAt for the watchdog.

## [1.9.99.53]

drift-recheck interval 3000ms → 500ms. Corp roams ~1 tile per 600ms game tick; the old 3s interval let Corp drift up to 5 tiles before the bot noticed, which is how we kept walking under Corp despite "safe" cardinal pick. antiStompTick at the top of the main loop is the last- resort safety, but it only fires AFTER we're already under Corp. Sub-tick drift checks let us reposition BEFORE Corp closes the gap. Cheap when Corp hasn't moved (no click); only clicks when we're actually out of the cardinal tolerance. User: "if we catch him moving before he does and update that in real time wouldnt that mean we would almost never walk under him?".

## [1.9.99.54]

dropped the LocalWalking.walkTo(bestPosition) step in moveToNearestCorpPosition. It was the only remaining path that could route through Corp's hitbox (the tile pathfinder routes through NPC tiles to reach a destination on the other side). The 1.9.99.43 watchdog caught it after the fact but the user wanted to PREVENT it entirely. Now we delegate the entire approach to attackCorpIfVisible: click-Attack on Corp when visible (game's attack pathfinder routes safely to an adjacent melee tile WITHOUT crossing), 1-tile step toward Corp's tile when not visible (small enough that it can never cross the 5x5 hitbox). bestPosition is retained for the paint overlay only. For team coord, the game's attack pathfinder picks the closest unoccupied melee tile so bots naturally distribute. User: "if hes ons creen we click attack on him. if we dont see him we use the minimap to click in his direction".

## [1.9.99.55]

revert/refine. User: "did dyou just delete my L walking? How will bots get to the correct location thats on the far side of him?". Two corrections:

- **(a)** Restored LocalWalking.walkTo(bestPosition) for the team- coord far-side approach. The 1.9.99.40 walk-watchdog (polls isUnderCorp every ~80ms during the walk) is the right safety net for the occasional A* mistake — preventing every tile-walk was too aggressive. The 1.9.54 L-shape walk to corner waypoints already handles "line crosses Corp".
- **(b)** Bumped attackCorpIfVisible's off-screen step from 1 tile to a CHUNK (up to 5 tiles toward Corp, capped so the destination stays at least 3 tiles from Corp.getTile() on the dominant axis — i.e. on the rim of the 5x5 hitbox, never inside). User: "our minimap shouldnt onyl walk 1 tile towards it. What is our flow if corp isnt on screen? We wan tto change the distance we are walking towards corp so that we dont minimap click to walk and end upw alking into a tile where he is."

## [1.9.99.56]

chunked walk for the final approach in moveToNearestCorpPosition. Pre-1.9.99.56 a single big LocalWalking.walkTo(bestPosition) handed the SDK pathfinder freedom to route through Corp's hitbox if it saw a shortcut. New walkInChunksTo() takes 5-tile chunks toward the target; each chunk's destination is clamped to be OUTSIDE Corp's hitbox (it pulls back tile-by-tile if the 5-tile point lands inside). Short clicks = pathfinder has no creative room to route through. Between chunks we re-check Corp's position and bail to stepOffCorp if we somehow ended up under. User: "use minimap walking to move towards the corp but make the location we walk there CLOSER To use ... making a simpleline path to get where we need to be like run straight 10 tiles, turn left 10 tiles after that".

## [1.9.99.57]

walkInChunksTo now uses an ADAPTIVE chunk size — up to 12 tiles per click when far away, scaling down to the remaining distance as we approach. 5-tile fixed chunks were too small for long walks. The per-axis pull-back when a chunk lands inside Corp's hitbox now prefers shrinking the larger-magnitude axis first so we route AROUND the hitbox rather than backing straight away. Also clarified to user: the minimap dots they see (Corp yellow, player white) are just a visual of the same game state we already query via Query.npcs().getTile() — no SDK pixel-reading needed to know where Corp is. User: "we dont only want to walk 5 tiles forward at a time. is there not a way to read whats on the minimap ... estimate the direction corp is in but not walk completelyt over it".

## [1.9.99.58]

walkInChunksTo respects a 4-tile BUFFER outside Corp's hitbox. Chunk destinations must be > 4 tiles from the hitbox edge — anywhere closer is the "danger zone" where Corp roaming 1-2 tiles per tick could land on us before we react. Once the player IS within the buffer, the function returns true and the caller (moveToNearestCorpPosition) takes over with attackCorpIfVisible — the game's attack pathfinder routes safely from short range. User: "we wouldnt ever want to click to close to the corp ... we would want to walk idk maybe 3-7 tiles outside of its hitbox. any closer and thats setting us up into a zone we could get walked on".

## [1.9.99.59]

two fixes from the BGS-phase test run.

- **(a)** Bot stood AFK when dark core was visible but distant (focusing teammate). The non-approaching-core branch in handleAdvancedDarkCoreModern returned without attacking anything, and auto-retaliate is OFF (1.9.64) during core handling, so the bot froze with whatever was equipped (BGS, in user's case). Now we keep clicking Corp via attackCorpIfVisible when the core is distant — DPS continues while the partner tanks the core. User: "we were getting stuck just afking with the bgs out ... while the core was out and focusing the real player while we sat tehre and did nothing".
- **(b)** Paint overlay now appends BGS damage progress for the BGS entry: "Bandos godsword=3 (~143/200 dmg)". BGS phase is gated on damage drained (INTERNAL_PHASE3_BGS_DAMAGE = 200), not spec count — the logic was already correct (bgsDamageDealt sums hitsplat values), but the overlay only showed the count which was misleading. User: "for the bandos godsword phase we are tracking damage dealt vs specs hit ... is that properly set up right?".

## [1.9.99.60]

BGS damage credit defaults to +30 when hitsplat value is 0, not when it's negative. Pre-1.9.99.60 the check `>= 0` accepted 0 as a literal "zero damage spec" — but 0 came from getMyLargestRecentHitOnCorp returning 0 (hitsplat aged out, or queue confirmed via XP-only with no hitsplat present). Phase 3 target is 200 (100 effective in duo); at 0 per spec, Phase 3 was never reachable. Now the check is `> 0`, so 0 falls to the +30 default. Also added a per-spec log: "BGS damage credited: +N (actual hitsplat=X, total=Y/200)" so you can see progress in the log directly. User: "stuck on the bgs forever ... i think that mostly stimmed around the core issues because on a previous kill it didnt have that issue".

## [1.9.99.63]

two timing fixes from user log.

- **(a)** walkInChunksTo's inner-loop wait was satisfied immediately at t=0 when chunk was only 1 tile (the wait condition `distance <= 1` is true at the start because we start 1 tile from the destination). Outer loop then spun without ever actually waiting for the walk to complete. User log showed "chunk 1 tiles to (2989, 4385)" repeated 5 times in 1 second from inside a single walkInChunksTo call. Now: wait for player to either REACH chunkDest exactly OR move AT ALL from the start tile (covers off-by-1 pathfinder landings). Pure distance-based check replaced with movement detection.
- **(b)** Dark-core attack-then-step now waits for the swing to LAND (XP delta > 0 OR animation end), not just start. Pre-1.9.99.63 we stepped at animation-start + 150ms, which is ~3 seconds BEFORE an Elder maul swing actually hits (6-tick animation). Stepping cancels the swing, core takes 0 damage. User: "sometimes it would run away from the core before the animation went off. Maybe we send the running input after we get an xp drop?". Yes — XP drop is now the gate.

## [1.9.99.62]

throttled the per-tick "Dark core not visible / close / distant" debug logs to one line per second per call site. Pre-1.9.99.62 these fired every main-loop iteration (~10+ per second) and buried useful log lines around core events. New field lastDarkCoreLogAt tracks the last debug emit; if within 1000ms we skip the log but still take the action. User: "its hard to grab logs revolvign the dark core because it spams so many per second it nukes the majority of our log".

## [1.9.99.61]

three fixes from the spinning-with-BGS log report.

- **(a)** Drift recheck skip: if isPlayerAttackingCorp OR we're within the buffer zone (edgeDist <= 5), don't re-fire moveToNearestCorpPosition. Pre-1.9.99.61 the drift recheck at 500ms intervals kept calling moveToNearestCorpPosition while click-attack's auto-walk was in flight, spamming walkInChunksTo with no-op chunks. User: "we are getting hit with this ... walkInChunksTo: chunk 1 tiles to (2976, 4379) [repeated 4x] ... just kind if idle around with our spec weapon out".
- **(b)** Paint shows the EFFECTIVE BGS damage target. In duo with the 2x multiplier, bot's effective contribution target is 100, not the raw INTERNAL_PHASE3_BGS_DAMAGE=200. Now: "Bandos godsword=3 (~120/100 dmg, duo 1p multiplier)". User: "my bgs damage even though i hit multiple times kept showing 120/200. but... dont we not need 200 since we are solo duoing?".
- **(c)** Spec credit weapon mismatch warning. processPendingSpecHit now cross-checks Equipment.contains against attempt.weapon at confirm time. If a swap happened mid-flight (e.g. phase rotation between fire and confirm), log a warning so the user can see WHICH weapon actually fired. User: "is tehre a way to check if we actually have the correct spec weapon equiped when we count a spec progression?". Deferred to a later iteration: face-tank-after-attack on dark core (need a log capture to see whether stepAwayFromCore is failing or being re-triggered), one-shot detection using core.getHealthBarPercent.

## [1.9.99.72]

death-spiral fixes from the 19:39 log.

- **(a)** Panic retreat on consecutive emergency eats. handleHealthAndPrayer now records lastEmergencyEatAt; if a second emergency eat fires within 2s, the bot eats THEN steps 5+ tiles off Corp's hitbox center via the new panicRetreatFromCorp(). Sets panicRetreatActiveUntil so handleFightingCorp won't re-engage for ~2.5s (HP regen / veng catches up). Pre-1.9.99.72 the bot stood-and-ate through Corp's burst damage, ran the karambwans dry, then died.
- **(b)** Vengeance gates on CURRENT (live, drained) magic level. canCastVengeance was using Skill.MAGIC.getActualLevel() — the BASE level from XP, which never drops. Corp's magic attack drains Magic by 1; on a 94-mage account that drops live level to 93 and the cast silently fails. User: "It did try to veng but its stats were lowered temporarily. We need to check if we have 94 magic before we try."
- **(c)** VENG-GATE log shows both magicCurrent + magicBase, and lastCastAgoMs prints "never" instead of the giant Unix timestamp when no successful cast has happened. Pre-1.9.99.72 the diag printed nowMs - 0 = 1.78e12 ms.
- **(d)** Karambwan-low pre-emptive bank trip. If karambwan count drops to 2 and we're NOT in kill phase, the fight handler routes to BANKING_AND_HEALING. Pre- 1.9.99.72 the bot burned the last karams in combat, then fell back to Shark-only (half heal rate) and died. User: "during the last phase we probably dont need to panic eat." — so the gate skips when isInKillPhase() is true.
- **(e)** Demoted isStatsBoosted's per-tick "Stat check" INFO log to DEBUG. Pre-1.9.99.72 every loop iteration logged the same boost values, drowning out actual diagnostics in the 19:39 log.
- **(f)** panicRetreatFromCorp hardened: removed backward retreat offsets (they reduced distance from Corp on one axis), bumped buffer to match stepAwayFromCore's CORP_BUFFER=3 (so destinations are 4+ tiles past hitbox edge — Corp can't pace-move into them), and added a defense-in-depth check that the destination is never closer to Corp on either axis than where we already stand. User: "we need to make sure the panic retreat doesnt actually retreat into his hitbox."

## [1.9.99.73]

two fixes from the 20:32 log where the bot finished a POH restoration, walked back to Corp with spec OFF, hit with a normal Elder maul swing, then bailed to bank.

- **(a)** Karam-low check moved from handleFightingCorp to handleEnteringCombat. The 1.9.99.72-d gate fired mid-kill — after the walk-in, after the first eat, after the first swing — wasting an entire trip from POH. Now the check runs BEFORE the walk to Corp kicks off; if karams are low and we're not in kill phase, divert to BANKING_AND_HEALING straight from the lobby. Once in FIGHTING_CORP, supply gates no longer abort a kill mid-flight — emergency-eat / panic-tele handle the survival side.
- **(b)** Stage B (lobby) spec pre-activation is now deterministic. The 50/50 roll in maybePreActivateSpecStageB meant ~half of all trips walked to Corp with spec OFF; if HP also happened to be below the spec-prep gate at arrival (50), prepareSpecWeaponForCorp skipped activation AGAIN and the first swing landed with no spec. Stage A (pool) keeps its 50/50 — Stage B now ALWAYS activates when stage A didn't and spec energy is at/above the floor. User: "we ran all the way to the corp without having our spec on and smacked him with a normal elder maul hit; i feel like this should never happen."

## [1.9.99.74]

vengeance reliability + overlay-live + panic tuning.

- **(a)** handleVengeanceLogic() lifted out of handleFightingCorp and into the main loop after handleHealthAndPrayer. Previously veng only ticked during FIGHTING_CORP state — during spec-dump phases (USING_SPECIAL_ATTACK), weapon swaps, and any early return from handleFightingCorp the veng tick was dropped. Net effect from the 20:53 log: zero mid-fight casts; the only cast fired from the boss-death branch. Now it ticks every loop iteration; the post-eat ordering ensures emergency HP wins the tick if both handlers want to fire.
- **(b)** HP gate inside handleVengeanceLogic. If HP is at or below INTERNAL_COMBO_EAT_HP (50), defer the cast — the eat handler is about to fire a combo eat and we don't want the veng widget click stealing the same tick. User: "this may have us try to vengenance when we are low hp and need to eat."
- **(c)** Explicit state gate inside handleVengeanceLogic to block POH/lobby/banking/teleport/death-recovery/ emergency-escape/starting states. Redundant with isInKillPhase() in many cases but covers the gap where we're in a transition state but isInKillPhase happens to return true (e.g. Corp HP visible but we just teleported out).
- **(d)** Cardinal-tile overlay recomputes LIVE every paint tick from Corp's current position. Pre-1.9.99.74 the 4 cardinals + cross flags were only refreshed when the positioning code recomputed (gated on drift, ~500ms minimum) — Corp moved but the cross overlay lagged. User: "our cross detections still don't update in real time."
- **(e)** Panic retreat now fires on the FIRST emergency eat instead of requiring two within 2s. Spec dumps are short; one panic eat = something already failed, so retreat instead of waiting for a second one. User: "we could probably change the panic eat requirement down to just 1 panic eat."
- **(f)** Emergency combo eat with karams=0 → insta-tele to EMERGENCY_ESCAPE instead of eating sharks alone. Shark-only (~20 HP) can't keep up with Corp's burst; better to bail with Ring of Dueling than chew through sharks while dying. User: "if we need to combo eat and we're out of kawambwans we can just insta tele."
- **(g)** needsPoolRestoration threshold: HP < 90% OR prayer < 70% (was: any drop below max). Pre- 1.9.99.74 the bot drank the Ferox pool right after a POH restore because prayer drained 5-10 points during the walk to bank — wasted animation lock. User: "since the prayer stayed on we also restored it again when we banked."

## [1.9.99.75]

vengeance diagnostics added to the in-client paint overlay. New block shows: state, casts this kill, casts this session, time since last cast, cooldown remaining, magic level (current/base + DRAINED flag), rune-pouch detection, tookDamageSinceLastVeng, isInKillPhase, and the last gate-block reason (HP<50, state=POH, magic drained, no runes, etc.). vengCastsThisKill resets in coordinatorOnKillEnded and resetPerKillStateAfterAbort. User: "can we add all of our vengeance info to the paint? maybe that will help us debug better."

## [1.9.99.76]

short-range click-attack early-out in moveToNearestCorpPosition. When Corp is visible AND we are within 12 tiles of the picked safe position, skip the chunked walk entirely and fire corp.interact("Attack") — the game's NPC attack pathfinder handles the last few tiles via screen-tile walks (not minimap clicks). Pre- 1.9.99.76 every walk-in went through walkInChunksTo → LocalWalking.walkTo, which sometimes routed the click via the minimap even for short hops. User: "even if corp is on screen we will still sometimes attempt to minimap walk even if its just a short distance but if we can already see it that should never happen."

## [1.9.99.77]

vengeance — drop the strict magic-level gate, add failed-attempt throttle + attempt counter on the overlay.

- **(a)** canCastVengeance no longer returns false when magic is drained. The 1.9.99.72 gate blocked all casts for the ~60s drain recovery window — measured in the 20:53 screenshot as the SINGLE biggest reason veng cast count stayed at 0 per kill. Now castVengeance tries anyway; if magic is drained the game refuses (no rune consumption), XP-delta check fails, castVengeance returns false.
- **(b)** After a failed cast (no XP), block retries for 5s via VENG_FAILED_RETRY_THROTTLE_MS. Prevents widget- click spam while waiting for magic drain to recover.
- **(c)** New paint fields: vengAttempts (every castVengeance call, succ or fail) and lastAttempt (time since most recent attempt). "attempts climbing, casts stuck at 0" tells you the click is firing but the spell is being refused. The vengLastGateReason now also captures "click fired but magic X/94 — spell refused" and "click fired but no XP — widget miss?" so the overlay shows post-click failure modes too. User: "B" + screenshot showing magic 94/94, runes ok, killPhase yes, but lastCast: never.

## [1.9.99.78]

revert 1.9.99.77 option B + walk visibility poll.

- **(a)** Restored the strict magic-level gate in canCastVengeance. User: "keep the magic level try. trying to click a spell that we clearly cant cast is obvious of a bot." When drained, hold off rather than click-and-fail. Drain recovers ~1 level/minute.
- **(b)** walkInChunksTo's inner wait loop now polls for Corp visibility every iteration. If Corp comes into render mid-walk, return immediately so the caller's attackCorpIfVisible can engage via game pathfinder. Pre-1.9.99.78 walkInChunksTo only broke on arrival or 1200ms no-movement — covered the "Corp visible at start" case (via 1.9.99.76 short-range early-out) and the "Corp invisible whole walk" case (via walkToPositionWithCorpCheck) but not the "becomes visible mid-walk" case. User: "do we have the walking thing set to exit out of it and attack early if corp appears on screen?"

## [1.9.99.79]

spec bar toggle-off race fix + house portal type-delay.

- **(a)** handleFightingCorp's PRE-ACTIVATING block now skips if we clicked the spec bar in the last 1500ms AND spec energy hasn't dropped. Pre-1.9.99.79 the gate fired solely on Combat.isSpecialAttackEnabled() — but that probe lags the actual game state by ~1 tick after a click, so the gate read FALSE on the lag tick and re-clicked the bar, toggling it OFF. The 00:40:56 log: lobby pre-activate at :55, then 5 consecutive "PRE-ACTIVATING / Failed to activate as backup" chains over 2 seconds before the bar finally settled. Energy-drop check distinguishes "spec swung legitimately" (energy dropped, re-activate is correct) from "SDK lag race" (energy unchanged, skip). User: "we enabled spec and then disabled and enabled and disabled when we could have just left it enabled."
- **(b)** Friend's-house dialog shortcut wait dropped 8s → 1.2s. The wait gated on the "Last name: <host>" shortcut widget rendering, which is usually <1 tick but occasionally took 5s+. Now if it doesn't render in ~2 ticks, fall straight to typing. Post-settle wait also tightened (~120ms → ~60ms). Net effect: house entry is 4-5s faster on slow shortcut renders. User: "we do correctly enter players names on the house portal; but it seems like it has a long timeout of maybe 5 or so seconds before it types even though the correct interface is open."

## [1.9.99.80]

walkInChunksTo visibility-bail progress guard. The 1.9.99.78 visibility poll bailed instantly when Corp was already in Query results at walk start — even from 14-17 tiles away. The 00:53:57 log showed the bot stuck at (2970, 4382) for several seconds, drift-recheck firing every 500ms, the chunked walk command issued but the poll returning true before LocalWalking could actually move the player. Caller's attackCorpIfVisible then fired a second click that didn't route, and the cycle repeated. Fix: require 3+ tiles of player movement since walk-start before the visibility poll is allowed to short-circuit. By the time we've moved 3 tiles, the game pathfinder has committed to the walk and a mid-walk "Corp now visible" is genuine — bail to click-attack is safe. User: "we got stuck in a phase where the script thinks we are fighting corp but we havnt actually entered the cave; mid kill after restoring spec."

## [1.9.99.81]

three fixes.

- **(a)** Reverted 1.9.99.79's house-portal shortcut wait timeout reduction (1.2s → back to 8s). The shorter timeout was firing typing fallback before the dialog input field was keyboard-focused; keystrokes landed in public chat or stale buffers, producing wrong names. Original 8s usually returns in ~1 tick on fast renders; the 5s worst-case delay is preferable to mistyping. User: "the previous version was better."
- **(b)** Karam-low check in handleEnteringCombat now ALSO gates on currentRestorationCycle == 0. Pre-1.9.99.81 every POH-then-return-to-Corp re-entered ENTERING_COMBAT and re-checked karam count — if we'd eaten karams during the previous fight portion, the next re-entry saw karams <= 2 and bailed to bank, wasting the specs already invested in this kill. Now: only bail on first engagement of a trip-from-bank. Once we've done any POH cycle, we're committed to finishing. User: "i am also noticing during the spec phase we are still banking if we have 2 karmbwans which i thought we changed."
- **(c)** handleActiveCasting: dropped the bossAlive + bossLowHealth gates and the boss-death-branch. Pre-1.9.99.81 the alive-branch required Corp to be in render AND Corp HP > 85; both silently no-op'd whenever the conditions weren't met. The java_Pm7TDAwTST.png screenshot showed state= ACTIVE_CASTING, killPhase=yes, magic=94/94, runes=ok, tookDmg=yes, cd=ready — every visible gate green — but attempts=0 because the bossAlive/bossLowHealth gate was failing silently. Veng is a self-buff that reflects the NEXT damage; it doesn't require Corp to be visible right this tick. Now: kill-phase + active-combat state + canCastVengeance is enough. User: "we just straight up dont veng and i cant figure out why."

## [1.9.99.82]

dropped the proactive karam-low pre-engagement check entirely. The reactive insta-tele in handleHealthAndPrayer (1.9.99.74-f) already handles the only case that matters: if we hit emergency-eat threshold AND karams == 0, tele straight to Ferox via Ring of Dueling. Proactive banking at 2 karams was wasting trips for situations that might never have actually required a combo eat. User: "If we need to get food its fine. We can just make it so if we run OUT of emergency eat combo foods and require it we force insta teleport to ferox to rebank. currently we are rebanking if we get to 2 karmawans left."

## [1.9.99.106]

trip-plan randomization for spec / pot / weapon swap.

- **(a)** Spec pre-activation: Stage A roll dropped from 1/2 to 1/3. New Stage A.5 added — fires after the ornate jewellery box click (overlaps the tele animation, looks like natural multi-tasking instead of a fixed pool-side ritual). Stage A.5 takes 1/2 of remaining (= 1/3 overall). Stage B (lobby) still forced when A and A.5 didn't activate. Stage C unchanged. Distribution per trip: ~1/3 pool, ~1/3 post-jewellery box, ~1/3 lobby. User: "currently after hitting the spec pool; if we decide to enable spec while still in the house, every single time 100% of the time we enable it while still at the pool and then click on the jewellery box."
- **(b)** Combat potion drink: new per-trip plan with even 3-way split: HOUSE_POST_POOL / LOBBY / BOSS_ROOM_WALK. Roll fired at trip start (alongside spec rolls). Each stage helper (maybeDrinkCombatPotAtHouse/InLobby) only fires if
- **(a)** plan matches AND
- **(b)** stats not already boosted. Boss-room fallback unchanged. User: "we can drink it in the house, in the lobby, or as we walk into the boss room."
- **(c)** Spec weapon swap: new per-trip plan with weighted choice — 70% LOBBY, 30% BOSS_ROOM. Lobby swap saves the 0.6s boss-room delay before first swing. Stage B skipped when swap deferred to boss room (avoids the swap-toggles-bar-OFF waste). User: "should add that we can also switch and have a higher randomized likelihood to do so in the lobby."

## [1.9.99.105]

sustained 15s timeout drops the engagement gate. User debug log: "peakHP=0.0% lastHP=100.0% missing=19979ms" — Corp NPC missing 20s in the boss room, but neither engagement signal triggered (peak=0 because isHealthBarVisible() returned false the entire fight; lastHP=100 is the default initial value, never updated). LOOTING was permanently blocked despite Corp clearly dead. 15s of NPC absence + in boss room IS the death signal — engagement details are downstream of detection and shouldn't gate it. Fast (3s) path keeps the engagement gate for false-positive safety. User: "i caint even read the dbeugs for corp deaths cuz the entire chat spams this 100x times."

## [1.9.99.104]

kill-phase shark-only allowed. The 1.9.99.74-f karam=0 insta-tele fired unconditionally, which is right during spec-dump phase (Corp hits hard, shark alone can't keep up) but wrong in kill phase (Corp debuffed, ~20 HP shark heal is plenty per attack). Now: only tele on karam=0 if NOT in kill phase OR if sharks also < 3. In kill phase with sharks >= 3, eat shark and continue fighting. User: "after weve finished dumping specs corp is so weak we wont ever need to combo eat again. so just having sharks is good enough and we keep banking in the last phase. because we are out of combo eats."

## [1.9.99.103]

Added PREPARING_RESTORATION_CYCLE to the handleHealthAndPrayer skip list. The bot routinely ate a karambwan right before teleing to POH because eatHealthThreshold (HP < ~78) fires during this brief prep window; the eat is wasted since the ornate pool restores HP to full a few seconds later. Same idea as the 1.9.99.86 LOOTING skip. User log: "Ate Karambwan (normal)" at 09:56:07 followed immediately by "Teleporting to house" at 09:56:08.

## [1.9.99.102]

POH-first emergency escape when supplies aren't critical. handleEmergencyEscape now checks if food (sharks >= 5, karams >= 5), pots (combat + super restore each >= 1 dose), and a house tab are all available before defaulting to Ferox + bank. If so, we skip the bank trip and route directly to TELEPORTING_TO_HOUSE — the existing POH restoration chain (house tab → ornate pool → ornate jewellery box back to Corp) handles the rest. Saves the 3-5 minute bank trip when supplies didn't need restocking. Falls through to Ferox+bank when supplies are below threshold OR pohSource is FEROX_ONLY. User: "it doesnt hurt to check if supplies arnt critical and then doing poh. theres rare occasions where we get combod from 50+30 at the start and panic tele out when we could just poh ornate pool."

## [1.9.99.101]

two fixes for "we NEVER transition to LOOTING". User screenshot showed peak=1%, lastHP=1%, missing=21754ms (Corp dead 21+ seconds), inBossRoom=yes — every signal pointing at "Corp died" but the bot stayed in FIGHTING_CORP.

- **(a)** Same-tick HP=0 detection threshold tightened from `<= 1.0` to `<= 0.0`. Corp's HP bar reading 1.0 means ~20 HP remaining (alive but dying), NOT dead. Pre- 1.9.99.101 we'd transition to LOOTING at 1%, run handleLooting (resets peakHP=0), then bounce back to combat with peak rebuilding from a dying Corp's 1% observation. With this tightened, only HP truly at 0% triggers same-tick LOOTING — the timeout paths (3s fast, 15s sustained) catch Corp's actual death via NPC despawn.
- **(b)** Relaxed engaged-this-kill gate for the timeout path: accepts `lowLastHp = lastObservedCorpHpPercent > 0 && < 30` as alternate proof of engagement. The original `peakHP > 5%` check protects against the freshly- engaged-bar-reads-0 false positive — but it also blocks legitimate deaths when peak got reset to a low value mid-fight (state oscillation, HP bar visibility flicker, etc.). A bar reading of 1-29% only occurs AFTER damage, so observing it IS proof of engagement. Either signal qualifies. User: "they are the size that you made them. They both show 1% but those values are absolutely incorrect."

## [1.9.99.100]

prepareSpecWeaponForCorp's eat-gate now uses settings.specDumpPanicTeleHp instead of the hardcoded INTERNAL_COMBO_EAT_HP (50). Pre-1.9.99.100 the function combo-ate at HP <= 50 every time Corp became visible — even when the bot was supposed to be mid spec dump and the user's setting said "don't eat above 35". Same threshold now applies to entering-combat-prep as the mid-spec-dump panic-tele gate. User: "it seems like we are still eating when above 35 health when specing down; specifically with the arclight but i dobut its weapon specific."

## [1.9.99.99]

coordinator-confirm sanity log. Dedicated WARN line when the team kill_id advanced past our local — surfaces the kill_id drift (team - local) so we can verify the cross-bot flow once we test with teammates. Drift > 1 means we missed multiple kills (extended bank trip); flagged in the log so it's obvious. The script doesn't currently run with a coordinator, but the log will fire the first time it does. User: "yes add the sanity log even though we arnt running with a cordinator yet."

## [1.9.99.98]

corp-death detection diagnostics added to the paint overlay. Mirrors the gate logic in handleFightingCorp's missing-Corp else branch so the user can see at-a-glance which gate is blocking the LOOTING transition when state is stuck at FIGHTING_CORP after a kill. New block shows inBossRoom, peakHP%, lastHP%, missing duration, fast/ sustained timeout readiness, local + team kill IDs, and whether a teammate has confirmed the kill. Box height bumped from 20 lines to 27. User: "can you add all these required things to the paint in case the debug blows through it after the kill so we can tell what actually happening?"

## [1.9.99.97]

drift recheck rewritten to fire ONLY on the four trigger scenarios the user identified: (1) ENTERING_COMBAT → FIGHTING_CORP (just walked in) (2) HANDLING_DARK_CORE → FIGHTING_CORP (just killed core) (3) Panic-retreat end (just walked 5 tiles off, need to re-find slot) (4) POH/banking return (covered by trigger 1 via ENTERING_COMBAT) New needsRepositioning flag defaults to true (first entry needs positioning) and flips false after a successful reposition OR after isInGoodCorpPosition returns true. Mid-fight transitions (USING_SPECIAL_ATTACK ↔ FIGHTING_CORP between specs) do NOT re-arm the flag. Removed the 1.9.99.96 dual-interaction check (alreadyAttackingCorp || corpInteractingWithMe) — redundant now that the entire drift block is gated mid-fight. antiStompTick at the top of the main loop still handles real under-Corp situations via corpArea.contains(myPos), so this aggressive gate is safe. To revert: remove the `needsRepositioning` gate at the drift recheck site and the four flag-set lines. User: "Most if not all occasions mid fight should not have any issues. Can we try changing our drift check code to only account for those scenearios and leave a note so if it doesnt work we can revert that?"

## [1.9.99.96]

two fixes from the post-kill review.

- **(a)** HP-jump respawn detection. The 1.9.99.94 absence- window check (Corp NPC missing > 1s + reappear at full HP) didn't fire when Corp's NPC stayed present continuously through death animation + respawn — same NPC slot, just animation transition. Now we also detect respawn via HP jump: if the most-recent observed Corp HP was low (< 30%) and current HP is high (> 80%), a death-and-respawn happened. Route to LOOTING. Fires before the existing absence check so log lines distinguish the two paths (RESPAWN (HP-jump) vs RESPAWN (absence)). User: "when corp dies our state stays as FIGHTING_CORP until he respawns and then we go straight to attacking him, no banking between kills, no reset."
- **(b)** Drift recheck skip now uses BOTH directions of interaction. Pre-1.9.99.96 the skip relied on isPlayerAttackingCorp(corp) alone — which can read false briefly between swings. Added corp.isInteractingWithMe() as a parallel signal so Corp targeting us (even between our own swings) also blocks the false-positive minimap reposition. antiStompTick at the top of the main loop handles real under-Corp situations, so this drift-skip is safe to aggregate-OR. User: "WE might hbe able to just use an interacting check? Is there every any way we would be interacting with him where we would get stomped?"

## [1.9.99.95]

handleLooting() now waits up to 6 seconds for valuable loot to appear on the ground before scanning. Pre-1.9.99.95 we transitioned to LOOTING the instant Corp died (or via the 1.9.99.91 timeout), ran the pickup loop once against an empty groundItems snapshot, and moved on without anything. Corp's death anim is ~3s + server tick lag means loot can take 4-5s to spawn. The wait early- exits as soon as ONE valuable item appears so the already-there common case adds no latency. User: "Loot handling should probably take around 5-7 seconds because its death animation is slow."

## [1.9.99.94]

Corp respawn detection. Closes the gap where the 15-second sustained-absence timeout was about to fire, but Corp respawned (e.g. at 13s) and the bot just engaged the new Corp without ever transitioning to LOOTING — missing the prior kill's drops AND skipping banking. Now: on each tick where Corp NPC is visible, we snapshot corpMissingSinceMs BEFORE resetting it. If (snapshot indicates Corp was absent > 1s) AND (current HP > 95%) AND (we engaged the prior kill), it's a respawn → route to LOOTING before touching the new Corp. corpSeenAtZeroHp set so handleLooting runs cleanly. User: "what happens if we go into the corp room and we are in the last phase waiting for the 15 seconds and it respawns at 13 seconds; what is our flow then?"

## [1.9.99.93]

coordinator-confirmed death signal added. Short- circuits the timeout when the team kill_id (max across all bots' published localKillId) has advanced past our localKillId — meaning a teammate completed the kill we were both fighting. Same in-boss-room + engaged-this-kill gates still apply so a stale teamKillId from before we joined doesn't trigger a false LOOTING. New helper coordinatorTeamKillId() reads from the port coordinator (in-memory, real-time) with file-coordinator fallback. User: "we need to either receive the info that he died from the cordinator if one exists; or be in the actual boss room and he doesnt exist/died."

## [1.9.99.92]

Corp-death timeout fallback hardened. The 1.9.99.91 3-second-missing trigger could false-positive if Corp roamed to a part of the room the SDK Query couldn't see, or if the player teleported out (boss room chunk unloads, Corp drops out of the scene). Now requires THREE gates:

- **(a)** isInCorpBossRoom() — we're physically in the room so the scene query is authoritative.
- **(b)** maxCorpHpPercentThisKill > 5% — we engaged.
- **(c)** Either (lastObservedCorpHpPercent < 30% AND missing > 3s) — Corp was dying, fast path; or sustained absence > 15s — Corp's been gone way too long to be merely roaming. New field lastObservedCorpHpPercent tracks the most-recent HP% reading; resets on kill end. Debug log when timeout conditions aren't met so we can tell why the bot is waiting. User: "he can just be on the far side of the room and we will wipe our progress assuming he died."

## [1.9.99.91]

Corp-death timeout fallback. When Corp dies, the death animation hides the HP bar for a tick or two before Corp's NPC fully despawns. If our HP-poll missed the brief "HP <= 1%" window (corp.isHealthBarVisible() can be false during the animation), corpSeenAtZeroHp stayed false and the bot logged "Corp not in render but no confirmed 0 HP" forever, never leaving FIGHTING_CORP. Fix: track corpMissingSinceMs (timestamp of first tick Corp wasn't in Query.npcs()). If Corp missing > 3s AND maxCorpHpPercentThisKill > 5% (proves we engaged this kill), declare dead → LOOTING. Resets on kill end and on Corp re-appearance. User: "when the kill ends we just prepare for combat again i think the status was fighting_corp even after he died. when we should be banking."

## [1.9.99.90]

spec-bar debounce energy-drop bypass. The 03:47 log caught a 15-second spec lockout: spec 1 fired (100→50), bar auto-toggled OFF (game rule), tryActivateSpec then refused to re-click for the full 600-1200ms debounce window AND beyond (because each subsequent retry hit the 600ms trust-window again). Bot took 5+ Corp hits during the lockout, HP dropped to 20, mid-spec-dump panic tele fired. Fix: snapshot Combat.getSpecialAttackPercent() at each successful click (specEnergyAtLastActivate). On entry to tryActivateSpec, if current energy < snapshot, a spec already fired and the bar is legitimately off — skip the debounce entirely and re-click. The original debounce was designed for "SDK lag after a click" not "spec already swung". This separates the two cases. User: "we got stuck trying to spec and failed it and then ended up eating; i thought we were supposed to be teleporting if we are under 35?"

## [1.9.99.89]

configurable spec-dump panic-tele HP threshold. Added settings.specDumpPanicTeleHp (default 35). During the spec dump cycle (spec weapon equipped + energy >= floor + phase incomplete), the panic-tele trigger uses this value instead of the general INTERNAL_PANIC_TELE_HP (25). Combo-eat-at-50 stays skipped via shouldSkipEats. Outside spec dump, the 25 threshold still applies. New GUI spinner in the Spec tab so users can tune per-account. User: "currently we will often get under 50 especially in the first few attacks of darklight/arclight before weve reduced stats ... If we get under 35 HP teleport out."

## [1.9.99.88]

mid spec-dump emergency eat now tele-outs to safety. 1.9.99.87's "keep speccing through panic eat" was a regression — the user wanted EMERGENCY_ESCAPE on any combo-eat-threshold hit during the spec dump cycle. Bot eats once (HP recovers) then routes to ring-tele Ferox. Abandoning the remaining spec(s) is the right trade for not dying in the kill room. The non-spec-dump panic retreat (5-tile step-off) stays for the FIGHTING_CORP general-combat case. User: "What ar you talking about; the Panic retrated skip is literally a regression. IF we hit an emergency eat threshold mid spec we should just tele out."

## [1.9.99.87]

three fixes from spec-cycle / drift / kill-phase review.

- **(a)** Skip panicRetreatFromCorp when inSpecDumpCycle is true. Pre-1.9.99.87 a panic eat fired during the 2-spec window also triggered the 5-tile retreat, trading a guaranteed spec hit for a manual walk — "instead of tping away after our double specs, if we had to panic eat sometimes we run away a few tiles instead of specing first which could get us killed". Now: eat, let the spec fire, energy depletes, the existing POH-restoration path handles the tele out.
- **(b)** Drift-recheck interval 500ms → 250ms. Sub-tick drift catches Corp's 1-tile roams within ~half a game tick so the bot's belief about Corp's position never lags by more than 1 tile. User: "if the corp for some reason moves 1 square back, before our realization of where the corp is standing updates, we think we are in its hitbox, and we use the minimap to walk back a square."
- **(c)** Re-added a kill-phase gate to shouldUseSpecialAttack. Once isInKillPhase() returns true (Corp HP < 1700 OR teamPhaseNeeded == 0), no more spec firing — Fang melee finishes the kill. Reverses 1.9.30's removal which let phase-1 specs continue past Corp HP 1700. User: "im thinking if we detect that the bosses health is under 1700 we stop spec dumping and just participate in the kill."

## [1.9.99.86]

post-kill maintenance skip. Pre-1.9.99.86 the bot ate food / drank prayer pots / etc. during LOOTING and the brief tick window where Corp had just died. Those actions were wasted because next stop is BANKING (full restore at pool) or WAITING_FOR_TEAM → POH restoration cycle (full restore). Now: handleHealthAndPrayer skips when state == LOOTING, same as it already did for TELEPORTING_TO_HOUSE / ENTERING_FRIEND_HOUSE / USING_ORNATE_POOL / TELEPORTING_BACK_TO_CORP / BANKING_AND_HEALING / W330_RESTORATION. handleVengeanceLogic also adds LOOTING to its state-block list (boss dead = nothing to reflect, wasted cast). User: "if corp died we dont need to repot or use food or anything because we are going to bank/poh and get full spec."

## [1.9.99.85]

house-tab withdrawal added to the bank trip. Pre- 1.9.99.85 the bot never restocked tabs at the bank; once tabs ran out, every subsequent kill silently degraded to slow Fang-only DPS (shouldStartRestorationCycle was blocked, so no POH restoration). Now: at the end of each banking trip, if inventory tab count is below INTERNAL_HOUSE_TAB_REFILL_BELOW (4), withdraw up to INTERNAL_HOUSE_TAB_TARGET (10). Tabs survive deposit (depositKeepList line 9933) so leftovers ride between trips. Warning logged if the bank itself has 0 tabs — user-craft pipeline can be added later. User: "add house tabs to bank withdrawal."

## [1.9.99.84]

spec-dump eat-skip window extended. Pre-1.9.99.84 the bot ate normal-threshold food between consecutive specs because state briefly flipped from USING_SPECIAL_ATTACK back to FIGHTING_CORP for ~600ms while the spec swing resolved; handleHealthAndPrayer ran 12× in that window and fired a normal eat if HP was below eatHealthThreshold. Now the eat-skip flag covers the entire spec-dump cycle: spec weapon equipped + spec energy >= floor + phase incomplete + state in {FIGHTING_CORP, USING_SPECIAL_ATTACK}. Critical-HP eats (PANIC_TELE) still fire. Walk-in eats still fire because walk-in state is ENTERING_COMBAT (not in the new gate). User: "the account im playing on manually run in and double spec and tp out without needing to eat food usually ... however our bot keeps eating and usually ends up doing a normal attack inbetween our eating/spec dumping."

## [1.9.99.83]

lobby-during-FIGHTING_CORP recovery. When Corp is right at the entrance, the click rectangle for "Attack Corp" overlaps the click rectangle for the passage "enter". A left-click intended for Corp sometimes lands on the passage hitbox, teleporting the player back to the lobby — but the state machine stays at FIGHTING_CORP. The next tick's attackCorpIfVisible fallback sees Corp "off-screen" and steps toward Corp's last-known tile, walks back through the passage, gets yanked to lobby again. In-and-out loop. Fix: top of handleFightingCorp checks isInCorpLobby() && !isInCorpBossRoom(). If true, bounce state to ENTERING_COMBAT so the walk-in / passage-click logic runs cleanly. User: "the failure is because the attack and the go-through passage are in the same location and when we left click to attack we miss the hitbox." (Note: 1.9.99.83's first attempt was a corpCave step-out guard in attackCorpIfVisible — that path uses LocalWalking which can't actually cross through the passage; the bug was always the passage click. Reverted that change.)

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
