package scripts.corp;

import org.tribot.script.sdk.*;
import org.tribot.script.sdk.input.Keyboard;
import org.tribot.script.sdk.query.*;
import org.tribot.script.sdk.script.TribotScriptManifest;
import org.tribot.script.sdk.types.*;
import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.walking.LocalWalking;
import org.tribot.script.sdk.util.ScriptSettings;
import org.tribot.script.sdk.util.TribotRandom;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.util.*;
import java.util.stream.Collectors;
import org.tribot.script.sdk.painting.Painting;


/*
 * Corp.java — multi-bot OSRS Corporeal Beast team-fighter script.
 *
 * Changelog: see CHANGELOG.md at the repo root
 *   https://github.com/not-tivia/TRiBot/blob/master/corp/CHANGELOG.md
 * Per-change-site rationale lives inline as // 1.9.99.X: comments
 * adjacent to the affected code — this header used to inline the full
 * changelog (~1087 lines) but that bloated the script. The MD file is
 * authoritative now; inline comments cover the per-line "why".
 *
 * Current bytecode version: see SCRIPT_VERSION constant below.
 */
@TribotScriptManifest(name = "Corp", author = "Me", category = "Combat", description = "Corporeal Beast team fighter (modernized)")

public class Corp implements TribotScript {

	// ========== SETTINGS / RUNTIME ==========
	// 1.9.90: explicit version constant (was previously only in changelog comments)
	// 1.9.91: friend-house dialog-already-open recovery in enterFriendHouse().
	// 1.9.92: re-arm spec bar each loop iteration in handleSpecialAttack.
	// 1.9.93: portal-first reorder — only run dialog-open probe if portal
	//         lookup fails. 1.9.91's pre-portal probe false-positived on
	//         stale chatbox widgets and made the bot type RSN into public chat.
	// 1.9.94: tryActivateSpec 250ms settle window — fixes intermittent
	//         double-click that toggled spec OFF when pre-activate + handle
	//         fired ~50ms apart and SDK lagged the post-click state.
	// 1.9.95: removed isInKillPhase early-return from handleVengeanceLogic
	//         (veng now fires lobby + whole kill, not just HP<1700 window);
	//         + 500ms debounce on the in-fight corp.interact("Attack")
	//         re-engage so multi-tick clicks after eat/spec don't stack.
	// 1.9.96: removed isVengeanceStillActive() block in canCastVengeance.
	//         The probe used HP-bar-invisible as proxy for "veng still up",
	//         which false-positived whenever the bot ate back to full —
	//         blocking re-casts for the entire long kill. 30s cooldown alone
	//         is sufficient; OSRS refreshes the buff on re-cast.
	// 1.9.97: deterministic veng-consumed signal via HP delta. New flag
	//         tookDamageSinceLastVeng — flipped true in updateHealthTracking
	//         on currentHealth<previousHealth, flipped false on successful
	//         cast. canCastVengeance now requires it true. Replaces all
	//         HP-bar-visibility inference.
	// 1.9.98: re-introduced isInKillPhase early-return in handleVengeanceLogic.
	//         Veng now restricted to kill phase (teamPhaseNeeded==0 OR Corp
	//         HP <1700) so it doesn't fire during spec dumping or in lobby.
	//         Safe to re-enable now that 1.9.97 fixed the underlying cast-
	//         blocking issue that motivated 1.9.95's removal.
	// 1.9.99: 3 fixes — (a) friend-house input probe rejects chat scrollback
	//         (timestamp prefix, "received a drop", >32 chars) so RSN drop
	//         announcements don't false-positive as the input field;
	//         (b) Games-necklace fallback now verifies tele actually landed;
	//             if not, transitions to BANKING_AND_HEALING instead of
	//             stranding in WAITING_FOR_TEAM at the wrong location;
	//         (c) spec-hit detection bumped from 2s→4s and XP baseline re-
	//             captured at energy-drop moment so Elder maul's 6-tick
	//             animation no longer false-negatives as "Spec MISS".
	// 1.9.99.1: spec-hit windows bumped further: handleSpecialAttack loop
	//         4s→5s, processPendingSpecHit (in-line detector) 2s→5s. User:
	//         "the spec hit twice and it counted as 1" — Elder maul 6-tick
	//         animation + SDK getXp() lag was still missing the second hit.
	// 1.9.99.2: prepareSpecWeaponForCorp now sets specPreActivatedThisTrip=true
	//         after pre-activating. Without this, stage C re-runs on the
	//         first FIGHTING_CORP tick and, if a spec fired between prepare
	//         and the tick (player auto-attacked Corp with spec bar on),
	//         stage C stomps lastSeenSpecEnergy to the post-fire value —
	//         hiding the fire from the L3287 in-line detector. User
	//         observed ~5 actual specs fired but only 3 counted; this was
	//         the silent-spec leak.
	// 1.9.99.3: in handleSpecialAttack loop, VERIFY spec bar actually ON
	//         after tryActivateSpec before firing corp.interact. The 1.9.94
	//         settle window can return true within 250ms of a previous
	//         click without verifying SDK state — if a weapon swap (e.g.
	//         rotation Elder maul → Arclight) happened in between, the bar
	//         was toggled OFF by the swap, settle window lied, and the
	//         next swing fired as auto-attack. 3 consecutive "timed out"
	//         warnings in user's Arclight rotation log.
	// 1.9.99.4: trimmed 1.9.99.3's verify waits 800→400ms each. SDK
	//         propagation of a spec-bar click usually completes within
	//         ~300ms; 400ms is enough headroom without adding a second of
	//         dead air per spec when the settle window was correct.
	// 1.9.99.5: REMOVED the 5s synchronous hit-confirmation wait in
	//         handleSpecialAttack. Bot was standing inside Corp's hitbox
	//         for up to 5s per spec waiting for XP/hitsplat while Corp
	//         freely dealt 50+ damage per swing. Now spec firing sets
	//         pendingHitWeapon and exits immediately; processPendingSpecHit
	//         confirms (or marks miss) asynchronously on subsequent
	//         FIGHTING_CORP ticks within the same 5s deadline window. Bot
	//         can eat / dodge core / continue swinging during confirmation
	//         instead of blocking. Multi-spec loop calls processPendingSpecHit
	//         between specs to preserve prior baseline before overwriting.
	//         BGS damage capture moved to processPendingSpecHit.
	// 1.9.99.6: REMOVED the force-click fallback in handleSpecialAttack's
	//         in-loop re-arm. 1.9.99.3-5 sent a second activateSpecialAttack
	//         click after 400ms if SDK still reported bar OFF — but if the
	//         first click was just in-flight, the second click toggled the
	//         bar OFF, then corp.interact swung as auto-attack with no
	//         energy drop and 5s timeout. Bot stood in Corp's hitbox for
	//         ~8s while this double-toggle played out. Now: single click
	//         + 700ms verify (one full game tick). If still OFF, break out
	//         and let the outer FIGHTING_CORP loop retry — between attempts
	//         bot can eat / dodge / swing instead of double-clicking
	//         itself into a stale-bar state.
	// 1.9.99.7: process prior pending hit BEFORE overwriting its baseline
	//         in handleSpecialAttack's loop. Spec N's XP arrives at swing
	//         END (~3600ms after energy drop), which is exactly when spec
	//         N+1's energy drop fires (next swing starts at end of prior
	//         anim). Overwriting baseline at spec N+1's fire burned spec
	//         N's XP into the new baseline → spec N never confirmed. User:
	//         "its hit 3 specs and has counted 0 of them in specs this
	//         kill being hit".
	// 1.9.99.8: keep specWeaponReadyForUse=true after handleSpecialAttack
	//         exit when energy >= min — so the L3287 in-line detector can
	//         pick up future spec fires from natural auto-attacks. Pre-
	//         1.9.99.8 we cleared it on every exit, blocking the detector.
	// 1.9.99.9: detect silent spec fires during handleSpecialAttack's
	//         in-loop re-arm verify wait. If tryActivateSpec clicked but
	//         the 700ms verify timed out yet energy dropped during the
	//         wait, a spec fired silently (click landed late, bar toggled
	//         ON briefly, player auto-attack consumed it, bar back OFF).
	//         Without detection here the silent fire was completely
	//         untracked. User log: "Spec bar didn't toggle ON within
	//         700ms ... Completed 1 special attack(s), final energy: 0%"
	//         when only spec 1 was logged firing.
	// 1.9.99.10: tryActivateSpec settle window 250ms→800ms. Two
	//         tryActivateSpec callers spaced 300-500ms apart (e.g.
	//         L3303 pre-activate then handleSpecialAttack's L7027 re-arm)
	//         both saw bar OFF (SDK hadn't reflected the first click) and
	//         both clicked → toggle ON then OFF → net DISABLED. User:
	//         "is it possible that when it enables spec on the way and
	//         then starts speccing the boss its disabling that? i see
	//         double clicking occasionally". 800ms = one game tick (600ms)
	//         + click-to-SDK-reflect lag (~200ms).
	// 1.9.99.11: TWO false-positive fixes for spec hit attribution.
	//         (A) Capture firedWeapon BEFORE processPendingSpecHit's
	//             rotation in handleSpecialAttack loop. Pre-1.9.99.11 a
	//             mid-bar rotation (Elder maul → Arclight when phase 1
	//             target met) caused the second spec's pending to be
	//             stamped with the rotated weapon, attributing Elder
	//             maul's swing to Arclight.
	//         (B) processPendingSpecHit checks deadline FIRST. Pre-1.9.99.11
	//             a pending hit that survived a state change (e.g. across
	//             a 30s restoration cycle) would later get "confirmed" via
	//             XP delta from normal attacks during that time — false
	//             positive. User: "counted 2 normal attacks with the
	//             arclight a with no spec as arclight specs". Now we mark
	//             miss when deadline expires regardless of signal state.
	// 1.9.99.12: handleSpecialAttack's 5s energy-drop wait now ticks
	//         processPendingSpecHit each ~50ms iteration. Pre-1.9.99.12
	//         the Waiting.waitUntil block prevented processPendingSpecHit
	//         from running during the wait — prior specs' XP/hitsplat
	//         signals arrived in that window but were never observed.
	//         By the time handleSpecialAttack exited and FIGHTING_CORP
	//         resumed, hitsplats (~3.6s lifespan) had expired and 1.9.99.11
	//         strict deadline marked the spec as miss. User: "i hit the
	//         corp with an elder maul spec 3 times and its only counted
	//         one". Now signals get processed in real-time during the
	//         wait, catching confirmations as they arrive.
	// 1.9.99.13: stepAwayFromCore SAFETY fixes after death from stomp.
	//         (A) Refuse to step if Corp area can't be read — pre-1.9.99.13
	//             null corpArea bypassed the inside-Corp check entirely
	//             and accepted tiles literally on Corp's hitbox. User:
	//             "i just ran away from the core directly under the corp
	//             and got stomped to death with 3 stomps".
	//         (B) 2-tile buffer around Corp's 5x5 hitbox. A tile right at
	//             the hitbox edge becomes inside it the next tick when
	//             Corp roams. 9x9 exclusion gives Corp room to move
	//             without stomping the player on arrival.
	// 1.9.99.14: processPendingSpecHit now runs in EVERY state (called
	//         before the state dispatcher), not just FIGHTING_CORP. The
	//         last spec of a bar fires right before the bot transitions
	//         to PREPARING_RESTORATION_CYCLE → ... 30s of non-FIGHTING_CORP
	//         states → no spec-hit signal processing → deadline expires
	//         → 1.9.99.11 strict deadline marks miss. User: "two of our
	//         elder maul specs hit back to back and only the first one
	//         counted".
	// 1.9.99.15: handleProtectionPrayers no longer spam-clicks
	//         PROTECT_FROM_MAGIC.enable() when prayer points = 0. The
	//         click can't succeed without points; bot was logging
	//         "Activating Protect from Magic" multiple times per second
	//         mid-fight, burning ticks between eat/spec cycles and
	//         causing deaths. Now: drink a prayer pot first if doses
	//         exist; if still 0, return silently so L3315's bank-trip
	//         check fires. User: "tries to enable prayer even though
	//         we have no prayer points constantly during the fight".
	// 1.9.99.16: wait for pool-drink animation to finish before clicking
	//         the bank chest in handleBankingAndHealing. Pre-1.9.99.16
	//         useRestorePool returned with the player still animation-
	//         locked from the drink, and the immediately-following bank
	//         chest click failed silently — Bank.isOpen() wait then hit
	//         its 10s timeout every single time. User: "we try to click
	//         the bank at ferox the same tick we use the pool so we are
	//         already locked in an animation so we hit the timeout every
	//         single time".
	// 1.9.99.17: handleStarting now routes to PREPARING_RESTORATION_CYCLE
	//         when prayer or spec is below full. Pre-1.9.99.17 the bot
	//         went straight to Corp regardless of stat state — starting
	//         after a disconnect with depleted prayer/spec meant entering
	//         combat unprepared. User: "if we start the script outside
	//         our friends poh but our prayer or spec isnt already 100 we
	//         start the trip without attempting to restore anything and
	//         just go straight to the boss".
	// 1.9.99.18: handleTeleportingToHouse short-circuits when already in
	//         a usable house. Saves a wasted house tab and ~5-10s of
	//         travel when the user starts inside their friend's house
	//         or inside their own POH. Three cases handled: in-friend-
	//         house+friend-mode → straight to pool; in-own-house+own-mode
	//         → straight to pool; in-own-house+friend-mode → skip tab,
	//         go straight to the friend-house portal step.
	// 1.9.99.19: settle wait after Prayer.enableQuickPrayer() so the
	//         immediately-following PFM check doesn't see stale "off"
	//         state and redundantly open the prayer tab to re-enable a
	//         prayer that's already on. User: "we enable our prayers
	//         with click prayers but then we go to our prayer tab to
	//         enable the prayer thats already on. this seems redundant".
	// 1.9.99.20: equipSpecWeapon resets lastSpecActivateAt=0 on success.
	//         Weapon swap toggles the spec bar OFF. Pre-1.9.99.20 the
	//         settle window (set by a prior pre-activate ~300-500ms ago)
	//         caused tryActivateSpec to return true without re-clicking
	//         after the swap — bot proceeded with bar OFF, swing fired
	//         as auto-attack, 5s energy-drop timeout. Now: invalidate
	//         the settle timestamp on weapon swap so the next
	//         tryActivateSpec actually fires a click.
	// 1.9.99.21: bumped tryActivateSpec settle window 800ms→1500ms. The
	//         L3287 in-line detector + L3303 main pre-activate cycle
	//         was ~1000ms apart (mouse + click animation), past the
	//         800ms settle. Second call fell through, SDK reported bar
	//         OFF momentarily (lag), clicked AGAIN, net result: toggled
	//         ON then OFF → bar OFF for the actual spec fire. User:
	//         "STILL DOUBLE SPEC ACTIVATING".
	// 1.9.99.22: pre-swing XP baseline in handleSpecialAttack loop +
	//         L3287 in-line detector. Pre-1.9.99.22 baseline was captured
	//         AT energy drop — meaning the swing's XP was ALREADY in the
	//         baseline (game applies XP at swing impact = same tick as
	//         energy drop). For single-hit specs (Elder maul / Arclight-
	//         on-Corp), nowXp later = baseline, delta = 0, no XP
	//         confirmation. Now: handleSpecialAttack's loop uses the
	//         previous-poll XP (~50ms before drop); L3287 in-line
	//         detector uses xpAtSpec (set at pre-activate, pre-swing).
	//         User: "some xp drops when spec drops doesnt count the
	//         specs as successful... mostly arclight".
	// 1.9.99.23: dark core grace-period path now DPSes Corp instead of
	//         standing AFK. Pre-1.9.99.23 the 3s grace window between core
	//         render-flickers had no fallback — bot held kill weapon and
	//         did nothing. With auto-retaliate off (1.9.64), no swings at
	//         all. User: "we got stuck in a state of the core being out
	//         so we were just standing there waiting for it... kinda afk".
	// 1.9.99.24: stepAwayFromCore Corp buffer 2→3 tiles. Corp can move
	//         up to 1 tile per game tick. With a 2-tile buffer + 2-3 tick
	//         walk, Corp can roam into the destination tile. 3-tile
	//         buffer (5 tiles from Corp center) covers Corp moving up
	//         to 3-4 tiles during the walk. User: "one of the moves we
	//         made to run away from core after hitting it was 1 tile
	//         under the corp".
	// 1.9.99.25: inSpecDump (eat-skip condition) now ALSO requires the
	//         state to be USING_SPECIAL_ATTACK. Pre-1.9.99.25 the spec-
	//         weapon-equipped check caught the entire approach phase
	//         (Elder maul equipped during walk to Corp). Bot took mage
	//         + melee hits through prayer for 9 seconds without eating,
	//         died on arrival. User: "we just ran up and died instead
	//         of panic eating".
	// 1.9.99.26: getDistanceToCorpHitboxEdge now uses corp.getArea().
	//         getCenter() instead of corp.getTile() as the hitbox center.
	//         For 5x5 NPCs, corp.getTile() returns the SW CORNER — pre-
	//         1.9.99.26 the hitbox radius (2 tiles) was applied to the
	//         SW corner, shifting the perceived hitbox 2 tiles SW of
	//         its real position. Half of the 4 cardinal candidates got
	//         falsely flagged as "too close to hitbox edge" and dropped
	//         from the filter list, then the line-cross check often
	//         rejected the remaining 2 → "ALL N cardinals require
	//         crossing Corp" fired and the bot fell back to game-pathfinder
	//         click-attack (slow approach, took mage+melee for ~9s).
	//         User: "theres ALWAYS free spaces open in front of corp
	//         but somehow we still are trying to find other positions".
	// 1.9.99.27: status overlay moved from Swing JFrame popout to in-client
	//         Painting.addPaint callback; plus tile-debug section showing
	//         Corp center, player pos, and each cardinal candidate with
	//         its cross/clear classification. Camera angle target lowered
	//         100→75 and the readjust threshold 80→50 — bot no longer
	//         pegs the camera at max pitch (a clear bot tell). User: "can
	//         we draw the paint in the screen/on the client instead of
	//         having the seperate popout window", "real players dont
	//         adjust their camera angle all the way to the lowest
	//         possible angle, they simply rotate the cameras view".
	// 1.9.99.28: "Spec HIT confirmed" log now includes Corp HP% so we
	//         can verify whether two same-XP-delta confirmations
	//         correspond to two real hits (corpHP drops twice) or a
	//         double-count of one hit (corpHP only drops once). User:
	//         "we counted two specs even though only one hit ... its
	//         improbable we hit a 45 twice in a row".
	// 1.9.99.29: Add Corp HP delta as a third veto signal in
	//         processPendingSpecHit. Root cause of the double-count:
	//         when spec 1's XP arrival and spec 2's energy drop land on
	//         adjacent poll iterations, lastPollXp holds the pre-spec-1
	//         baseline so spec 2's stamped baseline is stale. The
	//         dispatcher then sees the SAME XP delta a second time and
	//         credits a phantom hit. User's log showed two
	//         "+91 XP delta" confirmations with IDENTICAL corpHP=1.0% —
	//         Corp HP didn't change between them, so only one hit truly
	//         landed. New field pendingHitCorpHpBaseline is stamped at
	//         every spec-fire site; if confirmation arrives without
	//         corpHP having dropped > 0.05%, the confirmation is vetoed
	//         and logged as a stale-XP miss.
	// 1.9.99.30: Tighten isNearFeroxBank to require physical proximity
	//         to the bank/pool tile (3135, 3630) — distanceTo <= 4 — so
	//         handleBankingAndHealing actually walks to the tile before
	//         clicking the pool. Pre-1.9.99.30 the check returned true
	//         whenever Query found a Bank chest in render distance,
	//         which happens at the Ferox teleport landing spot even
	//         though the pool is still 10+ tiles away. Result: useRestorePool
	//         tried to interact with a pool whose bounding rect wasn't on
	//         screen → repeated hover-without-click. User: "we are trying
	//         to hover over the pool or bank instead of clicking to the
	//         designated tile first in ferox". This re-enforces the
	//         1.9.58 walk-first contract.
	// 1.9.99.31: Two fixes from the 01:38 combat log.
	//         (a) MIN_DISTANCE_FROM_CORP_EDGE: 3 → 1. The position
	//         generator places cardinals 1 tile from Corp's hitbox edge
	//         (correct for melee), but the validator rejected anything
	//         under 3 — so all four cardinals failed, the bot bailed to
	//         emergency positions (also failed), and attacked from
	//         wherever it stood. "EMERGENCY: Combo eating" fired because
	//         the bot was getting whacked while indecisively in/near the
	//         hitbox. The 3-tile threshold was a leftover from mage/range
	//         positioning, not melee.
	//         (b) CAMERA_CHECK_INTERVAL_MS: 5000 → 2000. Camera drifted
	//         from 93 to 33 over 11 seconds during combat. User
	//         confirmed: TRiBot's built-in camera-rotate-to-target,
	//         baked into the SDK's interact() pipeline — fires
	//         automatically when we click an NPC/tile and there's no
	//         public toggle to disable it (Camera class exposes
	//         setAngle/setRotation/setRotationMethod but no
	//         disable-auto-rotate flag; AntibanProperties has no
	//         camera-rotate setting either). Our only mitigation is
	//         catching the drift faster. 2s gives ~4 checks per spec
	//         cycle. User: "we also still lowered our camera angle too
	//         low ... its the camera rotate to target built into tribot".
	// 1.9.99.32: Suppress the camera-rotate-to-target at its trigger
	//         instead of just correcting after the fact. All 12 live
	//         corp.interact("Attack") sites now go through
	//         attackCorpIfVisible(corp): if Corp.isVisible() returns
	//         false, walk one tile toward Corp and skip this tick (the
	//         caller's outer loop retries next tick). The SDK only
	//         rotates the camera when the click target is off-screen;
	//         pre-gating on visibility means rotate-to-target never
	//         fires during combat. User: "as long as we are keeping a
	//         high camera angle we dont really need to adjust to
	//         interact we can just walk towards the boss or attack it
	//         IF its ons screen already". Dark-core attack at L4090
	//         left alone (different target, different visibility).
	// 1.9.99.33: walkToFeroxBank had the SAME Query-visibility trap
	//         that 1.9.99.30 fixed in isNearFeroxBank — at the Ferox
	//         teleport landing spot the bank chest is in render
	//         distance, Query found it, walkToFeroxBank logged "Bank
	//         chest already visible — no walk needed" and returned
	//         without walking. handleBankingAndHealing's next tick
	//         re-saw isNearFeroxBank=false (distance gated), called
	//         walkToFeroxBank again — infinite loop, bank never opened,
	//         bot burned every Games necklace re-teleporting. Replaced
	//         the Query check with the same distanceTo(3135,3630) <= 4
	//         guard isNearFeroxBank uses. User: "we ran out of game
	//         necklace ... character teleported to the enclave but
	//         isnt attempting to run to the tile ive told you to".
	// 1.9.99.34: Removed the pre-combat "Health low after spec prep"
	//         karambwan eat (was at L8832). HP 58 was triggering an
	//         eat right before combat start, which (a) wasted a
	//         karambwan and (b) put us in an eat-animation lock at the
	//         moment Corp is most likely to drift onto our tile —
	//         antiStompTick then had to bail us out of being under
	//         Corp. In-combat eat logic handles real HP danger. User:
	//         "we dont need to eat right after we drink our potion ...
	//         we also stepped under the boss and should have gotten
	//         stomped on".
	// 1.9.99.35: Root-cause fix for HP-58-at-trip-start. The startup
	//         restoration gate at L2548 was only checking prayer and
	//         spec — not HP. So if the user started the script at less
	//         than full HP (leftover from earlier play), the bot went
	//         straight to Corp at that HP and the in-combat eat fired
	//         immediately. Now: if HP < maxHP at startup we also route
	//         through the POH ornate pool. User: "why would a pre
	//         combat hp low eat be running if we only lose 10 health?"
	//         — answered: because the bot inherited the player's
	//         pre-script HP and never restored. 1.9.99.34 removed the
	//         pre-combat eat itself; 1.9.99.35 removes the reason it
	//         was triggering.
	// 1.9.99.36: Gate the lobby inventory-space eat on actually needing
	//         the slot. Pre-1.9.99.36 the lobby prep always ate a
	//         karambwan whenever Inventory.isFull, even when the spec
	//         weapon was already wielded and no equip-swap was needed.
	//         The eat animation locked the bot right as it walked into
	//         the boss room — Corp landed a free magic hit during the
	//         chew. User: "we use a karamwan to top oursleve off,
	//         meanwhile the corp notices we are in the room while we
	//         are eating and blasts us with another hit of damage so
	//         we start the fight attacking already lower hp than if we
	//         just ran straight to him and ate while we were running
	//         or panic ate later". Now: only eat-for-slot if the spec
	//         weapon needs to be equipped from inventory.
	// 1.9.99.37: Fix the Corp-HP-veto firing on real hits. The same
	//         timing problem 1.9.99.22 fixed for XP applies to Corp
	//         HP: damage and energy-drop happen on the same tick, so
	//         a readCorpHpPct() at the in-line / silent-fire / sync
	//         detectors returns POST-damage HP — baseline ≡ confirm
	//         reading, delta = 0, veto fires on real hits. New
	//         corpHpAtSpec field is stamped at every pre-activate
	//         (paired with xpAtSpec), and pendingHitCorpHpBaseline now
	//         reads from corpHpAtSpec / preReArmCorpHp / hpBeforeDrop
	//         instead of fresh reads at the detector. User log:
	//         "Spec MISSED (Corp HP didn't drop: 0.99% → 0.99%) ...
	//         XP delta +181" — +181 is a real ~45-damage Elder maul
	//         spec, but both HP samples were post-damage because the
	//         baseline was stamped too late.
	// 1.9.99.38: Draw actual tile polygons on the game world for the
	//         four cardinal Corp positions (replaces the text-only
	//         tile-debug snapshot in the corner). Uses
	//         WorldTile.getBounds() -> Optional<Polygon>. GREEN =
	//         picked, YELLOW = valid alternative, RED = rejected
	//         (line crosses Corp's hitbox), ORANGE = Corp's 5x5
	//         hitbox outline. User: "we are drawing the tex
	//         tlcoations of the corp outter tiles but nto actualyl
	//         drawing them on the game worlds physical tiles".
	// 1.9.99.39: Replaced the single global pending-spec slot with a
	//         FIFO Deque<PendingSpecAttempt>. The old single-slot
	//         design lost spec 1's record whenever spec 2 fired before
	//         spec 1 had confirmed — back-to-back specs were
	//         coin-flips. processPendingSpecHit now iterates oldest-
	//         first; after each confirmation we advance the XP
	//         baseline on remaining attempts so the next attempt
	//         can't double-credit the same delta. Corp HP delta is
	//         now logged for visibility but is no longer a hard veto.
	//         User: "the current detector is clever, but it's still
	//         built around a single mailbox. Back-to-back specs need
	//         a little queue ... Treat Corp HP delta as a debug/tie-
	//         breaker only, not as a hard veto when XP or own
	//         hitsplat confirms."
	// 1.9.99.40: Two safety fixes.
	//         (a) moveToNearestCorpPosition's walk-arrival wait now
	//         polls isUnderCorp every ~80ms and triggers stepOffCorp
	//         on detection. Pre-1.9.99.40 the wait was a single
	//         distance check — during that wait the main-loop's
	//         antiStompTick was BLOCKED, so an SDK A* route through
	//         Corp's hitbox would silently stomp us until arrival.
	//         User: "i just walked under the boss and got hit for a
	//         70 and died". Even at distance-1 cardinals the SDK can
	//         pathfind through the 5x5 hitbox if Corp's tiles register
	//         as walkable in the collision map (NPC blocking != wall).
	//         (b) useOrnateJewelryBox now waits for the pool-drink
	//         animation lock to clear (MyPlayer.isAnimating false)
	//         before clicking the box, and retries up to 3x on miss.
	//         Pre-1.9.99.40 the 600ms settle in useOrnatePool wasn't
	//         always enough — the box click hit a stale frame or
	//         registered on the pool below, leaving the bot frozen
	//         at the timeout. User: "i also noticed a timeout at the
	//         pool in the house wehre i got stuck watiing for a good
	//         couple seconds because i either misclicked on it or
	//         clicked on the jewlwery box too quickly after clicking
	//         the pool".
	// 1.9.99.41: Two more fixes from the dark-core death log.
	//         (a) Force combo-eat during active dark-core engagement
	//         (core seen in the last 5s). The normal-eat tier was
	//         giving a single karambwan (+18 HP) when the core is
	//         ticking 8-12 damage per game tick on top of Corp's hits
	//         — single eats can't keep up so HP bleeds out across
	//         multiple ticks. Combo eats (~38 HP) match the damage
	//         rate. User: "we are taking a garenteed 8-12 health a
	//         tick + whatever corp tiself does to us ... every eat is
	//         a combo eat during that duration if possible".
	//         (b) Refresh the live Corp center tile in overlayUpdate
	//         every tick so the in-client 5x5 hitbox outline tracks
	//         where Corp actually IS, not where the last positioning
	//         recompute snapshotted it. User screenshot: red overlay
	//         tiles (script's belief) were several tiles off from the
	//         green tiles (Corp's real position) — recompute is gated
	//         on the drift trigger, so it doesn't update visually
	//         while Corp roams.
	// 1.9.99.42: Two root-cause fixes for the under-Corp + core kill.
	//         (a) moveToNearestCorpPosition's straight-to-target step
	//         now uses corp.interact("Attack") instead of
	//         LocalWalking.walkTo(bestPosition). The OSRS attack-click
	//         pathfinder lands the player on a tile adjacent to Corp
	//         and explicitly avoids Corp's hitbox; the prior tile-walk
	//         pathfinder didn't, which is why we kept ending up under
	//         Corp despite "safe" cardinal selection. User: "one thing
	//         i dont understand is how i keep standign under it if im
	//         clicking attack on the corp. that should always palce me
	//         inf ront of it instead of running into and under it" —
	//         answered: we weren't actually clicking attack, we were
	//         tile-walking. Now we click-attack.
	//         (b) Dark-core attack now waits for the swing animation
	//         to commit (~1 game tick = 600ms) before stepping away.
	//         Pre-1.9.99.42 we waited only 350-470ms — that's BEFORE
	//         OSRS's tick boundary, so the step cancels the attack
	//         before it commits and the core takes 0 damage. With no
	//         damage the core doesn't despawn on its "jump back" and
	//         keeps draining HP. User: "how would we kill the cor ein
	//         1 hit? thats the intended thing anyway. if we kill it in
	//         1 hit and run away it dies while tryign to jump which
	//         permantly despawns it".
	// 1.9.99.43: Codex audit pass — five fixes.
	//         (a) handleUsingInitialSpecs now enqueues a PendingSpecAttempt
	//         instead of calling recordSpecUsed on energy-drain. The
	//         initial-specs path was the last spec-fire path bypassing
	//         the queue, counting MISSES as successes.
	//         (b) HIT_CONFIRM_TIMEOUT_MS: 5000 → 4200ms (7 game ticks).
	//         The 5s window extended past the spec swing's expected
	//         hit tick into the NEXT auto-attack — a missed spec
	//         followed by a normal hit could falsely confirm the
	//         missed spec via XP/hitsplat from the next swing.
	//         (c) tryActivateSpec debounce: the 1500ms unconditional
	//         trust block made the verification block (gated at
	//         SPEC_ACTIVATE_DEBOUNCE_MS=1200) unreachable. Now split
	//         into <600ms trust / 600-1200ms verify-only-no-reclick
	//         / >1200ms verify-and-reclick. Silent click failures
	//         surface within ~600ms instead of being masked for 1.5s.
	//         (d) processPendingSpecHit now runs BEFORE antiStompTick's
	//         continue, so a chain of stomp-step iterations can't
	//         expire a pending attempt's deadline without confirming.
	//         (e) Two-stage tile-walk + click-Attack approach (replaces
	//         1.9.99.42's straight-to-click-attack). Tile-walk to the
	//         picked cardinal with the 1.9.99.40 under-Corp watchdog,
	//         then click-Attack to finish on a melee-adjacent tile.
	//         Preserves the coordinator's claimed-offset spread for
	//         team play while still avoiding the under-Corp routing.
	//         User: "tile walk and attack click works if we have
	//         multiple bots".
	// 1.9.99.44: Codex second audit pass — two fixes.
	//         (a) FEROX_ONLY startup now routes to BANKING_AND_HEALING
	//         instead of PREPARING_RESTORATION_CYCLE. The 1.9.99.35
	//         startup HP/prayer/spec gate assumed POH access; for
	//         FEROX_ONLY users that meant looping in a POH state
	//         that couldn't find a friend's house. Codex audit.
	//         (b) Bank-withdraw failures now track consecutive
	//         strikes (INTERNAL_BANK_FAILURE_STRIKES = 4). After 4
	//         consecutive failures with no minimum-supply recovery
	//         we signalSessionEnd and stop — replacing the 1.9.88
	//         "TODO future fix" of looping indefinitely on a broken
	//         bank state. Codex audit.
	//         Deferred: friend/own house disambiguation (needs an
	//         entry-tracking flag — edge case), port-coordinator
	//         helper for teammatesNeedPoolRestoration / etc. (solo
	//         play doesn't hit it).
	// 1.9.99.45: Hitsplat one-shot consumption + start-in-house shortcut.
	//         (a) Codex audit: pre-1.9.99.45 the queue counted
	//         corp.getHitsplats().filter(isMine).count() fresh on every
	//         processPendingSpecHit call. Hitsplats live ~6 game ticks,
	//         so the same hit could confirm multiple attempts across
	//         calls. User log: two Elder maul specs both confirmed via
	//         hitsplat (own=1) with corpHP Δ=0.00% on both — only one
	//         hit actually landed, but both got credited. Fix:
	//         monotonicHitsplatCounter that only ever moves up (each
	//         new hitsplat adds 1, expirations don't decrement). Each
	//         PendingSpecAttempt snapshots the counter at enqueue;
	//         confirmation requires the counter to have advanced since.
	//         After a hit-confirmation we BUMP remaining attempts'
	//         baselines by 1 so the consumed hit can't re-confirm.
	//         Also added a 1200ms XP-suppression window after a
	//         hit-confirmation — the hit's delayed XP would otherwise
	//         confirm a younger pending attempt via the XP path. User
	//         (from Codex's draft): "handle delayed XP after a hitsplat
	//         confirmation: suppress/consume the next melee XP delta
	//         shortly after a hitsplat-confirmed spec so the same hit
	//         cannot confirm a younger pending attempt via XP".
	//         (b) handleStarting now routes through USING_ORNATE_POOL
	//         when the script starts INSIDE a POH with low resources.
	//         Skip the Games-necklace teleport entirely — drink pool
	//         + jewellery-box tele to Corp. User: "Also if we star tina
	//         house we can use the ornate pool -> jewelery box isntead
	//         of using an amulet. This insures we are topped off first".
	// 1.9.99.46: Start-in-house path now fires REGARDLESS of resources.
	//         At full HP/spec we route directly to TELEPORTING_BACK_TO_CORP
	//         (skip the no-op pool drink, just use the jewellery box).
	//         At low resources we still drink the pool first via
	//         USING_ORNATE_POOL. Either way the Games necklace stays
	//         in the pouch. User: "i started in house with full hp and
	//         full spec but it didnt use the ornate jelwlery box and
	//         used our jelwery instead".
	// 1.9.99.47: Mid-combat re-pot. Pre-1.9.99.47 the bot only drank a
	//         super combat potion when Inventory.isFull() — fine for
	//         lobby prep where banking just filled the inventory, but
	//         the boost wears off mid-kill and there was no re-trigger.
	//         New maybeReDrinkCombatPotion called from handleFightingCorp:
	//         if stats aren't boosted and we have ANY dose of the
	//         configured combat potion in inventory, drink one. 8s
	//         throttle. User: "we didnt repot when our boosted hp ran
	//         out". First successful Corp kill — major milestone.
	// 1.9.99.48: Vengeance trap from Codex audit + magic-drain fix.
	//         (a) updateHealthTracking now runs in the main loop before
	//         state dispatch — HP drops are detected in EVERY state,
	//         not only inside handleFightingCorp. Pre-1.9.99.48 the
	//         READY_FOR_FIRST_CAST -> ACTIVE_CASTING transition only
	//         fired when HP dropped inside handleVengeanceLogic, which
	//         itself only ran from handleFightingCorp — spec-dump and
	//         POH-cycle HP drops were invisible to the state machine.
	//         (b) handleVengeanceLogic now FORCE-promotes
	//         READY_FOR_FIRST_CAST -> ACTIVE_CASTING when the kill phase
	//         is reached AND Corp is alive. READY's own handler only
	//         casts when (!bossAlive || isInCorpLobby()) — neither true
	//         during a kill-phase fight — so the bot was stuck in READY
	//         forever waiting for a transition that couldn't happen.
	//         (c) canCastVengeance now checks Skill.MAGIC.getActualLevel
	//         instead of getCurrentLevel — Corp's magic attack can
	//         drain stats by 1, dropping current below 94 even on a 99
	//         magic account. ActualLevel is the XP-determined base.
	//         User: "we should also check if we have the correct magic
	//         level before casting 94 because corp magic attack can
	//         occasionalyl drain ur magic level by 1".
	//         (d) handleActiveCasting now logs WHY canCastVengeance
	//         blocked when the cooldown is ready (magic level, taken-
	//         damage flag, cooldown remaining). Replaces the silent
	//         Log.debug.
	// 1.9.99.49: handleTeleportingBackToCorp falls back to Games
	//         necklace if useOrnateJewelryBox fails. The 1.9.99.40
	//         box-retry already handles transient misses, but if all 3
	//         tries fail (e.g. starting inside a friend's POH where the
	//         box isn't on screen at land time) we previously bailed
	//         straight to EMERGENCY_ESCAPE → Ferox tele, even though
	//         a Games necklace was in inventory and would have worked.
	//         User log: "Failed to interact with jewellery box after 3
	//         attempts ... even though it has 1 games necklace".
	// 1.9.99.50: two spec-counting bugfixes.
	//         (a) advanceHitsplatCounter now filters by getValue() > 0
	//         so 0-damage MISS splats don't increment the monotonic
	//         counter. Pre-1.9.99.50 a missed Elder maul still showed
	//         up as a hitsplat (OSRS renders misses as the "0" splat)
	//         and falsely confirmed the pending attempt.
	//         (b) HIT_CONFIRM_TIMEOUT_MS: 4200ms → 3500ms. The 4200ms
	//         window still extended past the 3600ms next-swing tick for
	//         6-tick weapons (Elder maul / DWH / Arclight), allowing
	//         the next swing's hitsplat to confirm a missed spec.
	//         3500ms is just under the next-swing window — catches the
	//         actual spec swing but excludes the follow-up. User: "i
	//         think it counted an elder maul spec that missed as being
	//         a succesful spec ... our spec counts are getting all
	//         twisted".
	// 1.9.99.51: post-dark-core weapon restore. After killing/avoiding
	//         a dark core the bot used to call equipMainWeaponFast()
	//         which always swaps to Fang. Fine for the kill phase, but
	//         during an active spec-dump phase (e.g. Phase 3 BGS) the
	//         bot would auto-attack with Fang for 1-2 ticks before the
	//         main loop's shouldUseSpecialAttack noticed and re-equipped
	//         the phase spec weapon. Now: if pickSpecWeaponForCurrentPhase
	//         returns a weapon AND we have spec energy >= the minimum,
	//         equip THAT weapon (BGS / Arclight / whatever phase needs)
	//         instead of Fang. Only fall back to Fang if no usable spec
	//         weapon exists for the current phase. User: "at one point
	//         in the bgs phase it switched to another weapon, and
	//         started poking with eitehr the arclight or fang and
	//         eventually went back to the bgs ... maybe switchign to
	//         the elder maul when core spawne dbroke something?".
	// 1.9.99.52: harden BGS -> Fang transition at kill phase (Codex
	//         audit follow-up). Three additions inside handleFightingCorp:
	//         (a) KILL-PHASE-DIAG log fires once per second when
	//         isInKillPhase() AND a spec weapon is still equipped —
	//         dumps state, chosenSpec, bgsEquipped, specSwitchQueued,
	//         needsSwitchBack, Fang inv/equip status, availableMain,
	//         and how long the queue has been pending. Surfaces the
	//         exact reason the swap isn't completing.
	//         (b) Watchdog: if specWeaponSwitchQueued has been true
	//         for > 5s and isMainWeaponEquipped() is still false, force-
	//         call equipMainWeaponFast() and clear the queue. Replaces
	//         the implicit "wait forever for handleSpecWeaponSwitchTiming
	//         to retry" loop.
	//         (c) Fang-spec block now pre-equips Fang if the kill phase
	//         is reached and Fang is in inventory but not on. The old
	//         block required Equipment.contains("Osmumten's fang") so
	//         it silently no-op'd while BGS was still equipped.
	//         Bookkeeping: queueSpecWeaponSwitchBack stamps
	//         specWeaponSwitchQueuedAt for the watchdog.
	// 1.9.99.53: drift-recheck interval 3000ms → 500ms. Corp roams
	//         ~1 tile per 600ms game tick; the old 3s interval let Corp
	//         drift up to 5 tiles before the bot noticed, which is how
	//         we kept walking under Corp despite "safe" cardinal pick.
	//         antiStompTick at the top of the main loop is the last-
	//         resort safety, but it only fires AFTER we're already
	//         under Corp. Sub-tick drift checks let us reposition
	//         BEFORE Corp closes the gap. Cheap when Corp hasn't moved
	//         (no click); only clicks when we're actually out of the
	//         cardinal tolerance. User: "if we catch him moving before
	//         he does and update that in real time wouldnt that mean
	//         we would almost never walk under him?".
	// 1.9.99.54: dropped the LocalWalking.walkTo(bestPosition) step in
	//         moveToNearestCorpPosition. It was the only remaining path
	//         that could route through Corp's hitbox (the tile pathfinder
	//         routes through NPC tiles to reach a destination on the
	//         other side). The 1.9.99.43 watchdog caught it after the
	//         fact but the user wanted to PREVENT it entirely. Now we
	//         delegate the entire approach to attackCorpIfVisible:
	//         click-Attack on Corp when visible (game's attack pathfinder
	//         routes safely to an adjacent melee tile WITHOUT crossing),
	//         1-tile step toward Corp's tile when not visible (small
	//         enough that it can never cross the 5x5 hitbox). bestPosition
	//         is retained for the paint overlay only. For team coord,
	//         the game's attack pathfinder picks the closest unoccupied
	//         melee tile so bots naturally distribute. User: "if hes ons
	//         creen we click attack on him. if we dont see him we use
	//         the minimap to click in his direction".
	// 1.9.99.55: revert/refine. User: "did dyou just delete my L walking?
	//         How will bots get to the correct location thats on the far
	//         side of him?". Two corrections:
	//         (a) Restored LocalWalking.walkTo(bestPosition) for the team-
	//         coord far-side approach. The 1.9.99.40 walk-watchdog (polls
	//         isUnderCorp every ~80ms during the walk) is the right
	//         safety net for the occasional A* mistake — preventing
	//         every tile-walk was too aggressive. The 1.9.54 L-shape walk
	//         to corner waypoints already handles "line crosses Corp".
	//         (b) Bumped attackCorpIfVisible's off-screen step from 1
	//         tile to a CHUNK (up to 5 tiles toward Corp, capped so the
	//         destination stays at least 3 tiles from Corp.getTile()
	//         on the dominant axis — i.e. on the rim of the 5x5 hitbox,
	//         never inside). User: "our minimap shouldnt onyl walk 1
	//         tile towards it. What is our flow if corp isnt on screen?
	//         We wan tto change the distance we are walking towards
	//         corp so that we dont minimap click to walk and end upw
	//         alking into a tile where he is."
	// 1.9.99.56: chunked walk for the final approach in
	//         moveToNearestCorpPosition. Pre-1.9.99.56 a single big
	//         LocalWalking.walkTo(bestPosition) handed the SDK pathfinder
	//         freedom to route through Corp's hitbox if it saw a
	//         shortcut. New walkInChunksTo() takes 5-tile chunks toward
	//         the target; each chunk's destination is clamped to be
	//         OUTSIDE Corp's hitbox (it pulls back tile-by-tile if the
	//         5-tile point lands inside). Short clicks = pathfinder
	//         has no creative room to route through. Between chunks
	//         we re-check Corp's position and bail to stepOffCorp if
	//         we somehow ended up under. User: "use minimap walking to
	//         move towards the corp but make the location we walk
	//         there CLOSER To use ... making a simpleline path to get
	//         where we need to be like run straight 10 tiles, turn
	//         left 10 tiles after that".
	// 1.9.99.57: walkInChunksTo now uses an ADAPTIVE chunk size — up
	//         to 12 tiles per click when far away, scaling down to the
	//         remaining distance as we approach. 5-tile fixed chunks
	//         were too small for long walks. The per-axis pull-back
	//         when a chunk lands inside Corp's hitbox now prefers
	//         shrinking the larger-magnitude axis first so we route
	//         AROUND the hitbox rather than backing straight away.
	//         Also clarified to user: the minimap dots they see (Corp
	//         yellow, player white) are just a visual of the same game
	//         state we already query via Query.npcs().getTile() — no
	//         SDK pixel-reading needed to know where Corp is. User:
	//         "we dont only want to walk 5 tiles forward at a time.
	//         is there not a way to read whats on the minimap ...
	//         estimate the direction corp is in but not walk
	//         completelyt over it".
	// 1.9.99.58: walkInChunksTo respects a 4-tile BUFFER outside Corp's
	//         hitbox. Chunk destinations must be > 4 tiles from the
	//         hitbox edge — anywhere closer is the "danger zone" where
	//         Corp roaming 1-2 tiles per tick could land on us before
	//         we react. Once the player IS within the buffer, the
	//         function returns true and the caller (moveToNearestCorpPosition)
	//         takes over with attackCorpIfVisible — the game's attack
	//         pathfinder routes safely from short range. User: "we
	//         wouldnt ever want to click to close to the corp ... we
	//         would want to walk idk maybe 3-7 tiles outside of its
	//         hitbox. any closer and thats setting us up into a zone
	//         we could get walked on".
	// 1.9.99.59: two fixes from the BGS-phase test run.
	//         (a) Bot stood AFK when dark core was visible but distant
	//         (focusing teammate). The non-approaching-core branch in
	//         handleAdvancedDarkCoreModern returned without attacking
	//         anything, and auto-retaliate is OFF (1.9.64) during core
	//         handling, so the bot froze with whatever was equipped
	//         (BGS, in user's case). Now we keep clicking Corp via
	//         attackCorpIfVisible when the core is distant — DPS
	//         continues while the partner tanks the core. User: "we
	//         were getting stuck just afking with the bgs out ... while
	//         the core was out and focusing the real player while we
	//         sat tehre and did nothing".
	//         (b) Paint overlay now appends BGS damage progress for
	//         the BGS entry: "Bandos godsword=3 (~143/200 dmg)". BGS
	//         phase is gated on damage drained (INTERNAL_PHASE3_BGS_DAMAGE
	//         = 200), not spec count — the logic was already correct
	//         (bgsDamageDealt sums hitsplat values), but the overlay
	//         only showed the count which was misleading. User: "for
	//         the bandos godsword phase we are tracking damage dealt
	//         vs specs hit ... is that properly set up right?".
	// 1.9.99.60: BGS damage credit defaults to +30 when hitsplat value
	//         is 0, not when it's negative. Pre-1.9.99.60 the check
	//         `>= 0` accepted 0 as a literal "zero damage spec" — but
	//         0 came from getMyLargestRecentHitOnCorp returning 0
	//         (hitsplat aged out, or queue confirmed via XP-only with
	//         no hitsplat present). Phase 3 target is 200 (100
	//         effective in duo); at 0 per spec, Phase 3 was never
	//         reachable. Now the check is `> 0`, so 0 falls to the
	//         +30 default. Also added a per-spec log: "BGS damage
	//         credited: +N (actual hitsplat=X, total=Y/200)" so you
	//         can see progress in the log directly. User: "stuck on
	//         the bgs forever ... i think that mostly stimmed around
	//         the core issues because on a previous kill it didnt
	//         have that issue".
	// 1.9.99.63: two timing fixes from user log.
	//         (a) walkInChunksTo's inner-loop wait was satisfied
	//         immediately at t=0 when chunk was only 1 tile (the wait
	//         condition `distance <= 1` is true at the start because
	//         we start 1 tile from the destination). Outer loop then
	//         spun without ever actually waiting for the walk to
	//         complete. User log showed "chunk 1 tiles to (2989, 4385)"
	//         repeated 5 times in 1 second from inside a single
	//         walkInChunksTo call. Now: wait for player to either
	//         REACH chunkDest exactly OR move AT ALL from the start
	//         tile (covers off-by-1 pathfinder landings). Pure
	//         distance-based check replaced with movement detection.
	//         (b) Dark-core attack-then-step now waits for the swing
	//         to LAND (XP delta > 0 OR animation end), not just start.
	//         Pre-1.9.99.63 we stepped at animation-start + 150ms,
	//         which is ~3 seconds BEFORE an Elder maul swing actually
	//         hits (6-tick animation). Stepping cancels the swing,
	//         core takes 0 damage. User: "sometimes it would run away
	//         from the core before the animation went off. Maybe we
	//         send the running input after we get an xp drop?". Yes
	//         — XP drop is now the gate.
	// 1.9.99.62: throttled the per-tick "Dark core not visible / close /
	//         distant" debug logs to one line per second per call site.
	//         Pre-1.9.99.62 these fired every main-loop iteration (~10+
	//         per second) and buried useful log lines around core events.
	//         New field lastDarkCoreLogAt tracks the last debug emit; if
	//         within 1000ms we skip the log but still take the action.
	//         User: "its hard to grab logs revolvign the dark core
	//         because it spams so many per second it nukes the majority
	//         of our log".
	// 1.9.99.61: three fixes from the spinning-with-BGS log report.
	//         (a) Drift recheck skip: if isPlayerAttackingCorp OR we're
	//         within the buffer zone (edgeDist <= 5), don't re-fire
	//         moveToNearestCorpPosition. Pre-1.9.99.61 the drift
	//         recheck at 500ms intervals kept calling
	//         moveToNearestCorpPosition while click-attack's auto-walk
	//         was in flight, spamming walkInChunksTo with no-op chunks.
	//         User: "we are getting hit with this ... walkInChunksTo:
	//         chunk 1 tiles to (2976, 4379) [repeated 4x] ... just
	//         kind if idle around with our spec weapon out".
	//         (b) Paint shows the EFFECTIVE BGS damage target. In duo
	//         with the 2x multiplier, bot's effective contribution
	//         target is 100, not the raw INTERNAL_PHASE3_BGS_DAMAGE=200.
	//         Now: "Bandos godsword=3 (~120/100 dmg, duo 1p multiplier)".
	//         User: "my bgs damage even though i hit multiple times
	//         kept showing 120/200. but... dont we not need 200 since
	//         we are solo duoing?".
	//         (c) Spec credit weapon mismatch warning. processPendingSpecHit
	//         now cross-checks Equipment.contains against attempt.weapon
	//         at confirm time. If a swap happened mid-flight (e.g.
	//         phase rotation between fire and confirm), log a warning
	//         so the user can see WHICH weapon actually fired. User:
	//         "is tehre a way to check if we actually have the correct
	//         spec weapon equiped when we count a spec progression?".
	//         Deferred to a later iteration: face-tank-after-attack on
	//         dark core (need a log capture to see whether stepAwayFromCore
	//         is failing or being re-triggered), one-shot detection
	//         using core.getHealthBarPercent.
	// 1.9.99.72: death-spiral fixes from the 19:39 log.
	//         (a) Panic retreat on consecutive emergency eats.
	//             handleHealthAndPrayer now records lastEmergencyEatAt;
	//             if a second emergency eat fires within 2s, the bot
	//             eats THEN steps 5+ tiles off Corp's hitbox center
	//             via the new panicRetreatFromCorp(). Sets
	//             panicRetreatActiveUntil so handleFightingCorp won't
	//             re-engage for ~2.5s (HP regen / veng catches up).
	//             Pre-1.9.99.72 the bot stood-and-ate through Corp's
	//             burst damage, ran the karambwans dry, then died.
	//         (b) Vengeance gates on CURRENT (live, drained) magic level.
	//             canCastVengeance was using Skill.MAGIC.getActualLevel()
	//             — the BASE level from XP, which never drops. Corp's
	//             magic attack drains Magic by 1; on a 94-mage account
	//             that drops live level to 93 and the cast silently
	//             fails. User: "It did try to veng but its stats were
	//             lowered temporarily. We need to check if we have 94
	//             magic before we try."
	//         (c) VENG-GATE log shows both magicCurrent + magicBase,
	//             and lastCastAgoMs prints "never" instead of the giant
	//             Unix timestamp when no successful cast has happened.
	//             Pre-1.9.99.72 the diag printed nowMs - 0 = 1.78e12 ms.
	//         (d) Karambwan-low pre-emptive bank trip. If karambwan
	//             count drops to 2 and we're NOT in kill phase, the
	//             fight handler routes to BANKING_AND_HEALING. Pre-
	//             1.9.99.72 the bot burned the last karams in combat,
	//             then fell back to Shark-only (half heal rate) and
	//             died. User: "during the last phase we probably dont
	//             need to panic eat." — so the gate skips when
	//             isInKillPhase() is true.
	//         (e) Demoted isStatsBoosted's per-tick "Stat check" INFO
	//             log to DEBUG. Pre-1.9.99.72 every loop iteration
	//             logged the same boost values, drowning out actual
	//             diagnostics in the 19:39 log.
	//         (f) panicRetreatFromCorp hardened: removed backward
	//             retreat offsets (they reduced distance from Corp on
	//             one axis), bumped buffer to match stepAwayFromCore's
	//             CORP_BUFFER=3 (so destinations are 4+ tiles past
	//             hitbox edge — Corp can't pace-move into them), and
	//             added a defense-in-depth check that the destination
	//             is never closer to Corp on either axis than where
	//             we already stand. User: "we need to make sure the
	//             panic retreat doesnt actually retreat into his hitbox."
	// 1.9.99.73: two fixes from the 20:32 log where the bot finished a
	//         POH restoration, walked back to Corp with spec OFF, hit
	//         with a normal Elder maul swing, then bailed to bank.
	//         (a) Karam-low check moved from handleFightingCorp to
	//             handleEnteringCombat. The 1.9.99.72-d gate fired
	//             mid-kill — after the walk-in, after the first eat,
	//             after the first swing — wasting an entire trip from
	//             POH. Now the check runs BEFORE the walk to Corp
	//             kicks off; if karams are low and we're not in kill
	//             phase, divert to BANKING_AND_HEALING straight from
	//             the lobby. Once in FIGHTING_CORP, supply gates no
	//             longer abort a kill mid-flight — emergency-eat /
	//             panic-tele handle the survival side.
	//         (b) Stage B (lobby) spec pre-activation is now
	//             deterministic. The 50/50 roll in
	//             maybePreActivateSpecStageB meant ~half of all trips
	//             walked to Corp with spec OFF; if HP also happened
	//             to be below the spec-prep gate at arrival (50),
	//             prepareSpecWeaponForCorp skipped activation AGAIN
	//             and the first swing landed with no spec. Stage A
	//             (pool) keeps its 50/50 — Stage B now ALWAYS
	//             activates when stage A didn't and spec energy is
	//             at/above the floor. User: "we ran all the way to
	//             the corp without having our spec on and smacked him
	//             with a normal elder maul hit; i feel like this
	//             should never happen."
	// 1.9.99.74: vengeance reliability + overlay-live + panic tuning.
	//         (a) handleVengeanceLogic() lifted out of handleFightingCorp
	//             and into the main loop after handleHealthAndPrayer.
	//             Previously veng only ticked during FIGHTING_CORP state
	//             — during spec-dump phases (USING_SPECIAL_ATTACK),
	//             weapon swaps, and any early return from
	//             handleFightingCorp the veng tick was dropped. Net
	//             effect from the 20:53 log: zero mid-fight casts;
	//             the only cast fired from the boss-death branch.
	//             Now it ticks every loop iteration; the post-eat
	//             ordering ensures emergency HP wins the tick if both
	//             handlers want to fire.
	//         (b) HP gate inside handleVengeanceLogic. If HP is at or
	//             below INTERNAL_COMBO_EAT_HP (50), defer the cast —
	//             the eat handler is about to fire a combo eat and we
	//             don't want the veng widget click stealing the same
	//             tick. User: "this may have us try to vengenance when
	//             we are low hp and need to eat."
	//         (c) Explicit state gate inside handleVengeanceLogic to
	//             block POH/lobby/banking/teleport/death-recovery/
	//             emergency-escape/starting states. Redundant with
	//             isInKillPhase() in many cases but covers the gap
	//             where we're in a transition state but isInKillPhase
	//             happens to return true (e.g. Corp HP visible but
	//             we just teleported out).
	//         (d) Cardinal-tile overlay recomputes LIVE every paint
	//             tick from Corp's current position. Pre-1.9.99.74 the
	//             4 cardinals + cross flags were only refreshed when
	//             the positioning code recomputed (gated on drift,
	//             ~500ms minimum) — Corp moved but the cross overlay
	//             lagged. User: "our cross detections still don't
	//             update in real time."
	//         (e) Panic retreat now fires on the FIRST emergency eat
	//             instead of requiring two within 2s. Spec dumps are
	//             short; one panic eat = something already failed,
	//             so retreat instead of waiting for a second one.
	//             User: "we could probably change the panic eat
	//             requirement down to just 1 panic eat."
	//         (f) Emergency combo eat with karams=0 → insta-tele to
	//             EMERGENCY_ESCAPE instead of eating sharks alone.
	//             Shark-only (~20 HP) can't keep up with Corp's
	//             burst; better to bail with Ring of Dueling than
	//             chew through sharks while dying. User: "if we
	//             need to combo eat and we're out of kawambwans we
	//             can just insta tele."
	//         (g) needsPoolRestoration threshold: HP < 90% OR
	//             prayer < 70% (was: any drop below max). Pre-
	//             1.9.99.74 the bot drank the Ferox pool right after
	//             a POH restore because prayer drained 5-10 points
	//             during the walk to bank — wasted animation lock.
	//             User: "since the prayer stayed on we also restored
	//             it again when we banked."
	// 1.9.99.75: vengeance diagnostics added to the in-client paint
	//         overlay. New block shows: state, casts this kill,
	//         casts this session, time since last cast, cooldown
	//         remaining, magic level (current/base + DRAINED flag),
	//         rune-pouch detection, tookDamageSinceLastVeng,
	//         isInKillPhase, and the last gate-block reason
	//         (HP<50, state=POH, magic drained, no runes, etc.).
	//         vengCastsThisKill resets in coordinatorOnKillEnded and
	//         resetPerKillStateAfterAbort. User: "can we add all of
	//         our vengeance info to the paint? maybe that will help
	//         us debug better."
	// 1.9.99.76: short-range click-attack early-out in
	//         moveToNearestCorpPosition. When Corp is visible AND we
	//         are within 12 tiles of the picked safe position, skip
	//         the chunked walk entirely and fire corp.interact("Attack")
	//         — the game's NPC attack pathfinder handles the last few
	//         tiles via screen-tile walks (not minimap clicks). Pre-
	//         1.9.99.76 every walk-in went through walkInChunksTo →
	//         LocalWalking.walkTo, which sometimes routed the click
	//         via the minimap even for short hops. User: "even if corp
	//         is on screen we will still sometimes attempt to minimap
	//         walk even if its just a short distance but if we can
	//         already see it that should never happen."
	// 1.9.99.77: vengeance — drop the strict magic-level gate, add
	//         failed-attempt throttle + attempt counter on the overlay.
	//         (a) canCastVengeance no longer returns false when magic
	//             is drained. The 1.9.99.72 gate blocked all casts for
	//             the ~60s drain recovery window — measured in the
	//             20:53 screenshot as the SINGLE biggest reason veng
	//             cast count stayed at 0 per kill. Now castVengeance
	//             tries anyway; if magic is drained the game refuses
	//             (no rune consumption), XP-delta check fails,
	//             castVengeance returns false.
	//         (b) After a failed cast (no XP), block retries for 5s
	//             via VENG_FAILED_RETRY_THROTTLE_MS. Prevents widget-
	//             click spam while waiting for magic drain to recover.
	//         (c) New paint fields: vengAttempts (every castVengeance
	//             call, succ or fail) and lastAttempt (time since most
	//             recent attempt). "attempts climbing, casts stuck at
	//             0" tells you the click is firing but the spell is
	//             being refused. The vengLastGateReason now also
	//             captures "click fired but magic X/94 — spell refused"
	//             and "click fired but no XP — widget miss?" so the
	//             overlay shows post-click failure modes too.
	//         User: "B" + screenshot showing magic 94/94, runes ok,
	//         killPhase yes, but lastCast: never.
	// 1.9.99.78: revert 1.9.99.77 option B + walk visibility poll.
	//         (a) Restored the strict magic-level gate in
	//             canCastVengeance. User: "keep the magic level try.
	//             trying to click a spell that we clearly cant cast is
	//             obvious of a bot." When drained, hold off rather than
	//             click-and-fail. Drain recovers ~1 level/minute.
	//         (b) walkInChunksTo's inner wait loop now polls for Corp
	//             visibility every iteration. If Corp comes into render
	//             mid-walk, return immediately so the caller's
	//             attackCorpIfVisible can engage via game pathfinder.
	//             Pre-1.9.99.78 walkInChunksTo only broke on arrival
	//             or 1200ms no-movement — covered the "Corp visible at
	//             start" case (via 1.9.99.76 short-range early-out) and
	//             the "Corp invisible whole walk" case (via
	//             walkToPositionWithCorpCheck) but not the "becomes
	//             visible mid-walk" case. User: "do we have the walking
	//             thing set to exit out of it and attack early if corp
	//             appears on screen?"
	// 1.9.99.79: spec bar toggle-off race fix + house portal type-delay.
	//         (a) handleFightingCorp's PRE-ACTIVATING block now skips
	//             if we clicked the spec bar in the last 1500ms AND
	//             spec energy hasn't dropped. Pre-1.9.99.79 the gate
	//             fired solely on Combat.isSpecialAttackEnabled() — but
	//             that probe lags the actual game state by ~1 tick
	//             after a click, so the gate read FALSE on the lag
	//             tick and re-clicked the bar, toggling it OFF. The
	//             00:40:56 log: lobby pre-activate at :55, then 5
	//             consecutive "PRE-ACTIVATING / Failed to activate as
	//             backup" chains over 2 seconds before the bar finally
	//             settled. Energy-drop check distinguishes "spec swung
	//             legitimately" (energy dropped, re-activate is
	//             correct) from "SDK lag race" (energy unchanged,
	//             skip). User: "we enabled spec and then disabled and
	//             enabled and disabled when we could have just left it
	//             enabled."
	//         (b) Friend's-house dialog shortcut wait dropped 8s → 1.2s.
	//             The wait gated on the "Last name: <host>" shortcut
	//             widget rendering, which is usually <1 tick but
	//             occasionally took 5s+. Now if it doesn't render in
	//             ~2 ticks, fall straight to typing. Post-settle wait
	//             also tightened (~120ms → ~60ms). Net effect: house
	//             entry is 4-5s faster on slow shortcut renders.
	//             User: "we do correctly enter players names on the
	//             house portal; but it seems like it has a long
	//             timeout of maybe 5 or so seconds before it types
	//             even though the correct interface is open."
	// 1.9.99.80: walkInChunksTo visibility-bail progress guard.
	//         The 1.9.99.78 visibility poll bailed instantly when Corp
	//         was already in Query results at walk start — even from
	//         14-17 tiles away. The 00:53:57 log showed the bot stuck
	//         at (2970, 4382) for several seconds, drift-recheck firing
	//         every 500ms, the chunked walk command issued but the
	//         poll returning true before LocalWalking could actually
	//         move the player. Caller's attackCorpIfVisible then fired
	//         a second click that didn't route, and the cycle repeated.
	//         Fix: require 3+ tiles of player movement since walk-start
	//         before the visibility poll is allowed to short-circuit.
	//         By the time we've moved 3 tiles, the game pathfinder has
	//         committed to the walk and a mid-walk "Corp now visible"
	//         is genuine — bail to click-attack is safe. User: "we got
	//         stuck in a phase where the script thinks we are fighting
	//         corp but we havnt actually entered the cave; mid kill
	//         after restoring spec."
	// 1.9.99.81: three fixes.
	//         (a) Reverted 1.9.99.79's house-portal shortcut wait
	//             timeout reduction (1.2s → back to 8s). The shorter
	//             timeout was firing typing fallback before the dialog
	//             input field was keyboard-focused; keystrokes landed
	//             in public chat or stale buffers, producing wrong
	//             names. Original 8s usually returns in ~1 tick on
	//             fast renders; the 5s worst-case delay is preferable
	//             to mistyping. User: "the previous version was better."
	//         (b) Karam-low check in handleEnteringCombat now ALSO
	//             gates on currentRestorationCycle == 0. Pre-1.9.99.81
	//             every POH-then-return-to-Corp re-entered ENTERING_COMBAT
	//             and re-checked karam count — if we'd eaten karams
	//             during the previous fight portion, the next re-entry
	//             saw karams <= 2 and bailed to bank, wasting the
	//             specs already invested in this kill. Now: only bail
	//             on first engagement of a trip-from-bank. Once we've
	//             done any POH cycle, we're committed to finishing.
	//             User: "i am also noticing during the spec phase we
	//             are still banking if we have 2 karmbwans which i
	//             thought we changed."
	//         (c) handleActiveCasting: dropped the bossAlive +
	//             bossLowHealth gates and the boss-death-branch.
	//             Pre-1.9.99.81 the alive-branch required Corp to be
	//             in render AND Corp HP > 85; both silently no-op'd
	//             whenever the conditions weren't met. The
	//             java_Pm7TDAwTST.png screenshot showed state=
	//             ACTIVE_CASTING, killPhase=yes, magic=94/94, runes=ok,
	//             tookDmg=yes, cd=ready — every visible gate green —
	//             but attempts=0 because the bossAlive/bossLowHealth
	//             gate was failing silently. Veng is a self-buff that
	//             reflects the NEXT damage; it doesn't require Corp to
	//             be visible right this tick. Now: kill-phase +
	//             active-combat state + canCastVengeance is enough.
	//             User: "we just straight up dont veng and i cant
	//             figure out why."
	// 1.9.99.82: dropped the proactive karam-low pre-engagement check
	//         entirely. The reactive insta-tele in handleHealthAndPrayer
	//         (1.9.99.74-f) already handles the only case that matters:
	//         if we hit emergency-eat threshold AND karams == 0, tele
	//         straight to Ferox via Ring of Dueling. Proactive banking
	//         at 2 karams was wasting trips for situations that
	//         might never have actually required a combo eat. User:
	//         "If we need to get food its fine. We can just make it so
	//         if we run OUT of emergency eat combo foods and require
	//         it we force insta teleport to ferox to rebank. currently
	//         we are rebanking if we get to 2 karmawans left."
	// 1.9.99.106: trip-plan randomization for spec / pot / weapon swap.
	//         (a) Spec pre-activation: Stage A roll dropped from 1/2 to
	//             1/3. New Stage A.5 added — fires after the ornate
	//             jewellery box click (overlaps the tele animation,
	//             looks like natural multi-tasking instead of a fixed
	//             pool-side ritual). Stage A.5 takes 1/2 of remaining
	//             (= 1/3 overall). Stage B (lobby) still forced when
	//             A and A.5 didn't activate. Stage C unchanged.
	//             Distribution per trip: ~1/3 pool, ~1/3 post-jewellery
	//             box, ~1/3 lobby. User: "currently after hitting the
	//             spec pool; if we decide to enable spec while still
	//             in the house, every single time 100% of the time we
	//             enable it while still at the pool and then click on
	//             the jewellery box."
	//         (b) Combat potion drink: new per-trip plan with even
	//             3-way split: HOUSE_POST_POOL / LOBBY / BOSS_ROOM_WALK.
	//             Roll fired at trip start (alongside spec rolls). Each
	//             stage helper (maybeDrinkCombatPotAtHouse/InLobby) only
	//             fires if (a) plan matches AND (b) stats not already
	//             boosted. Boss-room fallback unchanged. User: "we can
	//             drink it in the house, in the lobby, or as we walk
	//             into the boss room."
	//         (c) Spec weapon swap: new per-trip plan with weighted
	//             choice — 70% LOBBY, 30% BOSS_ROOM. Lobby swap saves
	//             the 0.6s boss-room delay before first swing. Stage B
	//             skipped when swap deferred to boss room (avoids the
	//             swap-toggles-bar-OFF waste). User: "should add that
	//             we can also switch and have a higher randomized
	//             likelihood to do so in the lobby."
	// 1.9.99.105: sustained 15s timeout drops the engagement gate.
	//         User debug log: "peakHP=0.0% lastHP=100.0% missing=19979ms"
	//         — Corp NPC missing 20s in the boss room, but neither
	//         engagement signal triggered (peak=0 because
	//         isHealthBarVisible() returned false the entire fight;
	//         lastHP=100 is the default initial value, never updated).
	//         LOOTING was permanently blocked despite Corp clearly
	//         dead. 15s of NPC absence + in boss room IS the death
	//         signal — engagement details are downstream of detection
	//         and shouldn't gate it. Fast (3s) path keeps the
	//         engagement gate for false-positive safety. User: "i
	//         caint even read the dbeugs for corp deaths cuz the
	//         entire chat spams this 100x times."
	// 1.9.99.104: kill-phase shark-only allowed. The 1.9.99.74-f
	//         karam=0 insta-tele fired unconditionally, which is
	//         right during spec-dump phase (Corp hits hard, shark
	//         alone can't keep up) but wrong in kill phase (Corp
	//         debuffed, ~20 HP shark heal is plenty per attack).
	//         Now: only tele on karam=0 if NOT in kill phase OR if
	//         sharks also < 3. In kill phase with sharks >= 3, eat
	//         shark and continue fighting. User: "after weve finished
	//         dumping specs corp is so weak we wont ever need to
	//         combo eat again. so just having sharks is good enough
	//         and we keep banking in the last phase. because we are
	//         out of combo eats."
	// 1.9.99.103: Added PREPARING_RESTORATION_CYCLE to the
	//         handleHealthAndPrayer skip list. The bot routinely ate
	//         a karambwan right before teleing to POH because
	//         eatHealthThreshold (HP < ~78) fires during this brief
	//         prep window; the eat is wasted since the ornate pool
	//         restores HP to full a few seconds later. Same idea as
	//         the 1.9.99.86 LOOTING skip. User log: "Ate Karambwan
	//         (normal)" at 09:56:07 followed immediately by
	//         "Teleporting to house" at 09:56:08.
	// 1.9.99.102: POH-first emergency escape when supplies aren't
	//         critical. handleEmergencyEscape now checks if food
	//         (sharks >= 5, karams >= 5), pots (combat + super
	//         restore each >= 1 dose), and a house tab are all
	//         available before defaulting to Ferox + bank. If so,
	//         we skip the bank trip and route directly to
	//         TELEPORTING_TO_HOUSE — the existing POH restoration
	//         chain (house tab → ornate pool → ornate jewellery
	//         box back to Corp) handles the rest. Saves the
	//         3-5 minute bank trip when supplies didn't need
	//         restocking. Falls through to Ferox+bank when supplies
	//         are below threshold OR pohSource is FEROX_ONLY. User:
	//         "it doesnt hurt to check if supplies arnt critical
	//         and then doing poh. theres rare occasions where we
	//         get combod from 50+30 at the start and panic tele
	//         out when we could just poh ornate pool."
	// 1.9.99.101: two fixes for "we NEVER transition to LOOTING".
	//         User screenshot showed peak=1%, lastHP=1%, missing=21754ms
	//         (Corp dead 21+ seconds), inBossRoom=yes — every signal
	//         pointing at "Corp died" but the bot stayed in FIGHTING_CORP.
	//         (a) Same-tick HP=0 detection threshold tightened from
	//             `<= 1.0` to `<= 0.0`. Corp's HP bar reading 1.0 means
	//             ~20 HP remaining (alive but dying), NOT dead. Pre-
	//             1.9.99.101 we'd transition to LOOTING at 1%, run
	//             handleLooting (resets peakHP=0), then bounce back to
	//             combat with peak rebuilding from a dying Corp's 1%
	//             observation. With this tightened, only HP truly at
	//             0% triggers same-tick LOOTING — the timeout paths
	//             (3s fast, 15s sustained) catch Corp's actual death
	//             via NPC despawn.
	//         (b) Relaxed engaged-this-kill gate for the timeout path:
	//             accepts `lowLastHp = lastObservedCorpHpPercent > 0 &&
	//             < 30` as alternate proof of engagement. The original
	//             `peakHP > 5%` check protects against the freshly-
	//             engaged-bar-reads-0 false positive — but it also
	//             blocks legitimate deaths when peak got reset to a
	//             low value mid-fight (state oscillation, HP bar
	//             visibility flicker, etc.). A bar reading of 1-29%
	//             only occurs AFTER damage, so observing it IS proof
	//             of engagement. Either signal qualifies. User:
	//             "they are the size that you made them. They both
	//             show 1% but those values are absolutely incorrect."
	// 1.9.99.100: prepareSpecWeaponForCorp's eat-gate now uses
	//         settings.specDumpPanicTeleHp instead of the hardcoded
	//         INTERNAL_COMBO_EAT_HP (50). Pre-1.9.99.100 the function
	//         combo-ate at HP <= 50 every time Corp became visible —
	//         even when the bot was supposed to be mid spec dump and
	//         the user's setting said "don't eat above 35". Same
	//         threshold now applies to entering-combat-prep as the
	//         mid-spec-dump panic-tele gate. User: "it seems like we
	//         are still eating when above 35 health when specing
	//         down; specifically with the arclight but i dobut its
	//         weapon specific."
	// 1.9.99.99: coordinator-confirm sanity log. Dedicated WARN line
	//         when the team kill_id advanced past our local — surfaces
	//         the kill_id drift (team - local) so we can verify the
	//         cross-bot flow once we test with teammates. Drift > 1
	//         means we missed multiple kills (extended bank trip);
	//         flagged in the log so it's obvious. The script doesn't
	//         currently run with a coordinator, but the log will fire
	//         the first time it does. User: "yes add the sanity log
	//         even though we arnt running with a cordinator yet."
	// 1.9.99.98: corp-death detection diagnostics added to the paint
	//         overlay. Mirrors the gate logic in handleFightingCorp's
	//         missing-Corp else branch so the user can see at-a-glance
	//         which gate is blocking the LOOTING transition when state
	//         is stuck at FIGHTING_CORP after a kill. New block shows
	//         inBossRoom, peakHP%, lastHP%, missing duration, fast/
	//         sustained timeout readiness, local + team kill IDs, and
	//         whether a teammate has confirmed the kill. Box height
	//         bumped from 20 lines to 27. User: "can you add all these
	//         required things to the paint in case the debug blows
	//         through it after the kill so we can tell what actually
	//         happening?"
	// 1.9.99.97: drift recheck rewritten to fire ONLY on the four
	//         trigger scenarios the user identified:
	//           (1) ENTERING_COMBAT → FIGHTING_CORP (just walked in)
	//           (2) HANDLING_DARK_CORE → FIGHTING_CORP (just killed core)
	//           (3) Panic-retreat end (just walked 5 tiles off, need to re-find slot)
	//           (4) POH/banking return (covered by trigger 1 via ENTERING_COMBAT)
	//         New needsRepositioning flag defaults to true (first entry
	//         needs positioning) and flips false after a successful
	//         reposition OR after isInGoodCorpPosition returns true.
	//         Mid-fight transitions (USING_SPECIAL_ATTACK ↔ FIGHTING_CORP
	//         between specs) do NOT re-arm the flag. Removed the
	//         1.9.99.96 dual-interaction check (alreadyAttackingCorp ||
	//         corpInteractingWithMe) — redundant now that the entire
	//         drift block is gated mid-fight. antiStompTick at the top
	//         of the main loop still handles real under-Corp situations
	//         via corpArea.contains(myPos), so this aggressive gate is
	//         safe. To revert: remove the `needsRepositioning` gate at
	//         the drift recheck site and the four flag-set lines.
	//         User: "Most if not all occasions mid fight should not
	//         have any issues. Can we try changing our drift check
	//         code to only account for those scenearios and leave a
	//         note so if it doesnt work we can revert that?"
	// 1.9.99.96: two fixes from the post-kill review.
	//         (a) HP-jump respawn detection. The 1.9.99.94 absence-
	//             window check (Corp NPC missing > 1s + reappear at
	//             full HP) didn't fire when Corp's NPC stayed present
	//             continuously through death animation + respawn —
	//             same NPC slot, just animation transition. Now we
	//             also detect respawn via HP jump: if the most-recent
	//             observed Corp HP was low (< 30%) and current HP is
	//             high (> 80%), a death-and-respawn happened. Route
	//             to LOOTING. Fires before the existing absence check
	//             so log lines distinguish the two paths (RESPAWN
	//             (HP-jump) vs RESPAWN (absence)). User: "when corp
	//             dies our state stays as FIGHTING_CORP until he
	//             respawns and then we go straight to attacking him,
	//             no banking between kills, no reset."
	//         (b) Drift recheck skip now uses BOTH directions of
	//             interaction. Pre-1.9.99.96 the skip relied on
	//             isPlayerAttackingCorp(corp) alone — which can read
	//             false briefly between swings. Added
	//             corp.isInteractingWithMe() as a parallel signal so
	//             Corp targeting us (even between our own swings)
	//             also blocks the false-positive minimap reposition.
	//             antiStompTick at the top of the main loop handles
	//             real under-Corp situations, so this drift-skip is
	//             safe to aggregate-OR. User: "WE might hbe able to
	//             just use an interacting check? Is there every any
	//             way we would be interacting with him where we
	//             would get stomped?"
	// 1.9.99.95: handleLooting() now waits up to 6 seconds for
	//         valuable loot to appear on the ground before scanning.
	//         Pre-1.9.99.95 we transitioned to LOOTING the instant Corp
	//         died (or via the 1.9.99.91 timeout), ran the pickup loop
	//         once against an empty groundItems snapshot, and moved on
	//         without anything. Corp's death anim is ~3s + server tick
	//         lag means loot can take 4-5s to spawn. The wait early-
	//         exits as soon as ONE valuable item appears so the
	//         already-there common case adds no latency. User: "Loot
	//         handling should probably take around 5-7 seconds because
	//         its death animation is slow."
	// 1.9.99.94: Corp respawn detection. Closes the gap where the
	//         15-second sustained-absence timeout was about to fire,
	//         but Corp respawned (e.g. at 13s) and the bot just
	//         engaged the new Corp without ever transitioning to
	//         LOOTING — missing the prior kill's drops AND skipping
	//         banking. Now: on each tick where Corp NPC is visible,
	//         we snapshot corpMissingSinceMs BEFORE resetting it. If
	//         (snapshot indicates Corp was absent > 1s) AND (current
	//         HP > 95%) AND (we engaged the prior kill), it's a
	//         respawn → route to LOOTING before touching the new
	//         Corp. corpSeenAtZeroHp set so handleLooting runs cleanly.
	//         User: "what happens if we go into the corp room and we
	//         are in the last phase waiting for the 15 seconds and it
	//         respawns at 13 seconds; what is our flow then?"
	// 1.9.99.93: coordinator-confirmed death signal added. Short-
	//         circuits the timeout when the team kill_id (max across
	//         all bots' published localKillId) has advanced past our
	//         localKillId — meaning a teammate completed the kill we
	//         were both fighting. Same in-boss-room + engaged-this-kill
	//         gates still apply so a stale teamKillId from before we
	//         joined doesn't trigger a false LOOTING. New helper
	//         coordinatorTeamKillId() reads from the port coordinator
	//         (in-memory, real-time) with file-coordinator fallback.
	//         User: "we need to either receive the info that he died
	//         from the cordinator if one exists; or be in the actual
	//         boss room and he doesnt exist/died."
	// 1.9.99.92: Corp-death timeout fallback hardened. The 1.9.99.91
	//         3-second-missing trigger could false-positive if Corp
	//         roamed to a part of the room the SDK Query couldn't see,
	//         or if the player teleported out (boss room chunk unloads,
	//         Corp drops out of the scene). Now requires THREE gates:
	//         (a) isInCorpBossRoom() — we're physically in the room so
	//             the scene query is authoritative.
	//         (b) maxCorpHpPercentThisKill > 5% — we engaged.
	//         (c) Either (lastObservedCorpHpPercent < 30% AND missing
	//             > 3s) — Corp was dying, fast path; or sustained
	//             absence > 15s — Corp's been gone way too long to be
	//             merely roaming.
	//         New field lastObservedCorpHpPercent tracks the most-recent
	//         HP% reading; resets on kill end. Debug log when timeout
	//         conditions aren't met so we can tell why the bot is
	//         waiting. User: "he can just be on the far side of the
	//         room and we will wipe our progress assuming he died."
	// 1.9.99.91: Corp-death timeout fallback. When Corp dies, the
	//         death animation hides the HP bar for a tick or two before
	//         Corp's NPC fully despawns. If our HP-poll missed the
	//         brief "HP <= 1%" window (corp.isHealthBarVisible() can
	//         be false during the animation), corpSeenAtZeroHp stayed
	//         false and the bot logged "Corp not in render but no
	//         confirmed 0 HP" forever, never leaving FIGHTING_CORP.
	//         Fix: track corpMissingSinceMs (timestamp of first tick
	//         Corp wasn't in Query.npcs()). If Corp missing > 3s AND
	//         maxCorpHpPercentThisKill > 5% (proves we engaged this
	//         kill), declare dead → LOOTING. Resets on kill end and
	//         on Corp re-appearance. User: "when the kill ends we
	//         just prepare for combat again i think the status was
	//         fighting_corp even after he died. when we should be
	//         banking."
	// 1.9.99.90: spec-bar debounce energy-drop bypass. The 03:47 log
	//         caught a 15-second spec lockout: spec 1 fired (100→50),
	//         bar auto-toggled OFF (game rule), tryActivateSpec then
	//         refused to re-click for the full 600-1200ms debounce
	//         window AND beyond (because each subsequent retry hit the
	//         600ms trust-window again). Bot took 5+ Corp hits during
	//         the lockout, HP dropped to 20, mid-spec-dump panic tele
	//         fired. Fix: snapshot Combat.getSpecialAttackPercent() at
	//         each successful click (specEnergyAtLastActivate). On
	//         entry to tryActivateSpec, if current energy < snapshot,
	//         a spec already fired and the bar is legitimately off —
	//         skip the debounce entirely and re-click. The original
	//         debounce was designed for "SDK lag after a click" not
	//         "spec already swung". This separates the two cases.
	//         User: "we got stuck trying to spec and failed it and
	//         then ended up eating; i thought we were supposed to be
	//         teleporting if we are under 35?"
	// 1.9.99.89: configurable spec-dump panic-tele HP threshold.
	//         Added settings.specDumpPanicTeleHp (default 35). During
	//         the spec dump cycle (spec weapon equipped + energy >=
	//         floor + phase incomplete), the panic-tele trigger uses
	//         this value instead of the general
	//         INTERNAL_PANIC_TELE_HP (25). Combo-eat-at-50 stays
	//         skipped via shouldSkipEats. Outside spec dump, the 25
	//         threshold still applies. New GUI spinner in the Spec
	//         tab so users can tune per-account. User: "currently we
	//         will often get under 50 especially in the first few
	//         attacks of darklight/arclight before weve reduced
	//         stats ... If we get under 35 HP teleport out."
	// 1.9.99.88: mid spec-dump emergency eat now tele-outs to safety.
	//         1.9.99.87's "keep speccing through panic eat" was a
	//         regression — the user wanted EMERGENCY_ESCAPE on any
	//         combo-eat-threshold hit during the spec dump cycle. Bot
	//         eats once (HP recovers) then routes to ring-tele Ferox.
	//         Abandoning the remaining spec(s) is the right trade for
	//         not dying in the kill room. The non-spec-dump panic
	//         retreat (5-tile step-off) stays for the FIGHTING_CORP
	//         general-combat case. User: "What ar you talking about;
	//         the Panic retrated skip is literally a regression. IF we
	//         hit an emergency eat threshold mid spec we should just
	//         tele out."
	// 1.9.99.87: three fixes from spec-cycle / drift / kill-phase review.
	//         (a) Skip panicRetreatFromCorp when inSpecDumpCycle is
	//             true. Pre-1.9.99.87 a panic eat fired during the
	//             2-spec window also triggered the 5-tile retreat,
	//             trading a guaranteed spec hit for a manual walk —
	//             "instead of tping away after our double specs, if we
	//             had to panic eat sometimes we run away a few tiles
	//             instead of specing first which could get us killed".
	//             Now: eat, let the spec fire, energy depletes, the
	//             existing POH-restoration path handles the tele out.
	//         (b) Drift-recheck interval 500ms → 250ms. Sub-tick
	//             drift catches Corp's 1-tile roams within ~half a
	//             game tick so the bot's belief about Corp's position
	//             never lags by more than 1 tile. User: "if the corp
	//             for some reason moves 1 square back, before our
	//             realization of where the corp is standing updates,
	//             we think we are in its hitbox, and we use the
	//             minimap to walk back a square."
	//         (c) Re-added a kill-phase gate to shouldUseSpecialAttack.
	//             Once isInKillPhase() returns true (Corp HP < 1700 OR
	//             teamPhaseNeeded == 0), no more spec firing — Fang
	//             melee finishes the kill. Reverses 1.9.30's removal
	//             which let phase-1 specs continue past Corp HP 1700.
	//             User: "im thinking if we detect that the bosses
	//             health is under 1700 we stop spec dumping and just
	//             participate in the kill."
	// 1.9.99.86: post-kill maintenance skip. Pre-1.9.99.86 the bot
	//         ate food / drank prayer pots / etc. during LOOTING and
	//         the brief tick window where Corp had just died. Those
	//         actions were wasted because next stop is BANKING (full
	//         restore at pool) or WAITING_FOR_TEAM → POH restoration
	//         cycle (full restore). Now: handleHealthAndPrayer skips
	//         when state == LOOTING, same as it already did for
	//         TELEPORTING_TO_HOUSE / ENTERING_FRIEND_HOUSE /
	//         USING_ORNATE_POOL / TELEPORTING_BACK_TO_CORP /
	//         BANKING_AND_HEALING / W330_RESTORATION. handleVengeanceLogic
	//         also adds LOOTING to its state-block list (boss dead =
	//         nothing to reflect, wasted cast). User: "if corp died we
	//         dont need to repot or use food or anything because we
	//         are going to bank/poh and get full spec."
	// 1.9.99.85: house-tab withdrawal added to the bank trip. Pre-
	//         1.9.99.85 the bot never restocked tabs at the bank;
	//         once tabs ran out, every subsequent kill silently
	//         degraded to slow Fang-only DPS (shouldStartRestorationCycle
	//         was blocked, so no POH restoration). Now: at the end of
	//         each banking trip, if inventory tab count is below
	//         INTERNAL_HOUSE_TAB_REFILL_BELOW (4), withdraw up to
	//         INTERNAL_HOUSE_TAB_TARGET (10). Tabs survive deposit
	//         (depositKeepList line 9933) so leftovers ride between
	//         trips. Warning logged if the bank itself has 0 tabs —
	//         user-craft pipeline can be added later. User: "add house
	//         tabs to bank withdrawal."
	// 1.9.99.84: spec-dump eat-skip window extended. Pre-1.9.99.84
	//         the bot ate normal-threshold food between consecutive
	//         specs because state briefly flipped from
	//         USING_SPECIAL_ATTACK back to FIGHTING_CORP for ~600ms
	//         while the spec swing resolved; handleHealthAndPrayer ran
	//         12× in that window and fired a normal eat if HP was
	//         below eatHealthThreshold. Now the eat-skip flag covers
	//         the entire spec-dump cycle: spec weapon equipped +
	//         spec energy >= floor + phase incomplete + state in
	//         {FIGHTING_CORP, USING_SPECIAL_ATTACK}. Critical-HP eats
	//         (PANIC_TELE) still fire. Walk-in eats still fire because
	//         walk-in state is ENTERING_COMBAT (not in the new gate).
	//         User: "the account im playing on manually run in and
	//         double spec and tp out without needing to eat food
	//         usually ... however our bot keeps eating and usually
	//         ends up doing a normal attack inbetween our
	//         eating/spec dumping."
	// 1.9.99.83: lobby-during-FIGHTING_CORP recovery. When Corp is
	//         right at the entrance, the click rectangle for "Attack
	//         Corp" overlaps the click rectangle for the passage
	//         "enter". A left-click intended for Corp sometimes lands
	//         on the passage hitbox, teleporting the player back to
	//         the lobby — but the state machine stays at FIGHTING_CORP.
	//         The next tick's attackCorpIfVisible fallback sees Corp
	//         "off-screen" and steps toward Corp's last-known tile,
	//         walks back through the passage, gets yanked to lobby
	//         again. In-and-out loop. Fix: top of handleFightingCorp
	//         checks isInCorpLobby() && !isInCorpBossRoom(). If true,
	//         bounce state to ENTERING_COMBAT so the walk-in /
	//         passage-click logic runs cleanly. User: "the failure is
	//         because the attack and the go-through passage are in the
	//         same location and when we left click to attack we miss
	//         the hitbox." (Note: 1.9.99.83's first attempt was a
	//         corpCave step-out guard in attackCorpIfVisible — that
	//         path uses LocalWalking which can't actually cross
	//         through the passage; the bug was always the passage
	//         click. Reverted that change.)
	private static final String SCRIPT_VERSION = "1.9.99.233";
	private static final String SETTINGS_PREFIX = "corp_";
	private static final String DEFAULT_PROFILE = "default";
	private CorpSettings settings = new CorpSettings();
	private volatile boolean running = true;

	/** Runtime HP threshold for eating. Replaces the buggy class-load-time EAT_HEALTH_THRESHOLD constant
	 *  which read Skill.HITPOINTS.getActualLevel() before the player was guaranteed to be logged in. */
	private int eatHealthThreshold() {
		return Skill.HITPOINTS.getActualLevel() - INTERNAL_EAT_BELOW_MAX_HP;
	}

	// ========== 1. ADD THESE CONSTANTS TO TOP OF YOUR CLASS ==========
	// Friends-house entry no longer requires the host to have their house
	// "open" — same-world presence is enough. Failure modes are now real
	// (typo, offline, wrong world), so retries should fail fast.
	private static final int MAX_HOUSE_ENTRY_ATTEMPTS = 3;
	private static final int HOUSE_ENTRY_RETRY_DELAY_MIN = 1500;
	private static final int HOUSE_ENTRY_RETRY_DELAY_MAX = 3000;

	// ========== 2. ADD THESE VARIABLES WITH YOUR OTHER VARIABLES ==========
	private int currentRestorationCycle = 0;
	private int currentSpecialAttacksUsed = 0;
	private int currentHouseEntryAttempts = 0;
	private boolean isInRestorationPhase = false;
	private boolean needsPoolRestoration = false;
	private long lastHouseEntryAttempt = 0;

    // ========== CONFIGURATION ==========
    private static final String[] SUPER_RESTORE_NAMES = {"Super restore(4)", "Super restore(3)", "Super restore(2)", "Super restore(1)"};
    // ========== INTERNAL TUNING (1.8.0) ==========
    // These were CorpSettings fields in 1.7.x — moved to constants because
    // they're driven by Corp's known mechanics, not user preference.
    // Real user-facing levers remain in CorpSettings (corpMinHpForSpec,
    // totalRestorationCycles, useVengeance, mode/identity fields, etc.).
    public static final int INTERNAL_PHASE1_TARGET = 4;       // Elder maul + DWH specs
    public static final int INTERNAL_PHASE2_TARGET = 20;      // Arclight + Darklight + Emberlight specs
    public static final int INTERNAL_PHASE3_BGS_DAMAGE = 200; // BGS damage drained
    public static final int INTERNAL_EAT_BELOW_MAX_HP = 21;
    public static final int INTERNAL_EMERGENCY_HP = 15;
    // 1.9.4: one Corp hit away from death. Below this we skip eating/swapping
    // and bail straight to EMERGENCY_ESCAPE (Ferox tele / Games necklace /
    // run-to-entrance / logout) — eating clearly isn't keeping up.
    public static final int INTERNAL_PANIC_TELE_HP = 25; // 1.9.70: 8 -> 25
    // 1.8.9: combo-eat (Shark + Karambwan) trigger. Corp hits hard enough that
    // normal-eating at maxHp-21 (~78) can't keep up — by the time the next
    // tick fires the bot is already taking another swing. Combo eat below 50
    // gives a 38-HP heal in one cycle which actually outpaces Corp damage.
    public static final int INTERNAL_COMBO_EAT_HP = 50;
    public static final int INTERNAL_DRINK_PRAYER_THRESHOLD = 20;
    public static final int INTERNAL_CORP_LOW_HP_VENG_STOP_RAW_HP = 85;
    public static final int INTERNAL_COORD_WRITE_INTERVAL_TICKS = 5;
    // 1.9.99.211: bumped 10_000 -> 30_000 to align with
    // settings.coordinatorStaleThresholdMs (also 30s as of 1.9.99.201).
    // The mismatch caused teamPhaseNeeded() (using port/file aggregates at
    // 10s) to disagree with aggregateForDisplay (using 30s) on which
    // teammates were "live" — display showed teammates that the logic had
    // already dropped as stale, and vice-versa. With the heartbeat thread
    // republishing every 3s the 30s window is safe for normal sequences.
    public static final long INTERNAL_COORD_STALE_THRESHOLD_MS = 30_000L;
    // 1.9.99.85: house-tab withdrawal targets. Tabs are consumed 1 per
    // POH restoration cycle (typically 2-4 per kill). Refill when count
    // drops below REFILL_BELOW; top up to TARGET when refilling. Tabs
    // survive deposit (see depositKeepList line 9933) so unused tabs
    // ride between trips.
    public static final int INTERNAL_HOUSE_TAB_REFILL_BELOW = 4;
    public static final int INTERNAL_HOUSE_TAB_TARGET = 10;
    // Removed: INTERNAL_SPECS_PER_CYCLE — replaced with specsPerFullBar()
    //          method that derives from getMinOwnedSpecCost() (1.8.8).
    public static final int INTERNAL_TARGET_SHARKS = 10;
    public static final int INTERNAL_TARGET_KARAMBWANS = 9;
    public static final int INTERNAL_TARGET_SUPER_RESTORES = 2;
    public static final int INTERNAL_TARGET_SUPER_COMBAT = 1;
    public static final int INTERNAL_MIN_FOOD_COUNT = 10;
    public static final int INTERNAL_MIN_PRAYER_DOSES = 4;

    // ========== POH SOURCE CONSTANTS ==========
    public static final String POH_SOURCE_OWN_HOUSE   = "OWN_HOUSE";
    public static final String POH_SOURCE_FRIEND_HOUSE = "FRIEND_HOUSE";
    public static final String POH_SOURCE_BOT_HOST    = "BOT_HOST";
    public static final String POH_SOURCE_W330_RANDOM = "W330_RANDOM";
    public static final String POH_SOURCE_FEROX_ONLY  = "FEROX_ONLY";
    public static final String[] POH_SOURCE_OPTIONS = {
            POH_SOURCE_OWN_HOUSE,
            POH_SOURCE_FRIEND_HOUSE,
            POH_SOURCE_BOT_HOST,
            POH_SOURCE_W330_RANDOM, // visible but not yet implemented (1.7.0)
            POH_SOURCE_FEROX_ONLY
    };

    /** Combat-potion options users can pick from in the GUI. The actual
     *  in-game names are built from `<type> potion(N)` so any potion family
     *  following that naming pattern works. */
    public static final String[] COMBAT_POTION_OPTIONS = {
            "Divine super combat",
            "Super combat",
            "Crystalised super combat"
    };
    // ========== SPEC WEAPON DETECTION SYSTEM ==========
    private static final String ELDER_MAUL = "Elder maul";
    private static final String DARKLIGHT = "Darklight";
    private static final String RUNE_POUCH = "Rune pouch";
    // Supported main-weapon options. The GUI offers these as a dropdown.
    // Variant expansion (e.g., Fang -> regular + (or)) lives in getMainWeaponVariants().
    public static final String[] MAIN_WEAPON_OPTIONS = {
            "Osmumten's fang",
            // 1.9.99.148: Zamorakian spear option for the budget Corp setup
            // (no defender / no DFS). 2H stab weapon, same combat options
            // as fang. Variant expansion in getMainWeaponVariants() also
            // matches "Zamorakian hasta" since the two are functionally
            // identical at Corp and players often have one or the other.
            "Zamorakian spear"
    };
    // Inventory target counts
    // Valuable loot to pick up
    // Health and prayer thresholds
    // Corp combat settings
    private static final String CORPOREAL_BEAST = "Corporeal Beast";
    private static final String DARK_CORE = "Dark energy core";
    // Vengeance timing constants. Real spell cooldown is 30s; we use 31-37s for jitter.
    // The duplicate VENGEANCE_COOLDOWN_MS was removed; VENG_MIN/MAX_COOLDOWN below is the source of truth.
    private static final int VENGEANCE_FORGET_DELAY_MIN = 0; // 0-20 seconds additional delay
    private static final int VENGEANCE_FORGET_DELAY_MAX = 20000;
    // Corp spawn location (static coordinates - UPDATE THESE)
    // 1.9.99.115: corrected spawn location. The previous (2978, 4384, 2)
    // was 17 tiles west of where Corp actually spawns and the original
    // TODO comment said "Update with actual coordinates". User-provided
    // ground truth: Corp's 5x5 hitbox occupies (2993-2997, 4382-4386, 2)
    // at spawn. Center = (2995, 4384, 2). CORP_POSITIONS (static cardinal
    // fallbacks used when Corp isn't rendered yet) were targeting empty
    // floor 17 tiles short of Corp — bot then had to walk deeper via
    // moveToDeepCorpPosition before Corp became visible.
    private static final WorldTile CORP_SPAWN_LOCATION = new WorldTile(2995, 4384, 2);
    // 1.9.99.115: Corp's 5x5 spawn footprint as a polygon Area. Useful
    // for: (a) "Corp just respawned at home" detection (corp.getTile()
    // inside this area = first tick of a new kill, no roaming yet),
    // (b) cardinal pre-calculation BEFORE Corp is in render (we already
    // know where he is even when not yet loaded).
    private static final Area CORP_SPAWN_AREA = Area.fromPolygon(
            new WorldTile(2997, 4386, 2),
            new WorldTile(2997, 4382, 2),
            new WorldTile(2993, 4382, 2),
            new WorldTile(2993, 4386, 2)
    );
    // Relative positioning around Corp (not fixed coordinates, but relative offsets)
    // These offsets ensure proper spacing around Corp regardless of where it roams
	// NEW - Designed for 5x5 NPC
	// KEEP THESE - They put you 1 tile from Corp edge, which is perfect for melee
	// 1.9.99.143: expanded to the FULL 24-tile perimeter ring around Corp's
	// 5x5 hitbox. Pre-1.9.99.143 we had 8 fixed cardinals/diagonals only;
	// user wanted each "cross" treated as a 3-tile ribbon so the bot
	// doesn't stand on the same canonical tile every kill. The full ring
	// gives 24 candidate tiles all 1 tile from Corp's hitbox edge (all
	// melee-attackable). User: "EAch cross should technically be a 3
	// tile area on the outer side of the corp beast ... if we are on
	// the north tile, more than likely our west and east tiles will
	// also be safe (if they are walkable)."
	private static final List<int[]> CORP_POSITION_OFFSETS = Arrays.asList(
			// North ribbon (y = +3): 5 tiles, x from -2 to +2
			new int[]{-2,  3}, new int[]{-1,  3}, new int[]{ 0,  3}, new int[]{ 1,  3}, new int[]{ 2,  3},
			// South ribbon (y = -3)
			new int[]{-2, -3}, new int[]{-1, -3}, new int[]{ 0, -3}, new int[]{ 1, -3}, new int[]{ 2, -3},
			// East column (x = +3): 5 tiles, y from -2 to +2 (corners excluded; in N/S rows)
			new int[]{ 3, -2}, new int[]{ 3, -1}, new int[]{ 3,  0}, new int[]{ 3,  1}, new int[]{ 3,  2},
			// West column (x = -3)
			new int[]{-3, -2}, new int[]{-3, -1}, new int[]{-3,  0}, new int[]{-3,  1}, new int[]{-3,  2},
			// Corner tiles (x = ±3, y = ±3)
			new int[]{ 3,  3}, new int[]{ 3, -3}, new int[]{-3,  3}, new int[]{-3, -3}
	);
    // Get static positions around spawn for when Corp is not present
    private static final List<WorldTile> CORP_POSITIONS = Arrays.asList(
            new WorldTile(CORP_SPAWN_LOCATION.getX() - 3, CORP_SPAWN_LOCATION.getY(), CORP_SPAWN_LOCATION.getPlane()),
            new WorldTile(CORP_SPAWN_LOCATION.getX() + 3, CORP_SPAWN_LOCATION.getY(), CORP_SPAWN_LOCATION.getPlane()),
            new WorldTile(CORP_SPAWN_LOCATION.getX(), CORP_SPAWN_LOCATION.getY() - 3, CORP_SPAWN_LOCATION.getPlane()),
            new WorldTile(CORP_SPAWN_LOCATION.getX(), CORP_SPAWN_LOCATION.getY() + 3, CORP_SPAWN_LOCATION.getPlane())
    );
    // Positioning constants
    private static final int SAFE_DISTANCE_FROM_CORE = 3;
    private static final int SAFE_DISTANCE_FROM_TEAMMATES = 3;
    private static final int SAFE_DISTANCE_FROM_CORP_AREA = 2; // Distance from Corp's hitbox
    private static final int MAX_ATTACK_DISTANCE_FROM_CORP = 10; // Max distance to attack Corp
    // Different timeout thresholds for different states
    private static final long SHORT_STATE_TIMEOUT_MS = 30000; // 30 seconds for most states
    private static final long COMBAT_STATE_TIMEOUT_MS = 300000; // 5 minutes for combat states
    private static final long BANKING_STATE_TIMEOUT_MS = 60000; // 1 minute for banking
    private static final long TRAVEL_STATE_TIMEOUT_MS = 120000; // 2 minutes for travel
    private static final long TEAMMATE_GRACE_PERIOD_MS = 30000; // 30 seconds grace period (extended from 10s)
    private static final String DIVINE_RUNE_POUCH = "Divine rune pouch";
    // Vengeance constants
    private static final int VENG_MIN_COOLDOWN = 31000; // 31 seconds
    private static final int VENG_MAX_COOLDOWN = 37000; // 37 seconds
    // BOSS_LOW_HEALTH_THRESHOLD removed (was unused dead code).
    // The actual stop-vengeance threshold now lives in CorpSettings.corpLowHealthVengStop.
    private static final int BOSS_DEATH_VENG_MIN_DELAY = 1000;  // 1 second
    private static final int BOSS_DEATH_VENG_MAX_DELAY = 10000; // 10 seconds
    // ========== CAMERA ANGLE MANAGEMENT CONSTANTS ==========
    // 1.9.99.27: lowered from 100/80 to 75/50. Pre-1.9.99.27 the bot
    // pegged camera angle at max (100) and re-pegged it whenever it
    // dropped below 80 — visually screaming "bot" since real players
    // never set the top-down extreme. Now: target a moderate 75 only
    // when the current angle drops below 50 (genuinely too low to click
    // tiles), and let camera rotation handle visibility instead. User:
    // "real players dont adjust their camera angle all the way to the
    // lowest possible angle, they simply rotate the cameras view".
    private static final int MAX_CAMERA_ANGLE = 75; // moderate, human-like target
    private static final int MIN_ACCEPTABLE_ANGLE = 50; // only adjust when truly too low
    // 1.9.99.31: 5000 → 2000. User log: camera was at 93 at 01:38:00,
    // dropped to 33 by 01:38:11 (11s gap, drift below 50 caught only at
    // the next check). The SDK / TRiBot human-mouse antiban shifts the
    // angle during combat, and 5s is too slow to catch it before the
    // player loses visibility on incoming dark cores. 2s gives us
    // four checks per spec cycle.
    private static final int CAMERA_CHECK_INTERVAL_MS = 2000;
    private static final int CAMERA_ADJUSTMENT_DELAY_MIN = 800; // Min delay before adjusting
    private static final int CAMERA_ADJUSTMENT_DELAY_MAX = 1500; // Max delay before adjusting
    // ========== ENHANCED DARK CORE SYSTEM CONSTANTS ==========
    private static final int CORE_DANGER_DISTANCE = 3; // Start dodging when core within 3 tiles
    private static final int CORE_EMERGENCY_DISTANCE = 1; // Emergency movement when within 1 tile
    private static final int CORE_MIN_DODGE_DISTANCE = 2; // Minimum tiles to move when dodging
    private static final int CORE_MAX_DODGE_DISTANCE = 4; // Maximum tiles to move when dodging
    private static final int CORE_DISTANCE_SAMPLES = 3; // Number of distance samples to track
    // ========== Area locations =========
    Area corpCave = Area.fromPolygon(
            new WorldTile(2998, 4398, 2),
            new WorldTile(2974, 4398, 2),
            new WorldTile(2972, 4375, 2),
            new WorldTile(2991, 4367, 2),
            new WorldTile(3001, 4379, 2)
    );
    Area corpLobby = Area.fromPolygon(
            new WorldTile(2972, 4390, 2),
            new WorldTile(2972, 4378, 2),
            new WorldTile(2963, 4378, 2),
            new WorldTile(2964, 4388, 2)
    );
    Area ferox = Area.fromPolygon(
            new WorldTile(3156, 3646, 0),
            new WorldTile(3156, 3626, 0),
            new WorldTile(3141, 3617, 0),
            new WorldTile(3124, 3617, 0),
            new WorldTile(3124, 3640, 0),
            new WorldTile(3137, 3640, 0)
    );
    Area deepCorpArea = Area.fromPolygon(
            new WorldTile(2989, 4388, 2),
            new WorldTile(2997, 4387, 2),
            new WorldTile(2997, 4379, 2),
            new WorldTile(2990, 4380, 2)
    );
    private String chosenSpecWeapon = null; // Will be set at startup based on what player brings
    private BotState currentState = BotState.STARTING;
    // Combat tracking
    private boolean wasInCombat = false;
    private long lastCombatTime = 0;
    private long darkCoreLastSeen = 0;
    // 1.9.99.62: throttle the per-tick dark-core debug spam so user can
    // capture useful logs around core events without 10+ identical lines
    // per second drowning everything else.
    private long lastDarkCoreLogAt = 0;
    // 1.9.99.219: throttle the two POH-restoration skip logs that fired
    // every tick when they tripped (causing the user's logs to be unreadable).
    private long lastNoHouseTabLogAt = 0;
    private long lastKillPhaseSkipLogAt = 0;
    // 1.9.73: latched per core-engagement so we only attempt the
    // auto-retaliate disable once. Reset when the core grace expires.
    private boolean autoRetaliateDisabledForThisCore = false;
    private final WorldTile lastSafePosition = null;
    // Vengeance tracking
    private boolean hasUsedVengeanceThisTrip = false;
    private boolean vengeanceQueued = false;
    // 1.9.99.75: paint-side veng diagnostics.
    private int vengCastsThisKill = 0;
    private int vengCastsThisSession = 0;
    private volatile String vengLastGateReason = "-";
    private volatile long vengLastGateReasonAt = 0;
    // 1.9.99.77: track every castVengeance attempt (succ or fail) so we
    // can see widget-click activity on the overlay. lastVengAttemptAt
    // also throttles retry after a failed cast (no XP delta).
    private int vengAttemptCount = 0;
    private long lastVengAttemptAt = 0;
    private static final long VENG_FAILED_RETRY_THROTTLE_MS = 5000;
    // 1.9.97: deterministic veng-consumed tracking via HP delta. Set to false
    // when castVengeance succeeds; flipped true in updateHealthTracking when
    // currentHealth drops below previousHealth. Replaces the 1.9.96-removed
    // HP-bar-visibility probe that false-positived on full-HP heals. Starts
    // true so the first cast of a trip is always allowed.
    private boolean tookDamageSinceLastVeng = true;
    // 1.9.99.67: throttle for the vengeance-gate diagnostic log.
    private long lastVengGateDiagAt = 0;
    private long vengeanceUseTime = 0;
    private final long lastVengeanceCastTime = 0;
    // Prayer tracking
    private boolean prayerActivationQueued = false;
    private long prayerActivationTime = 0;
    private boolean prayerDeactivationQueued = false;
    private long prayerDeactivationTime = 0;
    private boolean corpWasAliveLastCheck = false;
    // 1.9.99.91: track when Corp NPC first went missing this kill.
    // Used as a timeout fallback to declare death and route to LOOTING
    // when we never observed Corp's HP bar at 0% (death animation hides
    // the bar before we poll the final HP value).
    private long corpMissingSinceMs = 0;
    // 1.9.99.92: most-recent observed Corp HP%. Used to gate the
    // missing-timeout: if last HP was high, Corp is more likely roaming
    // than dead (he doesn't go from full to dead in 3 seconds without a
    // coordinated dump). Reset at kill end.
    private double lastObservedCorpHpPercent = 100.0;
    // 1.9.99.105: throttle for the "Corp not in render — waiting" debug
    // log. Pre-1.9.99.105 it fired every main-loop tick (~20Hz), spamming
    // the chat console and burying actual important log lines.
    private long lastCorpMissingDiagAt = 0;
    // 1.9.24: explicit "we observed Corp HP at 0" flag. The transition
    // to LOOTING only fires when this is true. Resets per-kill.
    private boolean corpSeenAtZeroHp = false;
    // 1.9.28: track max Corp HP% observed this kill. The health bar reads
    // 0% on a freshly-engaged Corp because the bar exists (isHealthBarVisible
    // == true) but the percentage hasn't been populated server-side. Without
    // this tracker, we'd transition straight from FIGHTING_CORP to LOOTING
    // on the first tick of combat — wasting the kill. We only declare
    // Corp "dead from HP 0" if we've previously seen HP > 5% (proof the
    // bar was actually populated).
    private double maxCorpHpPercentThisKill = 0.0;

    /** 1.9.99.191: HP-aware bounded wait. Drop-in replacement for
     *  Waiting.waitUntil in combat-adjacent code paths where the standard
     *  call would freeze the main loop while Corp lands hits. Polls in
     *  50ms slices; bails early if HP drops into panic-tele range so the
     *  main loop's safety nets can fire.
     *  1.9.99.192: bail threshold lowered to INTERNAL_PANIC_TELE_HP (25)
     *  to match the "about to die" line. Original 50 was the combo-eat
     *  threshold, which spec-dump cycles intentionally ignore (the bot
     *  keeps speccing through HP 35-50 during dumps). 25 is the absolute
     *  floor below which the bot must abandon whatever it was doing
     *  regardless of state. */
    private boolean waitUntilHpSafe(long timeoutMs, java.util.function.BooleanSupplier predicate) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (predicate.getAsBoolean()) return true;
            } catch (Throwable ignored) {}
            int hp = MyPlayer.getCurrentHealth();
            if (hp > 0 && hp <= INTERNAL_PANIC_TELE_HP) {
                Log.warn("waitUntilHpSafe: HP " + hp + " <= " + INTERNAL_PANIC_TELE_HP
                        + " — bailing to main loop for emergency handling");
                return false;
            }
            Waiting.wait(50);
        }
        return false;
    }
    // 1.9.99.181: low-water ratchet for kill-phase detection. Once we've
    // seen Corp's bar drop below the spec floor THIS kill, latch kill phase
    // until kill-end — even if the bar briefly reads invisible, returns 0,
    // or Corp leaves the NPC cache during a relocate. Symmetric to the
    // max ratchet that protects late-join detection. 1.0 = "never seen".
    private double minCorpHpPercentThisKill = 1.0;
    // 1.9.99.107: death-detection diagnostic trackers. Capture Corp's
    // animation ID transitions and HP-bar-visibility falling edge so we
    // can identify the actual "Corp died" signal the client emits.
    // Hypothesis: isHealthBarVisible() going true → false IS the death
    // event (OSRS removes the bar at the start of the death animation,
    // before any 0% read). Animation ID at that moment should be Corp's
    // death anim. Once captured, the death-detect rewrite keys off these
    // signals instead of the unreliable HP-percent path.
    private int lastCorpAnimSeen = -2; // -2 sentinel: never observed
    private boolean lastCorpHealthBarVisible = false;
    private boolean lastCorpInteracting = false; // 1.9.99.108: track Corp's target presence
    // 1.9.99.108: ring buffer + frozen-after-despawn buffer rendered on
    // the overlay. The TRiBot log can't be relied on (finite + spammed
    // with unrelated lines), so we surface the death-detection trace
    // directly on the game canvas. recent[] is the live ring; frozen[]
    // is snapshotted at the moment of despawn and held until the next
    // kill begins, giving the user time to read it post-mortem.
    private final java.util.LinkedList<String> deathDiagRecent = new java.util.LinkedList<>();
    private volatile java.util.List<String> deathDiagFrozen = new java.util.ArrayList<>();
    private static final int DEATH_DIAG_BUFFER_SIZE = 14;

    /** 1.9.99.108: push one line into the death-diag ring buffer. Synchronized
     *  so the paint thread can snapshot safely. Auto-trims to DEATH_DIAG_BUFFER_SIZE. */
    private synchronized void pushDeathDiag(String line) {
        long t = (System.currentTimeMillis() / 100) % 100000; // last 5 digits, deciseconds
        deathDiagRecent.addLast(String.format("%05d %s", t, line));
        while (deathDiagRecent.size() > DEATH_DIAG_BUFFER_SIZE) {
            deathDiagRecent.removeFirst();
        }
    }

    /** 1.9.99.108: freeze a copy of the current ring buffer so the overlay
     *  shows the death moment after Corp despawns. Cleared at next kill start. */
    private synchronized void freezeDeathDiagBuffer() {
        deathDiagFrozen = new java.util.ArrayList<>(deathDiagRecent);
    }

    private synchronized java.util.List<String> snapshotDeathDiagFrozen() {
        return new java.util.ArrayList<>(deathDiagFrozen);
    }

    private synchronized java.util.List<String> snapshotDeathDiagRecent() {
        return new java.util.ArrayList<>(deathDiagRecent);
    }

    /** 1.9.99.108: clear both live and frozen buffers — called at new-kill start
     *  (handleEnteringCombat) so the user sees a fresh trace per kill. */
    private synchronized void clearDeathDiagBuffers() {
        deathDiagRecent.clear();
        deathDiagFrozen = new java.util.ArrayList<>();
    }
    // Weapon switching tracking
    private boolean specWeaponSwitchQueued = false;
    private long specWeaponSwitchTime = 0;
    private boolean needsToSwitchBackFromSpec = false;
    // 1.9.99.52: when specWeaponSwitchBack was queued. Used by the
    // kill-phase watchdog in handleFightingCorp to detect a stuck queue
    // (queued > 5s but main weapon still not equipped) and force the
    // swap instead of looping forever in spec-weapon-equipped DPS poke.
    private long specWeaponSwitchQueuedAt = 0;
    // 1.9.99.52: throttle the kill-phase diagnostic log so we get one
    // line per situation rather than 20/sec.
    private long lastKillPhaseDiagnosticAt = 0;
    private boolean specWeaponReadyForUse = false; // NEW: Track if spec weapon is ready
    // 1.9.34: debounce timestamp for spec-button activation. Combat.activateSpecialAttack()
    // is a CLICK on the spec button; if the button is already ON, clicking
    // it TURNS IT OFF. Multiple sites in the code call activate-with-guard,
    // but the guard (isSpecialAttackEnabled) returns stale data right after
    // a click. So 4 sites firing back-to-back in 2 seconds can produce an
    // unpredictable final state. Use this timestamp to skip activation if
    // we clicked recently — trust the prior click instead.
    private long lastSpecActivateAt = 0;
    // 1.9.99.90: snapshot of Combat.getSpecialAttackPercent() at the moment
    // we last successfully clicked the spec bar. If a subsequent caller
    // sees current energy < this snapshot, a spec has fired since our last
    // click — the bar auto-toggled OFF (game rule) and we MUST re-click
    // to arm the next spec. Without this signal, the 1.9.99.43 debounce
    // window (600-1200ms after a click) refuses to re-click because SDK
    // says bar is off — which is correct, but the cause is "spec swung"
    // not "click in flight". Result: 15s spec lockouts (see 03:47 log).
    private int specEnergyAtLastActivate = -1;
    // 1.9.78: randomized spec pre-activation timing. Each restoration
    // trip the bot rolls 50% at 3 stages (POH-before-tele, Corp-lobby,
    // boss-room). First "yes" wins and activates spec; final stage is
    // forced (guaranteed activation by the time we engage Corp). Once
    // a stage is rolled, we don't re-roll it this trip — pattern stays
    // varied across trips instead of activating at the same moment
    // every cycle.
    private boolean specPreActivatedThisTrip = false;
    private boolean preActivateStageARolled = false;
    private boolean preActivateStageA5Rolled = false;
    private boolean preActivateStageBRolled = false;
    private final java.util.Random preActivateRng = new java.util.Random();

    // 1.9.99.106: per-trip randomization for combat pot and weapon swap
    // timing. Rolled once per trip (alongside the spec stage rolls so
    // distribution lines up). Default values used when not rolled.
    private boolean combatPotPlanRolled = false;
    private CombatPotLocation combatPotPlanThisTrip = CombatPotLocation.BOSS_ROOM_WALK;
    private boolean weaponSwapPlanRolled = false;
    private WeaponSwapLocation weaponSwapPlanThisTrip = WeaponSwapLocation.LOBBY;
    private boolean combatPotDrunkThisTrip = false;

    private enum CombatPotLocation { HOUSE_POST_POOL, LOBBY, BOSS_ROOM_WALK }
    private enum WeaponSwapLocation { LOBBY, BOSS_ROOM }

    /** 1.9.99.106: roll the per-trip timing plans. Called once per trip
     *  alongside the spec-stage rolls. */
    private void rollTripTimingPlans() {
        // Combat pot: even 3-way split across house/lobby/boss-room.
        if (!combatPotPlanRolled) {
            combatPotPlanRolled = true;
            int r = preActivateRng.nextInt(3);
            combatPotPlanThisTrip = CombatPotLocation.values()[r];
            Log.info("Trip plan: combat pot drink at " + combatPotPlanThisTrip);
        }
        // Weapon swap: 70% lobby (saves 0.6s of boss-room delay), 30% boss room.
        if (!weaponSwapPlanRolled) {
            weaponSwapPlanRolled = true;
            int r = preActivateRng.nextInt(10);
            weaponSwapPlanThisTrip = (r < 7)
                    ? WeaponSwapLocation.LOBBY
                    : WeaponSwapLocation.BOSS_ROOM;
            Log.info("Trip plan: spec weapon swap at " + weaponSwapPlanThisTrip);
        }
    }

    /** 1.9.78: stage A — after pool drink, before tele back to Corp.
     *  1.9.99.106: 1/3 chance (was 1/2). Stage A.5 added between A and
     *  B for the during-tele-animation window. Net distribution per
     *  trip: ~1/3 POH-pool / ~1/3 post-jewellery-box / ~1/3 lobby. */
    private void maybePreActivateSpecStageA() {
        if (specPreActivatedThisTrip || preActivateStageARolled) return;
        preActivateStageARolled = true;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        if (preActivateRng.nextInt(3) == 0) {
            Log.info("Spec pre-activate stage A (POH pool): rolled YES");
            if (tryActivateSpec()) {
                specPreActivatedThisTrip = true;
                lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                xpAtSpec = getMeleeCombatXp();
                corpHpAtSpec = readCorpHpPct(); // 1.9.99.37
            }
        } else {
            Log.info("Spec pre-activate stage A (POH pool): rolled NO");
        }
    }

    /** 1.9.99.106: stage A.5 — right after clicking the ornate jewellery
     *  box. Overlaps the ~3-5s tele animation, so the spec click chains
     *  naturally with movement instead of looking like a fixed pre-cast
     *  ritual at the pool. 50% of remaining (= 1/3 overall once stage A
     *  takes its third). */
    private void maybePreActivateSpecStageA5() {
        if (specPreActivatedThisTrip || preActivateStageA5Rolled) return;
        preActivateStageA5Rolled = true;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        if (preActivateRng.nextBoolean()) {
            Log.info("Spec pre-activate stage A.5 (post-jewellery-box): rolled YES");
            if (tryActivateSpec()) {
                specPreActivatedThisTrip = true;
                lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                xpAtSpec = getMeleeCombatXp();
                corpHpAtSpec = readCorpHpPct();
            }
        } else {
            Log.info("Spec pre-activate stage A.5 (post-jewellery-box): rolled NO");
        }
    }

    /** 1.9.78: stage B — in Corp lobby, before walking to boss room.
     *  1.9.99.73: dropped the 50/50 roll. Stage A at the pool keeps its
     *  randomness, but once we're in the lobby with weapon equipped
     *  and spec energy at/above the floor, ALWAYS flip the bar on.
     *  The dice cost too many trips where the bot walked to Corp,
     *  took damage, then arrived with HP below the prep gate so
     *  prepareSpecWeaponForCorp also skipped activation, leaving the
     *  first swing un-spec'd. */
    private void maybePreActivateSpecStageB() {
        if (specPreActivatedThisTrip || preActivateStageBRolled) return;
        preActivateStageBRolled = true;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        Log.info("Spec pre-activate stage B (lobby): always-on (1.9.99.73)");
        if (tryActivateSpec()) {
            specPreActivatedThisTrip = true;
            lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
            xpAtSpec = getMeleeCombatXp();
            corpHpAtSpec = readCorpHpPct(); // 1.9.99.37
        }
    }

    /** 1.9.78: stage C — boss room. FORCED activate if not yet done. */
    private void forcePreActivateSpecStageC() {
        if (specPreActivatedThisTrip) return;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        if (!Combat.isSpecialAttackEnabled()) {
            Log.info("Spec pre-activate stage C (boss room): FORCED");
            if (tryActivateSpec()) {
                specPreActivatedThisTrip = true;
                lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                xpAtSpec = getMeleeCombatXp();
                corpHpAtSpec = readCorpHpPct(); // 1.9.99.37
            }
        } else {
            // already on (e.g. from a prior tick) — flag it as done
            specPreActivatedThisTrip = true;
        }
    }

    /** 1.9.99.106: combat-pot drink scheduled at the house (after pool).
     *  Only fires if (a) trip plan says HOUSE_POST_POOL, (b) we're not
     *  already boosted, and (c) we have a combat potion. */
    private void maybeDrinkCombatPotAtHouse() {
        if (combatPotDrunkThisTrip) return;
        if (combatPotPlanThisTrip != CombatPotLocation.HOUSE_POST_POOL) return;
        if (isStatsBoosted()) {
            combatPotDrunkThisTrip = true; // already boosted, no need
            return;
        }
        Log.info("Trip plan: drinking combat pot at house (post-pool)");
        if (drinkSuperCombat()) {
            combatPotDrunkThisTrip = true;
        }
    }

    /** 1.9.99.106: combat-pot drink scheduled in the lobby.
     *  1.9.99.187: dropped the trip-plan gate. Drinking outside banking is
     *  always safe (in-place dose decrement, no inventory slot needed),
     *  and being unboosted entering the boss room is a real DPS loss.
     *  User: "we can also drink our potion before we enter the room as
     *  long as we are not banking. its pretty much safe to consume it at
     *  any point if we arnt boosted." If inventory is full and we're about
     *  to drink the LAST dose (which creates an empty vial), eat a
     *  karambwan first to free a slot. */
    private void maybeDrinkCombatPotInLobby() {
        if (combatPotDrunkThisTrip) return;
        if (isStatsBoosted()) {
            combatPotDrunkThisTrip = true;
            return;
        }
        // 1.9.99.187: if inventory is full, eat a karambwan to make space
        // first — the last dose of a potion becomes an empty vial and
        // needs a slot. For non-last-dose drinks the eat is harmless prep
        // for the spec-weapon equip that follows.
        if (Inventory.isFull()) {
            Log.info("Lobby prep: inventory full — eating karambwan before drink");
            eatKarambwan();
        }
        Log.info("Lobby prep: drinking combat pot (not boosted)");
        if (drinkSuperCombat()) {
            // 1.9.99.190: drinkSuperCombat now waits 700ms internally; no
            // second wait needed here.
            combatPotDrunkThisTrip = true;
        }
    }

    /** 1.9.78: reset the pre-activation state machine. Called after a
     *  successful pool drink so the next restoration cycle gets fresh
     *  dice rolls. */
    private void resetSpecPreActivationRolls() {
        specPreActivatedThisTrip = false;
        preActivateStageARolled = false;
        preActivateStageA5Rolled = false; // 1.9.99.106
        preActivateStageBRolled = false;
        // 1.9.99.106: per-trip plan rolls for combat pot + weapon swap
        // location. Re-rolled at trip start so distribution varies.
        combatPotPlanRolled = false;
        weaponSwapPlanRolled = false;
        combatPotDrunkThisTrip = false;
    }
    // 1.9.40: friend-house typing debounce — wall-clock guard against double-typing.
    private long lastFriendHouseTypeAt = 0;
    private static final long FRIEND_HOUSE_TYPE_DEBOUNCE_MS = 12000;
    private static final long SPEC_ACTIVATE_DEBOUNCE_MS = 1200;

    // 1.9.2: tracks the spec-energy value at the most recent pre-activation.
    // The in-line spec detector now fires only when the current energy is
    // LOWER than this value — i.e. a real spec has consumed energy. Pre-1.9.2
    // the detector triggered on "energy < 100" which is true continuously
    // after the first spec, causing it to spam-click the spec button and
    // inflate the phase-spec counter (which then wrongly satisfied
    // teamPhaseNeeded() and blocked the POH restoration tele).
    private int lastSeenSpecEnergy = 100;
    // 1.9.9: XP-based hit detection. After a spec fires (energy drop), we
    // wait for combat XP to register. Increase = the hit landed; no
    // increase = miss. Only landed specs advance the phase counter. Magic
    // XP is excluded because vengeance casts would produce false positives.
    private long xpAtSpec = -1;
    // 1.9.99.37: same problem 1.9.99.22 fixed for XP applies to Corp HP.
    // Damage and energy-drop happen on the SAME server tick, so a
    // readCorpHpPct() at the in-line detector returns POST-damage HP —
    // baseline equals confirm reading, the HP-delta veto then fires on
    // a real hit. User log: "Spec MISSED (Corp HP didn't drop: 0.99% →
    // 0.99%) ... XP delta +181 was likely stale prior-spec XP" — but
    // +181 IS a real elder maul spec. Snapshot Corp HP at pre-activate
    // time (paired with xpAtSpec) so the baseline is pre-damage.
    private double corpHpAtSpec = -1;
    // 1.9.99.39: replaced the single global pending-spec slot (pendingHitWeapon /
    // pendingHitXpBaseline / pendingHitCorpHpBaseline / pendingHitDeadline) with
    // a FIFO of PendingSpecAttempt records. The old single-slot design lost spec
    // 1's record when spec 2 fired before spec 1 had confirmed — back-to-back
    // specs were effectively a coin-flip. User: "the current detector is clever,
    // but it's still built around a single mailbox. Back-to-back specs need a
    // little queue." processPendingSpecHit now iterates oldest-first; after
    // each confirmation we advance the XP baseline on younger attempts so we
    // don't double-credit the same delta.
    private static final class PendingSpecAttempt {
        final String weapon;
        long xpBaseline;
        double corpHpBaseline;
        final long deadline;
        // 1.9.99.45: monotonic-counter snapshot at enqueue. Confirmed via
        // hitsplat when monotonicHitsplatCounter > this value. After we
        // confirm an attempt via hit, we BUMP each remaining attempt's
        // snapshot by 1 — that hitsplat is consumed and can't re-confirm.
        // Mutable so we can bump it.
        long hitsplatCounterAtEnqueue;
        PendingSpecAttempt(String weapon, long xpBaseline, double corpHpBaseline,
                long deadline, long hitsplatCounterAtEnqueue) {
            this.weapon = weapon;
            this.xpBaseline = xpBaseline;
            this.corpHpBaseline = corpHpBaseline;
            this.deadline = deadline;
            this.hitsplatCounterAtEnqueue = hitsplatCounterAtEnqueue;
        }
    }
    private final java.util.Deque<PendingSpecAttempt> pendingHits = new java.util.ArrayDeque<>();
    // 1.9.99.45: monotonic counter of own hitsplats observed on Corp. Only
    // grows: each tick we read currentCount and add max(0, current - last)
    // to the counter. Hitsplat expirations shrink visible count but never
    // shrink the counter, so each NEW hit lands counter exactly once.
    private long monotonicHitsplatCounter = 0;
    private long lastObservedHitsplatCount = 0;
    // 1.9.99.45: after a hitsplat-confirmed spec, the corresponding XP
    // delta arrives 0-1 ticks later. Without this guard the same hit can
    // confirm a younger pending attempt via XP. Codex audit. Window =
    // 1200ms = 2 game ticks. During the window we advance pending XP
    // baselines to absorb the delayed XP without confirming.
    private long suppressXpConfirmUntil = 0;
    // 1.9.99.44: consecutive bank-withdraw failure tracking. Reset on
    // any successful withdraw or partial recovery via hasMinimumSupplies.
    // Hitting INTERNAL_BANK_FAILURE_STRIKES = clean stop with session-end
    // signal — replaces the 1.9.88 "TODO future fix" of looping forever.
    private int bankWithdrawFailureStrikes = 0;
    public static final int INTERNAL_BANK_FAILURE_STRIKES = 4;
    // 1.9.99.1: bumped from 2000ms to 5000ms. Elder maul animation is 6 ticks
    // (~3600ms) and XP propagates at the END of the swing. SDK getXp() can lag
    // the tick by another ~200-500ms. 2s was under-counting real hits.
    // 1.9.99.43: shrunk 5000ms → 4200ms. Codex audit: at 5s the window extends
    // past the spec swing's expected hit tick into the NEXT auto-attack
    // swing (6-tick weapons = 3.6s cycle). If the spec missed but the
    // follow-up auto-attack lands, its XP/hitsplat falsely confirms the
    // missed spec.
    // 1.9.99.50: tightened 4200ms → 3500ms. The 4200ms still bled past
    // the 3600ms next-swing tick for 6-tick weapons (Elder maul / DWH /
    // Arclight). A missed Elder maul could be confirmed by the next
    // auto-attack's hitsplat landing within those 600ms. 3500ms is just
    // under the next-swing window: covers the actual spec swing (~3000-
    // 3600ms after click depending on tick alignment) but excludes the
    // following swing. User: "it counted an elder maul spec that missed
    // as being a succesful spec ... our spec counts are getting all
    // twisted".
    private static final long HIT_CONFIRM_TIMEOUT_MS = 3500;
    // ========== STATE HANDLERS ==========
    // State machine timeouts to prevent infinite loops (different timeouts per state)
    private final long lastStateChangeTime = 0;
    private final BotState lastState = null;
    // Team coordination tracking
    private final long lastTeammateSeenTime = 0;
    private final long vengeanceActiveTime = 0;
    private long lastVengeanceCast = 0;
    // 1.9.95: re-engage attack debounce — prevents multiple corp.interact("Attack")
    // calls in the same game tick after eat/spec when player isn't yet showing
    // as "attacking Corp" but a click is already in flight.
    private long lastCorpReengageClickAt = 0;
    private static final long CORP_REENGAGE_DEBOUNCE_MS = 500;
    private VengeanceState vengeanceState = VengeanceState.READY_FOR_FIRST_CAST;
    private final long lastHealthCheck = 0;

    // 1.9.99.72: panic-retreat tracking. When emergency-eat fires twice
    // within EMERGENCY_EAT_SPIRAL_WINDOW_MS, handleHealthAndPrayer
    // calls panicRetreatFromCorp() and sets panicRetreatActiveUntil so
    // handleFightingCorp skips re-engaging for PANIC_RETREAT_PARK_MS
    // (or until HP recovers above PANIC_RETREAT_RESUME_HP).
    private long lastEmergencyEatAt = 0;
    private long panicRetreatActiveUntil = 0;
    // 1.9.99.97: gate the mid-fight drift recheck on this flag. The
    // recheck only needs to fire in four specific scenarios — walking
    // into the room, returning from dark-core handling, resuming after
    // panic retreat, returning from POH/banking. The rest of the fight
    // the bot is interacting with Corp continuously and a 1-tile Corp
    // roam is harmless (Corp doesn't stomp unless the PLAYER walks
    // into his hitbox, and antiStompTick covers that case). When this
    // flag is false, drift recheck is skipped entirely; the flag flips
    // true on the four trigger events and back to false after the
    // recheck confirms we're positioned. To revert: remove the
    // `needsRepositioning` gate at the drift-recheck site (search the
    // 1.9.99.97 tag). User: "we can disable the drift for most of the
    // fight because we will literally be interacting with it ... we
    // dont want to accidently run under him which we seem to not have
    // issues with anymore due to our previous fixes regarding the
    // buffer space."
    private boolean needsRepositioning = true;
    private BotState previousMainLoopState = null;
    private static final long EMERGENCY_EAT_SPIRAL_WINDOW_MS = 2000;
    private static final long PANIC_RETREAT_PARK_MS = 2500;
    private static final int PANIC_RETREAT_RESUME_HP = 80;

    // ========== MAIN CAMERA MANAGEMENT SYSTEM ==========
    private int previousHealth = 0;
    private boolean bossWasAlive = false;
    // 1.9.99.205: track the most recent big single-tick HP drop. Used to
    // override the spec-dump eat-skip rule when a 35+ magic hit lands —
    // the bot would otherwise hold eats until HP <= 35 (specDumpPanicTeleHp)
    // and die to the next 30 hit before getting another spec off. User
    // case: HP 99 → hit 35 → HP 64 (no eat, above panic threshold) → hit 30
    // → HP 34 → finally eats but the next 30 lands before food clears →
    // dead. With this override, the eat fires the tick after the 35.
    private int lastBigHitMagnitude = 0;
    private long lastBigHitAt = 0;
    private static final int BIG_HIT_THRESHOLD = 35;
    private static final long BIG_HIT_EAT_WINDOW_MS = 4000;
    // ========== CAMERA TRACKING VARIABLES ==========
    private long lastCameraCheck = 0;
    private long lastCameraAdjustment = 0;
    private final boolean cameraManagementEnabled = true;
    private CoreDodgeAxis chosenDodgeAxis = CoreDodgeAxis.NOT_SET;
    private CoreDodgeDirection lastDodgeDirection = null;
    private CoreDodgeState coreDodgeState = CoreDodgeState.DETECTED;
    private final Queue<Double> coreDistanceHistory = new LinkedList<>();
    private WorldTile lastCorePosition = null;
    private WorldTile lastCorpPosition = null;
    private long lastCoreDistanceCheck = 0;
    // 1.8.8: throttle for the mid-fight reposition check. Without this the
    // bot would re-issue a walk command every tick while Corp roams, which
    // looks robotic and also breaks combat (every walk click cancels the
    // current attack). 3 seconds matches how often Corp actually moves enough
    // to matter.
    private long lastRepositionCheck = 0;
    private long lastEncroachmentCheckAt = 0; // 1.9.99.125: 2s throttle for teammate-encroachment trigger
    // 1.9.99.131: set true when handleFightingCorp detects we're in the
    // lobby with state=FIGHTING_CORP (passage misclick teleported us
    // back). handleEnteringCombat reads this and walks DEEP into the
    // cave (away from the passage tile) BEFORE clicking Corp, so the
    // next attack click doesn't hit the passage again. Cleared once
    // we're safely deep in the cave.
    private boolean recoveringFromLobbyStuck = false;

    /** 1.9.99.133: encroachment-relocate helper. Pre-1.9.99.133 the
     *  check lived inside handleFightingCorp, so during a Fang spec
     *  animation lock (USING_SPECIAL_ATTACK state or any other tick
     *  that doesn't pass through handleFightingCorp) it didn't fire —
     *  even if a player walked right next to the bot. Extracted to a
     *  helper called every loop iteration from the main loop. Same 1s
     *  throttle. Returns true if we triggered a relocate (caller may
     *  want to skip rest-of-tick).
     *  User: "it doesnt seem like we are moving if players walk next to
     *  us during the killing phase." */
    private long lastEncroachmentStateGateLogAt = 0; // 1.9.99.139 throttle for state-gate-fail logs
    private long lastEncroachmentTriggerAt = 0; // 1.9.99.140 for paint
    private long lastCoreAttackClickAt = 0; // 1.9.99.145 debounce for core.interact("Attack")
    // 1.9.99.204: core commit latch. Once we successfully click Attack on the
    // core, we stay in HANDLING_DARK_CORE until the core actually despawns
    // (handled by the existing CORE_GRACE_MS no-render exit path). Without
    // this latch, isDarkCoreThreatening() flipping false the moment dist
    // exceeds 1.5 (teammate stun knocks core away, core drifts 1 tile, etc.)
    // bounced us back to FIGHTING_CORP — we'd attack Corp, the core would
    // re-enter range, we'd attack the core, and so on, alternating while the
    // core stayed alive the entire time. The latch keeps focus locked on the
    // core. Cleared when the core grace expires (real despawn) or per-kill
    // reset. Critical: this does NOT block antiStompTick (top of main loop),
    // emergency eat (HP-gated), or stepOffCorp inside handleDarkCore — those
    // continue to fire regardless so we can still bail when stuck under Corp.
    private boolean coreEngagementCommitted = false;
    private boolean maybeRelocateForEncroachment() {
        // 1.9.99.203: per-bot deterministic jitter on the 1s throttle. Without
        // jitter, both bots' lastEncroachmentCheckAt aligned on the same tick
        // after a simultaneous fire — every subsequent check fired in lockstep
        // (1s, 2s, 3s ...) and they evaluated identically. Adding a per-bot
        // 0-700ms offset to the throttle window phase-shifts the checks so
        // one bot's window opens before the other's — that bot fires first,
        // publishes its claim (see coordinatorPublishNow below), and the
        // later-firing bot sees the claim in scoring.
        long throttleMs = 1000;
        try {
            String selfNameJ = MyPlayer.getUsername();
            if (selfNameJ != null) {
                int hashJ = Math.abs(selfNameJ.hashCode());
                throttleMs += (hashJ % 700); // 0-699ms per-bot constant
            }
        } catch (Throwable ignored) {}
        if (System.currentTimeMillis() - lastEncroachmentCheckAt <= throttleMs) return false;
        // 1.9.99.140: update paint fields throughout so the user can see
        // exactly what's happening without relying on the debug log.
        // 1.9.99.144: dropped USING_SPECIAL_ATTACK from the eligible states.
        // User: "this should be gaited to fighting_corp phase i think" —
        // mid-spec animation lock makes mid-swing relocate clicks either
        // queue (delay our swing) or cancel (lose the spec). FIGHTING_CORP
        // is the only state where a relocate doesn't disrupt anything.
        // 1.9.99.150: also allow HANDLING_DARK_CORE. The bot is still
        // mid-Corp-fight while killing a core; once the core dies it goes
        // straight back to meleeing Corp, so a clean tile matters. Core
        // attacks are ground-clicks under our feet — a walkTo away from
        // the cluster doesn't interrupt that loop, the core just retargets
        // the new tile under us.
        if (currentState != BotState.FIGHTING_CORP
                && currentState != BotState.HANDLING_DARK_CORE) {
            paintState.encGateReason = "state=" + currentState;
            if (System.currentTimeMillis() - lastEncroachmentStateGateLogAt > 5000) {
                lastEncroachmentStateGateLogAt = System.currentTimeMillis();
                Log.debug("Encroachment check skipped — state=" + currentState
                        + " (not FIGHTING_CORP/HANDLING_DARK_CORE)");
            }
            return false;
        }
        // 1.9.99.193: skip relocate during active spec dumping. Between
        // specs the bot is briefly in FIGHTING_CORP (re-arming the spec
        // bar) — relocating there would burn ticks walking when the bot
        // should be firing the next spec. Spec dumps are time-critical;
        // hits taken during a relocate are worse than tolerating one
        // tile of teammate overlap until the dump ends. User: "our
        // repositioning and picking an ideal tile is happening during
        // the spec dumping phase. this means we take more hits because
        // we are worried about moving. this isnt intentional."
        boolean inSpecDumpRelocateBlock = isSpecWeaponEquipped()
                && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()
                && teamPhaseNeeded() > 0;
        if (inSpecDumpRelocateBlock) {
            paintState.encGateReason = "spec-dump active";
            if (System.currentTimeMillis() - lastEncroachmentStateGateLogAt > 5000) {
                lastEncroachmentStateGateLogAt = System.currentTimeMillis();
                Log.debug("Encroachment relocate skipped — spec dump in progress");
            }
            return false;
        }
        // 1.9.99.156 → 1.9.99.159: detect MID-CORE-ATTACK so we can apply
        // a tighter threshold below. User: "we can relocate mid core
        // attack if we are stacked or too close to eachother. but if we
        // properly set up around the corp that should almost never
        // happen." So instead of blocking relocate entirely during a
        // core attack (1.9.99.156's approach was too aggressive — bot
        // never moved off a stacked teammate during core), we just
        // tighten the threshold: require ACTUALLY stacked (≤ 1 tile)
        // before disrupting a core attack. Normal 3-tile threshold
        // applies when not attacking the core.
        boolean attackingCore = false;
        if (currentState == BotState.HANDLING_DARK_CORE) {
            try {
                Optional<org.tribot.script.sdk.types.Player> meOpt = MyPlayer.get();
                if (meOpt.isPresent()) {
                    Optional<org.tribot.script.sdk.interfaces.Character> tgt =
                            meOpt.get().getInteractingCharacter();
                    attackingCore = tgt.isPresent()
                            && tgt.get().getName() != null
                            && DARK_CORE.equalsIgnoreCase(tgt.get().getName());
                }
            } catch (Exception ignored) {}
        }
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) {
            paintState.encGateReason = "no corp in render";
            if (System.currentTimeMillis() - lastEncroachmentStateGateLogAt > 5000) {
                lastEncroachmentStateGateLogAt = System.currentTimeMillis();
                Log.debug("Encroachment check skipped — Corp NPC not in render");
            }
            return false;
        }
        lastEncroachmentCheckAt = System.currentTimeMillis();
        WorldTile myPosEnc = MyPlayer.getTile();
        if (myPosEnc == null) { paintState.encGateReason = "no player tile"; return false; }
        String selfName = MyPlayer.getUsername();
        long otherPlayerCount = Query.players().stream()
                .filter(p -> p.getName() != null
                        && !p.getName().equals(selfName))
                .count();
        // 1.9.99.141: Chebyshev distance (tile count) instead of Euclidean.
        // OSRS tiles are integer; Chebyshev = max(|dx|, |dy|) gives the
        // intuitive "N tiles away" count a player would say in chat.
        int meX = myPosEnc.getX();
        int meY = myPosEnc.getY();
        int closestPlayerTiles = Query.players().stream()
                .filter(p -> p.getName() != null
                        && !p.getName().equals(selfName))
                .mapToInt(p -> {
                    WorldTile pt = p.getTile();
                    if (pt == null) return Integer.MAX_VALUE;
                    return Math.max(Math.abs(pt.getX() - meX),
                                    Math.abs(pt.getY() - meY));
                })
                .min()
                .orElse(Integer.MAX_VALUE);
        int threshold = settings != null ? settings.encroachmentRelocateTiles : 3;
        // 1.9.99.159: tighter threshold when we're mid-core-attack. Only
        // relocate if literally stacked (≤ 1 tile away). Anything else
        // and we'd kite the core off ourselves before landing a hit.
        if (attackingCore && threshold > 1) {
            threshold = 1;
        }
        paintState.encOtherPlayers = (int) otherPlayerCount;
        paintState.encClosestDist = closestPlayerTiles == Integer.MAX_VALUE
                ? Double.MAX_VALUE : (double) closestPlayerTiles;
        Log.debug(String.format(
                "Encroachment check: state=%s, otherPlayers=%d, closestTiles=%s, threshold=%d",
                currentState, otherPlayerCount,
                closestPlayerTiles == Integer.MAX_VALUE ? "—" : String.valueOf(closestPlayerTiles),
                threshold));
        if (closestPlayerTiles <= threshold) {
            Log.info(String.format(
                    "Teammate encroachment (closest player %d tiles away, state=%s) — relocating to a less-crowded cardinal",
                    closestPlayerTiles, currentState));
            // 1.9.99.147: forceMove=true bypasses the "already in melee
            // range" + "already at bestPosition" early-returns inside
            // moveToNearestCorpPosition. Encroachment relocate MUST
            // actually walk to a different tile; the early-returns make
            // sense for initial entry but not here.
            if (moveToNearestCorpPosition(corpOpt.get(), true)) {
                needsRepositioning = false;
                lastEncroachmentTriggerAt = System.currentTimeMillis();
                paintState.encGateReason = "RELOCATING";
                return true;
            } else {
                paintState.encGateReason = "no better cardinal";
                Log.warn(String.format(
                        "Encroachment trigger fired (%d tiles) but moveToNearestCorpPosition returned false — no better cardinal available",
                        closestPlayerTiles));
            }
        } else if (closestPlayerTiles != Integer.MAX_VALUE) {
            paintState.encGateReason = "tiles > " + threshold;
        } else {
            paintState.encGateReason = "no other players";
        }
        return false;
    }
    // Add this to your class variables
    private boolean startedFightingWithTeammates = false;
    private long fightStartTime = 0;

    /**
     * Check and maintain optimal camera angle for tile clicking
     * Call this in your main execute loop
     */
	private void initializeCameraSetup() {
		Log.info("Initializing optimal camera setup...");

		Camera.setAngle(MAX_CAMERA_ANGLE);

		// Wait until camera angle is actually set
		boolean cameraSet = Waiting.waitUntil(3000, () -> {
			int currentAngle = Camera.getAngle();
			return currentAngle >= MIN_ACCEPTABLE_ANGLE;
		});

		if (cameraSet) {
			Log.info("Camera setup complete - angle: " + Camera.getAngle());
		} else {
			Log.warn("Camera setup timed out - angle: " + Camera.getAngle());
		}
	}

    private void maintainOptimalCameraAngle() {
        if (!cameraManagementEnabled) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        // Check camera angle every few seconds
        if (currentTime - lastCameraCheck >= CAMERA_CHECK_INTERVAL_MS) {
            lastCameraCheck = currentTime;

            try {
                int currentAngle = Camera.getAngle();

                // If camera angle is too low, adjust it
                if (currentAngle < MIN_ACCEPTABLE_ANGLE) {
                    Log.info("Camera angle too low: " + currentAngle + " (min acceptable: " + MIN_ACCEPTABLE_ANGLE + ")");

                    // Don't adjust too frequently
                    if (currentTime - lastCameraAdjustment >= 3000) { // 3 second cooldown
                        adjustCameraToOptimal(); // Uses the fixed version
                    } else {
                        Log.debug("Camera adjustment on cooldown");
                    }
                } else {
                    Log.debug("Camera angle acceptable: " + currentAngle);
                }

                // 1.9.77.1: removed Camera.resetZoomPercent. User reset is
                // the WRONG direction (default is zoomed IN, user wants
                // zoomed OUT). Just leave zoom alone — user sets it
                // manually and any antiban drift is their TRiBot
                // setting to disable.
            } catch (Exception e) {
                Log.error("Error checking camera angle: " + e.getMessage());
            }
        }
    }

    private void adjustCameraToOptimalWithRetry() {
        try {
            int currentAngle = Camera.getAngle();

            Log.info("Adjusting camera from " + currentAngle + " to " + MAX_CAMERA_ANGLE);

            // Add human-like delay
            long adjustmentDelay = TribotRandom.uniform(CAMERA_ADJUSTMENT_DELAY_MIN, CAMERA_ADJUSTMENT_DELAY_MAX);
            Waiting.waitUniform((int) adjustmentDelay, (int) adjustmentDelay + 200);

            // Try to set camera with retry logic
            if (setCameraAngleWithRetry(MAX_CAMERA_ANGLE, 3)) {
                Log.info("Camera adjustment completed successfully");
                lastCameraAdjustment = System.currentTimeMillis();
            } else {
                Log.warn("Camera adjustment failed after retries");
            }

        } catch (Exception e) {
            Log.error("Exception during camera adjustment with retry: " + e.getMessage());
        }
    }

    /**
     * Adjust camera to optimal angle with human-like timing
     */
	private void adjustCameraToOptimal() {
		int currentAngle = Camera.getAngle();
		Log.info("Adjusting camera from " + currentAngle + " to " + MAX_CAMERA_ANGLE);

		Camera.setAngle(MAX_CAMERA_ANGLE);

		// Wait until camera actually moves
		Waiting.waitUntil(2000, () -> {
			int newAngle = Camera.getAngle();
			return newAngle >= MIN_ACCEPTABLE_ANGLE;
		});

		lastCameraAdjustment = System.currentTimeMillis();
	}

    private void setCameraOptimalSimple() {
        try {
            Log.info("Setting camera to optimal angle: " + MAX_CAMERA_ANGLE);

            // Just set it and assume it works
            Camera.setAngle(MAX_CAMERA_ANGLE);

            // Wait for camera to settle
            Waiting.waitUniform(500, 800);

            lastCameraAdjustment = System.currentTimeMillis();
            Log.info("Camera adjustment command sent");

        } catch (Exception e) {
            Log.error("Exception setting camera angle: " + e.getMessage());
        }
    }

    /**
     * Force camera to optimal position immediately (for critical moments)
     */
    private void forceCameraOptimal() {
        try {
            Log.info("FORCE: Setting camera to optimal position for critical action");

            // Set camera angle (void method)
            Camera.setAngle(MAX_CAMERA_ANGLE);

            // Wait for camera movement to complete
			Waiting.waitUntil(3000, () -> Camera.getAngle() >= MIN_ACCEPTABLE_ANGLE);

            // Verify the camera angle was set
            int resultAngle = Camera.getAngle();
            if (resultAngle >= MIN_ACCEPTABLE_ANGLE) {
                Log.info("Force camera adjustment successful - angle: " + resultAngle);
                lastCameraAdjustment = System.currentTimeMillis();
            } else {
                Log.error("CRITICAL: Force camera adjustment failed - angle: " + resultAngle);
            }

        } catch (Exception e) {
            Log.error("CRITICAL: Exception during force camera adjustment: " + e.getMessage());
        }
    }

    // ========== SIMPLIFIED SPEC WEAPON METHODS ==========

    private boolean setCameraAngleWithRetry(int targetAngle, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Log.info("Setting camera angle to " + targetAngle + " (attempt " + attempt + "/" + maxAttempts + ")");

                // Set the angle
                Camera.setAngle(targetAngle);

                // Wait for camera to move
                Waiting.waitUniform(600, 1000);

                // Check if it worked
                int currentAngle = Camera.getAngle();
                if (Math.abs(currentAngle - targetAngle) <= 5) { // Allow 5 degree tolerance
                    Log.info("Camera angle set successfully: " + currentAngle);
                    return true;
                } else {
                    Log.warn("Camera angle not quite right: " + currentAngle + " (target: " + targetAngle + ")");
                    if (attempt < maxAttempts) {
                        Waiting.waitUniform(300, 600); // Brief wait before retry
                    }
                }

            } catch (Exception e) {
                Log.error("Exception on camera adjustment attempt " + attempt + ": " + e.getMessage());
            }
        }

        Log.error("Failed to set camera angle after " + maxAttempts + " attempts");
        return false;
    }

    /**
     * Enhanced screen tile clicking with camera angle validation
     */
    private boolean clickScreenTileWithCameraCheck(WorldTile targetTile) {
        try {
            // CRITICAL: Ensure camera angle is optimal before clicking
            if (!isCameraAngleAcceptable()) {
                Log.info("Camera angle not optimal for tile clicking, adjusting...");
                forceCameraOptimal(); // Uses fixed version

                // Brief additional wait and verify
                Waiting.waitUniform(300, 500);

                // Check if adjustment helped
                if (!isCameraAngleAcceptable()) {
                    Log.warn("Camera angle still not ideal, but continuing with tile click");
                }
            }

            Log.info("Clicking screen tile with camera angle " + Camera.getAngle() + ": " + targetTile);

            // Use direct tile interaction instead of minimap
            if (targetTile.interact("Walk here")) {
                Log.info("Successfully clicked screen tile");
                return true;
            } else {
                Log.warn("Failed to interact with screen tile, trying alternative method");

                // Alternative: try using LocalWalking as fallback
                if (LocalWalking.walkTo(targetTile)) {
                    Log.info("Fallback walking method succeeded");
                    return true;
                }
            }

            Log.error("All click methods failed for tile: " + targetTile);
            return false;

        } catch (Exception e) {
            Log.error("Exception during screen tile click: " + e.getMessage());
            return false;
        }
    }

    /**
     * Camera preparation for dark core dodging
     */
    private void prepareCameraForCoreDodging() {
        Log.info("Preparing camera for dark core dodging");

        // Force optimal camera for critical dodging movements
        forceCameraOptimal();

        // Ensure camera is ready
        if (isCameraAngleAcceptable()) {
            Log.info("Camera prepared for core dodging - optimal angle achieved");
        } else {
            Log.warn("Camera preparation incomplete - tile clicking may be less accurate");
        }
    }

    private void handleAdvancedDarkCoreWithCamera() {
        Log.info("=== ENHANCED DARK CORE HANDLING WITH CAMERA MANAGEMENT ===");

        // PRIORITY 0: Ensure camera is optimal for precise dodging
        if (!isCameraAngleAcceptable()) {
            prepareCameraForCoreDodging();
        }

        Optional<Npc> darkCoreOpt = findDarkCore();
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();

        if (!darkCoreOpt.isPresent()) {
            Log.info("Dark core disappeared, returning to combat");
            resetCoreDodgeTracking();
            currentState = BotState.FIGHTING_CORP;
            return;
        }

        Npc darkCore = darkCoreOpt.get();
        Npc corp = corpOpt.orElse(null);

        // Update core tracking data
        updateCoreDistanceTracking(darkCore);

        // Determine current core dodge state
        CoreDodgeState newState = determineCoreState(darkCore);
        if (newState != coreDodgeState) {
            Log.info("Core state change: " + coreDodgeState + " -> " + newState);
            coreDodgeState = newState;
        }

        // Execute appropriate action based on state
        switch (coreDodgeState) {
            case DETECTED:
                handleCoreDetectedWithCamera(darkCore, corp);
                break;
            case DODGING:
                handleCoreDodgingWithCamera(darkCore, corp);
                break;
            case ATTACKING:
                handleCoreAttacking(darkCore, corp); // No camera change needed
                break;
            case EMERGENCY:
                handleCoreEmergencyWithCamera(darkCore, corp);
                break;
        }

        // Check for core timeout or disappearance
        if (System.currentTimeMillis() - darkCoreLastSeen > 15000) {
            Log.info("Dark core timeout, returning to combat");
            resetCoreDodgeTracking();
            currentState = BotState.FIGHTING_CORP;
        }
    }

    private void handleCoreDetectedWithCamera(Npc darkCore, Npc corp) {
        Log.info("Core detected - ensuring camera optimal for movement calculations");

        // Ensure camera is optimal before choosing movement strategy
        if (!isCameraAngleAcceptable()) {
            forceCameraOptimal();
        }

        // Choose optimal movement axis based on available space
        chosenDodgeAxis = chooseOptimalDodgeAxis(darkCore, corp);
        Log.info("Chosen dodge axis: " + chosenDodgeAxis);

        // Immediately transition to appropriate state
        coreDodgeState = isCoreApproaching(darkCore) ? CoreDodgeState.DODGING : CoreDodgeState.ATTACKING;
    }

    private void handleCoreDodgingWithCamera(Npc darkCore, Npc corp) {
        Log.info("Core dodging - using optimal camera for precise movement");

        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();

        // Calculate dodge position along chosen axis
        WorldTile dodgePosition = calculateDodgePosition(myPos, corePos, corp);

        if (dodgePosition != null) {
            Log.info("Dodging to position with camera-assisted clicking: " + dodgePosition);
            if (clickScreenTileWithCameraCheck(dodgePosition)) {
                // Brief wait for movement to start
                Waiting.waitUniform(200, 400);
            } else {
                Log.error("Failed to click dodge position with camera assistance, trying emergency movement");
                coreDodgeState = CoreDodgeState.EMERGENCY;
            }
        } else {
            Log.warn("No valid dodge position found, trying emergency movement");
            coreDodgeState = CoreDodgeState.EMERGENCY;
        }
    }

    // ========== CORP AREA DETECTION METHODS ==========

    private void handleCoreEmergencyWithCamera(Npc darkCore, Npc corp) {
        Log.warn("EMERGENCY: Core too close - forcing optimal camera!");

        // Force optimal camera for emergency movement
        forceCameraOptimal();

        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();

        // Emergency movement - any direction that gets us away quickly
        WorldTile emergencyPos = calculateEmergencyPosition(myPos, corePos, corp);

        if (emergencyPos != null) {
            Log.info("Emergency movement with optimal camera to: " + emergencyPos);
            if (clickScreenTileWithCameraCheck(emergencyPos)) {
				Waiting.waitUntil(2000, () -> !MyPlayer.isMoving());
                // After emergency movement, reassess situation
                coreDodgeState = CoreDodgeState.DETECTED;
            }
        }
    }

    public void executeWithCameraManagement(String args) {
        Log.info("Starting Improved Corporeal Beast Team Fighter with Camera Management");

        // Initial camera setup
        Log.info("Setting initial optimal camera angle");
        forceCameraOptimal();

        while (true) {
            try {
                // PRIORITY 1: Maintain optimal camera angle
                maintainOptimalCameraAngle();

                // Handle emergency situations first
                if (shouldEmergencyEscape()) {
                    currentState = BotState.EMERGENCY_ESCAPE;
                }

                // Execute current state - ONLY ONE STATE RUNS PER ITERATION
                executeCurrentState();

                // 1.9.99.133: encroachment relocate runs in main loop now
                // (was only in handleFightingCorp, missed USING_SPECIAL_ATTACK).
                // Helper does its own state + throttle gating.
                maybeRelocateForEncroachment();

                // Always check for maintenance needs (unless in emergency or recovery)
                // 1.9.99.113: also skip during DEATH_RECOVERY. The recovery
                // flow has its own Protect-from-Magic toggle and we don't
                // have karams/rune-pouch/spec-weapon in inventory until the
                // gravestone loot step. Pre-1.9.99.113 the bot tried to cast
                // vengeance, swap spec weapon, queue prayer activations,
                // and emergency-eat during the walk from Lumbridge to Ferox.
                if (currentState != BotState.EMERGENCY_ESCAPE
                        && currentState != BotState.DEATH_RECOVERY) {
                    handleHealthAndPrayer();
                    handlePrayerActivationTiming();
                    handlePrayerDeactivationTiming();
                    handleSpecWeaponSwitchTiming();
                }

                Waiting.waitUniform(35, 75);

            } catch (Exception e) {
                Log.error("Error in main loop: " + e.getMessage());
                currentState = BotState.EMERGENCY_ESCAPE;
            }
        }
    }

    /**
     * Check if current camera angle is acceptable for tile clicking
     */
    private boolean isCameraAngleAcceptable() {
        try {
            int currentAngle = Camera.getAngle();
            boolean acceptable = currentAngle >= MIN_ACCEPTABLE_ANGLE;

            if (!acceptable) {
                Log.debug("Camera angle unacceptable for tile clicking: " + currentAngle);
            }

            return acceptable;

        } catch (Exception e) {
            Log.error("Error checking camera angle: " + e.getMessage());
            return false; // Assume not acceptable if we can't check
        }
    }

    /** True if the given spellbook widget is unambiguously Vengeance (Self),
     *  not Vengeance Other. Vengeance Other lives at a different child index
     *  on the Lunar tab but historically has caused mis-picks, so we also
     *  guard on the widget's display text containing "Vengeance" but NOT
     *  "Other". HTML color tags are stripped before comparison. */
    private boolean isVengeanceSelfWidget(Widget w) {
        if (w == null) return false;
        // 1.9.75: sprite ID 564 is the Vengeance Self icon — user's widget
        // inspector screenshot confirmed it. Sprite ID is the most stable
        // identifier (doesn't change with widget tree shifts). Match by
        // sprite ID first; fall back to name match.
        try {
            int textureId = w.getTextureId();
            if (textureId == 564) return true;
        } catch (Throwable ignored) {}

        // 1.9.65: dropped the 'getActions().contains("Cast")' requirement.
        // Some game variants only expose Cast on hover. Match by name.
        String combined = "";
        try { combined += " " + w.getName().orElse(""); } catch (Throwable ignored) {}
        combined += " " + w.getText().orElse("");
        String clean = combined.replaceAll("<[^>]*>", "").trim().toLowerCase();
        if (!clean.contains("vengeance")) return false;
        if (clean.contains("other")) return false;
        return true;
    }

    private void castVengeanceWidget(int childIndex) {
        // Open magic tab
        GameTab.MAGIC.open();
        Waiting.waitUntil(2000, () -> GameTab.MAGIC.isOpen());

        // 1.9.65: dropped the isVisible() filter — same root cause as the
        // 1.9.55 dialog-readiness fix. Spellbook icons can return false
        // for isVisible() depending on render state but still be
        // clickable. Just match by widget name (covered in
        // isVengeanceSelfWidget); try Cast action first, fall back to
        // a plain click.
        Optional<Widget> vengeanceWidget = Query.widgets()
                .inRoots(218)
                .filter(this::isVengeanceSelfWidget)
                .findFirst();

        if (vengeanceWidget.isPresent()) {
            Widget w = vengeanceWidget.get();
            int[] path = w.getIndexPath();
            Log.info("Clicking Vengeance widget at path " + java.util.Arrays.toString(path));
            boolean clicked = false;
            try { clicked = w.click("Cast"); } catch (Throwable ignored) {}
            if (!clicked) {
                Log.info("Cast action not available — falling back to plain click");
                try { clicked = w.click(); } catch (Throwable ignored) {}
            }
            if (clicked) {
                Waiting.waitUntil(3000, () -> !MyPlayer.isAnimating());
            } else {
                Log.warn("Vengeance widget click failed (both Cast and plain)");
            }
        } else {
            Log.warn("Vengeance widget not found (searched root 218 by name)");
        }
    }

    // Add this debug method
    private void debugVengeanceWidget() {
        castVengeanceWidget(142);

    }

    // ========== MAIN SCRIPT LOOP ==========
    @Override
    public void execute(String args) {
        // Resolve settings: args = profile name, else show GUI.
        if (args != null && !args.trim().isEmpty()) {
            String name = args.trim();
            Optional<CorpSettings> loaded = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, CorpSettings.class);
            if (loaded.isPresent()) {
                settings = loaded.get();
                Log.info("Loaded profile from args: '" + name + "'");
            } else {
                Log.warn("No profile '" + name + "', showing settings dialog.");
                if (!showSettingsDialog()) { Log.info("Cancelled."); return; }
            }
        } else {
            if (!showSettingsDialog()) { Log.info("Cancelled."); return; }
        }

        // 1.9.99.217: loud startup banner so the user can confirm at a glance
        // which version of the bytecode is loaded. TRiBot/JVM caches classes
        // across script Stop→Start within an open client window — you have
        // to close the entire TRiBot client and reopen it to pick up a
        // recompile. If the banner here doesn't match the source version,
        // the loaded class is stale and a full client restart is needed.
        Log.info("================================================================");
        Log.info("  Corp script started — bytecode version " + SCRIPT_VERSION);
        Log.info("  (if this doesn't match your source, fully close + reopen TRiBot)");
        Log.info("================================================================");
		initializeCameraSetup();
		scriptStartTime = System.currentTimeMillis();
		overlayInit();
		// 1.9.99.201: background heartbeat thread keeps our coord publish fresh
		// even when the main loop is stuck in a long Waiting.waitUntil sequence
		// (bank, POH portal, walks). See coordinatorHeartbeat for details.
		startCoordinatorHeartbeat();

        while (running) {
            //debugDarkCoreSystem();

            try {
                // Handle emergency situations first
                if (shouldEmergencyEscape()) {
                    currentState = BotState.EMERGENCY_ESCAPE;
                }

				maintainOptimalCameraAngle();

                // Publish our state to the team coordinator (if enabled).
                coordinatorPublish();

                // ANTI-STOMP: highest-priority safety check. If we're standing under
                // Corp (its 5x5 hitbox), step off NOW before doing anything else.
                // The stomp is 30-51 unblockable every 7 ticks — eats food fast and
                // can chain into a death. Runs before state logic so it overrides
                // any "attack Corp" or "walk to position" action that would keep
                // us standing on its tile.
                // 1.9.99.43: Codex audit — process pending spec hits BEFORE
                // any safety branch that can `continue`, so back-to-back
                // anti-stomp ticks don't starve the spec-hit confirmation
                // window. Pre-1.9.99.43 a chain of stomp-step iterations
                // could expire a pending attempt's deadline without ever
                // letting processPendingSpecHit run.
                processPendingSpecHit();
                // 1.9.99.48: maintain HP-delta tracking in EVERY state so
                // tookDamageSinceLastVeng flips correctly during spec dumps,
                // walks, POH cycles, etc. — not just inside handleFightingCorp.
                // Pre-1.9.99.48 vengeance never armed because READY_FOR_FIRST_CAST
                // only transitions to ACTIVE_CASTING inside handleVengeanceLogic,
                // which itself is only called from handleFightingCorp — so HP
                // drops during spec-dump or transitions were missed.
                updateHealthTracking();
                preemptiveApproachKarambwan(); // 1.9.99.206
                if (antiStompTick()) {
                    Waiting.waitUniform(23, 75);
                    continue; // skip the rest of this iteration; step needs to land
                }

                // Execute current state - ONLY ONE STATE RUNS PER ITERATION
                executeCurrentState();

                // 1.9.99.133: encroachment relocate runs in main loop now
                // (was only in handleFightingCorp, missed USING_SPECIAL_ATTACK).
                // Helper does its own state + throttle gating.
                maybeRelocateForEncroachment();

                // Always check for maintenance needs (unless in emergency or recovery)
                // 1.9.99.113: also skip during DEATH_RECOVERY. The recovery
                // flow has its own Protect-from-Magic toggle and we don't
                // have karams/rune-pouch/spec-weapon in inventory until the
                // gravestone loot step. Pre-1.9.99.113 the bot tried to cast
                // vengeance, swap spec weapon, queue prayer activations,
                // and emergency-eat during the walk from Lumbridge to Ferox.
                if (currentState != BotState.EMERGENCY_ESCAPE
                        && currentState != BotState.DEATH_RECOVERY) {
                    handleHealthAndPrayer();
                    handlePrayerActivationTiming();
                    handlePrayerDeactivationTiming();
                    handleSpecWeaponSwitchTiming();
                    // 1.9.99.74: vengeance lifted to main loop AFTER eats.
                    // Pre-1.9.99.74 handleVengeanceLogic was only invoked
                    // from handleFightingCorp's tail — during spec dumps
                    // (USING_SPECIAL_ATTACK state), weapon swaps, and any
                    // early return from handleFightingCorp it never fired.
                    // Net effect from the 20:53 log: veng cast once per
                    // kill, at boss death. Running here means it ticks
                    // every loop iteration regardless of state, and the
                    // post-eat ordering ensures emergency HP wins the tick
                    // if both want to fire. handleVengeanceLogic itself
                    // also gates on HP > INTERNAL_COMBO_EAT_HP and on
                    // currentState (POH/lobby/banking blocked).
                    handleVengeanceLogic();
                }

                Waiting.waitUniform(23,75);

                overlayUpdate();

            } catch (Exception e) {
                Log.error("Error in main loop: " + e.getMessage());
                currentState = BotState.EMERGENCY_ESCAPE;
            }
        }
        overlayClose();
        // 1.9.99.201: stop heartbeat thread first so it doesn't keep
        // publishing through the closing coordinator.
        stopCoordinatorHeartbeat();
        // 1.9.99.186: clean shutdown of coord threads so they don't survive
        // as zombies into the next script run.
        if (ACTIVE_PORT_COORD != null) {
            try { ACTIVE_PORT_COORD.shutdown(); } catch (Exception ignored) {}
            ACTIVE_PORT_COORD = null;
        }
        Log.info("Corp stopping.");
    }

    /**
     * Execute only the current state - this ensures only one action set runs per loop
     */
    private void executeCurrentState() {
        //debugVengeanceWidget();

        // Death detection runs before any state handler so we can pre-empt
        // whatever we thought we were doing. If we suddenly find ourselves
        // away from Corp with a gravestone nearby, we died.
        if (currentState != BotState.DEATH_RECOVERY && detectDeath()) {
            Log.warn("Death detected — switching to DEATH_RECOVERY");
            // 1.9.99.183: clear pending spec-hit confirmations on death.
            // Hits queued before death can't confirm against the wrong Corp
            // on the next kill (XP-delta / hitsplat baselines are stale).
            // Audit LOW #15.
            pendingHits.clear();
            currentState = BotState.DEATH_RECOVERY;
        }

        // Session-end propagation: if a teammate ran out of supplies and
        // signaled session end, we set the local pending flag so handleLooting
        // routes to EMERGENCY_ESCAPE after the current kill instead of starting
        // a new one. Check is cheap (one coordinator read).
        if (!sessionEndPending) {
            AccountSnapshot signaling = findTeammateRequestingSessionEnd();
            if (signaling != null) {
                Log.warn("Teammate signaled session end: " + signaling.sessionEndReason
                        + " - ending after current kill");
                sessionEndPending = true;
            }
        }

        // 1.9.99.14: tick processPendingSpecHit BEFORE the state dispatcher
        // so spec-hit confirmation runs in EVERY state, not just
        // FIGHTING_CORP. Pre-1.9.99.14 a spec fired right before a state
        // transition (e.g. last spec of a bar → mid-fight spec dump →
        // restoration cycle) could never get confirmed: the restoration
        // sequence (PREPARING_RESTORATION_CYCLE → USING_INITIAL_SPECS →
        // TELEPORTING_TO_HOUSE → ENTERING_FRIEND_HOUSE → USING_ORNATE_POOL
        // → TELEPORTING_BACK_TO_CORP) takes ~30 seconds, and the spec's
        // 5s deadline expired before the bot returned to FIGHTING_CORP.
        // 1.9.99.11's strict deadline check then marked it as miss even
        // though the signal arrived within the deadline. User: "two of
        // our elder maul specs hit back to back and only the first one
        // counted".
        processPendingSpecHit();

        switch (currentState) {
            case STARTING:
                handleStarting();
                break;
            case DEATH_RECOVERY:
                handleDeathRecovery();
                break;
            case W330_RESTORATION:
                handleW330Restoration();
                break;
            case BANKING_AND_HEALING:
                handleBankingAndHealing();
                break;
			case PREPARING_RESTORATION_CYCLE:
				handlePreparingRestorationCycle();
				break;
			case USING_INITIAL_SPECS:
				handleUsingInitialSpecs();
				break;
			case TELEPORTING_TO_HOUSE:
				handleTeleportingToHouse();
				break;
			case ENTERING_FRIEND_HOUSE:
				handleEnteringFriendHouse();
				break;
			case USING_ORNATE_POOL:
				handleUsingOrnatePool();
				break;
			case TELEPORTING_BACK_TO_CORP:
				handleTeleportingBackToCorp();
				break;
            case TRAVELING_TO_CORP:
                handleTravelingToCorp();
                break;
            case WAITING_FOR_TEAM:
                handleWaitingForTeam();
                break;
            case ENTERING_COMBAT:
                handleEnteringCombat();
                break;
            case FIGHTING_CORP:
                handleFightingCorp();
                break;
            case HANDLING_DARK_CORE:
                handleAdvancedDarkCore();
                break;
            case USING_SPECIAL_ATTACK:
                handleSpecialAttack();
                break;
            case LOOTING:
                handleLooting();
                break;
            case EMERGENCY_ESCAPE:
                handleEmergencyEscape();
                break;
        }
    }

	private boolean initialStaggerApplied = false;

	private void handleStarting() {
		Log.info("Initializing bot with simplified POH restoration...");

		// 1.9.99.137: multi-bot startup stagger. Wait once at script start
		// before doing anything. Each bot's profile gets a different
		// initialTripStaggerSec value (e.g., 0/10/20 for 3 bots). With
		// ~30s POH cycles, a 10s offset keeps 2/3 bots continuously
		// attacking Corp — minimizes HP regen between spec dumps.
		// Fires once per script start (flag prevents re-stagger on
		// state reload via handleStarting re-entry).
		if (!initialStaggerApplied) {
			initialStaggerApplied = true;
			int staggerSec = settings != null ? settings.initialTripStaggerSec : 0;
			if (staggerSec > 0) {
				Log.info("Initial trip stagger: waiting " + staggerSec
						+ "s before starting (multi-bot offset)");
				Waiting.wait(staggerSec * 1000);
			}
		}

		detectAndSetSpecWeapon();
		resetTripTracking();

		if (!hasRequiredItemsWithPOH()) {
			Log.info("Missing required items, going to Ferox Enclave");
			currentState = BotState.BANKING_AND_HEALING;
			return;
		}

		// 1.9.99.17: at startup, restore prayer/spec to full BEFORE traveling
		// to Corp. Pre-1.9.99.17 the bot went straight to Corp regardless of
		// stat state — if the user started the script after a disconnect /
		// pause / re-login with prayer or spec depleted, the bot entered
		// combat without resources and immediately needed an emergency eat
		// or escape. Now: if either is below full, route through restoration
		// FIRST. hasRequiredItemsWithPOH already guarantees we have house
		// tabs + pots so PREPARING_RESTORATION_CYCLE will succeed. User:
		// "if we start the script outside our friends poh but our prayer
		// or spec isnt already 100 we start the trip without attempting
		// to restore anything and just go straight to the boss".
		int currentPrayer = Prayer.getPrayerPoints();
		int prayerMax = Skill.PRAYER.getActualLevel();
		int currentSpec = Combat.getSpecialAttackPercent();
		int currentHp = MyPlayer.getCurrentHealth();
		int hpMax = Skill.HITPOINTS.getActualLevel();
		// 1.9.99.45: if we start INSIDE a POH (own or friend's), always use
		// the jewellery box to teleport to Corp — never burn a Games
		// necklace charge when we're already inside a house with a box.
		// If resources are low we drink the pool first (USING_ORNATE_POOL
		// handles both); if resources are full we skip straight to the
		// jewellery-box tele. isInOwnHouse() = "any POH with a drinkable
		// pool" (action-based detection). User: "Also if we star tina
		// house we can use the ornate pool -> jewelery box isntead of
		// using an amulet. This insures we are topped off first" +
		// "i started in house with full hp and full spec but it didnt
		// use the ornate jelwlery box and used our jelwery instead".
		// 1.9.99.46: route REGARDLESS of resources — full hp/spec just
		// skips the drink and goes straight to box.
		boolean lowResources = currentPrayer < prayerMax || currentSpec < 100 || currentHp < hpMax;
		if (isInOwnHouse()) {
			if (lowResources) {
				Log.info("Startup: already inside a POH at " + currentHp + "/" + hpMax
						+ " hp, " + currentPrayer + "/" + prayerMax + " prayer, " + currentSpec
						+ "% spec — drinking pool + jewellery-box to Corp");
				currentState = BotState.USING_ORNATE_POOL;
			} else {
				Log.info("Startup: already inside a POH at full resources "
						+ "— skipping pool, jewellery-box to Corp");
				currentState = BotState.TELEPORTING_BACK_TO_CORP;
			}
			return;
		}
		// 1.9.99.35: include HP in the startup restore gate. Pre-1.9.99.35
		// the check only covered prayer/spec — so if the user started
		// the script at HP 58/99 (e.g. left over from prior play), the
		// bot would go straight to Corp at 58 and the pre-combat eat at
		// L8832 (since removed in 1.9.99.34) would fire instantly. Root
		// cause of user's "eat at HP 58" complaint: the bot inherited
		// the player's pre-script HP and never restored. Now: route
		// through POH ornate pool if HP is below max too.
		if (currentPrayer < prayerMax || currentSpec < 100 || currentHp < hpMax) {
			// 1.9.99.44: FEROX_ONLY users have no POH access — route them
			// through BANKING_AND_HEALING (which uses the Ferox pool of
			// refreshment + bank) instead of PREPARING_RESTORATION_CYCLE
			// (which assumes a POH). Pre-1.9.99.44 a FEROX_ONLY startup
			// with low resources would enter PREPARING_RESTORATION_CYCLE,
			// fail to find a friend's POH, and either loop or bail to
			// emergency escape. Codex audit.
			if (isFeroxOnlyMode()) {
				Log.info("Startup (FEROX_ONLY): prayer " + currentPrayer + "/" + prayerMax
						+ ", spec " + currentSpec + "%, hp " + currentHp + "/" + hpMax
						+ " — routing through Ferox banking + pool");
				currentState = BotState.BANKING_AND_HEALING;
				return;
			}
			Log.info("Startup: prayer " + currentPrayer + "/" + prayerMax
					+ ", spec " + currentSpec + "%, hp " + currentHp + "/" + hpMax
					+ " — running restoration cycle before travel to Corp");
			currentState = BotState.PREPARING_RESTORATION_CYCLE;
			return;
		}

		if (isAtCorp()) {
			Log.info("Already at Corp, checking if we need restoration cycles");

			if (shouldStartRestorationCycle()) {
				Log.info("Starting POH restoration cycles before combat");
				currentState = BotState.PREPARING_RESTORATION_CYCLE;
			} else {
				Log.info("No restoration needed, waiting for team");
				currentState = BotState.WAITING_FOR_TEAM;
			}
			return;
		}

		Log.info("Ready to travel to Corp with spec weapon: " + chosenSpecWeapon);
		currentState = BotState.TRAVELING_TO_CORP;
	}


// ========== UTILITY METHOD FOR AREA CHECKING ==========

    /**
     * Check if our chosen spec weapon is equipped
     */
    private boolean isSpecWeaponEquipped() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

        // 1.9.58.1: dropped the per-call log. This is called every tick
        // (often multiple times per tick) and was spamming the log.
        return Equipment.contains(chosenSpecWeapon);
    }

    /**
     * Check if we have our chosen spec weapon in inventory
     */
    private boolean hasSpecWeaponInInventory() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

        boolean hasIt = Inventory.contains(chosenSpecWeapon);
        if (hasIt) {
            Log.debug("Chosen spec weapon in inventory: " + chosenSpecWeapon);
        }
        return hasIt;
    }

    /**
     * Check if we have our chosen spec weapon anywhere (inventory or equipped)
     */
    private boolean hasSpecWeapon() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

        return Inventory.contains(chosenSpecWeapon) || Equipment.contains(chosenSpecWeapon);
    }

    /** True if ANY owned spec weapon is present (inventory or equipment).
     *  Drives the pre-trip required-items check so DWH-only / BGS-only
     *  setups aren't rejected for missing Elder maul. Uses the auto-detected
     *  list as authoritative. */
    private boolean hasAnyOwnedSpecWeapon() {
        for (String w : getOwnedSpecWeapons()) {
            if (Inventory.contains(w) || Equipment.contains(w)) return true;
        }
        return false;
    }

    // ========== POH SOURCE HELPERS ==========

    /** The active POH source, normalized to one of POH_SOURCE_*. Falls back to
     *  OWN_HOUSE so a fresh install behaves like the previous default. */
    private String getPohSource() {
        if (settings == null) return POH_SOURCE_OWN_HOUSE;
        String src = settings.pohSource;
        if (src == null || src.isEmpty()) {
            return settings.useOwnHouse ? POH_SOURCE_OWN_HOUSE : POH_SOURCE_FRIEND_HOUSE;
        }
        return src;
    }

    private boolean isOwnHouseMode()    { return POH_SOURCE_OWN_HOUSE.equals(getPohSource()); }
    private boolean isFriendHouseMode() { return POH_SOURCE_FRIEND_HOUSE.equals(getPohSource()); }
    private boolean isBotHostMode()     { return POH_SOURCE_BOT_HOST.equals(getPohSource()); }
    private boolean isFeroxOnlyMode()   { return POH_SOURCE_FEROX_ONLY.equals(getPohSource()); }

    /** True if our POH flow needs to enter someone else's house via the
     *  friend's-house portal dialog (friend mode or bot-host mode). */
    private boolean needsFriendHousePortal() {
        return isFriendHouseMode() || isBotHostMode();
    }

    /** Configured combat potion family ("Divine super combat" /
     *  "Super combat" / etc). Always non-null, even on fresh installs. */
    private String getCombatPotionType() {
        if (settings == null || settings.combatPotionType == null
                || settings.combatPotionType.trim().isEmpty()) {
            return "Divine super combat";
        }
        return settings.combatPotionType.trim();
    }

    /** Build the (4)/(3)/(2)/(1) dose-suffixed item-name array for the
     *  user's chosen combat potion family. */
    private String[] getCombatPotionNames() {
        String type = getCombatPotionType();
        return new String[]{
                type + " potion(4)",
                type + " potion(3)",
                type + " potion(2)",
                type + " potion(1)"
        };
    }

    /** True if our inventory holds at least minFoodCount items across the
     *  configured food types. Replaces the hardcoded Shark+Karambwan check. */
    private boolean hasMinimumFood() {
        if (settings == null || settings.foodNames == null || settings.foodNames.length == 0) {
            return false;
        }
        int total = Inventory.getCount(settings.foodNames);
        return total >= INTERNAL_MIN_FOOD_COUNT;
    }

    // ========== TEAMMATE MANAGEMENT METHODS ==========

    /**
     * Equip our chosen spec weapon
     */
    /** Spec weapons that take up both hands. Wielding one of these from a
     *  1H+defender setup pushes BOTH the previous weapon and the defender
     *  into inventory, so we need 2 free slots before the swap. */
    private static final java.util.Set<String> TWO_HANDED_SPEC_WEAPONS = new java.util.HashSet<>(Arrays.asList(
            "Elder maul",
            // 1.9.99.166: Dragon warhammer REMOVED — it's 1H (defender
            // can go in offhand). Pre-1.9.99.166 it was incorrectly
            // listed here, which meant the swap-to-DWH path freed 2
            // slots (assuming weapon + defender both displaced) and
            // skipped re-equipping the defender after the spec. With
            // DWH correctly tagged 1H, defender stays equipped during
            // the swap and the post-swap re-equip uses the normal 1H
            // path. User: "dragon warhammer is 1h not 2h which means
            // we can use defender".
            "Bandos godsword",
            "Crystal halberd",
            "Dragon halberd"
    ));

    private boolean isTwoHandedSpec(String weaponName) {
        return weaponName != null && TWO_HANDED_SPEC_WEAPONS.contains(weaponName);
    }

    /** Eat a karambwan to free one inventory slot. Karambwan eats over the
     *  food cooldown so it's safe to call right after a normal food eat.
     *  Success = item count actually decreased (NOT just "inventory not full",
     *  which returns immediately when there was already a free slot). */
    private boolean comboEatToFreeSlot() {
        Optional<InventoryItem> kara = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (!kara.isPresent()) return false;
        int countBefore = Query.inventory().count();
        Log.info("Combo-eating Cooked karambwan to free inventory slot");
        if (kara.get().click("Eat")) {
            return Waiting.waitUntil(2000, () -> Query.inventory().count() < countBefore);
        }
        return false;
    }

    /** Eat a primary food (from settings.foodNames) to free one slot. Same
     *  count-based success criterion as comboEatToFreeSlot. */
    private boolean eatPrimaryFoodToFreeSlot() {
        if (settings.foodNames == null) return false;
        for (String f : settings.foodNames) {
            if ("Cooked karambwan".equalsIgnoreCase(f)) continue; // handled by combo path
            Optional<InventoryItem> food = Query.inventory().nameEquals(f).findFirst();
            if (!food.isPresent()) continue;
            int countBefore = Query.inventory().count();
            if (food.get().click("Eat")) {
                if (Waiting.waitUntil(2000, () -> Query.inventory().count() < countBefore)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Ensure at least `needed` free inventory slots. Eats food until the
     *  target is reached or we run out (safety cap = 28 attempts to avoid an
     *  infinite loop on a stuck eat). Previously this looped only `toFree`
     *  times, exited too early when a karambwan eat's success check fired
     *  on stale state, and left us 1 slot short of the requirement. */
    private boolean ensureInventorySlotsFree(int needed) {
        int safety = 28;
        while (safety-- > 0 && (28 - Query.inventory().count()) < needed) {
            if (comboEatToFreeSlot()) continue;
            if (eatPrimaryFoodToFreeSlot()) continue;
            break; // out of food
        }
        return (28 - Query.inventory().count()) >= needed;
    }

    private boolean equipSpecWeapon() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

		if (isSpecWeaponEquipped()) {
			Log.debug("Spec weapon already equipped: " + chosenSpecWeapon);
			return true;
		}

		// 2H spec weapon → wielding it sends both current weapon AND defender
		// back to inventory. Make sure we have room first, or the wield fails.
		if (isTwoHandedSpec(chosenSpecWeapon)) {
			int slotsNeeded = hasDefenderEquipped() ? 2 : 1;
			if (!ensureInventorySlotsFree(slotsNeeded)) {
				Log.warn("Cannot free " + slotsNeeded + " slots for 2H swap to "
						+ chosenSpecWeapon + " — skipping spec this attempt");
				return false;
			}
		}

		// 1.9.32: retry the wield up to 2 times if the verification times
		// out. Pre-1.9.32 a single failed wield returned false and the
		// caller couldn't recover — bot ended up in FIGHTING_CORP with
		// the wrong weapon, never re-attempting the swap.
		final int MAX_WIELD_ATTEMPTS = 2;
		boolean success = false;
		for (int attempt = 1; attempt <= MAX_WIELD_ATTEMPTS && !success; attempt++) {
			Optional<InventoryItem> specWeaponOpt = Query.inventory()
					.nameEquals(chosenSpecWeapon).findFirst();
			if (!specWeaponOpt.isPresent()) {
				Log.error("Spec weapon not found in inventory: " + chosenSpecWeapon);
				return false;
			}
			if (specWeaponOpt.get().click("Wield")) {
				// 1.9.99.191: HP-aware wait. Wielding the spec weapon often
				// runs in the boss room where Corp is hitting; pre-fix
				// the 3s blocking wait was a critical-HP window.
				success = waitUntilHpSafe(3000, () -> isSpecWeaponEquipped());
				if (!success && attempt < MAX_WIELD_ATTEMPTS) {
					Log.warn("Spec weapon wield attempt " + attempt + "/"
							+ MAX_WIELD_ATTEMPTS + " timed out — retrying");
					Waiting.waitNormal(400, 150);
				}
			}
		}
		if (success) {
			Log.info("Successfully equipped spec weapon: " + chosenSpecWeapon);
			// 1.9.99.20: weapon swap toggles the spec bar OFF. Invalidate the
			// settle-window timestamp so the next tryActivateSpec actually
			// CLICKS the bar instead of short-circuiting on the recent
			// pre-activate's lastSpecActivateAt. User: "the issue with
			// enabling the spec and double clicking it still exists" —
			// caused by tryActivateSpec returning true via settle without
			// re-clicking after a weapon swap killed the bar state.
			lastSpecActivateAt = 0;
			// 1.9.99.148: when main is 2H (spear), skip the defender add
			// even on a 1H spec — keeping a defender out of the rotation
			// means the swap-back to 2H spear only needs 1 free slot
			// instead of 2, and the typical spear-budget setup doesn't
			// carry a defender anyway.
			if (!isTwoHandedSpec(chosenSpecWeapon)
					&& !isMainWeaponTwoHanded()
					&& !hasDefenderEquipped()) {
				Log.info("1H spec weapon equipped — adding defender");
				equipAnyDefender();
			}
		} else {
			Log.error("Failed to equip spec weapon after " + MAX_WIELD_ATTEMPTS
					+ " attempts: " + chosenSpecWeapon);
		}
		return success;
    }

    /**
     * Combined banking and healing at Ferox Enclave
     */
    private void handleBankingAndHealing() {
        Log.info("Going to Ferox Enclave for banking and healing...");
        // 1.9.99.183: re-check for rune pouch each bank trip. Pre-1.9.99.183
        // the absent-flag latched for the whole session, so buying or
        // re-equipping a pouch wouldn't be detected until script restart.
        // Audit MEDIUM #13.
        runePouchKnownAbsent = false;

        // Step 1: Get to Ferox Enclave
        if (!isAtFeroxEnclave()) {
            if (teleportToFeroxEnclave()) {
                if (!Waiting.waitUntil(8000, () -> isAtFeroxEnclave())) {
                    Log.error("Failed to reach Ferox Enclave");
                    return;
                }
            } else {
                Log.error("Failed to teleport to Ferox Enclave");
                return;
            }
        }

        // 1.9.58: walk to the central Ferox tile (3135, 3630) FIRST,
        // then interact with pool/bank. User: 'i noticed i was trying
        // to click on the pool in ferox before moving to the tile that
        // we want to be in so we are clicking on things that are loaded
        // in the game chunk but too far to be rendered in.' The Query
        // can find game objects by tile coordinate even when they
        // aren't on-screen, but interact() needs the object's bounding
        // rect to be visible. Walking first guarantees both pool and
        // bank chest are in render distance.
        if (!isNearFeroxBank()) {
            walkToFeroxBank();
            return;
        }

        // Step 2: Use pool to restore health/prayer if needed
        if (needsPoolRestoration()) {
            if (useRestorePool()) {
                Log.info("Successfully used restoration pool");
                // 1.9.99.16: wait for the drink animation to finish before
                // moving to the bank-chest click. Pre-1.9.99.16 the bank
                // interact at L2632 fired the same tick as the pool drink
                // (or one tick later, while player still animating), and
                // the bank-open wait at L2643 always hit its 10s timeout
                // because the player was animation-locked. User: "we try
                // to click the bank at ferox the same tick we use the
                // pool so we are already locked in an animation so we
                // hit the timeout every single time".
                Waiting.waitUntil(2500, () -> !MyPlayer.isAnimating());
            } else {
                Log.warn("Failed to use pool, continuing to banking");
            }
        }

        // 1.9.86: bank-open check by widget content as backup. User:
        // 'it was still hovering trying to click on the bank but it
        // wasnt clickable because the bank screen was already open
        // so it got stuck hovering over it over and over.' Bank.isOpen()
        // returns false even when the bank UI is visibly open on this
        // client. The interact() click then hovers over the chest
        // beneath the open UI (unclickable). Detect bank-open by
        // searching for the standard 'Bank of RuneScape' / 'The Bank'
        // header text under any chatbox/bank-related root — visible
        // when the bank interface is up.
        // 1.9.89: reverted to Bank.isOpen() alone. User: 'Bank.isOpen
        // usually works pretty well.' The 1.9.86/1.9.88 widget-text
        // fallback was adding false-positive 'bank is open' paths
        // that skipped the chest-click step when they shouldn't have.
        // Step 4: Open bank
        if (!Bank.isOpen()) {
            // 1.9.14: settle delay before clicking the bank chest.
            Waiting.waitNormal(700, 200);
            // Try to find and left-click bank chest specifically
            Optional<GameObject> bankChestOpt = Query.gameObjects()
                    .nameContains("Bank chest")
                    .findFirst();

            if (bankChestOpt.isPresent()) {
                GameObject bankChest = bankChestOpt.get();
                Log.info("Left-clicking bank chest");
                if (bankChest.interact("Use") || bankChest.interact("Bank")) {
                    // 1.9.85: longer wait (10s) + post-timeout grace check.
                    // User: 'we are trying to open the bank even though we
                    // already have the bank open.' Bank.isOpen() can lag
                    // 1-3 seconds behind the actual open state on slow
                    // ticks — pre-1.9.85's 6s timeout would expire, the
                    // function would return, and the next iteration
                    // click the chest AGAIN which CLOSES the now-open
                    // bank (right-click style toggle). Now: 10s wait,
                    // then one more poll after a 1s settle before
                    // declaring failure.
                    if (!Waiting.waitUntil(10000, () -> Bank.isOpen())) {
                        Waiting.waitNormal(1000, 200);
                        if (Bank.isOpen()) {
                            Log.info("Bank opened (caught after 10s timeout window)");
                        } else {
                            Log.error("Failed to open bank via bank chest");
                            return;
                        }
                    }
                }
            } else if (Bank.open()) {
                // Fallback to generic bank opening
                if (!Waiting.waitUntil(3000, () -> Bank.isOpen())) {
                    Log.error("Failed to open bank");
                    return;
                }
            } else {
                Log.error("Could not find bank chest or open bank");
                return;
            }
        }

        // Step 5: Deposit all except keep items
        if (!depositAllExceptKeepItems()) {
            Log.error("Failed to deposit items");
            return;
        }

        // Step 6: Withdraw required items
        if (!withdrawBankingItems()) {
            Log.error("Failed to withdraw required items - checking if we have minimum supplies");

            if (hasMinimumSupplies()) {
                Log.warn("Continuing with minimum supplies");
                bankWithdrawFailureStrikes = 0; // 1.9.99.44: reset on partial recovery
            } else {
                // 1.9.88: don't auto-logout on first failure. User log:
                // bot drank pool, banking withdrawal called WITHOUT
                // first opening the bank (false-positive bank-open
                // check), Bank.withdraw returned false on all items,
                // script declared "insufficient supplies" and logged
                // out — but bank actually had supplies. Now: log a
                // diagnostic with bank counts, then RETURN (don't
                // stop). Next handleBankingAndHealing tick will retry
                // the open + withdraw. Only stop if the failure
                // persists across multiple consecutive ticks.
                // 1.9.99.44: implement the "tripFailureStrikes" the 1.9.88
                // comment promised. Codex audit: pre-1.9.99.44 this path
                // retried indefinitely. After
                // INTERNAL_BANK_FAILURE_STRIKES consecutive failures we
                // signal session-end and stop cleanly — better than
                // looping forever on a broken bank state or empty stock.
                int sharks = 0, karambwans = 0, ringDose = 0;
                try {
                    sharks = Bank.getCount("Shark");
                    karambwans = Bank.getCount("Cooked karambwan");
                    for (int d = 8; d >= 1; d--) {
                        if (Bank.getCount("Ring of dueling(" + d + ")") > 0) {
                            ringDose = d;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                bankWithdrawFailureStrikes++;
                Log.warn("Banking withdrawal failed. Bank counts: "
                        + "Shark=" + sharks + ", Karambwan=" + karambwans
                        + ", Ring=" + ringDose + " (strike "
                        + bankWithdrawFailureStrikes + "/"
                        + INTERNAL_BANK_FAILURE_STRIKES + ")");
                if (bankWithdrawFailureStrikes >= INTERNAL_BANK_FAILURE_STRIKES) {
                    Log.error("Bank withdrawal failed " + bankWithdrawFailureStrikes
                            + " times consecutively — signalling session end and stopping");
                    signalSessionEnd("bank-withdraw-failure");
                    running = false;
                    return;
                }
                // Close the bank UI if it's still open so the retry
                // starts clean.
                try { if (Bank.isOpen()) Bank.close(); } catch (Exception ignored) {}
                return;
            }
        } else {
            bankWithdrawFailureStrikes = 0; // 1.9.99.44: reset on success
        }

        // Pre-trip gear verification. Catch silent withdraw-failures before
        // we leave the bank under-geared. Skipped in DEATH_RECOVERY flow which
        // has its own minimal requirements.
        if (!verifyTripGear()) {
            // 1.9.99.179: count strikes on verifyTripGear failures too.
            // Pre-1.9.99.179 only withdrawBankingItems strikes were
            // counted; verifyTripGear failures (e.g. bank stock empty of
            // necklaces / defenders / spec weapons) yielded an infinite
            // retry loop because withdrawEssentialItems always returns
            // true regardless of per-item failure. Bot burned tabs/teles
            // forever with no session-end. Now: same INTERNAL_BANK_FAILURE_STRIKES
            // threshold for both withdraw failures AND gear-check
            // failures.
            bankWithdrawFailureStrikes++;
            Log.error("Pre-trip gear check failed — strike "
                    + bankWithdrawFailureStrikes + "/" + INTERNAL_BANK_FAILURE_STRIKES);
            if (bankWithdrawFailureStrikes >= INTERNAL_BANK_FAILURE_STRIKES) {
                Log.error("Pre-trip gear check failed " + bankWithdrawFailureStrikes
                        + " times consecutively — signalling session end and stopping");
                signalSessionEnd("gear-verification-failure");
                running = false;
                return;
            }
            return;
        }
        // Gear verified — clear strikes for the next trip's banking cycle.
        bankWithdrawFailureStrikes = 0;

        // Only proceed if we successfully got supplies
        Bank.close();
        Waiting.waitUntil(2000, () -> !Bank.isOpen());
        resetTripTracking();
        // Bank trip changed our inventory — re-scan owned spec weapons on next access.
        invalidateOwnedSpecWeaponsCache();

        // 1.9.99.126: after banking, if spec is still partial, route through
        // POH restoration before traveling to Corp. The Ferox pool drink in
        // needsPoolRestoration() only fires when HP/prayer are below
        // threshold (per 1.9.57 anti-loop fix) — it ignores spec. So a
        // user starting the script with full HP/prayer but partial spec
        // would bank, never trigger the pool, and head to Corp with the
        // bar still depleted. Fix: explicit spec check at bank exit.
        // Gated on house tabs + non-Ferox-only so we only POH if it's
        // actually usable. User: "it seems like if we start a trip
        // without full spec it still starts the fight without going to
        // poh."
        int specAfterBank = Combat.getSpecialAttackPercent();
        if (specAfterBank < 100 && hasHouseTeleportTab() && !isFeroxOnlyMode()) {
            Log.info("Banking complete, spec=" + specAfterBank
                    + "% — routing through POH for spec refresh before Corp");
            currentState = BotState.PREPARING_RESTORATION_CYCLE;
            return;
        }

        Log.info("Banking complete, ready to travel to Corp");
        currentState = BotState.TRAVELING_TO_CORP;
    }

    /** True if everything we need to fight Corp is present: main weapon
     *  (or its variants), at least one defender, at least one owned spec
     *  weapon, and the basic consumables. Logs the specific failure. */
    private boolean verifyTripGear() {
        boolean mainPresent = false;
        for (String v : getMainWeaponVariants()) {
            if (Inventory.contains(v) || Equipment.contains(v)) { mainPresent = true; break; }
        }
        if (!mainPresent) {
            Log.error("Trip gear: main weapon missing (" + settings.mainWeapon + ")");
            return false;
        }

        // 1.9.99.148: skip defender requirement entirely when main weapon
        // is 2H (Zamorakian spear / hasta). The 2H setup intentionally
        // forgoes a defender — gating the trip on its absence would
        // prevent spear users from ever leaving the bank.
        if (!isMainWeaponTwoHanded()) {
            boolean defenderPresent = false;
            for (String d : DEFENDER_PRIORITY) {
                if (Inventory.contains(d) || Equipment.contains(d)) { defenderPresent = true; break; }
            }
            if (!defenderPresent) {
                // 1.9.99.149: also accept Antler guard via isDefenderName.
                defenderPresent = Query.inventory()
                        .filter(it -> isDefenderName(it.getName())).isAny()
                        || Query.equipment()
                        .filter(it -> isDefenderName(it.getName())).isAny();
            }
            if (!defenderPresent) {
                Log.error("Trip gear: no defender present");
                return false;
            }
        }

        if (!hasAnyOwnedSpecWeapon()) {
            Log.error("Trip gear: no owned spec weapon present");
            return false;
        }

        if (!hasChargedGamesNecklace()) {
            Log.error("Trip gear: no charged Games necklace");
            return false;
        }
        if (!hasChargedRingOfDueling()) {
            Log.error("Trip gear: no charged Ring of dueling");
            return false;
        }

        if (!hasMinimumFood()) {
            Log.error("Trip gear: food below minFoodCount=" + INTERNAL_MIN_FOOD_COUNT);
            return false;
        }

        return true;
    }

	private void handleTravelingToCorp() {
		Log.info("Traveling to Corporeal Beast...");

		// 1.9.22: close the bank if it's still open. Clicking Games
		// necklace "Corporeal Beast" while bank is open opens a
		// quantity-input prompt instead of teleporting (Bank intercepts
		// inventory item-clicks). Pre-1.9.22 we'd just silently fail to
		// tele and end up stuck at Ferox.
		if (Bank.isOpen()) {
			Log.info("Bank still open — closing before tele to Corp");
			Bank.close();
			Waiting.waitUntil(2000, () -> !Bank.isOpen());
		}

		Optional<InventoryItem> necklaceOpt = Query.inventory().nameContains("Games necklace(").findFirst();
		if (!necklaceOpt.isPresent()) {
			Log.error("No Games Necklace found!");
			currentState = BotState.BANKING_AND_HEALING;
			return;
		}

		InventoryItem necklace = necklaceOpt.get();
		if (necklace.click("Corporeal Beast")) {
			if (Waiting.waitUntil(10000, () -> isAtCorp())) {
				Log.info("Successfully arrived at Corp");

				if (shouldStartRestorationCycle()) {
					Log.info("Starting POH restoration cycles after arrival");
					currentState = BotState.PREPARING_RESTORATION_CYCLE;
				} else {
					Log.info("No restoration needed after arrival, waiting for team");
					currentState = BotState.WAITING_FOR_TEAM;
				}
			} else {
				Log.error("Failed to teleport to Corp - retrying");
			}
		} else {
			Log.error("Failed to click Games Necklace");
		}
	}

    /** Initial spec-weapon detection. Iterates the auto-detected owned set
     *  in phase order so trip-start picks Phase 1 first, then 2, then 3. */
    private void detectAndSetSpecWeapon() {
        if (chosenSpecWeapon != null) {
            return; // Already detected
        }

        // Phase-ordered preference: defence drain first, combat drain second, then BGS / halberds.
        String[] preferenceOrder = {
                "Elder maul", "Dragon warhammer",              // Phase 1
                "Emberlight", "Arclight", "Darklight",         // Phase 2
                "Bandos godsword",                              // Phase 3
                "Crystal halberd", "Dragon halberd"             // Auxiliary
        };

        List<String> owned = getOwnedSpecWeapons();

        // First pass: weapons present right now (inv or equipped) AND in our owned list.
        for (String name : preferenceOrder) {
            if (!owned.contains(name)) continue;
            if (Inventory.contains(name) || Equipment.contains(name)) {
                chosenSpecWeapon = name;
                Log.info("Detected spec weapon: " + chosenSpecWeapon);
                return;
            }
        }

        // Fallback: anything we happen to be holding even if not in owned list yet
        // (e.g., bank-only weapon we just withdrew but cache is stale).
        for (String name : preferenceOrder) {
            if (Inventory.contains(name) || Equipment.contains(name)) {
                chosenSpecWeapon = name;
                Log.info("Detected spec weapon (fallback): " + chosenSpecWeapon);
                return;
            }
        }

        Log.error("No spec weapon found in inventory or equipment - please equip one before starting");
        // 1.9.90: do NOT silently fall back to ELDER_MAUL when not owned. Stale default
        // sent the bot into a fight planning to swap to a weapon it doesn't have.
        // Downstream usages already null-check chosenSpecWeapon (e.g. shouldUseSpecialAttack).
        chosenSpecWeapon = null;
        Log.warn("chosenSpecWeapon left null — spec phases will be skipped this trip");
    }

    private long lastWaitingForTeamLogAt = 0; // 1.9.99.200: log throttle
    private void handleWaitingForTeam() {
        // 1.9.99.200: throttle to 1 line per 5s. Pre-throttle this spammed
        // ~10 lines/sec while waiting for teammates, drowning everything
        // else in the log. The actual state isn't changing fast enough to
        // warrant per-tick logging.
        long nowWft = System.currentTimeMillis();
        boolean shouldLogWft = nowWft - lastWaitingForTeamLogAt > 5000;
        if (shouldLogWft) {
            lastWaitingForTeamLogAt = nowWft;
            Log.info("Waiting for team in lobby area...");
        }

        // 1.8.8: between-kills POH restoration check. Pre-1.8.8 the only
        // callers of shouldStartRestorationCycle() were arrival handlers, so
        // a bot that finished kill 1 with depleted spec would just go straight
        // into kill 2 with 0%. Run it before ENTERING_COMBAT so a depleted
        // bar can trigger a POH tele between kills.
        if (shouldStartRestorationCycle()) {
            Log.info("Spec depleted between kills - starting POH restoration cycle");
            currentState = BotState.PREPARING_RESTORATION_CYCLE;
            return;
        }

        // 1.9.15: removed handleVengeanceLogic() and prepareSpecWeaponInLobby()
        // from this state. Pre-1.9.15 the bot would veng + drink pot +
        // eat karambwan + equip spec + pre-activate spec while just standing
        // in the lobby waiting for teammates to join. Veng heals get burned
        // off before we engage Corp, the prep happens too early (might be
        // 30+ seconds before combat), and we kept failing the veng cast
        // anyway (wrong widget). The new flow defers all prep to
        // handleEnteringCombat which runs WHILE walking to the boss room,
        // so spec/prayer/veng all activate as we approach Corp.

        // 1.9.99.151: REMOVED the "teammates in boss room → commit" early-
        // return. User: "a teammate in room doesnt mean we should commit
        // thats rediculous. We either need to hit our goals or corp needs
        // to be at the hp threshold." Commit is now driven by isInKillPhase()
        // (teamPhaseNeeded == 0 OR Corp HP < corpMinHpForSpec) inside
        // shouldStartRestorationCycle / shouldUseSpecialAttack, not by a
        // teammate's presence. A teammate just standing in the boss room
        // shouldn't override our spec-dump goal — only an actual kill in
        // progress (which shows up as Corp HP dropping) should.

        // Check if we have at least one acceptable teammate in lobby
        if (hasAcceptableTeammatesInLobby()) {
            Log.info("Acceptable teammates in lobby, ready to enter together");
            currentState = BotState.ENTERING_COMBAT; // This will handle moving to boss room
            return;
        }

        // 1.9.27: solo engage. If Corp is alive and visible (we're already
        // in the boss room with no teammate around — e.g. teammate banking
        // or in their own POH), engage solo.
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent() && isCorpAlive(corpOpt.get())) {
            Log.info("Corp visible and alive — engaging solo (no teammates around)");
            currentState = BotState.ENTERING_COMBAT;
            return;
        }

        // 1.9.32: walk into boss room from lobby. Pre-1.9.32 the bot
        // would idle in the lobby forever when no teammates were
        // visible AND Corp wasn't visible from the lobby (Corp isn't
        // visible from outside the passage). Now: walk through the
        // passage to check. handleEnteringCombat will engage if Corp
        // is alive, or fall through if Corp's dead/missing — at which
        // point we'll come back here and walk back into the lobby
        // loop, eventually triggering an emergency escape if all
        // gates fail.
        if (isInCorpLobby() && !isInCorpBossRoom()) {
            Log.info("No teammates visible from lobby — walking into boss room to check for Corp");
            currentState = BotState.ENTERING_COMBAT;
            return;
        }

        // STAY IN LOBBY - genuinely nothing to do
        // 1.9.99.200: same throttle as "Waiting for team" above.
        if (shouldLogWft) {
            Log.info("No acceptable teammates found AND Corp not visible — staying in lobby...");
        }
        Waiting.waitUntil(5000, () ->
                hasAcceptableTeammatesInLobby() ||
                        hasAcceptableTeammatesInBossRoom() ||
                        needsResupplyAfterKill()
        );

        // Check if we need supplies while waiting
        if (needsResupplyAfterKill()) {
            Log.info("Need supplies while waiting for team");
            currentState = BotState.BANKING_AND_HEALING;
        }
    }

    private void handleEnteringCombat() {
        Log.info("Entering combat...");

        // 1.9.32: also reset Corp HP-bar tracker at kill START, not just
        // at kill end (handleLooting). Stale max from a previous kill
        // could persist into a new kill if the bot died/respawned or
        // restoration completed without a kill in between, leading to
        // either premature "kill phase" detection or premature LOOTING.
        maxCorpHpPercentThisKill = 0.0;
        minCorpHpPercentThisKill = 1.0; // 1.9.99.181: low-water ratchet reset
        corpSeenAtZeroHp = false;
        // 1.9.99.108: clear the death-trace buffer so this kill's trace
        // is fresh. Frozen buffer from last kill held through LOOTING +
        // routing — user had that window to read it.
        clearDeathDiagBuffers();

        // 1.9.70: HP gate before walking into the boss room. Audit caught
        // this — after a death respawn or a barely-survived previous fight
        // the bot would walk in at 10-40 HP, take Corp's first magic+stomp
        // before PfM activated, die. Now: if HP < 40, divert to
        // BANKING_AND_HEALING for a quick Ferox pool drink. The pool
        // restores to full and we re-enter at safe HP. This single fix
        // probably eliminates the most common death cause we've seen.
        if (MyPlayer.getCurrentHealth() < 40) {
            Log.warn("Entering combat with HP " + MyPlayer.getCurrentHealth()
                    + " < 40 — diverting to bank/pool before engaging");
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // 1.9.99.82: dropped the proactive karam-low check entirely.
        // 1.9.99.73 added it ("if karams <= 2, bank before walking in"),
        // 1.9.99.81 gated it on currentRestorationCycle == 0 to stop
        // mid-kill bails — but the user prefers no proactive check at
        // all. The REACTIVE insta-tele in handleHealthAndPrayer (added
        // in 1.9.99.74-f) already covers the only case that matters:
        // if we hit emergency-eat threshold AND karams == 0, we tele
        // straight to Ferox via Ring of Dueling. That's enough.
        // Proactive banking at 2 karams wasted trips for situations
        // that may never have actually triggered a combo eat. User:
        // "If we need to get food its fine. We can just make it so if
        // we run OUT of emergency eat combo foods and require it we
        // force insta teleport to ferox to rebank. currently we are
        // rebanking if we get to 2 karmawans left."

        // 1.9.70: prayer ON before the walk, not after. Pre-1.9.70 we
        // kicked off moveToCorpBossRoom THEN activated PfM — the walk
        // starts immediately but PfM takes a tick to register, so Corp's
        // first magic hit landed against unprotected HP. Now prayer
        // setup happens FIRST, then the walk kicks off; PfM is live
        // before we're in render range of Corp.
        // 1.9.99.19: enable quick prayer, then settle before checking PFM.
        // Pre-1.9.99.19 we ALWAYS clicked PFM in the prayer tab right after
        // enabling quick prayers — even though quick prayer typically
        // already includes PFM, the .isEnabled() probe ran before the
        // SDK reflected the quick-prayer state and the bot redundantly
        // re-clicked PFM in the prayer tab. User: "we enable our prayers
        // with click prayers but then we go to our prayer tab to enable
        // the prayer thats already on. this seems redundant".
        if (!Prayer.isQuickPrayerEnabled()) {
            Log.info("Activating quick prayer (entering combat)");
            Prayer.enableQuickPrayer();
            Waiting.waitUntil(800, Prayer::isQuickPrayerEnabled);
        }
        try {
            // 1.9.99.186: verify PFM is actually on, fall back to manual
            // enable if quick prayer doesn't include it OR the SDK reports
            // quick-prayer on but PFM individually off. Walking into the
            // boss room without PFM = Corp's magic blast (50+ on no prayer)
            // killing the bot before any safety net runs. User: "our quick
            // prayer has protect from magic. we should try our quick
            // prayers first and then if that doesnt enable the protect
            // from magic spell we can manually enable it."
            if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
                // Brief wait: quick-prayer toggle takes 1 tick to propagate
                // to individual prayer .isEnabled() flags.
                Waiting.waitUntil(700, () -> Prayer.PROTECT_FROM_MAGIC.isEnabled());
            }
            if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
                Log.info("Activating Protect from Magic (entering combat — quick prayer didn't include it)");
                Prayer.PROTECT_FROM_MAGIC.enable();
                Waiting.waitUntil(700, () -> Prayer.PROTECT_FROM_MAGIC.isEnabled());
                if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
                    Log.warn("PFM still not enabled after fallback click — proceeding anyway");
                }
            }
        } catch (Exception ignored) {}

        // 1.9.16: kick off the walk FIRST, then do prep DURING the walk.
        // OSRS walks are server-side: once we've issued the click on the
        // passage, the player keeps walking even while we open inventory
        // and click items.
        // 1.9.70: but AFTER prayers, not before — see HP/prayer gate above.
        if (!isInCorpBossRoom()) {
            Log.info("Kicking off walk to boss room (prep runs concurrently)");
            moveToCorpBossRoom(); // fire-and-forget; we don't block on arrival
        }

        prepareSpecWeaponInLobby();
        // 1.9.17: removed veng cast from handleEnteringCombat. User said
        // "we still veng and do things we dont want to do until we actually
        // start killing it outside of spec dumping." Vengeance heals fade
        // during pre-engagement walk + spec dump anyway. The veng cast now
        // only fires during ACTIVE_CASTING (after we've taken damage in
        // real melee combat), gated by handleVengeanceLogic itself.

        // Now wait for arrival before engaging.
        if (!isInCorpBossRoom()) {
            // 1.9.99.191: HP-aware wait. If Corp's magic hit lands during the
            // passage transition (player is briefly visible to Corp before
            // entering the instance), bail to main loop instead of standing
            // through the 8s timeout.
            waitUntilHpSafe(8000, () -> isInCorpBossRoom());
            return;
        }

        // 1.9.99.131: lobby-stuck recovery. We just bounced from
        // FIGHTING_CORP-in-lobby (passage-click misfire). The bot is now
        // standing on or near the passage tile inside the cave. If we
        // click Corp from here, the click can hit the passage again →
        // teleport back → infinite loop. Walk deeper INTO the cave
        // first, to a tile far enough from the passage that no future
        // click can overlap it. Once we're deep, clear the flag.
        if (recoveringFromLobbyStuck) {
            WorldTile myT = MyPlayer.getTile();
            WorldTile deepTarget = deepCorpArea.getCenter();
            if (deepTarget != null) {
                if (myT == null || !deepCorpArea.contains(myT)) {
                    Log.info("Lobby-stuck recovery: walking to deep cave tile "
                            + deepTarget + " before engaging Corp");
                    LocalWalking.walkTo(deepTarget);
                    return; // skip rest of tick — give the walk time to start
                }
                // We're already in the deep area; safe to proceed.
                Log.info("Lobby-stuck recovery: now in deep cave area — clearing flag and engaging normally");
                recoveringFromLobbyStuck = false;
            } else {
                recoveringFromLobbyStuck = false; // defensive — don't loop forever
            }
        }

        // Track if we started with teammates
        if (hasAcceptableTeammatesNearby()) {
            startedFightingWithTeammates = true;
            fightStartTime = System.currentTimeMillis();
        }

        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent()) {
            Npc corp = corpOpt.get();
            Log.info("Corp visible! Preparing for combat...");

            // IMMEDIATE prayer activation now that Corp is visible.
            // 1.9.99.19: settle for the SDK to reflect the click before
            // other paths re-check Prayer state.
            if (!Prayer.isQuickPrayerEnabled()) {
                Log.info("IMMEDIATELY activating prayers - Corp is visible");
                Prayer.enableQuickPrayer();
                Waiting.waitUntil(800, Prayer::isQuickPrayerEnabled);
            }

            // Position correctly before engaging
            if (!isInGoodCorpPosition(corp)) {
                if (moveToNearestCorpPosition(corp)) {
                    Log.info("Moved to assigned Corp position");
                }
            }

            // Prepare spec weapon now that we can see Corp
            Log.info("Preparing spec weapon - Corp is visible and positioned");
            prepareSpecWeaponForCorp(corp);

            // Start combat. Even if Corp is already engaged with a teammate
            // (isNpcInCombat==true), OUR bot still needs to issue an Attack
            // so it actually does damage and triggers the pre-activated spec.
            // The pre-1.8.7 "joining" branch just set state without attacking,
            // leaving the bot standing while the spec sat queued.
            // 1.9.99.183: rate-limit attackCorpIfVisible re-tries in
            // ENTERING_COMBAT. On interact() failure we stay in this state
            // and the next tick re-calls handleEnteringCombat ~50ms later —
            // without a gate that's 20 attack clicks/second. Reuse the
            // existing 500ms reengage debounce. Audit LOW #17.
            long sinceLastAttackTry = System.currentTimeMillis() - lastCorpReengageClickAt;
            if (sinceLastAttackTry < CORP_REENGAGE_DEBOUNCE_MS) {
                return;
            }
            lastCorpReengageClickAt = System.currentTimeMillis();
            if (attackCorpIfVisible(corp)) {
                // 1.9.99.191: HP-aware wait. Corp is right there and attacking;
                // the 5s block was a window for it to land 1-2 hits with no
                // defense running. Bail to main loop if HP drops critical so
                // panic/emergency-eat can fire.
                if (waitUntilHpSafe(5000, () -> isPlayerInCombat())) {
                    Log.info(isNpcInCombat(corp) ? "Joined existing combat" : "Combat initiated successfully");
                    currentState = BotState.FIGHTING_CORP;
                } else {
                    Log.warn("Attack registered but isPlayerInCombat timed out — proceeding to FIGHTING_CORP anyway");
                    currentState = BotState.FIGHTING_CORP;
                }
            } else {
                Log.warn("corp.interact('Attack') failed during ENTERING_COMBAT");
            }
        } else {
            // Corp not visible - use improved movement toward deep area
            Log.info("Corp not visible, moving toward deep Corp area...");
            moveToDeepCorpPosition();

            // After movement attempt, check if Corp is now visible
            corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpOpt.isPresent()) {
                Log.info("Corp became visible during movement! Continuing to combat preparation...");
                // Don't return - let the method continue to next loop where Corp will be handled
            } else {
                // 1.9.99.110: removed the equip-spec-weapon-while-searching
                // block entirely. The original (pre-1.9.99.109) code called
                // equipElderMaul() which is a misnomer — it equips whatever
                // chosenSpecWeapon currently is. After last kill's phase 3
                // it was BGS; bot would re-equip BGS for the new kill and
                // start smacking Corp with BGS as a melee weapon. My
                // 1.9.99.109 fix re-picked by phase, equipping Elder maul,
                // but that ALSO isn't what the user wants. The intent:
                // if we were already on Fang (kill phase last kill, banked,
                // returning), continue with Fang. Don't equip ANY spec
                // weapon in this cave-fallback path. Lobby/POH prep paths
                // (prepareSpecWeaponInLobby, prepareSpecWeaponForCorp) own
                // the spec-weapon-equip decision when it actually matters;
                // mid-cave fallback equip just thrashes the loadout.
                // User: "we wouldnt want to equip an elder maul or bandos
                // godsword at all; we can skip equipping a spec weapon if
                // we are using our fang and then bank; we will continue to
                // use our fang even after banking."

                // Brief wait before trying again
                Waiting.waitUniform(1000, 2000);
            }
        }
    }

    // ========== IMPROVED DEEP CORP MOVEMENT ==========
    private void moveToDeepCorpPosition() {
        Log.info("Moving toward deep Corp area to find boss...");

        WorldTile currentPos = MyPlayer.getTile();

        // Check if we're already in the deep area
        if (deepCorpArea.contains(currentPos)) {
            Log.info("Already in deep Corp area, checking for Corp visibility");

            // Brief wait to check if Corp becomes visible from current position
            Waiting.waitUniform(500, 1000);

            if (Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst().isPresent()) {
                Log.info("Corp now visible from deep area position");
                return;
            }

            // If still not visible, try moving to center of area
            WorldTile centerOfArea = deepCorpArea.getCenter();
            if (currentPos.distanceTo(centerOfArea) > 2) {
                Log.info("Moving to center of deep area: " + centerOfArea);
                walkToPositionWithCorpCheck(centerOfArea);
            }
        } else {
            // Move toward the deep area
            WorldTile targetPosition = findBestEntryPointToDeepArea(currentPos);

            if (targetPosition != null) {
                Log.info("Moving toward deep Corp area via: " + targetPosition);
                walkToPositionWithCorpCheck(targetPosition);
            } else {
                Log.warn("Could not find path to deep Corp area");
            }
        }
    }

    /**
     * Walk to a position but constantly check for Corp visibility and break out immediately
     */
    private boolean walkToPositionWithCorpCheck(WorldTile targetPosition) {
        // 1.9.67: if Corp is already visible BEFORE the walk, skip the
        // minimap walk and click-Attack on Corp directly. User: 'when
        // running into the cave we click on the minimap and sometimes
        // that sends us too far and sends us running through his body.'
        // LocalWalking.walkTo to a distant tile triggers a minimap-style
        // pathfind that can route straight through Corp's hitbox; the
        // 'Corp visible! Breaking out' poll only triggers AFTER the
        // walk has already committed. NPC click-attack routes around
        // the hitbox safely.
        Optional<Npc> corpEarly = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpEarly.isPresent()) {
            Log.info("Corp visible before walk — corp.interact('Attack') "
                    + "instead of minimap walk to " + targetPosition);
            if (attackCorpIfVisible(corpEarly.get())) {
                // 1.9.99.191: HP-aware wait. Walking toward Corp with Corp
                // visible — Corp is attacking us during this 6s window.
                return waitUntilHpSafe(6000, () ->
                        isPlayerInCombat() || MyPlayer.isAnimating());
            }
        }

        Log.info("Walking to " + targetPosition + " while checking for Corp...");

        if (LocalWalking.walkTo(targetPosition)) {
            // Walk with frequent Corp visibility checks
            long startTime = System.currentTimeMillis();
            long maxWalkTime = 8000; // Maximum 8 seconds of walking

            while (System.currentTimeMillis() - startTime < maxWalkTime) {
                // PRIORITY CHECK: Corp becomes visible - immediately stop walking
                if (Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst().isPresent()) {
                    Log.info("CORP VISIBLE! Breaking out of movement to prepare for combat");
                    return true; // Success - Corp found
                }

                // Check if we've reached close enough to target
                WorldTile currentPos = MyPlayer.getTile();
                if (currentPos.distanceTo(targetPosition) <= 2) {
                    Log.info("Reached target position, Corp check complete");
                    break;
                }

                // Brief wait before next check
                Waiting.waitUniform(300, 600);
            }

            // Final Corp check after reaching position
            if (Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst().isPresent()) {
                Log.info("Corp visible after reaching target position");
                return true;
            }

            Log.info("Reached position but Corp still not visible");
            return false;
        } else {
            Log.error("Failed to initiate walking to target position");
            return false;
        }
    }

    /**
     * Find the best entry point to the deep Corp area based on current position
     */
    private WorldTile findBestEntryPointToDeepArea(WorldTile currentPos) {
        // Define multiple entry points around the deep area
        List<WorldTile> entryPoints = Arrays.asList(
                new WorldTile(2989, 4388, 2), // Northwest corner
                new WorldTile(2997, 4387, 2), // Northeast corner
                new WorldTile(2997, 4379, 2), // Southeast corner
                new WorldTile(2990, 4380, 2), // Southwest corner
                deepCorpArea.getCenter()       // Center of area
        );

        // Find closest entry point
        WorldTile bestEntry = entryPoints.stream()
                .min((pos1, pos2) -> Double.compare(
                        currentPos.distanceTo(pos1),
                        currentPos.distanceTo(pos2)
                ))
                .orElse(null);

        if (bestEntry != null) {
            Log.info("Best entry point to deep area: " + bestEntry +
                    " (distance: " + currentPos.distanceTo(bestEntry) + ")");
        }

        return bestEntry;
    }

    private WorldTile findBestDeepPosition(WorldTile currentPos, List<WorldTile> deepPositions) {
        // Get teammate positions to avoid crowding
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // Find the deepest position that's not occupied
        for (WorldTile position : deepPositions) {
            // Check if position is occupied by teammates
            boolean occupied = teammatePositions.stream()
                    .anyMatch(teammatePos -> teammatePos.distanceTo(position) <= 2);

            if (!occupied) {
                return position;
            }
        }

        // If all positions occupied, go to the first one anyway (can stack if needed)
        return deepPositions.get(0);
    }

    private boolean isCorpTargetingUs(Npc corp) {
		/*// Method 1: Check if Corp's target is our player
		if (corp.getTarget() != null && corp.getTarget().equals(MyPlayer.getReference())) {
			return true;
		}*/

        // Method 2: Check if Corp is interacting with us
        if (corp.isInteractingWithMe()) {
            return true;
        }

        // Method 3: Fallback - check if we're in melee range and Corp is facing us
        WorldTile myPos = MyPlayer.getTile();
        WorldTile corpPos = corp.getTile();
		// We're in melee range, assume we might be targeted
		return myPos.distanceTo(corpPos) <= 1;
	}

    /**
     * Check if we're close to the deep Corp area (within reasonable distance)
     */
    private boolean isNearDeepCorpArea() {
        WorldTile currentPos = MyPlayer.getTile();
        WorldTile areaCenter = deepCorpArea.getCenter();

        boolean nearArea = currentPos.distanceTo(areaCenter) <= 8;

        if (nearArea) {
            Log.info("Near deep Corp area (distance: " + currentPos.distanceTo(areaCenter) + ")");
        }

        return nearArea;
    }

    /**
     * Enhanced Corp detection specifically for deep area searching
     */
    private boolean isCorpVisibleInDeepArea() {
        // Check for Corp visibility with additional range
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();

        if (corpOpt.isPresent()) {
            Npc corp = corpOpt.get();
            WorldTile corpPos = corp.getTile();
            WorldTile myPos = MyPlayer.getTile();

            Log.info("Corp found at: " + corpPos + " (distance: " + myPos.distanceTo(corpPos) + ")");

            // Check if Corp is in the deep area or nearby
            if (deepCorpArea.contains(corpPos) || myPos.distanceTo(corpPos) <= 15) {
                Log.info("Corp is accessible from current position");
                return true;
            } else {
                Log.info("Corp visible but too far away");
                return false;
            }
        }

        return false;
    }

    private void handleProtectionPrayers() {
        // 1.9.33: no more prayer switching. User: "we want protect from
        // magic only." Pre-1.9.33 the bot toggled between Protect-Melee
        // and Protect-Magic based on whether Corp was targeting us — a
        // false economy because the wrong-prayer ticks let big hits
        // through. Always Protect from Magic now; eating handles the
        // melee chip damage.
        // 1.9.99.15: don't spam-click enable() when prayer points are 0.
        // The activation can't succeed without points; bot was logging
        // "Activating Protect from Magic" 3+ times per second mid-fight,
        // burning ticks between eating and speccing. If a pot is available,
        // drink it first; otherwise return silently so the L3315 bank-trip
        // check (prayer<=5 && doses==0) gets to fire. User: "tries to
        // enable prayer even though we have no prayer points constantly
        // during the fight inbetween trying to spec and eat and causes
        // deaths".
        int prayer = Prayer.getPrayerPoints();
        if (prayer <= 0) {
            if (getPrayerDoses() > 0) {
                drinkPrayerPotion();
                prayer = Prayer.getPrayerPoints();
            }
            if (prayer <= 0) return;
        }
        if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
            Log.info("Activating Protect from Magic");
            Prayer.PROTECT_FROM_MAGIC.enable();
        }
    }

    /** 1.9.99.196: diagnostic + safety-net for the "stuck in FIGHTING_CORP
     *  doing nothing" bug. Counts consecutive no-action ticks; if it exceeds
     *  threshold, force-equips a spec weapon and bounces back to
     *  ENTERING_COMBAT to fully re-prep. Also logs which decision point
     *  short-circuits each tick so the user/log can pinpoint stuck-state
     *  causes. */
    private int fightingCorpNoActionTicks = 0;
    private long lastFightingCorpDiagLogAt = 0;

    private void handleFightingCorp() {
        // 1.9.99.196: stuck-state recovery — if neither spec weapon nor
        // main weapon is equipped (panic retreat or some other path
        // unequipped both), prepareSpecWeaponForCorp won't get called
        // from this state's normal flow. Bot ends up "fighting" with
        // fists, dealing 0 damage, no DPS, infinite loop. Bounce to
        // ENTERING_COMBAT which calls prepareSpecWeaponForCorp.
        if (!isSpecWeaponEquipped() && !isMainWeaponEquipped()) {
            Log.warn("FIGHTING_CORP but neither spec weapon nor main weapon "
                    + "equipped — bouncing to ENTERING_COMBAT to re-prep");
            currentState = BotState.ENTERING_COMBAT;
            return;
        }
        // 1.9.99.97: detect transitions INTO FIGHTING_CORP from states
        // that warrant a position recheck — walking into the room
        // (ENTERING_COMBAT), returning from dark-core (HANDLING_DARK_CORE).
        // POH/banking returns route through ENTERING_COMBAT first, so
        // they're covered by that. Panic-retreat end sets the flag in
        // its own block. Mid-fight transitions (USING_SPECIAL_ATTACK
        // back to FIGHTING_CORP) do NOT trigger — we're still
        // positioned from before the spec.
        if (previousMainLoopState != null
                && previousMainLoopState != BotState.FIGHTING_CORP
                && previousMainLoopState != BotState.USING_SPECIAL_ATTACK
                && previousMainLoopState != BotState.USING_INITIAL_SPECS) {
            switch (previousMainLoopState) {
                case ENTERING_COMBAT:
                case HANDLING_DARK_CORE:
                    if (!needsRepositioning) {
                        Log.debug("Drift recheck re-armed: transition from "
                                + previousMainLoopState + " → FIGHTING_CORP");
                    }
                    needsRepositioning = true;
                    break;
                default:
                    break;
            }
        }
        previousMainLoopState = currentState;

        // 1.9.99.156: defensive stale-state reset. committedSpecPhase
        // ratchets UP only; reset lives in coordinatorOnKillEnded which
        // fires from handleLooting. If a kill ended without our bot
        // looting (someone else got loot, bot was in POH at death,
        // missed-tick on death detection), committedSpecPhase carries
        // into the next kill and pickSpecWeaponForCurrentPhase() returns
        // the WRONG phase weapon (e.g. BGS instead of DWH for phase 1).
        // User: "BGS firing first instead of DWH; happens intermittently
        // when we have both in inventory". Detect via Corp HP: if Corp
        // is visibly at full health (≥95%) but our committedSpecPhase is
        // > 1, that's a fresh kill with stale state — reset.
        // 1.9.99.189: reverted 1.9.99.188's latchStuck trigger expansion.
        // The latch can be legitimately armed during a kill that's still
        // in progress (Corp HP regens to 100% between cycles per Corp's
        // out-of-combat regen, but stat reductions persist — kill is
        // ongoing). latchStuck + fresh HP fires regularly across bank
        // trips and would wipe specsThisKill prematurely.
        // 1.9.99.194: require corpSeenAtZeroHp before stale-reset can fire.
        // User confirmed Corp regens HP to 100% during empty rooms (between
        // POH cycles) WITHOUT actually dying — stat reductions persist
        // across these. Pre-1.9.99.194 the reset fired on "Corp at >=95%"
        // alone, wiping specsThisKill every cycle and forcing the bot to
        // re-do phase 1 specs forever instead of progressing through
        // phases 2 and 3. Now we require evidence Corp actually died
        // (HP bar previously read 0).
        if (committedSpecPhase > 1 && corpSeenAtZeroHp) {
            Optional<Npc> corpForFreshCheck = Query.npcs()
                    .nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpForFreshCheck.isPresent()
                    && corpForFreshCheck.get().isHealthBarVisible()
                    && corpForFreshCheck.get().getHealthBarPercent() >= 0.95) {
                Log.warn("Stale committedSpecPhase=" + committedSpecPhase
                        + " at fresh Corp HP — resetting per-kill state");
                committedSpecPhase = 0;
                // 1.9.99.189: reverted 1.9.99.188's HP ratchet clears here.
                // The ratchets exist to track per-kill HP movement; if we
                // were already in committedSpecPhase>1, the bot was in
                // active combat and the ratchets reflect real readings.
                // Clearing them prematurely on a Corp-regen-to-100%
                // false-positive would re-arm kill-phase wrongly on next
                // legitimate HP drop. Back to original 1.9.99.156 behavior.
                if (mySnapshot != null) {
                    // 1.9.99.211: clear() instead of new — keep the reference
                    // stable so heartbeat-thread serialization doesn't deref
                    // a stale map.
                    if (mySnapshot.specsThisKill != null) mySnapshot.specsThisKill.clear();
                    else mySnapshot.specsThisKill = new LinkedHashMap<>();
                    mySnapshot.bgsDamageDealt = 0;
                }
            }
        }

        // 1.9.99.83: lobby-during-FIGHTING_CORP recovery. When Corp is
        // pressed up against the entrance, a click meant for Corp can
        // accidentally land on the passage hitbox (both occupy the same
        // screen region). The passage interaction teleports us back to
        // the lobby, but the state machine stays at FIGHTING_CORP. We
        // sit in the lobby trying to "fight" a Corp we can't see, the
        // attackCorpIfVisible fallback steps us toward Corp's last
        // known tile (which is back in the cave) — we step into the
        // passage, get yanked back to lobby, repeat. Detect this by
        // checking isInCorpLobby() at the top of handleFightingCorp.
        // If we ARE in the lobby, bounce back to ENTERING_COMBAT so the
        // walk-in / passage-click logic runs cleanly. User: "the bot
        // gets stuck trying to enter the cave and leaves back to back.
        // this only occurs when the corp is super close to the entrance
        // ... when we left click to attack we miss the hitbox."
        // 1.9.99.196: per-tick decision log (throttled 5s). On a stuck bot
        // this gives a continuous trace of which early-return is firing.
        if (System.currentTimeMillis() - lastFightingCorpDiagLogAt > 5000) {
            lastFightingCorpDiagLogAt = System.currentTimeMillis();
            try {
                Log.info("FCDIAG state=" + currentState
                        + " specEq=" + isSpecWeaponEquipped()
                        + " mainEq=" + isMainWeaponEquipped()
                        + " specE=" + Combat.getSpecialAttackPercent()
                        + " chosenSpec=" + chosenSpecWeapon
                        + " phase=" + (settings != null ? teamPhaseNeeded() : -1)
                        + " inCombat=" + isPlayerInCombat()
                        + " needsRepos=" + needsRepositioning
                        + " panicUntil=" + (panicRetreatActiveUntil > 0
                                ? (panicRetreatActiveUntil - System.currentTimeMillis()) : 0));
            } catch (Throwable ignored) {}
        }
        if (isInCorpLobby() && !isInCorpBossRoom()) {
            Log.warn("FIGHTING_CORP but player is in lobby (likely passage-click "
                    + "misfire from a left-click meant for Corp at the boundary) "
                    + "— bouncing to ENTERING_COMBAT to re-enter");
            // 1.9.99.131: tell handleEnteringCombat to walk DEEP before
            // clicking Corp, so we don't hit the passage again from the
            // entrance tile. Cleared once we're safely deep in the cave.
            recoveringFromLobbyStuck = true;
            currentState = BotState.ENTERING_COMBAT;
            return;
        }

        // 1.9.59: mid-fight prayer-out bank trip. User: 'if we run out
        // of Prayer and have no prayer potions we can just bank really
        // quick and grab the supplies we need and hit the pool.' When
        // prayer is empty AND we have no doses in inventory, go to
        // BANKING_AND_HEALING — the bank trip handler walks to Ferox,
        // restocks pots, pools to full, returns. Better than camping
        // at Corp without Protect Magic and dying.
        if (Prayer.getPrayerPoints() <= 5 && getPrayerDoses() == 0) {
            Log.warn("Prayer empty (" + Prayer.getPrayerPoints()
                    + ") and no prayer pots — heading to bank for restock");
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // 1.9.99.72: panic-retreat park. handleHealthAndPrayer triggers a
        // 5-tile step-off when emergency eats fire twice within 2s; while
        // the park window is open we don't re-engage Corp so HP / veng can
        // catch up. Resume early if HP recovers above PANIC_RETREAT_RESUME_HP.
        if (panicRetreatActiveUntil > 0) {
            long now = System.currentTimeMillis();
            int hp = MyPlayer.getCurrentHealth();
            if (now >= panicRetreatActiveUntil || hp >= PANIC_RETREAT_RESUME_HP) {
                Log.info("PANIC-RETREAT: ending park (HP=" + hp
                        + ", window " + (now >= panicRetreatActiveUntil ? "elapsed" : "early-resume")
                        + ") — re-engaging");
                panicRetreatActiveUntil = 0;
                // 1.9.99.97: trigger drift recheck — we just walked 5 tiles
                // off Corp and need to find a fresh cardinal slot.
                needsRepositioning = true;
            } else {
                return;
            }
        }

        // 1.9.99.73: karam-low check moved to handleEnteringCombat
        // (pre-engagement). Bailing mid-fight wasted entire trips —
        // the 20:32 log finished a POH cycle, walked back to Corp,
        // ate one karam, then bailed at karams=2 without firing a
        // single spec this trip. Once we're committed to FIGHTING_CORP,
        // the kill runs to completion; emergency-eat / panic-tele
        // handle survival if supplies run out.

        // 1.8.9 / 1.9.4: emergency HP eat-only short-circuit + panic-tele.
        // Pre-1.8.9 the bot would pre-activate spec, swap weapons, and act
        // normally even at critical HP. 1.9.4 adds a panic-tele escalation:
        // if HP is one Corp hit from death (<=8), eating clearly isn't
        // keeping up — bail to EMERGENCY_ESCAPE (Ferox via Ring of Dueling
        // → Games necklace → run-to-entrance → logout). Saves the trip
        // instead of dying on the spec-weapon swap animation lock.
        int currentHpEmergency = MyPlayer.getCurrentHealth();
        if (currentHpEmergency <= INTERNAL_EMERGENCY_HP && !isDarkCorePresent()) {
            if (currentHpEmergency <= INTERNAL_PANIC_TELE_HP) {
                Log.warn("HP critical (" + currentHpEmergency + " <= " +
                        INTERNAL_PANIC_TELE_HP + ") — emergency escape from Corp");
                currentState = BotState.EMERGENCY_ESCAPE;
                return;
            }
            Log.warn("Emergency HP (" + currentHpEmergency + ") — eat-only mode, " +
                    "skipping spec/swap/vengeance this tick");
            // 1.9.4: try combo eat. If we have NO food (combo eat returns
            // false), tele out — we can't recover HP and Corp keeps hitting.
            // We don't cancel pre-activated spec here either; the extra
            // mouse-button click is wasted motion when we should be eating
            // or teleing. If spec auto-fires on next attack, we lose 50%
            // energy but we're not dead — recoverable.
            if (!emergencyComboEat()) {
                Log.warn("No food available at emergency HP — emergency escape from Corp");
                currentState = BotState.EMERGENCY_ESCAPE;
                return;
            }
            return;
        }

        // 1.9.9: process any deferred spec-hit confirmation BEFORE running
        // new spec logic. The previous tick's spec may have just registered
        // XP — confirm hit (advance phase) or mark miss (don't).
        processPendingSpecHit();

        // 1.9.78: stage C — if neither pool-roll nor lobby-roll activated
        // spec, force it now that we're in the boss room. Only fires when
        // we have a spec weapon equipped (otherwise activating spec is
        // wasted clicks) and a spec weapon for the current phase is owned.
        if (!specPreActivatedThisTrip && isSpecWeaponEquipped()
                && pickSpecWeaponForCurrentPhase() != null) {
            forcePreActivateSpecStageC();
        }

        // PRIORITY 1: Handle Dark Core — ONLY when it threatens us AND we
        // can afford to detour from spec dumping.
        // 1.9.99.118: gate on isDarkCoreThreatening() instead of mere
        // presence.
        // 1.9.99.135: ALSO skip the core during an active spec dump.
        // Spec dumping is time-critical — we have a finite spec bar and
        // teammates relying on us to debuff Corp. Detouring to kill the
        // core costs spec timing and weapon swings. Just take the core
        // damage, eat if needed, continue specing. handleHealthAndPrayer
        // covers HP, panic-tele covers worst case. User: "if the dark
        // core spawns while we are spec dumping we get stuck trying to
        // kill the core instead of just spec dumping/tping out."
        // "Active spec dump" = we own a spec weapon for the current
        // phase + we have spec energy + we're NOT in kill phase. If
        // any of those are false, normal dark-core handling resumes.
        boolean inActiveSpecDump = pickSpecWeaponForCurrentPhase() != null
                && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()
                && !isInKillPhase();
        if (isDarkCoreThreatening() && !inActiveSpecDump) {
            darkCoreLastSeen = System.currentTimeMillis();

            // Initialize core tracking if this is first detection
            if (chosenDodgeAxis == CoreDodgeAxis.NOT_SET) {
                Log.info("First dark core detection (threatening us) - initializing tracking");
                coreDodgeState = CoreDodgeState.DETECTED;
            }

            currentState = BotState.HANDLING_DARK_CORE;
            return;
        }

        handleProtectionPrayers();

        // 1.8.8: mid-fight repositioning. handleCorpPositioning() was
        // defined but never invoked, so the bot picked one starting tile
        // and stayed there for the whole kill. When Corp roams (especially
        // through a corner where the cave narrows), the player ends up
        // INSIDE Corp's 5x5 hitbox and takes free stomp damage.
        // Two checks: (a) emergency — we're already under Corp; immediate
        // reposition. (b) periodic — we've drifted from any of the assigned
        // cardinal positions; reposition on a 3s throttle.
        Optional<Npc> repositionCorp = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (repositionCorp.isPresent()) {
            Npc corp = repositionCorp.get();
            // 1.9.99.136: removed the duplicate inline encroachment check.
            // It used to live here (1.9.99.125/127/129) but 1.9.99.133
            // extracted it to maybeRelocateForEncroachment() called from
            // the main loop. Keeping both was racing on lastEncroachmentCheckAt
            // — whichever path ran first updated the timestamp and
            // suppressed the other. The helper handles all FIGHTING_CORP +
            // USING_SPECIAL_ATTACK ticks with its own gating.
            // 1.9.99.114: route through isUnderCorp() (with stability check)
            // instead of an inline single-frame check. Corp's animated area
            // false-positives on every walk-by frame; the stability counter
            // requires >=2 consecutive overlaps before treating it as a
            // real stomp threat.
            if (isUnderCorp(corp)) {
                Log.warn("Player on Corp's hitbox — emergency step away to avoid stomp damage");
                if (moveToNearestCorpPosition(corp)) {
                    lastRepositionCheck = System.currentTimeMillis();
                    return; // skip rest of tick — we just clicked-to-walk
                }
            } else if (System.currentTimeMillis() - lastRepositionCheck > 250) {
                // 1.9.99.53: 3000ms → 500ms. Corp roams ~1 tile per game
                // tick (600ms), so a 3s interval let Corp drift up to 5
                // tiles before we'd notice.
                // 1.9.99.87: 500ms → 250ms. User: "if the corp for some
                // reason moves 1 square back, before our realization of
                // where the corp is standing updates, we think we are in
                // its hitbox, and we use the minimap to walk back a square
                // even though its a one tile difference. no real player
                // would do this." At 250ms we catch Corp's single-tile
                // moves within ~half a game tick — fast enough that the
                // bot's belief about Corp's position never lags by more
                // than 1 tile, eliminating the false "we're under Corp"
                // panic-walk. The check itself is cheap; it only
                // clicks (reposition) when we're actually out of the
                // cardinal tolerance — so tighter checks just mean
                // earlier clicks when Corp moves, not more clicks when
                // it doesn't. User: "if we catch him moving before he
                // does and update that in real time wouldnt that mean we
                // would almost never walk under him?".
                lastRepositionCheck = System.currentTimeMillis();
                // 1.9.99.61: skip reposition if the player is already
                // attacking Corp (auto-walk to melee is in flight) OR
                // we're inside the chunk-walk buffer zone (already as
                // close as chunked-walk can safely get; click-attack
                // is handling the final approach). Pre-1.9.99.61 the
                // drift recheck at 500ms intervals re-fired
                // moveToNearestCorpPosition while the auto-walk was
                // mid-flight, looping walkInChunksTo with no-op chunks
                // and spamming the chunk log. User log: "chunk 1 tiles
                // to (2976, 4379)" repeated 4x while bot stood at that
                // tile during the auto-walk.
                // 1.9.99.97: gate the entire drift recheck on
                // needsRepositioning. Mid-fight (between spec swings,
                // during Fang melee) the flag is false and the check
                // is skipped. Flag turns true on the four trigger
                // events (entering room, exiting dark-core, panic
                // retreat ending, returning from POH/banking — the
                // first three set the flag explicitly, POH/banking
                // returns to ENTERING_COMBAT which sets it). After
                // this block reports a good position OR completes a
                // reposition, flag flips back to false. Removed the
                // 1.9.99.96 dual-interaction check — redundant now
                // because the gate itself stops the recheck mid-fight.
                if (!needsRepositioning) {
                    // Drift recheck not needed — we're committed to
                    // the current cardinal slot mid-fight.
                } else if (isInGoodCorpPosition(corp)) {
                    needsRepositioning = false;
                    Log.debug("Drift recheck: already in good position — flag cleared");
                } else {
                    Log.info("Drifted from Corp position (needsRepositioning=true) — repositioning");
                    if (moveToNearestCorpPosition(corp)) {
                        needsRepositioning = false;
                        return;
                    }
                }
            }
        }

        // 1.8.7 / 1.9.0 / 1.9.2: detect a pre-activated spec that just fired
        // in-line. The gate fires only when energy has actually DROPPED since
        // we last armed spec (lastSeenSpecEnergy) — the pre-1.9.2 "energy<100"
        // check stayed true forever after spec #1 and caused 30+ false fires
        // per kill, each one re-activating the spec button (toggle-spam) and
        // wrongly incrementing the phase-spec counter.
        int currentSpecPercent = Combat.getSpecialAttackPercent();
        if (specWeaponReadyForUse
                && !Combat.isSpecialAttackEnabled()
                && isSpecWeaponEquipped()
                && currentSpecPercent < lastSeenSpecEnergy) {
            Log.info("Pre-activated spec fired in-line (" + lastSeenSpecEnergy
                    + "% -> " + currentSpecPercent + "%)");
            specWeaponReadyForUse = false;
            lastSeenSpecEnergy = currentSpecPercent; // commit new floor

            // 1.9.9: defer recordSpecUsed until XP confirms the hit. Pre-1.9.9
            // every spec fire (hit OR miss) bumped the phase counter, so the
            // bot rotated weapons after 4 spec ATTEMPTS instead of 4 hits.
            // 1.9.41: capture the XP baseline NOW (energy just dropped), not
            // at pre-activate time. Pre-1.9.41 we used xpAtSpec which was
            // set when the spec button was activated — but the bot kept
            // auto-attacking between pre-activate and the spec firing, so
            // xpAtSpec was already stale by hundreds of XP. Any normal hit
            // landing AFTER pre-activate but BEFORE this branch was being
            // wrongly credited to the spec's XP delta. User: "if we get an
            // xp drop but our spec hasnt drained (like we get a normal hit
            // inbetween our attacks and specs) that counts as a spec dump
            // successful." Now baseline = XP at the exact tick energy
            // dropped — spec animation locks the player so the next XP
            // increase HAS to be from the spec itself.
            if (chosenSpecWeapon != null) {
                // 1.9.99.39: enqueue (don't overwrite). Baselines paired from
                // pre-activate snapshots — 1.9.99.22 (XP) / 1.9.99.37 (HP).
                // 1.9.99.45: also snapshot monotonicHitsplatCounter so
                // confirmation requires a NEW hit since enqueue.
                advanceHitsplatCounter(Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst());
                pendingHits.add(new PendingSpecAttempt(
                        chosenSpecWeapon,
                        xpAtSpec >= 0 ? xpAtSpec : getMeleeCombatXp(),
                        corpHpAtSpec >= 0 ? corpHpAtSpec : readCorpHpPct(),
                        System.currentTimeMillis() + HIT_CONFIRM_TIMEOUT_MS,
                        monotonicHitsplatCounter));
            }

            // 1.9.17: phase rotation moved to processPendingSpecHit — it
            // needs to run AFTER recordSpecUsed (which itself is deferred
            // until XP confirms hit). Doing it here was running on a stale
            // count, missing the 4th-spec rotation trigger.

            if (canFireAnotherSpecOnThisBar()) {
                Log.info("Energy + phase targets allow another spec — re-activating spec for next hit");
                if (tryActivateSpec()) { // 1.9.34
                    specWeaponReadyForUse = true;
                    // Re-arm tracking so the NEXT spec fire is detected as a real drop.
                    lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                    xpAtSpec = getMeleeCombatXp(); // 1.9.9: baseline for next spec
                    corpHpAtSpec = readCorpHpPct(); // 1.9.99.37: baseline for next spec
                    Log.info("Re-activated special attack: next hit will spec again");
                }
            } else {
                // 1.9.4: bar drained. The Fang swap doesn't belong here — we
                // only swap to Fang at the KILL-phase transition. While we
                // still want more specs (phase targets unmet + Corp HP above
                // floor + POH available), tele to POH for restoration with
                // the spec weapon still equipped. Only when we're done
                // dumping specs do we swap to Fang for melee finish.
                Log.info("Spec bar exhausted");
                if (shouldStartRestorationCycle()) {
                    Log.info("Insta-tele to POH for restoration (spec weapon stays equipped)");
                    currentState = BotState.PREPARING_RESTORATION_CYCLE;
                    return;
                }
                Log.info("Kill phase — swapping to Fang for melee finish");
                queueSpecWeaponSwitchBack();
            }
        }

        // 🔥 PRE-ACTIVATE SPECIAL ATTACK IF CONDITIONS MET
		// 1.9.99.79: skip pre-activate when we just clicked AND energy
		// hasn't dropped. Combat.isSpecialAttackEnabled() lags the
		// actual server state by ~1 tick after a click — pre-1.9.99.79
		// the gate fired on the SDK-lag tick, re-clicked the bar, and
		// toggled it OFF. The 00:40:56 log showed this: lobby pre-
		// activate at :55, "PRE-ACTIVATING" + "Failed to activate as
		// backup" chains for the next 2s until the bar finally settled.
		// If energy DROPS in the recent-click window, the spec swung
		// and the bar is legitimately off — gate passes, we re-activate
		// for the next spec. User: "we enabled spec and then disabled
		// and enabled and disabled when we could have just left it
		// enabled."
		long sinceSpecClick = System.currentTimeMillis() - lastSpecActivateAt;
		int currentSpecEnergyPre = Combat.getSpecialAttackPercent();
		boolean barLikelyOnFromRecentClick = sinceSpecClick < 1500
				&& currentSpecEnergyPre == lastSeenSpecEnergy;
		if (shouldUseSpecialAttack() && !Combat.isSpecialAttackEnabled()
				&& !barLikelyOnFromRecentClick) {
			Log.info("Special attack conditions met - PRE-ACTIVATING for next attack");
			if (tryActivateSpec()) { // 1.9.34
				lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); corpHpAtSpec = readCorpHpPct(); // 1.9.2 + 1.9.9: seed detector floor; 1.9.99.37 HP baseline
				Log.info("Special attack pre-activated in main combat loop");
			}
		}

		// Use special attack when available
		if (shouldUseSpecialAttack()) {
			currentState = BotState.USING_SPECIAL_ATTACK;
			return;
		}

		// 1.9.24: if we have a spec weapon equipped but shouldUseSpecialAttack
		// returned false (e.g. Corp HP below spec floor → kill phase), swap
		// to main weapon. Without this the bot just auto-attacks ("pokes")
		// with Arclight/Elder maul for the rest of the kill — much lower
		// DPS than Fang. Skip the swap if a switch-back is already queued.
		// 1.9.58.1: also swap when there's no usable spec weapon for the
		// current team phase (e.g. phase 3 needed but bot doesn't own
		// BGS). User log: with phase 3 needed and no BGS, bot stood
		// poking with Arclight forever — no spec firing, no restoration
		// cycle (1.9.58 fix), no weapon swap. Now we swap to Fang for
		// proper melee DPS.
		boolean noSpecWeaponForPhase = pickSpecWeaponForCurrentPhase() == null
				&& teamPhaseNeeded() > 0;
		if (isSpecWeaponEquipped() && !specWeaponSwitchQueued
				&& (isInKillPhase() || noSpecWeaponForPhase)) {
			Log.info("Spec weapon still equipped with "
					+ (isInKillPhase()
							? "kill phase reached"
							: "no spec weapon owned for phase "
									+ teamPhaseNeeded())
					+ " — queueing Fang swap");
			queueSpecWeaponSwitchBack();
		}

		// 1.9.99.52: kill-phase diagnostic + stuck-queue watchdog (Codex audit).
		// If we're in the kill phase and a spec weapon is still equipped,
		// log the state once per second so we can see why the Fang swap
		// isn't completing. If the queue has been pending > 5s and main
		// weapon still isn't equipped, force-call equipMainWeaponFast
		// rather than waiting for handleSpecWeaponSwitchTiming.
		if (isInKillPhase() && isSpecWeaponEquipped()) {
			long now = System.currentTimeMillis();
			if (now - lastKillPhaseDiagnosticAt > 1000) {
				lastKillPhaseDiagnosticAt = now;
				Log.info("KILL-PHASE-DIAG state=" + currentState
						+ " chosenSpec=" + chosenSpecWeapon
						+ " bgsEquipped=" + Equipment.contains("Bandos godsword")
						+ " specSwitchQueued=" + specWeaponSwitchQueued
						+ " needsSwitchBack=" + needsToSwitchBackFromSpec
						+ " fangInv=" + Inventory.contains(getMainWeaponVariants().toArray(new String[0]))
						+ " fangEquipped=" + isMainWeaponEquipped()
						+ " availableMain=" + getAvailableMainWeapon()
						+ " queuedAgoMs=" + (specWeaponSwitchQueued
								? (now - specWeaponSwitchQueuedAt) : -1));
			}
			if (specWeaponSwitchQueued && !isMainWeaponEquipped()
					&& (now - specWeaponSwitchQueuedAt) > 5000) {
				Log.warn("Spec switch-back queued > 5s but main weapon still not equipped "
						+ "— force-calling equipMainWeaponFast");
				if (equipMainWeaponFast()) {
					specWeaponSwitchQueued = false;
					needsToSwitchBackFromSpec = false;
				} else {
					Log.warn("Force-swap also failed; resetting queue so next iter retries");
					specWeaponSwitchQueued = false;
					needsToSwitchBackFromSpec = false;
				}
			}
		}

		// 1.9.58: in kill phase with Fang equipped, fire Fang's 25% spec.
		// User: 'while we are killing corp with the fang after actually
		// getting all debuff specs off we should be able to use our 25%
		// fang specs on the corp.' Fang spec hits harder + applies a
		// bleed for extra DPS. Energy gate is 25 (Fang cost), not the
		// generic getMinSpecEnergy() (50 for Arclight/Maul).
		// 1.9.99.52: if we're in kill phase and Fang is in INVENTORY but
		// not yet equipped, trigger the swap before trying to spec.
		// Pre-1.9.99.52 the Fang-spec block silently no-op'd because
		// Equipment.contains("Osmumten's fang") was false (we were still
		// on BGS), so the bot regen'd spec without acting.
		if (teamPhaseNeeded() == 0
				&& !isMainWeaponEquipped()
				&& getAvailableMainWeapon() != null
				&& !specWeaponSwitchQueued) {
			Log.info("Kill phase + Fang in inventory but not equipped — direct equip");
			equipMainWeaponFast();
		}
		if (teamPhaseNeeded() == 0
				&& isMainWeaponEquipped()
				&& Combat.getSpecialAttackPercent() >= 25
				&& !Combat.isSpecialAttackEnabled()
				&& isPlayerInCombat()) {
			Optional<Npc> corpForFang = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
			if (corpForFang.isPresent() && isCorpAlive(corpForFang.get())) {
				Log.info("Kill phase: activating Fang spec (energy "
						+ Combat.getSpecialAttackPercent() + "%)");
				tryActivateSpec();
			}
		}

		// 1.8.8: mid-fight POH restoration. If we're out of spec but the team
		// hasn't hit its phase targets yet and Corp is still healthy enough
		// to be worth dumping more stat-reducer specs into, break out of
		// combat and run a POH cycle to refill. The natural termination is
		// either teamPhaseNeeded()==0 (targets met, stay and melee) or Corp's
		// HP dropping below corpMinHpForSpec (a teammate is killing it,
		// switch to melee and help finish).
		if (shouldStartRestorationCycle()) {
			Log.info("Mid-fight spec dump: spec depleted with phase targets " +
					"remaining and Corp HP above floor — POH restoration cycle");
			currentState = BotState.PREPARING_RESTORATION_CYCLE;
			return;
		}

		// 1.9.99.74: handleVengeanceLogic() lifted to main loop (post-eat).
		// Previously called here, which only ran during FIGHTING_CORP and
		// missed all the USING_SPECIAL_ATTACK / weapon-swap ticks.
		// 1.9.99.47: opportunistic re-pot during combat. The lobby/in-room
		// prep paths only drink super combat when the inventory is FULL,
		// which doesn't fire mid-fight after the first potion runs out.
		// User: "we didnt veng the entire fight i think ... we didnt
		// repot when our boosted hp ran out". Now we check every tick:
		// if stats aren't boosted and we still have a combat potion in
		// inventory, drink one. Single click, ~1 tick animation lock.
		maybeReDrinkCombatPotion();

        // PRIORITY 3: Continue normal combat
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent()) {
            Npc corp = corpOpt.get();

            // Track Corp's alive status for prayer deactivation
            boolean corpCurrentlyAlive = isCorpAlive(corp);
            if (corpWasAliveLastCheck && !corpCurrentlyAlive) {
                // Corp just died - queue prayer deactivation
                queuePrayerDeactivation();
            }
            corpWasAliveLastCheck = corpCurrentlyAlive;

            // 1.9.23: re-engage if we're not ATTACKING Corp specifically.
            // 1.9.95: 500ms debounce. Pre-1.9.95 multiple back-to-back loop
            // iterations (50ms each) could each fire corp.interact("Attack")
            // after eat/spec when player wasn't yet showing as attacking Corp.
            if (!isPlayerAttackingCorp(corp)) {
                long sinceReengage = System.currentTimeMillis() - lastCorpReengageClickAt;
                if (sinceReengage >= CORP_REENGAGE_DEBOUNCE_MS) {
                    if (attackCorpIfVisible(corp)) {
                        lastCorpReengageClickAt = System.currentTimeMillis();
                    }
                }
            }

            // 1.9.24 / 1.9.28: only declare Corp dead when we observe HP
            // at 0 AFTER having previously seen HP > 5%. The SDK reports
            // 0% on a freshly-engaged Corp because the bar exists but
            // hasn't been populated yet — without the tracker, the bot
            // transitions from FIGHTING_CORP straight to LOOTING on the
            // first tick of combat and wastes the kill.
            // 1.9.99.94: snapshot how long Corp was missing BEFORE we reset
            // the timer. Used by the respawn-detection below — if Corp
            // reappears at full HP after a >1s absence, the previous kill
            // ended and we missed the 0% HP read (death animation, or
            // server-side respawn at <15s timeout). Without this snapshot
            // the timer resets to 0 the moment Corp reappears and we lose
            // the signal. User scenario: "if we are in the last phase
            // waiting for the 15 seconds and it respawns at 13 seconds".
            long savedCorpMissingSinceMs = corpMissingSinceMs;
            long npcAbsenceDuration = savedCorpMissingSinceMs > 0
                    ? System.currentTimeMillis() - savedCorpMissingSinceMs
                    : 0;

            // 1.9.99.107: death-detection diagnostic. Capture Corp's
            // animation ID transitions and HP-bar-visibility falling edge.
            // Goal: identify Corp's death anim ID and confirm whether
            // barVis true→false IS the actual death signal.
            // 1.9.99.108: log spam removed — diagnostic now pushes to a
            // ring buffer (deathDiagRecent) rendered on the overlay.
            // On despawn the buffer freezes so the user can read the
            // last ~14 events post-mortem.
            int corpAnimNow = -1;
            try { corpAnimNow = corp.getAnimation(); } catch (Throwable ignored) {}
            boolean barVisNow = corp.isHealthBarVisible();
            // 1.9.99.112: SDK returns 0-1 proportion (NOT 0-100 percent).
            // Convert to percent for display + log. User screenshot showed
            // "1%" displayed when Corp at 50% HP — raw 0.5 rounded up by
            // %.0f. Multiply by 100 here so the overlay reads true percent.
            double hpForDiag = barVisNow ? corp.getHealthBarPercent() * 100.0 : -1.0;
            double lastHpForDiag = lastObservedCorpHpPercent * 100.0;
            if (corpAnimNow != lastCorpAnimSeen) {
                pushDeathDiag("anim " + lastCorpAnimSeen + "→" + corpAnimNow
                        + " bar=" + (barVisNow ? "Y" : "N")
                        + " hp=" + String.format("%.1f", hpForDiag) + "%");
                lastCorpAnimSeen = corpAnimNow;
            }
            if (lastCorpHealthBarVisible && !barVisNow) {
                pushDeathDiag("BAR↓ anim=" + corpAnimNow
                        + " lastHP=" + String.format("%.1f", lastHpForDiag) + "%");
            } else if (!lastCorpHealthBarVisible && barVisNow) {
                pushDeathDiag("BAR↑ anim=" + corpAnimNow);
            }
            lastCorpHealthBarVisible = barVisNow;
            // 1.9.99.108: track Corp's interaction target. Live Corp
            // targets a player every tick — dead Corp's target drops.
            // SDK Optional<Character> getInteractingCharacter().
            boolean interactingNow = false;
            try { interactingNow = corp.getInteractingCharacter().isPresent(); } catch (Throwable ignored) {}
            if (lastCorpInteracting && !interactingNow) {
                pushDeathDiag("TGT↓ anim=" + corpAnimNow + " bar=" + (barVisNow ? "Y" : "N"));
            } else if (!lastCorpInteracting && interactingNow) {
                pushDeathDiag("TGT↑ anim=" + corpAnimNow);
            }
            lastCorpInteracting = interactingNow;

            if (corp.isHealthBarVisible()) {
                double currentHpPercent = corp.getHealthBarPercent();
                // 1.9.99.94: respawn detection via absence window.
                // 1.9.99.96: also detect respawn via HP JUMP. The
                // absence-window check only fires when Corp NPC was
                // missing from Query.npcs() for >1s — but Corp's NPC
                // can stay present continuously through death anim +
                // respawn (same NPC slot, just animation transition).
                // In that case absence-window never trips. The HP jump
                // catches it: if our last observed HP was low (Corp was
                // dying) and the current HP is suddenly high, a death-
                // and-respawn happened during the gap. This was the
                // missing piece — the user observed bot stays
                // FIGHTING_CORP through respawn because the state
                // transition never fires. Now: low→high HP jump =
                // RESPAWN → LOOTING.
                // 1.9.99.112: SDK returns 0-1 proportion, not 0-100 percent.
                // All thresholds rescaled. Pre-1.9.99.112 these used 0-100
                // values which never matched the actual 0-1 readings →
                // engagedPriorKill was always false (silently dead gate),
                // nowHighHp never fired (HP-jump respawn detection dead),
                // wasObservedDying always fired (any non-default value < 30).
                boolean engagedPriorKill = maxCorpHpPercentThisKill > 0.05;
                boolean wasObservedDying = lastObservedCorpHpPercent > 0.0
                        && lastObservedCorpHpPercent < 0.30;
                boolean nowHighHp = currentHpPercent > 0.80;
                // 1.9.99.173: also detect respawn via HP UP-jump even
                // when we never observed Corp at < 30%. User report:
                // "the bot didnt realize the kill ended and just stayed
                // there waiting for the boss to spawn; and when it did
                // it thought it was continuing the fight". If Corp went
                // 60% → dead → respawned 100% between two ticks (faster
                // than our sample rate), wasObservedDying never fired
                // because we never saw < 30%. A jump UP of 0.5+ on
                // Corp's HP between ticks is impossible mid-kill (Corp
                // doesn't regenerate that fast) — it's a respawn.
                boolean hpJumpedUp = lastObservedCorpHpPercent > 0.0
                        && currentHpPercent - lastObservedCorpHpPercent > 0.50;
                if ((wasObservedDying || hpJumpedUp) && nowHighHp
                        && engagedPriorKill && !corpSeenAtZeroHp) {
                    Log.info(String.format("Corp RESPAWN (HP-jump): %.1f%% → %.1f%% (prior peak %.1f%%) — prior kill ended, LOOTING",
                            lastObservedCorpHpPercent * 100.0,
                            currentHpPercent * 100.0,
                            maxCorpHpPercentThisKill * 100.0));
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                    corpMissingSinceMs = 0;
                    return;
                }
                boolean reappearedAfterAbsence = npcAbsenceDuration > 1000;
                boolean atOrNearFullHp = currentHpPercent > 0.95; // 1.9.99.112: 0-1 scale
                if (reappearedAfterAbsence && atOrNearFullHp
                        && engagedPriorKill && !corpSeenAtZeroHp) {
                    Log.info(String.format("Corp RESPAWN (absence): NPC was missing %dms, reappeared at %.1f%% HP (prior peak %.1f%%, lastHP %.1f%%) — prior kill ended, routing to LOOTING before engaging respawn",
                            npcAbsenceDuration,
                            currentHpPercent * 100.0,
                            maxCorpHpPercentThisKill * 100.0,
                            lastObservedCorpHpPercent * 100.0));
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                    corpMissingSinceMs = 0;
                    return;
                }
                // 1.9.99.223: HP UP-jump respawn detection. If Corp's HP
                // suddenly jumps from low (≤30%) to high (≥80%), Corp died
                // and respawned between our HP reads. Corp doesn't regen
                // this fast naturally (regen is slow + only when room is
                // empty), so an UP-jump > 50% in one tick means new Corp.
                // Treat as kill confirmation. Pre-fix the bot stuck in
                // FIGHTING_CORP for an entire run while peer counted kills
                // because the NPC reference never went missing — Corp
                // respawned within ~1 game tick, no NPC gap.
                if (lastObservedCorpHpPercent > 0.0
                        && lastObservedCorpHpPercent <= 0.30
                        && currentHpPercent >= 0.80
                        && (currentHpPercent - lastObservedCorpHpPercent) > 0.50) {
                    Log.info(String.format("Corp RESPAWN (HP up-jump): %.1f%% → %.1f%% "
                            + "— prior kill ended, routing to LOOTING",
                            lastObservedCorpHpPercent * 100.0,
                            currentHpPercent * 100.0));
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                    corpMissingSinceMs = 0;
                    return;
                }
                if (currentHpPercent > maxCorpHpPercentThisKill) {
                    maxCorpHpPercentThisKill = currentHpPercent;
                }
                // 1.9.99.92: track most-recent observed HP for the
                // timeout-gate logic in the else-branch.
                lastObservedCorpHpPercent = currentHpPercent;
                // 1.9.99.101: tightened from `<= 1.0` to `<= 0.0`. Corp's
                // HP bar reports fractional percentages — 1.0 means
                // Corp has ~20 HP (alive!), not dead. Pre-1.9.99.101 we
                // transitioned to LOOTING at 1% which is a false
                // positive: Corp is dying but not dead, handleLooting
                // resets maxCorpHpPercentThisKill = 0, bot bails to
                // bank, Corp finishes dying without us. Now: only
                // transition when HP bar truly reads 0%. Corp's actual
                // death is caught via the timeout fallback paths below
                // (fastTimeout at 3s + lastHP < 30, sustainedAbsence at
                // 15s) when the NPC despawns. User screenshot showed
                // peak=1% (legitimately Corp's bar reading 1%), which
                // means this gate had been wrongly firing repeatedly.
                if (currentHpPercent <= 0.0 && maxCorpHpPercentThisKill > 0.05) { // 1.9.99.112: 0-1 scale
                    Log.info(String.format("Corp HP observed at 0%% (peak this kill: %.1f%%) — looking for loot",
                            maxCorpHpPercentThisKill * 100.0));
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                }
            }
            // 1.9.99.91: Corp NPC is present this tick — reset the
            // missing-timer so a brief disappearance doesn't accumulate
            // toward the 3s death-fallback.
            corpMissingSinceMs = 0;
        } else {
            // 1.9.24: Corp NPC not in render — could be dead, could be
            // walked off-screen due to roaming. Require corpseenAt0Hp
            // confirmation before transitioning to LOOTING. Pre-1.9.24
            // we'd transition immediately and miss the kill if Corp had
            // briefly wandered out of render.
            if (corpWasAliveLastCheck) {
                queuePrayerDeactivation();
                corpWasAliveLastCheck = false;
            }
            // 1.9.99.91: stamp the first tick Corp went missing so we
            // can timeout if the HP-bar-at-0 detection misses the kill.
            long nowMs = System.currentTimeMillis();
            if (corpMissingSinceMs == 0) {
                corpMissingSinceMs = nowMs;
                // 1.9.99.107: capture the last animation we saw while
                // Corp was still in render — this is the most likely
                // death animation ID. 1.9.99.108: freeze the ring buffer
                // so the overlay holds the death-moment trace.
                pushDeathDiag("DESPAWN lastAnim=" + lastCorpAnimSeen
                        + " lastBar=" + (lastCorpHealthBarVisible ? "Y" : "N")
                        + " lastHP=" + String.format("%.1f", lastObservedCorpHpPercent * 100.0) + "%"
                        + " peak=" + String.format("%.1f", maxCorpHpPercentThisKill * 100.0) + "%");
                freezeDeathDiagBuffer();
            }
            long missingDuration = nowMs - corpMissingSinceMs;
            if (corpSeenAtZeroHp) {
                Log.info("Corp not found AND we saw HP at 0 — looking for loot");
                currentState = BotState.LOOTING;
            } else {
                // 1.9.99.92: timeout fallback hardened. Three gates
                // before declaring Corp dead from the timer:
                // (1) Player physically in the boss room. Query.npcs()
                //     scans the loaded scene around the player; if we
                //     teleported out, Corp wouldn't be queryable from
                //     the lobby/Ferox/POH but that doesn't mean dead.
                // (2) maxCorpHpPercentThisKill > 5% — we actually
                //     engaged Corp this kill (HP bar populated).
                // (3) lastObservedCorpHpPercent < 30% OR missing > 15s.
                //     If last-seen HP was high, Corp probably roamed —
                //     he doesn't drop from healthy to dead in 3s without
                //     a major event. Either give the dying gate a fast
                //     path (low last-HP + 3s) or require a long
                //     sustained absence (>15s) to declare dead.
                //     User: "he can just be on the far side of the room
                //     and we will wipe our progress assuming he died."
                boolean inBossRoom = false;
                try { inBossRoom = isInCorpBossRoom(); } catch (Exception ignored) {}
                boolean engagedThisKill = maxCorpHpPercentThisKill > 0.05; // 1.9.99.112: 0-1 scale
                // 1.9.99.93: coordinator-confirmed death. If a teammate's
                // kill_id has advanced past our localKillId, they finished
                // Corp before our local sensors picked it up. Short-circuits
                // the timeout entirely. Still requires (a) engagedThisKill
                // (otherwise we'd auto-skip kills we never joined) and
                // (b) inBossRoom (teammates' kill_id is irrelevant if we
                // teleported out for some other reason).
                long teamKillId = coordinatorTeamKillId();
                boolean teammateConfirmedDeath = teamKillId > localKillId;
                boolean wasDying = lastObservedCorpHpPercent < 0.30; // 1.9.99.112: 0-1 scale
                boolean fastTimeout = missingDuration > 3000 && wasDying;
                boolean sustainedAbsence = missingDuration > 15000;
                // 1.9.99.174: in-boss-room fast-death. If we're physically
                // in the boss room AND were engaged this kill AND Corp
                // has been missing for > 1.5s, treat as dead even without
                // observing low HP. Corp's roam range inside the boss
                // room is small (~8 tiles) so Query.npcs() reliably sees
                // him while we're inside. User: "if the bot was in the
                // room and now it isnt we should consider it dead and
                // reset our kill status".
                // 1.9.99.175: also require the bot to be DEEP in the room
                // (within ~12 tiles of Corp's spawn location), not just
                // anywhere inside the corpCave polygon. Pre-1.9.99.175 a
                // bot lingering at the entrance with Corp roamed to the
                // far side could false-positive: Corp out of player's
                // immediate Query.npcs() coverage just because of
                // distance, not actual death. User: "we have to make
                // sure we are deep enough in the room that it doesnt
                // incorrectly assume the corp is dead".
                boolean deepInRoom = false;
                try {
                    WorldTile here = MyPlayer.getTile();
                    if (here != null) {
                        deepInRoom = here.distanceTo(CORP_SPAWN_LOCATION) <= 12;
                    }
                } catch (Exception ignored) {}
                boolean inRoomFastDeath = inBossRoom && deepInRoom
                        && engagedThisKill && missingDuration > 1500;
                boolean timeoutFired = fastTimeout || sustainedAbsence || inRoomFastDeath;
                // 1.9.99.101: relax engaged-this-kill gate. Original
                // peakHP > 5% protects against the freshly-engaged-bar-
                // reads-0 false positive. But it also blocks legitimate
                // deaths when peak got reset mid-fight (state oscillation,
                // false LOOTING, etc.). Add an alt signal: if we
                // observed Corp's HP at 1-29%, we definitely engaged —
                // a fresh-spawn bar reads exactly 0% before damage, so
                // any value 1-29% means we did damage. Either signal
                // (high peak OR low last-HP) qualifies.
                boolean lowLastHp = lastObservedCorpHpPercent > 0.0
                        && lastObservedCorpHpPercent < 0.30; // 1.9.99.112: 0-1 scale
                boolean engagedRelaxed = engagedThisKill || lowLastHp;
                // 1.9.99.105: the sustained 15s timeout drops the
                // engagement gate. User log showed peakHP=0, lastHP=100
                // (default, never updated by HP-bar reads) — meaning
                // isHealthBarVisible() returned false for the entire
                // fight despite us dealing damage. Both engagement
                // signals (peak > 5% and lowLastHp) failed, blocking
                // LOOTING permanently. 15s of NPC absence in the boss
                // room is sufficient signal on its own. Fast path
                // (3s) keeps the gate for false-positive safety.
                boolean sustainedNoGate = inBossRoom && sustainedAbsence;
                // 1.9.99.223: drop the engagedRelaxed gate when a teammate
                // has confirmed the kill via coord. User report: bot stuck
                // in FIGHTING_CORP for an entire run while peer counted 2
                // kills. HP-bar visibility failed (crowded room / not
                // interacting), so both engagement signals stayed false →
                // teammateConfirmedDeath couldn't fire through the gate.
                // teammate's killId advancing is itself a strong death
                // signal (peer ran coordinatorOnKillEnded after looting,
                // which only happens after Corp actually died). Just being
                // in the boss room is enough confirmation.
                boolean teammateConfirmNoEngageGate = inBossRoom && teammateConfirmedDeath;
                if ((inBossRoom && engagedRelaxed
                            && (teammateConfirmedDeath || fastTimeout))
                        || sustainedNoGate
                        || teammateConfirmNoEngageGate) {
                    String reason = teammateConfirmedDeath
                            ? "coordinator(teamKillId=" + teamKillId
                                    + " > local=" + localKillId + ")"
                            : (fastTimeout ? "fast(lowLastHP)" : "sustained");
                    Log.info(String.format("Corp NPC missing %dms (peak HP %.1f%%, lastHP %.1f%%, inBossRoom=true, reason=%s) — assuming dead, looking for loot",
                            missingDuration,
                            maxCorpHpPercentThisKill * 100.0,
                            lastObservedCorpHpPercent * 100.0,
                            reason));
                    // 1.9.99.99: dedicated sanity log for the coordinator-
                    // confirm path. Surfaces the team-sync drift so we can
                    // verify the cross-bot kill_id flow once we run with
                    // teammates. drift = teamKillId - localKillId at the
                    // moment of transition. Anything > 1 means we missed
                    // multiple kills (e.g. extended bank trip). User: "yes
                    // add the sanity log even though we arnt running with
                    // a cordinator yet."
                    if (teammateConfirmedDeath) {
                        long drift = teamKillId - localKillId;
                        Log.warn("COORDINATOR-CONFIRM: team finished without us. "
                                + "localKillId " + localKillId + " → " + (localKillId + 1)
                                + " (team at " + teamKillId
                                + ", drift " + drift + " kill"
                                + (drift == 1 ? "" : "s") + ")"
                                + (drift > 1
                                        ? " — multi-kill drift; will resync at +1/LOOTING"
                                        : ""));
                    }
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                } else {
                    // 1.9.99.105: throttle to once per 2s. Pre-throttle
                    // this fired every tick (~20Hz) and buried real log
                    // lines. The overlay's death-detect block shows the
                    // same fields live, so the log is just a backup.
                    long nowMsDiag = System.currentTimeMillis();
                    if (nowMsDiag - lastCorpMissingDiagAt > 2000) {
                        Log.debug(String.format("Corp not in render but no confirmed 0 HP (inBossRoom=%s, peakHP=%.1f%%, lastHP=%.1f%%, missing=%dms, teamKillId=%d, localKillId=%d) — waiting",
                                inBossRoom,
                                maxCorpHpPercentThisKill * 100.0,
                                lastObservedCorpHpPercent * 100.0,
                                missingDuration,
                                teamKillId,
                                localKillId));
                        lastCorpMissingDiagAt = nowMsDiag;
                    }
                }
            }
        }
    }

    // ========== ENHANCED DEBUGGING METHOD ==========
    private void debugDarkCoreSystem() {
        if (currentState == BotState.HANDLING_DARK_CORE) {
            Log.info("=== DARK CORE DEBUG ===");
            Log.info("Chosen axis: " + chosenDodgeAxis);
            Log.info("Last dodge direction: " + lastDodgeDirection);
            Log.info("Core dodge state: " + coreDodgeState);
            Log.info("Distance history size: " + coreDistanceHistory.size());

            if (!coreDistanceHistory.isEmpty()) {
                Double[] distances = coreDistanceHistory.toArray(new Double[0]);
                Log.info("Recent distances: " + Arrays.toString(distances));

                if (distances.length >= 2) {
                    boolean approaching = distances[distances.length - 1] < distances[distances.length - 2];
                    Log.info("Core approaching: " + approaching);
                }
            }

            Optional<Npc> coreOpt = findDarkCore();
            if (coreOpt.isPresent()) {
                WorldTile corePos = coreOpt.get().getTile();
                WorldTile myPos = MyPlayer.getTile();
                Log.info("Core position: " + corePos + " (distance: " + myPos.distanceTo(corePos) + ")");
            }

            Log.info("=== END DEBUG ===");
        }
    }

    // ========== DARK CORE: DISPATCHER + MODERN STRATEGY (Phase G) ==========

    /** Entry point from the state machine. Branches between the modern
     *  attack-and-step strategy (current meta) and the legacy on-tile dodge
     *  (preserved as a fallback under settings.useLegacyDarkCoreLogic). */
    private void handleAdvancedDarkCore() {
        if (settings.useLegacyDarkCoreLogic) {
            handleAdvancedDarkCoreLegacy();
            return;
        }
        handleAdvancedDarkCoreModern();
    }

    /** 2026 meta: equip Elder maul / DWH on core spawn, the bot the core
     *  jumped to attacks it, then steps away so the core dies mid-air and
     *  doesn't respawn. Non-targeted bots hold the kill weapon ready. */
    private void handleAdvancedDarkCoreModern() {
        // 1.9.64: turn auto-retaliate OFF for the duration of the core
        // engagement. User: 'we eat cuz we are low then we click on
        // the core and then our auto retaliate makes us hit the corp
        // over and over again.' With auto-retaliate ON, Corp's next
        // hit yanks the bot's target back to Corp the tick after we
        // click the core, so the core attack never actually fires —
        // bot just stands there auto-attacking Corp while the core
        // lands. We turn it back on when the core is gone (after the
        // 3s grace timer below).
        // 1.9.73: throttle the auto-retaliate disable. Pre-1.9.73 this
        // fired every tick because Combat.setAutoRetaliate(false) may
        // not actually persist on some clients — isAutoRetaliateOn()
        // returned true again next tick. Spam-clicking the auto-retaliate
        // button each tick fights the core attack click. Now: only
        // attempt the disable once per core engagement, on the same
        // tick we transition into the handler. User: 'core handling
        // is a little better, although its still a little wonky between
        // staying alive, disable auto retliate and trying to kill the
        // core.'
        // 1.9.99.158: VERIFY the disable actually took effect. Pre-1.9.99.158
        // the flag flipped to true unconditionally after the click — if
        // setAutoRetaliate(false) silently failed (settings tab not open,
        // SDK widget race, etc.) auto-retaliate stayed ON for the entire
        // core engagement. User: "the flow from attacking corp returns
        // true even while we are supposed to be killing core. so we
        // attack the core but before our attack goes off we are back on
        // corp which means we probably arnt disabling our auto retaliate".
        // Corp's next hit retaliates → bot's interacting target flips to
        // Corp → core click cancels → loop. Now: only flip the latch
        // when isAutoRetaliateOn() reads false. Otherwise keep retrying
        // every tick until confirmed off.
        if (!autoRetaliateDisabledForThisCore) {
            try {
                if (Combat.isAutoRetaliateOn()) {
                    Log.info("Dark core present — disabling auto-retaliate");
                    Combat.setAutoRetaliate(false);
                    // Short verification poll. Don't sit forever — if the
                    // disable just won't take, fall through and try again
                    // next tick.
                    Waiting.waitUntil(600, () -> !Combat.isAutoRetaliateOn());
                }
                // Only latch the one-shot when actually verified off.
                if (!Combat.isAutoRetaliateOn()) {
                    autoRetaliateDisabledForThisCore = true;
                } else {
                    Log.warn("Auto-retaliate still ON after disable attempt — "
                            + "will retry next tick");
                }
            } catch (Exception ignored) {}
        }

        // 1.9.58: only eat when HEALTH IS CRITICAL during core. Pre-1.9.58
        // we eat-combo'd at HP <= INTERNAL_EMERGENCY_HP (50) AND at HP <=
        // eatHealthThreshold + 20 (~41). With Corp magic hits coming in
        // every few ticks the bot ping-ponged: eat -> attack queued ->
        // eat overrides attack click -> next tick eat again -> attack
        // never lands. User: 'We were also having issues with the core
        // between trying to eat and then attack and then eat and then
        // attack and the attack was never actually going off until we
        // lost a lot of health.' Now: skip the proactive eat when HP is
        // merely below eat-threshold; only emergency-eat when HP <=
        // INTERNAL_EMERGENCY_HP (true panic). The jump-kill on the core
        // is what saves us; eating instead of attacking lets the core
        // land and do more damage than the food restores.
        int currentHealth = MyPlayer.getCurrentHealth();
        if (currentHealth <= INTERNAL_EMERGENCY_HP) {
            Log.warn("CRITICAL HEALTH during dark core - emergency combo eating!");
            emergencyComboEatDuringMovement();
        }
        // Prayer only if actually low — same as before but no buffer.
        if (Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD) {
            drinkPrayerPotionDuringMovement();
        }

        // 1.9.44: hysteresis on "core gone" before swapping back. The core
        // ducks in and out of render between ticks, and pre-1.9.44 every
        // out-of-render tick triggered a full Fang swap that immediately
        // got reverted to maul next tick. User: 'the state couldnt decide
        // if we were fighting core or killing corp ... ideally we can
        // check the distance ... and if its the closest to us just keep
        // our elder maul out so we dont accidently get stuck in a state
        // of toggling back and forth between weapons.' Now: stay in
        // HANDLING_DARK_CORE (maul equipped) for 3 seconds after the
        // last sighting before reverting to Fang.
        final long CORE_GRACE_MS = 3000;
        // 1.9.99.123: exit condition. Once we entered HANDLING_DARK_CORE
        // via the threat-gated trigger (1.9.99.118), we used to stay
        // until the core completely despawned. But a teammate stunning
        // the core away leaves it in render at a safe distance — and we
        // kept sitting in HANDLING_DARK_CORE indefinitely with whatever
        // weapon happened to be equipped (Arclight from a spec dump, etc.)
        // and melee-stabbed Corp with the wrong weapon. Re-check each
        // tick: if the core is no longer threatening, bail back to
        // FIGHTING_CORP so the normal combat flow (Fang swap, spec re-arm,
        // etc.) takes over. User: "if its 2.5 tiles away diagonalyl it
        // isnt touching us but we detect ti as being there ... we are
        // gettign stuck with the wrong weapon out."
        // 1.9.99.204: skip the early bail when we've already committed to
        // attacking THIS core. The latch is set after a successful Attack
        // click below; it's cleared only when the core actually despawns
        // past CORE_GRACE_MS (handled in the no-coreOpt branch). User rule:
        // "we only need to reclick if we are no longer interacting with
        // them OR if we got pushed under the corp and ran away ... When
        // core is near us we disable auto retliate so it should be our
        // target until its dead." Bouncing back to FIGHTING_CORP because
        // dist briefly crossed 1.5 (teammate stunned the core away, core
        // drifted, etc.) caused the alternating Corp/core attacks the user
        // reported.
        if (!isDarkCoreThreatening() && !coreEngagementCommitted) {
            Log.info("Dark core no longer threatening (>1.5 tiles or gone) — exit to FIGHTING_CORP");
            try {
                if (!Combat.isAutoRetaliateOn()) Combat.setAutoRetaliate(true);
            } catch (Exception ignored) {}
            autoRetaliateDisabledForThisCore = false;
            currentState = BotState.FIGHTING_CORP;
            return;
        }

        Optional<Npc> coreOpt = findDarkCore();
        if (!coreOpt.isPresent()) {
            long sinceSeen = System.currentTimeMillis() - darkCoreLastSeen;
            if (darkCoreLastSeen > 0 && sinceSeen < CORE_GRACE_MS) {
                // 1.9.99.23: DPS Corp while waiting in the grace period.
                // Pre-1.9.99.23 the bot stood AFK during the entire grace
                // window (up to 3s) — auto-retaliate is off (1.9.64) for
                // core handling, so no swings were happening at all. User:
                // "we got stuck in a state of the core being out so we
                // were just standing there waiting for it... kinda afk".
                // Player keeps attacking Corp; the next tick's check
                // swaps weapons when core re-appears close.
                Optional<Npc> corpDuringGrace = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
                if (corpDuringGrace.isPresent() && isCorpAlive(corpDuringGrace.get())
                        && !isPlayerAttackingCorp(corpDuringGrace.get())) {
                    attackCorpIfVisible(corpDuringGrace.get());
                }
                // 1.9.99.62: throttled — was firing every tick (10+/sec)
                // and burying useful logs around dark-core events.
                if (System.currentTimeMillis() - lastDarkCoreLogAt > 1000) {
                    Log.debug("Dark core not visible (since="
                            + sinceSeen + "ms < " + CORE_GRACE_MS
                            + "ms grace) - holding kill weapon, DPSing Corp");
                    lastDarkCoreLogAt = System.currentTimeMillis();
                }
                return;
            }
            // 1.9.99.51: after the core, re-equip the SPEC WEAPON if we
            // still have spec-phase work to do AND we own a usable spec
            // for the current phase. Pre-1.9.99.51 we always swapped to
            // Fang (main weapon) — fine for kill phase, but during an
            // active BGS phase the bot would auto-attack with Fang for 1-2
            // ticks before the main loop re-equipped BGS for its next
            // spec. User: "at one point in the bgs phase it switched to
            // another weapon, and started poking with eitehr the arclight
            // or fang and eventually went back to the bgs ... maybe
            // switchign to the elder maul when core spawne dbroke
            // something?". Yes — equipMainWeaponFast went to Fang, not
            // back to the phase's spec weapon. Now we route through the
            // chosen spec weapon when one is needed.
            String corePostWeapon = pickSpecWeaponForCurrentPhase();
            if (corePostWeapon != null
                    && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()) {
                Log.info("Dark core gone (>" + CORE_GRACE_MS
                        + "ms grace expired) - re-equipping spec weapon "
                        + corePostWeapon + " (phase " + teamPhaseNeeded()
                        + " still active, energy "
                        + Combat.getSpecialAttackPercent() + "%)");
                chosenSpecWeapon = corePostWeapon;
                equipSpecWeapon();
            } else {
                Log.info("Dark core gone (>" + CORE_GRACE_MS
                        + "ms grace expired) - re-equipping main weapon");
                equipMainWeaponFast();
            }
            // 1.9.64: re-enable auto-retaliate now that the core's gone.
            try {
                if (!Combat.isAutoRetaliateOn()) {
                    Log.info("Core gone — re-enabling auto-retaliate");
                    Combat.setAutoRetaliate(true);
                }
            } catch (Exception ignored) {}
            // 1.9.73: reset latch so the next core triggers a fresh disable.
            autoRetaliateDisabledForThisCore = false;
            coreEngagementCommitted = false; // 1.9.99.204: core actually despawned
            currentState = BotState.FIGHTING_CORP;
            return;
        }
        Npc core = coreOpt.get();
        darkCoreLastSeen = System.currentTimeMillis();

        // 1.9.60: keep Fang+defender equipped UNLESS the core is on us
        // or about to land. User: 'we should keep the fang and defender
        // out until the core actually lands on us or is focusing us.
        // otherwise we just end up afking hitting the corp with our 2h
        // weapon.' Pre-1.9.60 we equipped the maul as soon as the core
        // was visible to ANYBODY, even when it was clearly chasing a
        // teammate — and during that window the bot DPS'd Corp with the
        // slower 2H instead of Fang.
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null) return;
        double dist = myPos.distanceTo(core.getTile());
        final double CORE_PROXIMITY_THRESHOLD = 2.5; // close enough that it's likely landing on us next tick
        boolean coreOnOrApproaching = dist <= CORE_PROXIMITY_THRESHOLD;

        if (!coreOnOrApproaching) {
            // 1.9.99.116: spec dump while teammate handles core. If we
            // still have phase work + spec energy + spec weapon equipped,
            // re-arm spec and DPS Corp — the next swing fires spec. Pre-
            // 1.9.99.116 the bot stayed HANDLING_DARK_CORE for the entire
            // core engagement with Arclight equipped, melee-stabbing Corp
            // and never firing further specs. User log: 90s of "Distant
            // core (dist=4.0) — DPSing Corp" with no spec fires while
            // Arclight was equipped + phase 2 incomplete + spec full.
            // User: "The bot was stabbing the corp to death with its
            // arclight; but its state was handling core (it was supposed
            // to be spec dumping); but a real player stunned the core.
            // more than 2 tiles away and we just stood there like a
            // dummy with the wrong weapon."
            String phaseWeapon = pickSpecWeaponForCurrentPhase();
            boolean haveSpecEnergy = Combat.getSpecialAttackPercent() >= getMinSpecEnergy();
            boolean phaseWeaponEquipped = phaseWeapon != null
                    && Equipment.contains(phaseWeapon);
            if (phaseWeapon != null && haveSpecEnergy && phaseWeaponEquipped
                    && !isInKillPhase()) {
                if (!Combat.isSpecialAttackEnabled()) {
                    tryActivateSpec();
                }
                Optional<Npc> corpSpec = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
                if (corpSpec.isPresent() && isCorpAlive(corpSpec.get())) {
                    if (System.currentTimeMillis() - lastDarkCoreLogAt > 1000) {
                        Log.debug("Distant core (dist=" + dist + ") — spec-dumping "
                                + phaseWeapon + " on Corp (teammate handling core)");
                        lastDarkCoreLogAt = System.currentTimeMillis();
                    }
                    attackCorpIfVisible(corpSpec.get());
                }
                return;
            }

            // Core is far / on a teammate — keep Fang on for Corp DPS.
            // If we accidentally have the maul equipped from a previous
            // tick, swap back to Fang.
            if (isCoreKillWeaponEquipped() && !specWeaponSwitchQueued) {
                Log.info("Dark core present but distant (dist=" + dist
                        + ") - swapping back to Fang for Corp DPS");
                queueSpecWeaponSwitchBack();
            }
            // 1.9.99.59: keep DPSing Corp while core focuses a teammate.
            // 1.9.99.116: keep this melee-DPS branch as the fallback when
            // spec dump conditions aren't met (no phase work, no energy,
            // kill phase, wrong weapon).
            Optional<Npc> corpForDistantCore = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpForDistantCore.isPresent() && isCorpAlive(corpForDistantCore.get())
                    && !isPlayerAttackingCorp(corpForDistantCore.get())) {
                // 1.9.99.62: throttled
                if (System.currentTimeMillis() - lastDarkCoreLogAt > 1000) {
                    Log.debug("Distant core (dist=" + dist + ") — DPSing Corp via attack click");
                    lastDarkCoreLogAt = System.currentTimeMillis();
                }
                attackCorpIfVisible(corpForDistantCore.get());
            }
            return;
        }

        // 1.9.99.118: NO weapon swap. Pre-1.9.99.118 we equipped Maul/DWH
        // for the "jump kill", but the swap costs ~1 tick which is more
        // time than the kill saves at high melee bonus. User mechanic:
        // "as soon as your attack goes off you click ATLEAST 2 tiles
        // away" + "this is making me think that maybe since its a thing
        // that happens in a quick amount of time; that we shouldnt even
        // try to switch to a heavier weapon like bgs/elder maul because
        // that requires precious time." Attack with whatever's equipped
        // (Fang/Arclight/BGS — all hit hard enough at 99 strength + bonus
        // gear to one-shot the ~100 HP core).

        if (dist > 1.5) {
            // 1.9.99.200: PRE-CLICK THE CORE instead of clicking Corp.
            // Pre-1.9.99.200 we attacked Corp here ("DPS Corp while waiting
            // for the core to land"), which created the user-reported bug:
            // "attacking corp and then attacking core before the core is
            // dead." Sequence was (a) we click Corp at dist 2.0,
            // (b) bot swings Corp, (c) core lands adjacent, (d) bot now
            // attacks core. The Corp swing at step (b) is a free hit
            // during a phase where we should be focused on killing the
            // core.
            //
            // Now: queue the core attack as soon as core is in render and
            // approaching (dist <= 2.5). The interact("Attack") click
            // walks us toward the core if needed and fires the swing the
            // moment we're adjacent. Auto-retaliate is OFF for this state
            // (1.9.64), so we won't bounce back to Corp.
            //
            // Guard: don't re-click if already interacting with the core
            // (cheap idempotency — same as the dist <= 1.5 branch below).
            boolean alreadyOnCore = false;
            try {
                Optional<org.tribot.script.sdk.types.Player> me = MyPlayer.get();
                if (me.isPresent()) {
                    Optional<org.tribot.script.sdk.interfaces.Character> tgt =
                            me.get().getInteractingCharacter();
                    if (tgt.isPresent() && tgt.get().getName() != null
                            && tgt.get().getName().equalsIgnoreCase(DARK_CORE)) {
                        alreadyOnCore = true;
                    }
                }
            } catch (Throwable ignored) {}
            if (!alreadyOnCore) {
                // 1.9.99.229: force-tile then interact. OSRS BFS picks the
                // W melee tile of the core when ties exist (W>E>S>N
                // order), which sits inside Corp's hitbox when the core
                // is touching Corp. Walk to a safe melee tile manually
                // first so click-Attack fires from a position where the
                // bot is already adjacent — no BFS surprise.
                // 1.9.99.231: honor the return value. If we couldn't reach
                // a safe melee tile (wall blocking path, all safe tiles
                // unreachable), DON'T click — wait for next tick when the
                // core/Corp may have moved.
                boolean safeToClick = false;
                try {
                    Optional<Npc> corpForDetour = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
                    if (corpForDetour.isPresent()) {
                        safeToClick = walkToSafeCoreMeleeTile(core, corpForDetour.get());
                    } else {
                        safeToClick = true; // no Corp visible → no stomp risk
                    }
                } catch (Throwable ignored) {}
                if (!safeToClick) {
                    if (System.currentTimeMillis() - lastDarkCoreLogAt > 1000) {
                        Log.info("Dark core pre-click skipped — no safe melee tile reachable "
                                + "(retrying next tick)");
                        lastDarkCoreLogAt = System.currentTimeMillis();
                    }
                    return;
                }
                if (core.interact("Attack")) {
                    lastCoreAttackClickAt = System.currentTimeMillis();
                    coreEngagementCommitted = true; // 1.9.99.204 latch
                    if (System.currentTimeMillis() - lastDarkCoreLogAt > 1000) {
                        Log.info("Dark core close (dist=" + dist + ") - pre-clicking core "
                                + "(focus core before it lands, no Corp hits)");
                        lastDarkCoreLogAt = System.currentTimeMillis();
                    }
                }
            }
            return;
        }

        // 1.9.99.124: simplified. Just click Attack — re-evaluate next tick.
        // 1.9.99.146: cleaner debounce. Pre-1.9.99.145 the click fired every
        // tick (~20Hz). 1.9.99.145 added a 1500ms timestamp + isAnimating
        // gate, but user pointed out we don't need that: auto-retaliate
        // is OFF during core handling, so once we're interacting with
        // the core, we stay on it until it dies. Only re-click if we
        // LOST the target (initial click hasn't registered yet, or we
        // got pushed under Corp and ran away). User: "we only need to
        // reclick if we are no longer interacting with them OR if we
        // got pushed under the corp and ran away ... When core is near
        // us we disable auto retliate so it should be our target until
        // its dead."
        try {
            Optional<org.tribot.script.sdk.types.Player> me = MyPlayer.get();
            if (me.isPresent()) {
                Optional<org.tribot.script.sdk.interfaces.Character> tgt =
                        me.get().getInteractingCharacter();
                if (tgt.isPresent() && tgt.get().getName() != null
                        && tgt.get().getName().equalsIgnoreCase(DARK_CORE)) {
                    return; // already attacking the core
                }
            }
        } catch (Throwable ignored) {}
        // 1.9.99.229: force-tile then interact. OSRS BFS uses W>E>S>N
        // cardinal preference for tile ties, so the engine deterministically
        // picks the W melee tile of the core when the bot is NW of it —
        // and that W tile lies INSIDE Corp's hitbox when the core is
        // hugging Corp. Walk to a safe melee tile manually so the click
        // doesn't trigger BFS at all. User: "if we click to attack the
        // core; 100% of the time it will walk us into the corps hitbox".
        // 1.9.99.231: honor the return value. If we couldn't reach a safe
        // melee tile (wall blocking, all safe tiles unreachable), DON'T
        // click — return and let next tick re-evaluate when core/Corp
        // may have moved. User: "What if we are up against the wall ...
        // we would still try to kill it ... which would cuz is to get
        // stomped and die?"
        boolean safeToClick = false;
        try {
            Optional<Npc> corpForCoreWalk = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpForCoreWalk.isPresent()) {
                safeToClick = walkToSafeCoreMeleeTile(core, corpForCoreWalk.get());
            } else {
                safeToClick = true; // no Corp visible → no stomp risk
            }
        } catch (Throwable ignored) {}
        if (!safeToClick) {
            Log.warn("Dark core attack skipped — no safe melee tile reachable "
                    + "(retrying next tick, core/Corp may move)");
            return;
        }
        Log.info("Dark core adjacent (dist=" + dist + ") - attacking with current weapon");
        if (!core.interact("Attack")) {
            Log.warn("Failed to interact with dark core - retrying next tick");
            return;
        }
        lastCoreAttackClickAt = System.currentTimeMillis();
        coreEngagementCommitted = true; // 1.9.99.204 latch
        // 1.9.99.127: the core attack frequently pushes us into Corp's
        // hitbox (we walk toward the core, end up adjacent to / under
        // Corp's 5x5). The top-of-main-loop antiStompTick has the
        // 1.9.99.114 stability gate (>=2 consecutive overlaps), which
        // eats 1-2 ticks before stepping out. During that window Corp's
        // stomp can land. Do an IMMEDIATE direct check here that
        // bypasses the stability gate — if we're under Corp right after
        // the core attack, step off NOW. User: "sometimes when we
        // attack the core it puts us under the corp and we end up
        // taking stomp damage. we should instantly run out if we are
        // under him."
        Optional<Npc> corpCheck = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpCheck.isPresent()) {
            try {
                Area cArea = corpCheck.get().getArea();
                WorldTile myT = MyPlayer.getTile();
                if (cArea != null && myT != null && cArea.contains(myT)) {
                    Log.warn("Post-core-attack: standing under Corp — immediate step-off (bypass stability gate)");
                    stepOffCorp(corpCheck.get());
                }
            } catch (Exception ignored) {}
        }
    }

    /** 1.9.99.66: weapons strong enough to one-shot the dark core (HP ~25).
     *  Any of these currently equipped means we skip the swap to Elder
     *  maul / DWH. Elder maul, DWH = canonical core killers. BGS,
     *  Noxious halberd, Scythe of vitur, etc. all max well above 25
     *  and despawn the core on a single hit just as effectively. User:
     *  "if we have for example a bgs out because its bgs specing phase,
     *  we dont need to switch to elder maul to kill core. the bgs is
     *  good enough. so is things like the noxus halbard". */
    private static final String[] CORE_KILL_ACCEPTABLE_WEAPONS = {
            "Elder maul",
            "Dragon warhammer",
            "Bandos godsword",
            "Bandos godsword (or)",
            "Noxious halberd",
            "Scythe of vitur",
            "Scythe of vitur (uncharged)",
            "Holy scythe of vitur",
            "Sanguine scythe of vitur",
            "Crystal halberd",
            "Dragon halberd",
            "Saradomin godsword",
            "Armadyl godsword",
            "Zamorak godsword"
    };

    /** True if any weapon strong enough to one-shot the dark core is equipped. */
    private boolean isCoreKillWeaponEquipped() {
        for (String name : CORE_KILL_ACCEPTABLE_WEAPONS) {
            if (Equipment.contains(name)) return true;
        }
        return false;
    }

    /** Wield user's designated core-killer (or fall back to Elder maul /
     *  DWH) from inventory. Returns true if a kill weapon is equipped
     *  after the call. 1.9.99.66: only swaps if NO acceptable core killer
     *  is already equipped (e.g. BGS during Phase 3). 1.9.99.68: also
     *  consults settings.coreKillerWeapon for user preference. */
    private boolean equipCoreKillWeapon() {
        if (isCoreKillWeaponEquipped()) return true;

        // Try the user's designated core killer FIRST, then fall back to
        // Elder maul / DWH (the canonical defaults).
        java.util.LinkedHashSet<String> swapCandidates = new java.util.LinkedHashSet<>();
        if (settings != null && settings.coreKillerWeapon != null
                && !settings.coreKillerWeapon.trim().isEmpty()) {
            swapCandidates.add(settings.coreKillerWeapon.trim());
        }
        swapCandidates.add(ELDER_MAUL);
        swapCandidates.add("Dragon warhammer");

        for (String name : swapCandidates) {
            Optional<InventoryItem> item = Query.inventory().nameEquals(name).findFirst();
            if (item.isPresent()) {
                Log.info("Dark core: wielding kill weapon " + name);
                if (item.get().click("Wield")) {
                    if (Waiting.waitUntil(2000, () -> Equipment.contains(name))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Step away from the core so its jump kills it mid-air. Tries 2-4 tile
     *  offsets in the "away from core" direction. Tiles inside Corp's 5x5
     *  hitbox are skipped — when the bot is adjacent to Corp, all 1-tile
     *  cardinal neighbours land inside the hitbox, which is why the old
     *  1-tile-only search frequently logged "no walkable target". */
    private boolean stepAwayFromCore(Npc core) {
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null || core == null) return false;
        WorldTile corePos = core.getTile();
        if (corePos == null) return false;

        int dx = myPos.getX() - corePos.getX();
        int dy = myPos.getY() - corePos.getY();
        // If we're stacked on the core, pick an arbitrary cardinal.
        int sx = dx == 0 ? 1 : Integer.signum(dx);
        int sy = dy == 0 ? 1 : Integer.signum(dy);

        // Corp's hitbox — we want to step OUTSIDE it.
        // 1.9.99.13: refuse to step-away if we can't read Corp's area.
        // Pre-1.9.99.13 the null-area path accepted tiles literally on
        // Corp's hitbox because the corpArea.contains() check was gated
        // on corpArea != null. User: "i just ran away from the core
        // directly under the corp and got stomped to death with 3 stomps".
        // Better to NOT move and take the core hit than to stomp into Corp.
        Area corpArea = null;
        WorldTile corpCenter = null;
        try {
            Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpOpt.isPresent()) {
                corpArea = corpOpt.get().getArea();
                if (corpArea != null) corpCenter = corpArea.getCenter();
            }
        } catch (Exception ignored) {}
        if (corpArea == null || corpCenter == null) {
            Log.warn("STEP-AWAY: can't read Corp area — refusing to step "
                    + "(would risk walking under Corp). Eating instead.");
            return false;
        }

        // Candidates ordered: long away-vector first (clean step), then
        // medium, then fall back to perpendicular / negative directions if
        // the obvious ones are inside Corp.
        int[][] offsets = {
                { sx * 4, sy * 4 }, { sx * 4, 0 }, { 0, sy * 4 },
                { sx * 3, sy * 3 }, { sx * 3, 0 }, { 0, sy * 3 },
                { sx * 2, sy * 2 }, { sx * 2, 0 }, { 0, sy * 2 },
                { -sx * 3, sy * 3 }, { sx * 3, -sy * 3 },
                { -sx * 2, sy * 2 }, { sx * 2, -sy * 2 },
                { sx, 0 }, { 0, sy }, { -sx, 0 }, { 0, -sy }
        };

        // 1.9.63: dropped the broken isReachable pre-filter.
        // 1.9.80: but added corpCave polygon check so we don't try
        // walking into walls. User: 'we need to have the tile be
        // walkable or else we will try to run into walls.' Cave
        // polygon defines the playable interior; outside = wall.
        // 1.9.99.13: also exclude tiles within N tiles of Corp's center
        // (5x5 hitbox + N-tile buffer). Corp roams; a tile that was just
        // outside the hitbox at decision time may be inside it after our
        // walk completes ~2-3 ticks later.
        // 1.9.99.24: bumped buffer 2→3 tiles (11x11 exclusion = 5-tile
        // distance from Corp center). 2-tile buffer covered Corp moving
        // up to 3 tiles during our walk, but Corp can sometimes roam
        // further (e.g., chasing a teammate that's pulling). User: "one
        // of the moves we made to run away from core after hitting it
        // was 1 tile under the corp" — Corp moved 4+ tiles into our
        // destination during the walk.
        final int CORP_BUFFER = 3;
        int cx = corpCenter.getX(), cy = corpCenter.getY();
        for (int[] o : offsets) {
            if (o[0] == 0 && o[1] == 0) continue;
            WorldTile target = new WorldTile(myPos.getX() + o[0], myPos.getY() + o[1], myPos.getPlane());
            if (corpArea.contains(target)) continue; // inside Corp = stomp damage
            // 1.9.99.13: padded exclusion — Corp's 5x5 area extends 2 tiles
            // in each direction from the center, so anything within
            // |dx| + |dy| <= 4 of center is risky if Corp roams toward us.
            int tdx = Math.abs(target.getX() - cx);
            int tdy = Math.abs(target.getY() - cy);
            if (tdx <= 2 + CORP_BUFFER && tdy <= 2 + CORP_BUFFER) continue;
            if (!corpCave.contains(target)) continue; // outside cave polygon = wall
            try {
                if (LocalWalking.walkTo(target)) {
                    Log.info("STEP-AWAY: moving to " + target
                            + " (Corp center " + cx + "," + cy + ")");
                    return true;
                }
            } catch (Exception ignored) {}
        }
        Log.warn("STEP-AWAY: every candidate walk failed (core may be cornering us)");
        return false;
    }

    /** 1.9.99.72: step 5+ tiles off Corp's hitbox to break the
     *  eat-attack-eat death spiral. Triggered from handleHealthAndPrayer
     *  when two emergency eats fire within EMERGENCY_EAT_SPIRAL_WINDOW_MS.
     *  Distance gives ~3 ticks of safety before Corp closes back into
     *  melee range — enough for HP regen / vengeance / a heal to land.
     *  Returns true if a walk was issued; caller sets
     *  panicRetreatActiveUntil so handleFightingCorp pauses re-engage. */
    private boolean panicRetreatFromCorp() {
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null) return false;
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return false;
        Npc corp = corpOpt.get();
        Area corpArea = null;
        WorldTile corpCenter = null;
        try {
            corpArea = corp.getArea();
            if (corpArea != null) corpCenter = corpArea.getCenter();
        } catch (Exception ignored) {}
        if (corpArea == null || corpCenter == null) {
            Log.warn("PANIC-RETREAT: can't read Corp area — skipping");
            return false;
        }

        int dx = myPos.getX() - corpCenter.getX();
        int dy = myPos.getY() - corpCenter.getY();
        // Stacked-on-center fallback: pick an arbitrary outward vector.
        int sx = dx == 0 ? 1 : Integer.signum(dx);
        int sy = dy == 0 ? 1 : Integer.signum(dy);

        // Outward-only offsets. Backward offsets (e.g. {-sx*4, sy*4})
        // were removed in 1.9.99.72-amend: they retreat on one axis but
        // pull us toward Corp on the perpendicular axis, leaving us
        // exposed if Corp roams during the walk. Stick to vectors that
        // never decrease tile distance from Corp center on either axis.
        int[][] offsets = {
                { sx * 5, sy * 5 }, { sx * 5, 0 }, { 0, sy * 5 },
                { sx * 4, sy * 4 }, { sx * 4, 0 }, { 0, sy * 4 },
                { sx * 3, sy * 3 }, { sx * 3, 0 }, { 0, sy * 3 },
        };
        int cx = corpCenter.getX(), cy = corpCenter.getY();
        int myTdx = Math.abs(myPos.getX() - cx);
        int myTdy = Math.abs(myPos.getY() - cy);
        // Corp hitbox is 5x5 (extends 2 tiles from center). Match
        // stepAwayFromCore's CORP_BUFFER=3 → require tdx > 5 OR tdy > 5
        // (i.e. at least 4 tiles past hitbox edge on whichever axis we
        // retreat along) so Corp can't pace-move into our destination
        // during the walk.
        final int CORP_BUFFER = 3;
        final int MIN_AXIS_DIST = 2 + CORP_BUFFER; // = 5 from center
        for (int[] o : offsets) {
            WorldTile target = new WorldTile(
                    myPos.getX() + o[0], myPos.getY() + o[1], myPos.getPlane());
            // (1) Never inside Corp's CURRENT hitbox.
            if (corpArea.contains(target)) continue;
            int tdx = Math.abs(target.getX() - cx);
            int tdy = Math.abs(target.getY() - cy);
            // (2) Defense-in-depth: destination must not be closer to
            //     Corp on either axis than where we already stand.
            //     Catches edge cases where the chosen sign was wrong
            //     (e.g. player stacked on Corp center, fallback sx=sy=1).
            if (tdx < myTdx || tdy < myTdy) continue;
            // (3) Buffer past hitbox edge — same rule stepAwayFromCore
            //     uses. Skip if BOTH axes are within MIN_AXIS_DIST of
            //     center (i.e. Corp could roam onto us mid-walk).
            if (tdx <= MIN_AXIS_DIST && tdy <= MIN_AXIS_DIST) continue;
            // (4) Stay inside the cave polygon (no walking into walls).
            if (!corpCave.contains(target)) continue;
            try {
                if (LocalWalking.walkTo(target)) {
                    Log.info("PANIC-RETREAT: walking to " + target
                            + " (Corp center " + cx + "," + cy
                            + ", parking " + PANIC_RETREAT_PARK_MS + "ms)");
                    return true;
                }
            } catch (Exception ignored) {}
        }
        // 1.9.99.204: when all 9 "ideal" outward retreats fail (typically in
        // corner positions where the cave wall blocks every directly-outward
        // tile + MIN_AXIS_DIST buffer), fall back to stepOffCorp — get out
        // of Corp's hitbox to ANY safe adjacent tile inside the cave even if
        // we don't satisfy the 5-tile-from-center buffer. Better to take
        // a stomp from 3 tiles away than to keep eating stomps directly
        // under Corp because the picker bailed. The death log at
        // (2993, 4378) → corp center (2996, 4381) showed every outward
        // offset landing outside the cave polygon. stepOffCorp uses tighter
        // adjacent-tile checks that succeed in those corners.
        Log.warn("PANIC-RETREAT: no ideal retreat tile — falling back to stepOffCorp");
        try {
            if (stepOffCorp(corp)) return true;
        } catch (Throwable ignored) {}
        Log.warn("PANIC-RETREAT: stepOffCorp fallback also failed — stuck");
        return false;
    }

    // ========== DEATH RECOVERY (Phase H) ==========
    // If we die at Corp while teammates are still fighting, we:
    //   1. Walk to a bank (Ferox), grab Games necklace + food + prayer pots.
    //   2. Tele back to Corp via Games necklace.
    //   3. Pray Protect-from-magic ON before entering the room.
    //   4. Loot our gravestone (returns gear to inventory).
    //   5. Re-equip main weapon + defender.
    //   6. Resume FIGHTING_CORP.

    /** Timestamp of the last tick where we observed our own HP hit 0.
     *  Used as the trigger gate for death detection — gravestones alone
     *  aren't enough because random gravestones can appear anywhere. */
    private long lastHpZeroAt = 0;

    /** Trigger DEATH_RECOVERY when we saw HP=0 recently AND we've ended up
     *  somewhere we shouldn't be (not Corp, not Ferox). The HP=0 gate
     *  rules out false positives from stray gravestones at random banks. */
    private boolean detectDeath() {
        // Already recovering — don't re-trigger.
        if (currentState == BotState.DEATH_RECOVERY) return false;

        int currentHp;
        try { currentHp = MyPlayer.getCurrentHealth(); } catch (Exception e) { currentHp = 1; }
        if (currentHp <= 0) {
            lastHpZeroAt = System.currentTimeMillis();
        }

        // Death has to have happened recently. After 60s, any leftover state
        // gets cleared — the user can manually recover if our handler missed it.
        if (lastHpZeroAt == 0 || System.currentTimeMillis() - lastHpZeroAt > 60_000) {
            return false;
        }

        // We're at Corp or banking; not death.
        if (corpCave.containsMyPlayer()) return false;
        try { if (isAtFeroxEnclave()) return false; } catch (Exception ignored) {}

        // Ignore intentional-transit states so we don't re-enter recovery
        // while the recovery flow itself is moving us around.
        switch (currentState) {
            case BANKING_AND_HEALING:
            case TRAVELING_TO_CORP:
            case PREPARING_RESTORATION_CYCLE:
            case TELEPORTING_TO_HOUSE:
            case ENTERING_FRIEND_HOUSE:
            case USING_ORNATE_POOL:
            case TELEPORTING_BACK_TO_CORP:
            case W330_RESTORATION:
            case STARTING:
                return false;
            default:
                break;
        }

        return true;
    }

    /** Tracks recovery sub-progress inside the single DEATH_RECOVERY state. */
    private enum DeathRecoveryStep {
        TO_BANK, WITHDRAW, TELE_TO_CORP, LOOT_GRAVE, REEQUIP, DONE
    }
    private DeathRecoveryStep deathStep = DeathRecoveryStep.TO_BANK;
    // 1.9.99.204: throttle TO_BANK teleport attempts. Pre-fix, when the bot
    // had no Ring of Dueling, handleDeathRecovery looped at full main-loop
    // speed calling teleportToFeroxEnclave() → "No Ring of Dueling found"
    // → return false → main loop next tick → same thing. 200+ identical
    // log lines in 10s with no progress and no exit. Throttle + failure
    // counter + session-end fallback so we either succeed, walk, or bail
    // gracefully.
    private long lastDeathTeleportAttemptAt = 0;
    private int consecutiveDeathTeleportFailures = 0;
    private static final long DEATH_TELE_RETRY_MS = 3000;
    private static final int DEATH_TELE_MAX_FAILURES = 8;

    private void handleDeathRecovery() {
        Log.info("=== DEATH RECOVERY (" + deathStep + ") ===");

        // Always keep mage prayer on once we have prayer points — Corp's
        // magic hit will one-shot a recovering bot if we leave it off.
        if (Prayer.getPrayerPoints() > 0 && !Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
            try { Prayer.PROTECT_FROM_MAGIC.enable(); } catch (Exception ignored) {}
        }

        switch (deathStep) {
            case TO_BANK:
                if (isAtFeroxEnclave() || isNearFeroxBank()) {
                    deathStep = DeathRecoveryStep.WITHDRAW;
                    consecutiveDeathTeleportFailures = 0; // 1.9.99.204
                    return;
                }
                // 1.9.99.204: throttle so we don't spam 200 log lines / 10s
                // when ring is missing. Try every 3s; after 8 consecutive
                // failures (~24s) signal session-end so teammates wrap up
                // and we exit gracefully instead of looping forever.
                long nowDR = System.currentTimeMillis();
                if (nowDR - lastDeathTeleportAttemptAt < DEATH_TELE_RETRY_MS) {
                    return;
                }
                lastDeathTeleportAttemptAt = nowDR;
                if (teleportToFeroxEnclave()) {
                    consecutiveDeathTeleportFailures = 0;
                    Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
                } else {
                    consecutiveDeathTeleportFailures++;
                    Log.warn("Death recovery: teleport attempt "
                            + consecutiveDeathTeleportFailures + "/"
                            + DEATH_TELE_MAX_FAILURES + " failed");
                    if (consecutiveDeathTeleportFailures >= DEATH_TELE_MAX_FAILURES) {
                        Log.error("Death recovery: teleport repeatedly failed "
                                + "(no ring or out of charges) — signaling session end");
                        signalSessionEnd("Death recovery: teleport unavailable");
                        consecutiveDeathTeleportFailures = 0;
                        deathStep = DeathRecoveryStep.TO_BANK; // reset for next time
                        lastHpZeroAt = 0;
                        currentState = BotState.EMERGENCY_ESCAPE;
                    }
                }
                return;

            case WITHDRAW:
                if (!Bank.isOpen()) {
                    // 1.9.14: settle delay before clicking — see Step 4 above.
                    Waiting.waitNormal(700, 200);
                    Optional<GameObject> chest = Query.gameObjects().nameContains("Bank chest").findFirst();
                    if (chest.isPresent()) {
                        if (chest.get().interact("Use") || chest.get().interact("Bank")) {
                            Waiting.waitUntil(6000, () -> Bank.isOpen());
                        }
                    } else if (Bank.open()) {
                        Waiting.waitUntil(3000, () -> Bank.isOpen());
                    }
                    return;
                }

                // Hard-stop check: if there are no Games necklaces in the bank
                // at all, we cannot recover. Signal session-end via coordinator
                // so teammates wrap up gracefully, then bail to EMERGENCY_ESCAPE.
                if (!bankHasGamesNecklace()) {
                    signalSessionEnd("Out of Games necklaces (death recovery bank check)");
                    Bank.close();
                    Waiting.waitUntil(2000, () -> !Bank.isOpen());
                    deathStep = DeathRecoveryStep.TO_BANK; // reset for next time
                    lastHpZeroAt = 0;
                    currentState = BotState.EMERGENCY_ESCAPE;
                    return;
                }

                // Minimum supplies to survive the round-trip and the recovery walk.
                withdrawForDeathRecovery();
                Bank.close();
                Waiting.waitUntil(2000, () -> !Bank.isOpen());
                deathStep = DeathRecoveryStep.TELE_TO_CORP;
                return;

            case TELE_TO_CORP:
                Optional<InventoryItem> necklace = Query.inventory()
                        .nameContains("Games necklace(").findFirst();
                if (!necklace.isPresent()) {
                    Log.warn("Death recovery: no Games necklace after bank — re-banking");
                    deathStep = DeathRecoveryStep.WITHDRAW;
                    return;
                }
                if (necklace.get().click("Corporeal Beast")) {
                    Waiting.waitUntil(10000, () -> isAtCorp());
                }
                if (isAtCorp()) {
                    deathStep = DeathRecoveryStep.LOOT_GRAVE;
                }
                return;

            case LOOT_GRAVE:
                Optional<GameObject> grave = Query.gameObjects()
                        .nameContains("Gravestone").findFirst();
                if (!grave.isPresent()) {
                    // Gravestone gone (timed out or already looted) — re-equip what we have.
                    deathStep = DeathRecoveryStep.REEQUIP;
                    return;
                }
                if (grave.get().interact("Loot")) {
                    Waiting.waitUntil(8000, () ->
                            !Query.gameObjects().nameContains("Gravestone").isAny());
                }
                return;

            case REEQUIP:
                // Items come back to inventory, not auto-wielded — re-wield the basics.
                equipMainWeaponFast();
                // 1.9.99.148: skip defender re-equip when main is 2H (spear).
                if (!isMainWeaponTwoHanded() && !hasDefenderEquipped()) equipAnyDefender();
                deathStep = DeathRecoveryStep.DONE;
                return;

            case DONE:
                Log.info("Death recovery complete — resuming combat");
                deathStep = DeathRecoveryStep.TO_BANK;
                deathCount++; // overlay counter
                lastHpZeroAt = 0;
                resetPerKillStateAfterAbort(); // 1.9.90: clear stale per-kill ratchets after death
                currentState = BotState.FIGHTING_CORP;
                return;
        }
    }

    /** Pull the bare-essential kit needed to get back into the fight. */
    private void withdrawForDeathRecovery() {
        try {
            Bank.withdraw("Games necklace(8)", 1);
        } catch (Exception ignored) {}
        if (!Query.inventory().nameContains("Games necklace(").isAny()) {
            try { Bank.withdraw("Games necklace(6)", 1); } catch (Exception ignored) {}
        }
        if (!Query.inventory().nameContains("Games necklace(").isAny()) {
            try { Bank.withdraw("Games necklace(4)", 1); } catch (Exception ignored) {}
        }

        String foodName = settings != null && settings.foodNames != null && settings.foodNames.length > 0
                ? settings.foodNames[0] : "Shark";
        try { Bank.withdraw(foodName, 10); } catch (Exception ignored) {}

        try { Bank.withdraw("Super restore(4)", 1); } catch (Exception ignored) {}
        try { Bank.withdraw("Super combat potion(4)", 1); } catch (Exception ignored) {}
        try { Bank.withdraw("Divine super combat potion(4)", 1); } catch (Exception ignored) {}
    }

    // ========== W330 RANDOM POH (Phase I-C) ==========
    // pohSource=W330_RANDOM: hop to world 330 (the public POH advertising
    // world), walk to the Rimmington portal, enter a random nearby player's
    // house, use their ornate pool, tele back to Corp, and hop back to the
    // designated world. Used by accounts with no POH access and no bot host.

    private enum W330Step {
        CAPTURE_HOME, HOP_TO_W330, TELE_TO_HOUSE_OUTSIDE, ENTER_HOUSE,
        VALIDATE_POOL, USE_POOL, TELE_TO_CORP, HOP_HOME, DONE
    }
    private W330Step w330Step = W330Step.CAPTURE_HOME;
    private int w330CapturedWorld = 0;
    private int w330HostAttempts = 0;
    private String w330CurrentHost = null;

    private static final int W330 = 330;

    private void handleW330Restoration() {
        Log.info("=== W330 RESTORATION (" + w330Step + ") ===");

        switch (w330Step) {
            case CAPTURE_HOME:
                // Remember the world we came from so we can hop back later.
                w330CapturedWorld = settings.designatedWorld > 0
                        ? settings.designatedWorld
                        : WorldHopper.getCurrentWorld();
                Log.info("W330: designated return world = " + w330CapturedWorld);
                w330HostAttempts = 0;
                w330CurrentHost = null;
                w330Step = W330Step.HOP_TO_W330;
                return;

            case HOP_TO_W330:
                if (WorldHopper.getCurrentWorld() == W330) {
                    w330Step = W330Step.TELE_TO_HOUSE_OUTSIDE;
                    return;
                }
                if (WorldHopper.hop(W330)) {
                    Waiting.waitUntil(10000, () -> WorldHopper.getCurrentWorld() == W330);
                }
                return;

            case TELE_TO_HOUSE_OUTSIDE:
                // Use the standard house tab "Outside" option. The bot's own
                // POH is set to Rimmington as part of its gear setup, so this
                // lands us right at the Rimmington portal — no walking needed.
                if (isAtHousePortal()) {
                    w330Step = W330Step.ENTER_HOUSE;
                    return;
                }
                if (!hasHouseTeleportTab()) {
                    Log.error("W330: no house tab in inventory - cannot reach Rimmington portal");
                    emergencyResetPOHSystem();
                    w330Step = W330Step.HOP_HOME;
                    return;
                }
                if (teleportToHouse()) {
                    if (isAtHousePortal()) {
                        w330Step = W330Step.ENTER_HOUSE;
                    }
                }
                return;

            case ENTER_HOUSE:
                if (w330HostAttempts >= Math.max(1, settings.w330MaxHostAttempts)) {
                    Log.warn("W330: " + w330HostAttempts + " failed host attempts - aborting this cycle");
                    emergencyResetPOHSystem();
                    w330Step = W330Step.HOP_HOME; // come home anyway
                    return;
                }
                w330HostAttempts++;

                String host = pickRandomNearbyPlayer();
                if (host == null) {
                    Log.warn("W330: no nearby players to pick as host");
                    return;
                }
                w330CurrentHost = host;
                Log.info("W330: attempting to visit " + host + " (try " + w330HostAttempts + ")");

                // 1.9.7.1: same double-click pattern as the friend-house
                // path — filter must not have side effects.
                Optional<GameObject> portal = Query.gameObjects()
                        .nameEquals("Portal")
                        .filter(p -> p.getActions().contains("Friend's house"))
                        .findFirst();
                if (!portal.isPresent()) {
                    Log.warn("W330: no Portal with Friend's house option visible");
                    return;
                }
                if (portal.get().interact("Friend's house")) {
                    if (Waiting.waitUntil(5000, () -> Chatbox.isOpen())) {
                        try {
                            Keyboard.typeString(host);
                            Waiting.waitUniform(200, 500);
                            Keyboard.pressEnter();
                        } catch (Exception ignored) {}
                        if (Waiting.waitUntil(10000, () -> isInRandomHouse())) {
                            w330Step = W330Step.VALIDATE_POOL;
                        }
                    }
                }
                return;

            case VALIDATE_POOL:
                // 1.9.13: action-based — any object with "Drink" is a pool.
                if (Query.gameObjects().filter(o -> o.getActions().contains("Drink")).isAny()) {
                    Log.info("W330: " + w330CurrentHost + " has a drinkable pool - proceeding");
                    w330Step = W330Step.USE_POOL;
                    return;
                }
                Log.warn("W330: " + w330CurrentHost + " has no drinkable pool - trying another host");
                exitRandomHouse();
                w330Step = W330Step.ENTER_HOUSE;
                return;

            case USE_POOL:
                if (useOrnatePool()) {
                    w330Step = W330Step.TELE_TO_CORP;
                }
                return;

            case TELE_TO_CORP:
                // 1.9.13: action-based — any object with "Corporeal Beast" is a tele box.
                if (Query.gameObjects().filter(o -> o.getActions().contains("Corporeal Beast")).isAny()) {
                    if (useOrnateJewelryBox() && isAtCorp()) {
                        w330Step = W330Step.HOP_HOME;
                        return;
                    }
                }
                // Fallback: use a Games necklace from inventory.
                Optional<InventoryItem> necklace = Query.inventory()
                        .nameContains("Games necklace(").findFirst();
                if (necklace.isPresent() && necklace.get().click("Corporeal Beast")) {
                    if (Waiting.waitUntil(10000, () -> isAtCorp())) {
                        w330Step = W330Step.HOP_HOME;
                    }
                }
                return;

            case HOP_HOME:
                if (w330CapturedWorld <= 0
                        || WorldHopper.getCurrentWorld() == w330CapturedWorld) {
                    w330Step = W330Step.DONE;
                    return;
                }
                if (WorldHopper.hop(w330CapturedWorld)) {
                    Waiting.waitUntil(10000,
                            () -> WorldHopper.getCurrentWorld() == w330CapturedWorld);
                }
                return;

            case DONE:
                Log.info("W330 restoration complete on world " + WorldHopper.getCurrentWorld());
                currentRestorationCycle++;
                w330Step = W330Step.CAPTURE_HOME;
                if (currentRestorationCycle >= settings.totalRestorationCycles) {
                    isInRestorationPhase = false;
                    currentState = BotState.WAITING_FOR_TEAM;
                } else {
                    currentState = BotState.PREPARING_RESTORATION_CYCLE;
                }
                return;
        }
    }

    /** Returns the name of a random non-self player visible nearby. Used by
     *  W330 mode to pick an advertiser at the Rimmington portal. */
    private String pickRandomNearbyPlayer() {
        String myName = MyPlayer.getUsername();
        try {
            return Query.players()
                    .filter(pl -> pl.getName() != null && !pl.getName().equals(myName))
                    .findRandom()
                    .map(pl -> pl.getName())
                    .orElse(null);
        } catch (Exception ignored) {}
        return null;
    }

    /** Heuristic: we're inside a player house if a house-specific object (Pool
     *  or named Portal) is visible. Used to confirm portal-entry success. */
    private boolean isInRandomHouse() {
        try {
            return Query.gameObjects().nameContains("Pool").isAny()
                    || Query.gameObjects().nameEquals("Exit Portal").isAny();
        } catch (Exception e) {
            return false;
        }
    }

    /** Leave the current player house — used when the host's pool is wrong
     *  and we want to try another advertiser. */
    private boolean exitRandomHouse() {
        try {
            Optional<GameObject> exit = Query.gameObjects().nameEquals("Exit Portal").findFirst();
            if (exit.isPresent() && exit.get().interact("Enter")) {
                Waiting.waitUntil(6000, () -> !isInRandomHouse());
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Legacy on-tile sidestep dodge (pre-2026 meta). Kept for A/B comparison
     * and as a fallback when no Elder maul / DWH is available. Toggle via
     * settings.useLegacyDarkCoreLogic.
     */
	private void handleAdvancedDarkCoreLegacy() {
		Log.info("=== DARK CORE DETECTED - SIDESTEP MOVEMENT ===");

		// PRIORITY 1: Health management (always do this)
		int currentHealth = MyPlayer.getCurrentHealth();
		if (currentHealth <= INTERNAL_EMERGENCY_HP) {
			Log.warn("CRITICAL HEALTH during dark core - emergency combo eating!");
			emergencyComboEatDuringMovement();
		} else if (currentHealth <= eatHealthThreshold() + 20) {
			Log.info("Low health during dark core - combo eating");
			emergencyComboEatDuringMovement();
		}

		// PRIORITY 2: Prayer management
		if (Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD + 10) {
			drinkPrayerPotionDuringMovement();
		}

		// PRIORITY 3: Precise sidestep movement
		Optional<Npc> darkCoreOpt = findDarkCore();
		Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();

		if (darkCoreOpt.isPresent() && corpOpt.isPresent()) {
			Npc darkCore = darkCoreOpt.get();
			Npc corp = corpOpt.get();

			WorldTile myPos = MyPlayer.getTile();
			WorldTile corePos = darkCore.getTile();
			WorldTile corpPos = corp.getTile();

			double distanceToCore = myPos.distanceTo(corePos);

			// Only move if we're on the same tile as core (distance 0)
			if (distanceToCore <= 0.5) { // Account for floating point precision
				Log.info("On same tile as core - calculating precise sidestep");

				WorldTile sidestepTile = calculatePreciseSidestep(myPos, corePos, corpPos);

				if (sidestepTile != null) {
					Log.info("Sidestepping to maintain stun range: " + sidestepTile);
					clickScreenTile(sidestepTile);
				} else {
					Log.warn("No valid sidestep position found - staying in place");
				}
			} else if (distanceToCore > 1.5) {
				Log.warn("Too far from core - moving back to stun range");
				// Move back towards core but not onto same tile
				WorldTile closePosition = calculateMoveToStunRange(myPos, corePos, corpPos);
				if (closePosition != null) {
					clickScreenTile(closePosition);
				}
			} else {
				Log.info("Perfect distance from core (" + distanceToCore + ") - staying in position");
			}
		}

		// Check if core disappeared
		if (System.currentTimeMillis() - darkCoreLastSeen > 10000) {
			Log.info("Dark core timeout, returning to combat");
			currentState = BotState.FIGHTING_CORP;
		}
	}

	private WorldTile calculatePreciseSidestep(WorldTile myPos, WorldTile corePos, WorldTile corpPos) {
		Log.info("Calculating sidestep from " + myPos + " (core: " + corePos + ", corp: " + corpPos + ")");

		// Get all tiles exactly 1 distance from core
		List<WorldTile> adjacentTiles = getAdjacentTiles(corePos);

		// Current distance to Corp
		double currentCorpDistance = myPos.distanceTo(corpPos);

		// Score each adjacent tile
		List<ScoredTile> scoredTiles = new ArrayList<>();

		for (WorldTile candidate : adjacentTiles) {
			if (!isTileWalkable(candidate)) {
				Log.debug("Skipping unwalkable tile: " + candidate);
				continue;
			}

			double newCorpDistance = candidate.distanceTo(corpPos);
			double corpDistanceChange = Math.abs(newCorpDistance - currentCorpDistance);

			// Calculate if this is a "sidestep" movement (perpendicular to corp direction)
			double sidestepScore = calculateSidestepScore(myPos, candidate, corpPos);

			// Penalty for changing distance to Corp
			double distancePenalty = corpDistanceChange * 10; // Heavy penalty for distance change

			// Total score (lower is better)
			double totalScore = distancePenalty - sidestepScore;

			scoredTiles.add(new ScoredTile(candidate, totalScore, corpDistanceChange, sidestepScore));

			Log.debug("Candidate " + candidate +
					": corpDistChange=" + String.format("%.2f", corpDistanceChange) +
					", sidestepScore=" + String.format("%.2f", sidestepScore) +
					", totalScore=" + String.format("%.2f", totalScore));
		}

		if (scoredTiles.isEmpty()) {
			Log.warn("No walkable adjacent tiles found!");
			return null;
		}

		// Sort by score (best first)
		scoredTiles.sort((a, b) -> Double.compare(a.score, b.score));

		ScoredTile best = scoredTiles.get(0);
		Log.info("Best sidestep: " + best.tile +
				" (corpDistChange: " + String.format("%.2f", best.corpDistanceChange) +
				", score: " + String.format("%.2f", best.score) + ")");

		return best.tile;
	}

	private List<WorldTile> getAdjacentTiles(WorldTile center) {
		List<WorldTile> adjacent = new ArrayList<>();

		// All 8 surrounding tiles (distance 1)
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue; // Skip center tile

				WorldTile tile = new WorldTile(
						center.getX() + dx,
						center.getY() + dy,
						center.getPlane()
				);
				adjacent.add(tile);
			}
		}

		return adjacent;
	}

	private double calculateSidestepScore(WorldTile fromPos, WorldTile toPos, WorldTile corpCenter) {
		// Calculate movement vector
		double moveX = toPos.getX() - fromPos.getX();
		double moveY = toPos.getY() - fromPos.getY();

		// Calculate vector from player to Corp CENTER (not edge)
		double corpX = corpCenter.getX() - fromPos.getX();
		double corpY = corpCenter.getY() - fromPos.getY();

		// Normalize corp vector
		double corpLength = Math.sqrt(corpX * corpX + corpY * corpY);
		if (corpLength > 0) {
			corpX /= corpLength;
			corpY /= corpLength;
		}

		// Calculate dot product (measures how aligned the vectors are)
		double dotProduct = moveX * corpX + moveY * corpY;

		// Perfect sidestep has dot product of 0 (perpendicular to Corp center)
		return 1.0 - Math.abs(dotProduct);
	}

	private WorldTile calculateMoveToStunRange(WorldTile myPos, WorldTile corePos, WorldTile corpPos) {
		// If we're too far from core, move closer but not onto same tile
		// Find a tile that's exactly distance 1 from core and closer to our current Corp distance

		List<WorldTile> adjacentTiles = getAdjacentTiles(corePos);
		double currentCorpDistance = myPos.distanceTo(corpPos);

		return adjacentTiles.stream()
				.filter(this::isTileWalkable)
				.min((tile1, tile2) -> {
					double dist1 = Math.abs(tile1.distanceTo(corpPos) - currentCorpDistance);
					double dist2 = Math.abs(tile2.distanceTo(corpPos) - currentCorpDistance);
					return Double.compare(dist1, dist2);
				})
				.orElse(null);
	}

	// Helper class for scoring tiles
	private static class ScoredTile {
		final WorldTile tile;
		final double score;
		final double corpDistanceChange;
		final double sidestepScore;

		ScoredTile(WorldTile tile, double score, double corpDistanceChange, double sidestepScore) {
			this.tile = tile;
			this.score = score;
			this.corpDistanceChange = corpDistanceChange;
			this.sidestepScore = sidestepScore;
		}
	}

    private void handleCoreDetected(Npc darkCore, Npc corp) {
        Log.info("Core detected - choosing movement axis");

        // Choose optimal movement axis based on available space
        chosenDodgeAxis = chooseOptimalDodgeAxis(darkCore, corp);
        Log.info("Chosen dodge axis: " + chosenDodgeAxis);

        // Immediately transition to appropriate state
        coreDodgeState = isCoreApproaching(darkCore) ? CoreDodgeState.DODGING : CoreDodgeState.ATTACKING;
    }

    // ========== CORE STATE HANDLERS ==========

    private void handleCoreDodging(Npc darkCore, Npc corp) {
        Log.info("Core dodging - moving away from core");

        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();

        // Calculate dodge position along chosen axis
        WorldTile dodgePosition = calculateDodgePosition(myPos, corePos, corp);

        if (dodgePosition != null) {
            Log.info("Dodging to position: " + dodgePosition);
            if (clickScreenTile(dodgePosition)) {
                // Brief wait for movement to start
				Waiting.waitUntil(2000, () -> {
					if (!MyPlayer.isMoving()) return true;
					WorldTile t = MyPlayer.getTile();
					return t != null && t.distanceTo(dodgePosition) <= 1; // 1.9.99.180: NPE guard
				});
            } else {
                Log.error("Failed to click dodge position, trying emergency movement");
                coreDodgeState = CoreDodgeState.EMERGENCY;
            }
        } else {
            Log.warn("No valid dodge position found, trying emergency movement");
            coreDodgeState = CoreDodgeState.EMERGENCY;
        }
    }

    private void handleCoreAttacking(Npc darkCore, Npc corp) {
        Log.info("Core not approaching - attacking Corp");

        if (corp != null) {
            // Attack Corp - this also moves us toward Corp if needed
            if (!isPlayerInCombat() || !isPlayerAttackingCorp(corp)) {
                if (attackCorpIfVisible(corp)) {
                    Log.info("Re-engaged Corp while core is not threatening");
                }
            }
        }
    }

    private void handleCoreEmergency(Npc darkCore, Npc corp) {
        Log.warn("EMERGENCY: Core too close!");

        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();

        // Emergency movement - any direction that gets us away quickly
        WorldTile emergencyPos = calculateEmergencyPosition(myPos, corePos, corp);

        if (emergencyPos != null) {
            Log.info("Emergency movement to: " + emergencyPos);
            if (clickScreenTile(emergencyPos)) {
				Waiting.waitUntil(2000, () -> !MyPlayer.isMoving());
                // After emergency movement, reassess situation
                coreDodgeState = CoreDodgeState.DETECTED;
            }
        }
    }

    private void updateCoreDistanceTracking(Npc darkCore) {
        long currentTime = System.currentTimeMillis();

        // Update distance tracking every 600ms
        if (currentTime - lastCoreDistanceCheck >= 600) {
            WorldTile myPos = MyPlayer.getTile();
            WorldTile corePos = darkCore.getTile();
            double distance = myPos.distanceTo(corePos);

            // Add to distance history
            coreDistanceHistory.offer(distance);
            if (coreDistanceHistory.size() > CORE_DISTANCE_SAMPLES) {
                coreDistanceHistory.poll();
            }

            lastCorePosition = corePos;
            lastCoreDistanceCheck = currentTime;

            Log.debug("Core distance: " + distance + " (samples: " + coreDistanceHistory.size() + ")");
        }
    }

    // ========== CORE TRACKING AND ANALYSIS ==========

    private CoreDodgeState determineCoreState(Npc darkCore) {
        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();
        double currentDistance = myPos.distanceTo(corePos);

        // Emergency state - core very close
        if (currentDistance <= CORE_EMERGENCY_DISTANCE) {
            return CoreDodgeState.EMERGENCY;
        }

        // If we don't have enough samples yet, stay in current state
        if (coreDistanceHistory.size() < 2) {
            return coreDodgeState == CoreDodgeState.DETECTED ? CoreDodgeState.DETECTED : CoreDodgeState.ATTACKING;
        }

        // Check if core is approaching (within danger range and getting closer)
        if (currentDistance <= CORE_DANGER_DISTANCE && isCoreApproaching(darkCore)) {
            return CoreDodgeState.DODGING;
        }

        // Core is either far away or moving away - safe to attack
        return CoreDodgeState.ATTACKING;
    }

    private boolean isCoreApproaching(Npc darkCore) {
        if (coreDistanceHistory.size() < 2) {
            return false;
        }

        // Get current and previous distances
        Double[] distances = coreDistanceHistory.toArray(new Double[0]);
        double currentDistance = distances[distances.length - 1];
        double previousDistance = distances[distances.length - 2];

        // Core is approaching if distance is decreasing
        boolean approaching = currentDistance < previousDistance;

        if (approaching) {
            Log.debug("Core approaching: " + previousDistance + " -> " + currentDistance);
        }

        return approaching;
    }

    private CoreDodgeAxis chooseOptimalDodgeAxis(Npc darkCore, Npc corp) {
        WorldTile myPos = MyPlayer.getTile();
        WorldTile corePos = darkCore.getTile();
        WorldTile corpPos = corp != null ? corp.getTile() : null;

        // Analyze available space in each direction
        int northSpace = calculateAvailableSpace(myPos, CoreDodgeDirection.NORTH, corpPos);
        int southSpace = calculateAvailableSpace(myPos, CoreDodgeDirection.SOUTH, corpPos);
        int eastSpace = calculateAvailableSpace(myPos, CoreDodgeDirection.EAST, corpPos);
        int westSpace = calculateAvailableSpace(myPos, CoreDodgeDirection.WEST, corpPos);

        Log.info("Available space - North: " + northSpace + ", South: " + southSpace +
                ", East: " + eastSpace + ", West: " + westSpace);

        // Calculate total space for each axis
        int northSouthSpace = northSpace + southSpace;
        int eastWestSpace = eastSpace + westSpace;

        // Choose axis with more available space
        if (northSouthSpace > eastWestSpace) {
            Log.info("Choosing North-South axis (" + northSouthSpace + " vs " + eastWestSpace + ")");
            return CoreDodgeAxis.NORTH_SOUTH;
        } else {
            Log.info("Choosing East-West axis (" + eastWestSpace + " vs " + northSouthSpace + ")");
            return CoreDodgeAxis.EAST_WEST;
        }
    }

// ========== MOVEMENT AXIS AND DIRECTION SELECTION ==========

    private WorldTile getPositionInDirection(WorldTile fromPos, CoreDodgeDirection direction, int distance) {
        switch (direction) {
            case NORTH:
                return new WorldTile(fromPos.getX(), fromPos.getY() + distance, fromPos.getPlane());
            case SOUTH:
                return new WorldTile(fromPos.getX(), fromPos.getY() - distance, fromPos.getPlane());
            case EAST:
                return new WorldTile(fromPos.getX() + distance, fromPos.getY(), fromPos.getPlane());
            case WEST:
                return new WorldTile(fromPos.getX() - distance, fromPos.getY(), fromPos.getPlane());
            default:
                return fromPos;
        }
    }

    private WorldTile calculateDodgePosition(WorldTile myPos, WorldTile corePos, Npc corp) {
        if (chosenDodgeAxis == CoreDodgeAxis.NOT_SET) {
            Log.error("No dodge axis set!");
            return null;
        }

        // Determine which direction to move along chosen axis
        CoreDodgeDirection moveDirection = chooseDodgeDirection(myPos, corePos, corp);

        if (moveDirection == null) {
            Log.warn("No valid dodge direction found");
            return null;
        }

        // Try distances from min to max
        for (int distance = CORE_MIN_DODGE_DISTANCE; distance <= CORE_MAX_DODGE_DISTANCE; distance++) {
            WorldTile candidate = getPositionInDirection(myPos, moveDirection, distance);

            if (isValidDodgePosition(candidate, corp != null ? corp.getTile() : null)) {
                lastDodgeDirection = moveDirection;
                Log.info("Dodge position found: " + moveDirection + " " + distance + " tiles");
                return candidate;
            }
        }

        Log.warn("No valid dodge position found in direction: " + moveDirection);
        return null;
    }

    // ========== DODGE POSITION CALCULATION ==========

    private CoreDodgeDirection chooseDodgeDirection(WorldTile myPos, WorldTile corePos, Npc corp) {
        WorldTile corpPos = corp != null ? corp.getTile() : null;

        List<CoreDodgeDirection> possibleDirections = new ArrayList<>();

        if (chosenDodgeAxis == CoreDodgeAxis.NORTH_SOUTH) {
            possibleDirections.add(CoreDodgeDirection.NORTH);
            possibleDirections.add(CoreDodgeDirection.SOUTH);
        } else {
            possibleDirections.add(CoreDodgeDirection.EAST);
            possibleDirections.add(CoreDodgeDirection.WEST);
        }

        // If we have a last dodge direction, try the opposite first (alternating pattern)
        if (lastDodgeDirection != null) {
            CoreDodgeDirection opposite = getOppositeDirection(lastDodgeDirection);
            if (possibleDirections.contains(opposite)) {
                // Check if opposite direction is viable
                if (hasSpaceInDirection(myPos, opposite, corpPos)) {
                    Log.info("Using opposite direction: " + opposite);
                    return opposite;
                }
            }
        }

        // Otherwise, choose direction that moves away from core
        CoreDodgeDirection awayFromCore = getDirectionAwayFromCore(myPos, corePos, possibleDirections);
        if (awayFromCore != null && hasSpaceInDirection(myPos, awayFromCore, corpPos)) {
            Log.info("Using direction away from core: " + awayFromCore);
            return awayFromCore;
        }

        // Finally, try any available direction
        for (CoreDodgeDirection direction : possibleDirections) {
            if (hasSpaceInDirection(myPos, direction, corpPos)) {
                Log.info("Using any available direction: " + direction);
                return direction;
            }
        }

        return null;
    }

    private CoreDodgeDirection getOppositeDirection(CoreDodgeDirection direction) {
        switch (direction) {
            case NORTH:
                return CoreDodgeDirection.SOUTH;
            case SOUTH:
                return CoreDodgeDirection.NORTH;
            case EAST:
                return CoreDodgeDirection.WEST;
            case WEST:
                return CoreDodgeDirection.EAST;
            default:
                return null;
        }
    }

    private CoreDodgeDirection getDirectionAwayFromCore(WorldTile myPos, WorldTile corePos, List<CoreDodgeDirection> possibleDirections) {
        // Calculate which direction moves us away from core
        int deltaX = myPos.getX() - corePos.getX();
        int deltaY = myPos.getY() - corePos.getY();

        if (possibleDirections.contains(CoreDodgeDirection.NORTH) && deltaY < 0) {
            return CoreDodgeDirection.NORTH;
        }
        if (possibleDirections.contains(CoreDodgeDirection.SOUTH) && deltaY > 0) {
            return CoreDodgeDirection.SOUTH;
        }
        if (possibleDirections.contains(CoreDodgeDirection.EAST) && deltaX < 0) {
            return CoreDodgeDirection.EAST;
        }
        if (possibleDirections.contains(CoreDodgeDirection.WEST) && deltaX > 0) {
            return CoreDodgeDirection.WEST;
        }

        return null;
    }

    private boolean hasSpaceInDirection(WorldTile fromPos, CoreDodgeDirection direction, WorldTile corpPos) {
        // Check if we have at least minimum dodge distance available
        for (int distance = CORE_MIN_DODGE_DISTANCE; distance <= CORE_MAX_DODGE_DISTANCE; distance++) {
            WorldTile testPos = getPositionInDirection(fromPos, direction, distance);

            // Check Corp blocking
            if (corpPos != null && testPos.distanceTo(corpPos) <= 2) {
                return false;
            }

            // Check if position is valid
            if (isValidDodgePosition(testPos, corpPos)) {
                return true;
            }
        }

        return false;
    }

    private boolean isValidPosition(WorldTile pos) {
        // Basic validation - this could be enhanced with actual tile accessibility checks
        return pos.getX() > 0 && pos.getY() > 0; // Placeholder validation
    }

// ========== POSITION VALIDATION ==========

    private boolean isValidDodgePosition(WorldTile pos, WorldTile corpPos) {
        // Must be valid tile
        if (!isValidPosition(pos)) {
            return false;
        }

        // Must not be too close to Corp (avoid Corp's area)
        if (corpPos != null && pos.distanceTo(corpPos) <= 2) {
            return false;
        }

        // Must be within reasonable range to attack Corp later
        if (corpPos != null && pos.distanceTo(corpPos) > MAX_ATTACK_DISTANCE_FROM_CORP) {
            return false;
        }

        // Check for teammates (less critical during emergency)
        long nearbyTeammates = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .filter(player -> player.getTile().distanceTo(pos) <= 1)
                .count();

        return nearbyTeammates == 0;
    }

    private boolean clickScreenTile(WorldTile targetTile) {
        try {
            Log.info("Clicking screen tile: " + targetTile);

            // Use direct tile interaction instead of minimap
            if (targetTile.interact("Walk here")) {
                Log.info("Successfully clicked screen tile");
                return true;
            } else {
                Log.warn("Failed to interact with screen tile, trying alternative method");

                // Alternative: try using LocalWalking as fallback
                if (LocalWalking.walkTo(targetTile)) {
                    Log.info("Fallback walking method succeeded");
                    return true;
                }
            }

            Log.error("All click methods failed for tile: " + targetTile);
            return false;

        } catch (Exception e) {
            Log.error("Exception during screen tile click: " + e.getMessage());
            return false;
        }
    }

// ========== SCREEN CLICKING SYSTEM ==========

    private WorldTile calculateEmergencyPosition(WorldTile myPos, WorldTile corePos, Npc corp) {
        Log.info("Calculating emergency position");

        // Emergency - try any direction that gets us away quickly
        int deltaX = myPos.getX() - corePos.getX();
        int deltaY = myPos.getY() - corePos.getY();

        // Normalize direction
        int dirX = deltaX > 0 ? 1 : (deltaX < 0 ? -1 : 0);
        int dirY = deltaY > 0 ? 1 : (deltaY < 0 ? -1 : 0);

        // If we're on same tile as core, pick random direction
        if (dirX == 0 && dirY == 0) {
            dirX = TribotRandom.uniform(-1, 1);
            dirY = TribotRandom.uniform(-1, 1);
            if (dirX == 0 && dirY == 0) dirX = 1;
        }

        // Try emergency distances
        for (int distance = 3; distance <= 5; distance++) {
            WorldTile candidate = new WorldTile(
                    myPos.getX() + (dirX * distance),
                    myPos.getY() + (dirY * distance),
                    myPos.getPlane()
            );

            if (isValidPosition(candidate)) {
                return candidate;
            }
        }

        return null;
    }

// ========== EMERGENCY HANDLING ==========

    private void resetCoreDodgeTracking() {
        chosenDodgeAxis = CoreDodgeAxis.NOT_SET;
        lastDodgeDirection = null;
        coreDodgeState = CoreDodgeState.DETECTED;
        coreDistanceHistory.clear();
        lastCorePosition = null;
        lastCorpPosition = null;
        lastCoreDistanceCheck = 0;
        Log.info("Dark core tracking reset");
    }

// ========== UTILITY METHODS ==========

    private WorldTile calculateAdvancedSafePosition(WorldTile myPos, WorldTile corpPos, WorldTile corePos) {
        // Get all player positions (teammates)
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // Calculate direction away from core
        int awayFromCoreX = myPos.getX() > corePos.getX() ? 1 : -1;
        int awayFromCoreY = myPos.getY() > corePos.getY() ? 1 : -1;

        // Try positions in expanding radius from current position
        for (int distance = 3; distance <= 6; distance++) {
            // Primary direction: directly away from core
            WorldTile candidate1 = new WorldTile(
                    myPos.getX() + (awayFromCoreX * distance),
                    myPos.getY() + (awayFromCoreY * distance),
                    myPos.getPlane()
            );

            if (isAdvancedSafePosition(candidate1, corpPos, corePos, teammatePositions)) {
                return candidate1;
            }

            // Alternative directions if primary blocked
            for (int[] direction : Arrays.asList(new int[]{awayFromCoreX, 0}, new int[]{0, awayFromCoreY},
                    new int[]{-awayFromCoreX, 0}, new int[]{0, -awayFromCoreY})) {
                WorldTile candidate = new WorldTile(
                        myPos.getX() + (direction[0] * distance),
                        myPos.getY() + (direction[1] * distance),
                        myPos.getPlane()
                );

                if (isAdvancedSafePosition(candidate, corpPos, corePos, teammatePositions)) {
                    return candidate;
                }
            }
        }

        Log.warn("No safe position found, using emergency movement");
        // Emergency: just move away from core regardless of other factors
        return new WorldTile(
                myPos.getX() + (awayFromCoreX * 4),
                myPos.getY() + (awayFromCoreY * 4),
                myPos.getPlane()
        );
    }

    private boolean isAdvancedSafePosition(WorldTile pos, WorldTile corpPos, WorldTile corePos, List<WorldTile> teammatePos) {
        // Must be far enough from dark core (most important)
        if (pos.distanceTo(corePos) < SAFE_DISTANCE_FROM_CORE) {
            return false;
        }

        // Must be within reasonable attack range of Corp
        if (pos.distanceTo(corpPos) > MAX_ATTACK_DISTANCE_FROM_CORP) {
            return false;
        }

        // Should not be too close to Corp (avoid melee range)
        if (pos.distanceTo(corpPos) < 2) {
            return false;
        }

        // Avoid teammate stacking (less critical during core dodging)
        for (WorldTile teammatePosition : teammatePos) {
            if (pos.distanceTo(teammatePosition) < 2) {
                return false;
            }
        }

        return true;
    }

    private boolean isAdvancedSafePositionWithWalkableCheck(WorldTile pos, WorldTile corpPos, WorldTile corePos, List<WorldTile> teammatePos) {
        // First check if tile is walkable
        if (!isTileWalkable(pos)) {
            return false;
        }

        // Then use your existing safety logic
        return isAdvancedSafePosition(pos, corpPos, corePos, teammatePos);
    }

    private void handleCorpPositioning() {
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();

        if (!corpOpt.isPresent()) {
            // Corp not visible - could be dead OR too far back in cave
            Log.info("Corp not visible, moving deeper into cave for better positioning");
            moveToDeepCorpPosition();
        } else {
            // Corp is visible, position around it
            Npc corp = corpOpt.get();
            if (!isInGoodCorpPosition(corp)) {
                Log.info("Corp visible but position not optimal, repositioning");
                moveToNearestCorpPosition(corp);
            }
        }
    }

    /**
     * Calculate safe position considering Corp's area, Dark Core, and teammates
     * Prefer returning to assigned Corp position when safe
     */


    // Add better position assignment logic
    private WorldTile assignUniqueCorpPosition(List<WorldTile> dynamicPositions) {
        WorldTile myPos = MyPlayer.getTile();
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        WorldTile corpCenter = corpOpt.isPresent() ? corpOpt.get().getArea().getCenter() : null;
        Area corpArea = corpOpt.isPresent() ? corpOpt.get().getArea() : null;

        // 1.9.40: HARD pre-filter — reject candidates whose direct line
        // from the player's current tile crosses Corp's hitbox.
        // 1.9.52: when all 4 cardinal candidates require crossing, don't
        // fall back to forcing one — synthesize a single player-side
        // tile at the hitbox edge in the direction of the player. User
        // log showed candidates (East 5-east, North 5-north) both
        // requiring crossing because the player came in from the SW;
        // the bot picked the canonical North and walked through the
        // hitbox, taking stomp damage for 7 seconds before dying.
        List<WorldTile> reachable = dynamicPositions;
        if (myPos != null && corpArea != null) {
            // 1.9.99.27: build tile-debug string for the in-client overlay so
            // user can see WHICH cardinal got which classification each tick.
            // 1.9.99.38: also snapshot the tiles + classification into paintState
            // so drawInClientOverlay can render actual polygons on the world.
            StringBuilder debug = new StringBuilder();
            debug.append("Corp ").append(corpCenter != null ? corpCenter : "?")
                    .append("  Me ").append(myPos).append("\n");
            WorldTile[] cands = new WorldTile[dynamicPositions.size()];
            boolean[] crossArr = new boolean[dynamicPositions.size()];
            for (int i = 0; i < dynamicPositions.size(); i++) {
                WorldTile p = dynamicPositions.get(i);
                boolean crosses = lineCrossesCorp(myPos, p, corpArea);
                debug.append("  ").append(p)
                        .append(crosses ? " CROSS" : " ok")
                        .append("\n");
                cands[i] = p;
                crossArr[i] = crosses;
            }
            paintState.tileDebug = debug.toString();
            paintState.candidateTiles = cands;
            paintState.candidateCrosses = crossArr;
            paintState.corpCenterTile = corpCenter;
            // pickedTile updated below once the choice is made

            // 1.9.99.198: ONLY hard-filter cross-tiles when there are enough
            // direct-reachable tiles AND enough survive the separation filter
            // downstream. Pre-1.9.99.198: cross-tiles were always removed,
            // limiting the candidate pool to one side of Corp. With multiple
            // teammates clustered together, the separation filter then had
            // only 2-3 candidates left, all on the same side — bots stacked.
            // Now: keep cross-tiles in the candidate pool (they get scored
            // with a self-distance penalty for the longer L-walk). The
            // existing L-shape logic in moveToNearestCorpPosition (lines
            // 10198 area) handles routing via pickCornerWaypoint. User:
            // "navigating on the outside quadrants should be considered
            // okay if we are getting to a better tile."
            List<WorldTile> directReach = dynamicPositions.stream()
                    .filter(p -> !lineCrossesCorp(myPos, p, corpArea))
                    .collect(Collectors.toList());
            // Use the full pool (cross-tiles included) unless ALL tiles
            // cross — then fall through to the existing "ALL cross" handler.
            if (!directReach.isEmpty()) {
                reachable = dynamicPositions; // 1.9.99.198: keep cross-tiles
                if (directReach.size() != dynamicPositions.size()) {
                    Log.info("Corp positions: "
                            + directReach.size() + "/" + dynamicPositions.size()
                            + " direct-reach, "
                            + (dynamicPositions.size() - directReach.size())
                            + " via L-walk (kept as candidates)");
                }
            } else {
                // 1.9.67: when ALL canonical cardinals cross, return null
                // and let the caller use corp.interact("Attack"). User log
                // showed the synthesized fallback tile was inside Corp's
                // hitbox at walk-arrival time — Corp moved 2 tiles between
                // snapshot and arrival, the supposedly-safe tile became
                // hitbox-internal, bot took stomp damage. We can't reliably
                // pre-compute a tile that survives Corp's roaming; the
                // game's NPC click-attack pathfinder handles approach
                // safely without ever entering the hitbox.
                Log.info("ALL " + dynamicPositions.size()
                        + " cardinals require crossing Corp — returning null "
                        + "so caller uses corp.interact('Attack')");
                paintState.pickedTile = null; // 1.9.99.38
                return null;
            }
        }

        List<WorldTile> allPlayerPositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // 1.8.8 / 1.9.20 / 1.9.99.119: score factors —
        //   - HARD floor: reject candidates within MIN_PLAYER_SEPARATION
        //     tiles of any player (stops the bot from picking the cardinal
        //     next to a human teammate just because self-distance was
        //     slightly better)
        //   + separation from other players (cap raised 6 → 10, so larger
        //     gaps get rewarded — 6 was too low, every >=6 separation tied)
        //   - distance from self (prefer closer)
        //   + bonus when position is on SAME SIDE of Corp as the player
        //     (we don't have to cross Corp's hitbox to get there)
        // Pre-1.9.20 the self-distance alone could pick a tile that was
        // technically close in Euclidean terms but on the opposite side
        // of Corp — the walker then routed through Corp's hitbox.
        // User: "we need to work on finding a positon around the corp
        // that isnt close to other players."
        final double MIN_PLAYER_SEPARATION = 3.0;
        final double SEPARATION_CAP = 10.0;
        WorldTile bestPosition = null;
        double bestScore = -Double.MAX_VALUE;

        // Pre-pass: filter out candidates that are too close to any player.
        // If filtering leaves nothing, fall back to the unfiltered list so
        // we always pick SOMETHING (better than null).
        List<WorldTile> spaced = reachable.stream()
                .filter(pos -> allPlayerPositions.stream()
                        .mapToDouble(p -> p.distanceTo(pos))
                        .min()
                        .orElse(Double.MAX_VALUE) >= MIN_PLAYER_SEPARATION)
                .collect(Collectors.toList());
        List<WorldTile> rankPool = spaced.isEmpty() ? reachable : spaced;
        // 1.9.99.161: track whether we're in the fallback (dense-team)
        // path. When the separation filter eliminated everything, the
        // standard score weighting (sep capped at 10, dominated by
        // sameSideBonus +5) picks "best of a bad bunch" — but doesn't
        // strongly prefer the LEAST crowded option. In fallback we
        // multiply the separation weight to make "even 1 extra tile of
        // space" dominate every other factor. This is what user wants:
        // when stacked, move to the FARTHEST AVAILABLE tile from
        // teammates, not the highest-scored same-side option.
        boolean inFallbackRanking = spaced.isEmpty() && !allPlayerPositions.isEmpty();
        if (!spaced.isEmpty() && spaced.size() != reachable.size()) {
            Log.info("Position-separation filter: "
                    + spaced.size() + "/" + reachable.size()
                    + " candidates >= " + MIN_PLAYER_SEPARATION + " tiles from all players");
        } else if (inFallbackRanking) {
            Log.info("All candidates within " + MIN_PLAYER_SEPARATION
                    + " of a player — fallback ranking (separation weight ×10)");
        }

        // 1.9.99.143: also score by distance from dark core (if present).
        // Picked positions far from the core minimize core-bump risk.
        // Cap at 8 tiles — beyond that, additional distance doesn't matter.
        WorldTile darkCorePos = null;
        try {
            Optional<Npc> coreOpt = findDarkCore();
            if (coreOpt.isPresent()) darkCorePos = coreOpt.get().getTile();
        } catch (Throwable ignored) {}
        final double CORE_DIST_CAP = 8.0;

        // 1.9.99.184: read teammates' claimed Corp-offsets from the
        // coordinator so we can heavily penalize tiles another bot has
        // already claimed. Pre-1.9.99.184 the scorer was deterministic
        // and two bots scoring the same room would pick the same tile
        // (RSN jitter alone wasn't enough). Result: bots ran to the
        // same tile, encroachment fired, they re-picked, often same
        // again — looked botty. With claim-penalty active, Bot B sees
        // Bot A's claim and skips that tile on its first pick.
        Set<String> teammateClaims = readOthersClaimedOffsets();

        for (WorldTile position : rankPool) {
            double minDistanceToPlayer = allPlayerPositions.stream()
                    .mapToDouble(playerPos -> playerPos.distanceTo(position))
                    .min()
                    .orElse(Double.MAX_VALUE);
            double selfDistance = myPos == null ? 0 : myPos.distanceTo(position);
            double separationScore = Math.min(minDistanceToPlayer, SEPARATION_CAP);
            double coreDistanceScore = darkCorePos == null ? 0
                    : Math.min(darkCorePos.distanceTo(position), CORE_DIST_CAP);

            // Same-side bonus: if player and target are both on the SAME
            // side of Corp's center on the dominant axis, no need to
            // cross. Add +5 to the score (large enough to outweigh small
            // self-distance differences but not separation).
            double sameSideBonus = 0;
            if (myPos != null && corpCenter != null) {
                int corpX = corpCenter.getX(), corpY = corpCenter.getY();
                int dx = myPos.getX() - corpX, dy = myPos.getY() - corpY;
                int pdx = position.getX() - corpX, pdy = position.getY() - corpY;
                // Dominant approach axis (the one with the bigger diff).
                if (Math.abs(dx) >= Math.abs(dy)) {
                    // X-axis approach: bonus if position's x sign matches player's
                    if (dx != 0 && pdx != 0 && Math.signum(dx) == Math.signum(pdx)) {
                        sameSideBonus = 5;
                    }
                } else {
                    if (dy != 0 && pdy != 0 && Math.signum(dy) == Math.signum(pdy)) {
                        sameSideBonus = 5;
                    }
                }
            }

            // 1.9.99.143: score combines separation (away from players),
            // self-distance (prefer closer), same-side bonus (no Corp cross),
            // and core-distance (away from dark core if present).
            // 1.9.99.161: separation gets ×10 weight in the fallback path
            // (when no candidate is >= MIN_PLAYER_SEPARATION). This makes
            // "less crowded" the dominant factor when the team is densely
            // stacked — the bot moves to the LEAST crowded available tile
            // rather than the highest same-side-bonus tile.
            double sepWeight = inFallbackRanking ? 10.0 : 1.0;
            // 1.9.99.163: corner tiles get a flat bonus. Corp's perimeter
            // has 4 corner tiles (|dx|=3 AND |dy|=3 from center) that
            // have access to TWO cardinal sides without crossing the
            // hitbox — much better repositioning flexibility than mid-
            // edge tiles. User: "we should give higher priority to corner
            // tiles because that gives us better repositioning access to
            // two entire directions". Bonus of +3 — large enough to break
            // ties between corner and adjacent mid-edge tiles, small
            // enough that a corner tile won't beat a clearly better
            // separation pick.
            double cornerBonus = 0;
            if (corpCenter != null) {
                int dxC = Math.abs(position.getX() - corpCenter.getX());
                int dyC = Math.abs(position.getY() - corpCenter.getY());
                if (dxC == 3 && dyC == 3) cornerBonus = 3.0;
            }
            // 1.9.99.184: heavy penalty for tiles already claimed by a
            // teammate via coordinator. Offset = (position - corpCenter).
            // Magnitude (-20) ensures a claimed tile loses to any
            // reasonable unclaimed alternative — separationScore caps at
            // 5, cornerBonus 3, sameSideBonus 5, coreDistanceScore 8,
            // worst-case sum ~21 → claimed tile's effective score < 1.
            double teammateClaimPenalty = 0;
            if (corpCenter != null && !teammateClaims.isEmpty()) {
                int dxClaim = position.getX() - corpCenter.getX();
                int dyClaim = position.getY() - corpCenter.getY();
                if (teammateClaims.contains(dxClaim + "," + dyClaim)) {
                    teammateClaimPenalty = -20;
                }
            }
            // 1.9.99.198: penalty for tiles requiring an L-walk around Corp
            // (straight line crosses hitbox). Small enough that going to a
            // FAR but UNCROWDED tile still beats stacking on a near but
            // crowded one — separationScore up to 5 × sepWeight (1 or 10)
            // dominates. Without this penalty, the bot could pick a
            // cross-tile when a direct-reach tile is equally good. With it,
            // direct-reach wins ties.
            double crossPenalty = (myPos != null && corpArea != null
                    && lineCrossesCorp(myPos, position, corpArea)) ? -2 : 0;
            // 1.9.99.225: deterministic-by-index quadrant pref (replaces
            // 1.9.99.185's hash-based approach). Pre-1.9.99.225 we mapped
            // RSN.hashCode() to an angle. For "<bot-a>" vs "<bot-b>"
            // the hash delta was 16 * 31^2 = 15,376, mapping to ~84° angle
            // separation — enough in theory but in practice both bots
            // ended up ping-ponging between (2998,4387) [NE] and
            // (2992,4387) [NW] every 3s because both had a north-Y
            // preference and the +6 quadrant weight couldn't overcome the
            // shared separation/corner scoring on the two top corners.
            // Now: sort the union of {selfRSN, botTeammates} alphabetically,
            // find self's index, distribute angles evenly around the circle.
            // For 2 bots: indices 0/1 → 0/π (perfectly opposite, EAST vs
            // WEST). For 3 bots: 120° apart. Both bots compute the same
            // sorted list so they agree on assignments without any
            // coordinator round-trip; zero hash collision risk; the two
            // bots provably cannot prefer the same side.
            // Weight bumped 6 → 10 so the assigned side beats marginal
            // separation differences between equivalently-safe candidates.
            double quadrantBonus = 0;
            if (corpCenter != null) {
                String selfName2 = MyPlayer.getUsername();
                if (selfName2 != null) {
                    java.util.SortedSet<String> rsnSet =
                            new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                    rsnSet.add(selfName2);
                    if (settings.botTeammates != null) {
                        for (String rsn : settings.botTeammates) {
                            if (rsn != null && !rsn.trim().isEmpty()) {
                                rsnSet.add(rsn.trim());
                            }
                        }
                    }
                    int total = rsnSet.size();
                    int myIdx = 0;
                    int i = 0;
                    for (String rsn : rsnSet) {
                        if (rsn.equalsIgnoreCase(selfName2)) { myIdx = i; break; }
                        i++;
                    }
                    double myPreferredAngle = (total > 0)
                            ? (myIdx * 2.0 * Math.PI / total)
                            : 0.0;
                    int dxQ = position.getX() - corpCenter.getX();
                    int dyQ = position.getY() - corpCenter.getY();
                    double tileAngle = Math.atan2(dyQ, dxQ); // -π..π
                    if (tileAngle < 0) tileAngle += 2.0 * Math.PI;
                    double angDist = Math.abs(tileAngle - myPreferredAngle);
                    if (angDist > Math.PI) angDist = 2.0 * Math.PI - angDist; // 0..π
                    quadrantBonus = (1.0 - angDist / Math.PI) * 10.0;
                }
            }
            double score = (separationScore * sepWeight)
                    - selfDistance + sameSideBonus + coreDistanceScore
                    + cornerBonus + teammateClaimPenalty + quadrantBonus + crossPenalty;

            // 1.9.99.160: per-bot RSN-seeded jitter. Pre-1.9.99.160 the
            // scoring was fully deterministic — two bots in identical
            // positions with the same Corp + player set computed the
            // EXACT same score for each tile and picked the same one.
            // Result: stacked bots stayed stacked even after a relocate
            // because they walked to the same target. Jitter breaks ties
            // without affecting the dominant scoring factors. Seeded by
            // RSN hash + tile coords so each bot has its own consistent
            // preference, deterministic per (bot, tile) so picks don't
            // wander pointlessly each tick.
            try {
                String selfName = MyPlayer.getUsername();
                if (selfName != null) {
                    long seed = ((long) selfName.hashCode() << 32)
                            ^ ((long) position.getX() * 1313L)
                            ^ ((long) position.getY());
                    java.util.Random r = new java.util.Random(seed);
                    score += r.nextDouble() * 1.5; // 0 to +1.5 jitter
                }
            } catch (Exception ignored) {}

            if (score > bestScore) {
                bestScore = score;
                bestPosition = position;
            }
        }

        if (bestPosition == null) {
            bestPosition = rankPool.get(0); // safety net, prefer pre-filtered list
        }
        Log.info("Picked Corp position " + bestPosition + " (score=" + bestScore
                + ") from " + rankPool.size() + " candidates"
                + (rankPool.size() != dynamicPositions.size()
                        ? " (filtered from " + dynamicPositions.size() + ")"
                        : ""));
        paintState.pickedTile = bestPosition; // 1.9.99.38
        return bestPosition;
    }

    private WorldTile calculateSafePositionWithCorpArea(WorldTile myPos, Area corpArea, WorldTile corePos) {
        // Get teammate positions (only acceptable teammates)
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // First, try to return to our assigned Corp position if it's safe
        for (WorldTile corpPosition : CORP_POSITIONS) {
            if (isSafePositionWithCorpArea(corpPosition, corpArea, corePos, teammatePositions)) {
                // Check if this position is close to where we were (prefer current assignment)
                if (myPos.distanceTo(corpPosition) <= 5) {
                    return corpPosition;
                }
            }
        }

        // If assigned position isn't safe, try other Corp positions
        for (WorldTile corpPosition : CORP_POSITIONS) {
            if (isSafePositionWithCorpArea(corpPosition, corpArea, corePos, teammatePositions)) {
                return corpPosition;
            }
        }

        // If no Corp positions are safe, try dynamic positions around current location
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                if (dx == 0 && dy == 0) continue; // Skip current position

                WorldTile candidate = new WorldTile(
                        myPos.getX() + dx,
                        myPos.getY() + dy,
                        myPos.getPlane()
                );

                if (isSafePositionWithCorpArea(candidate, corpArea, corePos, teammatePositions)) {
                    return candidate;
                }
            }
        }

        return null; // No safe position found
    }

    /**
     * Check if a position is safe from Corp's area, Dark Core, and teammates
     */
    private boolean isSafePositionWithCorpArea(WorldTile pos, Area corpArea, WorldTile corePos, List<WorldTile> teammatePos) {
        // Must be far enough from dark core
        if (pos.distanceTo(corePos) < SAFE_DISTANCE_FROM_CORE) {
            return false;
        }

        // Must not be within Corp's area (avoid crush damage)
        if (corpArea.contains(pos)) {
            return false;
        }

        // Must be close enough to Corp's area to attack but not too close
        //int distanceToCorpArea = corpArea.distanceTo(pos);
        //if (distanceToCorpArea < SAFE_DISTANCE_FROM_CORP_AREA || distanceToCorpArea > MAX_ATTACK_DISTANCE_FROM_CORP) {
        //	return false;
        //}

        // Must not be too close to teammates
        for (WorldTile teammatePosition : teammatePos) {
            if (pos.distanceTo(teammatePosition) < SAFE_DISTANCE_FROM_TEAMMATES) {
                return false;
            }
        }

        // Additional check: make sure we have line of sight to Corp
        // and the tile is walkable (this might need additional SDK methods)

        return true;
    }

    /**
     * Check if we have minimum supplies to continue (lower threshold than optimal)
     */
    private boolean hasMinimumSupplies() {
        return Inventory.getCount(settings.foodNames) >= 8 && // Minimum 8 food instead of 12
                getPrayerDoses() >= 2 && // Minimum 2 doses instead of 4
                (hasChargedGamesNecklace() || hasChargedRingOfDueling());
    }

    /**
     * Check if we're in the Corp lobby/waiting area
     */
    private boolean isInCorpLobby() {

        return corpLobby.containsMyPlayer();

    }

    /**
     * Check if we're in the Corp boss room (where Corp spawns)
     */
    private boolean isInCorpBossRoom() {


        return corpCave.containsMyPlayer();
    }

    /**
     * Move from lobby to boss room
     */
    private boolean moveToCorpBossRoom() {
        Optional<GameObject> passage = Query.gameObjects()
                .nameContains("Passage")
                .findFirst();

        if (passage.isPresent()) {
            GameObject passageExists = passage.get();
            Log.info("Passage found, attempting to use it");

            if (passageExists.isVisible()) {
                // Passage is visible on screen
                Log.info("Passage found on screen, clicking it after short delay");
				Waiting.waitUntil(1000, () -> passageExists.isVisible());

                if (passageExists.interact("Go-through")) {
                    // Check if we moved to boss room with timeout (1200-2400ms)
                    return Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                            isInCorpBossRoom());
                } else {
                    Log.warn("Failed to interact with passage");
                    return false;
                }
            } else {
                // Minimap walk to passage with same timeout
                Log.info("Passage not visible, walking to it via minimap");
                WorldTile passageTile = passageExists.getTile();

                if (LocalWalking.walkTo(passageTile)) {
                    // Wait for arrival at passage location
                    if (Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                            passageExists.isVisible())) {

                        // Short delay before clicking
                        Waiting.waitUntil(1000, () -> passageExists.isVisible());

                        if (passageExists.interact("Go-through")) {
                            // Final check if we made it to boss room
                            return Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                                    isInCorpBossRoom());
                        }
                    }
                }

                Log.warn("Failed to walk to passage or interact with it");
                return false;
            }
        } else {
            Log.info("Passage cannot be found");
            return false;
        }
    }

    /**
     * Move from boss room to lobby
     */
    private boolean moveToCorpLobby() {
        Optional<GameObject> passage = Query.gameObjects()
                .nameContains("Passage")
                .findFirst();

        if (passage.isPresent()) {
            GameObject passageExists = passage.get();
            Log.info("Passage found, attempting to use it");

            if (passageExists.isVisible()) {
                // Passage is visible on screen
                Log.info("Passage found on screen, clicking it after short delay");
                Waiting.waitUntil(1000, () -> passageExists.isVisible());
                if (passageExists.interact("Go-through")) {
                    // Check if we moved to boss room with timeout (1200-2400ms)
                    return Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                            !isInCorpBossRoom());
                } else {
                    Log.warn("Failed to interact with passage");
                    return false;
                }
            } else {
                // Minimap walk to passage with same timeout
                Log.info("Passage not visible, walking to it via minimap");
                WorldTile passageTile = passageExists.getTile();

                if (LocalWalking.walkTo(passageTile)) {
                    // Wait for arrival at passage location
                    if (Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                            passageExists.isVisible())) {

                        // Short delay before clicking
                        Waiting.waitUntil(1000, () -> passageExists.isVisible());
                        if (passageExists.interact("Go-through")) {
                            // Final check if we made it to boss room
                            return Waiting.waitUntil(TribotRandom.uniform(1200, 2400), () ->
                                    !isInCorpBossRoom());
                        }
                    }
                }

                Log.warn("Failed to walk to passage or interact with it");
                return false;
            }
        } else {
            Log.info("Passage cannot be found");
            return false;
        }
    }

    /**
     * Check if acceptable teammates are in the boss room
     */
    private boolean hasAcceptableTeammatesInBossRoom() {
        // 1.9.13: pre-1.9.13 this required the BOT to already be in the
        // boss room before checking for teammates there — backwards. The
        // bot would tele back from POH into Corp's lobby, find no
        // teammates "in the boss room" (because itself wasn't there yet),
        // and wait in lobby forever while teammates were already fighting
        // Corp in the boss room.
        // Now: check whether any acceptable teammate's TILE falls inside
        // corpCave, regardless of where the bot itself is.
        // 1.9.99.154: exclude self. User config sometimes contains the
        // bot's own RSN in acceptableTeammates (e.g. shared profile across
        // bots). Without this guard, the bot in the boss room sees itself
        // and the helper returns true even when actually alone — every
        // downstream gate that uses this fires incorrectly.
        if (settings == null || settings.acceptableTeammates == null) return false;
        final String self = MyPlayer.getUsername();
        return Query.players()
                .stream()
                .filter(p -> p.getName() != null
                        && !p.getName().equals(self)
                        && settings.acceptableTeammates.contains(p.getName()))
                .anyMatch(p -> {
                    WorldTile t = p.getTile();
                    return t != null && corpCave.contains(t);
                });
    }

    /**
     * Check if acceptable teammates are in the lobby
     */
    private boolean hasAcceptableTeammatesInLobby() {
        if (!isInCorpLobby()) {
            return false;
        }
        // 1.9.32: NPE guard. settings.acceptableTeammates could be null
        // on a freshly-loaded profile or after a settings migration.
        if (settings == null || settings.acceptableTeammates == null
                || settings.acceptableTeammates.isEmpty()) {
            return false;
        }
        // 1.9.99.154: exclude self. If acceptableTeammates contains the
        // bot's own RSN, Query.players() returns self and the helper
        // incorrectly returns true — bot then commits to ENTERING_COMBAT
        // with "ready to enter together" even when alone in the lobby.
        final String self = MyPlayer.getUsername();
        return Query.players()
                .stream()
                .anyMatch(player -> player.getName() != null
                        && !player.getName().equals(self)
                        && settings.acceptableTeammates.contains(player.getName()));
    }

    /**
     * Check if we can cast vengeance spell
     */
    private boolean canCastVengeance() {
        // 1.9.99.78: restored the strict magic-level gate. 1.9.99.77
        // had removed it (option B) so we'd try the cast even when
        // drained; the game would refuse the spell with no rune
        // consumption. User reverted: "keep the magic level try.
        // trying to click a spell that we clearly cant cast is
        // obvious of a bot". Drain recovers ~1 level/minute; while
        // drained we hold off rather than click-and-fail repeatedly.
        // The 1.9.99.77 attempt counter + failed-retry throttle stay
        // in place — they catch non-drain failure modes (widget search
        // miss, click race) which are real bugs we want to see.
        int magCurrent = Skill.MAGIC.getCurrentLevel();
        if (magCurrent < 94) {
            vengLastGateReason = "magic drained " + magCurrent + "/94";
            return false;
        }

        // 1.9.97: replaced 1.9.96's "cooldown alone" with HP-delta "veng
        // consumed" check. tookDamageSinceLastVeng is flipped true in
        // updateHealthTracking whenever currentHealth < previousHealth, and
        // flipped false in castVengeance success. So we only re-cast after
        // our HP has actually dropped since the previous cast — deterministic,
        // unlike the 1.9.18-era HP-bar-visibility probe that false-positived
        // on full-HP heals.
        if (!tookDamageSinceLastVeng) {
            return false;
        }

        return !isVengeanceOnCooldown();
    }

    /** 1.9.99.48: diagnostic — explains WHY canCastVengeance returned false.
     *  Called from handleActiveCasting when we hit the cooldown window but
     *  canCastVengeance() blocks. Logs Magic level (current + base),
     *  tookDamageSinceLastVeng flag, time since lastVengeanceCast. */
    private void logVengeanceBlocked() {
        int magCurrent = Skill.MAGIC.getCurrentLevel();
        int magBase = Skill.MAGIC.getActualLevel();
        long sinceLastCast = lastVengeanceCast == 0
                ? -1
                : System.currentTimeMillis() - lastVengeanceCast;
        long cooldownLeft = sinceLastCast < 0 ? 0 : Math.max(0, 30000 - sinceLastCast);
        // 1.9.99.72: flag CURRENT (drained) as too low. Pre-1.9.99.72 the
        // flag was magBase < 94, which never tripped because drain only
        // affects the live level.
        // 1.9.99.75: set vengLastGateReason for the paint overlay.
        if (magCurrent < 94) {
            vengLastGateReason = "magic drained " + magCurrent + "/94";
        } else if (!tookDamageSinceLastVeng) {
            vengLastGateReason = "no damage since last cast";
        } else if (cooldownLeft > 0) {
            vengLastGateReason = "cooldown " + cooldownLeft + "ms";
        } else {
            vengLastGateReason = "blocked (unknown)";
        }
        Log.info("Vengeance blocked: magicCurrent=" + magCurrent
                + "/" + magBase + " (base) "
                + (magCurrent < 94 ? "[LEVEL DRAINED BELOW 94]" : "")
                + ", tookDamageSinceLastVeng=" + tookDamageSinceLastVeng
                + (tookDamageSinceLastVeng ? "" : " [waiting for next HP drop]")
                + ", lastCast=" + (sinceLastCast < 0 ? "never" : sinceLastCast + "ms ago")
                + (cooldownLeft > 0 ? " [cooldown " + cooldownLeft + "ms remaining]" : ""));
    }

    private boolean isCorpLowHealth(Npc corp) {
        // Corp has 2000 total HP. Translate the configured absolute threshold
        // (INTERNAL_CORP_LOW_HP_VENG_STOP_RAW_HP) into a proportion comparison against
        // the visible bar.
        if (corp.isHealthBarVisible()) {
            double healthPercent = corp.getHealthBarPercent();
            // 1.9.99.117: scale fix — SDK returns 0-1 proportion, was
            // computing thresholdPercent on 0-100 scale (multiplied by 100).
            // healthPercent (e.g. 0.5 at half HP) was always less than the
            // 0-100 threshold (e.g. 20.0), so isCorpLowHealth returned true
            // always — making vengeance bail "Corp low HP" the whole fight.
            double thresholdProportion = INTERNAL_CORP_LOW_HP_VENG_STOP_RAW_HP / 2000.0;
            return healthPercent < thresholdProportion;
        }

        // If no health bar visible, assume full health
        return false;
    }

    /**
     * Cast the vengeance spell
     */
    private boolean castVengeance() {
        // 1.9.76.1: widget-click via sprite ID 564 (Vengeance Self). Was
        // briefly using Magic.cast("Vengeance") but the SDK signature
        // is cast(int, Magic.SpellBook) not cast(String) — compile fail.
        // Sprite ID match (1.9.75) is the canonical identifier anyway.
        long before = System.currentTimeMillis();
        // 1.9.99.77: stamp the attempt BEFORE the click. The failed-retry
        // throttle in handleActiveCasting gates on this so we don't
        // hammer the widget every tick after a failed cast (e.g. magic
        // drained, or widget not found). vengAttemptCount surfaces on
        // the overlay so a "attempts climbing, casts stuck at 0" pattern
        // tells us the click is firing but the spell is being refused.
        vengAttemptCount++;
        lastVengAttemptAt = before;
        try {
            // 1.9.90: verify cast actually fired via Magic XP delta before stamping
            // lastVengeanceCast. Without the gate a failed widget click silently
            // poisons the 30s cooldown and the bot never retries until next trip.
            long xpBefore = Skill.MAGIC.getXp();
            castVengeanceWidget(142);
            boolean xpGained = Waiting.waitUntil(1500, () -> Skill.MAGIC.getXp() > xpBefore);
            if (!xpGained) {
                int magCur = Skill.MAGIC.getCurrentLevel();
                vengLastGateReason = magCur < 94
                        ? "click fired but magic " + magCur + "/94 — spell refused"
                        : "click fired but no XP — widget miss?";
                Log.warn("Vengeance widget clicked but no Magic XP delta — not stamping cooldown"
                        + " (magic " + magCur + "/" + Skill.MAGIC.getActualLevel() + ", attempt #"
                        + vengAttemptCount + ")");
                return false;
            }
            lastVengeanceCast = before;
            hasUsedVengeanceThisTrip = true;
            vengeanceQueued = false;
            // 1.9.97: fresh cast — clear the "took damage" flag so we only
            // re-cast after our HP visibly drops below this moment's HP.
            tookDamageSinceLastVeng = false;
            // 1.9.99.75: paint counters.
            vengCastsThisKill++;
            vengCastsThisSession++;
            vengLastGateReason = "cast ok";
            Log.info("Vengeance cast via widget (sprite 564) — XP delta confirmed");
            return true;
        } catch (Exception e) {
            Log.error("Vengeance casting failed: " + e.getMessage());
            return false;
        }
    }

    /** Cached result of the Lunar-spellbook probe. Null until the first
     *  vengeance attempt of the session checks; then true/false. */
    private Boolean onLunarSpellbookCache = null;
    private boolean spellbookWarningLogged = false;
    private boolean runePouchWarningLogged = false;

    /** True if we appear to have the runes available for Vengeance — either a
     *  Rune pouch (or Divine variant, contents not introspectable without
     *  opening it) or all loose runes in inventory. */
    private boolean hasVengeanceRunes() {
        // 1.9.66: ALWAYS return true. User: 'can we just return it as
        // true even though we are failing to grab it for some reason?
        // i have thousands of casts ready.' The runtime detection of
        // the rune pouch was unreliable across SDK quirks; user has
        // thousands of casts stockpiled so a worst-case failed cast
        // (no runes / cooldown) costs one wasted click, much cheaper
        // than the current 'never cast' failure mode.
        return true;
    }

    /** Probe the magic-tab widget tree for the Vengeance spell. Returns true
     *  if Vengeance is castable from the current spellbook (i.e., we're on
     *  Lunars). Result is cached so we don't repeatedly probe. */
    private boolean isOnLunarSpellbook() {
        if (onLunarSpellbookCache != null) return onLunarSpellbookCache;
        try {
            GameTab.MAGIC.open();
            Waiting.waitUntil(1500, () -> GameTab.MAGIC.isOpen());
            // Use the same self-vs-other guard as the cast so a stray
            // "Vengeance Other" widget can't satisfy the probe.
            boolean found = Query.widgets()
                    .inRoots(218)
                    .filter(w -> w.getIndexPath().length >= 2 && w.getIndexPath()[1] == 142)
                    .filter(this::isVengeanceSelfWidget)
                    .findFirst()
                    .isPresent();
            onLunarSpellbookCache = found;
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== MAIN VENGEANCE HANDLER ==========
    private void handleVengeanceLogic() {
        // 1.9.99.48: updateHealthTracking now runs in the main loop before
        // state dispatch (top of run()), so HP drops during spec dumps,
        // walks, and POH cycles are tracked too. The call here is removed
        // to avoid double-polling the same tick.

        // 1.9.99.67: throttled diagnostic so user can see WHY vengeance
        // isn't firing in kill phase. Pre-1.9.99.67 the only diagnostic
        // (logVengeanceBlocked) fired ONLY when the cooldown was ready
        // but other gates failed — but if the kill phase is shorter
        // than the 31s cooldown, no log fires at all. Now we log the
        // early-return gate too. User: "i also noticed that it is still
        // not vengeancing in the last phases of the fight even when we
        // are in a position where its safe to do so".
        long nowMs = System.currentTimeMillis();
        boolean shouldDiag = nowMs - lastVengGateDiagAt > 2000;

        // User toggle: skip vengeance entirely on accounts that don't have Lunars / runes.
        if (!settings.useVengeance) {
            vengLastGateReason = "useVengeance=off";
            if (shouldDiag) {
                Log.debug("VENG-GATE: settings.useVengeance=false");
                lastVengGateDiagAt = nowMs;
            }
            return;
        }

        // 1.9.99.74: HP gate. If we're at/below the emergency combo-eat
        // threshold, defer veng — handleHealthAndPrayer is about to fire
        // a combo eat and we don't want the veng widget click stealing
        // the same tick. Lifting handleVengeanceLogic to the main loop
        // (post-eat ordering) handles the same-tick race, but this also
        // guards against ticks where the eat already fired but HP is
        // still in the danger zone (Corp got another hit in between).
        // User: "this may have us try to vengenance when we are low hp
        // and need to eat".
        int hpNow = MyPlayer.getCurrentHealth();
        if (hpNow > 0 && hpNow <= INTERNAL_COMBO_EAT_HP) {
            vengLastGateReason = "HP " + hpNow + " <= " + INTERNAL_COMBO_EAT_HP;
            if (shouldDiag) {
                Log.info("VENG-GATE: HP " + hpNow + " <= " + INTERNAL_COMBO_EAT_HP
                        + " (emergency eat threshold) — deferring cast this tick");
                lastVengGateDiagAt = nowMs;
            }
            return;
        }

        // 1.9.99.74: state gate. Block veng during POH / lobby / banking /
        // teleport / death-recovery — casts there are wasted or break the
        // restoration flow. Allow during all combat-adjacent states.
        // User: "We don't veng during the POH trip because you can't veng
        // in the house; venging in the lobby we disabled for now."
        switch (currentState) {
            case TELEPORTING_TO_HOUSE:
            case ENTERING_FRIEND_HOUSE:
            case USING_ORNATE_POOL:
            case TELEPORTING_BACK_TO_CORP:
            case BANKING_AND_HEALING:
            case W330_RESTORATION:
            case DEATH_RECOVERY:
            case EMERGENCY_ESCAPE:
            case STARTING:
            case PREPARING_RESTORATION_CYCLE:
            case LOOTING:
                vengLastGateReason = "state=" + currentState;
                if (shouldDiag) {
                    Log.info("VENG-GATE: state=" + currentState
                            + " — skipping veng (POH/lobby/banking/loot)");
                    lastVengGateDiagAt = nowMs;
                }
                return;
            default:
                break;
        }

        // 1.9.98: kill-phase gate re-introduced (originally 1.9.18, removed
        // briefly in 1.9.95). Veng should only fire during the kill phase
        // (post-spec-dump) — not during spec dumping (would compete with
        // weapon swaps + spec cadence) and not in the lobby. Gate passes
        // when teamPhaseNeeded() == 0 (all reducer specs landed) OR Corp HP
        // is below corpMinHpForSpec (1700, a teammate is finishing). The
        // 1.9.95 removal was the wrong remedy — the real bug was the HP-
        // bar-visibility probe in canCastVengeance blocking casts during
        // the kill phase (now fixed via 1.9.97 HP-delta tracking).
        // 1.9.99.69: reverted the 1.9.99.68 lobby allowance. User: "we
        // dont want to vengence for poh restorations unless weve
        // finished our spec dumpin phases". The kill-phase gate
        // already handles this: isInKillPhase returns true when
        // teamPhaseNeeded == 0 (all owned phases done) — which is
        // exactly "spec dumps finished." In the lobby with phases
        // done, gate passes. In the lobby with phases incomplete
        // (a POH restoration cycle mid-spec-dump), gate blocks.
        if (!isInKillPhase()) {
            vengLastGateReason = "not kill phase (needed="
                    + teamPhaseNeeded() + ")";
            if (shouldDiag) {
                Log.info("VENG-GATE: not in kill phase (teamPhaseNeeded="
                        + teamPhaseNeeded() + ") — vengeance won't fire yet");
                lastVengGateDiagAt = nowMs;
            }
            return;
        }
        // We're in kill phase. From here on, diagnostic logs at INFO so
        // the user can trace why vengeance isn't firing.
        if (shouldDiag) {
            // 1.9.99.72: log BOTH magicCurrent (live drained value used
            // by the gate) and magicBase (XP-derived). If they diverge,
            // we're being drained and casts will block. Also guard
            // lastCastAgoMs from printing the raw Unix timestamp when
            // lastVengeanceCast is still 0 (no successful cast yet).
            String lastCastAgo = lastVengeanceCast == 0
                    ? "never"
                    : (nowMs - lastVengeanceCast) + "ms";
            Log.info("VENG-GATE: in kill phase, vengeanceState=" + vengeanceState
                    + ", tookDamageSinceLastVeng=" + tookDamageSinceLastVeng
                    + ", magicCurrent=" + Skill.MAGIC.getCurrentLevel()
                    + ", magicBase=" + Skill.MAGIC.getActualLevel()
                    + ", lastCastAgo=" + lastCastAgo);
            lastVengGateDiagAt = nowMs;
        }

        // 1.9.4: removed the isOnLunarSpellbook() upfront probe. The probe was
        // returning false positives on the user's client even after the 1.9.3
        // text-filter relaxation — Query.widgets(218).filter(path==142) didn't
        // resolve the widget reliably across magic-tab refreshes. The cast
        // itself logs "Vengeance widget not found at [218, 142]" if the spell
        // actually isn't there, which is sufficient diagnosis without a
        // session-long lockout from one bad probe.

        // Rune-pouch gate: we can't introspect pouch contents, but a missing
        // pouch + no loose runes is a definite cast-will-fail signal. One-shot
        // warning, then keep checking each tick (so re-banking with a pouch
        // re-enables casts mid-session).
        if (!hasVengeanceRunes()) {
            vengLastGateReason = "no rune pouch";
            if (!runePouchWarningLogged) {
                // 1.9.60.1: dump inventory item names so we can see why
                // the pouch isn't being detected. User: 'Rune pouch is
                // right, idk why its not able to find it so it can
                // cast.' If a variant name is in play, the dump will
                // reveal it.
                String invDump = "";
                try {
                    invDump = Query.inventory()
                            .stream()
                            .map(InventoryItem::getName)
                            .filter(s -> s != null)
                            .distinct()
                            .collect(Collectors.joining(", "));
                } catch (Exception ignored) {}
                Log.warn("useVengeance is ON but no Rune pouch (or loose Vengeance runes) in "
                        + "inventory — vengeance casts will fail. Skipping. "
                        + "Inventory items: [" + invDump + "]");
                runePouchWarningLogged = true;
            }
            return;
        }
        // Got the pouch back — reset the one-shot so future drops re-log.
        runePouchWarningLogged = false;

        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        boolean bossAlive = corpOpt.isPresent() && isCorpAlive(corpOpt.get());
        boolean bossLowHealth = bossAlive && isCorpLowHealth(corpOpt.get());

        // 1.9.99.48: once we hit the kill phase with Corp still alive, force
        // the state machine into ACTIVE_CASTING. READY_FOR_FIRST_CAST only
        // casts when (!bossAlive || isInCorpLobby()) — neither is true
        // during a kill-phase fight — so without this transition the bot
        // would never veng in-fight. The original READY → ACTIVE transition
        // in updateHealthTracking required an HP drop to register; this
        // explicit transition is the more direct trigger for the kill phase.
        if (vengeanceState == VengeanceState.READY_FOR_FIRST_CAST && bossAlive) {
            Log.info("Kill phase reached — promoting vengeanceState to ACTIVE_CASTING");
            vengeanceState = VengeanceState.ACTIVE_CASTING;
        }

        switch (vengeanceState) {
            case READY_FOR_FIRST_CAST:
                handleReadyForFirstCast(bossAlive);
                break;
            case ACTIVE_CASTING:
                handleActiveCasting(bossAlive, bossLowHealth);
                break;
        }

        bossWasAlive = bossAlive;
    }

    // ========== STATE HANDLERS ==========
    private void handleReadyForFirstCast(boolean bossAlive) {
        // Can cast when: boss is dead OR we're in lobby
        boolean canCastNow = !bossAlive || isInCorpLobby();
        boolean recentlyCastWithoutDamage = hasRecentVengeanceCastWithoutDamage();

        if (canCastNow && canCastVengeance() && !recentlyCastWithoutDamage) {
            if (castVengeance()) {
                Log.info("Cast first vengeance (ready state) — will protect until first damage taken");
            }
        } else if (recentlyCastWithoutDamage) {
            Log.debug("Skipping vengeance cast - recently cast and no damage taken yet");
        }
    }

    /** 1.9.18: are we in the kill phase (post-spec-dump)? Vengeance only
     *  belongs here. Two triggers per the user's strategy:
     *    (a) teamPhaseNeeded() == 0  — all phase targets met, no more
     *        spec dumping wanted, time to melee finish
     *    (b) Corp HP visible AND below corpMinHpForSpec (1700) — a
     *        teammate is killing Corp, spec dumping is pointless, switch
     *        to melee
     *  Returning false during pre-engagement, walks, and spec-dump phases
     *  short-circuits handleVengeanceLogic so no veng cast fires while
     *  it'd be wasted.
     */
    private boolean isInKillPhase() {
        if (teamPhaseNeeded() == 0) return true;
        // 1.9.99.181: low-water latch. If we've ALREADY seen Corp below the
        // spec floor this kill, stay latched — even if Corp drops out of the
        // local NPC cache for a tick (relocate, scroll) or the bar reads
        // invisible/zero between animation frames.
        double floorFraction = settings.corpMinHpForSpec / 2000.0;
        if (minCorpHpPercentThisKill > 0.0 && minCorpHpPercentThisKill < floorFraction) {
            return true;
        }
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return false;
        Npc corp = corpOpt.get();
        if (!corp.isHealthBarVisible()) {
            // 1.9.99.176: do NOT reset firstNonZeroCorpHpAt here. Bar can
            // briefly disappear during Corp animations; resetting the
            // settling timer makes the bot re-wait 600ms each flicker and
            // false-fail kill-phase detection. Trust persists across
            // flickers within a kill. Reset still happens at kill-end.
            return false;
        }
        double hpPercent = corp.getHealthBarPercent();
        if (hpPercent > maxCorpHpPercentThisKill) {
            maxCorpHpPercentThisKill = hpPercent;
        }
        // 1.9.99.181: track low-water for the latch above. Only update on
        // strictly positive readings (filters the transient 0 frames the
        // hpPercent <= 0 bail below was guarding against).
        if (hpPercent > 0.0 && hpPercent < minCorpHpPercentThisKill) {
            minCorpHpPercentThisKill = hpPercent;
        }
        // 1.9.99.167: REPLACED the max-ratchet gate with a stability-time
        // gate. Pre-1.9.99.167 this required maxCorpHpPercentThisKill >
        // 5% before trusting low-HP readings — guarded against transient
        // 0% bar on fresh-engage, but ALSO blocked late-join: if the bot
        // first saw Corp at e.g. 0.6% HP (joining a near-dead Corp),
        // max ratcheted to 0.006 and stayed there (Corp HP doesn't go
        // UP mid-kill). isInKillPhase returned false → bot tabbed for
        // POH instead of committing to the kill. User log: "Spec HIT
        // confirmed ... corpHP=0.6% ... Insta-tele to POH". Now: only
        // skip when the bar reads <= 0% (transient pre-populate state),
        // and require the bar to have been visible AND non-zero for
        // ≥ 1500ms before trusting a low-HP reading. Catches transient
        // 0 without blocking late-join.
        if (hpPercent <= 0.0) {
            // 1.9.99.176: don't reset firstNonZeroCorpHpAt on 0% readings
            // mid-kill — Corp's HP bar can flicker invisible/zero between
            // animation frames. Once we've trusted the bar this kill, keep
            // trusting until kill end (reset by coordinatorOnKillEnded /
            // resetPerKillStateAfterAbort). Pre-1.9.99.176 the 600ms
            // settling timer reset every flicker, so the bot could read
            // Corp at 1200 HP, hit a flicker, and POH within the 600ms
            // re-settling window. User: "the bots still tab out when the
            // boss is under <1200 hp but the gate is set to what... 1500?"
            return false;
        }
        // 1.9.99.170: dropped 1500ms stability gate to 600ms (one game tick).
        // User reported bot tabbed out at 1300 HP — the 1.5s settling window
        // was blocking late-join detection: bot teleports back, walks in,
        // sees Corp at 1300 HP, but the stability timer hadn't elapsed yet
        // so isInKillPhase returned false → bot still POH'd. One tick is
        // enough to filter a single-frame transient 0 (the hpPercent > 0
        // check above catches the same case anyway).
        // 1.9.99.177: settling-timer gate REMOVED entirely. User: "the 600
        // ms gate shouldnt make a difference. we arnt doing over 300
        // health in one tick". The hpPercent <= 0 check above already
        // filters the transient 0-reading edge case the timer was meant
        // to guard against. Keeping the timer field for paint diagnostics
        // only — not used in the gating decision anymore.
        if (firstNonZeroCorpHpAt == 0) {
            firstNonZeroCorpHpAt = System.currentTimeMillis();
        }
        // 1.9.99.116: scale fix — hpPercent is 0-1, multiply by 2000 (Corp's
        // max HP) directly, not (/100 * 2000). Was computing approxHp = ~10
        // for any HP value, so this returned "kill phase = true" the moment
        // the bar showed anything — until blocked by the 5.0 gate above
        // which also never matched. Net effect: isInKillPhase ALWAYS
        // returned false via the HP path; only teamPhaseNeeded()==0 could
        // trigger kill phase. User: "we need to just give up on spec dumping
        // and continue normally killing the boss if our teammates have it
        // under 1700 health" — now actually works.
        int approxHp = (int) (hpPercent * 2000);
        return approxHp < settings.corpMinHpForSpec;
    }

    /** 1.9.99.164: true if ANY teammate bot's coordinator snapshot reports
     *  inKillPhase==true. Used by prepareSpecWeaponForCorp on join so a
     *  bot arriving from POH/lobby (where Corp's HP isn't visible) can
     *  skip spec-weapon prep entirely if another teammate has already
     *  confirmed kill phase. Coordinator must be enabled for this to
     *  return anything other than false. */
    private boolean isTeamInKillPhase() {
        if (settings == null || !settings.coordinatorEnabled) return false;
        try {
            TeamState ts = null;
            if (portCoordinator != null) {
                ts = portCoordinator.read();
            }
            if (ts == null && coordinator != null) {
                ts = coordinator.read();
            }
            if (ts == null || ts.accounts == null) return false;
            String selfName = MyPlayer.getUsername();
            for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
                if (selfName != null && selfName.equals(e.getKey())) continue;
                AccountSnapshot snap = e.getValue();
                if (snap != null && snap.inKillPhase) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean hasRecentVengeanceCastWithoutDamage() {
        // If we cast vengeance recently (within 60 seconds) and haven't been in combat yet
        long timeSinceLastCast = System.currentTimeMillis() - lastVengeanceCast;
        boolean recentCast = timeSinceLastCast < 60000; // 60 seconds

        // If recent cast and we haven't been in combat with Corp yet, don't recast
		return recentCast && !hasUsedVengeanceThisTrip;
	}

    // 1.9.99.81: simplified handleActiveCasting — dropped the bossAlive
    // and bossLowHealth gates. Pre-1.9.99.81 the alive-branch required
    // (bossAlive && !bossLowHealth) which silently no-op'd whenever Corp
    // was briefly out of render (between swings, Corp roaming off-screen)
    // OR Corp HP < 85 (the last few seconds of the kill). The
    // java_Pm7TDAwTST.png screenshot showed state=ACTIVE_CASTING,
    // killPhase=yes, magic=94/94, runes=ok, tookDmg=yes, cd=ready — every
    // gate green — but attempts=0 because the alive-branch never entered.
    // Veng is a self-buff: it reflects the NEXT damage taken, regardless
    // of whether Corp is in render right this tick. Kill-phase + active-
    // combat state + canCastVengeance is sufficient.
    // The boss-death branch (cast once after boss died) is also dropped —
    // it was a nice-to-have but added complexity and never reliably worked
    // (couple of cases in the logs had it fire instead of the alive-branch
    // because Corp HP < 85 blocked alive before death).
    // User: "we just straight up dont veng and i cant figure out why".
    private void handleActiveCasting(boolean bossAlive, boolean bossLowHealth) {
        long now = System.currentTimeMillis();
        long timeSinceLastCast = now - lastVengeanceCast;
        long randomCooldown = TribotRandom.uniform(VENG_MIN_COOLDOWN, VENG_MAX_COOLDOWN);
        // 1.9.99.77: failed-attempt throttle. If we attempted recently
        // and the cast didn't stamp lastVengeanceCast (lastVengeanceCast
        // still 0 OR older than lastVengAttemptAt), the click landed
        // but the spell didn't fire — wait VENG_FAILED_RETRY_THROTTLE_MS
        // before another widget click. Prevents spam when magic is
        // drained or the widget search is missing.
        boolean recentFailedAttempt = lastVengAttemptAt > lastVengeanceCast
                && (now - lastVengAttemptAt) < VENG_FAILED_RETRY_THROTTLE_MS;

        if (timeSinceLastCast >= randomCooldown && !recentFailedAttempt && canCastVengeance()) {
            Log.info("Attempting to cast vengeance during combat (active casting mode)");
            if (castVengeance()) {
                Log.info("Successfully cast vengeance during combat");
            } else {
                Log.warn("Failed to cast vengeance during combat - will retry in "
                        + (VENG_FAILED_RETRY_THROTTLE_MS / 1000) + "s");
            }
        } else if (timeSinceLastCast >= randomCooldown && recentFailedAttempt) {
            long waitMs = VENG_FAILED_RETRY_THROTTLE_MS - (now - lastVengAttemptAt);
            vengLastGateReason = "post-fail throttle " + waitMs + "ms";
        } else if (timeSinceLastCast >= randomCooldown) {
            // 1.9.99.48: cooldown done but canCastVengeance() blocked —
            // log WHY so we can diagnose silent-vengeance issues in the
            // field. User: "i want codex to audit it ... Add logs when
            // kill phase is reached but canCastVengeance() blocks".
            logVengeanceBlocked();
        }
    }

    // ========== HEALTH TRACKING ==========
    private void updateHealthTracking() {
        int currentHealth = MyPlayer.getCurrentHealth();

        // Check if HP went down (transition to active casting)
        if (previousHealth > 0 && currentHealth < previousHealth) {
            // 1.9.97: HP dropped — definitive "took damage" signal for veng
            // consumed detection. Flag stays true until next successful cast.
            tookDamageSinceLastVeng = true;
            if (vengeanceState == VengeanceState.READY_FOR_FIRST_CAST) {
                Log.info("HP went down, switching to active vengeance casting");
                vengeanceState = VengeanceState.ACTIVE_CASTING;
            }
            // 1.9.99.205: capture single-tick HP drops at/above the big-hit
            // threshold. Multiple ticks of damage in a row don't accumulate
            // — we track the LARGEST single drop within the window so the
            // override only fires for genuine big magic hits, not slow
            // bleeding from stomp+core ticks.
            int drop = previousHealth - currentHealth;
            if (drop >= BIG_HIT_THRESHOLD) {
                lastBigHitMagnitude = drop;
                lastBigHitAt = System.currentTimeMillis();
                Log.warn("Big hit detected: " + drop + " damage ("
                        + previousHealth + " -> " + currentHealth
                        + ") — eat override active for "
                        + BIG_HIT_EAT_WINDOW_MS + "ms");
            }
        }

        previousHealth = currentHealth;
    }

    // 1.9.99.206: preemptive karambwan eat during the approach to Corp.
    // The opener-combo death pattern: bot walks into the boss room, magic
    // hits 35 then 30 while still far away, then runs up and gets hit for
    // another 30 = dead. User wants a single karambwan eaten DURING the
    // walk (not while swinging, not while inside spec range) to absorb the
    // opener. Strict gates so this never disrupts spec dumping:
    //   - recent big hit (35+) in last 4s
    //   - MyPlayer.isMoving() — only during the actual walk
    //   - distance to Corp > 7 tiles — clearly the approach phase
    //   - HP has room to heal (else karambwan is wasted)
    //   - karambwan in inventory
    //   - throttled by lastEmergencyEatAt so we don't eat every tick
    private long lastPreemptiveKarambwanAt = 0;
    private static final long PREEMPTIVE_KARAM_COOLDOWN_MS = 3000;
    private void preemptiveApproachKarambwan() {
        long nowK = System.currentTimeMillis();
        if (nowK - lastBigHitAt > BIG_HIT_EAT_WINDOW_MS) return; // no recent big hit
        if (nowK - lastPreemptiveKarambwanAt < PREEMPTIVE_KARAM_COOLDOWN_MS) return; // throttle
        try {
            if (!MyPlayer.isMoving()) return; // only while walking
            WorldTile myT = MyPlayer.getTile();
            if (myT == null) return;
            Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (!corpOpt.isPresent()) return;
            WorldTile corpT = corpOpt.get().getTile();
            if (corpT == null) return;
            if (myT.distanceTo(corpT) <= 7) return; // too close — approach phase only
            int curHp = MyPlayer.getCurrentHealth();
            // Don't bother if we'd waste the heal (karambwan = +18).
            if (curHp >= 99 - 12) return; // arbitrary slack — eat if there's >=12 headroom
            if (Inventory.getCount("Cooked karambwan") <= 0) return;
            Log.info("PREEMPTIVE: ate karambwan during approach (HP=" + curHp
                    + ", dist=" + myT.distanceTo(corpT) + " tiles from Corp, last big hit "
                    + lastBigHitMagnitude + " dmg " + (nowK - lastBigHitAt) + "ms ago)");
            if (eatKarambwan()) {
                lastPreemptiveKarambwanAt = nowK;
            }
        } catch (Throwable ignored) {}
    }

    private boolean isVengeanceOnCooldown() {
        return (System.currentTimeMillis() - lastVengeanceCast) < 30000; // Back to 30s since we have smart detection
    }

    private boolean isVengeanceStillActive() {
        // If we're not interacting with Corp, vengeance status doesn't matter for this check
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) {
            return false;
        }

        Npc corp = corpOpt.get();

        // Check if we're interacting with Corp (attacking or being attacked)
        boolean interactingWithCorp = isPlayerAttackingCorp(corp) || corp.isInteractingWithMe();

        if (interactingWithCorp) {
            // If we're fighting Corp but our HP bar is NOT visible, vengeance is still protecting us
            boolean hpBarVisible = MyPlayer.isHealthBarVisible();
            boolean vengeanceStillActive = !hpBarVisible;

            if (vengeanceStillActive) {
                Log.debug("Vengeance still active - fighting Corp but no HP bar visible (no damage taken yet)");
            } else {
                Log.debug("Vengeance consumed - HP bar visible (damage was taken)");
            }

            return vengeanceStillActive;
        }

        return false; // Not fighting Corp, so this check doesn't apply
    }

    /**
     * Check if Corp is alive (has health)
     */
    private boolean isCorpAlive(Npc corp) {
        // If health bar is visible, Corp is alive and in combat
        // If no health bar but Corp exists, it's probably at full health
        return corp != null && (corp.isHealthBarVisible() || !isNpcInCombat(corp));
    }

    /**
     * Determine if we should stay in the boss room to prevent Corp from roaming
     * Strategy: Always try to keep at least one team member in the room
     */
    private boolean shouldStayToPreventRoaming() {
        // 1.9.99.153: filter at line 8927 was checking isInCorpBossRoom()
        // (a no-arg call that returns the BOT's location), not the player's
        // location. So when the bot was in the boss room, every visible
        // acceptable teammate counted; when the bot was in the lobby, count
        // was always 0. Audit caught this. Fixed to check each player's
        // tile against the corpCave polygon, like
        // hasAcceptableTeammatesInBossRoom() does.
        if (settings == null || settings.acceptableTeammates == null) return true;
        long acceptableTeammatesInBossRoom = Query.players()
                .stream()
                .filter(player -> player.getName() != null
                        && !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .filter(player -> {
                    WorldTile t = player.getTile();
                    return t != null && corpCave.contains(t);
                })
                .count();

        // If we're the only one or there's only one other teammate, consider staying
        return acceptableTeammatesInBossRoom <= 1;
    }

    private Optional<Npc> findDarkCore() {
        // Try exact name first
        Optional<Npc> coreOpt = Query.npcs().nameEquals(DARK_CORE).findFirst();
        if (coreOpt.isPresent()) {
            return coreOpt;
        }

        // Try alternative names
        List<String> alternativeNames = Arrays.asList(
                "Core", "dark core", "Dark Core", "Summoning spirit"
        );

        for (String name : alternativeNames) {
            coreOpt = Query.npcs().nameEquals(name).findFirst();
            if (coreOpt.isPresent()) {
                return coreOpt;
            }
        }

        return Optional.empty();
    }

    /**
     * Reset tracking variables when starting a new trip
     */
    private void resetTripTracking() {
        // 1.9.99.157: reset trip-wide real-teammate lock at every banking
        // trip. Within a single trip the multiplier stays stable; humans
        // walking in/out between kills don't flip it.
        lockedRealCountThisTrip = -1;
        hasUsedVengeanceThisTrip = false;
        vengeanceQueued = false;
        tookDamageSinceLastVeng = true; // 1.9.97: allow first cast of new trip
        vengeanceUseTime = 0;
        prayerActivationQueued = false;
        prayerActivationTime = 0;
        prayerDeactivationQueued = false;
        prayerDeactivationTime = 0;
        corpWasAliveLastCheck = false;
        specWeaponSwitchQueued = false;
        specWeaponSwitchTime = 0;
        needsToSwitchBackFromSpec = false;
        // 1.9.9: reset XP-based hit detection tracking on new trip.
        xpAtSpec = -1;
        corpHpAtSpec = -1; // 1.9.99.37
        pendingHits.clear(); // 1.9.99.39

        startedFightingWithTeammates = false;
        fightStartTime = 0;

        // Reset core dodging tracking
        resetCoreDodgeTracking();
		resetRestorationTracking();

        // 1.9.99.189: REVERTED 1.9.99.188's per-kill state wipe. Resetting
        // committedSpecPhase here broke the ratchet protection: with
        // accumulated specsThisKill persisting (stat reductions stick to
        // Corp across bank trips per the design), clearing committedSpecPhase
        // let teamPhaseNeeded() drop from 3 to 0 because all phase quotas
        // were already met by the accumulated specs — bot then jumped to
        // kill phase and equipped Fang on a Corp it hadn't actually
        // finished phase 2 (Arclight) for. The ratchet existed to prevent
        // exactly this transition. User: "we started using our fang and
        // committed to the kill even though we hadnt dumped the appropriate
        // amount of arclight specs."

        Log.info("Trip tracking reset for new Corp trip");
    }

    // New method to handle eating while dodging core
    private void handleHealthAndPrayerDuringCore() {
        int currentHealth = MyPlayer.getCurrentHealth();

        // Emergency eating during core dodging - don't wait for movement to stop
        if (currentHealth <= INTERNAL_EMERGENCY_HP) {
            emergencyComboEatDuringMovement();
        }
        // Normal eating if health is low
        else if (currentHealth <= eatHealthThreshold()) {
            eatDuringMovement();
        }

        // Prayer during movement
        if (Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD) {
            drinkPrayerPotionDuringMovement();
        }
    }

    private boolean eatDuringMovement() {
        // Don't wait for health increase - just eat and continue moving
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        if (sharkOpt.isPresent()) {
            if (sharkOpt.get().click("Eat")) {
                Log.info("Ate Shark while moving");
                return true;
            }
        }

        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent()) {
            if (karambwanOpt.get().click("Eat")) {
                Log.info("Ate Karambwan while moving");
                return true;
            }
        }

        return false;
    }

    private boolean emergencyComboEatDuringMovement() {
        Log.info("EMERGENCY: Combo eating while dodging core!");

        // Eat shark
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        boolean ateShark = false;
        if (sharkOpt.isPresent()) {
            sharkOpt.get().click("Eat");
            Log.info("Emergency: Ate Shark while moving");
            ateShark = true;
        }

        // 1.9.70: small delay between shark and karambwan so BOTH clicks
        // register. Pre-1.9.70 they fired in the same tick and only the
        // karambwan click landed — bot ate ~16 HP instead of ~38 HP and
        // died to subsequent stomp/jump. The non-moving emergencyComboEat
        // (line ~8103) already has this delay; this one didn't.
        if (ateShark) {
            Waiting.waitUniform(40, 80);
        }
        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent()) {
            karambwanOpt.get().click("Eat");
            Log.info("Emergency: Ate Karambwan while moving");
            return true;
        }

        return ateShark;
    }

    private boolean drinkPrayerPotionDuringMovement() {
        // Just drink, don't wait for prayer to increase
        for (String potName : SUPER_RESTORE_NAMES) {
            Optional<InventoryItem> potOpt = Query.inventory().nameEquals(potName).findFirst();
            if (potOpt.isPresent()) {
                if (potOpt.get().click("Drink")) {
                    Log.info("Drank " + potName + " while moving");
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTileWalkable(WorldTile tile) {
        try {
            // Use TRiBot Query system to check if tile is reachable
            // 1.9.99.161: relaxed reachability gate. Pre-1.9.99.161 we
            // ran Query.tiles().filter(equals).isReachable() which dropped
            // 15 of 24 perimeter candidates per log analysis — the query
            // requires the tile to be in the iterated local-tile snapshot
            // AND pass an SDK pathfinding check, both flaky during Corp
            // movement / instance refresh. Bots ended up with <10 options
            // and converged onto the same picks. Now: just bound the
            // distance from us; trust the Corp-hitbox safety filter +
            // the L-shape walk + the live-recheck in moveToNearestCorpPosition
            // to handle unreachable tiles at walk time. If a tile actually
            // can't be reached, LocalWalking.walkTo returns false and we
            // re-evaluate next tick.
            WorldTile myPos = MyPlayer.getTile();
            if (myPos == null) {
                Log.debug("No player tile — can't validate tile: " + tile);
                return false;
            }
            if (myPos.distanceTo(tile) > 15) {
                Log.debug("Tile too far away: " + tile
                        + " (distance: " + myPos.distanceTo(tile) + ")");
                return false;
            }

            Log.debug("Tile validated as walkable: " + tile);
            return true;

        } catch (Exception e) {
            Log.error("Error checking tile walkability for " + tile + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isImprovedSafePosition(WorldTile pos, WorldTile corpPos, WorldTile corePos, List<WorldTile> teammatePos) {
        // REQUIREMENT 1: Must be actually walkable (MOST IMPORTANT - prevents wall walking)
        if (!isTileWalkable(pos)) {
            Log.debug("Position rejected - not walkable: " + pos);
            return false;
        }

        // REQUIREMENT 2: Must be far enough from dark core (SAFETY)
        if (pos.distanceTo(corePos) < SAFE_DISTANCE_FROM_CORE) {
            Log.debug("Position rejected - too close to core: " + pos + " (distance: " + pos.distanceTo(corePos) + ")");
            return false;
        }

        // REQUIREMENT 3: Must be within reasonable attack range of Corp
        if (corpPos != null && pos.distanceTo(corpPos) > MAX_ATTACK_DISTANCE_FROM_CORP) {
            Log.debug("Position rejected - too far from Corp: " + pos + " (distance: " + pos.distanceTo(corpPos) + ")");
            return false;
        }

        // REQUIREMENT 4: Should not be too close to Corp (avoid melee range during core)
        if (corpPos != null && pos.distanceTo(corpPos) < 3) {
            Log.debug("Position rejected - too close to Corp: " + pos + " (distance: " + pos.distanceTo(corpPos) + ")");
            return false;
        }

        // REQUIREMENT 5: Avoid crowding teammates (less critical during emergency)
        for (WorldTile teammatePosition : teammatePos) {
            if (pos.distanceTo(teammatePosition) < 2) {
                Log.debug("Position rejected - too close to teammate: " + pos);
                return false;
            }
        }

        Log.debug("Position approved as safe: " + pos);
        return true;
    }

    private WorldTile calculateImprovedSafePosition(WorldTile myPos, WorldTile corpPos, WorldTile corePos) {
        Log.info("Calculating improved safe position with walkable tile validation");

        // Get teammate positions
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // Try positions in expanding rings around current position
        for (int radius = 3; radius <= 8; radius++) {
            List<WorldTile> walkablePositions = getWalkablePositionsInRadius(myPos, radius);

            for (WorldTile candidate : walkablePositions) {
                if (isTileWalkable(candidate) && isAdvancedSafePosition(candidate, corpPos, corePos, teammatePositions)) {
                    Log.info("Safe walkable position found at radius " + radius + ": " + candidate);
                    return candidate;
                }
            }
        }

        Log.warn("No improved safe walkable position found, using emergency escape");
        return calculateEmergencyEscapePosition(myPos, corePos);
    }

    /**
     * Calculate emergency escape position with walkable validation
     */
    private WorldTile calculateEmergencyEscapePosition(WorldTile myPos, WorldTile corePos) {
        Log.info("Calculating emergency escape position with walkable validation");

        // Calculate direction away from core
        int deltaX = myPos.getX() - corePos.getX();
        int deltaY = myPos.getY() - corePos.getY();

        // Normalize direction (handle case where we're on same tile)
        if (deltaX == 0 && deltaY == 0) {
            // We're on same tile as core! Pick a random direction
            deltaX = TribotRandom.uniform(-1, 1);
            deltaY = TribotRandom.uniform(-1, 1);
            if (deltaX == 0 && deltaY == 0) {
                deltaX = 1; // Fallback
            }
        }

        // Make direction unit vector (ish)
        int dirX = deltaX > 0 ? 1 : (deltaX < 0 ? -1 : 0);
        int dirY = deltaY > 0 ? 1 : (deltaY < 0 ? -1 : 0);

        // Try distances from 4 to 6 tiles away with walkable validation
        for (int distance = 4; distance <= 6; distance++) {
            WorldTile candidate = new WorldTile(
                    myPos.getX() + (dirX * distance),
                    myPos.getY() + (dirY * distance),
                    myPos.getPlane()
            );

            // Check both safety and walkability
            if (candidate.distanceTo(corePos) >= SAFE_DISTANCE_FROM_CORE && isTileWalkable(candidate)) {
                Log.info("Emergency walkable position found at distance " + distance + ": " + candidate);
                return candidate;
            }
        }

        // Last resort - try any walkable tile around us
        List<WorldTile> nearbyWalkable = getWalkablePositionsInRadius(myPos, 5);
        for (WorldTile candidate : nearbyWalkable) {
            if (candidate.distanceTo(corePos) >= SAFE_DISTANCE_FROM_CORE) {
                Log.info("Emergency walkable position found via radius search: " + candidate);
                return candidate;
            }
        }

        Log.error("Could not find any walkable emergency escape position!");
        return null;
    }

    /**
     * Calculate available space with walkable tile validation
     */
    private int calculateAvailableSpace(WorldTile fromPos, CoreDodgeDirection direction, WorldTile corpPos) {
        int space = 0;

        for (int distance = 1; distance <= CORE_MAX_DODGE_DISTANCE + 2; distance++) {
            WorldTile testPos = getPositionInDirection(fromPos, direction, distance);

            // Check if position is blocked by Corp
            if (corpPos != null && testPos.distanceTo(corpPos) <= 2) {
                Log.debug("Position blocked by Corp at distance " + distance + ": " + testPos);
                break; // Corp blocks further movement in this direction
            }

            // Check if position is walkable
            if (isTileWalkable(testPos)) {
                space = distance;
                Log.debug("Valid walkable space at distance " + distance + ": " + testPos);
            } else {
                Log.debug("Hit unwalkable tile at distance " + distance + ": " + testPos);
                break; // Hit an obstacle
            }
        }

        Log.info("Available walkable space in direction " + direction + ": " + space + " tiles");
        return space;
    }

// ========== ENHANCED AVAILABLE SPACE CALCULATION ==========

    private List<WorldTile> getWalkablePositionsInRadius(WorldTile center, int radius) {
        List<WorldTile> walkablePositions = new ArrayList<>();

        // Generate positions in a radius pattern
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                // Skip center point and positions too close to center
                if ((dx == 0 && dy == 0) || (Math.abs(dx) + Math.abs(dy)) < 2) {
                    continue;
                }

                WorldTile candidate = new WorldTile(
                        center.getX() + dx,
                        center.getY() + dy,
                        center.getPlane()
                );

                // Only add if tile is actually walkable
                if (isTileWalkable(candidate)) {
                    walkablePositions.add(candidate);
                } else {
                    Log.debug("Skipping unwalkable tile: " + candidate);
                }
            }
        }

        Log.info("Found " + walkablePositions.size() + " walkable positions in radius " + radius + " around " + center);
        return walkablePositions;
    }

    /**
     * Generate natural random delays using normal distribution
     * Creates more human-like timing that clusters around realistic values
     */
    private long getNaturalRandomDelay(long min, long max) {
        // Calculate mean (closer to min for more responsive timing)
        int mean = (int) (min + (max - min) * 0.3); // 30% toward max from min

        // Calculate standard deviation (about 20% of range)
        int sd = (int) ((max - min) * 0.2);

        // Generate normal distribution value
        int result = TribotRandom.normal((int) min, (int) max, mean, sd);

        // Ensure result is within bounds (normal distribution can exceed bounds)
        return Math.max(min, Math.min(max, result));
    }

    /**
     * Generate quick reaction delays (heavily skewed toward minimum)
     */
    private long getQuickReactionDelay(long min, long max) {
        // Mean very close to minimum for quick reactions
        int mean = (int) (min + (max - min) * 0.15); // Only 15% toward max

        // Smaller standard deviation for tighter clustering
        int sd = (int) ((max - min) * 0.15);

        int result = TribotRandom.normal((int) min, (int) max, mean, sd);
        return Math.max(min, Math.min(max, result));
    }

    // ========== NATURAL RANDOM HELPER ==========

    /**
     * Generate skewed random values (favoring lower end)
     */
    private long getSkewedRandom(long min, long max) {
        // Use quick reaction delay pattern for skewed random
        return getQuickReactionDelay(min, max);
    }

    /**
     * Count total prayer potion doses available in inventory
     */
    private int getPrayerDoses() {
        int totalDoses = 0;

        for (String potName : SUPER_RESTORE_NAMES) {
            List<InventoryItem> pots = Query.inventory().nameEquals(potName).toList();
            for (InventoryItem pot : pots) {
                // Each potion is a separate inventory slot (not stacked)
                // Extract dose count from potion name
                if (potName.contains("(4)")) {
                    totalDoses += 4; // Each (4) potion adds 4 doses
                } else if (potName.contains("(3)")) {
                    totalDoses += 3; // Each (3) potion adds 3 doses
                } else if (potName.contains("(2)")) {
                    totalDoses += 2; // Each (2) potion adds 2 doses
                } else if (potName.contains("(1)")) {
                    totalDoses += 1; // Each (1) potion adds 1 dose
                }
            }
        }

        return totalDoses;
    }

    /**
     * Handle prayer activation timing in lobby
     */
    private void handlePrayerActivationInLobby() {
        // Only activate if we have acceptable teammates and Corp is alive somewhere
        if (!hasAcceptableTeammatesInLobby() || Prayer.isQuickPrayerEnabled() || prayerActivationQueued) {
            return; // Don't double-queue
        }

        // Check if Corp is alive in boss room (even if we're not there yet)
        boolean corpAliveInBossRoom = Query.npcs().nameEquals(CORPOREAL_BEAST)
                .stream()
                .anyMatch(corp -> isCorpAlive(corp));

        if (!corpAliveInBossRoom) {
            return;
        }

        // Queue prayer activation using quick reaction timing
        long randomDelay = getQuickReactionDelay(600, 2100); // 1-7 seconds, clustered toward 1-2s
        prayerActivationTime = System.currentTimeMillis() + randomDelay;
        prayerActivationQueued = true;
        Log.info("Prayer activation queued for " + (randomDelay / 1000) + " seconds from now (from lobby)");
    }

    // ========== PRAYER DOSE COUNTING ==========

    /**
     * Handle prayer activation timing in boss room
     */
    private void handlePrayerActivationInBossRoom(Npc corp) {
        if (!isCorpAlive(corp) || Prayer.isQuickPrayerEnabled() || prayerActivationQueued) {
            return; // Don't double-queue
        }

        // Queue prayer activation using quick reaction timing
        long randomDelay = getQuickReactionDelay(1000, 7000); // 1-7 seconds, clustered toward 1-2s
        prayerActivationTime = System.currentTimeMillis() + randomDelay;
        prayerActivationQueued = true;
        Log.info("Prayer activation queued for " + (randomDelay / 1000) + " seconds from now (from boss room)");
    }

    // ========== ENHANCED PRAYER TIMING SYSTEM ==========

    /**
     * Handle queued prayer activation timing
     */
    private void handlePrayerActivationTiming() {
        if (!prayerActivationQueued || Prayer.isQuickPrayerEnabled()) {
            return;
        }

        // Check if it's time to activate prayers
        if (System.currentTimeMillis() >= prayerActivationTime) {
            if (Prayer.enableQuickPrayer()) {
                Log.info("Quick prayers enabled via timing system");
                prayerActivationQueued = false;
            } else {
                Log.warn("Failed to enable quick prayers, will retry");
                // Reset timing for retry
                prayerActivationTime = System.currentTimeMillis() + TribotRandom.uniform(1000, 3000);

            }
        }
    }

    /**
     * Queue prayer deactivation when Corp dies
     */
    private void queuePrayerDeactivation() {
        if (!Prayer.isQuickPrayerEnabled() || prayerDeactivationQueued) {
            return;
        }

        long randomDelay = getSkewedRandom(1000, 3000); // 1-3 seconds, skewed to lower end
        prayerDeactivationTime = System.currentTimeMillis() + randomDelay;
        prayerDeactivationQueued = true;
        Log.info("Prayer deactivation queued for " + (randomDelay / 1000) + " seconds from now");
    }

    /**
     * Handle queued prayer deactivation timing
     */
    private void handlePrayerDeactivationTiming() {
        if (!prayerDeactivationQueued || !Prayer.isQuickPrayerEnabled()) {
            return;
        }

        // Check if it's time to deactivate prayers
        if (System.currentTimeMillis() >= prayerDeactivationTime) {
            if (Prayer.disableQuickPrayer()) {
                Log.info("Quick prayers disabled via timing system");
                prayerDeactivationQueued = false;
            } else {
                Log.warn("Failed to disable quick prayers, will retry");
                // Reset timing for retry
                prayerDeactivationTime = System.currentTimeMillis() + getSkewedRandom(1000, 2000);
            }
        }
    }

    /** 1.9.52: pick a tile adjacent to Corp's hitbox on the player's side
     *  (so reaching it doesn't require crossing the hitbox). Used as a
     *  fallback when all canonical N/S/E/W cardinals would require a
     *  cross — typically when the player enters from a diagonal angle
     *  and Corp is positioned such that the perpendicular cardinals
     *  cross the hitbox.
     *
     *  Strategy: project the player onto the nearest edge of Corp's
     *  bounding box and step 1 tile away from Corp along the direction
     *  perpendicular to that edge. Result lands the bot 1 tile from
     *  the hitbox on the player's side, still in melee range. */
    private WorldTile synthesizePlayerSidePosition(WorldTile myPos,
                                                   WorldTile corpCenter,
                                                   Area corpArea) {
        if (myPos == null || corpCenter == null || corpArea == null) return null;
        // 1.9.99.176: restored to original purpose. Project player onto the
        // nearest edge of Corp's bounding box and step 1 tile outside.
        // 1.9.99.170 attempted to convert this into a multi-corner picker
        // but referenced `target` which isn't a parameter here — compile
        // error. The actual multi-corner logic lives in pickCornerWaypoint
        // (below) and was already correct; this orphan was the wrong
        // function to edit.
        int dx = myPos.getX() - corpCenter.getX();
        int dy = myPos.getY() - corpCenter.getY();
        int cx = corpCenter.getX(), cy = corpCenter.getY(), z = corpCenter.getPlane();
        WorldTile candidate;
        if (Math.abs(dx) >= Math.abs(dy)) {
            int sx = dx >= 0 ? 1 : -1;
            candidate = new WorldTile(cx + 3 * sx, myPos.getY(), z);
        } else {
            int sy = dy >= 0 ? 1 : -1;
            candidate = new WorldTile(myPos.getX(), cy + 3 * sy, z);
        }
        return corpArea.contains(candidate) ? null : candidate;
    }

    /** 1.9.49: true if the player is already standing in melee range of
     *  Corp — outside the 5x5 hitbox but close enough that no further
     *  movement is needed to attack. Returns false if the player is
     *  inside the hitbox (must step off via anti-stomp) or too far (need
     *  to walk closer). Skipping the move when this is true prevents the
     *  bot from flanking through Corp's hitbox to reach a 'canonical'
     *  N/S/E/W tile when the current frontal position is equally good. */
    private boolean isPlayerAlreadyInCorpMeleeRange(WorldTile playerPos, Npc corp) {
        if (playerPos == null || corp == null) return false;
        Area corpArea = corp.getArea();
        if (corpArea == null) return false;
        if (corpArea.contains(playerPos)) return false; // inside hitbox — anti-stomp handles
        WorldTile center = corpArea.getCenter();
        int dx = Math.abs(playerPos.getX() - center.getX());
        int dy = Math.abs(playerPos.getY() - center.getY());
        int maxOffset = Math.max(dx, dy);
        // Hitbox spans offsets -2..+2 from center (5x5). Adjacent tiles
        // sit at maxOffset == 3. Allow up to 5 so the player doesn't
        // have to be *exactly* 1 tile out — anywhere in the melee
        // "halo" around Corp is fine to attack from.
        return maxOffset >= 3 && maxOffset <= 5;
    }

    /**
     * Get dynamic positions around Corp's current location
     * This handles Corp roaming by calculating positions relative to where Corp actually is
     */
    private List<WorldTile> getDynamicCorpPositions(Npc corp) {
        // 1.9.99.179: NPE guards. corp.getArea() can return null
        // transiently (per the 1.9.90 comment in moveToNearestCorpPosition);
        // pre-1.9.99.179 this NPE'd on null Area, killing the tick.
        if (corp == null) return Collections.emptyList();
        Area corpArea = corp.getArea();
        if (corpArea == null) return Collections.emptyList();
        WorldTile corpCenter = corpArea.getCenter();
        if (corpCenter == null) return Collections.emptyList();

        List<WorldTile> positions = new ArrayList<>();

        Log.debug("Calculating dynamic positions around Corp at: " + corpCenter);

        for (int[] offset : CORP_POSITION_OFFSETS) {
            WorldTile position = new WorldTile(
                    corpCenter.getX() + offset[0],
                    corpCenter.getY() + offset[1],
                    corpCenter.getPlane()
            );

            // Ensure position is safe from Corp's area
            if (!corpArea.contains(position)) {
                positions.add(position);
                Log.debug("Valid Corp position: " + position);
            } else {
                Log.debug("Invalid Corp position (inside Corp area): " + position);
            }
        }

        if (positions.isEmpty()) {
            Log.warn("No valid positions found around Corp - Corp may be in an unusual location");
        }

        return positions;
    }


    // ========== ENHANCED VENGEANCE SYSTEM ==========

    /**
     * Check if we're in one of the good Corp positions relative to Corp's current location
     */
    private boolean isInGoodCorpPosition(Npc corp) {
        WorldTile myPos = MyPlayer.getTile();
        // 1.9.83: HARD reject if we're INSIDE Corp's hitbox. Pre-1.9.83
        // a player at (cx-2, cy) (inside the 5x5 hitbox) was distance 1
        // from the canonical (cx-3, cy) and the function returned true
        // — bot thought it was already 'positioned' and skipped the
        // move, engaging from inside the hitbox eating free stomp
        // damage every tick. User's recent death log showed exactly
        // this — no 'Moved to assigned Corp position' fired before
        // STOMP DEFENSE started spinning.
        try {
            Area corpArea = corp.getArea();
            if (corpArea != null && myPos != null && corpArea.contains(myPos)) {
                return false; // inside hitbox = NOT a good position
            }
        } catch (Exception ignored) {}

        List<WorldTile> dynamicPositions = getDynamicCorpPositions(corp);

        return dynamicPositions.stream()
                .anyMatch(pos -> myPos.distanceTo(pos) <= 2); // Allow 2 tile tolerance
    }

    // ========== DYNAMIC CORP POSITIONING SYSTEM ==========

    /**
     * Move to the nearest available Corp position around Corp's current location
     */
    // Update moveToNearestCorpPosition to use better assignment
	private boolean moveToNearestCorpPosition(Npc corp) {
		return moveToNearestCorpPosition(corp, false);
	}
	// 1.9.99.147: forceMove overload. When true, skips the
	// "already in melee range — stay put" early-return AND the
	// "already at bestPosition" short-range early-out. Encroachment
	// relocate MUST walk to a new tile even though we're already in
	// melee range; only the initial combat-entry path benefits from
	// staying put. User: "it still doesnt relocate" — paint showed
	// RELOCATING but the function early-returned at the melee-range
	// check before walking. Callers that genuinely need to move
	// (encroachment) pass true; default callers (initial entry, drift
	// recheck) pass false to preserve the 1.9.49 "stay frontal" behavior.
	private boolean moveToNearestCorpPosition(Npc corp, boolean forceMove) {
		// Phase E: try a coordinator-claimed offset first. Each bot claims a
		// different cardinal direction (N/S/E/W) around Corp so the team is
		// spread out. The claim is on the OFFSET, not the world tile — as Corp
		// moves around the cave, the actual target tile recomputes from
		// corp.getTile() + offset. Distance tolerance 2 means the bot doesn't
		// need to land exactly on the offset, just on Corp's claimed side.
		// 1.9.99.171: when forceMove (encroachment relocate), SKIP the
		// coordinator-claimed offset path. The claim is sticky per trip
		// — the bot keeps re-picking the same offset/tile every relocate,
		// even when stacked with a player on that tile. User log:
		// "Coordinator: claimed offset, target tile (2989, 4379, 2)"
		// firing identically on consecutive encroachment checks while
		// the bot was stacked with a human. Bypass the claim when
		// forceMove is set so the regular 24-tile perimeter picker
		// (with separation filter + jitter) runs and actually picks
		// a less-crowded tile. Also release the bot's current claim
		// so the next legitimate claim cycle can pick a different
		// offset.
		WorldTile claimed = forceMove ? null : pickCoordinatedCorpPosition(corp);
		if (forceMove && mySnapshot != null) {
			mySnapshot.claimedCorpOffset = null;
		}
		if (claimed != null && isPositionSafeFromCorpHitbox(claimed, corp)) {
			Log.info("Coordinator: claimed offset, target tile " + claimed);
			// 1.9.68: same hitbox-cross check as the non-coordinator
			// path. With 4-5 players each bot is assigned a different
			// cardinal — the bot on the FAR side from the entrance has
			// to flank around Corp to reach its tile. If the straight
			// line crosses Corp's hitbox, walk via an L-shape corner
			// first; if no safe corner exists, delegate to game
			// pathfinder via corp.interact('Attack').
			WorldTile myPosClaim = MyPlayer.getTile();
			Area corpAreaClaim = corp.getArea();
			if (myPosClaim != null && corpAreaClaim != null
					&& lineCrossesCorp(myPosClaim, claimed, corpAreaClaim)) {
				WorldTile corner = pickCornerWaypoint(myPosClaim, claimed,
						corpAreaClaim.getCenter(), corpAreaClaim);
				if (corner != null) {
					Log.info("Claimed tile " + claimed + " requires crossing Corp — "
							+ "L-shape via corner " + corner);
					LocalWalking.walkTo(corner);
					// 1.9.99.186: bail wait early if HP drops mid-walk. The
					// 8000ms blocking wait would otherwise prevent main-loop
					// safety nets (emergency-eat, panic-tele) from firing
					// while Corp's hits land — user died silently on the
					// walk-around.
					final int hpAtWalkStart = MyPlayer.getCurrentHealth();
					Waiting.waitUntil(8000, () -> {
						WorldTile cur = MyPlayer.getTile();
						if (cur == null) return false;
						int curHp = MyPlayer.getCurrentHealth();
						if (curHp > 0 && curHp <= Math.max(35, hpAtWalkStart - 25)) {
							Log.warn("L-shape walk: HP " + hpAtWalkStart + " -> "
									+ curHp + " — bailing to main loop");
							return true;
						}
						if (corpAreaClaim.contains(cur)) return false;
						if (cur.distanceTo(corner) <= 1) return true;
						return !lineCrossesCorp(cur, claimed, corpAreaClaim);
					});
				} else {
					// 1.9.99.233: don't fall to attackCorpIfVisible — its
					// game pathfinder routes through Corp's hitbox and
					// the bot ends up visually on the (cx±1, cy±1)
					// inner-corner tiles while walking through. Skip
					// this tick; next iteration re-evaluates with fresh
					// Corp position. User: "bot will somehow end up
					// inside the corps corner tiles ... happens
					// constantly".
					Log.warn("Claimed tile " + claimed + " requires crossing Corp "
							+ "AND no safe corner — skipping engage this tick");
					return false;
				}
			}
			if (LocalWalking.walkTo(claimed)) {
				return Waiting.waitUntil(5000, () -> {
					WorldTile t = MyPlayer.getTile(); // 1.9.99.180: NPE guard
					return t != null && t.distanceTo(claimed) <= 2;
				});
			}
		}

		WorldTile myPos = MyPlayer.getTile();

		// 1.9.49: if we're already standing outside Corp's hitbox but
		// close enough to attack (within 5 tiles of center, outside
		// the 5x5 hitbox = 1-3 tiles from the edge), just stay put.
		// User: 'We still run under the corp instead of just accepting
		// a frontal position.' The cardinal-position walk was forcing
		// the bot to flank to a canonical (N/S/E/W of center) tile
		// even when a perfectly attackable tile was already under our
		// feet. The frontal stand-still is far safer — no risk of
		// pathing through Corp's hitbox, no stomp damage, melee range
		// is identical anywhere around the edge.
		if (!forceMove && myPos != null && isPlayerAlreadyInCorpMeleeRange(myPos, corp)) {
			Log.info("Already in Corp melee range at " + myPos
					+ " — staying put (no need to flank)");
			return true;
		}

		List<WorldTile> dynamicPositions = getDynamicCorpPositions(corp);

		// 🔥 FILTER POSITIONS USING THE SAFETY CHECK
		List<WorldTile> safePositions = dynamicPositions.stream()
				.filter(pos -> isPositionSafeFromCorpHitbox(pos, corp))
				.collect(Collectors.toList());

		if (safePositions.isEmpty()) {
			Log.warn("No safe calculated positions found - using emergency position");
			WorldTile emergencyPos = getEmergencyCorpPosition(corp);
			if (emergencyPos != null) {
				safePositions = Arrays.asList(emergencyPos);
			} else {
				Log.error("No emergency positions available either!");
				return false;
			}
		}

		// Use improved position assignment on SAFE positions only
		WorldTile bestPosition = assignUniqueCorpPosition(safePositions);

		// 1.9.99.203: when this is an encroachment relocate, IMMEDIATELY claim
		// the picked tile and force a coordinator publish before the walk
		// starts. Pre-1.9.99.203 the encroachment path cleared the bot's
		// existing claim (line 10250) but never set a new one — so the OTHER
		// bot's encroachment check fired in the same window with no claim to
		// avoid, scored every candidate symmetrically (modulo small quadrant
		// + jitter bonuses), and picked the SAME tile. Both bots walked to
		// the same destination together — the "running in sync" pattern.
		// Now: the moment we pick a tile, publish the claim so the other
		// bot's NEXT check (1s later, after throttle releases) sees the new
		// claim and applies the -20 teammate-claim-penalty in scoring.
		if (forceMove && bestPosition != null && corp != null && mySnapshot != null) {
			try {
				WorldTile cTile = corp.getTile();
				if (cTile != null) {
					int dx = bestPosition.getX() - cTile.getX();
					int dy = bestPosition.getY() - cTile.getY();
					mySnapshot.claimedCorpOffset = new int[]{ dx, dy };
					coordinatorPublishNow();
				}
			} catch (Throwable ignored) {}
		}

		// 1.9.67: assignUniqueCorpPosition now returns null when all
		// cardinals cross Corp (synthesize path removed in 1.9.67 because
		// the synthesized tile could be inside Corp's hitbox by walk
		// arrival when Corp roamed). Delegate to game pathfinder via
		// click-attack.
		if (bestPosition == null) {
			Log.info("No safe walkable Corp position — corp.interact('Attack') "
					+ "(let game pathfinder handle approach)");
			if (attackCorpIfVisible(corp)) {
				return Waiting.waitUntil(6000, () ->
						isPlayerInCombat() || MyPlayer.isAnimating());
			}
			return false;
		}

		if (bestPosition != null) {
			Area corpArea = corp.getArea();
			// 1.9.90: corp.getArea() can return null transiently; the L-shape
			// block and lineCrossesCorp() below NPE on null area. Skip just the
			// hitbox-avoidance block; the live re-check + walk further down
			// gracefully handles null.
			WorldTile corpCenter = (corpArea != null) ? corpArea.getCenter() : null;
			// 1.9.54: explicit L-shape walk around Corp's hitbox using
			// CORNER waypoints, not 5-tile-out cardinals. Walk to a
			// corner of Corp's expanded bounding box first, verify we
			// got there safely (not inside Corp's hitbox AND straight
			// line to bestPosition no longer crosses), THEN walk to
			// bestPosition. User showed diagrams: bot should L-shape
			// around the hitbox, not cut through. Pre-1.9.54 the
			// waypoint code timed out at 4s and proceeded straight to
			// bestPosition even if the waypoint walk hadn't actually
			// gotten the bot past Corp.
			if (myPos != null && corpCenter != null && corpArea != null
					&& lineCrossesCorp(myPos, bestPosition, corpArea)) {
				WorldTile corner = pickCornerWaypoint(myPos, bestPosition,
						corpCenter, corpArea);
				if (corner != null) {
					Log.info("Path to " + bestPosition + " crosses Corp — "
							+ "L-shape via corner " + corner + " first");
					LocalWalking.walkTo(corner);
					// Wait until either we reached the corner OR the line
					// to bestPosition no longer crosses Corp from our
					// current tile (means we've effectively rounded the
					// hitbox). 8s timeout so a slow walk doesn't hand us
					// off to the straight-line fallback mid-stomp.
					Waiting.waitUntil(8000, () -> {
						WorldTile cur = MyPlayer.getTile();
						if (cur == null) return false;
						if (corpArea.contains(cur)) return false;
						if (cur.distanceTo(corner) <= 1) return true;
						return !lineCrossesCorp(cur, bestPosition, corpArea);
					});
				} else {
					// 1.9.99.233: same fix as the claimed-tile path above.
					// Game pathfinder routes through Corp; skip this
					// tick instead of stomp-walking.
					Log.warn("No safe corner waypoint found — skipping walk "
							+ "this tick (will re-evaluate next tick)");
					return false;
				}
			}
			// 1.9.61: LIVE re-check right before walking. Corp moves a
			// tile or two between snapshot and walk arrival; if it
			// shifted into our chosen tile, we'd walk straight into
			// the hitbox and stomp-die. User: 'we should never get
			// killed while running into the room.' Re-fetch corp and
			// abort if bestPosition is now inside its hitbox OR the
			// straight line still crosses.
			Optional<Npc> corpLive = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
			if (corpLive.isPresent()) {
				Area liveArea = corpLive.get().getArea();
				if (liveArea != null && liveArea.contains(bestPosition)) {
					// 1.9.99.233: same fix as the "no safe corner" paths.
					// Corp landed on our target. Game pathfinder would
					// route the bot under Corp to "reach" the target.
					// Skip this tick — next iteration picks a fresh
					// target tile not under Corp.
					Log.warn("Corp moved INTO target tile " + bestPosition
							+ " — skipping walk this tick (will re-pick next tick)");
					return false;
				}
				if (liveArea != null && myPos != null
						&& lineCrossesCorp(myPos, bestPosition, liveArea)) {
					// 1.9.99.232: pre-1.9.99.232 the fallback here was
					// attackCorpIfVisible — i.e. corp.interact("Attack")
					// — which delegates to the GAME pathfinder. The game
					// pathfinder doesn't know Corp's 5x5 hitbox is
					// stomp-dangerous, so it happily routes the bot
					// UNDER Corp to reach the nearest melee tile of
					// whichever NPC was clicked. User log 03:26:41 →
					// 03:26:45: "Corp shifted ... click-Attack instead"
					// → bot ran across the entire hitbox → "Player on
					// Corp's hitbox — emergency step away" (one stomp
					// landed before stepOffCorp could fire).
					// Now: try pickCornerWaypoint first with the LIVE
					// corp position; if a safe corner exists, walk to
					// it and let the next main-loop tick re-evaluate.
					// If no safe corner, skip this tick entirely — the
					// bot stays put for 1-2 ticks until Corp drifts to
					// a better position. NEVER fall through to
					// attackCorpIfVisible, which is the stomp path.
					WorldTile center = liveArea.getCenter();
					WorldTile corner = (center != null)
							? pickCornerWaypoint(myPos, bestPosition, center, liveArea)
							: null;
					if (corner != null) {
						Log.warn("Corp shifted — straight line to "
								+ bestPosition + " crosses hitbox, "
								+ "re-routing via corner " + corner);
						LocalWalking.walkTo(corner);
						Waiting.waitUntil(1500, () -> {
							WorldTile c = MyPlayer.getTile();
							return c != null && c.distanceTo(corner) <= 1;
						});
						return true;
					}
					Log.warn("Corp shifted — straight line to " + bestPosition
							+ " crosses hitbox AND no safe corner — skipping "
							+ "walk this tick (will re-evaluate next tick)");
					return false;
				}
			}
			// 1.9.99.76 / 1.9.99.142: short-range early-out. If Corp is
			// visible AND we're ALREADY essentially at bestPosition
			// (within ~1 tile), just click Attack to engage — no walk
			// needed. Pre-1.9.99.142 this fired any time we were within
			// 12 tiles of bestPosition, which broke encroachment relocate:
			// the helper picked a cardinal 4-6 tiles away (we're at
			// cardinal-W, picker chose cardinal-E), early-out clicked
			// Corp instead of walking, game pathfinder routed us to the
			// nearest attack tile = our current spot, no actual move.
			// User: "it hits relocating but doesnt attempt to move at
			// all." Now we only early-out when we're already at the
			// destination; otherwise we walk explicitly.
			final int EARLY_OUT_DIST = 1; // already-here threshold (Chebyshev-ish via Euclidean)
			if (!forceMove && myPos != null && myPos.distanceTo(bestPosition) <= EARLY_OUT_DIST) {
				try {
					if (corp.isVisible()) {
						Log.info("Already at bestPosition (dist="
								+ String.format("%.1f", myPos.distanceTo(bestPosition))
								+ ") — click-Attack to engage Corp");
						if (attackCorpIfVisible(corp)) {
							return Waiting.waitUntil(6000, () ->
									isPlayerInCombat() || MyPlayer.isAnimating());
						}
					}
				} catch (Throwable ignored) {}
			}

			// 1.9.99.56: chunked walk replaces the single big
			// LocalWalking.walkTo(bestPosition). Short clicks (5 tiles
			// each) leave the SDK pathfinder no room to route through
			// Corp's hitbox creatively. Each chunk's destination is
			// clamped so it never lands inside the 5x5 hitbox. Between
			// chunks we re-check (Corp may have moved) and bail to
			// stepOffCorp if we ended up under. User: "use minimap
			// walking to move towards the corp but make the location
			// we walk there CLOSER To use ... making a simpleline path
			// to get where we need to be like run straight 10 tiles,
			// turn left 10 tiles after that".
			Log.info("Moving to safe Corp position: " + bestPosition + " (chunked walk)");
			walkInChunksTo(bestPosition, corp);
			// Finish with click-Attack so we end up on a melee-adjacent
			// tile the game pathfinder picks (NOT crossing Corp).
			if (attackCorpIfVisible(corp)) {
				return Waiting.waitUntil(6000, () ->
						isPlayerInCombat() || MyPlayer.isAnimating());
			}
			return true;
		}
		return false;
	}

	/** 1.9.21: would a straight-line walk from `from` to `to` pass through
	 *  the corpArea? Samples points along the line and checks each against
	 *  Corp's 5x5 hitbox. This is a conservative check — RuneScape's
	 *  actual A* pathfinder may route around, but we want to FORCE the
	 *  bot to never cross Corp regardless of A*'s choices.
	 *  1.9.99.64: replaced corpArea.contains() with explicit ±2 bounds
	 *  from corpArea.getCenter(). User overlay log showed (2989, 4385)
	 *  marked "ok" when player was at (2979, 4385) — the straight line
	 *  clearly passes through Corp's hitbox at x=2984..2988 along
	 *  y=4385, so the line should have crossed. The SDK's
	 *  Area.contains() for Corp must be returning false for tiles the
	 *  visible 5x5 hitbox covers (likely the bounds are inclusive of
	 *  some corners but not others, or are returning a tighter area
	 *  than expected). We know the hitbox is exactly 5x5 around the
	 *  center, so check ±2 explicitly. User: "you already know that
	 *  corps tile returns ... southern most tile and you knows its
	 *  exact hitbox size so this si a weird issue". */
	private boolean lineCrossesCorp(WorldTile from, WorldTile to, Area corpArea) {
		if (corpArea == null) return false;
		WorldTile center = corpArea.getCenter();
		if (center == null) return false;
		final int minX = center.getX() - 2;
		final int maxX = center.getX() + 2;
		final int minY = center.getY() - 2;
		final int maxY = center.getY() + 2;
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps == 0) return false;
		for (int i = 1; i < steps; i++) {
			int x = from.getX() + dx * i / steps;
			int y = from.getY() + dy * i / steps;
			if (x >= minX && x <= maxX && y >= minY && y <= maxY) return true;
		}
		return false;
	}

	/** 1.9.54: pick an L-shape corner waypoint at one of the 4 corners of
	 *  Corp's expanded 7x7 bounding box (1 tile outside the hitbox in each
	 *  diagonal direction). Tries each corner and returns the first one
	 *  where BOTH legs (player->corner AND corner->target) don't cross
	 *  Corp's hitbox. Picks the corner with shortest total path distance.
	 *  Returns null if no corner satisfies both checks — caller falls
	 *  back to click-attack on Corp (game pathfinder).
	 *
	 *  Why corners instead of the 1.9.31 cardinal-5-out waypoints: a
	 *  corner enforces a true 90-degree turn, so each leg is mostly a
	 *  single-axis move that the SDK pathfinder is much less likely to
	 *  re-route through Corp's hitbox. The cardinal-5-out points are
	 *  in line with Corp's center on one axis, which often produces
	 *  long diagonal legs the pathfinder shortcuts. */
	private WorldTile pickCornerWaypoint(WorldTile player, WorldTile target,
	                                     WorldTile corpCenter, Area corpArea) {
		if (player == null || target == null || corpCenter == null) return null;
		int cx = corpCenter.getX(), cy = corpCenter.getY();
		int z = corpCenter.getPlane();
		// Corners of the 7x7 expanded bounding box (Corp hitbox is 5x5 centered
		// on cx,cy; +3 puts the corner 1 tile diagonally outside).
		WorldTile[] corners = new WorldTile[] {
				new WorldTile(cx - 3, cy + 3, z), // NW
				new WorldTile(cx + 3, cy + 3, z), // NE
				new WorldTile(cx - 3, cy - 3, z), // SW
				new WorldTile(cx + 3, cy - 3, z)  // SE
		};
		WorldTile best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorldTile corner : corners) {
			if (corpArea.contains(corner)) continue;
			if (lineCrossesCorp(player, corner, corpArea)) continue;
			if (lineCrossesCorp(corner, target, corpArea)) continue;
			double d = player.distanceTo(corner) + corner.distanceTo(target);
			if (d < bestDist) {
				bestDist = d;
				best = corner;
			}
		}
		return best;
	}

	/** 1.9.99.229: "force-tile then interact" pattern for the dark core.
	 *  OSRS BFS enumerates tiles in cardinal order W > E > S > N (verified
	 *  via OSRS Wiki Pathfinding article + Runemoro's shortest-path
	 *  RuneLite plugin). For a 1x1 NPC like the dark core, the 4 melee
	 *  tiles are its N/E/S/W orthogonal neighbors; ties on BFS distance
	 *  break in the W>E>S>N order, so if the core is touching Corp's
	 *  hitbox the engine routinely picks the W tile which lies INSIDE
	 *  Corp's 5x5 — bot walks under Corp, eats a 60+ stomp.
	 *  This function picks a safe melee tile (filters out under-Corp
	 *  candidates), walks there manually, then returns true so the
	 *  caller's core.interact("Attack") fires from a position where the
	 *  bot is already on a melee tile — no BFS pick, no surprise.
	 *  Returns false if all 4 melee tiles are under Corp (caller falls
	 *  through to the original click and risks the stomp). User insight:
	 *  "if we click to attack the core; 100% of the time it will walk
	 *  us into the corps hitbox". */
	private boolean walkToSafeCoreMeleeTile(Npc core, Npc corp) {
		if (core == null || corp == null) return false;
		WorldTile corePos;
		Area corpArea;
		WorldTile myPos;
		try {
			corePos = core.getTile();
			corpArea = corp.getArea();
			myPos = MyPlayer.getTile();
		} catch (Throwable t) { return false; }
		if (corePos == null || corpArea == null || myPos == null) return false;
		int cx = corePos.getX(), cy = corePos.getY(), cz = corePos.getPlane();
		WorldTile[] meleeTiles = new WorldTile[] {
				new WorldTile(cx - 1, cy, cz), // W
				new WorldTile(cx + 1, cy, cz), // E
				new WorldTile(cx, cy - 1, cz), // S
				new WorldTile(cx, cy + 1, cz)  // N
		};
		// 1.9.99.230: bias safe-tile pick toward maximum separation from any
		// other player (bot teammate, human teammate, random griefer — all
		// equally bad to stack with). Score = nearestPlayerDistance -
		// 0.1*botTravelDistance. The 10:1 weight makes player separation
		// the primary objective; bot travel is a tiebreaker. Without this
		// bias the bot would walk to the closest safe melee tile of the
		// core, which is often the tile a teammate is already attacking
		// from. User: "adding bias so that we pick a walking direction
		// thats keeps us farther away from our teammates positions is
		// ideal."
		List<WorldTile> otherPlayerPositions = new ArrayList<>();
		try {
			String myName = MyPlayer.getUsername();
			Query.players()
					.stream()
					.filter(p -> p.getName() != null
							&& (myName == null || !p.getName().equalsIgnoreCase(myName)))
					.forEach(p -> {
						try { otherPlayerPositions.add(p.getTile()); }
						catch (Throwable ignored) {}
					});
		} catch (Throwable ignored) {}
		WorldTile bestTile = null;
		double bestScore = -Double.MAX_VALUE;
		for (WorldTile t : meleeTiles) {
			if (corpArea.contains(t)) continue; // skip under-Corp tiles
			double minPlayerDist = Double.MAX_VALUE;
			for (WorldTile pp : otherPlayerPositions) {
				if (pp == null) continue;
				double d = t.distanceTo(pp);
				if (d < minPlayerDist) minPlayerDist = d;
			}
			// Cap to a finite value when nobody else is around so the
			// 0.1*botDist tiebreaker still applies cleanly.
			if (minPlayerDist == Double.MAX_VALUE) minPlayerDist = 10;
			double botDist = myPos.distanceTo(t);
			double score = minPlayerDist - 0.1 * botDist;
			if (score > bestScore) {
				bestScore = score;
				bestTile = t;
			}
		}
		if (bestTile == null) {
			Log.warn("walkToSafeCoreMeleeTile: all 4 melee tiles of core "
					+ corePos + " lie under Corp — skipping attack this tick "
					+ "(core/Corp may move next tick)");
			return false;
		}
		// 1.9.99.231: early exit if we're ALREADY on any safe melee tile of
		// the core — no need to walk just because some other safe tile
		// scored higher (e.g. for teammate separation). Attacking from a
		// safe tile we're already standing on is always preferable to
		// burning ticks moving.
		if (isSafeMeleeTileOf(myPos, corePos, corpArea)) {
			return true;
		}
		// Detour around Corp's hitbox if the path to bestTile would cross it.
		preWalkAroundCorp(bestTile, corp);
		Log.info("walkToSafeCoreMeleeTile: forcing attack tile " + bestTile
				+ " (was at " + myPos + ", core at " + corePos
				+ ", avoids BFS picking under-Corp tile)");
		if (!LocalWalking.walkTo(bestTile)) return false;
		final WorldTile safeTile = bestTile;
		final int hpStart = MyPlayer.getCurrentHealth();
		Waiting.waitUntil(1500, () -> {
			WorldTile cur = MyPlayer.getTile();
			if (cur == null) return false;
			int curHp = MyPlayer.getCurrentHealth();
			if (curHp > 0 && (curHp <= INTERNAL_PANIC_TELE_HP || curHp <= hpStart - 15)) {
				Log.warn("walkToSafeCoreMeleeTile: HP " + hpStart + " -> " + curHp
						+ " — bailing to main loop");
				return true;
			}
			return cur.distanceTo(safeTile) <= 0.5;
		});
		// 1.9.99.231: VERIFY we actually arrived on a safe melee tile before
		// returning true. Pre-1.9.99.231 the function returned true
		// unconditionally — if walkTo couldn't complete (wall blocked path,
		// other player on the destination, Corp shifted into the path), the
		// caller's core.interact("Attack") still fired from the wrong tile
		// and BFS picked the under-Corp tile → stomp. Now: if we didn't
		// arrive, return false so the caller skips the attack click this
		// tick and re-evaluates next tick. User: "What if we are up against
		// the wall ... we would still try to kill it ... which would cuz
		// is to get stomped and die?"
		WorldTile finalPos = MyPlayer.getTile();
		if (finalPos == null || !isSafeMeleeTileOf(finalPos, corePos, corpArea)) {
			Log.warn("walkToSafeCoreMeleeTile: didn't reach a safe melee tile (final="
					+ finalPos + ", target=" + safeTile + ") — caller should skip "
					+ "attack this tick");
			return false;
		}
		return true;
	}

	/** 1.9.99.231: is `pos` an orthogonally-adjacent (= melee-range) tile to
	 *  the core, AND outside Corp's hitbox? Used both for early-exit when
	 *  the bot is already in a safe spot and for verifying we actually
	 *  reached a safe tile after walking. */
	private boolean isSafeMeleeTileOf(WorldTile pos, WorldTile corePos, Area corpArea) {
		if (pos == null || corePos == null) return false;
		int dx = Math.abs(pos.getX() - corePos.getX());
		int dy = Math.abs(pos.getY() - corePos.getY());
		if (dx + dy != 1) return false; // not orthogonally adjacent
		if (corpArea != null && corpArea.contains(pos)) return false;
		return true;
	}

	/** 1.9.99.227: pre-walk via an L-shape corner if a straight line from us
	 *  to `target` would cross Corp's hitbox. Used to prevent the game
	 *  pathfinder from routing under Corp (stomp damage = 60+) when issuing
	 *  click-to-target actions like core.interact("Attack") or
	 *  LocalWalking.walkTo(faraway-tile). Returns true if we routed via a
	 *  corner, false if no detour was needed or no safe corner existed.
	 *  Callers should still issue their original action after this returns —
	 *  the helper just clears the path. */
	private boolean preWalkAroundCorp(WorldTile target, Npc corp) {
		if (target == null || corp == null) return false;
		Area corpArea;
		WorldTile myPos;
		try {
			corpArea = corp.getArea();
			myPos = MyPlayer.getTile();
		} catch (Throwable t) { return false; }
		if (corpArea == null || myPos == null) return false;
		if (!lineCrossesCorp(myPos, target, corpArea)) return false;
		WorldTile center = corpArea.getCenter();
		if (center == null) return false;
		WorldTile corner = pickCornerWaypoint(myPos, target, center, corpArea);
		if (corner == null) return false;
		Log.info("preWalkAroundCorp: straight line to " + target
				+ " crosses Corp — L-shape via corner " + corner);
		if (!LocalWalking.walkTo(corner)) return false;
		// 1.9.99.228: tightened wait — max 1500ms (was 3000) and bail on
		// any 15-HP drop (was 25). Reason: this wait blocks the main loop,
		// so while we're sleeping no emergency-eat / panic-tele can fire.
		// Worst case at the old 3s + 25-HP threshold: bot eats two full
		// Corp swings (~30 each) during the L-walk before main loop
		// regains control. New numbers: at most one swing's worth of
		// damage, and the wait exits the moment the line-to-target is
		// clear (usually 1-2 tiles into the walk). User: "make sure that
		// 3s wait isnt a sleep that will just sleep in the middle of the
		// fight and get us killed".
		final int hpStart = MyPlayer.getCurrentHealth();
		Waiting.waitUntil(1500, () -> {
			WorldTile cur = MyPlayer.getTile();
			if (cur == null) return false;
			int curHp = MyPlayer.getCurrentHealth();
			if (curHp > 0 && (curHp <= INTERNAL_PANIC_TELE_HP || curHp <= hpStart - 15)) {
				Log.warn("preWalkAroundCorp: HP " + hpStart + " -> " + curHp
						+ " — bailing to main loop (eat/panic-tele can fire)");
				return true;
			}
			if (cur.distanceTo(corner) <= 1) return true;
			Area a;
			try { a = corp.getArea(); } catch (Throwable t) { return false; }
			return a != null && !lineCrossesCorp(cur, target, a);
		});
		return true;
	}

	/** 1.9.31: pick a waypoint that goes AROUND Corp from player to target.
	 *  The waypoint sits 5 tiles out from Corp's center on the cardinal
	 *  side that's adjacent to BOTH the player's quadrant AND the target's
	 *  quadrant. This forces the RuneScape pathfinder to route around
	 *  Corp's hitbox in one move instead of cutting through it.
	 *  Pre-1.9.31 the waypoint was on the player's side only — which still
	 *  required crossing Corp to reach the target on the opposite side.
	 *  Tries multiple candidates; returns the first that's outside Corp's
	 *  hitbox and shorter than a straight-line crossing. */
	private WorldTile pickWaypointAroundCorp(WorldTile myPos, WorldTile target, Npc corp) {
		if (myPos == null || target == null || corp == null) return null;
		Area corpArea = corp.getArea();
		WorldTile corpCenter = corpArea.getCenter();
		int cx = corpCenter.getX(), cy = corpCenter.getY();
		int z = corpCenter.getPlane();

		// Four candidate waypoints — 5 tiles out from Corp center on each
		// cardinal side. Score = total path distance via this waypoint
		// (myPos → waypoint → target). Lower is better, and waypoints
		// that don't put us through Corp on EITHER leg are preferred.
		WorldTile[] candidates = new WorldTile[] {
				new WorldTile(cx - 5, cy, z),  // west
				new WorldTile(cx + 5, cy, z),  // east
				new WorldTile(cx, cy - 5, z),  // south
				new WorldTile(cx, cy + 5, z)   // north
		};
		WorldTile best = null;
		double bestDist = Double.MAX_VALUE;
		for (WorldTile c : candidates) {
			if (corpArea.contains(c)) continue;
			// Neither leg should pass through Corp.
			if (lineCrossesCorp(myPos, c, corpArea)) continue;
			if (lineCrossesCorp(c, target, corpArea)) continue;
			double d = myPos.distanceTo(c) + c.distanceTo(target);
			if (d < bestDist) {
				bestDist = d;
				best = c;
			}
		}
		return best; // null if no Corp-free waypoint found; caller falls back.
	}

    /** Defender tier priority — highest tier first. The iteration picks the
     *  best defender the user is actually carrying so we don't accidentally
     *  wield e.g. a Bronze defender when an Avernic is in the same inventory. */
    private static final String[] DEFENDER_PRIORITY = {
            "Avernic defender",
            "Dragon defender",
            "Rune defender",
            "Adamant defender",
            "Mithril defender",
            "Black defender",
            "Steel defender",
            "Iron defender",
            "Bronze defender",
            // 1.9.99.149: Antler guard is a Varlamore offhand that fills
            // the shield slot and counts as a defender for our purposes
            // (no spec needed, no DPS hit). User has one; without this
            // the bank-loop kept failing the "no defender present" check.
            "Antler guard"
    };

    /** 1.9.99.149: True if `itemName` is any accepted defender / defender-class
     *  offhand. Centralises the substring rule ("defender") plus explicit
     *  Antler guard handling, since "Antler guard" doesn't contain "defender". */
    private boolean isDefenderName(String itemName) {
        if (itemName == null) return false;
        String lc = itemName.toLowerCase();
        return lc.contains("defender") || lc.contains("antler guard");
    }

    private boolean equipAnyDefender() {
        // 1.9.90: verify the wield actually landed by re-checking equipment, not just click().
        for (String name : DEFENDER_PRIORITY) {
            Optional<InventoryItem> def = Query.inventory().nameEquals(name).findFirst();
            if (def.isPresent()) {
                Log.info("Equipping defender (priority): " + name);
                boolean clicked = def.get().click("Wield");
                if (!clicked) return false;
                return Waiting.waitUntil(2000, this::hasDefenderEquipped);
            }
        }
        // Last resort: any item whose name contains "defender" (catches custom
        // server variants we haven't listed in DEFENDER_PRIORITY).
        // 1.9.99.149: also accept Antler guard (Varlamore shield-slot offhand).
        Optional<InventoryItem> fallback = Query.inventory()
                .filter(it -> isDefenderName(it.getName())).findFirst();
        if (fallback.isPresent()) {
            Log.info("Equipping defender (fallback): " + fallback.get().getName());
            boolean clicked = fallback.get().click("Wield");
            if (!clicked) return false;
            return Waiting.waitUntil(2000, this::hasDefenderEquipped);
        }
        Log.debug("No defender found in inventory");
        return false;
    }

    // New method to check if any defender is equipped
    private boolean hasDefenderEquipped() {
        // 1.9.99.149: accept Antler guard too.
        return Query.equipment()
                .filter(it -> isDefenderName(it.getName()))
                .findFirst()
                .isPresent();
    }

    /**
     * Move to position around Corp spawn when Corp is not present
     */
    private boolean moveToCorpSpawnPosition() {
        WorldTile myPos = MyPlayer.getTile();

        // Get acceptable teammate positions to avoid crowding
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // Try positions around spawn location using same offsets
        for (int[] offset : CORP_POSITION_OFFSETS) {
            WorldTile position = new WorldTile(
                    CORP_SPAWN_LOCATION.getX() + offset[0],
                    CORP_SPAWN_LOCATION.getY() + offset[1],
                    CORP_SPAWN_LOCATION.getPlane()
            );

            // Check if position is not occupied by teammates
            boolean occupied = teammatePositions.stream()
                    .anyMatch(teammatePos -> teammatePos.distanceTo(position) <= 2);

            if (!occupied) {
                if (LocalWalking.walkTo(position)) {
                    Log.info("Moving to Corp spawn position: " + position);
                    return Waiting.waitUntil(5000, () -> {
                        WorldTile t = MyPlayer.getTile(); // 1.9.99.180: NPE guard
                        return t != null && t.distanceTo(position) <= 2;
                    });
                }
            }
        }

        // If all positions occupied, go to first one anyway
        int[] firstOffset = CORP_POSITION_OFFSETS.get(0);
        WorldTile fallbackPosition = new WorldTile(
                CORP_SPAWN_LOCATION.getX() + firstOffset[0],
                CORP_SPAWN_LOCATION.getY() + firstOffset[1],
                CORP_SPAWN_LOCATION.getPlane()
        );

        if (LocalWalking.walkTo(fallbackPosition)) {
            return Waiting.waitUntil(5000, () -> {
                WorldTile t = MyPlayer.getTile(); // 1.9.99.180: NPE guard
                return t != null && t.distanceTo(fallbackPosition) <= 2;
            });
        }

        return false;
    }

    /**
     * Get positions currently occupied by acceptable teammates from given position list
     */
    // Modify the positioning check to be more strict
    private List<WorldTile> getPositionsOccupiedByAcceptableTeammates(List<WorldTile> positions) {
        List<WorldTile> teammatePositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        return positions.stream()
                .filter(corpPos -> teammatePositions.stream()
                        .anyMatch(teammatePos -> teammatePos.distanceTo(corpPos) <= 1)) // Changed from 2 to 1
                .collect(Collectors.toList());
    }

    /**
     * Check if a Corp position is occupied by an acceptable teammate
     */
    private boolean isPositionOccupiedByAcceptableTeammate(WorldTile position, List<WorldTile> occupiedPositions) {
        return occupiedPositions.contains(position);
    }

    /**
     * Queue switching back from spec weapon to main weapons
     */
    private void queueSpecWeaponSwitchBack() {
        if (specWeaponSwitchQueued) {
            return;
        }

        long randomDelay = getSkewedRandom(400, 1800); // 0.4s to 1.8s, skewed to lower end
        specWeaponSwitchTime = System.currentTimeMillis() + randomDelay;
        specWeaponSwitchQueued = true;
        needsToSwitchBackFromSpec = true;
        specWeaponSwitchQueuedAt = System.currentTimeMillis(); // 1.9.99.52
        Log.info("Spec weapon switch back queued for " + randomDelay + "ms from now");
    }

    /**
     * Handle queued spec weapon switching
     */
    private void handleSpecWeaponSwitchTiming() {
        if (!specWeaponSwitchQueued || !needsToSwitchBackFromSpec) {
            return;
        }

        // 1.9.41: only honor the queued swap-back while in active combat
        // states. Pre-1.9.41 the timer fired on every main loop tick
        // regardless of state — so a swap queued during a spec dump
        // would execute AFTER PREPARING_RESTORATION_CYCLE / TELEPORTING_TO_HOUSE
        // / USING_ORNATE_POOL, leaving us at the POH with Fang equipped
        // and the spec weapon left in inventory. On return to Corp the
        // bot then had to re-equip the spec weapon. User: "it still
        // switched to the fang after teleporting out even though we are
        // still spec dumping."
        if (currentState != BotState.FIGHTING_CORP
                && currentState != BotState.USING_SPECIAL_ATTACK
                && currentState != BotState.ENTERING_COMBAT
                && currentState != BotState.HANDLING_DARK_CORE) {
            // Cancel the queue when we leave combat. The next combat
            // tick will re-evaluate via the standard equip logic.
            specWeaponSwitchQueued = false;
            needsToSwitchBackFromSpec = false;
            return;
        }

        // 1.9.4 / 1.9.26: HP guard. Pre-1.9.26 postponed the swap whenever
        // HP <= INTERNAL_COMBO_EAT_HP (50). That was correct for swapping
        // INTO a 2H spec weapon (slow animation lock) but counterproductive
        // for swapping BACK to Fang at kill phase — Corp keeps hitting,
        // HP never recovers, bot stays stuck on Arclight ("poking") and
        // dies. The swap-back to a 1H + defender is fast (~1 game tick)
        // and gets us higher DPS / accuracy, which actually helps survive.
        // Only postpone when HP is critical (emergency threshold = 15).
        int currentHp = MyPlayer.getCurrentHealth();
        if (currentHp <= INTERNAL_EMERGENCY_HP) {
            Log.debug("Postponing spec-weapon switch-back: HP " + currentHp +
                    " <= " + INTERNAL_EMERGENCY_HP + " (emergency, eat first)");
            return;
        }

        // Check if it's time to switch back to main weapons
        if (System.currentTimeMillis() >= specWeaponSwitchTime) {
            // Switch back to main weapon (main hand + offhand in quick succession)
            if (equipMainWeaponFast()) {
                Log.info("Switched back to main weapons after spec weapon delay");
                specWeaponSwitchQueued = false;
                needsToSwitchBackFromSpec = false;
            } else {
                Log.warn("Failed to switch back to main weapons, will retry");
                // Reset timing for retry (short delay)
                specWeaponSwitchTime = System.currentTimeMillis() + getSkewedRandom(200, 600);
            }
        }
    }

    // ========== SPEC WEAPON SWITCHING SYSTEM ==========

    /**
     * Equip main weapon quickly (main hand + offhand together) - FIXED VERSION
     */
    // Fix the defender equipping logic
	private boolean equipMainWeaponFast() {
		// Step 1: wield the main weapon if it isn't already equipped.
		if (!isMainWeaponEquipped()) {
			String availableMainWeapon = getAvailableMainWeapon();
			if (availableMainWeapon == null) {
				Log.warn("equipMainWeaponFast: no main weapon found in inventory or equipment");
				return false;
			}
			// 1.9.99.148: 2H main wield sends both the currently equipped
			// weapon AND any equipped defender / offhand back to inventory.
			// Make sure we have room before clicking Wield, otherwise the
			// game silently drops the wield and we end up still holding the
			// previous (slow / wrong) weapon.
			if (isMainWeaponTwoHanded()) {
				int slotsNeeded = hasDefenderEquipped() ? 2 : 1;
				if (!ensureInventorySlotsFree(slotsNeeded)) {
					Log.warn("equipMainWeaponFast: cannot free " + slotsNeeded
							+ " slot(s) for 2H main wield of " + availableMainWeapon);
					return false;
				}
			}
			Optional<InventoryItem> mainWeaponOpt = Query.inventory().nameEquals(availableMainWeapon).findFirst();
			if (!mainWeaponOpt.isPresent()) {
				// Already in equipment but isMainWeaponEquipped said false — name drift?
				Log.warn("equipMainWeaponFast: " + availableMainWeapon + " not in inventory either");
				return false;
			}
			Log.info("Equipping main weapon: " + availableMainWeapon);
			if (!mainWeaponOpt.get().click("Wield")) {
				Log.warn("equipMainWeaponFast: Wield click failed for " + availableMainWeapon);
				return false;
			}
			if (!Waiting.waitUntil(2000, () -> isMainWeaponEquipped())) {
				Log.warn("equipMainWeaponFast: " + availableMainWeapon + " did not equip in time");
				return false;
			}
		}

		// Step 2: equip a defender if one is missing. This is a best-effort
		// step — some setups don't carry a defender and that's OK, so we
		// don't fail the whole call when defender wield doesn't take.
		// 1.9.99.148: skip entirely when main is 2H (spear / hasta) — the
		// offhand slot is occupied by the 2H weapon and a defender wield
		// would either fail or kick the 2H back to inventory.
		if (!isMainWeaponTwoHanded() && !hasDefenderEquipped()) {
			equipAnyDefender();
			Waiting.waitUntil(2000, () -> hasDefenderEquipped());
		}

		return isMainWeaponEquipped();
	}

	private boolean isPositionSafeFromCorp(WorldTile position, Npc corp) {
		WorldTile corpPos = corp.getTile();
		double distance = position.distanceTo(corpPos);

		// Must be at least 5 tiles from Corp (not 3!)
		if (distance < 5) {
			Log.warn("Position too close to Corp: " + position + " (distance: " + distance + ")");
			return false;
		}

		// Must be within attack range but not melee range
		if (distance > MAX_ATTACK_DISTANCE_FROM_CORP) {
			Log.warn("Position too far from Corp: " + position + " (distance: " + distance + ")");
			return false;
		}

		return true;
	}

	// Updated for 5x5 Corp
	private static final int SAFE_DISTANCE_FROM_CORP_EDGE = 4;  // 4 tiles from hitbox edge
	// 1.9.99.31: was 3 — but CORP_POSITION_OFFSETS (L1523) deliberately
	// places the bot 1 tile from hitbox edge ("PERFECT for melee"). With
	// the threshold at 3, isPositionSafeFromCorpHitbox rejected every
	// generated cardinal, the bot bailed to "emergency positions" (which
	// then failed walkability), and silently attacked from wherever it
	// happened to stand. User log: all four cardinals at edge-distance
	// 1.0 logged "Position too close to Corp hitbox edge", "No safe
	// calculated positions found", "No emergency positions available
	// either!" — yet a few ticks later "Combat initiated successfully"
	// because positioning silently bailed. The threshold of 3 was for
	// mage/range gameplay; for melee Elder maul / Arclight we need to
	// be adjacent. Set to 1 (strictly outside the 5x5 hitbox) — Corp's
	// movement-on-arrival risk is handled by the snapshot re-check
	// elsewhere.
	private static final int MIN_DISTANCE_FROM_CORP_EDGE = 1;
	private static final int MAX_ATTACK_DISTANCE_FROM_CORP_CENTER = 12; // Can attack from 12 tiles from center

	private double getDistanceToCorpHitboxEdge(WorldTile playerPos, Npc corp) {
		// 1.9.99.26: use corp.getArea().getCenter(), NOT corp.getTile().
		// For multi-tile NPCs like Corp (5x5), corp.getTile() returns the
		// SW CORNER, not the center. Pre-1.9.99.26 the radius calc was
		// applied to the SW corner, shifting the perceived hitbox 2 tiles
		// SW of its actual position — half the cardinal positions were
		// then bogusly flagged as "too close to hitbox edge", filter
		// rejected them, and 1.9.67's "ALL N cardinals require crossing
		// Corp" branch fired even when there were obvious free tiles
		// adjacent to Corp. User: "i dont know why we need to route
		// around, most of the time with the current set up wtih just us
		// and 1 other player theres ALWAYS free spaces open in front of
		// corp but somehow we still are trying to find other positions".
		Area corpArea = corp.getArea();
		WorldTile corpCenter = (corpArea != null && corpArea.getCenter() != null)
				? corpArea.getCenter()
				: corp.getTile();

		// Corp is 5x5, so hitbox extends 2 tiles in each direction from center
		int corpHitboxRadius = 2;

		// Calculate closest point on Corp's hitbox to player
		int closestCorpX = Math.max(corpCenter.getX() - corpHitboxRadius,
				Math.min(playerPos.getX(), corpCenter.getX() + corpHitboxRadius));
		int closestCorpY = Math.max(corpCenter.getY() - corpHitboxRadius,
				Math.min(playerPos.getY(), corpCenter.getY() + corpHitboxRadius));

		WorldTile closestCorpPoint = new WorldTile(closestCorpX, closestCorpY, corpCenter.getPlane());

		double distanceToEdge = playerPos.distanceTo(closestCorpPoint);

		Log.debug("Distance to Corp hitbox edge: " + distanceToEdge +
				" (player: " + playerPos + ", corp center: " + corpCenter + ")");

		return distanceToEdge;
	}

	private boolean isPositionSafeFromCorpHitbox(WorldTile position, Npc corp) {
		double distanceToEdge = getDistanceToCorpHitboxEdge(position, corp);

		// Must be at least 3 tiles from Corp's hitbox edge
		if (distanceToEdge < MIN_DISTANCE_FROM_CORP_EDGE) {
			Log.warn("Position too close to Corp hitbox edge: " + position +
					" (distance to edge: " + distanceToEdge + ")");
			return false;
		}

		// Must be within attack range of Corp center
		double distanceToCenter = position.distanceTo(corp.getTile());
		if (distanceToCenter > MAX_ATTACK_DISTANCE_FROM_CORP_CENTER) {
			Log.warn("Position too far from Corp center: " + position +
					" (distance to center: " + distanceToCenter + ")");
			return false;
		}

		Log.debug("Position safe from Corp: " + position +
				" (edge distance: " + distanceToEdge +
				", center distance: " + distanceToCenter + ")");

		return true;
	}

	// If all calculated positions are unsafe, use emergency positions
	private WorldTile getEmergencyCorpPosition(Npc corp) {
		WorldTile corpPos = corp.getTile();

		// Emergency positions much further away
		List<WorldTile> emergencyPositions = Arrays.asList(
				new WorldTile(corpPos.getX() - 8, corpPos.getY(), corpPos.getPlane()),     // 8 tiles west
				new WorldTile(corpPos.getX() + 8, corpPos.getY(), corpPos.getPlane()),     // 8 tiles east
				new WorldTile(corpPos.getX(), corpPos.getY() - 8, corpPos.getPlane()),     // 8 tiles south
				new WorldTile(corpPos.getX(), corpPos.getY() + 8, corpPos.getPlane())      // 8 tiles north
		);

		// Return first walkable emergency position
		return emergencyPositions.stream()
				.filter(this::isTileWalkable)
				.findFirst()
				.orElse(null);
	}

    /**
     * Equip main weapon quickly (main hand + offhand together)
     */

    /**
     * Find emergency position that avoids both dark core and Corp's area
     */
    private WorldTile findEmergencyPosition(WorldTile myPos, WorldTile corePos, Area corpArea) {
        // Calculate direction away from both core and Corp center
        WorldTile corpCenter = corpArea.getCenter();

        // Get direction away from core
        int coreDirectionX = myPos.getX() > corePos.getX() ? 1 : -1;
        int coreDirectionY = myPos.getY() > corePos.getY() ? 1 : -1;

        // Get direction away from Corp center
        int corpDirectionX = myPos.getX() > corpCenter.getX() ? 1 : -1;
        int corpDirectionY = myPos.getY() > corpCenter.getY() ? 1 : -1;

        // Combine directions (prioritize getting away from core)
        int finalDirectionX = coreDirectionX;
        int finalDirectionY = coreDirectionY;

        // Try emergency position
        WorldTile emergencyTile = new WorldTile(
                myPos.getX() + (finalDirectionX * 4),
                myPos.getY() + (finalDirectionY * 4),
                myPos.getPlane()
        );

        // Make sure emergency position doesn't put us in Corp's area
        if (!corpArea.contains(emergencyTile)) {
            return emergencyTile;
        }

        // If that position is in Corp area, try other directions
        for (int multiplier : Arrays.asList(3, 5, 2)) {
            WorldTile altTile = new WorldTile(
                    myPos.getX() + (finalDirectionX * multiplier),
                    myPos.getY() + (finalDirectionY * multiplier),
                    myPos.getPlane()
            );
            if (!corpArea.contains(altTile)) {
                return altTile;
            }
        }

        return null; // Couldn't find safe emergency position
    }

    /**
     * Check if we're in a good Corp position (uses static positions when Corp not present)
     */
    private boolean isInGoodCorpPosition() {
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent()) {
            return isInGoodCorpPosition(corpOpt.get());
        } else {
            // Corp not present, check against static spawn positions
            WorldTile myPos = MyPlayer.getTile();
            return CORP_POSITIONS.stream()
                    .anyMatch(pos -> myPos.distanceTo(pos) <= 2);
        }
    }

    /**
     * Move to nearest Corp position (uses dynamic positioning if Corp present, static if not)
     */
    private boolean moveToNearestCorpPosition() {
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent()) {
            return moveToNearestCorpPosition(corpOpt.get());
        } else {
            return moveToCorpSpawnPosition();
        }
    }

    // ========== OVERLOADED POSITIONING METHODS ==========

	private void handleSpecialAttack() {
		Log.info("Using special attack with energy monitoring...");

		Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
		if (!corpOpt.isPresent()) {
			Log.warn("Corp not found for special attack");
			currentState = BotState.FIGHTING_CORP;
			return;
		}

		Npc corp = corpOpt.get();

		// 1.9.30: dropped the Corp-HP gate here too. The phase-target gate
		// (shouldSpecNowConsideringTeam, run below) is the right place to
		// decide whether to keep specing — when phase targets are done,
		// it skips and we fall through to Fang. The HP threshold was
		// gating phase-1 specs even when phase 1 wasn't complete, leaving
		// Corp's defense un-reduced and the melee finish much slower.

		// Phase D: rotate to the right spec weapon for the team's current phase.
		// 1.9.8: dropped the settings.coordinatorEnabled gate (mirrors the
		// 1.9.5 fix elsewhere). Phase rotation should work for solo bots too
		// — buildSoloAggregate provides the same phase counters as the
		// coordinator path.
		if (!refreshSpecWeaponForPhase()) {
			Log.info("No usable spec weapon for current team phase — falling through to DPS.");
			if (specWeaponReadyForUse) { queueSpecWeaponSwitchBack(); specWeaponReadyForUse = false; }
			currentState = BotState.FIGHTING_CORP;
			return;
		}

		// Phase C: consult team aggregate. If the team has already done enough specs
		// for our weapon's phase, skip and fall through to DPS.
		if (!shouldSpecNowConsideringTeam()) {
			Log.info("Team phase complete for " + chosenSpecWeapon + " — skipping spec.");
			if (specWeaponReadyForUse) {
				queueSpecWeaponSwitchBack();
				specWeaponReadyForUse = false;
			}
			currentState = BotState.FIGHTING_CORP;
			return;
		}

		if (!isSpecWeaponEquipped()) {
			if (!equipSpecWeapon()) {
				Log.error("Failed to equip spec weapon");
				currentState = BotState.FIGHTING_CORP;
				return;
			}
		}

		// 1.9.34: use the debounced activator. If we activated within the
		// debounce window, trust the prior click — don't re-toggle.
		if (!tryActivateSpec()) {
			Log.error("Failed to activate special attack as backup");
			currentState = BotState.FIGHTING_CORP;
			return;
		}

		// 🔥 ENERGY-BASED SPECIAL ATTACK EXECUTION
		int specsPerformed = 0;
		int maxSpecs = Combat.getSpecialAttackPercent() >= 100 ? 2 : 1;

		while (specsPerformed < maxSpecs && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()) {
			// 1.9.92: re-arm spec bar before EACH iteration. Pre-1.9.92 we
			// activated once before the loop; the bar auto-toggles OFF on
			// every spec swing, so iteration 2's corp.interact("Attack")
			// fired an auto-attack with no energy drain and the 5s wait
			// timed out. tryActivateSpec is idempotent post-1.9.90 (returns
			// true when bar already on, re-arms when off).
			// 1.9.99.3: also VERIFY the bar is actually ON before firing
			// corp.interact. The 1.9.94 settle window in tryActivateSpec
			// returns true within 250ms of a previous click without re-
			// verifying SDK state. If a weapon swap happened between the
			// previous click and now (rotation: Elder maul → Arclight +
			// defender), the bar got toggled OFF by the swap but the settle
			// window still says "bar is on, trust me". The next swing fires
			// as auto-attack — no energy drop, no spec, 5s timeout. User
			// log: Arclight rotated in from Elder maul, 3 consecutive
			// "Special attack timed out" warnings.
			if (!Combat.isSpecialAttackEnabled()) {
				// 1.9.99.9: capture energy + XP BEFORE re-arm so we can
				// detect a silent spec fire if the verify wait times out
				// but energy actually dropped (the click DID land but a
				// player auto-attack consumed the spec before the SDK
				// reflected bar-on).
				int preReArmEnergy = Combat.getSpecialAttackPercent();
				long preReArmXp = getMeleeCombatXp();
				double preReArmCorpHp = readCorpHpPct(); // 1.9.99.37
				if (!tryActivateSpec()) {
					Log.warn("Spec re-arm failed before iteration "
							+ (specsPerformed + 1) + " — breaking out of spec loop");
					break;
				}
				// 1.9.99.6: no more force-click on the first verify failure.
				// 1.9.99.3-5 sent a second click when the SDK reported bar
				// OFF after 400ms — but if the first click WAS in-flight,
				// the second click toggled the bar back OFF, then
				// corp.interact swung as auto-attack with no energy drop
				// and 5s timeout. Bot stood in Corp's hitbox for ~8s.
				// Now: wait one game tick (700ms) for the click to land.
				// If still OFF, break out and let the outer FIGHTING_CORP
				// loop retry — between attempts the bot can eat / dodge
				// core / continue swinging instead of double-clicking
				// itself into a stale-bar state.
				if (!Waiting.waitUntil(700, Combat::isSpecialAttackEnabled)) {
					// 1.9.99.9: before breaking, check if energy dropped
					// during the wait. If yes, a spec fired silently — the
					// click landed, bar briefly toggled ON, player swing
					// consumed it. Without this check, the silent fire goes
					// completely untracked (loop didn't set pendingHitWeapon
					// for it; in-line detector is blocked by specWeapon-
					// ReadyForUse). User: "specs are still not being tracked
					// properly even when we get xp drops during a spec drain".
					int postWaitEnergy = Combat.getSpecialAttackPercent();
					if (postWaitEnergy < preReArmEnergy) {
						specsPerformed++;
						processPendingSpecHit();
						// 1.9.99.39: enqueue (don't overwrite). Pre-spec baselines.
						// 1.9.99.45: snapshot hitsplat counter.
						advanceHitsplatCounter(Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst());
						pendingHits.add(new PendingSpecAttempt(
								chosenSpecWeapon,
								preReArmXp,
								preReArmCorpHp,
								System.currentTimeMillis() + HIT_CONFIRM_TIMEOUT_MS,
								monotonicHitsplatCounter));
						Log.info("Silent spec fire detected during re-arm "
								+ "verify (Energy: " + preReArmEnergy + "% → "
								+ postWaitEnergy + "%) — recorded as "
								+ chosenSpecWeapon + " for hit confirm");
					}
					Log.warn("Spec bar didn't toggle ON within 700ms after "
							+ "tryActivateSpec. Breaking out — outer loop "
							+ "will retry without a risky double-click.");
					break;
				}
			}
			int energyBefore = Combat.getSpecialAttackPercent();
			Log.info("Spec " + (specsPerformed + 1) + " - Energy before: " + energyBefore + "%");
			long preSpecXp = getMeleeCombatXp();

			if (attackCorpIfVisible(corp)) {
				// 1.9.99.12: custom wait loop that ALSO ticks
				// processPendingSpecHit. Pre-1.9.99.12 the 5s energy-drop
				// wait used Waiting.waitUntil which only polls the
				// condition. During that 5s, prior specs' XP/hitsplat
				// signals would arrive — but processPendingSpecHit
				// (only called in FIGHTING_CORP) couldn't run. By the
				// time the wait returned and handleSpecialAttack exited,
				// hitsplats had expired (~3.6s lifespan), and on
				// re-entry to FIGHTING_CORP the deadline check (1.9.99.11)
				// now strictly marks miss. Net: missed specs that
				// actually hit. User: "i hit the corp with an elder
				// maul spec 3 times and its only counted one".
				// 1.9.99.22: also track XP from the PREVIOUS poll
				// iteration so when energy drops, we use the pre-swing
				// XP as baseline (the swing's XP arrives WITH the energy
				// drop, so capturing AT-drop sees post-swing XP and the
				// delta is 0 for single-hit specs like Elder maul).
				// User: "some xp drops when spec drops doesnt count the
				// specs as successful".
				boolean specExecuted = false;
				long waitStart = System.currentTimeMillis();
				long xpBeforeDrop = getMeleeCombatXp();
				long lastPollXp = xpBeforeDrop;
				double hpBeforeDrop = readCorpHpPct();      // 1.9.99.37
				double lastPollCorpHp = hpBeforeDrop;       // 1.9.99.37
				while (System.currentTimeMillis() - waitStart < 5000) {
					processPendingSpecHit();
					int currentEnergy = Combat.getSpecialAttackPercent();
					if (currentEnergy < energyBefore) {
						Log.info("Special attack confirmed - Energy dropped from "
								+ energyBefore + "% to " + currentEnergy + "%");
						// 1.9.99.22: use the LAST POLL's XP as baseline (one
						// poll = ~50ms ago, before the swing's XP arrived).
						xpBeforeDrop = lastPollXp;
						hpBeforeDrop = lastPollCorpHp; // 1.9.99.37 (paired)
						specExecuted = true;
						break;
					}
					lastPollXp = getMeleeCombatXp();
					lastPollCorpHp = readCorpHpPct();  // 1.9.99.37
					Waiting.waitNormal(50, 20);
				}

				if (specExecuted) {
					specsPerformed++;
					// 1.9.99.11: CAPTURE the weapon that actually fired BEFORE
					// processPendingSpecHit runs. processPendingSpecHit may
					// confirm a prior hit and trigger refreshSpecWeaponForPhase
					// which rotates chosenSpecWeapon to the next phase's
					// weapon. If we then set pendingHitWeapon = chosenSpecWeapon
					// AFTER the rotation, we attribute this spec to the WRONG
					// weapon (rotated next-phase weapon, not the one actually
					// swinging — which is still equipped until prepare swaps
					// it on the next trip). User log: phase rotation Elder
					// maul → Arclight happened mid-bar, then spec 2's swing
					// (still Elder maul) got pending-stamped as Arclight,
					// confirmed later via stale XP delta, counted incorrectly.
					String firedWeapon = chosenSpecWeapon;
					// 1.9.99.7: PROCESS prior pending hit BEFORE overwriting its
					// baseline. Spec N's XP arrives at the END of its swing
					// (~3600ms for 6-tick weapons), which is exactly when spec
					// N+1's energy drop is detected (next swing starts at end
					// of prior animation). If we immediately overwrite
					// pendingHitXpBaseline with the post-spec-N XP, spec N's
					// confirmation is BURNED INTO the new baseline and lost.
					// User: "its hit 3 specs and has counted 0 of them".
					// Now we drain any pending hit using its existing baseline
					// before stamping the new one.
					processPendingSpecHit();
					// 1.9.99.5: defer hit confirmation to processPendingSpecHit.
					// Pre-1.9.99.5 we blocked for up to 5s waiting for XP delta
					// or hitsplat — meaning the bot stood idle inside Corp's
					// hitbox while Corp could land a 50+ damage hit. User:
					// "5s hit signal? so what if we dont hit we just stand
					// there afking a boss that does half our health in 1 hit
					// for 5000 ms???? thats unacceptable".
					// Now we set the pendingHitWeapon baseline and return to
					// FIGHTING_CORP, where the bot can eat / dodge core /
					// reposition / continue swinging while processPendingSpecHit
					// confirms or marks miss asynchronously within the same
					// 5s window — no blocking.
					// 1.9.99.39: enqueue (don't overwrite). Baselines come from
					// the pre-spec poll loop above — 1.9.99.22 (XP) / 1.9.99.37 (HP).
					// 1.9.99.45: snapshot hitsplat counter.
					advanceHitsplatCounter(Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst());
					pendingHits.add(new PendingSpecAttempt(
							firedWeapon, // 1.9.99.11: pre-rotation weapon
							xpBeforeDrop,
							hpBeforeDrop,
							System.currentTimeMillis() + HIT_CONFIRM_TIMEOUT_MS,
							monotonicHitsplatCounter));
					int energyAfter = Combat.getSpecialAttackPercent();
					Log.info("Special attack " + specsPerformed + "/" + maxSpecs
							+ " fired (Energy: " + energyBefore + "% → " + energyAfter
							+ "% " + firedWeapon + ") — deferring hit confirmation");

					// Brief pause before next spec if doing multiple. Animation
					// lock prevents the next swing from firing earlier anyway.
					if (specsPerformed < maxSpecs && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()) {
						Waiting.waitUntil(1000, () -> !MyPlayer.isAnimating());
						Log.info("Preparing for second special attack...");
					}
				} else {
					Log.warn("Special attack timed out - no energy drop detected");
					break;
				}
			} else {
				Log.warn("Failed to attack Corp with special");
				break;
			}
		}

		Log.info("Completed " + specsPerformed + " special attack(s), final energy: " + Combat.getSpecialAttackPercent() + "%");

		// 1.9.99.8: only clear specWeaponReadyForUse when there's no more spec
		// to fire on this bar. Pre-1.9.99.8 we cleared it unconditionally on
		// exit — which broke the L3287 in-line detector for "spec fired
		// silently after handleSpecialAttack broke out" cases (1.9.99.6's
		// 700ms verify break out left an in-flight click that toggled the
		// bar ON post-break, and the player's next auto-attack then fired
		// the spec → energy dropped, no script path observed it, hit went
		// uncounted). Keep the flag true if energy >= min so the in-line
		// detector in handleFightingCorp can pick up silently-fired specs
		// via processPendingSpecHit.
		if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) {
			specWeaponReadyForUse = false;
		}
		// 1.9.36: only swap back to Fang if we can't fire another spec right
		// now. Pre-1.9.36 we queued the switch unconditionally — so if spec 2
		// of a 2-spec bar timed out (Corp out of range, occluded, etc.) we
		// went to Fang with 50% energy still on the bar, and then a few ticks
		// later the pre-activate path re-equipped the spec weapon to fire
		// that remaining spec. Two wasted weapon swaps per stutter. Now:
		// keep the spec weapon equipped if energy >= minSpecEnergy and a
		// phase still needs specs. Kill-phase fall-through is handled by
		// handleFightingCorp at line ~3085 (isInKillPhase guard).
		int energyLeft = Combat.getSpecialAttackPercent();
		boolean canSpecAgain = energyLeft >= getMinSpecEnergy()
				&& teamPhaseNeeded() != 0
				&& !isInKillPhase();
		// 1.9.45: also keep the spec weapon if we're about to teleport to
		// POH for restoration. Pre-1.9.45 the queue fired during the next
		// FIGHTING_CORP tick BEFORE shouldStartRestorationCycle ran in
		// handleFightingCorp — Fang got equipped, then the bot teleported
		// to POH with Fang. After restoration the bot came back at 100%
		// energy needing to re-equip the spec weapon. Two wasted swaps
		// per restoration cycle. User: 'we equipped our defender and
		// fang which we shouldnt do.'
		boolean willRestore = shouldStartRestorationCycle();
		if (canSpecAgain || willRestore) {
			Log.info("Holding spec weapon (" + chosenSpecWeapon + "): "
					+ energyLeft + "% energy left, "
					+ (willRestore ? "restoration cycle pending"
							: "phase targets remain"));
		} else {
			queueSpecWeaponSwitchBack();
		}
		currentState = BotState.FIGHTING_CORP;
	}

    private void handleLooting() {
        Log.info("Looking for valuable loot...");

        // Phase C: this is the cleanest "Corp just died on our trip" hook.
        // Increment local kill id so the coordinator advances to the next kill
        // and clears per-kill spec counters on the next publish.
        coordinatorOnKillEnded();

        // 1.9.99.200: post-kill, equip the PHASE 1 spec weapon for the next
        // kill. Every new kill starts at phase 1 (Elder maul / DWH) with all
        // counters at 0, so the bot should be ready to spec immediately on
        // engage. Pre-1.9.99.199 the bot kept its last spec weapon equipped
        // (e.g. Arclight from phase 2) through to the next kill — wrong
        // weapon for phase 1. 1.9.99.199 tried swapping to Fang which the
        // user corrected: "we should wield whatever spec weapon we start
        // with because its literally a new kill and everything is at 0."
        //
        // IMPORTANT: do NOT use pickSpecWeaponForCurrentPhase() here — it
        // calls teamPhaseNeeded() which reads the coordinator aggregate,
        // and the OTHER bot's snapshot is still showing their old phase 2/3
        // counters until they publish their kill-end reset. So the aggregate
        // would lie about the phase. We just landed our kill — phase is
        // definitively 1, look it up directly via PHASE_SPEC_WEAPONS[1].
        try {
            String phase1Weapon = null;
            if (PHASE_SPEC_WEAPONS.length > 1 && PHASE_SPEC_WEAPONS[1] != null) {
                List<String> owned = getOwnedSpecWeapons();
                for (String w : PHASE_SPEC_WEAPONS[1]) {
                    if (!owned.contains(w)) continue;
                    if (Inventory.contains(new String[]{ w }) || Equipment.contains(w)) {
                        phase1Weapon = w;
                        break;
                    }
                }
            }
            if (phase1Weapon != null && !Equipment.contains(phase1Weapon)
                    && Inventory.contains(new String[]{ phase1Weapon })) {
                Log.info("Post-kill: equipping phase 1 spec weapon ("
                        + phase1Weapon + ") for next kill");
                chosenSpecWeapon = phase1Weapon;
                equipSpecWeapon();
            } else if (phase1Weapon != null && Equipment.contains(phase1Weapon)) {
                // Already wearing a phase-1 weapon (e.g. DWH from a previous
                // kill-end reset). Just make sure chosenSpecWeapon matches.
                chosenSpecWeapon = phase1Weapon;
            }
        } catch (Throwable ignored) {}

        // 1.9.99.95: wait up to 6s for valuable loot to appear on the
        // ground. Corp's death animation is ~3s; server-side loot can
        // take another tick or two after that to spawn. Pre-1.9.99.95
        // we ran the pickup loop ONCE immediately on entering LOOTING
        // — if loot hadn't dropped yet, the loop saw an empty ground
        // and the bot proceeded straight to bank/wait without grabbing
        // anything. Early-exit as soon as any one valuable item
        // appears so the common case (loot already there) doesn't add
        // latency. User: "Loot handling should probably take around
        // 5-7 seconds because its death animation is slow."
        long lootWaitStartedAt = System.currentTimeMillis();
        boolean lootDetected = Waiting.waitUntil(6000, () -> {
            for (String itemName : settings.valuableLoot) {
                if (Query.groundItems().nameEquals(itemName).isReachable()
                        .findFirst().isPresent()) {
                    return true;
                }
            }
            return false;
        });
        long lootWaitElapsed = System.currentTimeMillis() - lootWaitStartedAt;
        if (lootDetected) {
            Log.info("Valuable loot spawned after " + lootWaitElapsed + "ms — picking up");
        } else {
            Log.info("No valuable loot appeared within 6s (waited "
                    + lootWaitElapsed + "ms) — nothing for us to pick up this kill");
        }

        boolean foundLoot = false;

        for (String itemName : settings.valuableLoot) {
            Optional<GroundItem> lootOpt = Query.groundItems()
                    .nameEquals(itemName)
                    .isReachable()
                    .findFirst();

            if (lootOpt.isPresent()) {
                GroundItem loot = lootOpt.get();
                // If we're full, combo-eat a karambwan first so the take succeeds.
                if (Inventory.isFull()) {
                    Log.warn("Inventory full at loot pickup — combo-eating to free a slot");
                    ensureInventorySlotsFree(1);
                }
                if (loot.interact("Take")) {
                    if (Waiting.waitUntil(4000, () -> Inventory.contains(itemName))) {
                        Log.info("Successfully looted: " + itemName);
                        foundLoot = true;
                    }
                }
            }
        }

        if (foundLoot) {
            Log.info("Loot collected!");
        }

        // 1.8.8: restoration tracking is PER-KILL. Each new Corp kill starts
        // fresh — full POH cycle budget, zero specs counted, zero phase
        // contribution attributed to this account.
        // 1.9.6: also clear queued spec-weapon switch-back. If the kill
        // ended while a switch-back was queued (e.g. Corp died mid-bar),
        // the queue would survive into next kill's prep and fire at the
        // wrong moment. Clear it here at the same per-kill boundary.
        resetRestorationTracking();
        specWeaponSwitchQueued = false;
        specWeaponSwitchTime = 0;
        needsToSwitchBackFromSpec = false;
        corpSeenAtZeroHp = false; // 1.9.24: reset per-kill kill-confirmed flag
        maxCorpHpPercentThisKill = 0.0; // 1.9.28: reset HP-bar-populated tracker
        minCorpHpPercentThisKill = 1.0; // 1.9.99.181: low-water ratchet reset

        // IMPORTANT: Try to keep at least one team member in the room to prevent Corp roaming
        // Check if other teammates are staying or if we should stay
        boolean shouldStayInRoom = shouldStayToPreventRoaming();

        // Session-end signal takes precedence over any normal next-step
        // decision. Finish the kill, log out gracefully.
        if (sessionEndPending) {
            Log.warn("Session end pending - shutting down gracefully after this kill");
            currentState = BotState.EMERGENCY_ESCAPE;
            return;
        }

        // Check if we need to restock or can continue (different threshold after kill)
        if (needsResupplyAfterKill()) {
            if (shouldStayInRoom) {
                // Check if we can tough it out with current supplies
                if (hasEmergencySupplies()) {
                    Log.info("Low supplies but staying to prevent Corp roaming - using emergency reserves");
                    currentState = BotState.WAITING_FOR_TEAM; // Stay and wait
                } else {
                    Log.warn("Critically low supplies, must bank (Corp may roam)");
                    currentState = BotState.BANKING_AND_HEALING;
                }
            } else {
                Log.info("Teammates present, safe to resupply");
                currentState = BotState.BANKING_AND_HEALING;
            }
        } else {
            // 1.9.99.113: route to POH if spec isn't full — user wants
            // every kill to start with 100% spec so phase 1 can dump 4
            // Mauls without running out.
            // 1.9.99.128: reverted 1.9.99.120's "skip POH on join-late"
            // logic. User: "if we finish a kill; and its not our loot
            // then we need go back to PoH first; unless we need supplies.
            // then we go house -> poh so we have spec first." So
            // regardless of whether we got loot or not, we POH after a
            // kill if spec < 100. The bank-first-then-POH path is
            // already wired up via 1.9.99.126: if needsResupplyAfterKill
            // is true we go BANKING_AND_HEALING (handled above this
            // branch), and after banking handleBankingAndHealing routes
            // to PREPARING_RESTORATION_CYCLE if spec is still < 100.
            boolean specNotFull = Combat.getSpecialAttackPercent() < 100;
            boolean canPoh = hasHouseTeleportTab() && !isFeroxOnlyMode();
            if (specNotFull && canPoh) {
                Log.info("Ready for next kill, but spec at "
                        + Combat.getSpecialAttackPercent()
                        + "% — POH restore to start next kill at 100%");
                currentState = BotState.PREPARING_RESTORATION_CYCLE;
            } else {
                Log.info("Ready for next kill");
                currentState = BotState.WAITING_FOR_TEAM;
            }
        }
    }

    private boolean hasEmergencySupplies() {
        return Inventory.getCount(settings.foodNames) >= 5 && // Minimum 5 food
                getPrayerDoses() >= 2;                  // Minimum 2 prayer doses
    }

    /** 1.9.99.102: are supplies sufficient to skip the bank-trip and
     *  just do a POH ornate pool restore on emergency escape? Requires
     *  enough food, pots, and a house tab for the tele. Used to decide
     *  between Ferox+bank (full restock) vs POH-only (faster, no
     *  restock needed) in handleEmergencyEscape. */
    private boolean suppliesNotCriticalForPohEmergency() {
        int sharks = Inventory.getCount("Shark");
        int karams = Inventory.getCount("Cooked karambwan");
        int combatPots = 0;
        int restorePots = 0;
        try {
            combatPots = Inventory.getCount(getCombatPotionNames());
            restorePots = Inventory.getCount(SUPER_RESTORE_NAMES);
        } catch (Exception ignored) {}
        boolean hasTab = hasHouseTeleportTab();
        boolean ok = sharks >= 5 && karams >= 5
                && combatPots >= 1 && restorePots >= 1 && hasTab;
        if (!ok) {
            Log.info("POH-emergency check FAIL: sharks=" + sharks
                    + " (need 5+), karams=" + karams + " (need 5+), combatPot="
                    + combatPots + " (need 1+), restorePot=" + restorePots
                    + " (need 1+), houseTab=" + hasTab);
        }
        return ok;
    }

    private void handleEmergencyEscape() {
        Log.warn("Emergency escape activated!");

        // 1.9.99.102: prefer POH escape when supplies aren't critical.
        // The user's typical emergency case is a 50+30 combo at fight
        // start — supplies are still full (we just arrived from a
        // previous bank), but HP dropped below threshold. Going
        // Ferox+bank in that case is wasteful (no food/pot restock
        // needed), the POH ornate pool restores HP/prayer/spec in one
        // tab break. If supplies ARE depleted (we ate through them),
        // fall through to Ferox+bank as before. User: "it doesnt
        // hurt to check if supplies arnt critical and then doing poh.
        // theres rare occasions where we get combod from 50+30 at the
        // start and panic tele out when we could just poh ornate pool."
        if (suppliesNotCriticalForPohEmergency() && !isFeroxOnlyMode()) {
            Log.info("Emergency: supplies OK — POH ornate pool restore "
                    + "(skipping Ferox+bank trip)");
            resetPerKillStateAfterAbort();
            isInRestorationPhase = true;
            currentSpecialAttacksUsed = 0;
            currentState = BotState.TELEPORTING_TO_HOUSE;
            return;
        }

        // Method 1: Ferox Enclave teleport (fastest and safest)
        if (attemptFeroxEscape()) {
            Log.info("Successfully escaped to Ferox Enclave");
            resetPerKillStateAfterAbort(); // 1.9.90: clear stale per-kill ratchets after escape
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // Method 2: Games Necklace teleport
        if (attemptNecklaceEscape()) {
            Log.info("Successfully escaped using Games Necklace");
            resetPerKillStateAfterAbort(); // 1.9.90
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // Method 3: Run to entrance
        if (attemptRunEscape()) {
            Log.info("Successfully escaped by running");
            resetPerKillStateAfterAbort(); // 1.9.90
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // Method 4: Last resort - logout
        Log.warn("All escape methods failed, attempting logout");
        if (Login.logout()) {
            Log.info("Successfully logged out");
        } else {
            Log.error("CRITICAL: All escape methods failed!");
        }
        // 1.9.99.183: stop the script on emergency logout. Without this, the
        // main loop keeps spinning post-logout — re-attempts state handlers
        // every tick against a logged-out client, spamming "not logged in"
        // errors and never recovering. Audit MEDIUM #10.
        running = false;
    }

    private boolean teleportToFeroxEnclave() {
        Log.info("Attempting to teleport to Ferox Enclave...");

        // 1.9.23: close bank if open — same fix as handleTravelingToCorp.
        // Bank intercepts inventory item clicks while open, so Ring of
        // Dueling left-click opens a quantity prompt instead of teleporting.
        if (Bank.isOpen()) {
            Log.info("Bank still open — closing before Ferox tele");
            Bank.close();
            Waiting.waitUntil(2000, () -> !Bank.isOpen());
        }

        // Look for ring of dueling
        Optional<InventoryItem> ringOpt = Query.inventory()
                .nameContains("Ring of dueling")
                .findFirst();

        if (ringOpt.isPresent()) {
            InventoryItem ring = ringOpt.get();

            // Hover to check uptext
            if (ring.hover()) {
                Waiting.waitUniform(100, 300); // Brief delay to let uptext appear

                // Check if Ferox Enclave is the default option
                if (GameState.getUpText().contains("Ferox Enclave")) {
                    Log.info("Ferox Enclave is default option, left-clicking");
                    if (ring.click()) {
                        // Wait for teleport to complete
                        return Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
                    }
                } else {
                    // Right-click for menu
                    Log.info("Right-clicking ring for Rub menu");
                    // Try to click "Ferox Enclave" option directly
                    Log.info("Clicking Ferox Enclave option from ring menu");
                    if (ring.click("Ferox Enclave")) {
                        // Wait for teleport to complete
                        return Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
                    } else {
                        // Fallback: try "Rub" then look for Ferox option
                        Log.info("Trying Rub option as fallback");
                        if (ring.click("Rub")) {
                            Waiting.waitUniform(300, 600);
                            // After rubbing, it might open a dialog - handle that case
                            // This depends on how the ring works in your server
                            return Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
                        }
                    }
                }
            } else {
                Log.warn("Failed to hover over ring");
            }
        } else {
            Log.warn("No Ring of Dueling found in inventory");
        }

        return false;
    }

    private boolean isAtFeroxEnclave() {
        // Primary check: tile coords. The Ring of Dueling drops us at
        // approximately (3151, 3636, 0); the wider enclave covers
        // x:3120-3160, y:3620-3650. This works the moment the teleport
        // animation ends, before render has populated the bank chest /
        // pool / NPCs in our visibility window — which is what caused
        // the infinite-retele loop in 1.8.5.
        try {
            WorldTile pos = MyPlayer.getTile();
            if (pos != null && pos.getPlane() == 0
                    && pos.getX() >= 3120 && pos.getX() <= 3160
                    && pos.getY() >= 3620 && pos.getY() <= 3650) {
                return true;
            }
        } catch (Exception ignored) {}
        // Fallback: named objects/NPCs (covers the rare case where the
        // coord box is slightly off but we're clearly at Ferox).
        try {
            return Query.npcs().nameContains("Ferox").findFirst().isPresent() ||
                    Query.gameObjects().nameContains("Pool of Refreshment").findFirst().isPresent() ||
                    Query.gameObjects().nameContains("Bank chest").findFirst().isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    // ========== FEROX ENCLAVE METHODS ==========

    private boolean needsPoolRestoration() {
        // 1.9.57: dropped the spec-energy gate. User: 'if we have full hp
        // and prayer dont even touch the pool.' Pre-1.9.57 we also
        // returned true when spec < 100% — but after the pool drink
        // restored HP/prayer to max, spec was still typically partial,
        // so on the next handleBankingAndHealing tick this would return
        // true again, the bot would walk back to the pool, drink (no-op
        // because HP/prayer maxed), then back to bank, then back to
        // pool — user described: 'we go to the pool and then click on
        // the bank and then click on the pool again and just never
        // move.' Now only HP/prayer trigger the pool; spec refills
        // naturally during travel back to Corp + via specs we'll fire.
        // 1.9.99.74: thresholds bumped from "anything below max" to
        // "meaningfully depleted" (HP < 90%, prayer < 70%). Pre-1.9.99.74
        // ANY drop below max triggered a pool drink, including the
        // ~5-10 points of prayer lost just walking out of POH on the
        // way to bank for karambwans. Result: second pool drink wasted
        // an animation lock when we'd just been restored. User: "we
        // teled out went poh -> realized we didnt have aenough
        // kawambwans and then teleported to ferox and used the pool
        // again there instead of just banking fast ... since the
        // prayer stayed on we also restored it again when we banked".
        int currentHp = MyPlayer.getCurrentHealth();
        int maxHp = Skill.HITPOINTS.getActualLevel();
        int currentPrayer = Prayer.getPrayerPoints();
        int maxPrayer = Skill.PRAYER.getActualLevel();
        boolean hpLow = currentHp < (int) (maxHp * 0.9);
        boolean prayerLow = currentPrayer < (int) (maxPrayer * 0.7);
        return hpLow || prayerLow;
    }

    private boolean useRestorePool() {
        Log.info("Looking for restoration pool...");

        // First try to find pool on screen
        Optional<GameObject> poolOpt = Query.gameObjects()
                .nameContains("Pool of Refreshment")
                .findFirst();

		if (poolOpt.isPresent()) {
			GameObject pool = poolOpt.get();
			if (pool.interact("Drink")) {
				// 1.9.32: HP and prayer may restore on separate ticks
				// (different update batches server-side). Pre-1.9.32 we
				// required BOTH to be at max within 10s; if one hit max
				// first and the other lagged, the wait timed out and
				// useRestorePool returned false even though the drink
				// worked. Now: succeed as soon as EITHER reaches max,
				// then give a brief settle for the other.
				boolean firstRestored = Waiting.waitUntil(10000, () -> {
					boolean healthRestored = MyPlayer.getCurrentHealth() >= Skill.HITPOINTS.getActualLevel();
					boolean prayerRestored = Prayer.getPrayerPoints() >= Skill.PRAYER.getActualLevel();
					return healthRestored || prayerRestored;
				});
				if (firstRestored) {
					// Brief settle for the other stat.
					Waiting.waitUntil(2000, () -> {
						boolean healthRestored = MyPlayer.getCurrentHealth() >= Skill.HITPOINTS.getActualLevel();
						boolean prayerRestored = Prayer.getPrayerPoints() >= Skill.PRAYER.getActualLevel();
						return healthRestored && prayerRestored;
					});
					Log.info("Restored at Ferox pool (hp=" + MyPlayer.getCurrentHealth()
							+ ", prayer=" + Prayer.getPrayerPoints() + ")");
					return true;
				}
			}
		} else {
            Log.info("Pool not visible, walking via minimap to pool location");

            // Approximate coordinates for Ferox Enclave pool (adjust as needed)
            WorldTile poolLocation = new WorldTile(3150, 3635, 0);

            if (LocalWalking.walkTo(poolLocation)) {
                // Wait for movement and pool to appear on screen
                Waiting.waitUntil(5000, () ->
                        Query.gameObjects().nameContains("Pool of Refreshment").findFirst().isPresent());

                // Try clicking pool again after walking
                Optional<GameObject> poolAfterWalk = Query.gameObjects()
                        .nameContains("Pool of Refreshment")
                        .findFirst();

                if (poolAfterWalk.isPresent()) {
                    // 1.9.7.1: instant-true wait → real settle delay.
                    Waiting.waitNormal(400, 200);
                    return poolAfterWalk.get().interact("Drink");
                }
            }
        }

        Log.warn("Failed to use restoration pool");
        return false;
    }

    private boolean isNearFeroxBank() {
        // 1.9.99.30: require actual proximity to the bank/pool tile
        // (3135, 3630) — not just Query.gameObjects visibility. After
        // Ferox teleport the bot lands in a spot where the bank chest
        // is in render distance (Query returns it) but the pool is
        // 10+ tiles away, so useRestorePool's interact("Drink") hits a
        // tile that's loaded-but-too-far-to-click. User: "we are trying
        // to hover over the pool or bank instead of clicking to the
        // designated tile first in ferox". Same symptom 1.9.58 was
        // supposed to fix — the walk-first check was being bypassed
        // because Query.findFirst returned true prematurely.
        WorldTile feroxBankTile = new WorldTile(3135, 3630, 0);
        WorldTile myTile = MyPlayer.getTile();
        if (myTile != null && myTile.distanceTo(feroxBankTile) <= 4) {
            return true;
        }
        return false;
    }

    private void walkToFeroxBank() {
        // 1.9.43: user specified tile (3135, 3630, 0) — close enough to
        // BOTH the bank chest and the restoration fountains in a single
        // landing. Pre-1.9.43 we used (3128, 3631) which was on the far
        // side of the bank chest and sometimes left the bot in a spot
        // where neither the chest nor the pool was reachable in one
        // interact. User: "We want to specifically walk 3135,3630 tile
        // and that will put us close enough to interact with the
        // fountains and the bank chest."
        // 1.9.99.33: skip-walk guard uses physical distance, NOT
        // Query.gameObjects("Bank chest") visibility. After the Ferox
        // teleport the bank chest is in render distance so the old
        // Query check returned true, function bailed with "Bank chest
        // already visible — no walk needed", and isNearFeroxBank
        // (correctly distance-gated since 1.9.99.30) failed again on
        // the next tick — infinite loop. Bot stayed at the teleport
        // landing spot, never opened the bank, and burned every Games
        // necklace teleporting back and forth. User: "we ran out of
        // game necklace ... character teleported to the enclave but
        // isnt attempting to run to the tile ive told you to".
        WorldTile feroxBankTile = new WorldTile(3135, 3630, 0);
        WorldTile myTile = MyPlayer.getTile();
        if (myTile != null && myTile.distanceTo(feroxBankTile) <= 4) {
            Log.info("Already within range of Ferox bank/fountain tile — no walk needed");
            return;
        }
        Log.info("Walking to Ferox bank/fountain tile (3135, 3630)...");
        try {
            org.tribot.script.sdk.walking.GlobalWalking.walkTo(feroxBankTile);
            Waiting.waitUntil(15000, () -> {
                if (isNearFeroxBank()) return true;
                WorldTile t = MyPlayer.getTile(); // 1.9.99.180: NPE guard
                return t != null && t.distanceTo(feroxBankTile) <= 3;
            });
        } catch (Throwable e) {
            Log.warn("GlobalWalking.walkTo(Ferox bank) failed: " + e.getMessage()
                    + " — falling back to local walk");
            LocalWalking.walkTo(feroxBankTile);
        }
    }

    private boolean attemptFeroxEscape() {
        Log.info("Attempting escape via Ferox Enclave...");

        if (teleportToFeroxEnclave()) {
            return Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
        }
        return false;
    }

    private boolean attemptNecklaceEscape() {
        Log.info("Attempting escape via Games Necklace...");

        // 1.9.90: nameEquals("Games necklace(") never matched (charge suffix); use nameContains.
        Optional<InventoryItem> necklaceOpt = Query.inventory().nameContains("Games necklace(").findFirst();
        if (!necklaceOpt.isPresent()) {
            return false;
        }

        InventoryItem necklace = necklaceOpt.get();
        if (necklace.click("Barbarian Outpost")) {
            return Waiting.waitUntil(8000, () -> !isAtCorp());
        }
        return false;
    }

    private boolean attemptRunEscape() {
        Log.info("Attempting escape by running to entrance...");

        // First try to find the cave exit on screen
        // 1.9.90: null-guard obj.getName() — getName() can return null mid-stream and NPE the filter.
        Optional<GameObject> exitOpt = Query.gameObjects()
                .filter(obj -> obj.getName() != null && (obj.getName().contains("Exit") ||
                        obj.getName().contains("Cave entrance") ||
                        obj.getName().contains("Entrance")))
                .findFirst();

        if (exitOpt.isPresent()) {
            GameObject exit = exitOpt.get();
            Log.info("Exit found on screen, clicking it");

            if (exit.interact("Enter") || exit.interact("Exit") || exit.interact("Leave")) {
                // Wait to see if we successfully left Corp area
                return Waiting.waitUntil(10000, () -> !isAtCorp());
            }
        } else {
            Log.info("Exit not visible, walking toward entrance via minimap");

            // Approximate coordinates for Corp cave entrance direction (adjust as needed)
            // This should be the general direction of the entrance
            WorldTile entranceDirection = new WorldTile(2966, 4382, 2); // Example coordinates

            if (LocalWalking.walkTo(entranceDirection)) {
                Log.info("Walking toward entrance");

                // Wait a bit for movement, then check if we can see exit
                // 1.9.90: null-guard obj.getName() in waitUntil + post-walk filter.
                Waiting.waitUntil(5000, () ->
                        Query.gameObjects().filter(obj ->
                                obj.getName() != null && (obj.getName().contains("Exit") ||
                                        obj.getName().contains("Cave entrance"))).findFirst().isPresent());

                // Try to find and click exit again after walking
                Optional<GameObject> exitAfterWalk = Query.gameObjects()
                        .filter(obj -> obj.getName() != null && (obj.getName().contains("Exit") ||
                                obj.getName().contains("Cave entrance")))
                        .findFirst();

                if (exitAfterWalk.isPresent()) {
                    if (exitAfterWalk.get().interact("Enter") ||
                            exitAfterWalk.get().interact("Exit") ||
                            exitAfterWalk.get().interact("Leave")) {
                        return Waiting.waitUntil(10000, () -> !isAtCorp());
                    }
                }

                // If still no exit found, just keep walking in that direction
                return Waiting.waitUntil(10000, () -> !isAtCorp());
            }
        }

        Log.warn("Failed to escape by running");
        return false;
    }

    // ========== HELPER METHODS ==========

    /**
     * Smart detection of items we should keep during banking
     */
	private boolean shouldKeepItem(String itemName) {
		if (chosenSpecWeapon == null) {
			detectAndSetSpecWeapon();
		}

		// Keep our currently chosen spec weapon.
		if (itemName.equals(chosenSpecWeapon)) {
			return true;
		}

		// Keep every spec weapon this account owns (auto-detected). Trip might
		// rotate through Elder maul -> Arclight -> BGS depending on team phase;
		// banking flow shouldn't deposit any of them.
		for (String w : getOwnedSpecWeapons()) {
			if (itemName.equals(w)) return true;
		}

		// Keep the main weapon (both Fang variants if relevant).
		for (String mainVariant : getMainWeaponVariants()) {
			if (itemName.equals(mainVariant)) {
				return true;
			}
		}

		// Keep any defender (Avernic / Dragon / etc.) — and Antler guard (1.9.99.149).
		if (isDefenderName(itemName)) {
			return true;
		}

		// Essential gear
		if (itemName.equals(RUNE_POUCH) || itemName.equals(DIVINE_RUNE_POUCH)) {
			return true;
		}

		// Charged jewelry
		if (itemName.contains("Ring of dueling(") || itemName.contains("Games necklace(")) {
			return true;
		}

		// Potions
		if (Arrays.asList(getCombatPotionNames()).contains(itemName)) {
			return true;
		}
		if (Arrays.asList(SUPER_RESTORE_NAMES).contains(itemName)) {
			return true;
		}

		// Food (driven by settings.foodNames so it tracks the configured list).
		if (settings != null && settings.foodNames != null) {
			for (String f : settings.foodNames) {
				if (itemName.equals(f)) {
					return true;
				}
			}
		}

		// Keep house tabs
		if (itemName.equals("Teleport to house")) {
			return true;
		}

		return false;
	}

	private boolean shouldStartRestorationCycle() {
		// FEROX_ONLY mode bypasses the entire POH cluster — HP/prayer get
		// restored at Ferox during the next bank trip; spec only refills via
		// natural regen (or shared across a team where bots stagger specs).
		if (isFeroxOnlyMode()) {
			Log.debug("pohSource=FEROX_ONLY - skipping POH restoration cycle");
			return false;
		}

		Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
		boolean corpPresent = corpOpt.isPresent();
		// 1.8.8: trigger on DEPLETED spec (not full spec — pre-1.8.8 had this
		// inverted). Continue restoring as long as we still owe the team phase
		// progress AND Corp is healthy enough to be worth spec'ing. The
		// totalRestorationCycles count is a safety upper bound, not the real
		// driver — the loop terminates naturally when:
		//   (a) teamPhaseNeeded() returns 0 (phase targets met), OR
		//   (b) Corp HP drops below corpMinHpForSpec (a teammate is killing
		//       it; spec dumping is pointless past this point — go melee).
		boolean specDepleted = Combat.getSpecialAttackPercent() < getMinSpecEnergy();
		boolean phaseTargetsNotMet = teamPhaseNeeded() > 0;
		// 1.9.58: also require a usable spec weapon for the current phase.
		// Pre-1.9.58 the bot would tele to POH for phase 3 (BGS damage)
		// even with no BGS owned — back at Corp, refreshSpecWeaponForPhase
		// returned null, no spec fired, energy drained again somehow, and
		// the bot teled AGAIN. User: 'i clicked the special attack on the
		// fang manually and it went back to arlight tele debuff looping
		// for the rest of the kill.' The manual Fang spec dropped energy
		// to 25%, specDepleted+phaseTargetsNotMet flipped true, and the
		// tele cycle kicked in with no Phase-3 weapon to actually use.
		boolean haveWeaponForCurrentPhase = pickSpecWeaponForCurrentPhase() != null;
		if (!haveWeaponForCurrentPhase) {
			// 1.9.58.1: dropped the per-tick log spam.
			return false;
		}
		// 1.9.6: dropped corpHealthAboveFloor from this gate. The Corp-HP
		// gate is a SPEC-FIRE decision (don't waste a spec on a near-dead
		// Corp) — corpMinHpForSpec / isCorpHealthAboveSpecThreshold still
		// guards spec firing in shouldSpecNowConsideringTeam. But for the
		// tele-to-POH decision, the user wants ALWAYS-TELE after bar
		// exhaust as long as more specs are wanted. Production log showed
		// the gate failing in the in-line detector at one moment then
		// passing 6 sec later in the mid-fight check — bot wasted a Fang
		// swap in between. After POH, if Corp HP is below floor we'll
		// fall through to kill-phase melee instead of spec-firing again.
		// 1.9.25: dropped the safetyCap (currentRestorationCycle <
		// settings.totalRestorationCycles). Old profiles have
		// totalRestorationCycles=3 which capped restorations at 3/kill —
		// after 3 cycles in one kill, this gate flipped false and the
		// detector fell to "Kill phase swap to Fang" even though phase
		// targets (4 Elder maul + 20 Arclight + 200 BGS damage) weren't
		// met. Bot stopped specing way too early. The real gates are
		// phase targets + Corp HP floor + house tabs available.
		boolean hasHouseTabs = hasHouseTeleportTab();

		if (!hasHouseTabs) {
			// 1.9.99.219: throttled — was firing every tick, ~10 lines/sec.
			long nowNht = System.currentTimeMillis();
			if (nowNht - lastNoHouseTabLogAt > 10_000) {
				lastNoHouseTabLogAt = nowNht;
				Log.info("No house tabs available - skipping POH restoration");
			}
			return false;
		}

		// 1.9.99.116: reinstated the Corp-HP gate (was removed in 1.9.6).
		// If teammates have already burned Corp below the spec floor
		// (default 1700), more spec dumping is pointless — just stay and
		// Fang. Pre-1.9.99.116 the bot would tele to POH for another
		// spec cycle even when Corp had ~500 HP left. With 1.9.99.116's
		// isInKillPhase scale fix this now correctly returns true at
		// low HP, blocking the wasteful tele. User: "we need to just
		// give up on spec dumping and continue normally killing the
		// boss if our teammates have it under 1700 health."
		if (isInKillPhase()) {
			// 1.9.99.219: throttled — was firing every tick in kill phase.
			long nowKp = System.currentTimeMillis();
			if (nowKp - lastKillPhaseSkipLogAt > 10_000) {
				lastKillPhaseSkipLogAt = nowKp;
				Log.info("Skipping POH restoration — Corp below spec floor (kill phase)");
			}
			return false;
		}

		// 1.9.99.132 → 1.9.99.151: REMOVED the teammate-in-boss-room
		// POH-skip proxy. User: "a teammate in room doesnt mean we should
		// commit thats rediculous. We either need to hit our goals or
		// corp needs to be at the hp threshold." A teammate standing in
		// the boss room isn't proof a kill is happening. Commit is now
		// driven by isInKillPhase() above (teamPhaseNeeded == 0 OR Corp
		// HP < corpMinHpForSpec). If Corp's HP isn't visible from the
		// lobby, the bot does another POH cycle — once it walks back in
		// it'll see Corp's HP and skip future specs accordingly.

		return corpPresent
				&& specDepleted
				&& phaseTargetsNotMet
				&& !isInRestorationPhase;
	}

	/**
	 * Reset restoration tracking for new trip
	 */
	private void resetRestorationTracking() {
		currentRestorationCycle = 0;
		currentSpecialAttacksUsed = 0;
		currentHouseEntryAttempts = 0;
		isInRestorationPhase = false;
		needsPoolRestoration = false;
		lastHouseEntryAttempt = 0;
		Log.info("POH restoration tracking reset for new trip");
	}


    /**
     * Dynamic deposit method using smart detection
     */
    private boolean depositAllExceptKeepItems() {
        while (true) {
            Optional<InventoryItem> itemToDeposit = Query.inventory()
                    .stream()
                    .filter(item -> !shouldKeepItem(item.getName()))
                    .findFirst();

            if (!itemToDeposit.isPresent()) {
                break; // No more items to deposit
            }

            if (!itemToDeposit.get().click("Deposit-All")) {
                return false; // Failed to deposit
            }

            Waiting.waitUntil(1000, () ->
                    !Query.inventory().nameEquals(itemToDeposit.get().getName()).findFirst().isPresent());
        }
        // 1.9.44: also deposit DUPLICATES of single-keep essentials. User: 'if
        // we have a full inventory of gear but no sharks we still try to
        // withdraw sharks instead of dynamically just ignoring them' — the
        // root cause was extras of items the keep-filter approves (multiple
        // rings, multiple rune pouches, two defenders, two house tabs, ...).
        // Nothing was deposit-eligible, the inventory stayed full of keep
        // items, and the food/potion withdraws ran into 'cannot fit'. We
        // need exactly ONE of each of: rune pouch, defender, charged ring,
        // charged necklace, house tab. Spec weapons, potions, food keep
        // their existing target-count gating downstream.
        depositSingleKeepDuplicates();
        return true;
    }

    /** 1.9.44: deposit anything beyond the first instance of items where we
     *  only ever need ONE. Keeps the existing keep-set intact for the first
     *  of each item, deposits the rest. Idempotent; safe to call after the
     *  main deposit pass. */
    private void depositSingleKeepDuplicates() {
        // Single-instance keep items: name + max-keep.
        java.util.Map<String, Integer> singles = new java.util.LinkedHashMap<>();
        singles.put(RUNE_POUCH, 1);
        singles.put(DIVINE_RUNE_POUCH, 1);
        // 1.9.99.149: DEFENDER_PRIORITY now includes Antler guard, so this
        // automatically caps Antler guard to 1 in inventory too.
        for (String d : DEFENDER_PRIORITY) singles.put(d, 1);
        // 1.9.60: do NOT cap "Teleport to house" tabs — user does multiple
        // POH cycles per kill and needs the stack to survive.
        singles.put("Construct. cape", 1);
        singles.put("Construct. cape(t)", 1);

        for (java.util.Map.Entry<String, Integer> e : singles.entrySet()) {
            int have;
            try { have = Inventory.getCount(e.getKey()); } catch (Exception ex) { continue; }
            int max = e.getValue();
            if (have > max) {
                int excess = have - max;
                Log.info("Banking: depositing " + excess + " excess " + e.getKey()
                        + " (keeping " + max + ")");
                try { Bank.deposit(e.getKey(), excess); } catch (Exception ignored) {}
                Waiting.waitNormal(250, 80);
            }
        }

        // 1.9.71: charged jewelry — keep ONLY the highest-dose one of each
        // kind. User: 'each time we bank we pull out new things we dont
        // need. like currently i have 3 rings of dueling and 3 games
        // necklace.' Pre-1.9.71 the deposit table keyed each dose
        // separately ('Ring of dueling(8)', 'Ring of dueling(6)', ...)
        // so a (8), (5), and (3) all looked like unique items and none
        // got deposited. Now we find the highest dose present and
        // deposit every lower dose of the same item.
        depositLowerDoseJewelry("Ring of dueling");
        depositLowerDoseJewelry("Games necklace");
    }

    /** 1.9.71: deposit every dose of the named charged item except the
     *  highest one currently in inventory. */
    private void depositLowerDoseJewelry(String baseName) {
        int highestDose = -1;
        for (int dose = 8; dose >= 1; dose--) {
            String name = baseName + "(" + dose + ")";
            try {
                if (Inventory.getCount(name) > 0) {
                    highestDose = dose;
                    break;
                }
            } catch (Exception ignored) {}
        }
        if (highestDose < 0) return;
        for (int dose = 1; dose < highestDose; dose++) {
            String name = baseName + "(" + dose + ")";
            try {
                int n = Inventory.getCount(name);
                if (n > 0) {
                    Log.info("Banking: depositing " + n + " " + name
                            + " (keeping highest dose " + baseName + "(" + highestDose + "))");
                    Bank.deposit(name, n);
                    Waiting.waitNormal(250, 80);
                }
            } catch (Exception ignored) {}
        }
        // Also deposit any extras of the highest-dose itself (>1).
        String highest = baseName + "(" + highestDose + ")";
        try {
            int n = Inventory.getCount(highest);
            if (n > 1) {
                Log.info("Banking: depositing " + (n - 1) + " excess " + highest);
                Bank.deposit(highest, n - 1);
                Waiting.waitNormal(250, 80);
            }
        } catch (Exception ignored) {}
    }

	private boolean withdrawBankingItems() {
		Log.info("Starting banking withdrawal...");
		boolean overallSuccess = true;

		// Phase 1: Essential items
		if (!withdrawEssentialItems()) {
			Log.warn("Some essential items failed to withdraw");
			overallSuccess = false;
		}

		// Phase 2: Potions
		if (!withdrawPotions()) {
			Log.warn("Some potions failed to withdraw");
			overallSuccess = false;
		}

		// Phase 3: Food
		if (!withdrawFood()) {
			Log.warn("Some food failed to withdraw");
			overallSuccess = false;
		}

		// Phase 4 (1.9.99.85): house tabs — refill when below the
		// REFILL_BELOW threshold (default 4); top up to TARGET (default
		// 10). Skipped when inventory still has enough — keeps the
		// "don't withdraw every trip" cadence the user wanted. Pre-
		// 1.9.99.85 tabs were never withdrawn, so a session that started
		// with low tabs would silently degrade to slow Fang-only kills
		// once depleted.
		if (!withdrawHouseTabs()) {
			Log.warn("House tabs withdrawal failed (may be empty in bank)");
			// Not fatal — script can still kill Corp without restoration
			// (just slower). Hard-stop is the caller's choice.
		}

		// Check minimum supplies (no POH requirement)
		if (!overallSuccess && !hasMinimumSupplies()) {
			Log.error("Banking failed and insufficient minimum supplies");
			return false;
		}

		Log.info("Banking withdrawal complete");
		return true;
	}

	/** 1.9.99.85: house-tab withdrawal with refill-below threshold.
	 *  Skip when inventory count >= REFILL_BELOW. Otherwise top up to
	 *  TARGET. Returns true if no withdrawal needed OR if the withdrawal
	 *  succeeded; false only if we tried to withdraw and Bank.withdraw
	 *  returned false (e.g., bank is empty of tabs). */
	private boolean withdrawHouseTabs() {
		// 1.9.99.216: use the same Query-based count as hasHouseTeleportTab()
		// so the withdraw decision matches the runtime "do I have a tab?"
		// check. Prior code used Inventory.getCount("Teleport to house"),
		// which the user observed returning bogus values like 784 (likely
		// noted-form items or a count from another inventory entry with the
		// same name) — withdraw was skipped and mid-fight the bot found 0
		// actual tabs ("No house tabs available — skipping POH restoration").
		int have = (int) Query.inventory().nameEquals("Teleport to house").count();
		int rawGetCount = Inventory.getCount("Teleport to house");
		if (have != rawGetCount) {
			Log.warn("House tab count mismatch: Query.count=" + have
					+ " vs Inventory.getCount=" + rawGetCount
					+ " — using Query.count (matches hasHouseTeleportTab)");
		}
		if (have >= INTERNAL_HOUSE_TAB_REFILL_BELOW) {
			Log.info("Skip house tab withdraw — have " + have + " (refill below "
					+ INTERNAL_HOUSE_TAB_REFILL_BELOW + ")");
			return true;
		}
		// 1.9.99.168: randomize withdrawal target in 20–75 range per refill
		// (user request). Stops antiban-flag pattern of always withdrawing
		// to the same fixed target. Also gives a much longer runway between
		// bank trips for the tabs themselves.
		int targetThisTrip = 20 + (int) (Math.random() * 56); // 20..75
		int need = Math.max(1, targetThisTrip - have);
		int bankCount = Bank.getCount("Teleport to house");
		if (bankCount <= 0) {
			Log.warn("Inventory has " + have + " house tabs (need " + need
					+ ") but bank has 0 — cannot restock");
			return false;
		}
		int amount = Math.min(need, bankCount);
		Log.info("Withdrawing " + amount + " house tabs (have " + have
				+ ", target " + targetThisTrip + " [random 20-75]"
				+ ", bank " + bankCount + ")");
		if (!Bank.withdraw("Teleport to house", amount)) {
			Log.warn("Bank.withdraw(Teleport to house, " + amount + ") returned false");
			return false;
		}
		Waiting.waitUntil(2000, () ->
				Query.inventory().nameEquals("Teleport to house").count() >= have + 1);
		// 1.9.99.216: post-withdraw sanity check via hasHouseTeleportTab so we
		// notice if the bank.withdraw returned true but the tab didn't actually
		// land in our inventory (some bizarre API state). The mid-fight POH
		// gate uses this exact predicate.
		if (!hasHouseTeleportTab()) {
			Log.warn("withdrawHouseTabs: Bank.withdraw succeeded but hasHouseTeleportTab() "
					+ "still false — inventory state out of sync");
			return false;
		}
		return true;
	}

    private boolean withdrawEssentialItems() {
        List<String> essentialItems = new ArrayList<>();

        // Build list of needed essential items. For charged jewelry, top up
        // whenever the highest dose drops below threshold — not just when
        // missing — so a (1)-charge ring doesn't strand us next trip.
        // 1.9.99.149: gate on hasSpecWeapon (any owned spec, in inv OR
        // equipment) instead of hasElderMaul. Pre-1.9.99.149 a DWH-only
        // account whose DWH was already equipped still got flagged as
        // "missing Elder maul" and triggered a doomed bank withdraw.
        if (!hasSpecWeapon()) essentialItems.add("Elder maul");
        // 1.9.99.149: skip rune-pouch withdraw entirely once we've confirmed
        // the account doesn't own one — veng-gate already handles the
        // no-pouch case without blocking the trip.
        if (!hasRunePouch() && !runePouchKnownAbsent) essentialItems.add("Rune pouch");
        if (ringOfDuelingNeedsTopUp()) essentialItems.add("Ring of dueling");
        if (gamesNecklaceNeedsTopUp()) essentialItems.add("Games necklace");

        // Randomize order
        Collections.shuffle(essentialItems);

        for (String item : essentialItems) {
            if (!withdrawEssentialItem(item)) {
                Log.warn("Failed to withdraw: " + item);
                // Continue anyway instead of hard failing
            }

            // Random delay between withdrawals
            Waiting.waitUniform(200, 600);
        }

        return true; // Always return true, let individual methods handle failures
    }

    private boolean hasElderMaul() {
        return Inventory.contains(ELDER_MAUL) || Equipment.contains(ELDER_MAUL);
    }

    private boolean hasRunePouch() {
        return Inventory.contains(RUNE_POUCH) || Inventory.contains(DIVINE_RUNE_POUCH);
    }

    private boolean withdrawEssentialItem(String itemType) {
        boolean success = false;

        switch (itemType) {
            case "Elder maul": // This key still used, but now withdraws chosen spec weapon
                success = withdrawSpecWeapon();
                Log.info("Spec weapon withdrawal (" + chosenSpecWeapon + "): " + (success ? "SUCCESS" : "FAILED"));
                if (success) {
                    Waiting.waitUntil(2000, () -> hasSpecWeapon());
                }
                break;
            case "Rune pouch":
                success = withdrawRunePouch();
                Log.info("Rune pouch withdrawal: " + (success ? "SUCCESS" : "FAILED"));
                if (success) {
                    Waiting.waitUntil(2000, () -> hasRunePouch());
                }
                break;
            case "Ring of dueling":
                success = withdrawHighestChargedItem("Ring of dueling", 8);
                Log.info("Ring of dueling withdrawal: " + (success ? "SUCCESS" : "FAILED"));
                if (success) {
                    Waiting.waitUntil(2000, () -> hasChargedRingOfDueling());
                }
                break;
            case "Games necklace":
                success = withdrawHighestChargedItem("Games necklace", 8);
                Log.info("Games necklace withdrawal: " + (success ? "SUCCESS" : "FAILED"));
                if (success) {
                    Waiting.waitUntil(2000, () -> hasChargedGamesNecklace());
                }
                break;
        }

        return success;
    }

    /** 1.9.99.149: once we confirm the account has zero rune pouches in
     *  bank AND inventory, stop trying to withdraw one. Veng-gate already
     *  treats "no rune pouch" as a soft fail (won't cast); this flag just
     *  silences the per-bank-cycle error spam. Cleared on script restart. */
    private boolean runePouchKnownAbsent = false;

    private boolean withdrawRunePouch() {
        // Try divine rune pouch first, then regular
        if (Bank.getCount(DIVINE_RUNE_POUCH) > 0) {
            Log.info("Withdrawing Divine rune pouch");
            return Bank.withdraw(DIVINE_RUNE_POUCH, 1);
        } else if (Bank.getCount(RUNE_POUCH) > 0) {
            Log.info("Withdrawing Rune pouch");
            return Bank.withdraw(RUNE_POUCH, 1);
        }

        // 1.9.99.149: bank confirmed empty — and hasRunePouch() said
        // inventory was empty too (we wouldn't be in this method
        // otherwise). Mark absent so future banking cycles skip this
        // entirely instead of logging "No rune pouch ... FAILED"
        // every second.
        if (!runePouchKnownAbsent) {
            Log.info("No rune pouch in bank or inventory — proceeding "
                    + "without vengeance for this session");
            runePouchKnownAbsent = true;
        }
        return false;
    }

    private boolean withdrawPotions() {
        List<String> potionTasks = new ArrayList<>();

        // Build list of needed potions
        if (Inventory.getCount(getCombatPotionNames()) < 1) {
            potionTasks.add("super_combat");
        }
        if (Inventory.getCount(SUPER_RESTORE_NAMES) < 2) {
            potionTasks.add("super_restore");
        }

        // Randomize order
        Collections.shuffle(potionTasks);

        for (String task : potionTasks) {
            if (task.equals("super_combat")) {
                if (!withdrawSuperCombat()) return false;
            } else if (task.equals("super_restore")) {
                if (!withdrawSuperRestores()) return false;
            }

            // Random delay between withdrawals
            Waiting.waitUniform(300, 800);
        }

        return true;
    }

    private boolean withdrawFood() {
        // 1.9.22: skip withdrawal of a food type if we already have the
        // target count of it. Pre-1.9.22 we always withdrew at least the
        // target amount, even if the deposit step had left full counts
        // in the inventory — causing a "cannot fit" failure when the
        // inventory was already topped up.
        List<String> foodTasks = new ArrayList<>(Arrays.asList("karambwans", "sharks"));
        Collections.shuffle(foodTasks);

        boolean firstFood = true;
        for (String foodType : foodTasks) {
            if (foodType.equals("karambwans")) {
                int have = Inventory.getCount("Cooked karambwan");
                int want = settings.targetKarambwans;
                if (have >= want) {
                    Log.info("Skip karambwans withdraw — already have " + have + "/" + want);
                    continue;
                }
                int amount = firstFood ? Math.max(1, want - have) : 0;
                if (!withdrawKarambwans(amount)) return false;
            } else {
                int have = Inventory.getCount("Shark");
                int want = settings.targetSharks;
                if (have >= want) {
                    Log.info("Skip sharks withdraw — already have " + have + "/" + want);
                    continue;
                }
                int amount = firstFood ? Math.max(1, want - have) : 0;
                if (!withdrawSharks(amount)) return false;
            }
            firstFood = false;
            Waiting.waitUniform(400, 900);
        }
        return true;
    }

    // ========== UPDATED PREPARE SPEC WEAPON METHOD ==========
	/** 1.9.0: lobby-side prep. Same idea as prepareSpecWeaponForCorp but
	 *  without the Corp-visible / Corp-alive checks (we're outside the boss
	 *  room and Corp isn't loaded yet). Idempotent — if we're already prepped
	 *  (spec weapon equipped + spec pre-activated), it no-ops. */
	private void prepareSpecWeaponInLobby() {
		if (chosenSpecWeapon == null) {
			detectAndSetSpecWeapon();
		}
		if (chosenSpecWeapon == null) return; // nothing to prep with

		// 1.9.99.172: kill-phase pre-swap. If kill phase is already
		// confirmed (locally via isInKillPhase OR via coordinator
		// teammate snapshot via isTeamInKillPhase), pre-swap to the
		// main weapon (Fang) HERE in the lobby instead of going through
		// spec-weapon prep + later swap. Saves the in-room weapon-swap
		// delay and matches user's stated pattern: "same way we do with
		// pre speccing, drinking super combat". The bot walks into the
		// boss room with Fang already equipped, ready to melee.
		if (isInKillPhase() || isTeamInKillPhase()) {
			if (!isMainWeaponEquipped()) {
				Log.info("Lobby prep: kill phase detected — pre-swapping to main weapon (Fang)");
				equipMainWeaponFast();
			} else {
				Log.info("Lobby prep: kill phase detected, main weapon already equipped — no action");
			}
			return; // skip spec prep entirely
		}

		// 1.9.99.113: refresh chosenSpecWeapon for the current phase before
		// the equip check. Same fix as prepareSpecWeaponForCorp — without
		// this, the lobby would equip BGS (stale from last kill's phase 3)
		// instead of Maul for the new kill's phase 1.
		refreshSpecWeaponForPhase();

		// Already prepped? Spec weapon equipped + spec button active → no work.
		if (isSpecWeaponEquipped() && Combat.isSpecialAttackEnabled()) {
			specWeaponReadyForUse = true;
			return;
		}

		// Energy gate: don't waste prep on a near-empty bar.
		if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;

		// 1.9.99.187: maybeDrinkCombatPotInLobby now drinks unconditionally
		// when unboosted (no trip-plan gate). Handles inventory-space
		// preflight (eat karambwan if full before drink). The old
		// Inventory.isFull()-gated block below is redundant — removed.
		maybeDrinkCombatPotInLobby();
		// 1.9.99.36: only eat-for-slot if we ACTUALLY need a slot. The
		// only thing that needs a free slot here is equipSpecWeapon
		// (specifically when our currently-wielded shield/offhand needs
		// to move to inventory for a 2H spec weapon swap). If the spec
		// weapon is already equipped, the equip below is a no-op and
		// we don't need the slot at all. Pre-1.9.99.36 we ate
		// unconditionally on Inventory.isFull, which animation-locked
		// the bot right as it walked into the boss room — Corp then
		// got a free magic hit while we chewed. User: "we use a
		// karamwan to top oursleve off, meanwhile the corp notices we
		// are in the room while we are eating and blasts us with
		// another hit of damage so we start the fight attacking
		// already lower hp than if we just ran straight to him". The
		// 1.9.99.35 startup-HP gate already ensures we leave the POH
		// at full HP, so the eat truly isn't needed.
		if (Inventory.isFull() && !isSpecWeaponEquipped()) {
			Log.info("Lobby prep: eating karambwan to free a slot for spec weapon equip");
			eatKarambwan();
		}

		// 1.9.99.106: spec-weapon swap honors the trip plan. If the
		// plan says LOBBY (70% default), swap here so we arrive at
		// the boss room with weapon already equipped — saves the 0.6s
		// boss-room swap delay before first swing. If plan says
		// BOSS_ROOM (30%), defer to prepareSpecWeaponForCorp.
		if (!isSpecWeaponEquipped()) {
			if (weaponSwapPlanThisTrip == WeaponSwapLocation.LOBBY) {
				Log.info("Lobby prep: equipping spec weapon " + chosenSpecWeapon
						+ " (trip plan: LOBBY)");
				if (!equipSpecWeapon()) {
					Log.warn("Lobby prep: failed to equip spec weapon - will retry in-room");
					return;
				}
			} else {
				Log.info("Lobby prep: deferring spec weapon swap to boss room (trip plan: BOSS_ROOM)");
			}
		}

		specWeaponReadyForUse = true;
		// 1.9.78: stage B — roll for spec pre-activate in the lobby
		// (only if stage A at the pool didn't already activate).
		// The 50% miss case falls through to stage C in handleFightingCorp.
		// 1.9.99.106: skip Stage B if the weapon swap was deferred to
		// boss room — activating the spec bar with the wrong weapon
		// equipped wastes the click (the boss-room swap will toggle
		// the bar OFF). Stage C handles activation post-swap.
		if (isSpecWeaponEquipped()) {
			maybePreActivateSpecStageB();
		} else {
			Log.info("Lobby prep: skipping Stage B (weapon not yet equipped — Stage C will handle)");
		}
		// If neither stage A nor B activated, leave the energy floor /
		// xp baseline in their pre-activate state for stage C to handle.
		if (Combat.isSpecialAttackEnabled() && specPreActivatedThisTrip) {
			lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
			xpAtSpec = getMeleeCombatXp();
			corpHpAtSpec = readCorpHpPct(); // 1.9.99.37
		}
	}

	private void prepareSpecWeaponForCorp(Npc corp) {
		if (chosenSpecWeapon == null) {
			detectAndSetSpecWeapon();
		}

		// 1.9.99.166: stale-state reset MOVED here from handleFightingCorp.
		// Pre-1.9.99.166 the 1.9.99.156 reset ran AFTER prepareSpecWeapon
		// had already equipped and pre-activated the stale spec weapon
		// (e.g. BGS from previous kill's phase 3). Spec fired immediately
		// on Corp at full HP wasting a 50% bar on the wrong phase. User
		// log: "Stale committedSpecPhase=3 ... Pre-activated spec fired
		// in-line (BGS)" within the same second. Now: reset BEFORE the
		// equip path so refreshSpecWeaponForPhase picks the correct
		// phase 1 weapon (DWH / Elder maul) to equip.
		// 1.9.99.189: reverted 1.9.99.188's latchStuck trigger expansion —
		// it would over-fire across cycles while a kill is still in
		// progress (Corp regens HP between cycles per design) and wipe
		// the persistent stat-reduction spec counters. Back to the
		// original 1.9.99.156 trigger.
		// 1.9.99.194: require corpSeenAtZeroHp — Corp's HP regenerating to
		// 100% in an empty room is NOT a new kill. Without this gate the
		// stale-reset wiped specsThisKill every POH cycle on solo bots.
		if (committedSpecPhase > 1 && corpSeenAtZeroHp && isCorpAlive(corp)
				&& corp.isHealthBarVisible()
				&& corp.getHealthBarPercent() >= 0.95) {
			Log.warn("Stale committedSpecPhase=" + committedSpecPhase
					+ " at fresh Corp HP in prep — resetting per-kill state");
			committedSpecPhase = 0;
			if (mySnapshot != null) {
				// 1.9.99.211: clear() instead of new — keep reference stable.
				if (mySnapshot.specsThisKill != null) mySnapshot.specsThisKill.clear();
				else mySnapshot.specsThisKill = new LinkedHashMap<>();
				mySnapshot.bgsDamageDealt = 0;
			}
		}

		// 1.9.99.164: if it's already kill phase (Corp HP low OR team
		// goals met OR coordinator-reported kill phase from a teammate),
		// SKIP all spec-weapon prep and equip Fang immediately. User:
		// "when we join a kill and know that its time to start helping;
		// a lot of times we end up walking up and hitting it with our
		// spec weapon once or twice before switching; which is big
		// wasted dps over time ... if its time for us to join the fight
		// we should switch right away". Pre-1.9.99.164 the bot would
		// equip Elder maul/DWH, eat a karambwan to free a slot, pre-
		// activate spec, swing once or twice, THEN swap to Fang — all
		// while Corp was already dying. Now: skip directly to main-
		// weapon swap. shouldUseSpecialAttack() will also return false
		// during kill phase so no spec fires.
		// 1.9.99.207: only skip spec prep when phases are ACTUALLY done.
		// Pre-1.9.99.207 a late joiner who saw Corp below 1700 HP (Bot B
		// had already drained Corp's HP a bit) skipped DWH/BGS prep
		// entirely even though teamPhaseNeeded was still > 0 — bot just
		// meleed with Fang for the whole fight while spec phases sat
		// unfilled. Now gate on teamPhaseNeeded()==0 — Corp HP being
		// low is necessary but not sufficient; phases must also be
		// complete before we drop spec prep. Preserves the original
		// 1.9.99.164 intent (don't waste DWH on a dying Corp when
		// targets are met) while letting a late joiner contribute to
		// unfinished phases (BGS damage, etc.).
		if ((isInKillPhase() || isTeamInKillPhase()) && teamPhaseNeeded() == 0) {
			Log.info("Kill phase detected on join AND phases complete — "
					+ "skipping spec prep, equipping main weapon directly");
			equipMainWeaponFast();
			return;
		}

		// 1.9.99.113: refresh chosenSpecWeapon for the CURRENT phase before
		// the equip check. Pre-1.9.99.113 chosenSpecWeapon stayed pinned to
		// whatever was used last (e.g. BGS from the prior kill's phase 3).
		// At the start of a new kill, teamPhaseNeeded() resets to 1 and
		// we need Elder maul / DWH equipped — refreshSpecWeaponForPhase()
		// updates chosenSpecWeapon to the right weapon for this phase.
		// Without it, isSpecWeaponEquipped() returned false (Fang on, BGS
		// chosen, BGS not equipped) → equipped BGS, then specced phase 1
		// with BGS. User: "once the kill started; we started trying to
		// use our spec weapon as the BGS. Which isnt what we should start
		// with."
		refreshSpecWeaponForPhase();

		Log.info("prepareSpecWeaponForCorp called - Corp alive: " + isCorpAlive(corp) +
				", Spec energy: " + Combat.getSpecialAttackPercent() +
				", Chosen spec weapon: " + chosenSpecWeapon);

		// 1.9.0: skip prep entirely if HP is already low. Drinking a super
		// combat potion is a 1-2 tick animation lock and the karambwan
		// slot-eat that follows is another tick. With Corp swinging, that's
		// up to 60 HP of damage absorbed during prep. If we're below the
		// combo-eat threshold (50), prep is suicidal — eat instead and let
		// combat start un-prepped. We'll still spec via shouldUseSpecialAttack
		// once HP is back above the threshold.
		int currentHp = MyPlayer.getCurrentHealth();
		// 1.9.99.100: use settings.specDumpPanicTeleHp (default 35) as
		// the prep-eat threshold instead of the global
		// INTERNAL_COMBO_EAT_HP (50). prepareSpecWeaponForCorp runs
		// when Corp becomes visible — same context as the spec dump
		// cycle. At HP 36-50 the user's expectation is "keep specing,
		// don't eat" (Corp's first few Arclight specs frequently drop
		// HP into this range before stats are reduced); only bail at
		// HP <= 35. Matches the handleHealthAndPrayer panic-tele gate.
		// User: "it seems like we are still eating when above 35
		// health when specing down; specifically with the arclight."
		int prepEatThreshold = settings.specDumpPanicTeleHp;
		if (currentHp <= prepEatThreshold) {
			Log.warn("HP " + currentHp + " <= " + prepEatThreshold
					+ " (spec-dump prep threshold) — skipping spec prep, combo-eating instead");
			emergencyComboEat();
			return;
		}

		if (isCorpAlive(corp) && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()) {

			// 🔥 HEALTH CHECK BEFORE STARTING
			int healthAtStart = MyPlayer.getCurrentHealth();
			if (healthAtStart <= INTERNAL_EMERGENCY_HP) {
				Log.warn("Low health at start of spec prep - emergency eating first");
				emergencyComboEat();
			}

			// Make inventory space with health monitoring
			if (Inventory.isFull()) {
				Log.info("Inventory is full, checking if stats are boosted");

				if (!isStatsBoosted()) {
					Log.info("Stats not boosted - drinking super combat + eating karambwan");

					if (drinkSuperCombat()) {
						Log.info("Successfully drank super combat, waiting brief moment");

						// 🔥 HEALTH CHECK DURING WAIT
						Waiting.waitUntil(300, () -> {
							int currentHealth = MyPlayer.getCurrentHealth();
							if (currentHealth < healthAtStart - 15) {
								Log.warn("Taking damage during potion wait! " + healthAtStart + " → " + currentHealth);
								emergencyComboEatDuringMovement();
							}
							return isStatsBoosted();
						});

						if (eatKarambwan()) {
							Log.info("Successfully ate karambwan for inventory space");
						}
					}
				} else {
					Log.info("Stats already boosted, just eating karambwan for space");
					eatKarambwan();
				}
			}

			// Equip spec weapon with health monitoring
			if (!isSpecWeaponEquipped()) {
				Log.info("Chosen spec weapon not equipped, attempting to equip: " + chosenSpecWeapon);

				// 🔥 HEALTH CHECK BEFORE EQUIPPING
				int healthBeforeEquip = MyPlayer.getCurrentHealth();

				if (equipSpecWeapon()) {
					specWeaponReadyForUse = true;
					Log.info("Spec weapon equipped: " + chosenSpecWeapon);

					// 🔥 HEALTH CHECK AFTER EQUIPPING
					int healthAfterEquip = MyPlayer.getCurrentHealth();
					if (healthAfterEquip < healthBeforeEquip - 20) {
						Log.warn("Lost significant health during equip! " + healthBeforeEquip + " → " + healthAfterEquip);
						emergencyComboEat();
					}

					// Pre-activate special attack
					if (!Combat.isSpecialAttackEnabled()) {
						Log.info("PRE-ACTIVATING special attack now that spec weapon is equipped");
						if (tryActivateSpec()) { // 1.9.34
							lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); corpHpAtSpec = readCorpHpPct(); // 1.9.2 + 1.9.9; 1.9.99.37 HP baseline
							specPreActivatedThisTrip = true; // 1.9.99.2
							Log.info("Special attack pre-activated successfully!");
						} else {
							Log.warn("Failed to pre-activate special attack");
						}
					} else {
						lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); corpHpAtSpec = readCorpHpPct(); // 1.9.2 + 1.9.9; 1.9.99.37 HP baseline
						specPreActivatedThisTrip = true; // 1.9.99.2
					}
				}
			} else {
				Log.info("Chosen spec weapon already equipped: " + chosenSpecWeapon);
				specWeaponReadyForUse = true;

				// Pre-activate if not already active
				if (!Combat.isSpecialAttackEnabled()) {
					Log.info("PRE-ACTIVATING special attack - weapon ready");
					if (tryActivateSpec()) { // 1.9.34
						lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); corpHpAtSpec = readCorpHpPct(); // 1.9.2 + 1.9.9; 1.9.99.37 HP baseline
						specPreActivatedThisTrip = true; // 1.9.99.2
						Log.info("Special attack pre-activated successfully!");
					}
				} else {
					lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); corpHpAtSpec = readCorpHpPct(); // 1.9.2 + 1.9.9; 1.9.99.37 HP baseline
					specPreActivatedThisTrip = true; // 1.9.99.2
				}
			}

			// 1.9.99.34: removed the post-prep "Health low after spec prep"
			// eat. Pre-1.9.99.34 we'd eat any time HP was below
			// eatHealthThreshold (maxHP-21 = 78 for a 99-HP account) right
			// before entering combat. User log: bot at HP 58 ate karambwan
			// here, then 2 seconds later got pushed UNDER Corp by Corp
			// drift while the eat animation blocked repositioning — stomp
			// defense had to bail us out. HP 58 is a safe engage range
			// (one Corp magic hit through protect-from-melee leaves us at
			// 16+); the in-combat eat logic catches any actual danger.
			// User: "we dont need to eat right after we drink our potion,
			// we can start runnign towrds the bos and eat while we run.
			// we also stepped under the boss and should have gotten
			// stomped on". Removing this eat addresses both: no wasted
			// karambwan, no animation lock at the moment Corp is most
			// likely to drift onto our tile.
		} else if (isCorpAlive(corp)) {
			// 1.9.99.224: energy < minSpec but Corp is alive — equip the
			// main weapon instead of falling through with nothing equipped.
			// Without this, prepareSpecWeaponForCorp returned silently, the
			// state machine moved on to FIGHTING_CORP, handleFightingCorp's
			// "neither weapon equipped" bounce fired, and we looped
			// ENTERING_COMBAT ↔ FIGHTING_CORP forever (~100 iterations/sec
			// in the user's 05:37:04 log). Equipping main here breaks the
			// loop; spec firing later still works once energy regens, gated
			// by shouldUseSpecialAttack in handleFightingCorp.
			Log.info("Spec energy " + Combat.getSpecialAttackPercent()
					+ "% < required " + getMinSpecEnergy()
					+ "% — skipping spec prep, equipping main weapon");
			equipMainWeaponFast();
		}
	}

    /** 1.9.99.47: opportunistic mid-combat re-pot. Called from
     *  handleFightingCorp once per tick. No-ops if stats are already
     *  boosted or no combat potion is available in inventory. Throttled
     *  to one attempt every 8s so we don't spam clicks if the SDK lags
     *  on isStatsBoosted updates. */
    private long lastRePotAttemptAt = 0;
    private void maybeReDrinkCombatPotion() {
        if (isStatsBoosted()) return;
        long now = System.currentTimeMillis();
        if (now - lastRePotAttemptAt < 8000) return;
        try {
            for (String name : getCombatPotionNames()) {
                Optional<InventoryItem> dose = Query.inventory().nameEquals(name).findFirst();
                if (dose.isPresent()) {
                    Log.info("Re-pot: stats not boosted and " + name + " available — drinking");
                    if (dose.get().click("Drink")) {
                        lastRePotAttemptAt = now;
                        Waiting.waitUntil(2000, this::isStatsBoosted);
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}
        // No combat potion in inventory — record the attempt anyway so we
        // don't keep iterating the Query every tick.
        lastRePotAttemptAt = now;
    }

    private boolean isStatsBoosted() {
        int currentAttack = Skill.ATTACK.getCurrentLevel();
        int baseAttack = Skill.ATTACK.getActualLevel();
        int currentStrength = Skill.STRENGTH.getCurrentLevel();
        int baseStrength = Skill.STRENGTH.getActualLevel();

        boolean attackBoosted = currentAttack > baseAttack;
        boolean strengthBoosted = currentStrength > baseStrength;

        // 1.9.99.219: even DEBUG-level was reaching the user's log (TRiBot
        // log level config). Drop it entirely — boosted/not-boosted is the
        // return value, no extra context needed every tick. The repot
        // path logs when it actually drinks.
        return attackBoosted || strengthBoosted;
    }

    private boolean drinkSuperCombat() {
        Log.info("Looking for combat potions in inventory...");

        String[] combatPotionNames = getCombatPotionNames();
        Log.info("Searching for: " + Arrays.toString(combatPotionNames));

        for (String potName : combatPotionNames) {
            Optional<InventoryItem> potOpt = Query.inventory().nameEquals(potName).findFirst();
            if (potOpt.isPresent()) {
                Log.info("Found " + potName + " in inventory, attempting to drink");
                if (potOpt.get().click("Drink")) {
                    Log.info("Successfully drank " + potName + " for stat boost");
                    // 1.9.99.190: trust the click. waitUntil polling for
                    // isStatsBoosted spammed ~60 "Stat check" log lines per
                    // drink while waiting up to 3s for the boost flag to
                    // flip. With Corp wandering during that 3s, the bot's
                    // position pick (computed right after) was stale by
                    // the time the bot got there → circle overlays drawn
                    // at the wrong spot. One tick is enough for the click
                    // to register; downstream code can re-check stats as
                    // needed.
                    Waiting.wait(700);
                    return true;
                } else {
                    Log.error("Failed to interact with " + potName);
                }
            } else {
                Log.debug("No " + potName + " found in inventory");
            }
        }

        // Log entire inventory contents for debugging
        Log.error("No super combat potions found. Current inventory:");
        Query.inventory().stream().forEach(item ->
                Log.info("  - " + item.getName()));

        return false;
    }

    private boolean eatKarambwan() {
        Log.info("Looking for Cooked karambwan in inventory...");

        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent()) {
            Log.info("Found Cooked karambwan, attempting to eat");
            if (karambwanOpt.get().click("Eat")) {
                Log.info("Successfully ate Cooked karambwan to make inventory space");

                // Wait for inventory to update
                Waiting.waitUntil(2000, () -> !Inventory.isFull());
                return true;
            } else {
                Log.error("Failed to interact with Cooked karambwan");
            }
        } else {
            Log.error("No Cooked karambwan found in inventory");

            // Log inventory for debugging
            Log.info("Current inventory contents:");
            Query.inventory().stream().forEach(item ->
                    Log.info("  - " + item.getName()));
        }

        return false;
    }

	private boolean withdrawSuperCombat() {
		int currentDoses = getSuperCombatDoses();
		if (currentDoses >= 2) {
			Log.info("Already have enough combat-potion doses: " + currentDoses);
			return true; // Have enough doses
		}

		// 1.9.99.165: same fix as 1.9.99.149 for jewellery. If we have any
		// PARTIAL-dose combat potions in inventory (e.g. (1) or (2) from a
		// previous trip we didn't drink), deposit them BEFORE withdrawing
		// the fresh (4). Pre-1.9.99.165 we'd end up with both a (1) AND a
		// (4) eating two inventory slots for 5 doses when one (4) is all
		// we need. User: "the fix we did for low charge jewlery should
		// also apply to potions".
		String potionBase = getCombatPotionType() + " potion";
		for (int dose = 1; dose <= 3; dose++) {
			String partial = potionBase + "(" + dose + ")";
			try {
				if (Inventory.getCount(partial) > 0) {
					Log.info("Depositing partial-dose " + partial
							+ " before topping up combat potion");
					Bank.deposit(partial, 0); // all
					Waiting.waitNormal(250, 80);
				}
			} catch (Exception ignored) {}
		}

		String fourDosePotion = potionBase + "(4)";
		Log.info("Need combat potion - current doses: " + currentDoses + ", withdrawing " + fourDosePotion);
		return Bank.withdraw(fourDosePotion, 1);
	}

	private int getSuperCombatDoses() {
		int totalDoses = 0;
		for (String potName : getCombatPotionNames()) {
			int count = Inventory.getCount(potName);
			if (potName.contains("(4)")) totalDoses += count * 4;
			else if (potName.contains("(3)")) totalDoses += count * 3;
			else if (potName.contains("(2)")) totalDoses += count * 2;
			else if (potName.contains("(1)")) totalDoses += count * 1;
		}
		Log.debug("Total super combat doses: " + totalDoses);
		return totalDoses;
	}

    private boolean withdrawSuperRestores() {
        // 1.9.99.165: deposit partial-dose Super restores before topping up.
        // Same fix as combat potions (above) and jewellery (1.9.99.149) —
        // a (1) or (2) sitting in inventory wastes a slot once a fresh (4)
        // gets withdrawn. Deposit them first.
        for (int dose = 1; dose <= 3; dose++) {
            String partial = "Super restore(" + dose + ")";
            try {
                if (Inventory.getCount(partial) > 0) {
                    Log.info("Depositing partial-dose " + partial
                            + " before topping up super restores");
                    Bank.deposit(partial, 0);
                    Waiting.waitNormal(250, 80);
                }
            } catch (Exception ignored) {}
        }

        int needed = 2 - Inventory.getCount(SUPER_RESTORE_NAMES);
        if (needed <= 0) return true;

        Log.info("Withdrawing " + needed + " Super Restore Potions");
        return Bank.withdraw("Super restore(4)", needed);
    }

    private boolean withdrawKarambwans(int amount) {
        if (amount == 0) {
            Log.info("Withdrawing all remaining Karambwans");
            return Bank.withdraw("Cooked karambwan", 0); // Withdraw all
        } else {
            Log.info("Withdrawing " + amount + " Karambwans");
            return Bank.withdraw("Cooked karambwan", amount);
        }
    }

    private boolean withdrawSharks(int amount) {
        if (amount == 0) {
            Log.info("Filling remaining inventory with Sharks");
            return Bank.withdraw("Shark", 0); // Withdraw all
        } else {
            Log.info("Withdrawing " + amount + " Sharks");
            return Bank.withdraw("Shark", amount);
        }
    }

    /**
     * Withdraw highest charged version of an item (e.g., Ring of dueling(8) -> Ring of dueling(1))
     */
    private boolean withdrawHighestChargedItem(String itemBaseName, int maxCharges) {
        Log.info("Withdrawing highest charged " + itemBaseName);

        // 1.9.99.149: deposit ANY existing instances of this baseName before
        // withdrawing a fresh charged one. Pre-1.9.99.149 a (1)-charge
        // jewellery sitting in inventory was the "highest dose" so
        // depositLowerDoseJewelry never deposited it; the subsequent
        // withdraw then either failed (no inv space → stuck banking
        // loop) or left both items in inventory wasting a slot.
        for (int dose = 1; dose <= maxCharges; dose++) {
            String name = itemBaseName + "(" + dose + ")";
            try {
                if (Inventory.getCount(name) > 0) {
                    Log.info("Depositing existing " + name + " before topping up");
                    Bank.deposit(name, 0); // 0 = all
                    Waiting.waitNormal(250, 80);
                }
            } catch (Exception ignored) {}
        }

        for (int charges = maxCharges; charges >= 1; charges--) {
            String chargedItemName = itemBaseName + "(" + charges + ")";

            if (Bank.getCount(chargedItemName) > 0) {
                Log.info("Found " + chargedItemName + " in bank, withdrawing");
                if (Bank.withdraw(chargedItemName, 1)) {
                    Waiting.waitUntil(2000, () -> Inventory.contains(chargedItemName));
                    return true;
                }
            }
        }

        Log.error("No charged " + itemBaseName + " found in bank");
        return false;
    }

    /**
     * Check if we have a charged Ring of Dueling
     */
    private boolean hasChargedRingOfDueling() {
        return getHighestJewelryDose("Ring of dueling", 8) > 0;
    }

    /**
     * Check if we have a charged Games Necklace
     */
    private boolean hasChargedGamesNecklace() {
        return getHighestJewelryDose("Games necklace", 8) > 0;
    }

    /** Highest dose count of `baseName(N)` currently in inventory, 0 if none. */
    private int getHighestJewelryDose(String baseName, int maxDose) {
        for (int charges = maxDose; charges >= 1; charges--) {
            if (Inventory.contains(baseName + "(" + charges + ")")) {
                return charges;
            }
        }
        return 0;
    }

    /** True if our highest-dose jewelry is below the trip floor. We need
     *  charges to actually return mid-trip (Ferox -> Corp via necklace, Corp ->
     *  Ferox via ring). A (1)-charge ring sometimes passes hasChargedX but
     *  doesn't survive the next trip — top up at the bank. */
    private static final int JEWELRY_TOP_UP_THRESHOLD = 4;

    private boolean ringOfDuelingNeedsTopUp() {
        return getHighestJewelryDose("Ring of dueling", 8) < JEWELRY_TOP_UP_THRESHOLD;
    }

    private boolean gamesNecklaceNeedsTopUp() {
        return getHighestJewelryDose("Games necklace", 8) < JEWELRY_TOP_UP_THRESHOLD;
    }

    // Update the food handling in main loop
    private void handleHealthAndPrayer() {
        // 1.9.62: skip eating/prayer when we're en-route to the restoration
        // pool. Pool drink restores HP and prayer to FULL — eating right
        // before it is wasted food. User: 'i just noticed that sometimes
        // when spec dumping the boss even though we are going to the
        // house to restore stats we will eat before entering the house.'
        // Combat states (FIGHTING_CORP / USING_SPECIAL_ATTACK / USING_INITIAL_SPECS)
        // still eat because Corp is hitting us during spec dump; once we
        // tele out (TELEPORTING_TO_HOUSE+) there's no further damage and
        // the next pool drink will top us off anyway.
        if (currentState == BotState.TELEPORTING_TO_HOUSE
                || currentState == BotState.ENTERING_FRIEND_HOUSE
                || currentState == BotState.USING_ORNATE_POOL
                || currentState == BotState.TELEPORTING_BACK_TO_CORP
                || currentState == BotState.BANKING_AND_HEALING
                || currentState == BotState.W330_RESTORATION
                || currentState == BotState.LOOTING
                // 1.9.99.103: PREPARING_RESTORATION_CYCLE also skips
                // eats — we're seconds away from tele'ing to POH where
                // the ornate pool restores HP to full. Pre-1.9.99.103
                // the bot routinely ate a karambwan right before
                // teleing because eatHealthThreshold (HP < ~78) fires
                // during this brief prep window. User log 09:56:07:
                // "Ate Karambwan (normal)" then 09:56:08 "Teleporting
                // to house for restoration cycle 1".
                || currentState == BotState.PREPARING_RESTORATION_CYCLE) {
            // 1.9.99.86: LOOTING also skips eat/prayer maintenance.
            // After Corp dies we're heading to bank or POH next anyway —
            // food/prayer/spec will refresh there. Eating during loot
            // pickup just consumes food we'd otherwise keep for the next
            // kill. User: "if corp died we dont need to repot or use
            // food or anything because we are going to bank/poh and
            // get full spec."
            return;
        }

        int currentHealth = MyPlayer.getCurrentHealth();

        // 1.9.72: ALSO skip the normal-eat threshold when restoration is
        // pending. User: 'sometimes when we are in the spec dump phase
        // it still eats at pretty high hp before teleporting out. like
        // i can be 70 hp and then ill eat to 90 and then teele out.'
        // 1.9.62 only skipped during the TELEPORTING_TO_HOUSE+ states;
        // but there's a window in FIGHTING_CORP/USING_SPECIAL_ATTACK
        // where the bot has decided to restore (shouldStartRestorationCycle
        // = true) but hasn't transitioned yet. During that window
        // handleHealthAndPrayer fires a normal eat that the pool would
        // overwrite. Combo eat (HP <= 50 = panic) still runs because
        // we might die before getting to the pool.
        boolean restorationPending = false;
        try { restorationPending = shouldStartRestorationCycle(); } catch (Exception ignored) {}

        // 1.9.87: 'spec dump phase' = spec weapon equipped, not just
        // 'restoration pending'. User: 'i feel like it was still eating
        // when it should just be specing and teleporting out.' Log
        // showed eating at 02:16:22 (between spec 1 and timed-out
        // spec 2 — energy 50%, restorationPending = false because
        // spec 2 was about to fire and reach 0%). The eat was wasteful
        // because either spec 2 succeeds → tele soon → pool restores,
        // or spec 2 times out → still going to tele eventually. Either
        // way the eat gets overwritten.
        // 1.9.99.25: but only consider us 'in spec dump' when the state
        // is actually USING_SPECIAL_ATTACK. Pre-1.9.99.25 the spec-weapon-
        // equipped check caught the entire approach phase too (Elder maul
        // equipped during walk from lobby to Corp), preventing the bot
        // from panic-eating during the dangerous 9s walk while Corp
        // freely landed mage + melee through prayer. User: "we just ran
        // up and died instead of panic eating".
        // 1.9.99.84: extended the "in spec dump" window to include
        // FIGHTING_CORP when we still have energy for another spec AND
        // phase targets aren't complete. Pre-1.9.99.84 the bot ate
        // normal-threshold food between consecutive specs because state
        // briefly flipped from USING_SPECIAL_ATTACK back to FIGHTING_CORP
        // for ~600ms while the spec swing resolved — handleHealthAndPrayer
        // ran ~12 times in that window and fired a normal eat when HP was
        // below eatHealthThreshold. The user runs in, double-specs, TPs
        // out manually with no food eaten. We were inserting normal eats
        // that broke the rhythm. Now: as long as spec weapon equipped +
        // energy >= floor + phase target incomplete, we treat the whole
        // 2-spec window as one spec-dump cycle and skip non-emergency
        // eats. Critical-HP eats (HP <= INTERNAL_PANIC_TELE_HP) still
        // fire — those are unconditional in handleHealthAndPrayer below.
        // The 1.9.99.25 walk-in regression is avoided because we ALSO
        // require currentState to be one of the combat states
        // (FIGHTING_CORP / USING_SPECIAL_ATTACK), not the approach states.
        // User: "the account im playing on manually run in and double
        // spec and tp out without needing to eat food usually ... however
        // our bot keeps eating and usually ends up doing a normal attack
        // inbetween our eating/spec dumping."
        boolean inSpecDumpCycle = isSpecWeaponEquipped()
                && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()
                && teamPhaseNeeded() > 0
                && (currentState == BotState.USING_SPECIAL_ATTACK
                    || currentState == BotState.FIGHTING_CORP);
        // 1.9.99.111: USING_INITIAL_SPECS is a bridge state between
        // PREPARING_RESTORATION_CYCLE and TELEPORTING_TO_HOUSE — we fire
        // any remaining spec(s) then tele. Pool restores HP on arrival,
        // so the combo-eat threshold (HP <= 50) fires wastefully here.
        // Skip non-critical eats; the unconditional panic-tele branch
        // (HP <= effectivePanicHp, default 35) below still fires if HP
        // truly gets critical. User log: spec depleted -> "Preparing
        // restoration cycle" -> "EMERGENCY: Combo eating Shark +
        // Karambwan" -> panic retreat, all in 1.5s right before tele.
        // User: "why am i combo eating here instead of just double
        // specing and teleporting?"
        boolean inInitialSpecsBridge = (currentState == BotState.USING_INITIAL_SPECS);
        boolean shouldSkipEats = restorationPending || inSpecDumpCycle || inInitialSpecsBridge;
        // 1.9.99.205-revised: do NOT override shouldSkipEats globally. The
        // preemptive big-hit eat happens BEFORE this method via the
        // dedicated approach-eat branch in the main loop — only while
        // moving and >7 tiles from Corp. Inside spec dump range, the
        // skip-eats rule still holds as before.
        boolean recentBigHit = lastBigHitAt > 0
                && (System.currentTimeMillis() - lastBigHitAt) < BIG_HIT_EAT_WINDOW_MS;

        // 1.9.99.41: during an active dark-core engagement (core seen in
        // the last 5s) force every eat to be a combo eat. The core
        // ticks for 8-12 damage per game tick on top of whatever Corp
        // hits — a single karambwan (+18 HP) doesn't keep up. User:
        // "we are taking a garenteed 8-12 health a tick + whatever corp
        // tiself does to us ... if the core is on us and we are going
        // to tyr to kill it that every eat is a combo eat during that
        // duration if possible".
        boolean coreEngaged = darkCoreLastSeen > 0
                && (System.currentTimeMillis() - darkCoreLastSeen) < 5000;

        // 1.9.99.72: spiral detection. Track the last emergency-eat
        // timestamp.
        // 1.9.99.74: panic retreat now triggers on the FIRST emergency
        // eat (not just back-to-back). Spec dumps are short — if we hit
        // the emergency threshold even once, kiting failed and we
        // should step off immediately. Plus: if karams are gone, an
        // emergency eat can't combo (shark-only = ~20 HP, can't keep
        // up with Corp's burst) — insta-tele instead. User: "we could
        // probably change the panic eat requirement down to just 1
        // panic eat ... so if we need to combo eat and we're out of
        // kawambwans we can just insta tele."
        // 1.9.99.89: during spec dump, use settings.specDumpPanicTeleHp
        // (default 35) as the panic threshold instead of the general
        // INTERNAL_PANIC_TELE_HP (25). First-spec windows on
        // Arclight/Darklight see HP drops into the 30-50 range before
        // Corp's stats are reduced — the lower base 25 left us in the
        // danger zone too long. Combo-eat-at-50 stays skipped via
        // shouldSkipEats; the new 35 threshold catches us BEFORE the
        // 25 fallback fires.
        int effectivePanicHp = inSpecDumpCycle
                ? settings.specDumpPanicTeleHp
                : INTERNAL_PANIC_TELE_HP;
        boolean willEmergencyEat = currentHealth <= effectivePanicHp
                || (currentHealth <= INTERNAL_COMBO_EAT_HP && !shouldSkipEats);

        if (willEmergencyEat) {
            long now = System.currentTimeMillis();
            int karamCount = Inventory.getCount("Cooked karambwan");
            int sharkCount = Inventory.getCount("Shark");
            // 1.9.99.104: in kill phase Corp is fully debuffed and hits
            // soft — shark-only eating (~20 HP) keeps up with damage.
            // Only insta-tele on karam=0 BEFORE kill phase, when Corp's
            // hits are full-strength and shark alone can't outheal them.
            // User: "after weve finished dumping specs corp is so weak
            // we wont ever need to combo eat again. so just having
            // sharks is good enough and we keep banking in the last
            // phase. because we are out of combo eats."
            boolean killPhaseSharkOnly = isInKillPhase() && sharkCount >= 3;
            if (karamCount == 0 && !killPhaseSharkOnly) {
                Log.warn("EMERGENCY: HP " + currentHealth
                        + " <= combo threshold AND no karambwans (not kill phase OR sharks low) "
                        + "— insta-tele to safety");
                currentState = BotState.EMERGENCY_ESCAPE;
                lastEmergencyEatAt = now;
                return;
            }
            if (karamCount == 0 && killPhaseSharkOnly) {
                Log.info("EMERGENCY in kill phase, no karams but " + sharkCount
                        + " sharks — riding shark-only (Corp is debuffed, ~20 HP/eat sufficient)");
            }
            emergencyComboEat();
            lastEmergencyEatAt = now;
            if (inSpecDumpCycle) {
                // 1.9.99.88: mid spec-dump emergency eat → tele out.
                // Restored to pre-1.9.99.205 behavior: any emergency eat
                // during the spec dump means we lost control of HP, abandon
                // and retreat. The preemptive eat (approach-time, far from
                // Corp) is handled separately and doesn't reach this path.
                Log.warn("EMERGENCY: mid spec-dump eat fired (HP="
                        + currentHealth + ") — abandoning specs, tele to safety");
                currentState = BotState.EMERGENCY_ESCAPE;
                return;
            }
            if (!coreEngaged) {
                // Skip during core engagement — stepAwayFromCore owns
                // movement there and the 5-tile retreat would conflict.
                Log.warn("PANIC-RETREAT: emergency eat fired (HP="
                        + currentHealth + ") — stepping off Corp");
                if (panicRetreatFromCorp()) {
                    panicRetreatActiveUntil = now + PANIC_RETREAT_PARK_MS;
                }
            }
        } else if (!shouldSkipEats && currentHealth <= eatHealthThreshold()) {
            if (coreEngaged) {
                emergencyComboEat();
                lastEmergencyEatAt = System.currentTimeMillis();
            } else {
                normalEat();
            }
        }

        if (!shouldSkipEats && Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD) {
            drinkPrayerPotion();
        }
    }

    /**
     * Emergency combo eating - Shark + Karambwan in quick succession
     */
    private boolean emergencyComboEat() {
        Log.info("EMERGENCY: Combo eating Shark + Karambwan!");

        boolean ateShark = false;
        boolean ateKarambwan = false;

        // Step 1: Eat shark first. 1.9.11.1: capture the shark's slot index
        // so we can pick the nearest karambwan slot afterward (minimizes
        // mouse travel for the combo follow-up).
        int sharkSlot = -1;
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        if (sharkOpt.isPresent()) {
            sharkSlot = sharkOpt.get().getIndex();
        }
        if (sharkOpt.isPresent() && sharkOpt.get().click("Eat")) {
            ateShark = true;
            Log.info("Emergency: Ate Shark");

            // 1.9.10: tightened from 100-300ms. Karambwan in OSRS bypasses
            // the standard eat cooldown so both heals can land in the SAME
            // tick — but only if the karambwan click happens fast. The
            // pre-1.9.10 wait often pushed karambwan into the next tick,
            // doubling the time-to-full-heal. 40-80ms is long enough for
            // TRiBot's click pipeline to flush but short enough to keep
            // both eats in one game tick.
            Waiting.waitUniform(40, 80);
        }

        // Step 2: Eat karambwan immediately after.
        // 1.9.11.1: pick the karambwan whose slot index is closest to the
        // shark slot. After the shark click the cursor sits over that slot,
        // so the nearest karambwan minimizes mouse travel — helps both
        // eats land in the same game tick. Falls back to naive pick if we
        // didn't capture the shark slot.
        Optional<InventoryItem> karambwanOpt = (sharkSlot >= 0)
                ? pickClosestKarambwanToSlot(sharkSlot)
                : Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent() && karambwanOpt.get().click("Eat")) {
            ateKarambwan = true;
            Log.info("Emergency: Ate Karambwan");
        }

        if (ateShark || ateKarambwan) {
            // Wait briefly to see health increase
            Waiting.waitUntil(2000, () ->
                    MyPlayer.getCurrentHealth() > INTERNAL_EMERGENCY_HP);
            return true;
        }

        Log.error("CRITICAL: No food available for emergency eating!");
        return false;
    }

    /**
     * Normal eating - Sharks preferred, Karambwans as fallback
     */
    private boolean normalEat() {
        // 1.9.74: karambwan FIRST for normal eats, shark fallback. User:
        // 'we should also use karamwans as our main food and use sharks
        // mostly for combo eating.' Keeps shark stock available for the
        // emergencyComboEat path (shark + karambwan in same tick = 38 HP)
        // which is what saves us in dark-core / panic situations.
        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent() && karambwanOpt.get().click("Eat")) {
            Log.info("Ate Karambwan (normal)");
            return waitForHealthIncrease();
        }

        // Fallback to shark if no karambwans
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        if (sharkOpt.isPresent() && sharkOpt.get().click("Eat")) {
            Log.info("Ate Shark (fallback - no karambwans)");
            return waitForHealthIncrease();
        }

        // Priority 3: Try manta ray as last resort
        Optional<InventoryItem> mantaOpt = Query.inventory().nameEquals("Manta ray").findFirst();
        if (mantaOpt.isPresent() && mantaOpt.get().click("Eat")) {
            Log.info("Ate Manta ray (last resort)");
            return waitForHealthIncrease();
        }

        Log.warn("No food available for normal eating");
        return false;
    }

    /**
     * Wait for health to increase after eating
     */
    private boolean waitForHealthIncrease() {
        int healthBefore = MyPlayer.getCurrentHealth();

        return Waiting.waitUntil(3000, () ->
                MyPlayer.getCurrentHealth() > healthBefore);
    }

    /**
     * Emergency combo eating - Shark + Karambwan in quick succession
     */

    /**
     * Check if we have food available (updated to be more specific)
     */
    private boolean hasFood() {
        return Query.inventory().nameEquals("Shark").findFirst().isPresent() ||
                Query.inventory().nameEquals("Cooked karambwan").findFirst().isPresent() ||
                Query.inventory().nameEquals("Manta ray").findFirst().isPresent();
    }

    /**
     * Check if we have emergency combo food available
     */
    private boolean hasEmergencyFood() {
        return Query.inventory().nameEquals("Shark").findFirst().isPresent() ||
                Query.inventory().nameEquals("Cooked karambwan").findFirst().isPresent();
    }

    private boolean drinkPrayerPotion() {
        for (String potName : SUPER_RESTORE_NAMES) {
            Optional<InventoryItem> potOpt = Query.inventory().nameEquals(potName).findFirst();
            if (potOpt.isPresent()) {
                InventoryItem prayerPot = potOpt.get();
                int currentPrayer = Prayer.getPrayerPoints();

                if (prayerPot.click("Drink")) {
                    boolean prayerIncreased = Waiting.waitUntil(3000, () ->
                            Prayer.getPrayerPoints() > currentPrayer);

                    if (prayerIncreased) {
                        Log.info("Successfully drank " + potName);
                        return true;
                    }
                }
                break;
            }
        }
        return false;
    }

    private boolean shouldEmergencyEscape() {
        // 1.9.43: if we're already safely OUT of the boss room or in a
        // safe-state transition (banking/emergency/teleporting/etc.),
        // don't re-trigger emergency escape. Pre-1.9.43 the trigger fired
        // every tick on "out of food + HP < 75"; after the first tele to
        // Ferox the bot was still out of food (banking step hadn't run
        // yet) and shouldEmergencyEscape re-fired, forcing another
        // Ring-of-Dueling tele. The user watched it burn an entire
        // ring's worth of charges in seconds.
        try { if (isAtFeroxEnclave()) return false; } catch (Exception ignored) {}
        if (currentState == BotState.EMERGENCY_ESCAPE
                || currentState == BotState.BANKING_AND_HEALING
                || currentState == BotState.STARTING
                || currentState == BotState.DEATH_RECOVERY
                || currentState == BotState.W330_RESTORATION) {
            return false;
        }

        if (startedFightingWithTeammates) {
            // Only escape if out of food AND health < 75
            boolean outOfFood = Inventory.getCount(settings.foodNames) == 0;
            boolean lowHealth = MyPlayer.getCurrentHealth() < 75;

            if (outOfFood && lowHealth) {
                Log.info("Emergency escape: Started with teammates but now out of food and HP < 75");
                return true;
            }

            // Don't escape just because teammates left
            return false;
        }

        // 1.9.22: removed "teammate not visible" from emergency triggers.
        // Teammates can briefly walk out of render or be obscured during
        // combat — that's not an emergency. Mid-fight spec dumping should
        // continue regardless of teammate visibility. The remaining
        // triggers (low HP, no food, no prayer) cover the actual danger
        // cases.
        boolean originalEmergency = MyPlayer.getCurrentHealth() <= INTERNAL_EMERGENCY_HP ||
                (Inventory.getCount(settings.foodNames) == 0 && isPlayerInCombat()) ||
                (Prayer.getPrayerPoints() == 0 && getPrayerDoses() == 0 && isPlayerInCombat());

        // Additional check: Dark core emergency
        if (isDarkCorePresent()) {
            Optional<Npc> coreOpt = findDarkCore();
            if (coreOpt.isPresent()) {
                WorldTile myPos = MyPlayer.getTile();
                WorldTile corePos = coreOpt.get().getTile();
                double coreDistance = myPos.distanceTo(corePos);

                // Emergency if core is on us and we can't move
                if (coreDistance <= 1 && !hasFood() && MyPlayer.getCurrentHealth() <= 50) {
                    Log.warn("Dark core emergency: Core on us with low health and no food");
                    return true;
                }
            }
        }

        return originalEmergency;
    }

    private boolean hasRequiredItems() {
        return hasAnyOwnedSpecWeapon() &&
                (Inventory.contains(RUNE_POUCH) || Inventory.contains(DIVINE_RUNE_POUCH)) &&
                hasChargedRingOfDueling() &&
                hasChargedGamesNecklace() &&
                Inventory.getCount(getCombatPotionNames()) >= INTERNAL_TARGET_SUPER_COMBAT &&
                Inventory.getCount(SUPER_RESTORE_NAMES) >= INTERNAL_TARGET_SUPER_RESTORES &&
                hasMinimumFood();
    }

    /**
     * Check if we need to resupply after a kill (different threshold)
     */
    private boolean needsResupplyAfterKill() {
        // 1.9.99.168: also resupply when house tabs run low. Pre-1.9.99.168
        // the bot would stop POH'ing entirely once it ran out of tabs
        // (shouldStartRestorationCycle returned false on hasHouseTabs())
        // but no resupply trigger fired — bot stayed at Corp on whatever
        // spec it had and slowly died. User: "do bots bank if they run
        // out of poh tabs" — they didn't. Now they do.
        return Inventory.getCount(settings.foodNames) < INTERNAL_MIN_FOOD_COUNT ||
                getPrayerDoses() < INTERNAL_MIN_PRAYER_DOSES ||
                (!hasChargedGamesNecklace() || !hasChargedRingOfDueling()) ||
                Inventory.getCount("Teleport to house") < INTERNAL_HOUSE_TAB_REFILL_BELOW;
    }

    // ========== UTILITY METHODS ==========

    private boolean isAtCorp() {
        // Check if we're anywhere in the Corp area (lobby or boss room)
        return isInCorpLobby() || isInCorpBossRoom();
    }

    private boolean hasAcceptableTeammatesNearby() {
        // 1.9.90: null/empty-guard list and exclude self — bot's own name shouldn't count as a teammate.
        if (settings == null || settings.acceptableTeammates == null
                || settings.acceptableTeammates.isEmpty()) {
            return false;
        }
        final String self = MyPlayer.getUsername();
        return Query.players()
                .stream()
                .anyMatch(player -> !player.getName().equals(self)
                        && settings.acceptableTeammates.contains(player.getName()));
    }

    /**
     * Check for acceptable teammates with grace period for disconnections
     */
    private boolean hasAcceptableTeammatesWithGracePeriod() {
        // If we currently see teammates, we're good
        if (hasAcceptableTeammatesNearby()) {
            return true;
        }

        // If we haven't seen teammates recently, check grace period
        long timeSinceLastSeen = System.currentTimeMillis() - lastTeammateSeenTime;
        return timeSinceLastSeen < TEAMMATE_GRACE_PERIOD_MS;
    }

    /** 1.9.99.118: gate dark-core handling on whether the core ACTUALLY
     *  threatens us. Pre-1.9.99.118 we transitioned to HANDLING_DARK_CORE
     *  the moment ANY core existed in render — even when a teammate was
     *  tanking it 7 tiles away. That left the bot stuck in core handling
     *  for ~90s, standing on Corp with the wrong weapon. User: "IF the
     *  core it outside of our hitbox why would that break US?"
     *
     *  Detection caveat: the dark core doesn't TARGET a character like
     *  a normal NPC — it picks a GROUND TILE to jump to (user: "it
     *  doesnt attack you specificalyl it jumps to a ground location
     *  where you are"). So getInteractingCharacter() probably returns
     *  empty. The real signal is DISTANCE — if the core is within 2
     *  tiles, it's either landed on us or imminently about to. We try
     *  the interacting-character check first as a soft signal (if the
     *  SDK happens to expose anything useful) and fall back to distance. */
    private boolean isDarkCoreThreatening() {
        Optional<Npc> coreOpt = findDarkCore();
        if (!coreOpt.isPresent()) return false;
        Npc core = coreOpt.get();
        // Soft signal: maybe targeting us (probably empty for cores).
        try {
            Optional<org.tribot.script.sdk.interfaces.Character> target =
                    core.getInteractingCharacter();
            if (target.isPresent()) {
                String targetName = target.get().getName();
                if (targetName != null
                        && targetName.equalsIgnoreCase(MyPlayer.getUsername())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        // Primary signal: distance to core's current tile.
        // 1.9.99.123: tightened 2.0 → 1.5. The dark core's hitbox is its
        // center tile + 1 tile out in each direction (a 3x3 footprint).
        // Euclidean dist <= 1.5 captures everything inside that footprint
        // (cardinal-1 = 1.0, diagonal-1 = 1.41). Cardinal-2 (2.0) and
        // beyond are OUTSIDE the hitbox — core can't touch us there.
        // Pre-1.9.99.123 threshold of 2.0 false-positive'd at 1 tile
        // beyond hitbox (e.g. 2.5 tiles diagonal), forcing us into
        // HANDLING_DARK_CORE while a teammate had the core stunned.
        // User: "if its 2.5 tiles away diagonalyl it isnt touching us
        // but we detect ti as being there. its hitbox is its center
        // tile and 1 tile out in each direction."
        try {
            WorldTile myPos = MyPlayer.getTile();
            if (myPos == null) return false;
            return myPos.distanceTo(core.getTile()) <= 1.5;
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean isDarkCorePresent() {
        // Try the exact name that works for your server
        Optional<Npc> coreOpt = Query.npcs().nameEquals(DARK_CORE).findFirst();
        if (coreOpt.isPresent()) {
            return true;
        }

        // Fallback detection methods
        List<String> alternativeNames = Arrays.asList(
                "Core", "dark core", "Dark Core", "Summoning spirit"
        );

        for (String name : alternativeNames) {
            if (Query.npcs().nameEquals(name).findFirst().isPresent()) {
                Log.info("Dark core detected with alternative name: " + name);
                return true;
            }
        }

        return false;
    }

    private boolean shouldUseSpecialAttack() {
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy() || !isPlayerInCombat()) {
            return false;
        }
        // 1.9.30: dropped the Corp-HP gate from this check (the 1.9.30
        // comment is preserved below for context).
        // 1.9.99.87: RE-ADDED a kill-phase gate per user request: "if we
        // detect that the bosses health is under 1700 we stop spec
        // dumping and just participate in the kill." Once Corp is below
        // the spec-floor (corpMinHpForSpec, default 1700) or our team's
        // phase quotas are met (teamPhaseNeeded == 0), stop firing specs
        // — Fang melee carries from here. The 1.9.30 regression (skipping
        // phase-1 spec #4 when Corp was already low) is acceptable to the
        // user; they prefer faster melee finishes over chasing the last
        // phase spec when Corp is dying.
        if (isInKillPhase()) {
            return false;
        }
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return false;

        // 1.9.43: also gate on having a usable spec weapon for the
        // currently-needed team phase. Pre-1.9.43 this returned true the
        // moment energy was available, even at phase 3 with no BGS
        // owned. handleSpecialAttack would then refreshSpecWeaponForPhase
        // -> null -> "No usable spec weapon for current team phase -
        // falling through to DPS" and return to FIGHTING_CORP. The
        // pre-activate check would re-arm spec, shouldUseSpecialAttack
        // re-triggered, state churn looped 30+ times/second. User log
        // showed the bot in this loop right before the dark core spawn.
        String phaseWeapon = pickSpecWeaponForCurrentPhase();
        if (phaseWeapon == null) return false;
        // 1.9.99.110: don't enable spec if the currently-equipped weapon
        // isn't the one needed for this phase. Activating spec on the
        // wrong weapon, then swapping to the right one, toggles the spec
        // bar OFF (OSRS quirk: weapon swap clears the queued spec). The
        // bot wasted the click and the next swing didn't spec. User:
        // "we could enable spec preemptively; then its time for us to
        // move on to the next weapon for spec dumping; so we take our
        // new weapon out and now our spec is no longer enabled because
        // switching weapons turns our spec off." Hold off on activating
        // until the swap completes, then re-arm cleanly.
        if (!Equipment.contains(phaseWeapon)) return false;
        return true;
    }

    /**
     * Updated Elder Maul check - now checks if Elder Maul is our chosen weapon and equipped
     */
    private boolean isElderMaulEquipped() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

        // Only return true if Elder Maul is both our chosen weapon AND equipped
        return ELDER_MAUL.equals(chosenSpecWeapon) && Equipment.contains(ELDER_MAUL);
    }

    /**
     * Updated Elder Maul equip - now equips whichever spec weapon was chosen
     */
    private boolean equipElderMaul() {
        // This method now just calls the generic spec weapon equip
        return equipSpecWeapon();
    }

    private boolean isPlayerInCombat() {
        boolean currentlyAnimating = MyPlayer.isAnimating();
        if (currentlyAnimating) {
            lastCombatTime = System.currentTimeMillis();
            wasInCombat = true;
            return true;
        }

        boolean recentCombat = (System.currentTimeMillis() - lastCombatTime) < 6000;
        if (!recentCombat) {
            wasInCombat = false;
        }

        return recentCombat && wasInCombat;
    }

    private boolean isNpcInCombat(Npc npc) {
        return npc.isHealthBarVisible();
    }

    // ========== AUTO SPEC-WEAPON DETECTION (1.8.0) ==========
    // Replaces the hand-maintained settings.availableSpecWeapons checkbox map.
    // Scans equipment + inventory + bank (when open) and caches the result.

    private List<String> ownedSpecWeaponsCache = null;

    /** Scan equip + inv + (bank if open) for each name in ALL_SPEC_WEAPONS,
     *  return the list we actually own. Cached until invalidate() is called. */
    private List<String> getOwnedSpecWeapons() {
        if (ownedSpecWeaponsCache != null) return ownedSpecWeaponsCache;
        List<String> found = new ArrayList<>();
        for (String w : ALL_SPEC_WEAPONS) {
            boolean have = Inventory.contains(w) || Equipment.contains(w);
            if (!have) {
                try {
                    if (Bank.isOpen() && Bank.getCount(w) > 0) have = true;
                } catch (Exception ignored) {}
            }
            if (have) found.add(w);
        }
        // Fall back to legacy settings if we somehow detected nothing — e.g.,
        // detection running before bank-open at script start.
        if (found.isEmpty() && settings != null && settings.availableSpecWeapons != null) {
            for (Map.Entry<String, Boolean> e : settings.availableSpecWeapons.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) found.add(e.getKey());
            }
        }
        ownedSpecWeaponsCache = found;
        Log.debug("Owned spec weapons (detected): " + found);
        return found;
    }

    /** Drop the cache so the next getOwnedSpecWeapons() call re-scans. Called
     *  after every bank trip and on script start. */
    private void invalidateOwnedSpecWeaponsCache() {
        ownedSpecWeaponsCache = null;
    }

    /** 1.9.11: pick the Cooked karambwan whose inventory slot index is
     *  closest to the given shark slot. The inventory is a 4-wide grid, so
     *  slot-index proximity correlates with on-screen distance (an exact
     *  Manhattan distance over (idx/4, idx%4) would be marginally better
     *  but |a-b| is a good enough proxy and avoids the awkward grid math).
     *  After clicking shark the cursor sits over that slot — eating the
     *  nearest karambwan keeps the second click on the same row/column and
     *  helps both eats land in the same game tick.
     *  Mouse.getPosition() isn't exposed in the public SDK, so we can't do
     *  literal cursor-distance; slot proximity is the best available proxy. */
    private Optional<InventoryItem> pickClosestKarambwanToSlot(int referenceSlot) {
        List<InventoryItem> candidates = Query.inventory()
                .nameEquals("Cooked karambwan").toList();
        if (candidates.isEmpty()) return Optional.empty();
        InventoryItem best = candidates.get(0);
        int bestDist = Math.abs(best.getIndex() - referenceSlot);
        for (InventoryItem item : candidates) {
            int d = Math.abs(item.getIndex() - referenceSlot);
            if (d < bestDist) {
                bestDist = d;
                best = item;
            }
        }
        return Optional.of(best);
    }

    /** 1.9.9: sum of melee combat XP (Attack + Strength + Defence + Hitpoints).
     *  Magic is excluded because vengeance casts give Magic XP and would
     *  produce false hit confirmations for Elder maul / DWH / Arclight / BGS
     *  specs (all melee weapons). Returns 0 on SDK error rather than
     *  throwing — a "no XP delta" result is treated as a miss, which is
     *  conservative. */
    private long getMeleeCombatXp() {
        try {
            return Skill.ATTACK.getXp()
                    + Skill.STRENGTH.getXp()
                    + Skill.DEFENCE.getXp()
                    + Skill.HITPOINTS.getXp();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 1.9.9: process the deferred hit confirmation. Called once at the top
     *  of handleFightingCorp every tick. If XP has increased since the
     *  baseline, the spec hit — record it and clear the pending state.
     *  If 2 seconds have elapsed without XP, treat as a miss. */
    /** 1.9.99.29: read Corp's current HP%. Returns -1 if Corp isn't in
     *  render or its health bar isn't visible. */
    private double readCorpHpPct() {
        try {
            Optional<Npc> corp = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corp.isPresent() && corp.get().isHealthBarVisible()) {
                return corp.get().getHealthBarPercent();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    /** 1.9.99.32: gate corp.interact("Attack") on Npc.isVisible() so the
     *  SDK's built-in rotate-to-target — which fires inside interact()
     *  when the click point is off-screen — can't drag the camera angle
     *  down. If Corp isn't visible, walk one tile toward Corp's tile
     *  ourselves (preserving the high camera angle) and return false;
     *  the caller's outer loop retries next tick when Corp is on-screen.
     *  User: "as long as we are keeping a high camera angle we dont
     *  really need to adjust to interact we can just walk towards the
     *  boss or attack it IF its ons screen already ... its the camera
     *  rotate to target built into tribot". */
    /** 1.9.99.57: walk toward target in ADAPTIVE chunks — big jumps
     *  when far, smaller as we approach. Each chunk capped at 12 tiles
     *  (long enough to make real progress, short enough that the SDK
     *  pathfinder can't take a creative detour through Corp's hitbox).
     *  Each chunk's destination is clamped so it never lands inside
     *  Corp's 5x5 hitbox. Re-evaluates Corp's position between chunks.
     *  The SDK exposes Corp's tile via Query.npcs().getTile() — minimap
     *  dots are just a visual of the same data, no need to read pixels.
     *  User: "we dont only want to walk 5 tiles forward at a time ...
     *  estimate the direction corp is in but not walk completelyt over
     *  it". Returns true
     *  if we reached within 2 tiles of target, false on timeout. */
    /** 1.9.99.58: buffer zone around Corp where chunk-walks are banned.
     *  Chunk destinations must stay at least this many tiles from Corp's
     *  hitbox edge. Once the player is within this buffer, walkInChunksTo
     *  returns true and the caller takes over with click-attack (game
     *  pathfinder routes safely from there). User: "we wouldnt ever want
     *  to click to close to the corp ... we would want to walk idk maybe
     *  3-7 tiles outside of its hitbox. any closer and thats setting us
     *  up into a zone we could get walked on". */
    private static final int CHUNK_WALK_BUFFER_FROM_HITBOX = 4;

    /** 1.9.99.71: single-shot walk toward target. Compute one destination
     *  (capped to stay outside Corp's buffer zone), click once, wait for
     *  arrival or stop. NO multi-chunk loop. User: "as long as corp isnt
     *  in that chunk, we dont need to calculate multiple chunks. walking
     *  into the room that much is almost always enough for us to then
     *  see corp on our screen. which should break out of needing to walk
     *  again." Caller's attackCorpIfVisible handles the final approach
     *  via click-attack once Corp is on-screen. */
    private boolean walkInChunksTo(WorldTile target, Npc corp) {
        if (target == null) return false;
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null) return false;
        int distance = (int) Math.ceil(myPos.distanceTo(target));
        if (distance <= 2) return true;

        // Under-Corp guard: step off first if somehow inside the hitbox.
        // 1.9.99.168: use STABLE isUnderCorp() (≥2 consecutive frame
        // overlaps) instead of a raw single-frame corpArea.contains()
        // check. Pre-1.9.99.168 a single-frame Corp animation expanding
        // its area false-positived → stepOffCorp picked a new tile near
        // the bot's current position → encroachment relocate to the
        // corner was wiped out. User: "got to the corner tile and then
        // ran straight back to the original tile it was stacked on ...
        // we werent under the corps hitbox". The 1.9.99.114 stable
        // counter exists for exactly this case but wasn't wired here.
        Area corpArea = null;
        try { corpArea = corp != null ? corp.getArea() : null; } catch (Exception ignored) {}
        if (corp != null && isUnderCorp(corp)) {
            Log.warn("walkInChunksTo: under Corp — stepping off first");
            stepOffCorp(corp);
            return true;
        }

        // 1.9.99.162: ROOT-CAUSE FIX for "repositioning never works".
        // Pre-1.9.99.162 this returned true (no walk) whenever we were
        // within CHUNK_WALK_BUFFER_FROM_HITBOX (4 tiles) of Corp's edge.
        // The buffer was meant to prevent long approach walks from
        // routing THROUGH Corp's hitbox. But the bot is ALWAYS within
        // the buffer during FIGHTING_CORP (melee range = 1 tile from
        // Corp's edge), so EVERY encroachment relocate bailed without
        // walking. User: "repositioning has never worked and i want it
        // to". Now: only bail when the TARGET is essentially the same
        // tile (within 2 tiles of myPos) — that's the only case where
        // there's nothing to walk. The L-shape / live-recheck logic
        // elsewhere handles hitbox-crossing during the walk itself.
        // Already handled above: distance <= 2 returns true.
        // Already in buffer with a meaningful target → just walkTo directly.
        if (corp != null) {
            double edgeDist = getDistanceToCorpHitboxEdge(myPos, corp);
            if (edgeDist >= 0 && edgeDist <= CHUNK_WALK_BUFFER_FROM_HITBOX) {
                // 1.9.99.227: cross-Corp guard. Pre-1.9.99.227 this branch
                // did a raw LocalWalking.walkTo(target) which delegates to
                // the game pathfinder — and the pathfinder happily routes
                // STRAIGHT THROUGH Corp's 5x5 hitbox when target is on the
                // opposite side, eating a 60+ stomp. Now: detour via
                // pickCornerWaypoint first if the straight line crosses
                // the hitbox. User: "sometimes running ACROSS corp to get
                // to a desired tile instead of L walking on the outside
                // perimeter".
                preWalkAroundCorp(target, corp);
                Log.debug("walkInChunksTo: already in buffer (edgeDist=" + edgeDist
                        + ", target dist=" + distance + ") — direct walkTo");
                boolean walked = LocalWalking.walkTo(target);
                if (walked) {
                    Waiting.waitUntil(5000, () -> {
                        WorldTile t = MyPlayer.getTile(); // 1.9.99.180: NPE guard
                        return t != null && t.distanceTo(target) <= 2;
                    });
                }
                return walked;
            }
        }

        // Pick a destination as close to target as the buffer allows.
        // Walk straight along the line from myPos toward target, clamping
        // back if the chosen tile would land inside the buffer.
        int dx = target.getX() - myPos.getX();
        int dy = target.getY() - myPos.getY();
        double absMax = Math.max(Math.abs(dx), Math.abs(dy));
        // Walk as far as possible (entire remaining distance up to 25 tiles
        // — covers all reasonable Corp-room distances in one click).
        double scale = absMax == 0 ? 0 : Math.min(1.0, 25.0 / absMax);
        int chunkX = (int) Math.round(dx * scale);
        int chunkY = (int) Math.round(dy * scale);
        WorldTile next = new WorldTile(
                myPos.getX() + chunkX,
                myPos.getY() + chunkY,
                myPos.getPlane());

        // Clamp back so the destination stays outside Corp's buffer.
        if (corp != null) {
            int safetyHops = 0;
            while (safetyHops < 30 && (chunkX != 0 || chunkY != 0)) {
                double nextEdgeDist = getDistanceToCorpHitboxEdge(next, corp);
                if (nextEdgeDist > CHUNK_WALK_BUFFER_FROM_HITBOX) break;
                if (Math.abs(chunkX) >= Math.abs(chunkY) && chunkX != 0) {
                    chunkX -= Integer.signum(chunkX);
                } else if (chunkY != 0) {
                    chunkY -= Integer.signum(chunkY);
                }
                next = new WorldTile(myPos.getX() + chunkX, myPos.getY() + chunkY, myPos.getPlane());
                safetyHops++;
            }
            if (chunkX == 0 && chunkY == 0) {
                Log.debug("walkInChunksTo: no walkable destination outside buffer toward "
                        + target + " — handing off to click-attack");
                return true;
            }
        }

        Log.info("walkInChunksTo: single walk " + Math.max(Math.abs(chunkX), Math.abs(chunkY))
                + " tiles to " + next + " (target=" + target + ", remaining=" + distance + ")");
        final WorldTile chunkDest = next;
        final long[] lastMoveAt = { System.currentTimeMillis() };
        final WorldTile[] lastObservedPos = { myPos };
        LocalWalking.walkTo(next);
        final WorldTile startPos = myPos;
        Waiting.waitUntil(8000, () -> {
            WorldTile p = MyPlayer.getTile();
            if (p == null) return false;
            if (p.equals(chunkDest)) return true;
            if (!p.equals(lastObservedPos[0])) {
                lastObservedPos[0] = p;
                lastMoveAt[0] = System.currentTimeMillis();
            }
            // 1.9.99.78: bail early if Corp comes into render mid-walk.
            // 1.9.99.80: BUT only after we've actually made walk progress.
            // The 00:53:57 log showed walkInChunksTo bailing instantly
            // when Corp was already visible at walk start (player 17
            // tiles away, Corp visible in Query but unreachable by
            // click). Walk command never executed because the poll
            // returned true on the first iteration, then the caller's
            // attackCorpIfVisible queued a second click that didn't
            // route. Drift recheck looped without progress. Require
            // 3+ tiles of movement (game pathfinder will have committed
            // to the walk by then) before allowing the visibility bail.
            try {
                if (p.distanceTo(startPos) >= 3
                        && Query.npcs().nameEquals(CORPOREAL_BEAST)
                                .filter(Npc::isVisible)
                                .findFirst().isPresent()) {
                    return true;
                }
            } catch (Throwable ignored) {}
            // No tile change for 1200ms (~2 OSRS ticks) = walk done at
            // wherever the pathfinder landed us.
            return System.currentTimeMillis() - lastMoveAt[0] > 1200;
        });
        return true;
    }

    private boolean attackCorpIfVisible(Npc corp) {
        if (corp == null) return false;
        try {
            if (!corp.isVisible()) {
                WorldTile myTile = MyPlayer.getTile();
                WorldTile corpTile = corp.getTile();
                if (myTile != null && corpTile != null) {
                    // 1.9.99.55: step a CHUNK toward Corp (up to 5 tiles
                    // along the dominant axis) instead of just 1 tile.
                    // 1-tile steps from off-screen distances meant many
                    // ticks of slow walking. 5 tiles covers ground but
                    // is short enough to stay outside Corp's 5x5 hitbox
                    // (we cap each axis so the destination is at least
                    // 3 tiles from Corp's center on the dominant axis,
                    // i.e. on the rim of the hitbox, never inside).
                    int dx = corpTile.getX() - myTile.getX();
                    int dy = corpTile.getY() - myTile.getY();
                    int sx = Integer.signum(dx);
                    int sy = Integer.signum(dy);
                    // Cap step so we don't overshoot into Corp's hitbox.
                    // Corp is 5x5: from Corp.getTile() (SW corner) the
                    // hitbox spans 0..4 in each axis. Keep us at least 3
                    // tiles from Corp.getTile() on the dominant axis so
                    // we land outside the hitbox.
                    int stepX = (Math.abs(dx) > 3) ? sx * Math.min(5, Math.abs(dx) - 3) : 0;
                    int stepY = (Math.abs(dy) > 3) ? sy * Math.min(5, Math.abs(dy) - 3) : 0;
                    if (stepX == 0 && stepY == 0) {
                        // Already close enough; fall through to attack
                        // attempt below (might be in-range but mis-rendered).
                    } else {
                        WorldTile step = new WorldTile(
                                myTile.getX() + stepX,
                                myTile.getY() + stepY,
                                myTile.getPlane());
                        Log.info("Corp off-screen — stepping " + Math.max(Math.abs(stepX), Math.abs(stepY))
                                + " tiles toward " + step
                                + " (instead of triggering SDK camera rotate)");
                        LocalWalking.walkTo(step);
                        return false;
                    }
                }
                return false;
            }
        } catch (Throwable ignored) {}
        return corp.interact("Attack");
    }

    /** 1.9.99.39: queue-based spec hit confirmation. Each spec fire enqueues a
     *  PendingSpecAttempt; processPendingSpecHit iterates oldest-first. Each
     *  attempt is independently resolved as CONFIRMED (via own hitsplat or
     *  XP delta from this attempt's baseline) or MISSED (deadline expired).
     *  After confirming an attempt we advance the XP baseline on remaining
     *  younger attempts to the current XP — that prevents the next attempt
     *  from double-crediting the same XP delta when two specs share a stamped
     *  baseline due to the lastPollXp race. Corp HP delta is logged for
     *  visibility but is no longer a hard veto (user: "HP percent is a noisy/
     *  coarse signal, especially with teammates hitting Corp. Use it as a
     *  debug log or tie-breaker, not a hard rejection after XP/hitsplat says
     *  the spec landed."). */
    /** 1.9.99.45: monotonic-counter tick. Read current own-hitsplat count;
     *  if it's higher than the last observation, add the delta to the
     *  monotonic counter and update the observation. Hitsplat expirations
     *  shrink the visible count but never decrement the counter, so each
     *  NEW hitsplat increments the counter exactly once. */
    private void advanceHitsplatCounter(Optional<Npc> corpForHit) {
        // 1.9.99.50: filter on getValue() > 0 so 0-damage MISS splats don't
        // count as confirmations. Pre-1.9.99.50 any own hitsplat (including
        // misses, which OSRS still renders as a "0" splat) advanced the
        // monotonic counter — a missed Elder maul spec was credited as a
        // hit because the miss-splat still passed isMine(). User: "i think
        // it counted an elder maul spec that missed as being a succesful
        // spec ... our spec counts are getting all twisted".
        long current = corpForHit.map(c -> {
            try {
                return c.getHitsplats().stream()
                        .filter(h -> {
                            try { return h.isMine() && h.getValue() > 0; }
                            catch (Exception e) { return false; }
                        })
                        .count();
            } catch (Exception e) { return 0L; }
        }).orElse(0L);
        if (current > lastObservedHitsplatCount) {
            monotonicHitsplatCounter += (current - lastObservedHitsplatCount);
        }
        lastObservedHitsplatCount = current;
    }

    private void processPendingSpecHit() {
        if (pendingHits.isEmpty()) return;
        long now = System.currentTimeMillis();
        long nowXp = getMeleeCombatXp();
        double nowCorpHp = readCorpHpPct();
        Optional<Npc> corpForHit = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        advanceHitsplatCounter(corpForHit);
        int largestRecentHit = corpForHit.map(this::getMyLargestRecentHitOnCorp).orElse(0);

        boolean anyConfirmed = false;
        Iterator<PendingSpecAttempt> it = pendingHits.iterator();
        while (it.hasNext()) {
            PendingSpecAttempt attempt = it.next();

            // 1.9.99.11 (preserved): deadline first. If the loop hasn't run
            // within the window (state moved through POH cycle), the XP
            // baseline is stale by 30+s of normal-attack XP — fall through
            // to a deterministic miss instead of a stale-XP false positive.
            if (now > attempt.deadline) {
                Log.info("Spec MISSED (deadline " + HIT_CONFIRM_TIMEOUT_MS
                        + "ms exceeded) for " + attempt.weapon
                        + " — not advancing phase counter");
                it.remove();
                continue;
            }

            // 1.9.99.45: confirm by monotonic-counter advance (new hitsplat
            // since enqueue) — NOT by raw count of hitsplats on Corp. The
            // raw-count approach in 1.9.99.39 re-counted long-lived
            // hitsplats every call and falsely confirmed multiple attempts
            // with the same hit. Codex audit: "the new queue still confirms
            // using generic 'my hitsplat' ... Same issue with
            // hitsplatsAvailable: it counts any own hitsplat on Corp, not
            // necessarily the spec hitsplat."
            boolean confirmedByHit = monotonicHitsplatCounter > attempt.hitsplatCounterAtEnqueue;
            boolean confirmedByXp = nowXp > attempt.xpBaseline;
            // 1.9.99.45: during the XP-suppression window we absorb new XP
            // into baselines but do NOT confirm via XP alone. The window
            // is set right after a hitsplat confirmation to swallow that
            // hit's delayed XP arrival.
            if (!confirmedByHit && confirmedByXp && System.currentTimeMillis() < suppressXpConfirmUntil) {
                attempt.xpBaseline = nowXp;
                continue;
            }
            if (!confirmedByHit && !confirmedByXp) {
                // Not confirmed yet. Leave in queue (younger attempts can't
                // be confirmed before older ones, but continue iterating so
                // a younger expired attempt can still time out).
                continue;
            }

            double hpDelta = (attempt.corpHpBaseline >= 0 && nowCorpHp >= 0)
                    ? (attempt.corpHpBaseline - nowCorpHp) : Double.NaN;
            // 1.9.99.169: SDK returns 0-1 fraction. Multiply by 100 for
            // human-readable percentage in the log. Pre-1.9.99.169 the
            // log showed "corpHP=0.9%" when Corp was actually at 90% HP
            // (1800 HP). The math elsewhere is correct (approxHp = frac
            // × 2000) but the display misled the user into thinking the
            // bot was POH'ing at near-zero Corp HP.
            String hpFragment = nowCorpHp < 0
                    ? "?"
                    : (Double.isNaN(hpDelta)
                            ? String.format("%.1f%%", nowCorpHp * 100.0)
                            : String.format("%.1f%% (Δ%.2f%%)",
                                    nowCorpHp * 100.0, hpDelta * 100.0));

            // 1.9.99.61: verify the equipped weapon matches attempt.weapon
            // before crediting the spec. Pre-1.9.99.61 the queue trusted
            // attempt.weapon (the weapon equipped at fire time), but if a
            // weapon swap happened mid-flight (e.g., Elder maul → Arclight
            // rotation triggered between fire and confirm), the hitsplat
            // could be from the NEW weapon while we credit the OLD one.
            // Now we cross-check Equipment.contains: if mismatch, log a
            // warning but still credit attempt.weapon (it's our best
            // guess for which spec actually fired — the new weapon's spec
            // bar might not have activated yet). User: "is tehre a way to
            // check if we actually have the correct spec weapon equiped
            // when we count a spec progression?".
            String equippedWeaponNow = null;
            for (String candidate : new String[]{"Bandos godsword", "Elder maul",
                    "Dragon warhammer", "Arclight", "Darklight", "Emberlight"}) {
                if (Equipment.contains(candidate)) { equippedWeaponNow = candidate; break; }
            }
            if (equippedWeaponNow != null
                    && !equippedWeaponNow.equalsIgnoreCase(attempt.weapon)) {
                Log.warn("Spec credit mismatch: attempt.weapon=" + attempt.weapon
                        + " but Equipment now has " + equippedWeaponNow
                        + " — weapon swap happened post-fire, crediting "
                        + attempt.weapon + " (the fired weapon)");
            }

            // 1.9.99.5 (preserved): BGS damage is captured from hitsplat for
            // phase-3 tracking.
            if ("Bandos godsword".equalsIgnoreCase(attempt.weapon)) {
                recordSpecUsed(attempt.weapon, largestRecentHit);
                Log.info("BGS spec dealt ~" + largestRecentHit
                        + " damage (recorded for team phase 3) (corpHP=" + hpFragment + ")");
            } else {
                recordSpecUsed(attempt.weapon);
                long hitsplatDelta = monotonicHitsplatCounter - attempt.hitsplatCounterAtEnqueue;
                Log.info("Spec HIT confirmed "
                        + (confirmedByHit ? ("via hitsplat (+" + hitsplatDelta + " since enqueue)")
                                          : ("via XP delta +" + (nowXp - attempt.xpBaseline)))
                        + " for " + attempt.weapon
                        + " (corpHP=" + hpFragment + ")");
            }
            it.remove();
            anyConfirmed = true;

            // 1.9.99.45: advance baselines on remaining attempts so the
            // same hit / XP delta can't confirm two of them.
            //   - XP baseline → nowXp (this attempt's XP delta consumed).
            //   - Hitsplat counter baseline → at least the value that just
            //     confirmed (the one hitsplat we just consumed).
            //   - HP baseline → nowCorpHp (we've already applied any drop).
            for (PendingSpecAttempt remaining : pendingHits) {
                if (remaining.xpBaseline < nowXp) {
                    remaining.xpBaseline = nowXp;
                }
                if (nowCorpHp >= 0 && (remaining.corpHpBaseline < 0 || remaining.corpHpBaseline > nowCorpHp)) {
                    remaining.corpHpBaseline = nowCorpHp;
                }
                if (confirmedByHit) {
                    long consumeCounter = attempt.hitsplatCounterAtEnqueue + 1;
                    if (remaining.hitsplatCounterAtEnqueue < consumeCounter) {
                        remaining.hitsplatCounterAtEnqueue = consumeCounter;
                    }
                }
            }

            // 1.9.99.45: when confirmation was by hitsplat, the corresponding
            // XP delta arrives 0-1 game ticks later. Suppress XP-only confirms
            // for 1200ms so the same hit's delayed XP can't pick up a younger
            // pending attempt. Codex audit.
            if (confirmedByHit) {
                suppressXpConfirmUntil = System.currentTimeMillis() + 1200;
            }
        }

        if (anyConfirmed) {
            // 1.9.17 (preserved): phase rotation runs after recordSpecUsed so
            // the per-kill counter reflects the just-confirmed hit. Only run
            // once per processPendingSpecHit pass regardless of how many
            // attempts were confirmed this tick.
            String previous = chosenSpecWeapon;
            refreshSpecWeaponForPhase();
            if (chosenSpecWeapon != null && previous != null
                    && !chosenSpecWeapon.equals(previous)) {
                Log.info("Phase target met for " + previous
                        + " — rotating spec weapon to " + chosenSpecWeapon);
                // 1.9.35 (preserved): only equip the rotated weapon if we
                // still have spec energy. Otherwise the next tick goes to
                // PREPARING_RESTORATION_CYCLE — defer the equip until after
                // teleport so we don't burn the wrong weapon on the melee
                // finish if the host is offline.
                if (Combat.getSpecialAttackPercent() >= getMinSpecEnergy()
                        && Inventory.contains(chosenSpecWeapon)) {
                    equipSpecWeapon();
                } else {
                    Log.info("Deferring " + chosenSpecWeapon
                            + " equip until after POH restoration (energy "
                            + Combat.getSpecialAttackPercent() + "%)");
                }
            }
        }
    }

    /** 1.9.34: debounced spec-button activation. Skips the click if we
     *  activated within the last SPEC_ACTIVATE_DEBOUNCE_MS — the previous
     *  click may not have propagated to isSpecialAttackEnabled yet, and a
     *  fresh click on an already-ON button TOGGLES IT OFF.
     *  Returns true if we either activated successfully OR believe the
     *  spec is already on (recent activate). False only if activate failed
     *  AND no recent activate.
     */
    private boolean tryActivateSpec() {
        long now = System.currentTimeMillis();
        long sinceLastClick = now - lastSpecActivateAt;
        // 1.9.94: settle window. Within Nms of a click, trust the click
        // without re-verifying SDK state. Combat.isSpecialAttackEnabled()
        // can lag the actual game state by ~half a tick after a click;
        // 1.9.90's "verify SDK" path combined with a fast back-to-back
        // caller (pre-activate path at L3302 then handleSpecialAttack at
        // L6970, ~50ms apart) re-clicks the bar during the lag window
        // and toggles it OFF, causing the next swing to fire as an
        // auto-attack instead of a spec.
        // 1.9.99.10: bumped settle window 250ms→800ms. 250ms was enough
        // for back-to-back same-tick callers but two callers ~300-500ms
        // apart (which happens between L3303 pre-activate and
        // handleSpecialAttack's L7027 re-arm) both saw bar OFF and
        // both clicked → toggle ON then OFF → net DISABLED.
        // 1.9.99.21: bumped 800ms→1500ms. 800ms still wasn't enough — the
        // L3287 in-line detector + L3303 main pre-activate cycle was
        // ~1000ms apart (mouse movement + click animation), past 800ms
        // settle. Second caller fell through, SDK reported bar OFF
        // momentarily (lag), clicked again, toggled ON→OFF. 1500ms
        // covers two full game ticks + click animation lag while still
        // letting a genuinely-failed click retry after the next caller.
        // User: "STILL DOUBLE SPEC ACTIVATING".
        // 1.9.99.43: Codex audit caught dead code here. Pre-1.9.99.43 the
        // first block returned true for ANY call within 1500ms, making the
        // verification block at SPEC_ACTIVATE_DEBOUNCE_MS=1200 unreachable —
        // so for 1.5 seconds tryActivateSpec lied "yes, spec is armed" even
        // if the click silently failed and the bar was actually OFF. Now
        // split into three windows: very recent (<600ms) trust without
        // verify (avoids race with click-in-flight); recent (600-1200ms)
        // verify isSpecialAttackEnabled and return false (signal caller)
        // if bar is OFF — but do NOT re-click yet (preserves the original
        // double-click avoidance); past debounce (>1200ms) free to verify
        // and re-click as needed.
        // 1.9.99.90: energy-drop bypass. If current spec energy is BELOW
        // the snapshot from the last click, a spec has fired in between —
        // bar is legitimately off (game auto-toggle on swing). The
        // debounce windows below would refuse to re-click "because we
        // just clicked", but the click that landed already triggered
        // the swing. We need a fresh click immediately. The 03:47 log
        // showed this exact lockout: spec 1 fired (100→50), bar off,
        // 15 seconds of debounce-blocked re-arm attempts.
        int curEnergy = Combat.getSpecialAttackPercent();
        boolean energyDroppedSinceLastClick = specEnergyAtLastActivate >= 0
                && curEnergy < specEnergyAtLastActivate;
        if (!energyDroppedSinceLastClick) {
            if (sinceLastClick < 600) {
                return true; // click likely still in flight; trust
            }
            if (sinceLastClick < SPEC_ACTIVATE_DEBOUNCE_MS) {
                // Recent click — verify rather than blindly trust.
                if (Combat.isSpecialAttackEnabled()) return true;
                // Bar not yet on but we clicked recently — don't re-click
                // (would risk toggling OFF if the previous click is in flight).
                // Caller sees false and skips this swing; next call past the
                // debounce window will re-evaluate.
                return false;
            }
        } else {
            Log.debug("tryActivateSpec: energy dropped " + specEnergyAtLastActivate
                    + "% → " + curEnergy + "% — bypassing debounce, fresh click");
        }
        if (Combat.isSpecialAttackEnabled()) {
            // Already on per the SDK; don't click, just record we trust it.
            lastSpecActivateAt = now;
            specEnergyAtLastActivate = curEnergy;
            return true;
        }
        if (Combat.activateSpecialAttack()) { // 1.9.34.1: actual SDK call (was recursive)
            lastSpecActivateAt = now;
            specEnergyAtLastActivate = curEnergy;
            return true;
        }
        return false;
    }

    /** 1.9.0 / 1.9.1: can we fire another spec on the current bar?
     *  1.9.0 originally gated this on phase targets + Corp HP floor, but
     *  those gates belong at the BAR boundary (shouldStartRestorationCycle
     *  before the next bar), not mid-bar. When the team kills fast, Corp's
     *  HP drops below 1700 between our pre-activation and the in-line spec
     *  fire — which would wrongly cancel the second spec on a bar we'd
     *  already committed to. Once a spec bar starts, drain it to empty.
     *  Only checks: enough energy for one more spec AND Corp is alive. */
    private boolean canFireAnotherSpecOnThisBar() {
        int energy = Combat.getSpecialAttackPercent();
        int minEnergy = getMinSpecEnergy();
        if (energy < minEnergy) {
            Log.info("canFireAnotherSpec: NO — energy " + energy + " < min " + minEnergy);
            return false;
        }
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) {
            Log.info("canFireAnotherSpec: NO — Corp not visible");
            return false;
        }
        if (!isCorpAlive(corpOpt.get())) {
            Log.info("canFireAnotherSpec: NO — Corp not alive");
            return false;
        }
        Log.info("canFireAnotherSpec: YES — energy " + energy + " >= " + minEnergy);
        return true;
    }

    /** How many specs we can fire from a full bar with the cheapest owned
     *  spec weapon. Used as a per-cycle safety bound on the spec loop —
     *  the real terminator is the energy check, but a count cap prevents
     *  infinite loops if specs silently fail. 1.8.8 replaces the hardcoded
     *  INTERNAL_SPECS_PER_CYCLE (2) which over-capped Arclight (4/bar). */
    private int specsPerFullBar() {
        int cost = getMinOwnedSpecCost();
        return cost > 0 ? Math.max(1, 100 / cost) : 2;
    }

    /** Cheapest spec cost among owned weapons. Default 50% if nothing known. */
    private int getMinOwnedSpecCost() {
        int min = 100;
        for (String w : getOwnedSpecWeapons()) {
            Integer c = SPEC_COST.get(w);
            if (c != null && c < min) min = c;
        }
        return min == 100 ? 50 : min;
    }

    /** Derived from the cheapest owned spec weapon — replaces the legacy
     *  settings.minSpecEnergy slider. */
    private int getMinSpecEnergy() {
        return getMinOwnedSpecCost();
    }

    // ========== MODE-AWARE SPEC BUDGET (1.8.0) ==========

    /** How many spec attempts we estimate are available this whole TRIP, not
     *  per kill. Drives whether we dump specs early (FEROX_ONLY, no refill)
     *  or pace them across restoration cycles. */
    private int getTripSpecBudget() {
        int specPct = 0;
        try { specPct = Combat.getSpecialAttackPercent(); } catch (Exception ignored) {}
        int cost = getMinOwnedSpecCost();
        if (cost <= 0) cost = 50;
        int currentBarSpecs = specPct / cost;

        if (isFeroxOnlyMode()) {
            // No mid-trip pool refill. Trip budget == what we have right now
            // plus the trickle of natural regen (~1%/30s), which we approximate
            // as +1 spec over the trip's length. Conservative.
            return Math.max(0, currentBarSpecs) + 1;
        }

        // POH modes (OWN / FRIEND / BOT_HOST / W330): each restoration cycle
        // tops the spec bar back up. Total = current + (cycles * fullBar).
        int cyclesRemaining = Math.max(0,
                (settings == null ? 3 : settings.totalRestorationCycles)
                - currentRestorationCycle);
        int perCycleSpecs = 100 / cost;
        return currentBarSpecs + (cyclesRemaining * perCycleSpecs);
    }

    /** True when we should dump specs aggressively this kill instead of saving
     *  them. Aggressive mode means: don't skip on low Corp HP, don't conserve
     *  for "later" — there's no later. */
    private boolean shouldDumpSpecsAggressively() {
        return isFeroxOnlyMode();
    }

    /**
     * Expand settings.mainWeapon into the list of in-game names that count as
     * "the main weapon". E.g., "Osmumten's fang" -> [(or), regular]. This lets
     * the user pick one entry in the GUI while we still match both ornament
     * and non-ornament variants in inventory / equipment.
     */
    private List<String> getMainWeaponVariants() {
        String chosen = settings == null || settings.mainWeapon == null
                ? "Osmumten's fang"
                : settings.mainWeapon.trim();
        if (chosen.isEmpty()) chosen = "Osmumten's fang";

        if (chosen.toLowerCase().contains("osmumten") && chosen.toLowerCase().contains("fang")) {
            return Arrays.asList("Osmumten's fang (or)", "Osmumten's fang");
        }
        // 1.9.99.148: Zamorakian spear and Zamorakian hasta share the same
        // combat stats / attack style and are interchangeable for our
        // purposes. Match either if the user picked "Zamorakian spear".
        String lc = chosen.toLowerCase();
        if (lc.contains("zamorakian") && (lc.contains("spear") || lc.contains("hasta"))) {
            return Arrays.asList("Zamorakian spear", "Zamorakian hasta");
        }
        return Collections.singletonList(chosen);
    }

    /**
     * 1.9.99.148: True if the configured main weapon is a 2-handed weapon.
     * Used to skip defender steps and to demand an extra inventory slot
     * before wielding the main weapon (a 2H wield sends both the previous
     * weapon AND any equipped defender / offhand back to inventory).
     */
    private boolean isMainWeaponTwoHanded() {
        String chosen = settings == null || settings.mainWeapon == null
                ? "" : settings.mainWeapon.trim().toLowerCase();
        return chosen.contains("zamorakian")
                && (chosen.contains("spear") || chosen.contains("hasta"));
    }

    /**
     * Check if any acceptable main weapon is equipped
     */
    private boolean isMainWeaponEquipped() {
        // 1.9.99.219: dropped per-call log — this is a hot predicate called
        // ~every loop tick. The log spammed "Main weapon equipped: Fang"
        // hundreds of times per second, burying useful diagnostics like
        // TEAM-DIAG and FCDIAG. Predicate result is what callers care about.
        for (String weaponName : getMainWeaponVariants()) {
            if (Equipment.contains(weaponName)) {
                return true;
            }
        }
        return false;
    }

    // ========== MAIN WEAPON COMPATIBILITY METHODS ==========

    /**
     * Get the name of the main weapon we have (for equipping)
     */
    private String getAvailableMainWeapon() {
        for (String weaponName : getMainWeaponVariants()) {
            if (Inventory.contains(weaponName)) {
                return weaponName;
            }
        }
        return null;
    }

    private boolean isPlayerAttackingCorp(Npc corp) {
        // Option 2: Check if we're interacting with the specific Corp instance
        return Query.npcs()
                .filter(npc -> npc.equals(corp))
                .isMyPlayerInteractingWith()
                .findFirst()
                .isPresent();
    }

    /**
     * Check if Corp's health is above the threshold for special attacks.
     * Corp has 2000 max HP, so we map the visible health-bar % into absolute
     * HP and compare against settings.corpMinHpForSpec. Returns false when
     * the bar isn't visible (Corp dead or not in combat).
     */
    private boolean isCorpHealthAboveSpecThreshold(Npc corp) {
        if (!corp.isHealthBarVisible()) return false;
        double healthPercent = corp.getHealthBarPercent();
        // 1.9.29: update + use the per-kill max tracker so a freshly-
        // engaged Corp (bar visible but reading 0% before server populates
        // it) doesn't make us think Corp is dying. Same fix as 1.9.28 but
        // for this gate, which handleSpecialAttack and shouldUseSpecialAttack
        // both depend on. Pre-1.9.29 the bot would refuse to spec on the
        // first tick of combat because "Corp HP looks low (it's 0%)",
        // queue a Fang swap, and the kill would devolve.
        if (healthPercent > maxCorpHpPercentThisKill) {
            maxCorpHpPercentThisKill = healthPercent;
        }
        // 1.9.99.117: scale fix matching 1.9.99.116's isInKillPhase fix —
        // SDK returns 0-1 proportion. Was <= 5.0 (always true since max
        // is 1.0) and (/100 * 2000) which produced bogus approxHp ~10.
        // Net effect: this gate never blocked. shouldSpecNowConsideringTeam
        // and handleSpecialAttack both depend on this — they thought Corp
        // was above threshold even when very low.
        if (maxCorpHpPercentThisKill <= 0.05) {
            // Bar not yet populated this kill — assume Corp is at full HP,
            // i.e. above threshold (allow spec).
            return true;
        }
        int approxHp = (int) (healthPercent * 2000);
        return approxHp >= settings.corpMinHpForSpec;
	}

    /**
     * Withdraw our chosen spec weapon from bank
     */
    private boolean withdrawSpecWeapon() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
        }

        // 1.9.99.149: short-circuit if we ALREADY have the spec weapon
        // (equipped or in inventory). Pre-1.9.99.149 an account whose
        // DWH was equipped got "not found in bank" errors every bank
        // cycle because the bank lookup ran unconditionally — banking
        // would then loop forever even though nothing was actually
        // missing.
        if (hasSpecWeapon()) {
            Log.info("Spec weapon already present (inv/equipment): " + chosenSpecWeapon);
            return true;
        }

        if (Bank.getCount(chosenSpecWeapon) > 0) {
            Log.info("Withdrawing chosen spec weapon: " + chosenSpecWeapon);
            return Bank.withdraw(chosenSpecWeapon, 1);
        } else {
            Log.error("Chosen spec weapon not found in bank: " + chosenSpecWeapon);
            return false;
        }
    }




	// ========== SIMPLIFIED POH IMPLEMENTATION ==========

	/**
	 * Simple house teleport using "Outside" option
	 */

	// ========== 4. ADD THESE STATE HANDLER METHODS ==========
	private void handlePreparingRestorationCycle() {
		Log.info("Preparing restoration cycle " + (currentRestorationCycle + 1) + "/" + settings.totalRestorationCycles);

		// W330 mode skips house tabs (walks to Rimmington portal instead) and
		// also skips the initial-specs phase since we may need to bail to a
		// new world mid-trip. Route directly into the W330 sub-FSM.
		if (POH_SOURCE_W330_RANDOM.equals(getPohSource())) {
			currentSpecialAttacksUsed = 0;
			isInRestorationPhase = true;
			w330Step = W330Step.CAPTURE_HOME;
			currentState = BotState.W330_RESTORATION;
			return;
		}

		if (!hasHouseTeleportTab()) {
			// 1.9.99.179: route to BANKING_AND_HEALING instead of
			// WAITING_FOR_TEAM. handleWaitingForTeam only progresses from
			// the Corp lobby/boss room — if we hit this bail at Ferox /
			// POH-exterior / Rimmington, bot loops forever. BANKING_AND_HEALING
			// can travel from anywhere and will withdraw tabs (1.9.99.168
			// added tab top-up to the banking cycle).
			Log.warn("No house tabs found — routing to BANKING_AND_HEALING for refill");
			emergencyResetPOHSystem();
			currentState = BotState.BANKING_AND_HEALING;
			return;
		}

		currentSpecialAttacksUsed = 0;
		isInRestorationPhase = true;
		// 1.9.99.134: if we're not in the boss room (e.g., post-bank at
		// Ferox, after a tele somewhere else), skip USING_INITIAL_SPECS.
		// That handler requires Corp to be visible (it fires remaining
		// specs at Corp before tele'ing to POH). From Ferox Corp isn't
		// visible → "Corp not found" → EMERGENCY_ESCAPE → POH attempt →
		// "already in friend's house" false-positive → USING_ORNATE_POOL
		// → location guard → BANKING_AND_HEALING → loop. Skip straight
		// to TELEPORTING_TO_HOUSE when there are no specs to fire at
		// Corp right now. User log: "Banking complete, spec=80% ...
		// Corp not found during initial spec phase ... handleUsingOrnatePool
		// entered while at Ferox ... INFINITE LOOP."
		if (!isInCorpBossRoom()) {
			Log.info("Not in boss room — skipping USING_INITIAL_SPECS, going straight to TELEPORTING_TO_HOUSE");
			currentState = BotState.TELEPORTING_TO_HOUSE;
			return;
		}
		currentState = BotState.USING_INITIAL_SPECS;
	}

	private void handleUsingInitialSpecs() {
		Log.info("Using initial special attacks (" + currentSpecialAttacksUsed + "/" + specsPerFullBar() + ")");

		Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
		if (!corpOpt.isPresent()) {
			Log.error("Corp not found during initial spec phase");
			currentState = BotState.EMERGENCY_ESCAPE;
			return;
		}

		Npc corp = corpOpt.get();

		// Check if we're done with all specs
		if (currentSpecialAttacksUsed >= specsPerFullBar() ||
				Combat.getSpecialAttackPercent() < getMinSpecEnergy()) {

			Log.info("Finished all special attacks - Energy remaining: " + Combat.getSpecialAttackPercent() + "%");
			// 1.9.7: don't swap to Fang before house teleport. The spec
			// weapon stays equipped through POH so the next bar can start
			// firing immediately on return. User wants minimal actions:
			// spec → spec → tele → restore → tele → spec → spec → ...
			// The Fang swap is reserved for the kill-phase transition.
			currentState = BotState.TELEPORTING_TO_HOUSE;
			return;
		}

		// Phase D: rotate to the right spec weapon for current team phase.
		// 1.9.8: dropped coordinator gate so phase rotation works for solo.
		if (!refreshSpecWeaponForPhase()) {
			Log.info("No usable spec weapon for current team phase — skipping initial spec cycle.");
			currentState = BotState.TELEPORTING_TO_HOUSE;
			return;
		}

		// Phase C: if team has already done enough specs for our phase, skip.
		if (!shouldSpecNowConsideringTeam()) {
			Log.info("Team phase complete for " + chosenSpecWeapon + " — skipping initial spec cycle.");
			equipMainWeaponFast();
			currentState = BotState.TELEPORTING_TO_HOUSE;
			return;
		}

		// Equip and pre-activate spec weapon
		if (!isSpecWeaponEquipped()) {
			if (!equipSpecWeapon()) {
				Log.error("Failed to equip spec weapon");
				currentState = BotState.EMERGENCY_ESCAPE;
				return;
			}

			// 1.9.34: debounced activator — avoid rapid toggles.
			Log.info("PRE-ACTIVATING special attack for POH restoration cycle");
			tryActivateSpec();
		} else {
			Log.info("PRE-ACTIVATING special attack for POH cycle");
			tryActivateSpec();
		}

		// 1.9.99.43: unified with the pending-queue. Pre-1.9.99.43 this path
		// called recordSpecUsed on energy-drain success — counting MISSES as
		// successful specs (energy drops whether or not the hit lands).
		// Codex audit: "handleUsingInitialSpecs records a spec as successful
		// after energy drains, not after hit confirmation. So misses during
		// the USING_INITIAL_SPECS path still count as landed specs." Now we
		// snapshot pre-spec XP+HP, fire the spec, and on energy-drain
		// success we enqueue a PendingSpecAttempt — processPendingSpecHit
		// confirms/misses it just like every other spec-fire path.
		long preInitialSpecXp = getMeleeCombatXp();
		double preInitialSpecHp = readCorpHpPct();
		// 1.9.99.45: snapshot the hitsplat counter BEFORE firing.
		advanceHitsplatCounter(Optional.of(corp));
		long preInitialSpecHitsplatCounter = monotonicHitsplatCounter;
		if (useSpecialAttackOnCorpPreActivated(corp)) {
			currentSpecialAttacksUsed++;
			pendingHits.add(new PendingSpecAttempt(
					chosenSpecWeapon,
					preInitialSpecXp,
					preInitialSpecHp,
					System.currentTimeMillis() + HIT_CONFIRM_TIMEOUT_MS,
					preInitialSpecHitsplatCounter));
			Log.info("Initial spec " + currentSpecialAttacksUsed + "/" + specsPerFullBar()
					+ " fired (Energy: " + Combat.getSpecialAttackPercent()
					+ "% " + chosenSpecWeapon + ") — deferring hit confirmation");
		} else {
			// 1.9.99.179: do NOT increment currentSpecialAttacksUsed on
			// failure. Pre-1.9.99.179 a transient fail (off-screen Corp,
			// click miss) bumped the counter — two consecutive failures
			// could hit specsPerFullBar() and exit the spec phase with
			// ~100% energy still on the bar, bot teled to POH without
			// having actually specced. Retry next tick instead.
			Log.warn("Failed to use special attack — will retry next tick");
		}
	}

	/**
	 * Execute an attack and wait for special attack energy to drop
	 * @param target The target to attack
	 * @param expectedDrop Expected energy drop (50 for most specs, 25 for some)
	 * @param timeoutMs Maximum time to wait for energy drop
	 * @return true if energy dropped as expected
	 */
	private boolean executeSpecialAttackWithEnergyConfirmation(Npc target, int expectedDrop, int timeoutMs) {
		int energyBefore = Combat.getSpecialAttackPercent();
		int expectedEnergyAfter = energyBefore - expectedDrop;

		Log.info("Executing special attack - Current: " + energyBefore + "%, Expected after: " + expectedEnergyAfter + "%");

		if (!target.interact("Attack")) {
			Log.warn("Failed to interact with target for special attack");
			return false;
		}

		// Wait for energy drop
		boolean energyDropped = Waiting.waitUntil(timeoutMs, () -> {
			int currentEnergy = Combat.getSpecialAttackPercent();
			boolean dropped = currentEnergy <= expectedEnergyAfter;

			if (dropped) {
				Log.info("Special attack energy confirmed - " + energyBefore + "% → " + currentEnergy + "%");
			}

			return dropped;
		});

		if (!energyDropped) {
			int finalEnergy = Combat.getSpecialAttackPercent();
			Log.warn("Special attack energy drop timeout - Expected: " + expectedEnergyAfter + "%, Actual: " + finalEnergy + "%");
		}

		return energyDropped;
	}

	private boolean useSpecialAttackOnCorpPreActivated(Npc corp) {
		try {
			int energyBefore = Combat.getSpecialAttackPercent();
			Log.info("POH Special attack - Energy before: " + energyBefore + "%");

			if (!attackCorpIfVisible(corp)) {
				Log.warn("Failed to attack Corp with special");
				return false;
			}

			// 🔥 WAIT FOR ENERGY DROP
			boolean specExecuted = Waiting.waitUntil(5000, () -> {
				int currentEnergy = Combat.getSpecialAttackPercent();
				// 1.9.90: accept "energy at floor" as confirmation. Pre-activation can drain
				// energyBefore to ~0 before this check, masking the actual spec fire.
				boolean energyDropped = currentEnergy < energyBefore || currentEnergy <= 5;

				if (energyDropped) {
					Log.info("POH Special attack confirmed - Energy: " + energyBefore + "% → " + currentEnergy + "%");
					return true;
				}
				return false;
			});

			if (specExecuted) {
				// Brief wait for animation to complete after energy drop
				Waiting.waitUntil(2000, () -> !MyPlayer.isAnimating());
				return true;
			} else {
				Log.warn("POH Special attack timed out - no energy drop detected");
				return false;
			}

		} catch (Exception e) {
			Log.error("Exception during special attack: " + e.getMessage());
			return false;
		}
	}

	private void handleTeleportingToHouse() {
		Log.info("Teleporting to house for restoration cycle " + (currentRestorationCycle + 1)
				+ " (pohSource=" + getPohSource() + ")");

		// 1.9.99.18: short-circuit if we're already in the right house.
		// Saves a wasted house tab and ~5-10s of unnecessary travel. Three
		// common cases from script start: (a) already in friend's house
		// w/ friend pohSource, (b) already in own house w/ own pohSource,
		// (c) already in own house w/ friend pohSource (skip to portal
		// walk step instead of tabbing back out and back in).
		// User: "what if we are already outside the house do we just
		// waste a tab? if we are outside the portal? or what if we start
		// inside our friends house?"
		boolean ownHouseMode = isOwnHouseMode();
		if (isInFriendHouse() && !ownHouseMode) {
			Log.info("Already in friend's house — skipping tab tele");
			currentHouseEntryAttempts = 0;
			currentState = BotState.USING_ORNATE_POOL;
			return;
		}
		if (isInOwnHouse() && ownHouseMode) {
			Log.info("Already in own house — skipping tab tele");
			currentHouseEntryAttempts = 0;
			currentState = BotState.USING_ORNATE_POOL;
			return;
		}
		if (isInOwnHouse() && !ownHouseMode) {
			Log.info("Already in own house (friend mode) — skipping tab tele, "
					+ "going straight to portal");
			currentHouseEntryAttempts = 0;
			currentState = BotState.ENTERING_FRIEND_HOUSE;
			return;
		}

		if (teleportToHouse()) {
			Log.info("Successfully teleported to house");
			currentHouseEntryAttempts = 0;
			// Own house: tab lands us inside, skip the friend's-house portal step.
			// Friend / bot-host: walk to the portal and authenticate / resolve host.
			currentState = ownHouseMode
					? BotState.USING_ORNATE_POOL
					: BotState.ENTERING_FRIEND_HOUSE;
		} else {
			// 1.9.99.179: route to BANKING_AND_HEALING (recoverable from
			// anywhere) instead of WAITING_FOR_TEAM (lobby-only).
			Log.error("Failed to teleport to house — routing to BANKING_AND_HEALING");
			emergencyResetPOHSystem();
			currentState = BotState.BANKING_AND_HEALING;
		}
	}

	private void handleEnteringFriendHouse() {
		String hostName = getEffectiveFriendName();
		// 1.9.7: this method ran every main-loop tick (~50ms). Pre-1.9.7
		// every entry logged "Attempting to enter..." even when the throttle
		// blocked the actual attempt — 10+ identical log lines per second
		// of waiting. Move the log INSIDE the actual-attempt branch.

		if (hostName == null || hostName.trim().isEmpty()) {
			// 1.9.99.179: route to BANKING_AND_HEALING (recoverable from
			// anywhere) instead of WAITING_FOR_TEAM (lobby-only).
			Log.error("No host name resolved (pohSource=" + getPohSource()
					+ ") — routing to BANKING_AND_HEALING");
			emergencyResetPOHSystem();
			currentState = BotState.BANKING_AND_HEALING;
			return;
		}

		if (isInFriendHouse()) {
			Log.info("Already in " + hostName + "'s house");
			currentState = BotState.USING_ORNATE_POOL;
			return;
		}

		if (currentHouseEntryAttempts >= MAX_HOUSE_ENTRY_ATTEMPTS) {
			Log.error("Exceeded maximum house entry attempts - ending restoration");
			// 1.9.10: route back to Corp via Games necklace instead of stranding
			// the bot at the friend's-house portal area waiting for teammates
			// that aren't there. Pre-1.9.10 transitioned to WAITING_FOR_TEAM
			// directly, but WAITING_FOR_TEAM expects the bot to be in Corp's
			// lobby — outside the cave, the team-detection logic finds no
			// acceptable teammates and the bot waits forever.
			emergencyResetPOHSystem();
			Log.warn("Restoration failed (host probably offline). Falling back to "
					+ "Games-necklace tele to Corp and finishing in melee.");
			Optional<InventoryItem> necklace = Query.inventory()
					.nameContains("Games necklace").findFirst();
			if (necklace.isPresent() && necklace.get().click("Corporeal Beast")) {
				// 1.9.99: verify the tele actually landed. Pre-1.9.99 we
				// transitioned to WAITING_FOR_TEAM unconditionally — if the
				// click registered but tele never completed (lag, interrupt,
				// random delay > 10s), bot stayed at the friend's-house area
				// in WAITING_FOR_TEAM state. handleWaitingForTeam then logs
				// "No acceptable teammates found AND Corp not visible —
				// staying in lobby..." every 5s forever because none of the
				// state-recovery paths trigger from outside the cave.
				boolean arrived = Waiting.waitUntil(10000, () -> isAtCorp());
				if (arrived) {
					currentState = BotState.WAITING_FOR_TEAM;
				} else {
					Log.warn("Games-necklace tele didn't land within 10s — banking trip instead");
					currentState = BotState.BANKING_AND_HEALING;
				}
			} else {
				Log.error("No Games necklace for fallback tele — banking trip");
				currentState = BotState.BANKING_AND_HEALING;
			}
			return;
		}

		long timeSinceLastAttempt = System.currentTimeMillis() - lastHouseEntryAttempt;
		if (timeSinceLastAttempt < HOUSE_ENTRY_RETRY_DELAY_MIN) {
			// 1.9.7: was logging spam every 50ms tick; silent throttle now.
			return;
		}

		currentHouseEntryAttempts++;
		lastHouseEntryAttempt = System.currentTimeMillis();
		Log.info("Attempting to enter " + hostName + "'s house (mode=" + getPohSource()
				+ ", attempt " + currentHouseEntryAttempts + ")");

		if (enterFriendHouse()) {
			Log.info("Successfully entered " + hostName + "'s house");
			currentState = BotState.USING_ORNATE_POOL;
		} else {
			Log.warn("Failed to enter house, will retry in " +
					(HOUSE_ENTRY_RETRY_DELAY_MIN / 1000) + "-" +
					(HOUSE_ENTRY_RETRY_DELAY_MAX / 1000) + " seconds");

			long waitTime = TribotRandom.uniform(HOUSE_ENTRY_RETRY_DELAY_MIN, HOUSE_ENTRY_RETRY_DELAY_MAX);
			lastHouseEntryAttempt = System.currentTimeMillis() + waitTime - HOUSE_ENTRY_RETRY_DELAY_MIN;
		}
	}

	/** Tracks how long we've held in the pool waiting for teammates so we
	 *  don't hold forever if a teammate disconnects or dies. */
	private long poolWaitStartedAt = 0;
	private static final long POOL_WAIT_MAX_MS = 90_000; // 90s hard cap
	// 1.9.90: mutual-wait timeout — if both us and a teammate are at the pool
	// for >30s, break the standoff. Avoids the deadlock where each bot is
	// "waiting for teammates" while teammates are also at the pool waiting.
	private static final long POOL_MUTUAL_WAIT_MS = 30_000;

	private void handleUsingOrnatePool() {
		// 1.9.99.130: location guard. If we end up in this state at Ferox
		// (state bled in from a stale flow, or the friend's-house entry
		// failed and we landed somewhere weird), useOrnatePool would
		// happily drink the Ferox Pool of Refreshment (its "Drink"
		// action matches the filter), restore stats, then try the ornate
		// jewellery box which doesn't exist at Ferox, looping forever.
		// User: "the bot keeps trying to use ornate pool at the ferox
		// locaiton and it rbeaks." Bail out to BANKING_AND_HEALING which
		// owns the Ferox flow.
		try {
			if (isAtFeroxEnclave()) {
				Log.warn("handleUsingOrnatePool entered while at Ferox — bailing to BANKING_AND_HEALING (state was stale)");
				emergencyResetPOHSystem();
				currentState = BotState.BANKING_AND_HEALING;
				return;
			}
		} catch (Exception ignored) {}

		// 1.9.99.138: dynamic POH stagger. If another player is visible
		// in this POH, wait before drinking the pool — this naturally
		// staggers our return-to-Corp time so multi-bot teams keep
		// continuous DPS on Corp. Re-check every 1s up to
		// pohOccupiedMaxWaitSec. User: "if we are in the PoH at the
		// same time as another player we add x delay before continuing."
		int staggerSec = settings != null ? settings.pohOccupiedDelaySec : 0;
		int maxWaitSec = settings != null ? settings.pohOccupiedMaxWaitSec : 30;
		if (staggerSec > 0) {
			String selfName = MyPlayer.getUsername();
			long waitDeadline = System.currentTimeMillis() + (long) maxWaitSec * 1000;
			long firstWaitedAt = 0;
			while (running && System.currentTimeMillis() < waitDeadline) {
				// 1.9.99.183: treat unnamed players as "other" — name can be
				// briefly null during loading or rendering edge cases, and
				// counting that as "no one here" would let two bots drink
				// the pool simultaneously. Audit LOW #19.
				boolean otherPlayerInPoh = Query.players().stream()
						.anyMatch(p -> {
							String n = p.getName();
							return n == null || !n.equals(selfName);
						});
				if (!otherPlayerInPoh) {
					if (firstWaitedAt != 0) {
						Log.info("POH stagger: clear after "
								+ ((System.currentTimeMillis() - firstWaitedAt) / 1000)
								+ "s — drinking pool now");
					}
					break;
				}
				if (firstWaitedAt == 0) {
					firstWaitedAt = System.currentTimeMillis();
					Log.info("POH stagger: another player present — delaying pool drink (max "
							+ maxWaitSec + "s)");
				}
				// 1.9.99.180: poll in 500ms slices so we honour the deadline,
				// react to script-stop, and re-check player presence promptly
				// instead of blocking for the full staggerSec.
				long sliceDeadline = Math.min(
						waitDeadline,
						System.currentTimeMillis() + (long) staggerSec * 1000);
				while (running && System.currentTimeMillis() < sliceDeadline) {
					Waiting.wait(500);
				}
				if (!running) break;
			}
			if (System.currentTimeMillis() >= waitDeadline) {
				Log.warn("POH stagger: hit " + maxWaitSec + "s max wait — drinking pool anyway");
			}
		}

		// Step 1: drink the pool if we haven't yet.
		boolean specFull = Combat.getSpecialAttackPercent() >= 100;
		if (!specFull) {
			Log.info("Using ornate pool for restoration");
			if (useOrnatePool()) {
				Log.info("Successfully used ornate pool (including 0.6s wait)");
				poolWaitStartedAt = 0; // reset wait timer for next time
				// 1.9.78: fresh dice rolls for the new restoration trip.
				resetSpecPreActivationRolls();
				// 1.9.99.106: roll per-trip plans for combat pot + weapon
				// swap location alongside the spec stage rolls.
				rollTripTimingPlans();
				// 1.9.99.106: combat pot drink — if our plan is
				// HOUSE_POST_POOL and we're not already boosted, drink
				// here. Stats need restoring after the pool tops us off
				// (super combat boost decays naturally over time, and
				// we may have just come from a kill with depleted stats).
				maybeDrinkCombatPotAtHouse();
				// Stage A: roll right after the pool fills us up.
				maybePreActivateSpecStageA();
			} else {
				Log.error("Failed to use ornate pool - ending restoration");
				emergencyResetPOHSystem();
				currentState = BotState.EMERGENCY_ESCAPE;
				return;
			}
		}

		// Step 2: optionally wait for teammates to finish restoring before we
		// tele back. Useful when only one bot owns the POH and others need to
		// arrive and use the pool too.
		if (settings.waitForTeammateSpec && settings.coordinatorEnabled) {
			if (poolWaitStartedAt == 0) poolWaitStartedAt = System.currentTimeMillis();
			long waitedMs = System.currentTimeMillis() - poolWaitStartedAt;

			if (waitedMs < POOL_WAIT_MAX_MS && teammatesNeedPoolRestoration()) {
				// 1.9.90: if we've waited >30s AND any teammate is also at the pool
				// (USING_ORNATE_POOL state), break the mutual standoff and leave.
				if (waitedMs >= POOL_MUTUAL_WAIT_MS && teammatesAlsoAtPool()) {
					Log.warn("Mutual pool standoff detected after "
							+ (waitedMs / 1000) + "s — leaving without further wait");
				} else {
					Log.info("Holding at pool — waiting for teammates to refresh spec ("
							+ (waitedMs / 1000) + "s)");
					return;
				}
			}
			if (waitedMs >= POOL_WAIT_MAX_MS) {
				Log.warn("Pool wait timeout — leaving without all teammates restored");
			}
			poolWaitStartedAt = 0;
		}

		currentState = BotState.TELEPORTING_BACK_TO_CORP;
	}

	/** True if any tracked bot teammate's snapshot shows spec &lt; 100 and they're
	 *  somewhere in the restoration pipeline (PoH-related states). */
	private boolean teammatesNeedPoolRestoration() {
		if (coordinator == null) return false;
		try {
			TeamState ts = coordinator.read();
			if (ts == null || ts.accounts == null) return false;
			long now = System.currentTimeMillis();
			String myName = MyPlayer.getUsername();

			for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
				if (e.getKey().equals(myName)) continue;
				AccountSnapshot snap = e.getValue();
				if (snap == null) continue;
				if (now - snap.lastUpdate > INTERNAL_COORD_STALE_THRESHOLD_MS) continue;

				// Only count bots the user explicitly named in botTeammates.
				if (settings.botTeammates != null && !settings.botTeammates.isEmpty()
						&& !settings.botTeammates.contains(e.getKey())) {
					continue;
				}

				if (snap.specPct < 100 && isRestorationState(snap.botState)) {
					return true;
				}
			}
		} catch (Exception ex) {
			Log.warn("teammatesNeedPoolRestoration: " + ex.getMessage());
		}
		return false;
	}

	// 1.9.90: helper for mutual pool-wait timeout. True if any tracked teammate's
	// state shows they're at the pool right now — we should break the standoff.
	private boolean teammatesAlsoAtPool() {
		if (coordinator == null) return false;
		try {
			TeamState ts = coordinator.read();
			if (ts == null || ts.accounts == null) return false;
			long now = System.currentTimeMillis();
			String myName = MyPlayer.getUsername();
			for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
				if (e.getKey().equals(myName)) continue;
				AccountSnapshot snap = e.getValue();
				if (snap == null) continue;
				if (now - snap.lastUpdate > INTERNAL_COORD_STALE_THRESHOLD_MS) continue;
				if (settings.botTeammates != null && !settings.botTeammates.isEmpty()
						&& !settings.botTeammates.contains(e.getKey())) {
					continue;
				}
				if (snap.botState != null
						&& snap.botState.equals(BotState.USING_ORNATE_POOL.name())) {
					return true;
				}
			}
		} catch (Exception ex) {
			Log.warn("teammatesAlsoAtPool: " + ex.getMessage());
		}
		return false;
	}

	private boolean isRestorationState(String stateName) {
		if (stateName == null) return false;
		return stateName.equals(BotState.PREPARING_RESTORATION_CYCLE.name())
				|| stateName.equals(BotState.TELEPORTING_TO_HOUSE.name())
				|| stateName.equals(BotState.ENTERING_FRIEND_HOUSE.name())
				|| stateName.equals(BotState.USING_ORNATE_POOL.name())
				|| stateName.equals(BotState.TELEPORTING_BACK_TO_CORP.name());
	}

	/** Coordinator-driven host resolver for pohSource=BOT_HOST. Returns the
	 *  RSN of a teammate-bot whose snapshot is (a) fresh, (b) flagged as
	 *  isPohHost, and (c) in our botTeammates list (if configured). Falls
	 *  back to settings.friendName if no host found. */
	private String resolveBotHostName() {
		String fallback = settings.friendName == null ? "" : settings.friendName.trim();
		if (coordinator == null) return fallback;
		try {
			TeamState ts = coordinator.read();
			if (ts == null || ts.accounts == null) return fallback;
			long now = System.currentTimeMillis();
			String myName = MyPlayer.getUsername();

			for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
				if (e.getKey().equals(myName)) continue;
				AccountSnapshot snap = e.getValue();
				if (snap == null || !snap.isPohHost) continue;
				if (now - snap.lastUpdate > INTERNAL_COORD_STALE_THRESHOLD_MS) continue;
				if (settings.botTeammates != null && !settings.botTeammates.isEmpty()
						&& !settings.botTeammates.contains(e.getKey())) continue;
				Log.info("Resolved POH host from coordinator: " + e.getKey());
				return e.getKey();
			}
		} catch (Exception ex) {
			Log.warn("resolveBotHostName: " + ex.getMessage());
		}
		Log.warn("No live POH host found in coordinator — falling back to friendName=" + fallback);
		return fallback;
	}

	/** Returns the RSN we should type into the friend's-house dialog, based
	 *  on the active pohSource. Manual mode uses settings.friendName; bot-host
	 *  mode resolves via coordinator. */
	private String getEffectiveFriendName() {
		if (isBotHostMode()) return resolveBotHostName();
		return settings.friendName == null ? "" : settings.friendName.trim();
	}

	private void emergencyResetPOHSystem() {
		Log.warn("Emergency reset of POH restoration system");

		isInRestorationPhase = false;
		currentRestorationCycle = settings.totalRestorationCycles;
		currentSpecialAttacksUsed = 0;
		currentHouseEntryAttempts = 0;
		needsPoolRestoration = false;

		Log.info("POH system reset - will proceed to normal combat");
	}



	private void handleTeleportingBackToCorp() {
		Log.info("Teleporting back to Corp via ornate jewelry box");

		boolean teleported = useOrnateJewelryBox();
		if (!teleported) {
			// 1.9.99.49: jewellery box failed (off-screen, wrong house tier,
			// transient render issue, ...) — try Games necklace before
			// bailing to emergency Ferox escape. From inside any POH the
			// Games necklace "Corporeal Beast" teleport works exactly like
			// it does from the outside: jumps us to Corp's lobby. User log:
			// "No jewellery box with 'Corporeal Beast' action found ...
			// Failed to interact with jewellery box after 3 attempts ...
			// even though it has 1 games necklace".
			Optional<InventoryItem> necklaceOpt = Query.inventory()
					.nameContains("Games necklace(").findFirst();
			if (necklaceOpt.isPresent()) {
				Log.warn("Jewellery box failed — falling back to Games necklace");
				InventoryItem necklace = necklaceOpt.get();
				if (necklace.click("Corporeal Beast")) {
					teleported = Waiting.waitUntil(10000, () -> isAtCorp());
					if (teleported) {
						Log.info("Successfully teleported back to Corp via Games necklace");
					} else {
						Log.error("Games necklace fallback timed out");
					}
				} else {
					Log.error("Games necklace click failed");
				}
			} else {
				Log.warn("Jewellery box failed and no Games necklace available — bailing to Ferox");
			}
		}

		if (teleported) {
			Log.info("Successfully teleported back to Corp");

			currentRestorationCycle++;
			Log.info("Completed restoration cycle " + currentRestorationCycle
					+ "/" + settings.totalRestorationCycles);
			isInRestorationPhase = false;

			// 1.9.12: ALWAYS go to WAITING_FOR_TEAM after tele back, never
			// straight into another PREPARING_RESTORATION_CYCLE. We just
			// refilled spec — we should USE it on Corp before considering
			// another POH cycle. Pre-1.9.12 the bot would land in Corp's
			// lobby with full spec and immediately try to do "Using initial
			// special attacks (0/4)" — but Corp isn't in render from the
			// lobby tile, so the state would error out with "Corp not found
			// during initial spec phase" and fall through to emergency
			// Ferox tele. The mid-fight restoration trigger in
			// handleFightingCorp will fire ANOTHER POH cycle naturally when
			// the spec bar drains again.
			currentState = BotState.WAITING_FOR_TEAM;
		} else {
			Log.error("Failed to teleport back to Corp - ending restoration");
			emergencyResetPOHSystem();
			currentState = BotState.EMERGENCY_ESCAPE;
		}
	}

	private boolean useSpecialAttackOnCorp(Npc corp) {
		try {
			if (!tryActivateSpec()) { // 1.9.34: debounced
				Log.warn("Failed to activate special attack");
				return false;
			}

			if (!attackCorpIfVisible(corp)) {
				Log.warn("Failed to attack Corp with special");
				return false;
			}

			boolean attackExecuted = Waiting.waitUntil(3000, () ->
					MyPlayer.isAnimating() || isPlayerInCombat());

			if (attackExecuted) {
				Waiting.waitUntil(5000, () -> !MyPlayer.isAnimating());
				return true;
			}

			return false;

		} catch (Exception e) {
			Log.error("Exception during special attack: " + e.getMessage());
			return false;
		}
	}


	private boolean teleportToHouse() {
		Log.info("Teleporting to house using house tab...");

		// 1.9.23: close bank if open before clicking the house tab.
		if (Bank.isOpen()) {
			Log.info("Bank still open — closing before house tele");
			Bank.close();
			Waiting.waitUntil(2000, () -> !Bank.isOpen());
		}

		Optional<InventoryItem> houseTabOpt = Query.inventory()
				.nameEquals("Teleport to house")
				.findFirst();

		boolean ownHouse = isOwnHouseMode();

		if (!houseTabOpt.isPresent()) {
			// 1.9.90: fall back to Construct. cape "Tele to POH" when out of house tabs.
			// Try inventory item click first (works if cape is unequipped); otherwise
			// route through the equipment slot via Query.equipment().
			Optional<InventoryItem> invCape = Query.inventory().nameContains("Construct. cape").findFirst();
			if (invCape.isPresent()) {
				Log.info("No house tab — using Construct. cape (inventory) fallback");
				if (invCape.get().click("Tele to POH") || invCape.get().click("Teleport")) {
					return Waiting.waitUntil(8000, () -> isInOwnHouse());
				}
			}
			if (Equipment.contains("Construct. cape") || Equipment.contains("Construct. cape(t)")) {
				Log.info("No house tab — using equipped Construct. cape fallback");
				boolean clicked = Query.equipment().nameContains("Construct. cape").findFirst()
						.map(c -> c.click("Tele to POH") || c.click("Teleport"))
						.orElse(false);
				if (clicked) {
					return Waiting.waitUntil(8000, () -> isInOwnHouse());
				}
			}
			Log.error("No 'Teleport to house' found in inventory!");
			return false;
		}

		InventoryItem houseTab = houseTabOpt.get();
		// Own house: use "Inside" so we land on the pool floor directly.
		// Friend / bot-host: use "Outside" so we can click the portal.
		String option = ownHouse ? "Inside" : "Outside";
		if (houseTab.click(option)) {
			Log.info("Used house tab (" + option + "), waiting for arrival...");
			if (ownHouse) {
				return Waiting.waitUntil(8000, () -> isInOwnHouse());
			}
			return Waiting.waitUntil(8000, () -> isAtHousePortal());
		}

		Log.error("Failed to use house teleport tab");
		return false;
	}

	/** True if we're inside our own house — heuristic is any drinkable pool
	 *  being visible (no friend-portal walk needed). 1.9.13: action-based. */
	private boolean isInOwnHouse() {
		// 1.9.99.121: tighten the false-positive. Pre-1.9.99.121 this returned
		// TRUE for ANY game object with a "Drink" action — including the
		// Ferox Pool of Refreshment. User started script at Ferox with low
		// stats; handleStarting at line 4072 called isInOwnHouse(), got
		// TRUE, set state to USING_ORNATE_POOL → bot drank the Ferox pool
		// (spec restored to 100) → transitioned to TELEPORTING_BACK_TO_CORP
		// → useOrnateJewelryBox failed (no box at Ferox) → looped trying.
		// User: "i started the script at ferox and it had our state as
		// using ornate pool and just kept refreshing it because it was
		// gtting spec... cuz we were at the wrong pool."
		// Fix: a real POH always has an Ornate Jewellery Box (or at least
		// some POH-only object). Match on that instead. If your POH lacks
		// a jewellery box, drink-by-action still picks up the pool but
		// isInOwnHouse correctly returns false at Ferox.
		boolean hasJewelleryBox = Query.gameObjects()
				.nameContains("ewellery")
				.findFirst().isPresent();
		boolean hasDrinkablePool = Query.gameObjects()
				.filter(o -> o.getActions().contains("Drink"))
				.findFirst().isPresent();
		return hasJewelleryBox && hasDrinkablePool;
	}

	/**
	 * Simple portal interaction for friend's house
	 */
	private boolean enterFriendHouse() {
		String hostName = getEffectiveFriendName();
		Log.info("Attempting to enter " + hostName + "'s house via portal...");

		// 1.9.7: pre-1.9.7 the filter lambda called portal.interact(...)
		// which actually CLICKS the portal as a side-effect of the filter.
		// Then the code called interact() AGAIN outside the filter — two
		// clicks on the portal in rapid succession. Use the actions list
		// to pick the right portal, then interact exactly once.
		Optional<GameObject> portalOpt = Query.gameObjects()
				.nameEquals("Portal")
				.filter(p -> p.getActions().contains("Friend's house"))
				.findFirst();

		if (!portalOpt.isPresent()) {
			// 1.9.93: portal not interactable. ONLY in this case fall back to
			// the dialog-already-open recovery path. The 1.9.91 version ran
			// the probe before the portal lookup which false-positived on
			// stale chatbox-tree widgets and made the bot type into public
			// chat. Now the probe only runs when the portal genuinely can't
			// be clicked (i.e. the dialog is open and consuming the action),
			// which is exactly the post-break recovery case we want.
			if (isFriendHouseDialogOpen()) {
				Log.info("Portal not interactable but friend-house dialog "
						+ "appears open — dispatching name-dialog handler");
				return handleFriendNameDialog();
			}
			Log.error("No Portal with 'Friend's house' action found "
					+ "and no friend-house dialog detected");
			return false;
		}

		if (!portalOpt.get().interact("Friend's house")) {
			Log.error("Failed to interact with portal");
			return false;
		}
		Log.info("Clicked 'Friend's house', waiting for dialog...");

		// 1.9.35.2: detect the dialog by widget TEXT content under root 162
		// rather than by IndexPath. User confirmed via widget inspector
		// screenshot that the dialog opens correctly (162.43 = "Enter name:"
		// and 162.39 = "Last name: <host>") but 1.9.35.1's IndexPath-based
		// filters never fired — likely because getIndexPath() in this SDK
		// returns the path WITHIN the root (e.g. [39] not [162, 39]) so
		// length==2 && [1]==X conditions never matched. Text-based detection
		// is index-path agnostic and works whatever the SDK encodes:
		//   - "Enter name:" is the dialog's static prompt label
		//   - "Last name:" prefix is the host-shortcut row
		// Either being present in root 162 means the dialog is open.
		final String hostLower2 = getEffectiveFriendName() == null
				? "" : getEffectiveFriendName().toLowerCase();
		boolean dialogOpened = Waiting.waitUntil(8000, () -> {
			try {
				return Query.widgets()
						.inRoots(162)
						.filter(w -> {
							String raw = w.getText().orElse("");
							if (raw.isEmpty()) return false;
							String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
							return clean.contains("enter name")
									|| clean.contains("last name")
									|| (!hostLower2.isEmpty() && clean.contains(hostLower2));
						})
						.findFirst()
						.isPresent();
			} catch (Exception e) {
				return false;
			}
		});
		if (!dialogOpened) {
			Log.warn("Friend's-house dialog did not open within 8s "
					+ "(no 'Enter name:' / 'Last name:' / host-name text "
					+ "found anywhere under root 162)");
			return false;
		}
		// 1.9.56: wait specifically for the SHORTCUT widget. Pre-1.9.56's
		// 'shortcut OR input-empty' escape failed when input was empty at
		// the readiness check but got populated by the server BEFORE
		// handleFriendNameDialog ran. Just always wait for the shortcut.
		// 1.9.99.81: REVERTED 1.9.99.79's timeout reduction. User saw
		// typoed usernames after the timeout cut to 1.2s — the typing
		// fallback fired before the dialog input field was actually
		// keyboard-focused, so keystrokes landed in the wrong target
		// (public chat or stale buffer). Original 8s is the safe value:
		// shortcut nearly always renders within ~1 tick, so the wait
		// returns fast in the common case; the worst case is a 5s
		// delay on slow renders, which is preferable to mistyping.
		// User: "the previous version was better."
		final String hostForReady = getEffectiveFriendName();
		boolean shortcutReady = Waiting.waitUntil(8000, () -> {
			try {
				return findFriendHouseShortcutByText(hostForReady).isPresent();
			} catch (Exception e) {
				return false;
			}
		});
		if (!shortcutReady) {
			Log.info("Shortcut widget didn't render within 8s — "
					+ "proceeding anyway (will type or press Enter)");
			Waiting.waitNormal(400, 120);
		} else {
			Log.debug("Shortcut widget ready");
			Waiting.waitNormal(120, 50);
		}
		return handleFriendNameDialog();
	}

	/**
	 * Simple dialog handling for friend name input
	 */
	/** 1.9.42: detect typed text in the friend-house input field by SCANNING
	 *  widget text under root 162 for the host name itself. Pre-1.9.42 we
	 *  tried to find the input widget by Widget.getName() returning
	 *  "Chatbox.MES_TEXT2" — turns out that's the widget-inspector display
	 *  string, not what the SDK exposes at runtime. So readFriendHouseInputText
	 *  always returned "" and the "don't re-type if input already has text"
	 *  gate was a no-op. User: "the name was already there and we still
	 *  tried to type."
	 *
	 *  New approach: walk every widget under root 162 and return the FIRST
	 *  text content that:
	 *    - contains the host name as alphanumerics (case-insensitive), AND
	 *    - does NOT start with "Enter name:" (the prompt label), AND
	 *    - does NOT start with "Last name:" (the shortcut row)
	 *  Anything else carrying the host name has to be the typed input.
	 *  Returns "" if no such widget exists (input is empty / dialog not
	 *  open / no host name typed yet). */
	private String readFriendHouseInputText() {
		String hostName = getEffectiveFriendName();
		if (hostName == null) return "";
		final String hostStripped = hostName
				.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		if (hostStripped.isEmpty()) return "";
		try {
			return Query.widgets()
					.inRoots(162)
					.filter(w -> {
						String raw = w.getText().orElse("");
						if (raw.isEmpty()) return false;
						String clean = raw.replaceAll("<[^>]*>", "").trim();
						String lower = clean.toLowerCase();
						if (lower.startsWith("enter name")) return false;
						if (lower.startsWith("last name")) return false;
						// 1.9.99: skip chat-history scrollback. Chat messages all
						// start with a timestamp like "[10:27]" and frequently
						// include the host's RSN (drop announcements, public chat,
						// trade msgs). User log showed
						// "[10:27] <friend-host> received a drop: 30 x Tuna potato..."
						// matching here and blocking the friend-house entry for
						// three attempts until the script gave up. The input field
						// itself only contains the typed name (no timestamp).
						if (clean.startsWith("[")) return false;
						if (lower.contains("received a drop")) return false;
						// Input fields are short — the typed name plus cursor.
						// Chat messages are routinely 40+ chars. Cap defensively.
						if (clean.length() > 32) return false;
						String alphanum = lower.replaceAll("[^A-Za-z0-9]", "");
						return alphanum.contains(hostStripped);
					})
					.findFirst()
					.flatMap(w -> w.getText())
					.map(s -> s.replaceAll("<[^>]*>", "")
							.replaceAll("[*|_]", "")
							.trim())
					.orElse("");
		} catch (Exception e) {
			return "";
		}
	}

	/** 1.9.91: detect whether the friend-house name-entry dialog is currently
	 *  open. Same text-content probe used by enterFriendHouse / dialogStillOpen:
	 *  any descendant of root 162 carrying "Enter name:" or "Last name:" text
	 *  proves the dialog is up (public chat doesn't have those labels). Used
	 *  to recover from post-break states where the portal was already clicked
	 *  before the break and the dialog persists into the next run. */
	private boolean isFriendHouseDialogOpen() {
		try {
			return Query.widgets()
					.inRoots(162)
					.filter(w -> {
						String raw = w.getText().orElse("");
						if (raw.isEmpty()) return false;
						String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
						return clean.contains("enter name")
								|| clean.contains("last name");
					})
					.findFirst()
					.isPresent();
		} catch (Exception e) {
			return false;
		}
	}

	/** 1.9.46: find the friend-house "Last name: <host>" shortcut widget
	 *  by TEXT content under root 162 (IndexPath-agnostic — same reason
	 *  1.9.35.2 dropped IndexPath checks). Clicking this widget submits
	 *  the friend name without needing keyboard focus on the canvas,
	 *  which is more reliable than Keyboard.pressEnter() after a context
	 *  switch from the portal interact. */
	private Optional<Widget> findFriendHouseShortcutByText(String hostName) {
		if (hostName == null) return Optional.empty();
		final String hostLower = hostName.toLowerCase();
		try {
			return Query.widgets()
					.inRoots(162)
					.filter(w -> {
						String raw = w.getText().orElse("");
						if (raw.isEmpty()) return false;
						String clean = raw.replaceAll("<[^>]*>", "")
								.toLowerCase();
						return clean.startsWith("last name")
								&& clean.contains(hostLower);
					})
					.findFirst();
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private boolean handleFriendNameDialog() {
		String hostName = getEffectiveFriendName();
		Log.info("Dialog appeared for host=" + hostName + ", checking widget shortcut first...");

		// 1.9.39: refined input check. Only skip the typing path if the
		// existing input text is ALREADY CORRECT (== hostName, ignoring
		// case and cursor markers). If it's correct, press Enter to submit
		// and wait for entry to resolve. If it's wrong/partial, it's a
		// leftover from a prior attempt — wait for the dialog to clear
		// itself before doing anything. If empty, proceed to the normal
		// shortcut-click / type path. Pre-1.9.39 we skipped on ANY
		// non-empty input which got the bot stuck when the input had
		// garbage like a stray "*" cursor.
		String existingInput = readFriendHouseInputText();
		String hostStripped = hostName == null
				? "" : hostName.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		String inputStripped = existingInput
				.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		if (!inputStripped.isEmpty()) {
			if (inputStripped.equals(hostStripped)) {
				Log.info("Friend-house input already correct (\"" + existingInput
						+ "\") — submitting");
				// 1.9.77: per user 'its not typing in the name' — when
				// the shortcut isn't rendered, bare Keyboard.pressEnter()
				// no-ops because canvas focus is lost. Instead RETYPE
				// the name and then press Enter. typeString restores
				// canvas focus by sending keypresses, after which Enter
				// reliably submits even with the same "<friend-host>" already
				// in the buffer (the chat input handles repeat input
				// without doubling because the dialog submits on Enter,
				// not on each keystroke).
				Optional<Widget> shortcutByText = findFriendHouseShortcutByText(hostName);
				if (shortcutByText.isPresent()) {
					Log.info("Clicking 'Last name:' shortcut to submit");
					shortcutByText.get().click();
				} else {
					Log.info("No shortcut widget — retyping name + Enter");
					try {
						Keyboard.typeString(hostName);
						Waiting.waitNormal(150, 50);
						Keyboard.pressEnter();
					} catch (Exception ignored) {}
				}
				return Waiting.waitUntil(5000, () -> isInFriendHouse());
			} else {
				Log.info("Friend-house input has stale/wrong text \""
						+ existingInput + "\" (want \"" + hostName
						+ "\") — waiting for it to clear, not typing");
				return Waiting.waitUntil(10000, () -> {
					if (isInFriendHouse()) return true;
					String fresh = readFriendHouseInputText();
					return fresh.replaceAll("[^A-Za-z0-9]", "").isEmpty();
				}) && isInFriendHouse();
			}
		}

		// 1.9.15 / 1.9.16: search root 162 (chatbox) for an INTERACTIVE
		// widget whose text contains the host name. Pre-1.9.16 we just
		// matched text containing "<friend-host>" — but that also matches the
		// static text-label widget which is NOT clickable. Clicking a
		// non-interactive widget falls through to a "click ground" which
		// makes the bot walk off into the distance. Now we require at
		// least one action OR an explicit visible/clickable state, so
		// the label widget gets filtered out.
		final String hostLower = hostName == null ? "" : hostName.toLowerCase();
		// 1.9.20: target the CHILD at [162, 39, X] where the text actually
		// lives — empty parent [162, 39] matched in 1.9.19 but clicking it
		// fell through to a ground click. The user identified the text as
		// living at [162, 39, 0]. Match any child of [162, 39] whose text
		// (after stripping color tags) contains the host name, case-
		// insensitive.
		// 1.9.24: broadened from path [162, 39, 0] to ANY descendant of
		// [162, 39] whose text contains the host name. User reported the
		// shortcut visible on screen but the bot typed anyway — the strict
		// path[2]==0 filter missed the widget because the dialog's child
		// index isn't always 0. Match anywhere under [162, 39, *].
		Optional<Widget> friendWidgetOpt = Query.widgets()
				.inRoots(162)
				.filter(w -> w.getIndexPath().length >= 2
						&& w.getIndexPath()[1] == 39)
				.filter(w -> {
					String raw = w.getText().orElse("");
					String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
					return clean.contains(hostLower);
				})
				.findFirst();
		if (friendWidgetOpt.isPresent()) {
			int[] path = friendWidgetOpt.get().getIndexPath();
			String txt = friendWidgetOpt.get().getText().orElse("")
					.replaceAll("<[^>]*>", "").trim();
			Log.info("Matched friend shortcut widget path "
					+ java.util.Arrays.toString(path) + " text=\"" + txt + "\"");
		} else {
			Log.info("No widget under [162, 39, *] matching host name " + hostName);
		}

		if (friendWidgetOpt.isPresent()) {
			Log.info("Found friend widget shortcut for " + hostName + ", clicking it...");
			Widget friendWidget = friendWidgetOpt.get();
			friendWidget.click();

			return Waiting.waitUntil(8000, () -> {
				if (isInFriendHouse()) {
					Log.info("Successfully entered " + hostName + "'s house via widget");
					return true;
				}
				return false;
			});
		}

		// Fallback: Type host's name manually
		Log.info("No widget shortcut found, attempting to type host name: " + hostName);

		// 1.9.71: safety gate by TEXT content, not IndexPath. User: 'the
		// issue is now if we dont have the friends name then we dont
		// type it in.' The IndexPath check at [162, 44] failed to match
		// in this SDK (same reason as 1.9.55 / 1.9.42's dialog-open
		// fixes), so the bot refused to type even when the dialog was
		// clearly open. Now: confirm the dialog is open by searching
		// for the "Enter name:" prompt or "Last name:" shortcut text
		// under root 162. Either being present proves we're on the
		// friend-house dialog (not public chat — public chat doesn't
		// have those labels), safe to type.
		boolean dialogOpen = Query.widgets()
				.inRoots(162)
				.filter(w -> {
					String raw = w.getText().orElse("");
					if (raw.isEmpty()) return false;
					String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
					return clean.contains("enter name")
							|| clean.contains("last name");
				})
				.findFirst()
				.isPresent();
		if (!dialogOpen) {
			Log.warn("Friend-house dialog not open (no 'Enter name:' / "
					+ "'Last name:' text under root 162) — refusing to type");
			return false;
		}
		Log.info("Verified friend-house dialog open by text, typing now");

		// 1.9.41.1: 3-5 second dialog wait, NOT 5 minutes — user clarified
		// "we want to wait no more than 3-5 SECONDS." The teleport drops
		// us right next to the portal so the dialog should open within a
		// couple of ticks; if it doesn't, the click missed or the host
		// dropped the dialog and we should bail to a fresh attempt
		// rather than camping for minutes.
		int longDialogWait = 5_000;
		boolean dialogStillOpen = Waiting.waitUntil(longDialogWait, () -> {
			if (isInFriendHouse()) return true;
			// dialog open evidence: the input widget OR a friend shortcut
			// OR any descendant of root 162 with "Enter name:" text.
			try {
				return Query.widgets()
						.inRoots(162)
						.filter(w -> {
							String raw = w.getText().orElse("");
							if (raw.isEmpty()) return false;
							String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
							return clean.contains("enter name")
									|| clean.contains("last name");
						})
						.findFirst().isPresent();
			} catch (Exception e) {
				return false;
			}
		});
		if (isInFriendHouse()) return true;
		if (!dialogStillOpen) {
			Log.warn("Friend-house dialog never opened — aborting");
			return false;
		}

		// 1.9.41: gate the actual typing on the input being empty. If text
		// is already in it, skip — wait for the previous entry to resolve.
		String preTypeInput = readFriendHouseInputText();
		if (!preTypeInput.replaceAll("[^A-Za-z0-9]", "").isEmpty()) {
			Log.info("Input already has text \"" + preTypeInput
					+ "\" at type-time — not typing, waiting for resolve");
			// 1.9.41.1: 5s, not 60s — keep waits short, retry quickly.
			return Waiting.waitUntil(5_000, () -> isInFriendHouse());
		}

		try {
			// 1.9.7 / 1.9.11: pre-typing settle delay so the input field
			// is focused before we start typing.
			Waiting.waitNormal(550, 150);
			Keyboard.typeString(hostName);
			Waiting.waitNormal(900, 200);

			// 1.9.21: re-verify input field still exists before pressing
			// Enter. If the dialog closed between typing and Enter (e.g.
			// timeout), pressing Enter could send the buffered text to
			// public chat.
			boolean stillInDialog = Query.widgets()
					.inRoots(162)
					.filter(w -> w.getIndexPath().length == 2
							&& w.getIndexPath()[1] == 44)
					.findFirst().isPresent();
			if (!stillInDialog) {
				Log.warn("Dialog closed between typing and Enter — NOT pressing Enter");
				return false;
			}
			Keyboard.pressEnter();

			Log.info("Typed host name and pressed Enter, waiting for entry...");

			return Waiting.waitUntil(8000, () -> {
				if (isInFriendHouse()) {
					Log.info("Successfully entered " + hostName + "'s house");
					return true;
				}

//				// Check for error messages
//				if (Chatbox.isOpen()) {
//					String message = Chatbox.getMessage();
//					if (message.contains("not online") || message.contains("not found")) {
//						Log.warn("Friend not available: " + message);
//						return false;
//					}
//				}

				return false;
			});

		} catch (Exception e) {
			Log.error("Exception handling friend name dialog: " + e.getMessage());
			return false;
		}
	}


	/**
	 * Simplified banking - ignore house tabs completely
	 */
	private boolean hasRequiredItemsWithPOH() {
		// 1.9.99.122: added karambwan check. Pre-1.9.99.122 the gate accepted
		// any inventory with the spec weapon + 1 combat pot + 2 restores +
		// 10 sharks — but NOT karams. So a user who started the script
		// after dying/manual play with 0 karams in inventory would skip the
		// bank trip, POH for spec, and engage Corp without combo-eat food.
		// Karams are critical (combo-eat covers Corp's burst), so missing
		// them = high death risk. User: "if we are spec dumping and we
		// start the script and dont have full spec; after making sure we
		// hav a full invneoty/supplies we should poh to get full spec."
		// Now any sub-target karam count routes through BANKING_AND_HEALING
		// (Ferox trip restocks karams + uses pool which restores spec)
		// before continuing to Corp.
		boolean hasBasicItems = hasAnyOwnedSpecWeapon() &&
				(Inventory.contains(RUNE_POUCH) || Inventory.contains(DIVINE_RUNE_POUCH)) &&
				hasChargedRingOfDueling() &&
				hasChargedGamesNecklace() &&
				Inventory.getCount(getCombatPotionNames()) >= INTERNAL_TARGET_SUPER_COMBAT &&
				Inventory.getCount(SUPER_RESTORE_NAMES) >= INTERNAL_TARGET_SUPER_RESTORES &&
				Inventory.getCount("Cooked karambwan") >= INTERNAL_TARGET_KARAMBWANS &&
				hasMinimumFood();

		// Check for house tabs but don't require specific amount
		boolean hasPOHItems = hasHouseTeleportTab();

		if (!hasPOHItems) {
			Log.warn("No house tabs found - POH restoration will be skipped");
		}

		return hasBasicItems; // Continue even without house tabs
	}

	/**
	 * Simple ornate pool usage
	 */
	private boolean useOrnatePool() {
		// 1.9.7.1: match by ACTION ("Drink").
		// 1.9.8: retry up to 3 times; refuse to tele back un-restored.
		// 1.9.13: use LEFT-click (click()) instead of right-click menu.
		// 1.9.14: settle delay before the first click. We've just entered
		// the house — the pool object may render a tick or two after the
		// player tile updates, and clicking too early either hits empty
		// ground (walk-here) or misses the object entirely.
		Waiting.waitNormal(700, 200);
		final int MAX_DRINK_ATTEMPTS = 3;
		for (int attempt = 1; attempt <= MAX_DRINK_ATTEMPTS; attempt++) {
			// 1.9.99.130: filter by name (Ornate pool of Rejuvenation +
			// other POH tier names) instead of just "has Drink action".
			// Pre-1.9.99.130 the broad filter matched the Ferox Pool of
			// Refreshment, drinking the wrong pool when state was stale
			// at Ferox. POH pool names include: "Ornate pool of
			// Rejuvenation" (best), "Pool of Revitalisation",
			// "Pool of Restoration", "Pool of Refreshment" — wait that
			// last name is shared with Ferox. Match by "Rejuvenation"
			// only since that's what the user has; if a lower-tier
			// owner reports failure we expand. Ferox = "Pool of
			// Refreshment", will never match "Rejuvenation".
			Optional<GameObject> poolOpt = Query.gameObjects()
					.nameContains("Rejuvenation")
					.filter(o -> o.getActions().contains("Drink"))
					.findFirst();
			if (!poolOpt.isPresent()) {
				Log.error("No drinkable pool found in render (attempt " + attempt + ")");
				return false;
			}
			GameObject pool = poolOpt.get();
			Log.info("Left-clicking " + pool.getName()
					+ " for restoration (attempt " + attempt + "/" + MAX_DRINK_ATTEMPTS + ")");

			if (!pool.click()) {
				Log.warn("Pool left-click failed (attempt " + attempt + ")");
				Waiting.waitNormal(800, 200);
				continue;
			}

			// Wait for restoration — full bar, full HP, full prayer.
			// 15s on attempt 1 (we may need to walk to the pool first); 10s
			// for retries when we're already adjacent.
			long waitMs = (attempt == 1) ? 15000 : 10000;
			boolean restored = Waiting.waitUntil((int) waitMs, () -> {
				boolean specOk = Combat.getSpecialAttackPercent() >= 100;
				boolean hpOk = MyPlayer.getCurrentHealth() >= Skill.HITPOINTS.getActualLevel();
				boolean prayerOk = Prayer.getPrayerPoints() >= Skill.PRAYER.getActualLevel();
				return specOk && hpOk && prayerOk;
			});

			if (restored) {
				Log.info("Successfully restored at " + pool.getName()
						+ " (spec=" + Combat.getSpecialAttackPercent()
						+ "%, hp=" + MyPlayer.getCurrentHealth()
						+ ", prayer=" + Prayer.getPrayerPoints() + ")");
				Waiting.waitUniform(600, 600); // settle before jewellery box
				return true;
			}
			Log.warn("Restoration didn't complete on attempt " + attempt
					+ " (spec=" + Combat.getSpecialAttackPercent()
					+ "%, hp=" + MyPlayer.getCurrentHealth()
					+ ", prayer=" + Prayer.getPrayerPoints() + ") — will retry");
			Waiting.waitNormal(800, 200);
		}
		Log.error("Pool restoration failed after " + MAX_DRINK_ATTEMPTS
				+ " attempts — refusing to tele back un-restored");
		return false;
	}

	/**
	 * Simple jewelry box interaction
	 */
	private boolean useOrnateJewelryBox() {
		// 1.9.7.1: match by ACTION ("Corporeal Beast") instead of name.
		// The actual object is "Ornate Jewellery Box" (capital J, B) — the
		// pre-1.9.7.1 nameEquals("Ornate jewellery box") would mismatch
		// case. Action match handles any jewellery-box tier.
		// 1.9.99.40: wait for the pool-drink animation lock to clear before
		// clicking the box. The 600ms settle in useOrnatePool isn't always
		// enough; if the player is still mid-animation the box click either
		// fails or registers on the pool below. User: "i also noticed a
		// timeout at the pool in the house wehre i got stuck watiing for a
		// good couple seconds because i either misclicked on it or clicked
		// on the jewlwery box too quickly after clicking the pool". Also
		// retry on miss — the box is a fixed POH object so if it's not
		// found, it's a transient render issue.
		Waiting.waitUntil(2000, () -> !MyPlayer.isAnimating());
		final int MAX_ATTEMPTS = 3;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Optional<GameObject> jewelryBoxOpt = Query.gameObjects()
					.filter(o -> o.getActions().contains("Corporeal Beast"))
					.findFirst();

			if (!jewelryBoxOpt.isPresent()) {
				Log.warn("No jewellery box with 'Corporeal Beast' action found "
						+ "(attempt " + attempt + "/" + MAX_ATTEMPTS + ")");
				Waiting.waitNormal(600, 200);
				continue;
			}

			GameObject box = jewelryBoxOpt.get();
			Log.info("Using " + box.getName() + " to teleport to Corp "
					+ "(attempt " + attempt + "/" + MAX_ATTEMPTS + ")");
			if (box.interact("Corporeal Beast")) {
				Log.info("Selected Corporeal Beast teleport, waiting for arrival...");
				// 1.9.99.106: stage A.5 — overlap the spec activation with
				// the tele animation/wait. The click already registered;
				// we have ~3-5s of tele animation before isAtCorp() can
				// return true. Firing a spec-bar click in that window
				// looks like natural multi-tasking instead of a fixed
				// pool-side ritual.
				maybePreActivateSpecStageA5();
				if (Waiting.waitUntil(10000, () -> isAtCorp())) {
					return true;
				}
				Log.warn("Teleport-to-Corp didn't land within 10s (attempt "
						+ attempt + "/" + MAX_ATTEMPTS + ")");
			} else {
				Log.warn("interact(box) returned false (attempt "
						+ attempt + "/" + MAX_ATTEMPTS + ") — likely animation lock or stale click");
				Waiting.waitNormal(700, 200);
			}
		}

		Log.error("Failed to interact with jewellery box after " + MAX_ATTEMPTS + " attempts");
		return false;
	}


	/**
	 * Check if we're at the house portal (after teleporting "Outside")
	 */
	private boolean isAtHousePortal() {
		// 1.9.7.1: was a side-effect double-click bug — same shape as the
		// enterFriendHouse one. Calling portal.interact() inside the filter
		// CLICKS the portal as a side-effect of "is this a match" testing.
		// Use getActions().contains() — predicate only, no click.
		return Query.gameObjects()
				.nameEquals("Portal")
				.filter(p -> p.getActions().contains("Friend's house"))
				.findFirst()
				.isPresent();
	}

	/**
	 * Check if we're in friend's house (simplified)
	 */
	private boolean isInFriendHouse() {
		// 1.9.7.1: match by ACTION rather than name. POH pools have "Drink"
		// and POH jewellery boxes have "Corporeal Beast" (among other tele
		// actions).
		// 1.9.99.134: tighten to REQUIRE a jewellery box specifically. Pre-
		// 1.9.99.134 the OR-with-"Drink" matched the Ferox Pool of
		// Refreshment, causing handleTeleportingToHouse's "Already in
		// friend's house — skipping tab tele" false-positive at Ferox.
		// That fed the BANKING→POH→Ferox-pool-guard→BANKING loop in
		// 1.9.99.130. The jewellery box has the "Corporeal Beast" action
		// AND only exists in POHs — perfect discriminator.
		boolean result = Query.gameObjects()
				.filter(o -> o.getActions().contains("Corporeal Beast"))
				.findFirst().isPresent();
		// 1.9.99.155: throttled diagnostic when detection fails. One bot
		// reported "isn't detecting it's in the house" — log what's
		// actually visible so we can see if jewellery box is missing the
		// 'Corporeal Beast' action (e.g. POH tier doesn't have it
		// unlocked, or game objects haven't loaded yet).
		if (!result) logHouseDetectionDiag("isInFriendHouse");
		return result;
	}

	private long lastHouseDiagLogAt = 0;
	private void logHouseDetectionDiag(String caller) {
		long now = System.currentTimeMillis();
		if (now - lastHouseDiagLogAt < 5000) return;
		lastHouseDiagLogAt = now;
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("HOUSE-DIAG [").append(caller).append("] visible objects: ");
			Query.gameObjects().stream()
					.filter(o -> {
						String n = o.getName();
						if (n == null) return false;
						String lc = n.toLowerCase();
						return lc.contains("ewellery") || lc.contains("pool")
								|| lc.contains("portal") || lc.contains("box");
					})
					.limit(20)
					.forEach(o -> sb.append(o.getName())
							.append("[actions=").append(o.getActions()).append("] "));
			Log.info(sb.toString());
		} catch (Exception e) {
			Log.warn("HOUSE-DIAG dump failed: " + e.getMessage());
		}
	}


	/**
	 * Check if we have house tabs (simplified - no need to count)
	 */
	private boolean hasHouseTeleportTab() {
		// 1.9.99.217: filter out noted form — only unnoted tabs can teleport.
		// User pointed out that Inventory.getCount returns the stack size,
		// so a noted stack of 784 was making the bot think it had plenty
		// while every mid-fight teleport attempt failed. Now we look for
		// an actually-usable tab.
		return Query.inventory().nameEquals("Teleport to house")
				.filter(i -> {
					try { return !i.getDefinition().isNoted(); }
					catch (Throwable t) { return true; } // default: usable
				})
				.findFirst().isPresent();
	}




    // ========== DARK CORE TRACKING VARIABLES ==========
    private enum CoreDodgeAxis {NORTH_SOUTH, EAST_WEST, NOT_SET}

    private enum CoreDodgeDirection {NORTH, SOUTH, EAST, WEST}

    private enum CoreDodgeState {DETECTED, DODGING, ATTACKING, EMERGENCY}

    // ========== VENGEANCE STATE TRACKING ==========
    private enum VengeanceState {
        READY_FOR_FIRST_CAST,    // Can cast once when boss dead/in lobby
        ACTIVE_CASTING           // Cast every 31-37s while boss alive
    }

    // ========== STATE MACHINE ==========
	private enum BotState {
		STARTING,
		BANKING_AND_HEALING,
		TRAVELING_TO_CORP,
		PREPARING_RESTORATION_CYCLE,
		USING_INITIAL_SPECS,
		TELEPORTING_TO_HOUSE,
		ENTERING_FRIEND_HOUSE,
		USING_ORNATE_POOL,
		TELEPORTING_BACK_TO_CORP,
		WAITING_FOR_TEAM,
		ENTERING_COMBAT,
		FIGHTING_CORP,
		HANDLING_DARK_CORE,
		USING_SPECIAL_ATTACK,
		LOOTING,
		EMERGENCY_ESCAPE,
		DEATH_RECOVERY,
		W330_RESTORATION
	}

    // ========== CONFIGURABLE SETTINGS ==========
    public static final String[] ALL_SPEC_WEAPONS = {
            "Elder maul", "Dragon warhammer", "Bandos godsword",
            "Arclight", "Darklight", "Emberlight",
            "Crystal halberd", "Dragon halberd"
    };

    /** Spec energy cost per weapon (% of full bar). */
    public static final Map<String, Integer> SPEC_COST;
    /** Which phase each weapon belongs to (1=defense reducer, 2=combat reducer, 3=BGS damage drain, 0=bonus dps). */
    public static final Map<String, Integer> SPEC_PHASE;
    static {
        Map<String, Integer> c = new HashMap<>();
        c.put("Elder maul", 50);
        c.put("Dragon warhammer", 50);
        c.put("Bandos godsword", 50);
        c.put("Arclight", 50);     // 1.9.26: fixed from 25 — Arclight is 50%
        c.put("Darklight", 50);    // 1.9.26: fixed from 25 — Darklight is 50%
        c.put("Emberlight", 25);   // Emberlight (upgraded Arclight) is 25%
        c.put("Crystal halberd", 60);
        c.put("Dragon halberd", 30);
        SPEC_COST = Collections.unmodifiableMap(c);

        Map<String, Integer> p = new HashMap<>();
        p.put("Elder maul", 1);
        p.put("Dragon warhammer", 1);
        p.put("Arclight", 2);
        p.put("Darklight", 2);
        p.put("Emberlight", 2);
        p.put("Bandos godsword", 3);
        p.put("Crystal halberd", 0);   // bonus DPS, not a phase
        p.put("Dragon halberd", 0);
        SPEC_PHASE = Collections.unmodifiableMap(p);
    }

    /** All user-tweakable values. Defaults match the original hardcoded constants. */
    public static class CorpSettings {
        // POH / Team
        public String friendName = "";
        public List<String> acceptableTeammates = new ArrayList<>();
        // 1.8.8: no longer the primary loop driver — it's now a safety upper
        // bound. The real termination is phase targets met OR Corp HP <
        // corpMinHpForSpec. Bumped 3 → 10 so it never triggers in practice;
        // 10 cycles per kill is well above the realistic ceiling.
        public int totalRestorationCycles = 10;
        public int specialAttacksPerCycle = 2;

        // Combat
        public String mainWeapon = "Osmumten's fang (or)";
        // 1.9.99.68: user-designated dark-core killer. The bot swaps to
        // this weapon (from inventory) when a dark core lands adjacent.
        // If left as the default Elder maul / Dragon warhammer is used.
        // User: "i also noticed that it is still not vengeancing in the
        // last phases ... wire in the settings field for user designated
        // core killing weapon".
        public String coreKillerWeapon = "Elder maul";
        public String[] foodNames = new String[]{ "Shark", "Cooked karambwan" };

        // Inventory targets
        public int targetSharks = 10;
        public int targetKarambwans = 9;
        public int targetSuperRestores = 2;
        public int targetSuperCombat = 1;
        public int minFoodCount = 10;
        public int minPrayerDoses = 4;

        // Health / prayer thresholds
        public int eatBelowMaxHp = 21;            // eat when currentHp <= maxHp - this
        public int emergencyHpThreshold = 15;
        public int drinkPrayerThreshold = 20;
        // 1.9.99.89: spec-dump-specific panic-tele threshold. During the
        // spec dump cycle (spec weapon equipped + energy >= floor +
        // phase incomplete), combo-eat at HP <= 50 is already skipped
        // — but the bot still panic-teled at HP <= 25 (the general
        // INTERNAL_PANIC_TELE_HP). User wanted a higher threshold for
        // spec dumps because the first few Arclight/Darklight specs
        // happen before Corp's stats are reduced, so HP frequently
        // dips into the 30-50 range. Default 35: at HP <= 35 during
        // spec dump, eat once + tele out. Outside spec dump, normal
        // panic-tele at 25 still applies. User: "I'd prefer it it was
        // a customizable option of panic tele out during spec dumping
        // if we go under < HP ... If we get under 35 HP teleport out."
        public int specDumpPanicTeleHp = 35;

        // Spec
        public int minSpecEnergy = 50;
        // 1.8.8: this is the restoration-loop termination floor, not a per-spec
        // cooldown. Corp's stat reductions persist for the whole kill but its
        // HP regens, so the right time to stop dumping defense/attack-reducer
        // specs and join melee is when Corp's HP has already dropped (a real
        // teammate is actively damaging it). 1700 means "Corp lost ~15% HP →
        // stop spec dumping, start meleeing."
        // 1.9.99.150: lowered default 1700 → 1500. Spec dumping with 1700
        // means ~300 HP into a kill we abandon specs; a staggered teammate
        // hit can punt Corp under 1700 while we still have specs to fire.
        // 1500 ≈ Corp lost ~25%, gives more spec-dump runway before we
        // commit to melee. Still editable in the GUI.
        public int corpMinHpForSpec = 1500;

        // 1.9.99.137: startup stagger for multi-bot teams. Deprecated by
        // 1.9.99.138 dynamic POH stagger — left as-is for users who want
        // a fixed initial offset. Set to 0 to disable.
        public int initialTripStaggerSec = 0;

        // 1.9.99.138: dynamic POH stagger. When entering the ornate pool,
        // if ANY other player is visible in the POH, wait this many
        // seconds before drinking — re-checks each second until either
        // the POH clears OR we hit pohOccupiedMaxWaitSec. Naturally
        // staggers our return-to-Corp time so multi-bot teams maintain
        // continuous DPS on Corp. Set pohOccupiedDelaySec > 0 to enable.
        // User: "if we are in the PoH at the same time as another player
        // we add x delay before continuing as the easiest fix."
        public int pohOccupiedDelaySec = 0; // 0 disables the feature
        public int pohOccupiedMaxWaitSec = 30; // safety cap so we don't sit forever

        // 1.9.99.141: encroachment relocate threshold in tile-counts
        // (Chebyshev — max of |dx|, |dy|). User: "i dont know if thats
        // possible. there isnt portions of tiles in runescape." Switched
        // from Euclidean (fractional, confusing) to Chebyshev (integer
        // tile counts, matches OSRS mental model). Threshold 3 means
        // "another player within 3 tiles on either axis = relocate."
        // 8-cardinal layout adjacency:
        //   - on top of us (same tile) → Chebyshev 0
        //   - adjacent diagonal/cardinal → Chebyshev 1
        //   - adjacent cardinal slot (3 tiles N while we're 3 W) → 3
        //   - opposite cardinal → 6
        // Default 3 catches "on top + nearby + adjacent cardinal slot".
        public int encroachmentRelocateTiles = 3;

        // Dark core strategy (Phase G).
        // false = modern attack-and-step (Elder maul / DWH burst, kill core mid-air).
        // true  = legacy on-tile sidestep dodge (preserved as fallback).
        public boolean useLegacyDarkCoreLogic = false;

        // Vengeance.
        // useVengeance: cast vengeance during combat (requires Lunars + runes).
        // corpLowHealthVengStop: stop casting once Corp HP drops below this (absolute HP).
        public boolean useVengeance = true;
        public int corpLowHealthVengStop = 85;

        // Combat potion family. Resolved via getCombatPotionNames() into
        // "<type> potion(4)" / (3) / (2) / (1) variants. Editable via GUI.
        public String combatPotionType = "Divine super combat";

        // Status overlay (small always-on-top window with live counters).
        public boolean showOverlay = true;

        // PoH source. One of:
        //   OWN_HOUSE     - this account has an ornate pool in its own house.
        //   FRIEND_HOUSE  - enter manually-named friend's house via portal.
        //   BOT_HOST      - enter a teammate-bot's house (resolved from coordinator).
        //   W330_RANDOM   - hop to W330 + Rimmington portal + random advertiser (1.7.0).
        //   FEROX_ONLY    - skip POH entirely; rely on Ferox's Pool of Refreshment
        //                   for HP/prayer (spec only refills via natural regen).
        public String pohSource = POH_SOURCE_OWN_HOUSE;

        // Coordinator role flag: this bot's POH is the team's restoration host.
        // When other bots are configured pohSource=BOT_HOST, they look for the
        // teammate snapshot with isPohHost=true and use that account's RSN as
        // the friend's-house entry name.
        public boolean isPohHost = false;

        // Deprecated as of 1.6.0 — kept for profile-load backward compatibility.
        // Migrated to pohSource=OWN_HOUSE on first load. Do not read in new code;
        // use pohSource directly.
        public boolean useOwnHouse = false;

        // 1.9.13: poolName / jewelleryBoxName retired. Pool detection now
        // matches by ACTION ("Drink") and jewellery box by action
        // ("Corporeal Beast"), so the name is irrelevant. Fields kept (not
        // deleted) only for backwards compatibility with old saved profiles
        // that still reference them; no production code reads them.
        @Deprecated public String poolName = "";
        @Deprecated public String jewelleryBoxName = "";

        // Wait at the pool for teammates with low spec before teleporting back.
        // Requires coordinatorEnabled. Useful when only one account owns the POH.
        public boolean waitForTeammateSpec = false;

        // W330_RANDOM mode settings.
        // designatedWorld: world the bot returns to after using the W330 random POH.
        //                  0 means "remember whichever world we were on when restoration started".
        // w330MaxHostAttempts: how many random advertisers to try before giving up
        //                  on a restoration cycle (skips to FIGHTING_CORP).
        public int designatedWorld = 0;
        public int w330MaxHostAttempts = 3;

        // Loot
        public List<String> valuableLoot = new ArrayList<>(Arrays.asList(
                "Spectral sigil", "Arcane sigil", "Elysian sigil", "Spirit shield",
                "Cannonball", "Mystic robe top", "Mystic robe bottom"));

        // ===== Per-account & multi-account coordination =====
        // Which spec weapons THIS account owns.
        public Map<String, Boolean> availableSpecWeapons = new LinkedHashMap<>();
        // Role hint. "auto" means decide based on available weapons.
        public String accountRole = "auto"; // auto | stat_drainer | finisher | dps
        // Bot teammates (RSNs of OTHER bot accounts you run with).
        public List<String> botTeammates = new ArrayList<>();
        // Coordinator toggle + tuning.
        public boolean coordinatorEnabled = false;
        public int coordinatorWriteIntervalTicks = 5;
        // 1.9.99.201: bumped 10_000 -> 30_000. The previous 10s window dropped
        // bots from the aggregate every time the main loop stalled longer
        // than 10s (banking, POH portal sequences, long walks, eat chains).
        // While dropped, the OTHER bot computed teamPhaseNeeded from its own
        // snapshot only and could re-spec phases the dropped bot already
        // finished. 30s covers all normal sequences; combined with the
        // heartbeat thread (1.9.99.201) it should be effectively zero false
        // staleness.
        public int coordinatorStaleThresholdMs = 30_000;

        // 1.9.76: port-based coordinator (replaces / supplements file).
        // When useCoordinatorPort = true, host bot opens a TCP server on
        // port (45000 + coordinatorPortId), client bots connect and
        // exchange AccountSnapshots in real time. File coordinator is
        // still written as a backup. coordinatorPortId 1-99, host IP
        // defaults to 127.0.0.1 (same machine); set to public IP +
        // port-forward 45000+ID for multi-machine.
        public boolean useCoordinatorPort = false;
        public boolean coordinatorIsHost = false;
        public int coordinatorPortId = 1;
        public String coordinatorHostIp = "127.0.0.1";
        // 1.9.99.182: auto-elect host. Try connecting to hostIp:port first;
        // if no one's there, bind locally and become host. Works for both
        // same-machine (loopback) and cross-machine (LAN/public IP) setups.
        // When true, coordinatorIsHost is ignored.
        public boolean autoElectCoordinator = true;

        // ===== Multi-phase spec targets (Phase B) =====
        // 1.9.99.226: autoDetectTeamSpecs toggles how the targets below are
        // interpreted. TRUE (default) = current behavior: phase1/phase2/phase3
        // are TEAM-WIDE aggregate targets, kill phase triggers once the
        // team's combined specs+BGS damage meet them. Effective targets get
        // divided by (1 + real teammates) so adding humans shortens the
        // grind. FALSE = per-bot manual override: this bot uses ITS OWN
        // counts vs ITS OWN target spinners, completely ignoring the team
        // aggregate and the real-teammate multiplier. Use FALSE when
        // playing with humans whose specs aren't reported to the
        // coordinator — set each bot's targets to exactly what THAT
        // account should do per kill (e.g. 0 phase 2 for a no-Arclight bot,
        // high phase 3 BGS so it keeps draining while teammates finish).
        public boolean autoDetectTeamSpecs = true;
        // Phase 1: DWH+Elder maul specs (team-wide aggregate if
        // autoDetectTeamSpecs=true, this bot's personal target if false).
        public int phase1TargetSpecs = 4;
        // Phase 2: Arclight+Darklight+Emberlight specs (team-wide
        // aggregate or this bot's personal target — see autoDetectTeamSpecs).
        // Default 20 assumes Emberlight; with only Darklight, bump to 30-40.
        public int phase2TargetSpecs = 20;
        // Phase 3: BGS damage drained (team-wide aggregate or this bot's
        // personal target — see autoDetectTeamSpecs).
        public int phase3TargetBgsDamage = 200;

        public CorpSettings() {
            // Default weapon availability — flip to true the ones most setups have.
            availableSpecWeapons.put("Elder maul", true);
            availableSpecWeapons.put("Dragon warhammer", false);
            availableSpecWeapons.put("Bandos godsword", true);
            availableSpecWeapons.put("Arclight", false);
            availableSpecWeapons.put("Darklight", true);
            availableSpecWeapons.put("Emberlight", false);
            availableSpecWeapons.put("Crystal halberd", false);
            availableSpecWeapons.put("Dragon halberd", false);
        }
    }

    // ========== TEAM COORDINATOR ==========

    /** Snapshot of one bot account, written to the shared file. */
    public static class AccountSnapshot {
        public long lastUpdate;
        /** 1.9.99.210: which kill the spec counters in this snapshot belong to.
         *  Set at publish time from the bot's localKillId. The display
         *  aggregate filters out snapshots whose killId doesn't match the
         *  viewing bot's own killId — stops mid-kill flicker when one bot
         *  finishes a kill and resets its specsThisKill before the other
         *  has detected the kill end. */
        public long killId;
        public int specPct;
        public String botState;
        public List<String> availableWeapons = new ArrayList<>();
        public Map<String, Integer> specsThisKill = new LinkedHashMap<>();
        public int bgsDamageDealt;
        /** Phase E: which Corp-position offset (from CORP_POSITION_OFFSETS) this
         *  bot has claimed. Null/empty when not engaged. Format: [dx, dy] relative
         *  to Corp's spawn tile. Other bots avoid the same offset. */
        public int[] claimedCorpOffset;
        /** 1.6.0: this bot owns the POH the team uses for restoration. Other
         *  bots configured with pohSource=BOT_HOST will resolve to whichever
         *  live teammate has this set to true. */
        public boolean isPohHost;
        /** 1.7.1: this bot has hit an unrecoverable supply failure and is
         *  shutting down. Other bots see this and gracefully end after
         *  their current kill rather than continuing one account short. */
        public boolean sessionEndRequested;
        public String sessionEndReason;
        /** 1.9.99.164: true when this bot's isInKillPhase() returned true
         *  on the most recent publish (Corp HP < spec floor OR team
         *  phase targets met). Other bots arriving from lobby/POH read
         *  this via the coordinator and skip spec-weapon prep — go
         *  straight to main-weapon swap on engage. */
        public boolean inKillPhase;
    }

    /** Whole shared file shape. Serialized as JSON via ScriptSettings' Gson. */
    public static class TeamState {
        public long killId = 0;
        public long killStartedAt = 0;
        public Map<String, AccountSnapshot> accounts = new LinkedHashMap<>();
    }

    /** Aggregated counts derived from live (non-stale) entries in TeamState. */
    public static class TeamAggregate {
        public int phase1Specs;     // sum of Elder maul + Dragon warhammer
        public int phase2Specs;     // sum of Arclight + Darklight + Emberlight
        public int phase3BgsDamage; // sum of BGS damage drained
        public int liveAccounts;    // count of bots with fresh snapshots
        public boolean anyTeamDamage; // true if any bot has BGS damage > 0 or specsThisKill > 0
    }

    /** Coordinator handles read/write of the shared team-state files.
     *  1.9.99.195: per-bot files. Each bot writes ONLY its own file
     *  (corp_team_state_<sanitized-rsn>.json). read() scans the directory,
     *  merges all per-bot files into a unified TeamState. Eliminates the
     *  write-race that caused "Coordinator publish failed" on Windows when
     *  two bots both tried to atomic-rename the same shared file. The old
     *  single file is still read (if present) for legacy compatibility. */
    private static class CorpCoordinator {
        private final java.io.File dir;
        private final java.io.File legacyFile;
        private final long staleThresholdMs;
        private static final String PER_BOT_PREFIX = "corp_team_state_";
        private static final String PER_BOT_SUFFIX = ".json";

        CorpCoordinator(java.io.File file, long staleThresholdMs) {
            this.legacyFile = file;
            this.dir = file.getParentFile();
            this.staleThresholdMs = staleThresholdMs;
        }

        /** Sanitize an RSN to a safe filename component. Strips anything
         *  that could be path-troublesome on Windows or Linux. */
        private static String sanitizeName(String name) {
            if (name == null) return "unknown";
            return name.replaceAll("[^a-zA-Z0-9_-]", "_");
        }

        private java.io.File perBotFile(String accountName) {
            return new java.io.File(dir, PER_BOT_PREFIX + sanitizeName(accountName) + PER_BOT_SUFFIX);
        }

        private long lastParseWarnAt = 0;
        private TeamState parseFile(java.io.File f) {
            try {
                if (!f.exists()) return null;
                String json = new String(java.nio.file.Files.readAllBytes(f.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (json.trim().isEmpty()) return null;
                com.google.gson.stream.JsonReader reader =
                        new com.google.gson.stream.JsonReader(new java.io.StringReader(json));
                reader.setLenient(true);
                return new com.google.gson.Gson().fromJson(reader, TeamState.class);
            } catch (Exception e) {
                // 1.9.99.211: log corrupt shards (throttled to 1/5s) so user
                // can see when a shard fails to parse — pre-fix this was a
                // silent data drop. If a shard is persistently corrupt the
                // file would re-fail every read; rate limit so logs don't
                // get flooded.
                long now = System.currentTimeMillis();
                if (now - lastParseWarnAt > 5000) {
                    lastParseWarnAt = now;
                    Log.warn("[Coord/FILE] failed to parse shard " + f.getName()
                            + ": " + e.getMessage());
                }
                return null;
            }
        }

        synchronized TeamState read() {
            TeamState merged = new TeamState();
            if (merged.accounts == null) merged.accounts = new LinkedHashMap<>();
            if (dir == null || !dir.exists()) return merged;
            // 1.9.99.195: scan all per-bot shards + legacy file, merge.
            java.io.File[] shards = dir.listFiles((d, name) ->
                    name != null && name.startsWith(PER_BOT_PREFIX) && name.endsWith(PER_BOT_SUFFIX));
            if (shards != null) {
                for (java.io.File shard : shards) {
                    TeamState s = parseFile(shard);
                    if (s == null) continue;
                    if (s.killId > merged.killId) {
                        merged.killId = s.killId;
                        merged.killStartedAt = s.killStartedAt;
                    }
                    if (s.accounts != null) {
                        merged.accounts.putAll(s.accounts);
                    }
                }
            }
            // Legacy single-file fallback (pre-1.9.99.195 setups).
            if (legacyFile != null && legacyFile.exists()) {
                TeamState legacy = parseFile(legacyFile);
                if (legacy != null) {
                    if (legacy.killId > merged.killId) {
                        merged.killId = legacy.killId;
                        merged.killStartedAt = legacy.killStartedAt;
                    }
                    if (legacy.accounts != null) {
                        for (Map.Entry<String, AccountSnapshot> e : legacy.accounts.entrySet()) {
                            // Per-bot shards take precedence if same accountName.
                            if (!merged.accounts.containsKey(e.getKey())) {
                                merged.accounts.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                }
            }
            return merged;
        }

        synchronized void publish(String accountName, AccountSnapshot snap, long killId, Set<String> liveBots) {
            if (accountName == null || accountName.isEmpty()) return;
            snap.lastUpdate = System.currentTimeMillis();

            // 1.9.99.195: write only OUR shard — no read-modify-write race.
            TeamState shard = new TeamState();
            shard.killId = killId;
            shard.killStartedAt = System.currentTimeMillis();
            shard.accounts = new LinkedHashMap<>();
            shard.accounts.put(accountName, snap);

            java.io.File target = perBotFile(accountName);
            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(shard);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            // 1.9.99.213-rev: DROPPED the .tmp + ATOMIC_MOVE dance entirely.
            // On Windows the pattern was failing repeatedly with FileSystemException
            // "process cannot access the file" — concurrent readers from other
            // bots ran every loop tick, and Java's MoveFileEx underneath ATOMIC_MOVE
            // is fragile when ANY process has the target handle open. The warnings
            // flooded the log and the shard file stayed stale, making team totals
            // read as 0. Direct write is safer here:
            //   - Files.write(target, ...) opens with FILE_SHARE_READ on Windows,
            //     so concurrent readers don't block the write
            //   - readers use Files.readAllBytes (also FILE_SHARE_WRITE), so a
            //     concurrent write doesn't block reads
            //   - if a reader catches us mid-write and gets torn JSON, parseFile
            //     catches the parse exception and treats that read as no data;
            //     next read (≤3s) gets full data
            // Atomicity was nice-to-have but not essential given the heartbeat
            // republishes every 3s.
            int attempts = 0;
            Exception lastEx = null;
            while (attempts < 3) {
                attempts++;
                try {
                    java.nio.file.Files.write(target.toPath(), bytes);
                    lastEx = null;
                    break;
                } catch (Exception e) {
                    lastEx = e;
                    try { Thread.sleep(20); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
            if (lastEx != null) {
                // Final failure — log throttled so we don't flood (publish runs
                // every 3s from heartbeat, every 5 ticks from main loop).
                long now = System.currentTimeMillis();
                if (now - lastPublishFailWarnAt > 5000) {
                    lastPublishFailWarnAt = now;
                    Log.warn("Coordinator publish failed for " + target.getName()
                            + " after " + attempts + " retries: " + lastEx.getMessage());
                }
            }
        }

        private long lastPublishFailWarnAt = 0;

        TeamAggregate aggregate(TeamState state) {
            TeamAggregate a = new TeamAggregate();
            if (state == null || state.accounts == null) return a;
            long now = System.currentTimeMillis();
            for (AccountSnapshot snap : state.accounts.values()) {
                if (now - snap.lastUpdate > staleThresholdMs) continue;
                a.liveAccounts++;
                a.phase3BgsDamage += snap.bgsDamageDealt;
                if (snap.bgsDamageDealt > 0) a.anyTeamDamage = true;
                if (snap.specsThisKill != null) {
                    for (Map.Entry<String, Integer> e : snap.specsThisKill.entrySet()) {
                        Integer phase = SPEC_PHASE.get(e.getKey());
                        if (phase == null) continue;
                        if (e.getValue() > 0) a.anyTeamDamage = true;
                        if (phase == 1) a.phase1Specs += e.getValue();
                        else if (phase == 2) a.phase2Specs += e.getValue();
                    }
                }
            }
            return a;
        }
    }

    /** 1.9.76: TCP-based coordinator transport. One bot per team runs in
     *  HOST mode (opens ServerSocket on port 45000 + portId). Other bots
     *  run as CLIENTs (connect to host:port). Both send their own
     *  AccountSnapshot, both receive the aggregated TeamState. Same
     *  publish()/read() API as the file CorpCoordinator so the calling
     *  code doesn't change.
     *
     *  Protocol: newline-delimited JSON. Each line is either:
     *    - From client: a JSON-serialized AccountSnapshot (the client's own)
     *    - From host:   a JSON-serialized TeamState (the aggregate)
     *
     *  The host also writes to the file coordinator as a backup so a
     *  restarted host can recover the latest known state.
     */
    private static class CorpPortCoordinator {
        private final int port;
        private final boolean isHost;
        private final String hostIp;
        private final long staleThresholdMs;
        private final CorpCoordinator fileBackup; // host writes through to file

        private volatile TeamState latestState = new TeamState();
        private final Object stateLock = new Object();

        // HOST: server socket + per-client handlers
        private java.net.ServerSocket serverSocket;
        private final java.util.List<java.net.Socket> clientSockets =
                java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        // CLIENT: connection to host
        private java.net.Socket clientSocket;
        private java.io.BufferedWriter clientWriter;
        private java.io.BufferedReader clientReader;
        // 1.9.99.182: pre-connected client socket from auto-elect, consumed on first iteration.
        private java.net.Socket preConnectedClientSocket;

        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        private volatile boolean running = true;

        CorpPortCoordinator(boolean isHost, String hostIp, int portId,
                            long staleThresholdMs, CorpCoordinator fileBackup) {
            this(isHost, hostIp, portId, staleThresholdMs, fileBackup, null, null);
        }

        // 1.9.99.182: extended constructor accepting pre-acquired sockets from
        // the auto-elect factory. preBoundSocket is non-null when we won the
        // bind race (host); preConnected is non-null when we successfully
        // connected to an existing host (client).
        CorpPortCoordinator(boolean isHost, String hostIp, int portId,
                            long staleThresholdMs, CorpCoordinator fileBackup,
                            java.net.ServerSocket preBoundSocket,
                            java.net.Socket preConnected) {
            this.isHost = isHost;
            this.hostIp = hostIp;
            this.port = 45000 + portId;
            this.staleThresholdMs = staleThresholdMs;
            this.fileBackup = fileBackup;
            if (preBoundSocket != null) this.serverSocket = preBoundSocket;
            if (preConnected != null) this.preConnectedClientSocket = preConnected;
            start();
        }

        /** 1.9.99.182: try-connect-first / fall-back-to-bind election.
         *  Connects to hostIp:port with a short timeout — if a host is already
         *  listening, we become CLIENT using the established socket.
         *  Otherwise we bind locally and become HOST. Retries on the small race
         *  window where both connect and bind fail (another bot grabbed the
         *  port between our two syscalls). Returns null if all attempts fail.
         *  Works for same-machine (hostIp=127.0.0.1) and cross-machine (LAN/
         *  public IP) without changing the code path. */
        static CorpPortCoordinator autoElect(String hostIp, int portId,
                                             long staleThresholdMs,
                                             CorpCoordinator fileBackup) {
            int port = 45000 + portId;
            java.net.ServerSocket boundSocket = null;
            java.net.Socket connectedSocket = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    java.net.Socket s = new java.net.Socket();
                    s.connect(new java.net.InetSocketAddress(hostIp, port), 1500);
                    connectedSocket = s;
                    Log.info("[Coord/AUTO] Host detected at " + hostIp + ":" + port
                            + " — joining as CLIENT");
                    break;
                } catch (Exception ce) {
                    try {
                        boundSocket = new java.net.ServerSocket(port);
                        Log.info("[Coord/AUTO] No host at " + hostIp + ":" + port
                                + " — self-elected as HOST");
                        break;
                    } catch (java.io.IOException be) {
                        Log.info("[Coord/AUTO] Bind race lost on port " + port
                                + " — retrying connect (attempt " + (attempt + 1) + "/5)");
                        try { Thread.sleep(200 + (long)(Math.random() * 200)); }
                        catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    }
                }
            }
            if (boundSocket == null && connectedSocket == null) {
                Log.warn("[Coord/AUTO] Election failed after 5 attempts — falling back to file-only");
                return null;
            }
            boolean amHost = boundSocket != null;
            return new CorpPortCoordinator(amHost, hostIp, portId, staleThresholdMs,
                    amHost ? fileBackup : null, boundSocket, connectedSocket);
        }

        private void start() {
            if (isHost) startHost();
            else startClient();
        }

        private void startHost() {
            Thread t = new Thread(() -> {
                try {
                    // 1.9.99.182: reuse the pre-bound socket from autoElect if present.
                    if (serverSocket == null) {
                        serverSocket = new java.net.ServerSocket(port);
                    }
                    Log.info("[Coord/HOST] Listening on port " + port);
                    while (running) {
                        try {
                            java.net.Socket sock = serverSocket.accept();
                            clientSockets.add(sock);
                            Log.info("[Coord/HOST] Client connected: "
                                    + sock.getRemoteSocketAddress());
                            handleClient(sock);
                        } catch (Exception e) {
                            if (running) Log.warn("[Coord/HOST] accept error: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    Log.warn("[Coord/HOST] startup failed: " + e.getMessage());
                }
            }, "corp-coord-host");
            t.setDaemon(true);
            t.start();
        }

        private void handleClient(java.net.Socket sock) {
            // 1.9.99.211: track client's account name so we can clean up its
            // snapshot from latestState when this connection drops. Without
            // this, a disconnected bot's stale snapshot kept being counted
            // in the team aggregate for the full stale window (30s).
            final String[] clientAccountName = new String[]{ null };
            Thread t = new Thread(() -> {
                try {
                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(sock.getInputStream(),
                                    java.nio.charset.StandardCharsets.UTF_8));
                    String line;
                    while (running && (line = in.readLine()) != null) {
                        try {
                            // Client message format: {"name":"...","snapshot":{...},"killId":N}
                            ClientMessage msg = gson.fromJson(line, ClientMessage.class);
                            if (msg != null && msg.name != null && msg.snapshot != null) {
                                clientAccountName[0] = msg.name; // 1.9.99.211 for cleanup
                                synchronized (stateLock) {
                                    if (latestState.accounts == null) {
                                        latestState.accounts = new LinkedHashMap<>();
                                    }
                                    // 1.9.99.209: only advance team killId — do NOT
                                    // wipe peer accounts' specs here. Each bot is
                                    // responsible for its own per-kill reset via its
                                    // own coordinatorOnKillEnded(). Wiping peers caused
                                    // mid-kill flicker: when Bot A advanced killId at
                                    // kill-end, Bot B's snapshot got cleared on the
                                    // host even though B was still fighting, and B's
                                    // overlay showed 0 until B caught up.
                                    if (msg.killId > latestState.killId) {
                                        latestState.killId = msg.killId;
                                        latestState.killStartedAt = System.currentTimeMillis();
                                    }
                                    msg.snapshot.lastUpdate = System.currentTimeMillis();
                                    latestState.accounts.put(msg.name, msg.snapshot);
                                }
                                broadcastState();
                            }
                        } catch (Exception e) {
                            // 1.9.99.211: include socket remote address so
                            // multi-client debug points at the right bot.
                            String remote;
                            try { remote = sock.getRemoteSocketAddress().toString(); }
                            catch (Throwable t2) { remote = "?"; }
                            Log.warn("[Coord/HOST] parse error from " + remote
                                    + " (acct=" + clientAccountName[0] + "): "
                                    + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // disconnect — normal
                } finally {
                    clientSockets.remove(sock);
                    try { sock.close(); } catch (Exception ignored) {}
                    // 1.9.99.211: remove the disconnected bot's snapshot
                    // from latestState immediately. Pre-fix it sat there
                    // for the full stale window (30s), keeping zombie
                    // specs in the team aggregate.
                    if (clientAccountName[0] != null) {
                        synchronized (stateLock) {
                            if (latestState.accounts != null) {
                                latestState.accounts.remove(clientAccountName[0]);
                            }
                        }
                        try { broadcastState(); } catch (Throwable ignored) {}
                    }
                }
            }, "corp-coord-client-handler");
            t.setDaemon(true);
            t.start();
        }

        private void broadcastState() {
            String json;
            synchronized (stateLock) {
                json = gson.toJson(latestState);
            }
            String line = json + "\n";
            byte[] bytes = line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            synchronized (clientSockets) {
                java.util.Iterator<java.net.Socket> it = clientSockets.iterator();
                while (it.hasNext()) {
                    java.net.Socket s = it.next();
                    try {
                        s.getOutputStream().write(bytes);
                        s.getOutputStream().flush();
                    } catch (Exception e) {
                        it.remove();
                        try { s.close(); } catch (Exception ignored) {}
                    }
                }
            }
            // 1.9.99.209: REMOVED the host mirror to file backup. With per-bot
            // shards (1.9.99.195) every bot writes its OWN file shard via its
            // own coordinatorPublish() — the host mirror was racing the
            // client's own write to the SAME shard file, causing repeated
            // "Coordinator publish failed: ...tmp -> ...json" warnings from
            // failed ATOMIC_MOVE on Windows. The race also left stale shards
            // on disk, which made the file-coord aggregate read inconsistent
            // numbers between bots. Per-bot writers are sufficient: clients'
            // heartbeats (3s) keep their shards fresh, and the host's own
            // coordinatorPublish() writes the host's shard. No mirror needed.
        }

        private void startClient() {
            Thread t = new Thread(() -> {
                // 1.9.90: exponential backoff with jitter. Flat 5s caused a stampede
                // when the host was offline (all clients reconnecting in lockstep).
                long backoff = 1000;
                while (running) {
                    try {
                        // 1.9.99.182: consume the pre-connected socket from autoElect on first iteration.
                        if (preConnectedClientSocket != null) {
                            clientSocket = preConnectedClientSocket;
                            preConnectedClientSocket = null;
                        } else {
                            clientSocket = new java.net.Socket(hostIp, port);
                        }
                        clientWriter = new java.io.BufferedWriter(
                                new java.io.OutputStreamWriter(clientSocket.getOutputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        clientReader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(clientSocket.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        Log.info("[Coord/CLIENT] Connected to " + hostIp + ":" + port);
                        backoff = 1000; // 1.9.90: reset on successful connect
                        // Reader loop: receive aggregated state from host.
                        String line;
                        while (running && (line = clientReader.readLine()) != null) {
                            try {
                                TeamState ts = gson.fromJson(line, TeamState.class);
                                if (ts != null) {
                                    synchronized (stateLock) { latestState = ts; }
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception e) {
                        Log.warn("[Coord/CLIENT] connection error: " + e.getMessage()
                                + " — reconnecting in " + backoff + "ms");
                    }
                    // Close and back off before reconnect.
                    try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
                    clientWriter = null;
                    clientReader = null;
                    try { Thread.sleep(backoff + (long)(Math.random() * 500)); }
                    catch (InterruptedException ie) { break; }
                    backoff = Math.min(backoff * 2, 60000);
                }
            }, "corp-coord-client");
            t.setDaemon(true);
            t.start();
        }

        synchronized TeamState read() {
            synchronized (stateLock) {
                // Return a defensive copy via gson roundtrip — caller may mutate.
                return gson.fromJson(gson.toJson(latestState), TeamState.class);
            }
        }

        /** 1.9.99.186: clean shutdown so daemon threads exit between script
         *  runs. Pre-1.9.99.186 the host/client threads survived across
         *  script restarts (daemon=true, JVM not killed), leaving zombie
         *  client connections that masqueraded as real teammates on the
         *  next host's accept(). User reported "the other bot never
         *  connected" — that ghost connection was the old client thread
         *  from a previous session. */
        void shutdown() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
            try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
            synchronized (clientSockets) {
                for (java.net.Socket s : clientSockets) {
                    try { s.close(); } catch (Exception ignored) {}
                }
                clientSockets.clear();
            }
        }

        synchronized void publish(String accountName, AccountSnapshot snap,
                                  long killId, Set<String> liveBots) {
            snap.lastUpdate = System.currentTimeMillis();
            if (isHost) {
                // Host: write directly into local state, then broadcast.
                synchronized (stateLock) {
                    if (latestState.accounts == null) {
                        latestState.accounts = new LinkedHashMap<>();
                    }
                    // 1.9.99.209: advance team killId only — don't wipe peers.
                    // See handleClient for full rationale; same race here when
                    // the host advances its own killId first.
                    if (killId > latestState.killId) {
                        latestState.killId = killId;
                        latestState.killStartedAt = System.currentTimeMillis();
                    }
                    latestState.accounts.put(accountName, snap);
                    // 1.9.99.183: removed retainAll. The staleness threshold
                    // (lastUpdate > staleMs filter at aggregate-read time)
                    // already prunes dead bots; retainAll here was asymmetric
                    // with handleClient (which never filtered) and blocked
                    // dynamically-added teammates. Audit MEDIUM #12.
                }
                broadcastState();
            } else {
                // Client: send to host.
                if (clientWriter == null) return; // not connected yet; reader thread will reconnect
                ClientMessage msg = new ClientMessage();
                msg.name = accountName;
                msg.snapshot = snap;
                msg.killId = killId;
                try {
                    clientWriter.write(gson.toJson(msg));
                    clientWriter.write("\n");
                    clientWriter.flush();
                } catch (Exception e) {
                    Log.warn("[Coord/CLIENT] write failed: " + e.getMessage());
                    try { clientSocket.close(); } catch (Exception ignored) {}
                }
            }
        }

        TeamAggregate aggregate(TeamState state) {
            TeamAggregate a = new TeamAggregate();
            if (state == null || state.accounts == null) return a;
            long now = System.currentTimeMillis();
            for (AccountSnapshot snap : state.accounts.values()) {
                if (now - snap.lastUpdate > staleThresholdMs) continue;
                a.liveAccounts++;
                a.phase3BgsDamage += snap.bgsDamageDealt;
                if (snap.bgsDamageDealt > 0) a.anyTeamDamage = true;
                if (snap.specsThisKill != null) {
                    for (Map.Entry<String, Integer> e : snap.specsThisKill.entrySet()) {
                        Integer phase = SPEC_PHASE.get(e.getKey());
                        if (phase == null) continue;
                        if (e.getValue() > 0) a.anyTeamDamage = true;
                        if (phase == 1) a.phase1Specs += e.getValue();
                        else if (phase == 2) a.phase2Specs += e.getValue();
                    }
                }
            }
            return a;
        }

        /** Wire-format wrapper for a client → host snapshot push. */
        private static class ClientMessage {
            String name;
            AccountSnapshot snapshot;
            long killId;
        }
    }

    // Runtime coordinator state (only used when settings.coordinatorEnabled).
    private CorpCoordinator coordinator;
    private CorpPortCoordinator portCoordinator; // 1.9.76: nullable
    // 1.9.99.186: static so we can find and kill the previous run's coord
    // threads when the script is re-started in the same JVM. Without this
    // a zombie client thread from a prior session connects to the new host
    // and pollutes coordination state.
    private static CorpPortCoordinator ACTIVE_PORT_COORD;
    private long localKillId = 0;
    private int coordTickCounter = 0;
    private final AccountSnapshot mySnapshot = new AccountSnapshot();

    // 1.9.99.201: heartbeat publish thread. The main loop's coordinatorPublish
    // can stall for >10s during banks, POH portals, long walks, eat chains
    // — anything that yields to Waiting.waitUntil for a while. While stalled
    // our snapshot ages out of the team aggregate and the OTHER bot's
    // teamPhaseNeeded computes from its own snapshot only (can re-spec
    // phases we already finished). The heartbeat thread re-publishes the
    // existing mySnapshot every ~3s independent of the main loop so our
    // lastUpdate stays fresh even when we're mid-walk/mid-bank. It does
    // NOT touch SDK methods (no Combat/MyPlayer reads from a background
    // thread); it only refreshes the timestamp on the already-populated
    // snapshot. The main loop's coordinatorPublish still owns refreshing
    // the snapshot's fields.
    private volatile Thread coordHeartbeatThread;
    private static final long COORD_HEARTBEAT_INTERVAL_MS = 3_000L;
    /** Username cached by the main loop's coordinatorPublish so the heartbeat
     *  thread can publish without calling MyPlayer.getUsername() (which is
     *  not safe from a non-main thread). Null until the main loop has run
     *  at least one full publish. */
    private volatile String coordPublishedAccountName;

    private void ensureCoordinator() {
        if (coordinator != null) return;
        try {
            java.io.File dir = ScriptSettings.getDefault().getDirectory();
            if (!dir.exists()) dir.mkdirs();
            // 1.9.99.211: delete per-bot shards from previous sessions that
            // are older than 5 minutes. Without this, a fresh script start
            // reads stale data from disk for the first 30s (stale window)
            // — including phantom kill_ids from the prior run that
            // coordinatorTeamKillId picks up, making this bot think the
            // team is many kills ahead. 5min is conservative: a real teammate
            // restarting won't have shards >5min old (heartbeat is 3s).
            try {
                java.io.File[] oldShards = dir.listFiles((d, n) ->
                        n != null && n.startsWith("corp_team_state_") && n.endsWith(".json"));
                long now = System.currentTimeMillis();
                if (oldShards != null) {
                    for (java.io.File s : oldShards) {
                        if (now - s.lastModified() > 5 * 60_000L) {
                            String name = s.getName();
                            if (s.delete()) {
                                Log.info("Cleaned up stale coord shard from previous session: " + name);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            coordinator = new CorpCoordinator(new java.io.File(dir, "corp_team_state.json"),
                    INTERNAL_COORD_STALE_THRESHOLD_MS);
            // 1.9.76: also spin up port coordinator if enabled.
            // 1.9.99.182: auto-elect by default — first bot becomes host, rest become clients.
            if (settings.useCoordinatorPort && portCoordinator == null) {
                // 1.9.99.186: kill any leftover coord from a previous script
                // run in this same JVM. The daemon threads survive script
                // stops; without this, the old client thread keeps retrying
                // and eventually connects to the new host as a phantom
                // teammate, polluting team state.
                if (ACTIVE_PORT_COORD != null) {
                    Log.info("Shutting down leftover coordinator from previous run");
                    try { ACTIVE_PORT_COORD.shutdown(); } catch (Exception ignored) {}
                    ACTIVE_PORT_COORD = null;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                try {
                    if (settings.autoElectCoordinator) {
                        portCoordinator = CorpPortCoordinator.autoElect(
                                settings.coordinatorHostIp,
                                settings.coordinatorPortId,
                                INTERNAL_COORD_STALE_THRESHOLD_MS,
                                coordinator);
                    } else {
                        portCoordinator = new CorpPortCoordinator(
                                settings.coordinatorIsHost,
                                settings.coordinatorHostIp,
                                settings.coordinatorPortId,
                                INTERNAL_COORD_STALE_THRESHOLD_MS,
                                settings.coordinatorIsHost ? coordinator : null);
                    }
                    if (portCoordinator != null) {
                        ACTIVE_PORT_COORD = portCoordinator; // 1.9.99.186: track for next-run cleanup
                        Log.info("Port coordinator started: "
                                + (portCoordinator.isHost ? "HOST" : "CLIENT")
                                + " on " + settings.coordinatorHostIp
                                + ":" + (45000 + settings.coordinatorPortId));
                    }
                } catch (Exception e) {
                    Log.warn("Port coordinator init failed: " + e.getMessage()
                            + " — falling back to file-only");
                }
            }
        } catch (Exception e) {
            Log.error("Failed to init coordinator: " + e.getMessage());
        }
    }

    /** Publish current state to the team file. Call periodically from tick(). */
    private void coordinatorPublish() {
        // 1.9.99.183: gate on EITHER coordinator mode. Pre-1.9.99.183 file
        // publish only ran when coordinatorEnabled was on — if user only
        // ticked useCoordinatorPort, the file never received state, so when
        // the port refused there was no fallback. Now port mode implicitly
        // uses the file as backup.
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return;
        ensureCoordinator();
        if (coordinator == null) return;

        coordTickCounter++;
        if (coordTickCounter < INTERNAL_COORD_WRITE_INTERVAL_TICKS) return;
        coordTickCounter = 0;

        String name = MyPlayer.getUsername();
        if (name == null || name.isEmpty()) return;
        coordPublishedAccountName = name; // 1.9.99.201: cache for heartbeat thread

        // Fresh snapshot of what we look like right now.
        // 1.9.90: real login guard. getCurrentHealthPercent() >= 0 was always true and
        // published bogus snapshots pre-login. MyPlayer.get().isPresent() is the safe gate.
        mySnapshot.specPct = MyPlayer.get().isPresent() ? Combat.getSpecialAttackPercent() : 0;
        mySnapshot.botState = currentState == null ? "UNKNOWN" : currentState.name();
        mySnapshot.isPohHost = settings.isPohHost;
        mySnapshot.availableWeapons = new ArrayList<>(getOwnedSpecWeapons());
        mySnapshot.killId = localKillId; // 1.9.99.210: per-snapshot killId for display filter
        // 1.9.99.164: publish kill-phase flag so teammates arriving from
        // lobby/POH can short-circuit spec prep and equip main weapon
        // directly.
        try { mySnapshot.inKillPhase = isInKillPhase(); }
        catch (Throwable ignored) { mySnapshot.inKillPhase = false; }
        // specsThisKill and bgsDamageDealt are updated by Phase C wiring; we just publish.

        Set<String> live = new HashSet<>();
        if (settings.botTeammates != null) live.addAll(settings.botTeammates);
        live.add(name);  // always include ourselves

        // 1.9.99.211: deep-copy the snapshot before publish so the
        // heartbeat thread and port-host's stored reference can never see
        // the live mySnapshot being mutated by recordSpecUsed,
        // coordinatorOnKillEnded, etc.
        AccountSnapshot snapCopy = cloneSnapshot(mySnapshot);
        // 1.9.99.214: TCP-only when port is active — skip file writes.
        // The recurring "Coordinator publish failed: ...tmp -> ...json"
        // warnings were Windows file-lock collisions between writers and
        // concurrent readers from peer bots. Port coordinator handles
        // real-time sync; file was only a backup. User: "cant we
        // communicate purely over tcp and not write to disk?" Yes — when
        // port is on we skip disk entirely. File still used for the
        // solo / port-disabled case.
        if (portCoordinator != null) {
            try { portCoordinator.publish(name, snapCopy, localKillId, live); }
            catch (Exception e) { Log.warn("Port publish failed: " + e.getMessage()); }
        } else {
            coordinator.publish(name, snapCopy, localKillId, live);
        }
    }

    /** 1.9.99.211: deep-copy a snapshot via gson roundtrip so concurrent
     *  mutation of the source can't corrupt the published value. Falls back
     *  to returning the original on failure (better to publish slightly-
     *  raced data than to skip the publish entirely). */
    private AccountSnapshot cloneSnapshot(AccountSnapshot src) {
        if (src == null) return null;
        try {
            com.google.gson.Gson g = new com.google.gson.Gson();
            return g.fromJson(g.toJson(src), AccountSnapshot.class);
        } catch (Throwable t) {
            return src;
        }
    }

    /** 1.9.99.203: force an immediate publish bypassing the tick-counter gate.
     *  Used by encroachment relocate after picking a new bestPosition: we
     *  want the OTHER bot to see our new claim on its next check (~1s later),
     *  not on the next coordinator-write-interval boundary (up to 5 ticks ≈
     *  3s). Without this, both bots' first relocate fires symmetrically and
     *  picks the same tile because neither has a published claim yet. */
    private void coordinatorPublishNow() {
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return;
        coordTickCounter = INTERNAL_COORD_WRITE_INTERVAL_TICKS; // force next call to fire
        coordinatorPublish();
    }

    /** 1.9.99.201: lightweight publish for the heartbeat thread. Re-publishes
     *  the already-populated mySnapshot under the same accountName, killId,
     *  and live set. Does NOT call SDK methods (Combat/MyPlayer/etc.) — those
     *  must run on the main thread; we just refresh the timestamp via the
     *  publish() implementation which sets snap.lastUpdate. No-op until the
     *  main loop has run coordinatorPublish at least once (so mySnapshot
     *  has a username + populated fields). */
    private void coordinatorHeartbeat() {
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return;
        if (coordinator == null) return; // ensureCoordinator runs on main thread
        String name = coordPublishedAccountName;
        if (name == null || name.isEmpty()) return;
        Set<String> live = new HashSet<>();
        if (settings.botTeammates != null) live.addAll(settings.botTeammates);
        live.add(name);
        // 1.9.99.211: capture localKillId once at the top so a main-thread
        // kill_id increment between the port and file publish below doesn't
        // produce inconsistent (port=N, file=N+1) writes. Also deep-copy
        // mySnapshot so the heartbeat-thread serialization can't trip CME
        // when main thread mutates specsThisKill mid-publish.
        long killIdAtFire = localKillId;
        AccountSnapshot snapCopy = cloneSnapshot(mySnapshot);
        // 1.9.99.211: also stamp killId ON the cloned snapshot so the
        // aggregateForDisplay killId-filter (1.9.99.210) sees the freshest
        // value. The main loop's coordinatorPublish stamps mySnapshot.killId
        // = localKillId, but the heartbeat fires between main-loop ticks —
        // if main thread bumped localKillId since the last coordinatorPublish,
        // the clone still has the old killId field.
        if (snapCopy != null) snapCopy.killId = killIdAtFire;
        try {
            // 1.9.99.214: TCP-only when port is active.
            if (portCoordinator != null) {
                portCoordinator.publish(name, snapCopy, killIdAtFire, live);
            } else {
                coordinator.publish(name, snapCopy, killIdAtFire, live);
            }
        } catch (Throwable t) {
            // Quiet — the next heartbeat tick will retry.
        }
    }

    /** 1.9.99.201: spawn the heartbeat thread. Called once from execute()
     *  after settings are loaded. Idempotent — won't spawn a second thread
     *  if one is already running. */
    // 1.9.99.218: static reference to the heartbeat thread + a static
    // "alive" flag, mirroring ACTIVE_PORT_COORD. Without this, a heartbeat
    // thread from a PREVIOUS script run could survive into a new run with
    // OLD bytecode references — it would keep calling the OLD
    // CorpCoordinator.publish() (which had the .tmp + ATOMIC_MOVE dance),
    // producing the persistent "Coordinator publish failed: ...tmp -> ...json"
    // warnings even after a fresh Start. Now the new run shuts down the old
    // thread first via the static flag before starting its own.
    private static volatile Thread ACTIVE_HEARTBEAT_THREAD;
    private static final java.util.concurrent.atomic.AtomicBoolean ACTIVE_HEARTBEAT_ALIVE
            = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void startCoordinatorHeartbeat() {
        // 1.9.99.220: kill ALL leftover heartbeat threads from prior JVM
        // sessions, not just the one tracked in ACTIVE_HEARTBEAT_THREAD.
        // Multiple zombies accumulate when the user does several Stop/Start
        // cycles within an open TRiBot client — each restart spawns a new
        // thread but doesn't always join the predecessor (and 1.9.99.218
        // only tracked one). Each zombie publishes a stale snapshot,
        // causing the team-totals to flip between conflicting values.
        // Iterate the JVM thread set, find any thread named
        // "Corp-Coord-Heartbeat", interrupt and join.
        ACTIVE_HEARTBEAT_ALIVE.set(false); // signal all loops to exit
        int killed = 0;
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) root = root.getParent();
            Thread[] all = new Thread[Math.max(64, root.activeCount() * 2)];
            int n = root.enumerate(all, true);
            for (int i = 0; i < n; i++) {
                Thread t = all[i];
                if (t != null && t.isAlive() && "Corp-Coord-Heartbeat".equals(t.getName())) {
                    t.interrupt();
                    try { t.join(300); } catch (InterruptedException ignored) {}
                    killed++;
                }
            }
        } catch (Throwable ignored) {}
        if (killed > 0) {
            Log.info("1.9.99.220: shut down " + killed + " leftover heartbeat thread(s) from previous run(s)");
        }
        ACTIVE_HEARTBEAT_THREAD = null;
        ACTIVE_HEARTBEAT_ALIVE.set(true);
        Thread t = new Thread(() -> {
            // 1.9.99.218: read static flag, not instance `running`. The
            // instance can be GC'd while the thread is alive otherwise,
            // and the thread captures a stale `running` value.
            while (ACTIVE_HEARTBEAT_ALIVE.get() && running) {
                try { Thread.sleep(COORD_HEARTBEAT_INTERVAL_MS); }
                catch (InterruptedException ie) { return; }
                coordinatorHeartbeat();
            }
        }, "Corp-Coord-Heartbeat");
        t.setDaemon(true);
        coordHeartbeatThread = t;
        ACTIVE_HEARTBEAT_THREAD = t;
        t.start();
    }

    /** 1.9.99.201: signal the heartbeat thread to stop. The thread polls
     *  the static flag so it exits on the next interval; interrupt
     *  accelerates the exit. 1.9.99.218: also clears the static ref. */
    private void stopCoordinatorHeartbeat() {
        Thread t = coordHeartbeatThread;
        coordHeartbeatThread = null;
        ACTIVE_HEARTBEAT_ALIVE.set(false);
        if (t == ACTIVE_HEARTBEAT_THREAD) ACTIVE_HEARTBEAT_THREAD = null;
        if (t != null) t.interrupt();
    }

    /** 1.9.99.93: read the team's current kill_id from the coordinator.
     *  Returns -1 if coordinator disabled or no state. Used by the
     *  death-detection path to confirm Corp died via a teammate's
     *  observation when our local "Corp NPC missing" signal is
     *  ambiguous (e.g., Corp roamed off-scene). */
    private long coordinatorTeamKillId() {
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return -1; // 1.9.99.183
        ensureCoordinator();
        if (coordinator == null) return -1;
        try {
            if (portCoordinator != null) {
                TeamState ts = portCoordinator.read();
                if (ts != null) return ts.killId;
            }
            TeamState ts = coordinator.read();
            if (ts != null) return ts.killId;
        } catch (Exception ignored) {}
        return -1;
    }

    /** Read team aggregate. Returns null if disabled or unavailable. */
    private TeamAggregate coordinatorAggregate() {
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return null; // 1.9.99.183
        ensureCoordinator();
        if (coordinator == null) return null;
        // 1.9.76: prefer port coordinator's in-memory state when active —
        // it's real-time. File read is the fallback if port is off or
        // hasn't received any state yet.
        if (portCoordinator != null) {
            try {
                TeamState ts = portCoordinator.read();
                if (ts != null && ts.accounts != null && !ts.accounts.isEmpty()) {
                    return portCoordinator.aggregate(ts);
                }
            } catch (Exception ignored) {}
        }
        return coordinator.aggregate(coordinator.read());
    }

    /** 1.9.99.208: file-coord-only aggregate for the paint overlay. The
     *  port coordinator is asymmetric — the host sees its own mySnapshot
     *  by reference in latestState (always fresh) while the client sees
     *  whatever was last broadcast (up to ~3s old). That made each bot's
     *  overlay show different team totals. Both bots write to the SAME
     *  on-disk per-bot shards, so a file read gives identical results
     *  on both sides. Used only for display — teamPhaseNeeded() and other
     *  real-time logic still use coordinatorAggregate() (port preferred).
     *
     *  1.9.99.212: REMOVED the per-snapshot killId filter from 1.9.99.210.
     *  The filter was hiding peers whose killId didn't match the viewer's,
     *  which made both `live bots` and team totals drop to 0 whenever a
     *  peer was transitioning between kills (briefly killId+1 ahead).
     *  User: "shouldn't live bots and team totals always stay current
     *  instead of reverting to 0?" Yes — show what's actually published.
     *  Mid-kill flicker (the original 1.9.99.210 concern) is now mitigated
     *  by 1.9.99.211's clone-before-publish: a peer's local kill-end reset
     *  doesn't corrupt the snapshot already in flight. Between-kill brief
     *  drops are semantically correct (no kill in progress = 0 specs). */
    private TeamAggregate aggregateForDisplay() {
        if (!settings.coordinatorEnabled && !settings.useCoordinatorPort) return null;
        ensureCoordinator();
        if (coordinator == null) return null;
        try {
            // 1.9.99.213: ALWAYS include our own live mySnapshot first.
            // Pre-fix the aggregate read only from disk shards; if our own
            // shard's publish kept failing (Windows ATOMIC_MOVE collision
            // when another reader had the file open), the own bot's data
            // would be missing from the aggregate → team total displayed
            // as 0 even when actively specing. mySnapshot is in-memory and
            // always reflects what recordSpecUsed has accumulated.
            TeamAggregate a = new TeamAggregate();
            long now = System.currentTimeMillis();
            String selfName = null;
            try { selfName = coordPublishedAccountName; } catch (Throwable ignored) {}
            if (mySnapshot != null && selfName != null) {
                a.liveAccounts++;
                a.phase3BgsDamage += mySnapshot.bgsDamageDealt;
                if (mySnapshot.bgsDamageDealt > 0) a.anyTeamDamage = true;
                if (mySnapshot.specsThisKill != null) {
                    for (Map.Entry<String, Integer> e : mySnapshot.specsThisKill.entrySet()) {
                        Integer phase = SPEC_PHASE.get(e.getKey());
                        if (phase == null) continue;
                        if (e.getValue() > 0) a.anyTeamDamage = true;
                        if (phase == 1) a.phase1Specs += e.getValue();
                        else if (phase == 2) a.phase2Specs += e.getValue();
                    }
                }
            }
            // 1.9.99.214: read peers from port coordinator when active.
            // The host's latestState contains all clients; clients receive
            // it via broadcast. No disk I/O.
            TeamState ts = null;
            if (portCoordinator != null) {
                try { ts = portCoordinator.read(); } catch (Throwable ignored) {}
            }
            if (ts == null) {
                try { ts = coordinator.read(); } catch (Throwable ignored) {}
            }
            if (ts == null || ts.accounts == null) return a;
            for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
                if (e.getKey() != null && e.getKey().equals(selfName)) continue; // own already added
                AccountSnapshot snap = e.getValue();
                if (snap == null) continue;
                if (now - snap.lastUpdate > settings.coordinatorStaleThresholdMs) continue;
                a.liveAccounts++;
                a.phase3BgsDamage += snap.bgsDamageDealt;
                if (snap.bgsDamageDealt > 0) a.anyTeamDamage = true;
                if (snap.specsThisKill != null) {
                    for (Map.Entry<String, Integer> sp : snap.specsThisKill.entrySet()) {
                        Integer phase = SPEC_PHASE.get(sp.getKey());
                        if (phase == null) continue;
                        if (sp.getValue() > 0) a.anyTeamDamage = true;
                        if (phase == 1) a.phase1Specs += sp.getValue();
                        else if (phase == 2) a.phase2Specs += sp.getValue();
                    }
                }
            }
            return a;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Call when our bot confirms a Corp kill, so the local kill id advances and
     *  per-kill counters reset. Triggers `kill_id` bump on next publish. */
    private void coordinatorOnKillEnded() {
        localKillId++;
        killCount++; // overlay counter
        // 1.9.99.211: clear() instead of new — heartbeat thread may hold a
        // reference to specsThisKill via in-flight publish; replacing the
        // reference creates a window where heartbeat serializes the OLD map
        // while main thread acts on the NEW one. clear() keeps reference
        // stable so both threads always see the same (now-empty) map.
        if (mySnapshot.specsThisKill != null) mySnapshot.specsThisKill.clear();
        else mySnapshot.specsThisKill = new LinkedHashMap<>();
        mySnapshot.bgsDamageDealt = 0;
        mySnapshot.claimedCorpOffset = null;  // release positional claim for next kill
        committedSpecPhase = 0; // 1.9.37: clear per-kill ratchet
        maxRealCountThisKill = 0; // 1.9.38: clear per-kill realCount ratchet
        lockedRealCountThisKill = -1; // 1.9.99.152: clear locked real-teammate count
        firstNonZeroCorpHpAt = 0; // 1.9.99.167: clear bar-stability timer
        vengCastsThisKill = 0; // 1.9.99.75: per-kill veng counter for overlay
        corpMissingSinceMs = 0; // 1.9.99.91: reset missing-timer for next kill
        lastObservedCorpHpPercent = 100.0; // 1.9.99.92: reset HP tracker
        lastCorpAnimSeen = -2; // 1.9.99.107: reset death-diag trackers
        lastCorpHealthBarVisible = false;
        lastCorpInteracting = false; // 1.9.99.108
        coreEngagementCommitted = false; // 1.9.99.204
    }

    // 1.9.90: clear per-kill latches when a kill aborts (death/escape) without going
    // through coordinatorOnKillEnded. Otherwise stale ratchets bleed into the next kill.
    private void resetPerKillStateAfterAbort() {
        committedSpecPhase = 0;
        maxRealCountThisKill = 0;
        lockedRealCountThisKill = -1; // 1.9.99.152: clear locked real-teammate count
        firstNonZeroCorpHpAt = 0; // 1.9.99.167: clear bar-stability timer
        corpSeenAtZeroHp = false;
        maxCorpHpPercentThisKill = 0.0;
        minCorpHpPercentThisKill = 1.0; // 1.9.99.181: low-water ratchet reset
        specPreActivatedThisTrip = false;
        preActivateStageARolled = false;
        preActivateStageA5Rolled = false; // 1.9.99.179: was missing, caused A.5 to be permanently skipped after any abort
        preActivateStageBRolled = false;
        autoRetaliateDisabledForThisCore = false;
        coreEngagementCommitted = false; // 1.9.99.204
        vengCastsThisKill = 0; // 1.9.99.75: per-kill veng counter for overlay
        corpMissingSinceMs = 0; // 1.9.99.91: reset missing-timer
        lastObservedCorpHpPercent = 100.0; // 1.9.99.92: reset HP tracker
        lastCorpAnimSeen = -2; // 1.9.99.107: reset death-diag trackers
        lastCorpHealthBarVisible = false;
        lastCorpInteracting = false; // 1.9.99.108
        // 1.9.99.179: clear isInRestorationPhase on abort. Pre-1.9.99.179
        // a death mid-POH-cycle left isInRestorationPhase=true; on
        // recovery, shouldStartRestorationCycle() refused to POH for
        // the rest of the session (the !isInRestorationPhase gate
        // blocked it). resetTripTracking would have cleared it but
        // death-recovery's REEQUIP step goes straight to FIGHTING_CORP
        // without re-banking.
        isInRestorationPhase = false;
    }

    // ========== SESSION-END SIGNALING (1.7.1) ==========

    /** Local flag set when we either originated a session-end signal or
     *  observed one from a teammate. handleLooting() reads this and routes
     *  to EMERGENCY_ESCAPE after the current kill instead of starting a new one. */
    private boolean sessionEndPending = false;

    // ========== STATUS OVERLAY (1.7.2, in-client paint 1.9.99.27) ==========

    private int killCount = 0;
    private int deathCount = 0;
    private long scriptStartTime = 0;
    private boolean paintRegistered = false;

    /** 1.9.99.27: paint-state holder. Volatile because the main script thread
     *  writes here every tick while TRiBot's render thread reads from inside
     *  the Painting.addPaint callback. */
    private static final class PaintState {
        volatile String state = "?";
        volatile String weapon = "-";
        volatile String specCounts = "";
        volatile int kills = 0;
        volatile int deaths = 0;
        volatile long runtimeMs = 0;
        volatile boolean coordEnabled = false;
        volatile int phaseNeeded = -1;
        volatile boolean sessionEnd = false;
        // Tile debug — single multiline string, updated each tick by
        // getDynamicCorpPositions. Empty when no Corp visible.
        volatile String tileDebug = "";
        // 1.9.99.38: tile rendering — actual tile polygons drawn on the
        // game world (not just text). Updated by assignUniqueCorpPosition
        // each tick when Corp is visible. Immutable snapshots; the paint
        // thread reads these and never mutates.
        volatile WorldTile[] candidateTiles = null;     // 4 cardinals
        volatile boolean[] candidateCrosses = null;     // matches candidateTiles
        volatile WorldTile pickedTile = null;           // assignUniqueCorpPosition's choice
        volatile WorldTile corpCenterTile = null;       // Corp.getArea().getCenter()
        // 1.9.99.75: vengeance diagnostics. Updated each tick by overlayUpdate.
        volatile String vengState = "?";
        volatile int vengCastsKill = 0;
        volatile int vengCastsSession = 0;
        volatile long vengLastCastAgoMs = -1;     // -1 = never
        volatile long vengCooldownLeftMs = 0;
        volatile boolean vengTookDamage = false;
        volatile boolean vengRunesOk = false;
        volatile int vengMagicCurrent = 0;
        volatile int vengMagicBase = 0;
        volatile boolean vengInKillPhase = false;
        volatile String vengLastGateReason = "-";
        // 1.9.99.77: track attempts (widget clicks) separately from
        // confirmed casts (XP-delta detected). If attempts climb while
        // casts stay 0, the click is firing but the spell is refused.
        volatile int vengAttempts = 0;
        volatile long vengLastAttemptAgoMs = -1;
        // 1.9.99.98: corp-death detection diagnostics. Snapshot of every
        // gate input the LOOTING transition logic reads, so we can see
        // at-a-glance which check is blocking the transition when the
        // bot stays stuck at FIGHTING_CORP after a kill.
        volatile boolean ddInBossRoom = false;
        volatile double ddPeakHpThisKill = 0;
        volatile double ddLastObservedHp = 100;
        volatile long ddMissingMs = 0;
        volatile boolean ddCorpSeenAtZero = false;
        volatile long ddLocalKillId = 0;
        volatile long ddTeamKillId = -1;
        volatile boolean ddFastTimeoutReady = false;
        volatile boolean ddSustainedReady = false;
        volatile boolean ddTeammateConfirmed = false;

        // 1.9.99.140: encroachment-relocate diagnostics. Live state of the
        // maybeRelocateForEncroachment helper so the user can see what's
        // happening without relying on the debug log (which is unusable).
        volatile String encGateReason = "—"; // why the most recent check returned (state-gate, no-corp, dist>=3, relocated, etc.)
        volatile int encOtherPlayers = 0;
        volatile double encClosestDist = -1;
        volatile long encLastTriggerAgoMs = -1; // ms since last successful relocate
        // 1.9.99.197: team-aggregate spec totals from the coordinator. Shows
        // the sum across all live bot teammates so user can verify coord is
        // actually reading shared data correctly.
        volatile String teamSpecTotals = "—";
        volatile int teamLiveBots = 0;
    }
    private final PaintState paintState = new PaintState();

    private void overlayInit() {
        if (paintRegistered || !settings.showOverlay) return;
        try {
            Painting.addPaint(this::drawInClientOverlay);
            paintRegistered = true;
        } catch (Throwable t) {
            Log.warn("Failed to register in-client paint: " + t.getMessage());
        }
    }

    /** 1.9.99.27: render status info + Corp tile debug directly on the game
     *  canvas (replaces the pre-1.9.99.27 Swing JFrame popout). Runs on
     *  TRiBot's render thread — reads volatile fields from paintState. */
    private void drawInClientOverlay(Graphics2D g) {
        if (!settings.showOverlay) return;
        try {
            // 1.9.99.140: bumped font from Monospaced/12 to SansSerif/16,
            // increased row spacing + box dims accordingly. User: "consider
            // our debug basically nonexistent" + "up the size of our paint
            // and use a better font. Our currnent paint is so hard and
            // tiny to lead." Enable antialiasing for the bigger font so
            // edges aren't pixelated.
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.PLAIN, 16));
            int x = 12;
            int y = 38;
            final int rowH = 20;       // normal row spacing (was 15)
            final int blockGap = 26;   // block separator gap (was 18)
            final int subRowH = 19;    // tight intra-block rows (was 14)
            // Background panel for readability.
            int boxH = 950;
            int boxW = 540;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRect(x - 4, y - 14, boxW, boxH);
            g.setColor(Color.WHITE);
            g.drawString("Corp script — state: " + paintState.state, x, y); y += rowH;
            g.drawString("Spec weapon: " + paintState.weapon, x, y); y += rowH;
            g.drawString("Specs this kill: " + (paintState.specCounts.isEmpty() ? "-" : paintState.specCounts), x, y); y += rowH;
            // 1.9.99.197: team totals from coord aggregate. Verifies coord is
            // reading shared data correctly. If P1/P2/BGS counters are zero
            // while local specs are firing, the coord layer is broken.
            g.setColor(new Color(180, 220, 255));
            g.drawString("Team totals: " + paintState.teamSpecTotals
                    + "   (live bots: " + paintState.teamLiveBots + ")", x, y); y += rowH;
            g.setColor(Color.WHITE);
            g.drawString("Kills: " + paintState.kills + "   Deaths: " + paintState.deaths, x, y); y += rowH;
            long sec = paintState.runtimeMs / 1000;
            g.drawString(String.format("Runtime: %d:%02d:%02d", sec/3600, (sec%3600)/60, sec%60), x, y); y += rowH;
            g.drawString("Coordinator: " + (paintState.coordEnabled ? "on" : "off"), x, y); y += rowH;
            g.drawString("Phase needed: " + (paintState.phaseNeeded == 0 ? "done"
                                            : paintState.phaseNeeded == -1 ? "-"
                                            : String.valueOf(paintState.phaseNeeded)), x, y); y += rowH;
            g.drawString("Session end: " + (paintState.sessionEnd ? "PENDING" : "no"), x, y); y += blockGap;

            // 1.9.99.75: vengeance diagnostic block. User: "can we add all
            // of our vengeance info to the paint? maybe that will help us
            // debug better."
            g.setColor(new Color(255, 200, 120));
            g.drawString("--- Vengeance ---", x, y); y += subRowH;
            g.setColor(Color.WHITE);
            g.drawString("state: " + paintState.vengState
                    + "   kill: " + paintState.vengCastsKill
                    + "   sess: " + paintState.vengCastsSession, x, y); y += subRowH;
            String lastAttempt = paintState.vengLastAttemptAgoMs < 0
                    ? "never"
                    : String.format("%.1fs ago", paintState.vengLastAttemptAgoMs / 1000.0);
            g.drawString("attempts: " + paintState.vengAttempts
                    + "   lastAttempt: " + lastAttempt, x, y); y += subRowH;
            String lastCast = paintState.vengLastCastAgoMs < 0
                    ? "never"
                    : String.format("%.1fs ago", paintState.vengLastCastAgoMs / 1000.0);
            String cooldown = paintState.vengCooldownLeftMs <= 0
                    ? "ready"
                    : String.format("%.1fs left", paintState.vengCooldownLeftMs / 1000.0);
            g.drawString("lastCast: " + lastCast + "   cd: " + cooldown, x, y); y += subRowH;
            g.drawString("magic: " + paintState.vengMagicCurrent
                    + "/" + paintState.vengMagicBase
                    + (paintState.vengMagicCurrent < 94 ? " DRAINED" : "")
                    + "   runes: " + (paintState.vengRunesOk ? "ok" : "MISSING"), x, y); y += subRowH;
            g.drawString("tookDmg: " + (paintState.vengTookDamage ? "yes" : "no")
                    + "   killPhase: " + (paintState.vengInKillPhase ? "yes" : "no"), x, y); y += subRowH;
            g.drawString("lastBlock: " + paintState.vengLastGateReason, x, y); y += blockGap;

            // 1.9.99.98: corp-death detection block. Reveals which gate
            // is blocking the LOOTING transition when state is stuck at
            // FIGHTING_CORP after a kill.
            g.setColor(new Color(255, 200, 120));
            g.drawString("--- Corp death detect ---", x, y); y += subRowH;
            g.setColor(Color.WHITE);
            g.drawString("inBossRoom: " + (paintState.ddInBossRoom ? "yes" : "no")
                    + "   seenAt0: " + (paintState.ddCorpSeenAtZero ? "yes" : "no"), x, y); y += subRowH;
            // 1.9.99.112: multiply by 100 — SDK returns 0-1 proportion.
            g.drawString(String.format("peakHP: %.1f%%   lastHP: %.1f%%",
                    paintState.ddPeakHpThisKill * 100.0,
                    paintState.ddLastObservedHp * 100.0), x, y); y += subRowH;
            g.drawString("missing: " + (paintState.ddMissingMs == 0
                            ? "no" : (paintState.ddMissingMs + "ms")), x, y); y += subRowH;
            g.drawString("fastTimeout: " + (paintState.ddFastTimeoutReady ? "READY" : "no")
                    + "   sustained: " + (paintState.ddSustainedReady ? "READY" : "no"), x, y); y += subRowH;
            g.drawString("local kid: " + paintState.ddLocalKillId
                    + "   team kid: " + (paintState.ddTeamKillId < 0
                            ? "off" : String.valueOf(paintState.ddTeamKillId))
                    + (paintState.ddTeammateConfirmed ? "  TEAM-CONFIRM" : ""),
                    x, y); y += blockGap;

            // 1.9.99.140: positioning / encroachment block. User asked
            // for all encroachment debug on the paint because the debug
            // log is unusable.
            g.setColor(new Color(255, 200, 120));
            g.drawString("--- Positioning ---", x, y); y += subRowH;
            g.setColor(Color.WHITE);
            // 1.9.99.141: now reads as integer tile-counts (Chebyshev)
            // instead of fractional Euclidean — matches OSRS player
            // mental model.
            String closestStr = paintState.encClosestDist < 0
                    ? "—"
                    : (paintState.encClosestDist >= Double.MAX_VALUE / 2
                            ? "—"
                            : String.valueOf((int) paintState.encClosestDist));
            int threshTiles = settings != null ? settings.encroachmentRelocateTiles : 3;
            g.drawString("others: " + paintState.encOtherPlayers
                    + "   closest: " + closestStr + " tiles"
                    + "   threshold: " + threshTiles, x, y); y += subRowH;
            g.drawString("lastResult: " + paintState.encGateReason, x, y); y += blockGap;

            // 1.9.99.108: death-detection trace — animation IDs + bar
            // visibility transitions in chronological order. Frozen
            // post-despawn for the user to read; live during a kill.
            // Log spam is unreliable (finite + drowned by other lines),
            // overlay is canonical.
            java.util.List<String> frozenLines = snapshotDeathDiagFrozen();
            java.util.List<String> recentLines = snapshotDeathDiagRecent();
            boolean hasFrozen = !frozenLines.isEmpty();
            g.setColor(new Color(255, 200, 120));
            g.drawString(hasFrozen
                    ? "--- Death trace (last kill — frozen) ---"
                    : "--- Death trace (live) ---", x, y); y += subRowH;
            g.setColor(Color.WHITE);
            java.util.List<String> showLines = hasFrozen ? frozenLines : recentLines;
            if (showLines.isEmpty()) {
                g.drawString("(no events yet — waiting for Corp NPC)", x, y); y += subRowH;
            } else {
                for (String line : showLines) {
                    g.drawString(line, x, y); y += subRowH;
                }
            }
            y += 4;

            if (!paintState.tileDebug.isEmpty()) {
                g.setColor(new Color(180, 220, 255));
                for (String line : paintState.tileDebug.split("\n")) {
                    g.drawString(line, x, y); y += subRowH;
                }
            }
            // 1.9.99.38: draw the actual tile polygons on the game world.
            // GREEN = picked position, YELLOW = valid alternative,
            // RED = rejected (would cross Corp's hitbox), ORANGE = Corp's
            // 5x5 hitbox outline.
            drawCorpTilePolygons(g);
        } catch (Throwable ignored) { /* never let paint throw to TRiBot */ }
    }

    /** 1.9.99.38: project candidate tiles via legacy Projection.getTileBoundsPoly
     *  and fill/outline them on the game canvas. Reads only volatile snapshots
     *  from paintState — never touches the live Query API from the render
     *  thread (would race with the main script thread). */
    private void drawCorpTilePolygons(Graphics2D g) {
        WorldTile[] cands = paintState.candidateTiles;
        boolean[] crosses = paintState.candidateCrosses;
        WorldTile picked = paintState.pickedTile;
        WorldTile corpCenter = paintState.corpCenterTile;
        if (cands == null || crosses == null || cands.length != crosses.length) return;

        java.awt.Stroke savedStroke = g.getStroke();
        g.setStroke(new java.awt.BasicStroke(2f));

        // Draw Corp's 5x5 hitbox outline.
        if (corpCenter != null) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    WorldTile t = new WorldTile(
                            corpCenter.getX() + dx,
                            corpCenter.getY() + dy,
                            corpCenter.getPlane());
                    drawTilePoly(g, t, new Color(255, 140, 0, 100), new Color(255, 140, 0, 200));
                }
            }
        }

        // Draw each candidate tile.
        for (int i = 0; i < cands.length; i++) {
            WorldTile t = cands[i];
            if (t == null) continue;
            boolean isPicked = picked != null
                    && t.getX() == picked.getX()
                    && t.getY() == picked.getY()
                    && t.getPlane() == picked.getPlane();
            Color fill, outline;
            if (isPicked) {
                fill = new Color(0, 255, 0, 110);
                outline = new Color(0, 255, 0, 230);
            } else if (crosses[i]) {
                fill = new Color(220, 30, 30, 90);
                outline = new Color(255, 60, 60, 220);
            } else {
                fill = new Color(230, 220, 0, 90);
                outline = new Color(255, 240, 0, 220);
            }
            drawTilePoly(g, t, fill, outline);
        }

        g.setStroke(savedStroke);
    }

    /** 1.9.99.38: project one WorldTile to a screen polygon and paint it.
     *  Silent no-op if the tile isn't currently in render distance / on-screen.
     *  Uses WorldTile.getBounds() (SDK Optional<Polygon>) — the legacy
     *  Projection class isn't on the script's compile classpath. */
    private void drawTilePoly(Graphics2D g, WorldTile tile, Color fill, Color outline) {
        if (tile == null) return;
        try {
            Optional<java.awt.Polygon> bounds = tile.getBounds();
            if (!bounds.isPresent()) return;
            java.awt.Polygon poly = bounds.get();
            if (poly.npoints == 0) return;
            g.setColor(fill);
            g.fillPolygon(poly);
            g.setColor(outline);
            g.drawPolygon(poly);
        } catch (Throwable ignored) {}
    }

    private void overlayUpdate() {
        if (!settings.showOverlay) return;
        if (!paintRegistered) overlayInit();
        int phaseNeeded = -1;
        try {
            // 1.9.39: always show phase, not just when coordinator on —
            // teamPhaseNeeded works solo via buildSoloAggregate.
            phaseNeeded = teamPhaseNeeded();
        } catch (Exception ignored) {}
        paintState.state = currentState == null ? "?" : currentState.name();
        paintState.weapon = chosenSpecWeapon == null ? "-" : chosenSpecWeapon;
        paintState.specCounts = formatSpecCountsThisKill();
        paintState.teamSpecTotals = formatTeamSpecTotals(); // 1.9.99.197
        try {
            // 1.9.99.208: same symmetric source as totals.
            TeamAggregate aggForLive = (settings != null
                    && (settings.coordinatorEnabled || settings.useCoordinatorPort))
                    ? aggregateForDisplay() : null;
            paintState.teamLiveBots = aggForLive == null ? 0 : aggForLive.liveAccounts;
        } catch (Throwable t) { paintState.teamLiveBots = 0; }
        paintState.kills = killCount;
        paintState.deaths = deathCount;
        paintState.runtimeMs = System.currentTimeMillis() - scriptStartTime;
        paintState.coordEnabled = settings.coordinatorEnabled;
        paintState.phaseNeeded = phaseNeeded;
        paintState.sessionEnd = sessionEndPending;
        // 1.9.99.41: refresh Corp's live position every tick so the overlay's
        // 5x5 hitbox outline tracks Corp's real tile, not the snapshot from
        // the last positioning recompute. User showed a screenshot where the
        // red overlay tiles (script's belief) were several tiles off from
        // the actual Corp (green) — the cardinals had stale data. Position
        // recompute is gated on "Drifted from Corp position" so it doesn't
        // run every tick; this lightweight refresh keeps the overlay live
        // even between recomputes.
        try {
            Optional<Npc> corpForOverlay = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpForOverlay.isPresent()) {
                Area liveArea = corpForOverlay.get().getArea();
                if (liveArea != null) {
                    WorldTile liveCenter = liveArea.getCenter();
                    paintState.corpCenterTile = liveCenter;
                    // 1.9.99.74: recompute the 4 cardinal candidates +
                    // cross classifications LIVE every paint tick. The
                    // candidates are just corpCenter +/- (3,0) or (0,3)
                    // — trivial — and lineCrossesCorp depends on Corp's
                    // current area + the player's current tile. Before
                    // 1.9.99.74 these were only refreshed when the
                    // positioning code recomputed (gated on drift, ~500ms+),
                    // so the cross overlay lagged Corp's actual movement.
                    // User: "our cross detections still dont update in
                    // real time it hasnt caused any issues yet but it's
                    // something worth noticing."
                    WorldTile myPosLive = MyPlayer.getTile();
                    int n = CORP_POSITION_OFFSETS.size();
                    WorldTile[] candsLive = new WorldTile[n];
                    boolean[] crossLive = new boolean[n];
                    for (int i = 0; i < n; i++) {
                        int[] off = CORP_POSITION_OFFSETS.get(i);
                        candsLive[i] = new WorldTile(
                                liveCenter.getX() + off[0],
                                liveCenter.getY() + off[1],
                                liveCenter.getPlane());
                        crossLive[i] = myPosLive != null
                                && lineCrossesCorp(myPosLive, candsLive[i], liveArea);
                    }
                    paintState.candidateTiles = candsLive;
                    paintState.candidateCrosses = crossLive;
                }
            } else {
                paintState.corpCenterTile = null;
                paintState.candidateTiles = null;
                paintState.candidateCrosses = null;
                paintState.pickedTile = null;
            }
        } catch (Throwable ignored) {}

        // 1.9.99.75: snapshot vengeance diagnostics for the overlay.
        try {
            paintState.vengState = vengeanceState == null ? "?" : vengeanceState.name();
            paintState.vengCastsKill = vengCastsThisKill;
            paintState.vengCastsSession = vengCastsThisSession;
            paintState.vengLastCastAgoMs = lastVengeanceCast == 0
                    ? -1 : System.currentTimeMillis() - lastVengeanceCast;
            paintState.vengCooldownLeftMs = lastVengeanceCast == 0
                    ? 0
                    : Math.max(0, 30000 - (System.currentTimeMillis() - lastVengeanceCast));
            paintState.vengTookDamage = tookDamageSinceLastVeng;
            paintState.vengMagicCurrent = Skill.MAGIC.getCurrentLevel();
            paintState.vengMagicBase = Skill.MAGIC.getActualLevel();
            paintState.vengRunesOk = hasVengeanceRunes();
            paintState.vengInKillPhase = isInKillPhase();
            paintState.vengLastGateReason = vengLastGateReason == null
                    ? "-" : vengLastGateReason;
            paintState.vengAttempts = vengAttemptCount;
            paintState.vengLastAttemptAgoMs = lastVengAttemptAt == 0
                    ? -1 : System.currentTimeMillis() - lastVengAttemptAt;
        } catch (Throwable ignored) {}

        // 1.9.99.98: corp-death detection diagnostics snapshot. Mirrors
        // the gate logic in handleFightingCorp's missing-Corp else branch
        // so the overlay shows exactly which gate is keeping the bot
        // stuck at FIGHTING_CORP.
        try {
            boolean inBossRoom = false;
            try { inBossRoom = isInCorpBossRoom(); } catch (Exception ignored) {}
            long missingMs = corpMissingSinceMs > 0
                    ? System.currentTimeMillis() - corpMissingSinceMs : 0;
            long teamKid = coordinatorTeamKillId();
            boolean engaged = maxCorpHpPercentThisKill > 0.05; // 1.9.99.112: 0-1 scale
            boolean wasDying = lastObservedCorpHpPercent > 0.0
                    && lastObservedCorpHpPercent < 0.30; // 1.9.99.112: 0-1 scale
            paintState.ddInBossRoom = inBossRoom;
            paintState.ddPeakHpThisKill = maxCorpHpPercentThisKill;
            paintState.ddLastObservedHp = lastObservedCorpHpPercent;
            paintState.ddMissingMs = missingMs;
            paintState.ddCorpSeenAtZero = corpSeenAtZeroHp;
            paintState.ddLocalKillId = localKillId;
            paintState.ddTeamKillId = teamKid;
            paintState.ddFastTimeoutReady = engaged && wasDying && missingMs > 3000;
            paintState.ddSustainedReady = engaged && missingMs > 15000;
            paintState.ddTeammateConfirmed = teamKid > localKillId;
        } catch (Throwable ignored) {}
    }

    /** 1.9.39: compact "weapon=count, weapon=count" string for the overlay.
     *  Reads mySnapshot.specsThisKill (per-kill, cleared in coordinatorOnKillEnded).
     *  User flagged: bot kept Arclighting for 10+ minutes — counter on the
     *  overlay will make it obvious whether specs are being RECORDED or
     *  whether the phase target itself is unreachable. */
    /** 1.9.99.197: format team-aggregate spec totals from the coordinator
     *  for the paint overlay. Reads the same TeamState the rest of the
     *  script uses, so if this row shows expected sums the coord is
     *  healthy. If it shows "—" or zeros while you've personally fired
     *  specs, the coord read/aggregate path is broken. */
    // 1.9.99.215: TEAM-DIAG — track previous totals so we can log every
    // change and explain which source moved.
    private int diagPrevP1 = -1, diagPrevP2 = -1, diagPrevBgs = -1, diagPrevLive = -1;
    private String formatTeamSpecTotals() {
        try {
            // 1.9.99.208: use file coord for symmetric display (host & client agree).
            TeamAggregate agg = (settings != null
                    && (settings.coordinatorEnabled || settings.useCoordinatorPort))
                    ? aggregateForDisplay() : buildSoloAggregate();
            if (agg == null) return "—";
            // 1.9.99.221: ALL phase targets are now divided by (1 + realCount)
            // so the display matches the logic. teamPhaseNeeded multiplies
            // the bot-team aggregate by (1 + realCount) before comparing to
            // the raw target — equivalent to comparing raw aggregate to
            // raw_target / (1 + realCount). Previously only BGS showed this
            // effective value; P1/P2 showed the raw 4/20 which confused the
            // user when both bots advanced to the next phase at 3/4 (with
            // 1 real teammate, 3 × 2 = 6 ≥ 4 → phase done).
            // 1.9.99.222: use the rolling-window count so the display
            // matches the logic in teamPhaseNeeded (same source).
            int multForDisplay = Math.max(1, 1 + effectiveRealTeammateCount());
            int effTarget1 = (INTERNAL_PHASE1_TARGET + multForDisplay - 1) / multForDisplay;
            int effTarget2 = (INTERNAL_PHASE2_TARGET + multForDisplay - 1) / multForDisplay;
            int effTarget3 = INTERNAL_PHASE3_BGS_DAMAGE / multForDisplay;
            // 1.9.99.215: log on any change. Include per-bot breakdown so
            // user can see WHICH bot's data moved (or vanished).
            if (diagPrevP1 != agg.phase1Specs
                    || diagPrevP2 != agg.phase2Specs
                    || diagPrevBgs != agg.phase3BgsDamage
                    || diagPrevLive != agg.liveAccounts) {
                StringBuilder perBot = new StringBuilder();
                perBot.append("self=");
                if (mySnapshot != null) {
                    int sp1 = 0, sp2 = 0;
                    if (mySnapshot.specsThisKill != null) {
                        for (Map.Entry<String, Integer> e : mySnapshot.specsThisKill.entrySet()) {
                            Integer phase = SPEC_PHASE.get(e.getKey());
                            if (phase == null) continue;
                            if (phase == 1) sp1 += e.getValue();
                            else if (phase == 2) sp2 += e.getValue();
                        }
                    }
                    perBot.append("P1=").append(sp1).append("/P2=").append(sp2)
                            .append("/BGS=").append(mySnapshot.bgsDamageDealt);
                }
                try {
                    TeamState ts = null;
                    if (portCoordinator != null) {
                        try { ts = portCoordinator.read(); } catch (Throwable ignored) {}
                    }
                    if (ts == null && coordinator != null) {
                        try { ts = coordinator.read(); } catch (Throwable ignored) {}
                    }
                    if (ts != null && ts.accounts != null) {
                        String selfName = coordPublishedAccountName;
                        long now = System.currentTimeMillis();
                        for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
                            if (e.getKey() == null || e.getKey().equals(selfName)) continue;
                            AccountSnapshot snap = e.getValue();
                            if (snap == null) continue;
                            long ageMs = now - snap.lastUpdate;
                            int sp1 = 0, sp2 = 0;
                            if (snap.specsThisKill != null) {
                                for (Map.Entry<String, Integer> sp : snap.specsThisKill.entrySet()) {
                                    Integer phase = SPEC_PHASE.get(sp.getKey());
                                    if (phase == null) continue;
                                    if (phase == 1) sp1 += sp.getValue();
                                    else if (phase == 2) sp2 += sp.getValue();
                                }
                            }
                            perBot.append(" | ").append(e.getKey()).append("=")
                                    .append("P1=").append(sp1).append("/P2=").append(sp2)
                                    .append("/BGS=").append(snap.bgsDamageDealt)
                                    .append(" age=").append(ageMs).append("ms")
                                    .append(" kid=").append(snap.killId);
                        }
                    }
                } catch (Throwable ignored) {}
                Log.info("[TEAM-DIAG] live=" + agg.liveAccounts
                        + " P1=" + diagPrevP1 + "→" + agg.phase1Specs
                        + " P2=" + diagPrevP2 + "→" + agg.phase2Specs
                        + " BGS=" + diagPrevBgs + "→" + agg.phase3BgsDamage
                        + "  [" + perBot + "]");
                diagPrevP1 = agg.phase1Specs;
                diagPrevP2 = agg.phase2Specs;
                diagPrevBgs = agg.phase3BgsDamage;
                diagPrevLive = agg.liveAccounts;
            }
            return String.format("P1=%d/%d  P2=%d/%d  BGS=%d/%d",
                    agg.phase1Specs, effTarget1,
                    agg.phase2Specs, effTarget2,
                    agg.phase3BgsDamage, effTarget3);
        } catch (Throwable t) {
            return "ERR";
        }
    }

    private String formatSpecCountsThisKill() {
        try {
            if (mySnapshot == null || mySnapshot.specsThisKill == null
                    || mySnapshot.specsThisKill.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, Integer> e : mySnapshot.specsThisKill.entrySet()) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(e.getKey()).append("=").append(e.getValue());
                // 1.9.99.59: BGS phase is gated on DAMAGE drained, not
                // spec count — append the damage total so the user can
                // see Phase 3 progress directly. User: "for the bandos
                // godsword phase we are tracking damage dealt vs specs
                // hit. is that properly set up right? the paint still
                // is counting specs hit so i jsut want to make sure
                // thats all wired correctly". Logic was correct
                // (bgsDamageDealt accumulates hitsplat values); the
                // paint just wasn't surfacing it.
                if ("Bandos godsword".equalsIgnoreCase(e.getKey())) {
                    // 1.9.99.61: show EFFECTIVE target (raw 200 / (1 +
                    // realCount)). In duo the multiplier means the bot
                    // only needs to do 100 damage personally — so the
                    // raw 200 was misleading. User: "but... dont we
                    // not need 200 since we are solo duoing? or how
                    // does this work".
                    // 1.9.99.222: use rolling-window count.
                    int realCountForBgs = effectiveRealTeammateCount();
                    int effectiveTarget = INTERNAL_PHASE3_BGS_DAMAGE
                            / Math.max(1, 1 + realCountForBgs);
                    sb.append(" (~").append(mySnapshot.bgsDamageDealt)
                            .append("/").append(effectiveTarget).append(" dmg");
                    if (realCountForBgs > 0) {
                        sb.append(", duo ").append(realCountForBgs)
                                .append("p multiplier");
                    }
                    sb.append(")");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void overlayClose() {
        // 1.9.99.27: in-client paint stays registered until script terminates;
        // nothing to dispose here. Left as a no-op for call-site compatibility.
    }

    /** True if the bank holds at least one Games necklace of any dose count.
     *  Bank.isOpen() must be true before this is called. */
    private boolean bankHasGamesNecklace() {
        String[] doses = { "Games necklace(8)", "Games necklace(6)",
                "Games necklace(4)", "Games necklace(2)", "Games necklace(1)" };
        for (String d : doses) {
            try { if (Bank.getCount(d) > 0) return true; } catch (Exception ignored) {}
        }
        return false;
    }

    /** Mark our snapshot for session end and force-publish so teammates see
     *  the signal on their next coordinator read. Sets local sessionEndPending
     *  so we ourselves also start the graceful shutdown path. */
    private void signalSessionEnd(String reason) {
        Log.error("SESSION END requested: " + reason);
        sessionEndPending = true;
        if (mySnapshot != null) {
            mySnapshot.sessionEndRequested = true;
            mySnapshot.sessionEndReason = reason;
        }
        // Force a publish immediately so teammates don't miss the signal.
        try { coordinatorPublish(); } catch (Exception ignored) {}
    }

    /** Reads the coordinator's TeamState and returns the first live teammate
     *  whose snapshot has sessionEndRequested=true, or null if none. */
    private AccountSnapshot findTeammateRequestingSessionEnd() {
        if (!settings.coordinatorEnabled || coordinator == null) return null;
        try {
            TeamState ts = coordinator.read();
            if (ts == null || ts.accounts == null) return null;
            long now = System.currentTimeMillis();
            String myName = MyPlayer.getUsername();
            for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
                if (e.getKey().equals(myName)) continue;
                AccountSnapshot snap = e.getValue();
                if (snap == null || !snap.sessionEndRequested) continue;
                if (now - snap.lastUpdate > INTERNAL_COORD_STALE_THRESHOLD_MS) continue;
                if (settings.botTeammates != null && !settings.botTeammates.isEmpty()
                        && !settings.botTeammates.contains(e.getKey())) continue;
                return snap;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** Phase E: pick an offset from CORP_POSITION_OFFSETS that no other live bot has
     *  claimed AND is currently safe + walkable relative to Corp's position.
     *
     *  Handles two failure cases:
     *   1. Corp moves into a wall/door and our existing claim becomes unreachable.
     *      We detect via isTileWalkable, release the claim, and pick another.
     *   2. All four cardinals are claimed by others (5+ bots). We don't claim
     *      anything; caller falls through to the existing assignUniqueCorpPosition.
     *
     *  Crucially: this method is only consulted by moveToNearestCorpPosition,
     *  which itself only runs when isInGoodCorpPosition returns false. During
     *  active combat where the bot is attacking Corp naturally (even if drifted
     *  from the anchor), we don't force re-anchoring — the bot just follows Corp. */
    private WorldTile pickCoordinatedCorpPosition(Npc corp) {
        if (!settings.coordinatorEnabled || coordinator == null || corp == null) return null;

        // Read offsets currently claimed by OTHER live bots.
        Set<String> claimedByOthers = readOthersClaimedOffsets();

        // If we have an existing claim and it's still viable, keep it.
        if (mySnapshot.claimedCorpOffset != null && mySnapshot.claimedCorpOffset.length >= 2) {
            WorldTile target = currentClaimedPosition(corp);
            if (target != null && isPositionSafeFromCorpHitbox(target, corp) && isTileWalkable(target)) {
                return target;
            }
            Log.info("Coordinator: releasing unreachable claim "
                    + Arrays.toString(mySnapshot.claimedCorpOffset));
            mySnapshot.claimedCorpOffset = null;
        }

        // Try unclaimed offsets; commit only to a safe AND walkable one.
        WorldTile ct = corp.getTile();
        for (int[] off : CORP_POSITION_OFFSETS) {
            String key = off[0] + "," + off[1];
            if (claimedByOthers.contains(key)) continue;
            WorldTile target = new WorldTile(ct.getX() + off[0], ct.getY() + off[1], ct.getPlane());
            if (!isPositionSafeFromCorpHitbox(target, corp)) continue;
            if (!isTileWalkable(target)) continue;
            mySnapshot.claimedCorpOffset = new int[]{ off[0], off[1] };
            return target;
        }
        // All cardinals taken or unreachable; let the caller find anywhere safe.
        return null;
    }

    // ========== ANTI-STOMP SAFETY (Phase F) ==========
    // Corp's stomp deals 30-51 unblockable damage every 7 ticks (4.2s) to anyone
    // standing on its 5x5 hitbox. This is a real "your bot dies" risk that all
    // the positioning logic in the world doesn't prevent if we end up under Corp
    // mid-path (e.g., Corp moved on top of our tile during combat).

    /** True if my current tile is inside Corp's 5x5 hitbox. */
    // 1.9.99.114: stability counter for isUnderCorp. The SDK derives
    // corp.getArea() from Corp's INTERPOLATED position (localX/Y / 128),
    // not the server tile — so mid-walk the area momentarily overlaps
    // our tile even when Corp is just walking PAST us, not onto us. A
    // raw single-frame check fires panic-step on every transient overlap.
    // Require the condition to hold for >=2 consecutive checks (~50-150ms
    // depending on caller frequency) before treating it as a real stomp
    // threat. Corp's stomp tick is 7 game ticks (~4.2s) so a 100ms confirm
    // window is well within the reaction budget. User: "every single
    // recent attempt ive sene has been a false positive."
    private int corpUnderConsecutiveChecks = 0;
    private static final int UNDER_CORP_CONFIRM_CHECKS = 2;

    private boolean isUnderCorp(Npc corp) {
        if (corp == null) { corpUnderConsecutiveChecks = 0; return false; }
        try {
            WorldTile myPos = MyPlayer.getTile();
            if (myPos == null) { corpUnderConsecutiveChecks = 0; return false; }
            Area corpArea = corp.getArea();
            boolean overlapNow = corpArea != null && corpArea.contains(myPos);
            if (!overlapNow) {
                corpUnderConsecutiveChecks = 0;
                return false;
            }
            corpUnderConsecutiveChecks++;
            // 1.9.99.202: state-aware confirm threshold. The 2-frame gate
            // exists to filter Corp's interpolated-position transients
            // when he walks PAST us during FIGHTING_CORP. During
            // HANDLING_DARK_CORE the overlap is almost always real — we
            // just walked into Corp's hitbox while approaching the core.
            // The 2-frame gate plus handleDarkCore's "already attacking
            // core" early return delays the step-off by 1-2 ticks, long
            // enough for the stomp to land. Drop to 1-frame confirm in
            // core-handling so antiStompTick fires immediately on the
            // first overlap frame.
            int threshold = (currentState == BotState.HANDLING_DARK_CORE)
                    ? 1 : UNDER_CORP_CONFIRM_CHECKS;
            return corpUnderConsecutiveChecks >= threshold;
        } catch (Exception e) { corpUnderConsecutiveChecks = 0; return false; }
    }

    /** Immediately move to the nearest tile outside Corp's hitbox. Tries multiple
     *  click methods in order of reliability because LocalWalking.walkTo can
     *  misbehave when the bot itself is standing on a "blocked" tile (Corp's
     *  hitbox). Returns true if any method initiated a step.
     *
     *  Method order: LocalWalking.walkTo -> minimap click -> on-screen tile click.
     *  Minimap clicks bypass server-side path validation and are most reliable
     *  when our starting tile is technically inside an NPC's hitbox. */
    /** 1.9.82: count walkable tiles in a box centered on `tile`. Radius
     *  was 4 in 1.9.81 — half that box overlapped Corp's own 5x5 hitbox
     *  (which we exclude), so the score barely reflected actual room
     *  openness. Bumped to 10 (21x21 box). The Corp cave is ~24 tiles
     *  across so radius 10 covers most of it from any vantage point.
     *  Walkable = inside corpCave polygon AND outside Corp's hitbox.
     *  ~441 contains() calls per invocation, 8 candidates = ~3500 ops.
     *  Still cheap (microseconds). */
    private int openTileCountAround(WorldTile tile, Area corpArea) {
        if (tile == null) return 0;
        int count = 0;
        int radius = 10;
        int tx = tile.getX(), ty = tile.getY(), tz = tile.getPlane();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                WorldTile t = new WorldTile(tx + dx, ty + dy, tz);
                if (!corpCave.contains(t)) continue;
                if (corpArea != null && corpArea.contains(t)) continue;
                count++;
            }
        }
        return count;
    }

    private boolean stepOffCorp(Npc corp) {
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null || corp == null) return false;
        Area corpArea = corp.getArea();
        if (corpArea == null) return false;

        int[][] dirs = { {-1, 0}, {1, 0}, {0, -1}, {0, 1},
                         {-1, -1}, {-1, 1}, {1, -1}, {1, 1} };

        // Build list of valid step-off candidates ranked closest-to-edge first.
        List<WorldTile> candidates = new ArrayList<>();
        for (int[] d : dirs) {
            WorldTile candidate = new WorldTile(myPos.getX() + d[0], myPos.getY() + d[1], myPos.getPlane());
            if (corpArea.contains(candidate)) continue;       // still under
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            Log.error("STOMP DEFENSE: no adjacent tile outside Corp's hitbox! Trying 2-tile step.");
            // 2-tile cardinals as last resort (e.g., if we're deep inside a 5x5)
            int[][] far = { {-2, 0}, {2, 0}, {0, -2}, {0, 2} };
            for (int[] d : far) {
                WorldTile c = new WorldTile(myPos.getX() + d[0], myPos.getY() + d[1], myPos.getPlane());
                if (!corpArea.contains(c)) candidates.add(c);
            }
        }

        // 1.9.81: rank candidates by OPENNESS — how many walkable tiles
        // exist in an 8x8 box around each candidate. User: 'we can check
        // which tiles have the most space between corp and walls ... so
        // that we prioritize tiles that have more walkable tiles in that
        // direction. that means if the corp is camping the door, we will
        // just run through him to the other side of him because the
        // entire room is full of open walkable tiles.' This biases
        // escape toward the open interior of the room rather than
        // toward the entrance corner where Corp loves to camp.
        candidates.sort((a, b) -> {
            int aScore = openTileCountAround(a, corpArea);
            int bScore = openTileCountAround(b, corpArea);
            return Integer.compare(bScore, aScore); // descending
        });

        for (WorldTile candidate : candidates) {
            // 1.9.80: check the cave polygon bounds, NOT the broken
            // Query.tiles().isReachable(). Pre-1.9.80 had no check
            // (bot would attempt walks into walls); pre-1.9.79 had the
            // isReachable check that false-negatived walkable tiles.
            // Solution: use corpCave.contains() as the wall guard —
            // tile inside the cave polygon AND outside Corp's hitbox
            // = guaranteed walkable. Tiles outside the polygon are
            // walls / out-of-bounds.
            if (!corpCave.contains(candidate)) {
                Log.debug("STOMP DEFENSE: skipping " + candidate
                        + " — outside corpCave polygon (wall)");
                continue;
            }
            Log.warn("STOMP DEFENSE: stepping off to " + candidate);

            // 1) LocalWalking (fastest path when it works).
            try {
                if (LocalWalking.walkTo(candidate)) {
                    if (waitForOutsideCorp(corpArea, 1500)) return true;
                }
            } catch (Exception ignored) {}

            // 2) On-screen tile click — the step-off tile is adjacent
            // (1 tile away), so it's ALWAYS on screen. No real player
            // minimap-clicks a single tile. 1.9.99.114: minimap fallback
            // removed entirely. User: "while spec dumping we are still
            // minimap walking to get our of the zone lagging behind and
            // we think we are in the hitbox. i thought we changed this."
            try {
                if (candidate.isVisible() && candidate.click("Walk here")) {
                    if (waitForOutsideCorp(corpArea, 1500)) return true;
                }
            } catch (Exception ignored) {}
        }

        Log.error("STOMP DEFENSE: all step-off methods failed. Corp may be blocking all neighbors.");
        return false;
    }

    private boolean waitForOutsideCorp(Area corpArea, int timeoutMs) {
        return Waiting.waitUntil(timeoutMs, () -> {
            WorldTile pos = MyPlayer.getTile();
            return pos != null && !corpArea.contains(pos);
        });
    }

    /** Top-of-tick safety. If we're standing under Corp, step off immediately.
     *  Returns true if a step was triggered (caller should skip the rest of the
     *  tick to let the action land before doing anything else). */
    private boolean antiStompTick() {
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return false;
        Npc corp = corpOpt.get();
        if (!isUnderCorp(corp)) return false;
        return stepOffCorp(corp);
    }

    private Set<String> readOthersClaimedOffsets() {
        Set<String> claimed = new HashSet<>();
        // 1.9.99.184: prefer port coordinator's real-time state when active;
        // file coord lags by INTERNAL_COORD_WRITE_INTERVAL_TICKS (5 ticks).
        TeamState ts = null;
        if (portCoordinator != null) {
            try { ts = portCoordinator.read(); } catch (Exception ignored) {}
            if (ts != null && (ts.accounts == null || ts.accounts.isEmpty())) ts = null;
        }
        if (ts == null && coordinator != null) {
            try { ts = coordinator.read(); } catch (Exception ignored) {}
        }
        if (ts == null || ts.accounts == null) return claimed;
        long now = System.currentTimeMillis();
        String myName = MyPlayer.getUsername();
        for (Map.Entry<String, AccountSnapshot> e : ts.accounts.entrySet()) {
            if (e.getKey() == null || e.getKey().equals(myName)) continue;
            AccountSnapshot s = e.getValue();
            if (s == null || s.claimedCorpOffset == null) continue;
            if (now - s.lastUpdate > INTERNAL_COORD_STALE_THRESHOLD_MS) continue;
            if (s.claimedCorpOffset.length < 2) continue;
            claimed.add(s.claimedCorpOffset[0] + "," + s.claimedCorpOffset[1]);
        }
        return claimed;
    }

    /** Phase E: recompute our claimed position relative to Corp's CURRENT tile.
     *  Corp roams, so even after we've claimed an offset, our world target changes
     *  every tick. Returns null if we don't have a claim yet. */
    private WorldTile currentClaimedPosition(Npc corp) {
        if (corp == null || mySnapshot.claimedCorpOffset == null
                || mySnapshot.claimedCorpOffset.length < 2) return null;
        WorldTile ct = corp.getTile();
        return new WorldTile(
                ct.getX() + mySnapshot.claimedCorpOffset[0],
                ct.getY() + mySnapshot.claimedCorpOffset[1],
                ct.getPlane());
    }

    /** Returns which phase the team still needs specs for:
     *  1 = defense reducers (Elder maul / DWH), 2 = combat reducers
     *  (Arclight / Darklight / Emberlight), 3 = BGS damage drain, 0 = all done.
     *  If coordinator disabled, returns 1 (no team gating). */
    /** RSNs from acceptableTeammates that are NOT in botTeammates and NOT
     *  ourselves — i.e., real human partners. Derived so users don't have
     *  to maintain a third list; just put humans in acceptableTeammates and
     *  bots in both lists. */
    private List<String> getRealTeammateRSNs() {
        List<String> real = new ArrayList<>();
        if (settings.acceptableTeammates == null) return real;
        Set<String> bots = settings.botTeammates == null
                ? Collections.emptySet()
                : new HashSet<>(settings.botTeammates);
        String myName = MyPlayer.getUsername();
        for (String rsn : settings.acceptableTeammates) {
            if (rsn == null || rsn.trim().isEmpty()) continue;
            String trimmed = rsn.trim();
            if (trimmed.equals(myName)) continue;
            if (bots.contains(trimmed)) continue;
            real.add(trimmed);
        }
        return real;
    }

    /** Real teammates currently visible to us. We only credit phase progress
     *  for partners who are actually present — out-of-area teammates don't
     *  inflate the bot's perception of team output. */
    private int countRealTeammatesNearby() {
        List<String> realRSNs = getRealTeammateRSNs();
        if (realRSNs.isEmpty()) return 0;
        int count = 0;
        try {
            for (String rsn : realRSNs) {
                boolean present = Query.players()
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(rsn))
                        .findFirst()
                        .isPresent();
                if (present) count++;
            }
        } catch (Exception ignored) {}
        return count;
    }

    // 1.9.99.222: rolling-window real-teammate tracker. Replaces both
    // lockedRealCountThisTrip (frozen at trip start) and maxRealCountThisKill
    // (ratchet-up per-kill, never drops). User wants: a real teammate counts
    // if seen at ANY point in the last 3 minutes (during travel, kill,
    // spec-dumping, anywhere), and stops counting if not seen for 3 minutes.
    // Per-RSN last-seen map: updated on every effectiveRealTeammateCount()
    // call (driven by paint + teamPhaseNeeded reads, both call several times
    // per second). 3-min grace covers POH/bank trips where the teammate is
    // off-render but still a real partner.
    private final java.util.Map<String, Long> realTeammateLastSeenAt =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long REAL_TEAMMATE_GRACE_MS = 180_000L;
    private int effectiveRealTeammateCount() {
        List<String> realRSNs = getRealTeammateRSNs();
        if (realRSNs.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        // Update sightings: any real teammate currently visible refreshes
        // their timestamp. Run inside a try so SDK hiccups don't lose the
        // existing map state.
        try {
            for (String rsn : realRSNs) {
                boolean present = Query.players()
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(rsn))
                        .findFirst()
                        .isPresent();
                if (present) realTeammateLastSeenAt.put(rsn, now);
            }
        } catch (Throwable ignored) {}
        // Count those seen within the grace window.
        int count = 0;
        for (String rsn : realRSNs) {
            Long ts = realTeammateLastSeenAt.get(rsn);
            if (ts != null && (now - ts) < REAL_TEAMMATE_GRACE_MS) count++;
        }
        return count;
    }

    /** Builds a phase-progress aggregate from our own snapshot. Used when the
     *  coordinator is off so the real-teammate multiplier still has something
     *  to scale. Mirrors the coordinator's aggregate() but for a team of one. */
    private TeamAggregate buildSoloAggregate() {
        TeamAggregate a = new TeamAggregate();
        if (mySnapshot == null) return a;
        a.liveAccounts = 1;
        a.phase3BgsDamage += mySnapshot.bgsDamageDealt;
        if (mySnapshot.bgsDamageDealt > 0) a.anyTeamDamage = true;
        if (mySnapshot.specsThisKill != null) {
            for (Map.Entry<String, Integer> e : mySnapshot.specsThisKill.entrySet()) {
                Integer phase = SPEC_PHASE.get(e.getKey());
                if (phase == null) continue;
                if (e.getValue() > 0) a.anyTeamDamage = true;
                if (phase == 1) a.phase1Specs += e.getValue();
                else if (phase == 2) a.phase2Specs += e.getValue();
            }
        }
        return a;
    }

    /** 1.9.37: per-kill ratchet on the team phase needed. Once we cross
     *  into phase N this kill, we never regress to a lower phase — even
     *  if the natural calculation says otherwise. This prevents weapon
     *  thrash from teammate-visibility flicker: realCount drops to 0 when
     *  a real teammate walks behind a wall, multiplier goes 2x → 1x, phase1Specs
     *  agg drops back below target, "natural" phase regresses 2 → 1, bot
     *  re-equips Elder maul mid-Arclight-rotation. Reset in
     *  coordinatorOnKillEnded(). */
    private int committedSpecPhase = 0;

    /** 1.9.99.167: timestamp of the first non-zero Corp HP reading this
     *  kill. Used by isInKillPhase() as the "bar has settled" gate so
     *  late-join (bot first sees Corp at low HP) still triggers kill
     *  phase. Reset on kill end / abort / bar-loss. */
    private long firstNonZeroCorpHpAt = 0;

    /** 1.9.38: per-kill ratchet on the real-teammate count. Once we've
     *  seen N real teammates nearby this kill, we keep treating "team
     *  output" as if N were still present, even if the SDK loses sight
     *  of them later. Without this ratchet a teammate stepping behind
     *  Corp regresses the multiplier 2x → 1x, doubling the effective
     *  spec target for the rest of the kill (10 Arclight → 20). Reset
     *  in coordinatorOnKillEnded().
     *
     *  1.9.99.152: now deprecated in favor of lockedRealCountThisKill —
     *  the ratchet caused a CATASTROPHIC bug when a real teammate
     *  appeared mid-kill: maxRealCountThisKill jumped from 0 to 1,
     *  multiplier flipped from 1 to 2, and ALL phase counters DOUBLED
     *  instantly. The team aggregate would skip past phase targets,
     *  isInKillPhase() would flip true via teamPhaseNeeded()==0, and
     *  the bots would stop POH'ing and commit. User: "as soon as my
     *  second account ran in the bot accounts started committing to
     *  the fight". Kept the field for log/diagnostic purposes only;
     *  not used in the multiplier path anymore. */
    private int maxRealCountThisKill = 0;

    /** 1.9.99.152: snapshot of countRealTeammatesNearby() taken at the
     *  FIRST teamPhaseNeeded() call after the bot enters a combat
     *  state this kill. Value -1 means "not yet locked"; the locker
     *  only fires while currentState ∈ {FIGHTING_CORP, USING_SPECIAL_ATTACK,
     *  USING_INITIAL_SPECS, HANDLING_DARK_CORE}. The lock prevents
     *  retroactive credit when a real teammate walks into the area
     *  mid-kill — they only get the multiplier on FUTURE kills they
     *  attend from the start. Reset to -1 in coordinatorOnKillEnded /
     *  resetPerKillStateAfterAbort. */
    private int lockedRealCountThisKill = -1;

    /** 1.9.99.157: TRIP-WIDE lock on the real-teammate count. Used by
     *  teamPhaseNeeded() to scale phase targets by team size when humans
     *  are present. Locks the FIRST time the bot enters a combat state
     *  this trip (FIGHTING_CORP / USING_SPECIAL_ATTACK / USING_INITIAL_SPECS
     *  / HANDLING_DARK_CORE). Persists until resetTripTracking() — i.e.
     *  the next bank trip. This is the stable version of the multiplier:
     *  humans walking in/out WITHIN a trip don't change it. -1 means
     *  not yet locked. */
    private int lockedRealCountThisTrip = -1;

    private int teamPhaseNeeded() {
        // 1.9.99.226: manual per-bot override. When the user disables
        // autoDetectTeamSpecs (typically because real human teammates throw
        // off the team aggregate — coordinator only sees bots), this branch
        // ignores the team aggregate AND the real-teammate multiplier
        // entirely. Each bot reads its OWN phaseX target spinners and its
        // OWN per-kill counts (via buildSoloAggregate). Set one bot's
        // phase2TargetSpecs = 0 if it has no Arclight; set its
        // phase3TargetBgsDamage high so it keeps BGS-ing while the
        // Arclight bot finishes phase 2.
        if (!settings.autoDetectTeamSpecs) {
            TeamAggregate mine = buildSoloAggregate();
            int t1 = Math.max(0, settings.phase1TargetSpecs);
            int t2 = Math.max(0, settings.phase2TargetSpecs);
            int t3 = Math.max(0, settings.phase3TargetBgsDamage);
            int naturalManual;
            if (t1 > 0 && mine.phase1Specs < t1 && ownsAnyWeaponForPhase(1)) {
                naturalManual = 1;
            } else if (t2 > 0 && mine.phase2Specs < t2 && ownsAnyWeaponForPhase(2)) {
                naturalManual = 2;
            } else if (t3 > 0 && mine.phase3BgsDamage < t3 && ownsAnyWeaponForPhase(3)) {
                naturalManual = 3;
            } else {
                return 0;
            }
            if (naturalManual > committedSpecPhase) committedSpecPhase = naturalManual;
            return committedSpecPhase;
        }

        // Base aggregate: coordinator if enabled, otherwise just our own snapshot.
        // 1.9.99.183: also use coordinator when useCoordinatorPort is on (file
        // is now publish-mirrored as backup regardless of coordinatorEnabled).
        TeamAggregate agg;
        if (settings.coordinatorEnabled || settings.useCoordinatorPort) {
            agg = coordinatorAggregate();
            if (agg == null) agg = new TeamAggregate();
        } else {
            agg = buildSoloAggregate();
        }

        // History of the real-teammate multiplier:
        //   1.9.38  ratchet-on-max-realCount per kill → flipped mid-kill
        //   1.9.99.152  lock per kill → flipped per-kill on entry
        //   1.9.99.153  removed entirely → over-spec'd (10 BGS, etc.)
        //   1.9.99.157  TRIP-WIDE lock — locked at first combat-state
        //               eval of the trip, persists until resetTripTracking
        //               (next bank trip). Humans walking in/out WITHIN a
        //               trip can't flip it. User: "the bot is doing like
        //               10 bgs attacks and the other bot is doing 4 elder
        //               maul specs now? why is the amount going up?" —
        //               caused by 1.9.99.153 removal. Multiplier restored
        //               with stable lock.
        // 1.9.99.222: rolling-window count replaces the trip-start lock.
        // A real teammate counts if seen at any point in the last 3 minutes;
        // they drop out only after 3 min of no sightings. Stable enough to
        // avoid the original visibility-flicker that motivated the lock,
        // dynamic enough to credit teammates who join mid-trip.
        int effectiveRealCount = effectiveRealTeammateCount();
        if (effectiveRealCount > 0) {
            int multiplier = 1 + effectiveRealCount;
            agg.phase1Specs *= multiplier;
            agg.phase2Specs *= multiplier;
            agg.phase3BgsDamage *= multiplier;
        }

        // 1.9.59: skip any phase the bot doesn't own a weapon for. User:
        // 'we should be able to detect that we dont have a bgs (or
        // whatever specific weapon) and skip that phase if we somehow
        // get to it. the real problem is we somehow keep ending up in
        // phase 3 when just from our items, phase 3 shouldnt exist.'
        // Phases the bot can't contribute to are treated as completed.
        int natural;
        if (agg.phase1Specs < INTERNAL_PHASE1_TARGET && ownsAnyWeaponForPhase(1)) natural = 1;
        else if (agg.phase2Specs < INTERNAL_PHASE2_TARGET && ownsAnyWeaponForPhase(2)) natural = 2;
        else if (agg.phase3BgsDamage < INTERNAL_PHASE3_BGS_DAMAGE && ownsAnyWeaponForPhase(3)) natural = 3;
        else return 0; // all targets met OR no usable weapon — kill phase

        // Ratchet up to the highest phase we've ever needed this kill, never
        // back down. Reset to 0 on kill-end via coordinatorOnKillEnded.
        if (natural > committedSpecPhase) committedSpecPhase = natural;
        return committedSpecPhase;
    }

    /** Record a successful spec to our local snapshot. For non-BGS weapons, just
     *  increments the per-kill count. For BGS, accepts an explicit damage value
     *  if available (Phase D — via Hitsplat.isMine()); otherwise falls back to
     *  a rough +30 approximation. */
    private void recordSpecUsed(String weaponName) { recordSpecUsed(weaponName, -1); }

    private void recordSpecUsed(String weaponName, int actualBgsDamage) {
        if (weaponName == null) return;
        if (mySnapshot.specsThisKill == null) mySnapshot.specsThisKill = new LinkedHashMap<>();
        Integer current = mySnapshot.specsThisKill.get(weaponName);
        mySnapshot.specsThisKill.put(weaponName, (current == null ? 0 : current) + 1);
        if ("Bandos godsword".equalsIgnoreCase(weaponName)) {
            // 1.9.99.60: changed `>= 0` to `> 0`. Pre-1.9.99.60 a 0 value
            // (no hitsplat captured at confirm time — e.g. queue confirmed
            // via XP-only because the hitsplat already aged out, or the
            // hit landed but getMyLargestRecentHitOnCorp returned 0 due
            // to timing) was added as literal 0 damage to bgsDamageDealt.
            // Phase 3 target is 200 damage drained (100 effective in
            // duo with the 2x multiplier); at 0 per spec, Phase 3 was
            // never reachable — bot just BGS'd forever. Now 0 falls
            // back to the +30 default so each confirmed BGS spec
            // advances the counter. User: "stuck on the bgs forever".
            int credit = actualBgsDamage > 0 ? actualBgsDamage : 30;
            mySnapshot.bgsDamageDealt += credit;
            Log.info("BGS damage credited: +" + credit
                    + " (actual hitsplat=" + actualBgsDamage
                    + ", total=" + mySnapshot.bgsDamageDealt + "/"
                    + INTERNAL_PHASE3_BGS_DAMAGE + ")");
        }
    }

    /** Phase D: read Corp's hitsplats and return the largest one tagged as ours.
     *  Hitsplats stick around ~6 ticks, so calling this right after a spec
     *  confirmation gives us the spec's actual damage. */
    private int getMyLargestRecentHitOnCorp(Npc corp) {
        if (corp == null) return 0;
        try {
            return corp.getHitsplats().stream()
                    .filter(h -> { try { return h.isMine(); } catch (Exception e) { return false; } })
                    .mapToInt(h -> { try { return h.getValue(); } catch (Exception e) { return 0; } })
                    .max()
                    .orElse(0);
        } catch (Exception e) { return 0; }
    }

    /** 1.9.59: per-phase spec weapon preference. Index 0 unused; phases 1-3
     *  match teamPhaseNeeded()'s output. */
    private static final String[][] PHASE_SPEC_WEAPONS = {
            null,
            { "Elder maul", "Dragon warhammer" },           // Phase 1 (defense)
            { "Emberlight", "Arclight", "Darklight" },      // Phase 2 (combat levels)
            { "Bandos godsword" }                           // Phase 3 (damage drain)
    };

    /** 1.9.59: true if the bot owns (in inventory or equipment) at least one
     *  spec weapon for the given phase. Used by teamPhaseNeeded() to SKIP
     *  phases the bot can't contribute to — most often phase 3 on accounts
     *  without BGS. User: 'we should be able to detect that we dont have a
     *  bgs (or whatever specific weapon) and skip that phase if we somehow
     *  get to it.' */
    private boolean ownsAnyWeaponForPhase(int phase) {
        if (phase < 1 || phase >= PHASE_SPEC_WEAPONS.length) return false;
        if (PHASE_SPEC_WEAPONS[phase] == null) return false;
        List<String> owned = getOwnedSpecWeapons();
        for (String w : PHASE_SPEC_WEAPONS[phase]) {
            if (!owned.contains(w)) continue;
            if (Inventory.contains(new String[]{ w }) || Equipment.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Phase D: pick the highest-priority spec weapon I own for the team's
     *  currently-needed phase. Returns null if no usable weapon for this phase
     *  (in which case the bot should fall through to DPS). */
    private String pickSpecWeaponForCurrentPhase() {
        int phase = teamPhaseNeeded();
        if (phase == 0) return null;
        if (phase >= PHASE_SPEC_WEAPONS.length || PHASE_SPEC_WEAPONS[phase] == null) return null;
        List<String> owned = getOwnedSpecWeapons();
        for (String w : PHASE_SPEC_WEAPONS[phase]) {
            if (!owned.contains(w)) continue;
            // 1.9.33: check Equipment as well as Inventory.
            if (Inventory.contains(new String[]{ w }) || Equipment.contains(w)) {
                return w;
            }
        }
        return null;
    }

    /** Phase D: refresh chosenSpecWeapon based on the team's current phase needs.
     *  If coordinator says we need Phase 2 and we currently have Elder maul (Phase 1)
     *  set, switch to our best Phase 2 weapon. Returns true if a usable weapon was
     *  picked; false if we have nothing for the current phase. */
    private boolean refreshSpecWeaponForPhase() {
        // 1.9.5: removed the coordinatorEnabled gate. teamPhaseNeeded() works
        // for solo bots too via buildSoloAggregate (sums per-weapon-by-phase
        // from mySnapshot.specsThisKill). Pre-1.9.5 the rotation was locked
        // to coordinator mode, so a solo bot would stay on Elder maul forever
        // even after the 4 phase-1 specs landed.
        String desired = pickSpecWeaponForCurrentPhase();
        if (desired == null) {
            // No weapon for current phase needed — don't null out
            // chosenSpecWeapon (keep it for kill-phase fall-through logic).
            return false;
        }
        if (!desired.equals(chosenSpecWeapon)) {
            Log.info("Phase rotation: switching spec weapon "
                    + chosenSpecWeapon + " -> " + desired);
            chosenSpecWeapon = desired;
            // Caller is responsible for equipping the new weapon.
        }
        return true;
    }

    /** Combined gate: should we spec right now? Considers Corp HP threshold,
     *  team phase progress, and whether our current spec weapon belongs to the
     *  phase the team still needs. In FEROX_ONLY mode (no mid-trip refill) we
     *  ignore phase mismatches — every spec we can land is worth landing. */
    private boolean shouldSpecNowConsideringTeam() {
        boolean aggressive = shouldDumpSpecsAggressively();
        int needed = teamPhaseNeeded();
        if (needed == 0) {
            // Team phases all done. In FEROX mode we still spec for damage —
            // the limited budget means every spec is worthwhile DPS.
            return aggressive;
        }
        if (chosenSpecWeapon == null) return true; // not yet detected, let existing logic handle
        Integer myWeaponPhase = SPEC_PHASE.get(chosenSpecWeapon);
        if (myWeaponPhase == null || myWeaponPhase == 0) return true; // bonus DPS weapon, allow
        // If my spec weapon is for a phase the team has already finished, don't
        // skip — in aggressive mode we'd rather burn a stat-drain weapon on
        // damage than save it for a refill that never comes.
        if (myWeaponPhase < needed) return aggressive;
        return true;
    }

    // ========== PROFILE HELPERS ==========

    private List<String> getProfileNames() {
        try {
            return ScriptSettings.getDefault().getSaveNames().stream()
                    .filter(n -> n.startsWith(SETTINGS_PREFIX))
                    .map(n -> n.substring(SETTINGS_PREFIX.length()))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private CorpSettings loadProfile(String name) {
        try {
            CorpSettings s = ScriptSettings.getDefault()
                    .load(SETTINGS_PREFIX + name, CorpSettings.class)
                    .orElseGet(CorpSettings::new);
            return migrateLegacySettings(s);
        } catch (Exception e) { return new CorpSettings(); }
    }

    /** Migrate profiles saved under earlier versions to the current schema.
     *  Right now this just maps the legacy useOwnHouse boolean (1.5.x) to the
     *  new pohSource enum (1.6.0). Keep the migration small and idempotent. */
    private CorpSettings migrateLegacySettings(CorpSettings s) {
        if (s == null) return new CorpSettings();
        if (s.pohSource == null || s.pohSource.isEmpty()) {
            s.pohSource = s.useOwnHouse ? POH_SOURCE_OWN_HOUSE : POH_SOURCE_FRIEND_HOUSE;
        }
        return s;
    }

    private void saveProfile(String name, CorpSettings s) {
        try { ScriptSettings.getDefault().save(SETTINGS_PREFIX + name, s); }
        catch (Exception e) { Log.warn("Save '" + name + "' failed: " + e.getMessage()); }
    }

    private void deleteProfile(String name) {
        try { ScriptSettings.getDefault().delete(SETTINGS_PREFIX + name); }
        catch (Exception e) { Log.warn("Delete '" + name + "' failed: " + e.getMessage()); }
    }

    private void refreshProfileBox(JComboBox<String> box) {
        Object selected = box.getSelectedItem();
        box.removeAllItems();
        for (String n : getProfileNames()) box.addItem(n);
        if (selected != null) box.setSelectedItem(selected);
    }

    // ========== SETTINGS DIALOG ==========

    private boolean showSettingsDialog() {
        // Pre-load the default profile if it exists.
        CorpSettings preload = loadProfile(DEFAULT_PROFILE);
        settings = preload;
        final boolean[] ok = { false };
        try {
            SwingUtilities.invokeAndWait(() -> {
                JDialog dlg = new JDialog((Frame) null, "Corporeal Beast Bot", true);
                dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JTabbedPane tabs = new JTabbedPane();

                // --- Combat tab ---
                JComboBox<String> mainWeapon = new JComboBox<>(MAIN_WEAPON_OPTIONS);
                mainWeapon.setEditable(true);
                mainWeapon.setSelectedItem(settings.mainWeapon);
                // 1.9.99.68: user-designated dark-core killer
                JComboBox<String> coreKillerWeapon = new JComboBox<>(new String[]{
                        "Elder maul", "Dragon warhammer", "Bandos godsword",
                        "Noxious halberd", "Scythe of vitur", "Crystal halberd"
                });
                coreKillerWeapon.setEditable(true);
                coreKillerWeapon.setSelectedItem(settings.coreKillerWeapon == null
                        ? "Elder maul" : settings.coreKillerWeapon);
                JTextField food1 = new JTextField(settings.foodNames.length > 0 ? settings.foodNames[0] : "Shark", 14);
                JTextField food2 = new JTextField(settings.foodNames.length > 1 ? settings.foodNames[1] : "Cooked karambwan", 14);
                JCheckBox useVengeance = new JCheckBox("Cast Vengeance (requires Lunars + runes)", settings.useVengeance);
                JComboBox<String> combatPotion = new JComboBox<>(COMBAT_POTION_OPTIONS);
                combatPotion.setEditable(true);
                combatPotion.setSelectedItem(settings.combatPotionType);
                JCheckBox showOverlay = new JCheckBox("Show live status overlay window", settings.showOverlay);
                JPanel combatP = new JPanel(new GridLayout(0, 2, 4, 4));
                combatP.setBorder(BorderFactory.createTitledBorder("Combat"));
                combatP.add(new JLabel("Main weapon:"));      combatP.add(mainWeapon);
                combatP.add(new JLabel("Core killer weapon:")); combatP.add(coreKillerWeapon);
                combatP.add(new JLabel("Food (primary):"));   combatP.add(food1);
                combatP.add(new JLabel("Food (secondary):"));  combatP.add(food2);
                combatP.add(new JLabel("Vengeance:"));        combatP.add(useVengeance);
                combatP.add(new JLabel("Combat potion:"));    combatP.add(combatPotion);
                combatP.add(new JLabel("Status overlay:"));   combatP.add(showOverlay);
                tabs.addTab("Combat", combatP);

                // --- Spec tab ---
                JSpinner corpMinHp = new JSpinner(new SpinnerNumberModel(settings.corpMinHpForSpec, 0, 2200, 50));
                JSpinner restoreCycles = new JSpinner(new SpinnerNumberModel(settings.totalRestorationCycles, 1, 10, 1));
                JCheckBox legacyDarkCore = new JCheckBox(
                        "Use legacy dodge logic for dark core (fallback)",
                        settings.useLegacyDarkCoreLogic);
                // 1.9.99.89: spec-dump panic-tele threshold.
                JSpinner specDumpPanicHp = new JSpinner(
                        new SpinnerNumberModel(settings.specDumpPanicTeleHp, 10, 80, 5));
                // 1.9.99.178: GUI spinners for previously hidden settings.
                JSpinner encroachTiles = new JSpinner(new SpinnerNumberModel(
                        Math.max(1, Math.min(8, settings.encroachmentRelocateTiles)), 1, 8, 1));
                JSpinner phase1Target = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.phase1TargetSpecs), 0, 20, 1));
                JSpinner phase2Target = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.phase2TargetSpecs), 0, 50, 1));
                JSpinner phase3Target = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.phase3TargetBgsDamage), 0, 800, 10));
                // 1.9.99.226: toggle between team-aggregate auto-detect and
                // per-bot manual targets. ON = current behavior (team-wide
                // detection, divided by real teammates). OFF = each bot
                // follows its OWN target spinners against its OWN per-kill
                // counts, no team aggregate, no human-teammate multiplier.
                JCheckBox autoDetectSpecs = new JCheckBox(
                        "Auto-detect team required specs",
                        settings.autoDetectTeamSpecs);
                autoDetectSpecs.setToolTipText("<html>ON: targets below are TEAM-WIDE. "
                        + "Team aggregate vs the target triggers kill phase. "
                        + "Divided by (1 + real teammates).<br>"
                        + "OFF: per-bot manual override. This bot uses its OWN "
                        + "counts vs the targets below. Use when playing with "
                        + "humans whose specs aren't tracked — set each bot's "
                        + "targets to what THAT account should personally do "
                        + "per kill.</html>");
                JSpinner vengStopHp = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, Math.min(100, settings.corpLowHealthVengStop)), 0, 100, 5));
                // 1.9.99.178: grouped sub-panels with TitledBorder for visual
                // separation within the same tab. User: "can you add dividers
                // in swing between gui categories that are in the same tab
                // but different settings areas".
                JPanel specCore = new JPanel(new GridLayout(0, 2, 4, 4));
                specCore.setBorder(BorderFactory.createTitledBorder("Spec floor & cycles"));
                specCore.add(new JLabel("Spec only if Corp HP >=")); specCore.add(corpMinHp);
                specCore.add(new JLabel("Restoration cycles per trip:")); specCore.add(restoreCycles);
                specCore.add(new JLabel("Spec-dump panic tele HP <=:")); specCore.add(specDumpPanicHp);
                specCore.add(new JLabel("Dark core strategy:")); specCore.add(legacyDarkCore);

                JPanel specPhase = new JPanel(new GridLayout(0, 2, 4, 4));
                specPhase.setBorder(BorderFactory.createTitledBorder("Phase targets"));
                specPhase.add(autoDetectSpecs); specPhase.add(new JLabel(""));
                specPhase.add(new JLabel("Phase 1 spec target (EM+DWH):")); specPhase.add(phase1Target);
                specPhase.add(new JLabel("Phase 2 spec target (Arc/Dark/Ember):")); specPhase.add(phase2Target);
                specPhase.add(new JLabel("Phase 3 BGS damage target:")); specPhase.add(phase3Target);

                JPanel specMisc = new JPanel(new GridLayout(0, 2, 4, 4));
                specMisc.setBorder(BorderFactory.createTitledBorder("Vengeance & positioning"));
                specMisc.add(new JLabel("Vengeance stop Corp HP <=:")); specMisc.add(vengStopHp);
                specMisc.add(new JLabel("Relocate if teammate within (tiles):")); specMisc.add(encroachTiles);

                JPanel specP = new JPanel();
                specP.setLayout(new BoxLayout(specP, BoxLayout.Y_AXIS));
                specP.add(specCore);
                specP.add(specPhase);
                specP.add(specMisc);
                tabs.addTab("Spec", specP);

                // --- POH / Team tab ---
                JTextField friendName = new JTextField(settings.friendName, 14);
                JTextArea teammates = new JTextArea(String.join("\n", settings.acceptableTeammates), 5, 16);
                JComboBox<String> pohSource = new JComboBox<>(POH_SOURCE_OPTIONS);
                pohSource.setSelectedItem(getPohSource());
                JCheckBox isPohHost = new JCheckBox(
                        "This account hosts the team's POH (publishes via coordinator)",
                        settings.isPohHost);
                // 1.9.13: poolName / jewelleryBoxName GUI fields removed —
                // detection is now action-based ("Drink" / "Corporeal Beast")
                // and works across all pool/box tiers automatically.
                JCheckBox waitForTeammateSpec = new JCheckBox(
                        "Wait at pool for teammates to refresh spec (coordinator)",
                        settings.waitForTeammateSpec);
                JSpinner designatedWorld = new JSpinner(new SpinnerNumberModel(
                        settings.designatedWorld, 0, 600, 1));
                JSpinner w330MaxHostAttempts = new JSpinner(new SpinnerNumberModel(
                        Math.max(1, settings.w330MaxHostAttempts), 1, 10, 1));
                JCheckBox coordEnabled = new JCheckBox(
                        "Enable multi-account coordinator", settings.coordinatorEnabled);
                JTextArea botList = new JTextArea(
                        settings.botTeammates == null ? "" : String.join("\n", settings.botTeammates), 4, 18);
                // 1.9.99.178: coordinator port + stagger spinners.
                JCheckBox useCoordPort = new JCheckBox(
                        "Use real-time port coordinator (TCP)", settings.useCoordinatorPort);
                JCheckBox coordIsHost = new JCheckBox(
                        "This bot is the coord host", settings.coordinatorIsHost);
                JCheckBox autoElectCoord = new JCheckBox(
                        "Auto-elect host (first bot wins)", settings.autoElectCoordinator);
                JSpinner coordPortId = new JSpinner(new SpinnerNumberModel(
                        Math.max(1, Math.min(99, settings.coordinatorPortId)), 1, 99, 1));
                JTextField coordHostIp = new JTextField(
                        settings.coordinatorHostIp == null ? "127.0.0.1" : settings.coordinatorHostIp, 12);
                JSpinner initialTripStagger = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.initialTripStaggerSec), 0, 120, 5));
                JSpinner pohOccupiedDelay = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.pohOccupiedDelaySec), 0, 30, 1));
                JSpinner pohOccupiedMaxWait = new JSpinner(new SpinnerNumberModel(
                        Math.max(1, settings.pohOccupiedMaxWaitSec), 1, 120, 5));

                JPanel teamP = new JPanel();
                teamP.setLayout(new BoxLayout(teamP, BoxLayout.Y_AXIS));

                JPanel pohGroup = new JPanel(new GridLayout(0, 2, 4, 4));
                pohGroup.setBorder(BorderFactory.createTitledBorder("POH"));
                pohGroup.add(new JLabel("PoH source:"));        pohGroup.add(pohSource);
                pohGroup.add(new JLabel("Friend's RSN:"));      pohGroup.add(friendName);
                pohGroup.add(new JLabel("POH host role:"));     pohGroup.add(isPohHost);
                pohGroup.add(new JLabel("Coordinator wait:"));  pohGroup.add(waitForTeammateSpec);

                JPanel coordGroup = new JPanel(new GridLayout(0, 2, 4, 4));
                coordGroup.setBorder(BorderFactory.createTitledBorder("Coordinator"));
                coordGroup.add(new JLabel("Coordinator (file):")); coordGroup.add(coordEnabled);
                coordGroup.add(new JLabel("Use TCP port coordinator:")); coordGroup.add(useCoordPort);
                coordGroup.add(new JLabel("Auto-elect host:")); coordGroup.add(autoElectCoord);
                coordGroup.add(new JLabel("Is coordinator host (manual):")); coordGroup.add(coordIsHost);
                coordGroup.add(new JLabel("Coord port ID (45000+ID):")); coordGroup.add(coordPortId);
                coordGroup.add(new JLabel("Coord host IP:")); coordGroup.add(coordHostIp);

                JPanel staggerGroup = new JPanel(new GridLayout(0, 2, 4, 4));
                staggerGroup.setBorder(BorderFactory.createTitledBorder("Multi-bot stagger"));
                staggerGroup.add(new JLabel("Initial trip stagger (sec):")); staggerGroup.add(initialTripStagger);
                staggerGroup.add(new JLabel("POH occupied delay (sec, 0=off):")); staggerGroup.add(pohOccupiedDelay);
                staggerGroup.add(new JLabel("POH occupied max wait (sec):")); staggerGroup.add(pohOccupiedMaxWait);

                JPanel w330Group = new JPanel(new GridLayout(0, 2, 4, 4));
                w330Group.setBorder(BorderFactory.createTitledBorder("W330 random POH"));
                w330Group.add(new JLabel("W330 return world (0 = remember):")); w330Group.add(designatedWorld);
                w330Group.add(new JLabel("W330 max host tries:")); w330Group.add(w330MaxHostAttempts);

                JPanel teamLists = new JPanel(new GridLayout(1, 2, 6, 6));
                JPanel acceptablePanel = new JPanel(new BorderLayout());
                acceptablePanel.setBorder(BorderFactory.createTitledBorder("Acceptable teammates (one RSN per line)"));
                acceptablePanel.add(new JScrollPane(teammates), BorderLayout.CENTER);
                JPanel botListPanel = new JPanel(new BorderLayout());
                botListPanel.setBorder(BorderFactory.createTitledBorder("Bot teammate RSNs (coordinator filter)"));
                botListPanel.add(new JScrollPane(botList), BorderLayout.CENTER);
                teamLists.add(acceptablePanel);
                teamLists.add(botListPanel);

                teamP.add(pohGroup);
                teamP.add(coordGroup);
                teamP.add(staggerGroup);
                teamP.add(w330Group);
                teamP.add(teamLists);
                tabs.addTab("POH / Team", teamP);

                // --- Supplies tab (1.9.99.178) ---
                JSpinner targetSharks = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.targetSharks), 0, 28, 1));
                JSpinner targetKarambwans = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.targetKarambwans), 0, 28, 1));
                JSpinner targetSuperRestores = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.targetSuperRestores), 0, 10, 1));
                JSpinner targetSuperCombat = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.targetSuperCombat), 0, 5, 1));
                JSpinner minFoodCount = new JSpinner(new SpinnerNumberModel(
                        Math.max(0, settings.minFoodCount), 0, 28, 1));

                JPanel foodGroup = new JPanel(new GridLayout(0, 2, 4, 4));
                foodGroup.setBorder(BorderFactory.createTitledBorder("Food targets"));
                foodGroup.add(new JLabel("Sharks per trip:")); foodGroup.add(targetSharks);
                foodGroup.add(new JLabel("Karambwans per trip:")); foodGroup.add(targetKarambwans);
                foodGroup.add(new JLabel("Resupply if food below:")); foodGroup.add(minFoodCount);

                JPanel potionGroup = new JPanel(new GridLayout(0, 2, 4, 4));
                potionGroup.setBorder(BorderFactory.createTitledBorder("Potion targets"));
                potionGroup.add(new JLabel("Super restore doses (4-dose):")); potionGroup.add(targetSuperRestores);
                potionGroup.add(new JLabel("Super combat doses (4-dose):")); potionGroup.add(targetSuperCombat);

                JPanel supplyP = new JPanel();
                supplyP.setLayout(new BoxLayout(supplyP, BoxLayout.Y_AXIS));
                supplyP.add(foodGroup);
                supplyP.add(potionGroup);
                tabs.addTab("Supplies", supplyP);

                // --- Loot tab ---
                JTextArea loot = new JTextArea(String.join("\n", settings.valuableLoot), 8, 20);
                JPanel lootP = new JPanel(new BorderLayout());
                lootP.setBorder(BorderFactory.createTitledBorder("Valuable loot (one name per line)"));
                lootP.add(new JScrollPane(loot), BorderLayout.CENTER);
                tabs.addTab("Loot", lootP);

                // --- Profile row ---
                JComboBox<String> profileBox = new JComboBox<>(getProfileNames().toArray(new String[0]));
                JButton loadBtn = new JButton("Load");
                JButton saveAsBtn = new JButton("Save as...");
                JButton deleteBtn = new JButton("Delete");

                Runnable populate = () -> {
                    mainWeapon.setSelectedItem(settings.mainWeapon);
                    food1.setText(settings.foodNames.length > 0 ? settings.foodNames[0] : "Shark");
                    food2.setText(settings.foodNames.length > 1 ? settings.foodNames[1] : "Cooked karambwan");
                    useVengeance.setSelected(settings.useVengeance);
                    combatPotion.setSelectedItem(settings.combatPotionType);
                    showOverlay.setSelected(settings.showOverlay);
                    corpMinHp.setValue(settings.corpMinHpForSpec);
                    restoreCycles.setValue(settings.totalRestorationCycles);
                    legacyDarkCore.setSelected(settings.useLegacyDarkCoreLogic);
                    pohSource.setSelectedItem(getPohSource());
                    friendName.setText(settings.friendName);
                    teammates.setText(String.join("\n", settings.acceptableTeammates));
                    isPohHost.setSelected(settings.isPohHost);
                    coordEnabled.setSelected(settings.coordinatorEnabled);
                    waitForTeammateSpec.setSelected(settings.waitForTeammateSpec);
                    designatedWorld.setValue(settings.designatedWorld);
                    w330MaxHostAttempts.setValue(Math.max(1, settings.w330MaxHostAttempts));
                    botList.setText(settings.botTeammates == null ? "" : String.join("\n", settings.botTeammates));
                    loot.setText(String.join("\n", settings.valuableLoot));
                    // 1.9.99.178: populate new fields
                    encroachTiles.setValue(Math.max(1, Math.min(8, settings.encroachmentRelocateTiles)));
                    phase1Target.setValue(Math.max(0, settings.phase1TargetSpecs));
                    phase2Target.setValue(Math.max(0, settings.phase2TargetSpecs));
                    phase3Target.setValue(Math.max(0, settings.phase3TargetBgsDamage));
                    autoDetectSpecs.setSelected(settings.autoDetectTeamSpecs);
                    vengStopHp.setValue(Math.max(0, Math.min(100, settings.corpLowHealthVengStop)));
                    useCoordPort.setSelected(settings.useCoordinatorPort);
                    coordIsHost.setSelected(settings.coordinatorIsHost);
                    autoElectCoord.setSelected(settings.autoElectCoordinator);
                    coordPortId.setValue(Math.max(1, Math.min(99, settings.coordinatorPortId)));
                    coordHostIp.setText(settings.coordinatorHostIp == null ? "127.0.0.1" : settings.coordinatorHostIp);
                    initialTripStagger.setValue(Math.max(0, settings.initialTripStaggerSec));
                    pohOccupiedDelay.setValue(Math.max(0, settings.pohOccupiedDelaySec));
                    pohOccupiedMaxWait.setValue(Math.max(1, settings.pohOccupiedMaxWaitSec));
                    targetSharks.setValue(Math.max(0, settings.targetSharks));
                    targetKarambwans.setValue(Math.max(0, settings.targetKarambwans));
                    targetSuperRestores.setValue(Math.max(0, settings.targetSuperRestores));
                    targetSuperCombat.setValue(Math.max(0, settings.targetSuperCombat));
                    minFoodCount.setValue(Math.max(0, settings.minFoodCount));
                };
                Runnable collect = () -> {
                    Object selectedWeapon = mainWeapon.getSelectedItem();
                    settings.mainWeapon = selectedWeapon == null
                            ? "Osmumten's fang"
                            : selectedWeapon.toString().trim();
                    Object selectedCoreKiller = coreKillerWeapon.getSelectedItem();
                    settings.coreKillerWeapon = selectedCoreKiller == null
                            ? "Elder maul"
                            : selectedCoreKiller.toString().trim();
                    String f1 = food1.getText().trim();
                    String f2 = food2.getText().trim();
                    settings.foodNames = f2.isEmpty() ? new String[]{ f1 } : new String[]{ f1, f2 };
                    settings.useVengeance = useVengeance.isSelected();
                    Object selectedPotion = combatPotion.getSelectedItem();
                    settings.combatPotionType = selectedPotion == null
                            ? "Divine super combat"
                            : selectedPotion.toString().trim();
                    settings.showOverlay = showOverlay.isSelected();
                    settings.corpMinHpForSpec = (Integer) corpMinHp.getValue();
                    settings.totalRestorationCycles = (Integer) restoreCycles.getValue();
                    settings.specDumpPanicTeleHp = (Integer) specDumpPanicHp.getValue();
                    settings.useLegacyDarkCoreLogic = legacyDarkCore.isSelected();
                    // 1.9.99.178: collect new fields.
                    settings.encroachmentRelocateTiles = (Integer) encroachTiles.getValue();
                    settings.phase1TargetSpecs = (Integer) phase1Target.getValue();
                    settings.phase2TargetSpecs = (Integer) phase2Target.getValue();
                    settings.phase3TargetBgsDamage = (Integer) phase3Target.getValue();
                    settings.autoDetectTeamSpecs = autoDetectSpecs.isSelected();
                    settings.corpLowHealthVengStop = (Integer) vengStopHp.getValue();
                    settings.useCoordinatorPort = useCoordPort.isSelected();
                    settings.coordinatorIsHost = coordIsHost.isSelected();
                    settings.autoElectCoordinator = autoElectCoord.isSelected();
                    settings.coordinatorPortId = (Integer) coordPortId.getValue();
                    String ipText = coordHostIp.getText();
                    settings.coordinatorHostIp = ipText == null || ipText.trim().isEmpty()
                            ? "127.0.0.1" : ipText.trim();
                    settings.initialTripStaggerSec = (Integer) initialTripStagger.getValue();
                    settings.pohOccupiedDelaySec = (Integer) pohOccupiedDelay.getValue();
                    settings.pohOccupiedMaxWaitSec = (Integer) pohOccupiedMaxWait.getValue();
                    settings.targetSharks = (Integer) targetSharks.getValue();
                    settings.targetKarambwans = (Integer) targetKarambwans.getValue();
                    settings.targetSuperRestores = (Integer) targetSuperRestores.getValue();
                    settings.targetSuperCombat = (Integer) targetSuperCombat.getValue();
                    settings.minFoodCount = (Integer) minFoodCount.getValue();
                    Object selectedSource = pohSource.getSelectedItem();
                    settings.pohSource = selectedSource == null
                            ? POH_SOURCE_OWN_HOUSE
                            : selectedSource.toString();
                    settings.useOwnHouse = POH_SOURCE_OWN_HOUSE.equals(settings.pohSource);
                    settings.friendName = friendName.getText().trim();
                    settings.acceptableTeammates = Arrays.stream(teammates.getText().split("\\R"))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    settings.isPohHost = isPohHost.isSelected();
                    settings.coordinatorEnabled = coordEnabled.isSelected();
                    settings.waitForTeammateSpec = waitForTeammateSpec.isSelected();
                    settings.designatedWorld = (Integer) designatedWorld.getValue();
                    settings.w330MaxHostAttempts = (Integer) w330MaxHostAttempts.getValue();
                    settings.botTeammates = Arrays.stream(botList.getText().split("\\R"))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    settings.valuableLoot = Arrays.stream(loot.getText().split("\\R"))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                    // Owned spec weapons are auto-detected at trip start —
                    // no GUI for them anymore.
                    settings.accountRole = "auto";
                };
                populate.run();

                loadBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    settings = loadProfile(name);
                    populate.run();
                    Log.info("Loaded profile: " + name);
                });
                saveAsBtn.addActionListener(e -> {
                    String name = JOptionPane.showInputDialog(dlg, "Profile name:",
                            "Save Profile", JOptionPane.QUESTION_MESSAGE);
                    if (name == null || name.trim().isEmpty()) return;
                    name = name.trim();
                    collect.run();
                    saveProfile(name, settings);
                    refreshProfileBox(profileBox);
                    profileBox.setSelectedItem(name);
                });
                deleteBtn.addActionListener(e -> {
                    String name = (String) profileBox.getSelectedItem();
                    if (name == null || name.isEmpty()) return;
                    int c = JOptionPane.showConfirmDialog(dlg, "Delete profile '" + name + "'?",
                            "Confirm Delete", JOptionPane.YES_NO_OPTION);
                    if (c == JOptionPane.YES_OPTION) {
                        deleteProfile(name);
                        refreshProfileBox(profileBox);
                    }
                });

                JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
                profileRow.setBorder(BorderFactory.createTitledBorder("Profile"));
                profileRow.add(new JLabel("Saved:")); profileRow.add(profileBox);
                profileRow.add(loadBtn); profileRow.add(saveAsBtn); profileRow.add(deleteBtn);

                JButton start = new JButton("Start");
                JButton cancel = new JButton("Cancel");
                start.addActionListener(e -> {
                    collect.run();
                    saveProfile(DEFAULT_PROFILE, settings);
                    ok[0] = true;
                    dlg.dispose();
                });
                cancel.addActionListener(e -> dlg.dispose());
                JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                btns.add(start); btns.add(cancel);

                dlg.setLayout(new BorderLayout());
                dlg.add(profileRow, BorderLayout.NORTH);
                dlg.add(tabs, BorderLayout.CENTER);
                dlg.add(btns, BorderLayout.SOUTH);
                dlg.pack();
                dlg.setLocationRelativeTo(null);
                dlg.setVisible(true);
            });
        } catch (Exception e) {
            Log.error("Settings dialog failed: " + e.getMessage());
            return false;
        }
        return ok[0];
    }
}