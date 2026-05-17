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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.*;
import java.util.stream.Collectors;


/*
 * CHANGELOG
 *   1.9.24 (2026-05-16) - Three fixes:
 *                         (a) Friend-widget shortcut matches ANY descendant
 *                             of [162, 39] (length>=2, path[1]==39).
 *                             User saw shortcut on screen but the
 *                             length==3 && path[2]==0 filter missed it —
 *                             the dialog's child slot isn't always 0.
 *                             Now match anywhere under [162, 39, *].
 *                         (b) Corp-death detection requires EXPLICIT
 *                             HP-at-zero observation. Pre-1.9.24 said
 *                             "Corp dead" whenever health bar invisible
 *                             AND not in combat — but the bar can be
 *                             invisible 1-2 ticks during transitions
 *                             while Corp is alive. Bot wasted kills
 *                             transitioning to LOOTING prematurely.
 *                             Now: LOOTING only fires when (a) we observe
 *                             corp.getHealthBarPercent() <= 1.0, OR
 *                             (b) corp NPC is gone AND we previously
 *                             flipped the corpSeenAtZeroHp flag (per-kill
 *                             reset). Real-player teams rely on direct
 *                             observation; all-bot teams via coordinator
 *                             would also flip this flag on kill signal.
 *                         (c) "Poking with Arclight" fix. When the bot
 *                             reaches kill phase (Corp HP < floor) with
 *                             a spec weapon still equipped, queue Fang
 *                             swap immediately. Pre-1.9.24 the swap
 *                             happened only after a spec FIRE; if
 *                             shouldUseSpecialAttack gated the spec
 *                             (HP < floor), no swap queued and the bot
 *                             poked with Arclight rest of the kill.
 *   1.9.23 (2026-05-16) - Three fixes after the 1.9.22 test:
 *                         (a) Bot stopped attacking Corp after the
 *                             Elder maul -> Arclight phase rotation.
 *                             Pre-1.9.23 the re-engage gate was
 *                             "if (!isPlayerInCombat()) attack" — but
 *                             isPlayerInCombat is true whenever Corp
 *                             hits us, even if we aren't attacking
 *                             back. With Arclight equipped (1H), the
 *                             bot ended up in combat (taking hits) but
 *                             not actually swinging at Corp; spec
 *                             never fired and Corp died of teammate
 *                             damage. Now uses isPlayerAttackingCorp
 *                             which checks our actual interaction
 *                             target, not just combat tag.
 *                         (b) equipSpecWeapon now also equips a
 *                             defender after wielding a 1H spec weapon
 *                             (Arclight, Darklight, Emberlight, Dragon
 *                             halberd). 2H weapons (Elder maul, DWH,
 *                             BGS, Crystal halberd) take the offhand
 *                             slot themselves so we skip defender for
 *                             those.
 *                         (c) Close-bank-before-tele extended to
 *                             teleportToFeroxEnclave and the house tab
 *                             tele (in addition to handleTravelingToCorp
 *                             which got the fix in 1.9.22). All
 *                             inventory-item tele clicks now close the
 *                             bank first.
 *   1.9.22 (2026-05-16) - Three behavior fixes:
 *                         (a) No more emergency-escape just because the
 *                             teammate isn't visible. Teammates can walk
 *                             out of render or be obscured mid-fight;
 *                             that's not an emergency. Spec dumping
 *                             continues regardless of teammate visibility.
 *                             Emergency triggers reduced to actual danger:
 *                             low HP, no food, no prayer.
 *                         (b) withdrawFood now skips a food type if the
 *                             inventory already has the target count.
 *                             Pre-1.9.22 we always tried to withdraw at
 *                             least the target amount, even when the
 *                             deposit step had left full counts behind —
 *                             "cannot fit" errors at the bank.
 *                         (c) handleTravelingToCorp closes the bank if
 *                             still open before clicking the Games
 *                             necklace. Bank intercepts inventory
 *                             item-clicks while open, turning a
 *                             "Corporeal Beast" tele into a quantity
 *                             prompt — the bot would silently fail to
 *                             tele and get stuck at Ferox.
 *   1.9.21 (2026-05-16) - CRITICAL safety fix + walk-around-Corp fix.
 *                         (a) Hard gate against typing into public chat.
 *                             Production log showed the bot typing
 *                             "TimeToAFK" into PUBLIC CHAT after a state
 *                             transition closed the friend-house dialog
 *                             mid-sequence. Chatbox.isOpen() returns
 *                             true for any chat window incl. public chat
 *                             input, so the old gate didn't catch it.
 *                             Now we require widget [162, 44] (the
 *                             friend-house input field) BEFORE typing
 *                             AND re-verify it BETWEEN typing and Enter.
 *                             If either check fails, abort without typing.
 *                         (b) Multi-step walk when straight line from
 *                             player to chosen Corp position crosses
 *                             Corp's hitbox. lineCrossesCorp samples
 *                             points along the line; if any falls in
 *                             corpArea, walk first to a waypoint on the
 *                             player's side (5 tiles out from Corp
 *                             center along the dominant approach axis),
 *                             THEN to the target. Pre-1.9.21 RuneScape's
 *                             A-star / L-shape pathing took the bot
 *                             straight under Corp and we took stomp damage.
 *   1.9.20 (2026-05-16) - Two fixes:
 *                         (a) Friend-widget shortcut now targets the exact
 *                             path [162, 39, 0] (first child of [162, 39]
 *                             where the host name text lives — empty
 *                             parent matched but clicking it ground-clicked).
 *                             Filter requires path length 3 AND
 *                             path[1]==39 AND path[2]==0.
 *                         (b) Corp position picker: "same-side" bonus
 *                             (+5) for tiles on the player's side of
 *                             Corp's center. Pre-1.9.20 the picker only
 *                             optimized self-distance, which could pick
 *                             a tile that's close in Euclidean terms but
 *                             on the OPPOSITE side of Corp — walker then
 *                             routes through Corp's hitbox. Same-side
 *                             bonus pulls the chosen tile toward the
 *                             approach side. Caveat: if all safe tiles
 *                             ARE on the far side, the walker can still
 *                             cross Corp; would need a multi-step walk
 *                             to fully fix.
 *   1.9.19 (2026-05-16) - Earlier widget targeting attempt at [162, 39]
 *                         (parent box). Diagnostic logging showed parent
 *                         text was empty; superseded by 1.9.20 which
 *                         targets the [..., 0] child instead.
 *   1.9.18 (2026-05-16) - Fix vengeance trigger logic. 1.9.17 made
 *                         handleReadyForFirstCast a no-op and relied on
 *                         ACTIVE_CASTING (which fires after damage taken).
 *                         But ACTIVE_CASTING triggers on ANY damage,
 *                         including the damage we take during spec dump
 *                         — so veng would still fire mid-spec-dump and
 *                         the heal would be wasted before the kill.
 *                         User clarified the real intent: cast vengeance
 *                         only during the KILL PHASE — defined as
 *                         (teamPhaseNeeded() == 0) OR (Corp HP <
 *                         corpMinHpForSpec). New isInKillPhase() helper;
 *                         handleVengeanceLogic returns early if not in
 *                         kill phase. handleReadyForFirstCast restored
 *                         to its original logic — gate is now the
 *                         top-level kill-phase check.
 *   1.9.17 (2026-05-16) - Three fixes after the 1.9.16 test:
 *                         (a) Phase rotation timing. The in-line detector
 *                             was calling refreshSpecWeaponForPhase BEFORE
 *                             recordSpecUsed (deferred to next tick via
 *                             pending XP check). Rotation saw the OLD
 *                             phase count and missed the 4th-spec trigger.
 *                             Moved rotation into processPendingSpecHit,
 *                             AFTER recordSpecUsed runs. Also now equips
 *                             the new weapon immediately on rotation so
 *                             the next bar fires with the right weapon
 *                             instead of waiting for handleSpecialAttack
 *                             (which may not run if bot's stuck eating).
 *                         (b) Vengeance no-op during pre-engagement and
 *                             spec-dump phases. User: "we still veng and
 *                             do things we dont want to do until we
 *                             actually start killing it outside of spec
 *                             dumping." handleReadyForFirstCast now
 *                             returns immediately; vengeance only fires
 *                             during ACTIVE_CASTING (after we've taken
 *                             damage in real melee), which is what
 *                             updateHealthTracking switches us to once
 *                             HP drops.
 *                         (c) Friend-widget shortcut now targets the
 *                             user's exact path [162, 39] (parent
 *                             widget), not just any widget with text +
 *                             actions. Verifies host text in the widget
 *                             or any child.
 *   1.9.16 (2026-05-16) - Two flow fixes after the 1.9.15 test:
 *                         (a) Prep now runs IN PARALLEL with the walk to
 *                             the boss room. Pre-1.9.16 handleEnteringCombat
 *                             ran all prep (pot drink, slot eat, equip,
 *                             pre-activate spec, veng cast) FIRST, then
 *                             called moveToCorpBossRoom. User saw 11s of
 *                             prep in the lobby before any walking
 *                             started. OSRS walks are server-side — once
 *                             we click the passage, the player keeps
 *                             moving while we open inventory and click
 *                             items. Now: kick off the walk first
 *                             (fire-and-forget), then run prep clicks
 *                             concurrently, finally wait for arrival.
 *                         (b) Friend-widget shortcut now requires an
 *                             action, not just text containing the host
 *                             name. Pre-1.9.16 the filter matched the
 *                             static text-label widget which has no
 *                             actions; widget.click() on it fell through
 *                             to a "click ground" and the bot walked
 *                             off into the distance instead of teleing
 *                             to the friend's house.
 *   1.9.15 (2026-05-16) - Flow restructuring after the user pointed out
 *                         several order-of-operations issues:
 *                         (a) Ferox banking now uses GlobalWalking.walkToBank
 *                             instead of LocalWalking-walking to a fixed
 *                             tile. Ring-of-Dueling teleport lands the
 *                             player outside the bank's render range, so
 *                             LocalWalking can't path. The bot was stuck
 *                             "looking for restoration pool" forever.
 *                         (b) Removed vengeance casting and spec prep from
 *                             handleWaitingForTeam. Waiting for teammates
 *                             in the lobby should be a true idle — no
 *                             clicks, no casts. Veng gets healed off before
 *                             we engage anyway, and lobby-prep was firing
 *                             the failing Ice-Plateau-instead-of-Vengeance
 *                             cast.
 *                         (c) Both moved into handleEnteringCombat which
 *                             runs WHILE we walk into the boss room.
 *                             Prayer + spec-button + veng all activate as
 *                             instant clicks during the walk, so by the
 *                             time we see Corp everything is queued.
 *                         (d) Friend-portal widget shortcut now searches
 *                             root 162 by name instead of by hardcoded
 *                             path [162, 39, 0] — same fragility we hit
 *                             with the Vengeance widget at [218, 142].
 *                             Path-agnostic match should resolve "Last
 *                             name: TimeToAFK" reliably.
 *   1.9.14 (2026-05-16) - Three fixes:
 *                         (a) Vengeance now identified by NAME, not by
 *                             fixed widget path. Pre-1.9.14 we trusted
 *                             indexPath[1] == 142 to be Vengeance Self,
 *                             but the spellbook widget tree shifts between
 *                             game versions/pages; on this user's client
 *                             [218, 142] is Ice Plateau Teleport. Bot was
 *                             teleporting to Ice Plateau instead of
 *                             casting Vengeance. Now samples getName /
 *                             getComponentName / getText and requires
 *                             "vengeance" in at least one of them, while
 *                             still excluding "other".
 *                         (b) 700±200ms settle delay before clicking the
 *                             pool. Just-arrived players sometimes see the
 *                             pool object on the next render tick after
 *                             the player tile updates; clicking too early
 *                             can miss the object and hit empty ground
 *                             (walk-here).
 *                         (c) Same settle delay before clicking the Ferox
 *                             bank chest (both in the regular banking
 *                             path and in death-recovery WITHDRAW step).
 *   1.9.13 (2026-05-16) - Four fixes after the 1.9.12 test:
 *                         (a) Pool drink: left-click instead of right-click
 *                             menu. interact("Drink") opens the right-click
 *                             menu and selects "Drink"; if the menu rendering
 *                             races against the click we could end up
 *                             clicking the wrong option. The pool's default
 *                             left-click IS "Drink" — pool.click() is simpler
 *                             and less prone to misclicks.
 *                         (b) hasAcceptableTeammatesInBossRoom required the
 *                             bot to BE in the boss room before it would
 *                             check for teammates there. Result: bot tele'd
 *                             back from POH into the lobby, saw "no teammates
 *                             in boss room" (because itself wasn't there),
 *                             waited in lobby forever while teammates were
 *                             already fighting Corp. Now checks each
 *                             player's tile against the corpCave area
 *                             regardless of where the bot itself is.
 *                         (c) Removed Pool object name and Jewellery box
 *                             name GUI fields. Detection is action-based
 *                             ("Drink" / "Corporeal Beast") and works
 *                             across all tiers — name was unused. Fields
 *                             kept @Deprecated in settings for backward
 *                             compatibility with old saved profiles.
 *                         (d) W330 VALIDATE_POOL and TELE_TO_CORP cases
 *                             also switched to action-based detection.
 *                             isInOwnHouse() too.
 *   1.9.12 (2026-05-16) - After tele back from POH, ALWAYS go to
 *                         WAITING_FOR_TEAM. Pre-1.9.12 handleTeleportingBackToCorp
 *                         would loop straight back into PREPARING_RESTORATION_CYCLE
 *                         if more cycles were available — but we just
 *                         refilled spec and should USE it on Corp first.
 *                         Production log: bot landed in Corp's lobby with
 *                         spec=100%, immediately ran "Using initial special
 *                         attacks (0/4)" — Corp isn't visible from the
 *                         lobby tile, state errored with "Corp not found",
 *                         fell through to emergency Ferox tele. The
 *                         mid-fight restoration trigger fires the NEXT
 *                         POH cycle naturally once the spec bar drains
 *                         again, so always-WAITING_FOR_TEAM is correct.
 *   1.9.11.1 (2026-05-16) - Fix 1.9.11 compile error: Mouse.getPosition() and
 *                           InventoryItem.getStackRectangle() aren't exposed
 *                           in the public TRiBot SDK. Replaced "closest to
 *                           mouse" with "closest by slot index" — InventoryItem
 *                           DOES expose getIndex(), and the inventory is a
 *                           4-wide grid so slot proximity correlates with
 *                           on-screen distance. Captures the shark's slot
 *                           index before clicking, then picks the karambwan
 *                           with the closest abs(index - sharkSlot).
 *   1.9.11 (2026-05-16) - Two fixes after diagnosing the 1.9.10 typing bug:
 *                         (a) Friend-name typing dropped the first 4
 *                             characters. The chatbox dialog opens visually
 *                             but the input field isn't always focused for
 *                             keyboard input in the same tick. Production
 *                             evidence: bot typed "TimeToAFK" but the
 *                             remembered last-typed buffer was "ToAfK"
 *                             (first 4 chars eaten by focus transition).
 *                             Added a 400-700ms settle wait between
 *                             dialog-open and typing.
 *                         (b) Combo-eat picks the karambwan slot CLOSEST
 *                             to the current mouse position. After the
 *                             shark click the cursor sits on the shark
 *                             slot; the nearest karambwan minimizes
 *                             mouse-travel time so both eats land in the
 *                             same game tick. Pre-1.9.11 took the first
 *                             karambwan returned by Query.inventory which
 *                             is typically the top-left slot — often far
 *                             from the shark we just clicked.
 *   1.9.10 (2026-05-16) - Two fixes after the 1.9.9 test:
 *                         (a) Restoration failure fallback. When house entry
 *                             fails MAX_HOUSE_ENTRY_ATTEMPTS times (e.g. the
 *                             POH host is offline), the bot was left
 *                             standing at the friend's-house portal area
 *                             and transitioning to WAITING_FOR_TEAM — which
 *                             expects to be in Corp's lobby, not outside
 *                             the cave. Bot waited forever for teammates.
 *                             Now falls back to a Games-necklace tele back
 *                             to Corp; if no necklace, banks.
 *                         (b) Combo-eat timing tightened (100-300ms → 40-80ms).
 *                             Karambwan in OSRS bypasses the standard eat
 *                             cooldown so both heals can land in the SAME
 *                             game tick — but only if the karambwan click
 *                             is fast. Pre-1.9.10 wait was long enough to
 *                             push karambwan into the next tick, halving
 *                             the burst-heal speed. Combat-log evidence:
 *                             bot was emergency-combo-eating twice between
 *                             specs because single-tick burst wasn't
 *                             outpacing Corp damage.
 *   1.9.9 (2026-05-16) - XP-based spec hit detection. Pre-1.9.9 every spec
 *                        fire (hit OR miss) bumped the phase counter, so
 *                        the bot would rotate weapons after 4 spec ATTEMPTS
 *                        even if half missed. Now we snapshot melee XP
 *                        (Attack + Strength + Defence + Hitpoints, NOT
 *                        Magic — Magic XP is vengeance) at every spec
 *                        activation. After the spec fires (energy drop),
 *                        we wait one tick for XP to register and check the
 *                        delta:
 *                          XP increased → spec HIT → recordSpecUsed
 *                          XP unchanged after 2s → spec MISS → don't record
 *                        Real-teammate multiplier is unchanged: each
 *                        confirmed hit still counts × (1 + realTeammates)
 *                        in the team aggregate per the user's spec.
 *   1.9.8 (2026-05-16) - Three follow-up fixes after the 1.9.7.1 success:
 *                        (a) Defer phase-rotation equip. Rotating Elder maul
 *                            → Arclight always happens at energy 0 (just spent
 *                            the last spec of the bar), so equipping Arclight
 *                            this tick is wasted — we can't fire on it
 *                            anyway. Pre-1.9.8 the equip ran in the boss room
 *                            (2 seconds of swap animation while Corp keeps
 *                            hitting). Now we update chosenSpecWeapon but
 *                            defer the equip until the next bar (handleSpecialAttack
 *                            already equips the chosen weapon if it isn't
 *                            on). Saves the in-room exposure.
 *                        (b) Pool restoration actually verifies. Pre-1.9.8
 *                            useOrnatePool said "Pool restoration timed out,
 *                            but continuing" — bot teleported back with 0%
 *                            spec and low HP and died on Corp. Now retries
 *                            up to 3 drinks, then refuses to tele if stats
 *                            didn't actually restore.
 *                        (c) Two more coordinator-gated refreshSpecWeaponForPhase
 *                            calls (handleSpecialAttack + handleUsingInitialSpecs).
 *                            Both gates dropped — phase rotation works for
 *                            solo via buildSoloAggregate. handleUsingInitialSpecs
 *                            also no longer calls equipMainWeaponFast before
 *                            tele (mirrors the 1.9.7 fix elsewhere).
 *   1.9.7.1 (2026-05-16) - Hotfix follow-up to 1.9.7:
 *                          (a) Pool/jewellery-box matching is now action-based,
 *                              not name-based. The actual object names are
 *                              "Ornate pool of Rejuvenation" (different word
 *                              order from 1.9.7's "rejuvenation pool" nameContains)
 *                              and "Ornate Jewellery Box" (case-mismatched with
 *                              nameEquals("Ornate jewellery box")). Both
 *                              expose stable actions ("Drink" / "Corporeal Beast")
 *                              that work across all tiers.
 *                          (b) Two more portal double-click bugs of the same
 *                              shape as enterFriendHouse: isAtHousePortal and
 *                              the W330 path both had .filter(p -> p.interact(...))
 *                              which clicks as a side-effect of filtering.
 *                              Fixed both with .filter(p -> p.getActions()...)
 *                          (c) Another instant-true Waiting.waitUntil(1000, () -> true)
 *                              in the Ferox-pool retry path — replaced with a
 *                              real waitNormal settle delay.
 *   1.9.7 (2026-05-16) - Four fixes after the 1.9.6 test:
 *                        (a) enterFriendHouse double-clicked the portal. The
 *                            filter lambda called portal.interact(...) which
 *                            actually clicks as a side-effect of filtering,
 *                            and then the code called interact() again outside
 *                            the filter. Use .getActions().contains() for
 *                            filtering, interact exactly once.
 *                        (b) handleFriendNameDialog typed the host name then
 *                            pressed Enter inside the same tick — the typing-
 *                            then-wait used Waiting.waitUntil(2000, () -> true)
 *                            which returns instantly. Replaced with a real
 *                            900±200ms wait so the game-side typing buffer
 *                            actually registers before Enter.
 *                        (c) useOrnatePool() required an exact match against
 *                            settings.poolName ("Ornate rejuvenation pool"
 *                            default). Friend's house has a different pool
 *                            tier → "pool not found" → emergency Ferox tele
 *                            even though we'd successfully entered the
 *                            house. Now uses the same broad nameContains
 *                            check as isInFriendHouse — any rejuvenation /
 *                            restoration / revitalisation pool works.
 *                        (d) handleUsingInitialSpecs called equipMainWeaponFast
 *                            ("Switching back to main weapon before house
 *                            teleport") right before tele. The spec weapon
 *                            should stay equipped through the POH cycle.
 *                            User: "we want to spec twice and tele out and
 *                            that's essentially it. Anything else is wasted
 *                            input." Removed the swap; Fang swap reserved
 *                            for the kill-phase transition only.
 *                        Plus log spam fix: handleEnteringFriendHouse no
 *                        longer logs "Attempting to enter..." every 50ms
 *                        tick while throttled — only on the actual attempt.
 *   1.9.6 (2026-05-16) - Three fixes after the user's 1.9.5 test:
 *                        (a) In-line spec detector swapped to Fang when
 *                            shouldStartRestorationCycle returned false at
 *                            the bar boundary, but the same check passed
 *                            6 seconds later in the mid-fight handler —
 *                            bot wasted a Fang-swap that immediately got
 *                            un-done by the POH tele. Dropped the Corp-
 *                            HP-above-floor gate from shouldStartRestorationCycle.
 *                            The HP gate is for SPEC-FIRE decisions (don't
 *                            waste a spec on a near-dead Corp); the tele
 *                            decision should be "always tele if we want
 *                            more specs". After POH, if Corp HP is below
 *                            floor we fall through to kill-phase melee via
 *                            existing spec-fire gates.
 *                        (b) isInFriendHouse() only checked for one
 *                            specific pool object. The friend's house has
 *                            both a portal (also in entry lobby) and POH
 *                            furniture; if the pool wasn't loaded in render
 *                            yet OR was a different tier, the bot would
 *                            re-click the portal endlessly. Now matches
 *                            any pool / jewellery box / spirit tree.
 *                        (c) Per-kill reset (in handleLooting) now also
 *                            clears the queued spec-weapon switch-back —
 *                            previously a queue scheduled mid-bar could
 *                            survive into the next kill's prep and fire at
 *                            the wrong moment.
 *   1.9.5 (2026-05-16) - Phase rotation now happens mid-bar. The full Corp
 *                        meta is Phase 1 → Phase 2 → Phase 3:
 *                          Phase 1 (defense reducers): 4 Elder maul / DWH
 *                          Phase 2 (combat-level reducers): 20 Arclight /
 *                                                            Darklight /
 *                                                            Emberlight
 *                          Phase 3 (HP drain): 200 BGS damage
 *                        Pre-1.9.5 the in-line spec detector never called
 *                        refreshSpecWeaponForPhase(), so the bot stayed on
 *                        Elder maul forever even after the 4 phase-1 specs
 *                        landed. Also refreshSpecWeaponForPhase itself was
 *                        gated on settings.coordinatorEnabled, so solo bots
 *                        wouldn't rotate even if the call was added.
 *                        Fixes: dropped the coordinator gate (rotation works
 *                        for solo via buildSoloAggregate); the detector now
 *                        calls refreshSpecWeaponForPhase after every
 *                        recordSpecUsed and equips the new weapon if the
 *                        choice changed.
 *   1.9.4 (2026-05-16) - Strategy + survival fixes after a death log.
 *                        Insight: during spec-dump phase the spec weapon
 *                        IS the main weapon. Fang only comes out at the
 *                        kill-phase transition. Pre-1.9.4 the bot swapped
 *                        to Fang after every spec bar — extra weapon-swap
 *                        animation lock during which Corp kept hitting.
 *                        (a) In-line spec detector: on bar exhaust, route
 *                            INSTA to PREPARING_RESTORATION_CYCLE when
 *                            shouldStartRestorationCycle says we still
 *                            want more specs. Fang swap only fires when
 *                            restoration is no longer wanted (kill phase).
 *                            The spec weapon stays equipped through the
 *                            POH cycle and into the next bar.
 *                        (b) Removed isOnLunarSpellbook() probe gate from
 *                            handleVengeanceLogic. The probe returned false
 *                            positives on the user's client even after 1.9.3
 *                            text-filter relaxation, locking out vengeance
 *                            for the whole session. castVengeance now just
 *                            attempts the cast; widget-not-found logs
 *                            sufficient diagnosis if not on Lunars.
 *                        (c) New INTERNAL_PANIC_TELE_HP = 8. At HP <= 8
 *                            (one Corp hit from death) we skip eating and
 *                            bail straight to EMERGENCY_ESCAPE — Ferox
 *                            tele / Games necklace / run / logout.
 *                        (d) Emergency HP check: if emergencyComboEat
 *                            returns false (no food available), tele out
 *                            via EMERGENCY_ESCAPE instead of standing and
 *                            dying. Also dropped the spec-cancel mouse
 *                            click during the emergency tick — wasted
 *                            motion when we should be eating/teling.
 *                        (e) HP guard on handleSpecWeaponSwitchTiming:
 *                            postpone the Fang-swap animation if HP <=
 *                            INTERNAL_COMBO_EAT_HP. Production log
 *                            showed the bot dying mid-swap because
 *                            equipMainWeaponFast blocks the thread.
 *   1.9.3 (2026-05-16) - Fix vengeance not casting. The isVengeanceSelfWidget
 *                        filter (added in 1.7.4 to guard against Vengeance
 *                        Other) required widget text to contain "vengeance"
 *                        — but Lunar spellbook widgets are sprite-based and
 *                        may return empty text via getText().orElse(""). The
 *                        path filter (root 218, child 142) already uniquely
 *                        identifies Vengeance Self; the text-contains check
 *                        rejected the real widget when text was empty,
 *                        causing isOnLunarSpellbook() to return false and
 *                        the entire vengeance system to silently skip every
 *                        cast for the session. Dropped the text-contains-
 *                        vengeance requirement; kept the "other" guard.
 *   1.9.2 (2026-05-16) - Fix spec-button spam-click. The in-line spec detector
 *                        triggered on "energy < 100" which is true continuously
 *                        after spec #1 — every loop iteration fired the
 *                        detector, called recordSpecUsed(), and toggled the
 *                        spec button. Result: 30+ "Pre-activated spec fired"
 *                        logs per kill while the energy bar stayed stuck at
 *                        50%, only 2 real specs fired but phase counter
 *                        inflated to 30+ (which then wrongly satisfied
 *                        teamPhaseNeeded() and blocked the POH restoration
 *                        tele). Now the detector triggers on real energy
 *                        DROPS via a new lastSeenSpecEnergy field. The field
 *                        is updated after every Combat.activateSpecialAttack
 *                        success (lobby prep, in-room prep, mid-fight pre-
 *                        activate, mid-bar re-activate) so each "fire"
 *                        corresponds to actual energy consumption.
 *   1.9.1 (2026-05-16) - Fix mid-bar spec bail. 1.9.0 added multi-spec-per-bar
 *                        but the canFireAnotherSpecOnThisBar gate also
 *                        checked Corp HP floor + phase targets. In a real
 *                        team kill, Corp's HP can drop below 1700 between
 *                        the bot's first spec firing and the gate's
 *                        post-spec evaluation — wrongly cancelling spec #2
 *                        on a bar already committed to. Those gates belong
 *                        at the BAR boundary (shouldStartRestorationCycle
 *                        before the next bar), not mid-bar. Once we commit
 *                        to a bar, drain it. canFireAnotherSpec now only
 *                        checks energy + Corp alive, and logs the result
 *                        so we can diagnose if it ever bails unexpectedly.
 *   1.9.0 (2026-05-16) - Three fixes from a 1.8.9 production log:
 *                        (a) Multi-spec per bar. The in-line spec detector
 *                            unconditionally queued switch-back-to-main after
 *                            the first spec. With Elder maul (50%) and 50%
 *                            energy left in the bar, the bot should fire a
 *                            second spec before switching off — but it
 *                            didn't, so 1.8.8's mid-fight restoration trigger
 *                            never saw a depleted bar. Now after each in-line
 *                            spec fire we check canFireAnotherSpecOnThisBar()
 *                            (energy >= cost AND phase targets not met AND
 *                            Corp HP above floor) and re-activate spec
 *                            instead of switching back. Switch-back only
 *                            fires when the bar can't support another spec.
 *                        (b) Lobby pre-spec. handleWaitingForTeam now calls
 *                            prepareSpecWeaponInLobby() before transitioning
 *                            to ENTERING_COMBAT, so the bot drinks super
 *                            combat, eats karambwan for slot, equips spec
 *                            weapon, and pre-activates spec WHILE STILL IN
 *                            THE LOBBY. Pre-1.9.0 the entire prep sequence
 *                            ran under Corp's nose — ~10 seconds of stomp
 *                            damage absorbed while drinking/equipping. The
 *                            in-room prepareSpecWeaponForCorp becomes
 *                            idempotent: already-prepped → no-op.
 *                        (c) Skip prep when HP critical. prepareSpecWeaponForCorp
 *                            now bails to emergencyComboEat if HP <=
 *                            INTERNAL_COMBO_EAT_HP (50). Drinking a potion
 *                            at 50 HP under sustained Corp damage was getting
 *                            the bot killed; it's better to start combat
 *                            un-prepped and let shouldUseSpecialAttack pick
 *                            up the spec later once HP recovers.
 *   1.8.9 (2026-05-16) - Survival fixes after a death log: bot was firing
 *                        pre-activated spec and swapping weapons at single-
 *                        digit HP instead of eating.
 *                        (a) New top-of-handleFightingCorp short-circuit at
 *                            INTERNAL_EMERGENCY_HP: eat (combo if possible),
 *                            cancel pre-activated spec so the next attack
 *                            doesn't waste it, skip swap/vengeance/positioning
 *                            this tick, return. Dark-core check still runs
 *                            (being inside the core is what's killing us;
 *                            dodging out matters more than eating in place).
 *                        (b) New INTERNAL_COMBO_EAT_HP = 50. Pre-1.8.9
 *                            combo-eat only fired at <= 15 HP, which let
 *                            the bot fall into one-shot range before
 *                            reacting. handleHealthAndPrayer now combo-eats
 *                            (38 HP from Shark+Karambwan) whenever HP
 *                            drops below 50, outpacing Corp's damage.
 *   1.8.8 (2026-05-16) - Restoration model rebuilt to match real Corp meta:
 *                        spec → POH → spec → POH until phase targets met or
 *                        Corp HP drops below the floor (a teammate is
 *                        actively damaging it). Pre-1.8.8 the bot would spec
 *                        once per kill and never tele to POH for more.
 *                        Changes:
 *                        (a) Restoration is now PER-KILL, not per-trip.
 *                            resetRestorationTracking() runs at the end of
 *                            handleLooting() so every new kill starts with a
 *                            fresh cycle budget and zero spec counters. Phase
 *                            aggregator counters were already per-kill via
 *                            coordinatorOnKillEnded().
 *                        (b) shouldStartRestorationCycle() gate rewritten:
 *                            triggers when spec is DEPLETED (was inverted —
 *                            pre-1.8.8 required percent >= minSpecEnergy,
 *                            backwards) AND phase targets not met AND Corp
 *                            HP above floor. totalRestorationCycles is now
 *                            just a safety upper bound (default 10), not the
 *                            real loop driver.
 *                        (c) Mid-fight restoration trigger added to
 *                            handleFightingCorp — once spec is dry in
 *                            combat, the bot breaks out to POH instead of
 *                            standing there meleeing with no spec. Loop
 *                            naturally terminates when teamPhaseNeeded()==0
 *                            or Corp HP drops below corpMinHpForSpec (a real
 *                            teammate is killing it — go melee and help).
 *                        (d) corpMinHpForSpec default 600 → 1700. This is
 *                            the restoration-loop termination floor, not a
 *                            per-spec cooldown. Stats stay reduced; only HP
 *                            regens. 1700 ≈ Corp lost ~15% HP → time to
 *                            stop dumping defense-reducers and join melee.
 *                        (e) INTERNAL_SPECS_PER_CYCLE (hardcoded 2) replaced
 *                            with specsPerFullBar() which derives from the
 *                            cheapest owned spec weapon's cost. Arclight
 *                            (25%) now correctly fits 4 specs per cycle
 *                            instead of being clipped at 2.
 *                        (f) assignUniqueCorpPosition() now factors in self-
 *                            distance. Pre-1.8.8 it only weighted "max
 *                            separation from other players", so when no
 *                            teammates were nearby it just picked the first
 *                            safe offset in iteration order (East). With a
 *                            NW approach to Corp, picking East routed the
 *                            player under Corp's hitbox. Now picks the
 *                            closest safe offset on tie.
 *                        (g) Mid-fight repositioning. handleCorpPositioning()
 *                            was a defined-but-never-called method, so the
 *                            bot picked one tile at engage and stayed there
 *                            for the entire kill. When Corp roamed (esp.
 *                            through narrow corners) the player ended up
 *                            inside Corp's 5x5 hitbox taking free stomp
 *                            damage. handleFightingCorp now has two checks
 *                            at the top: (i) emergency reposition if
 *                            corpArea.contains(myTile), (ii) periodic 3s
 *                            reposition if isInGoodCorpPosition returns
 *                            false (drifted from assigned offset).
 *   1.8.7 (2026-05-15) - Spec rotation finally works when joining an in-progress
 *                        teammate kill. Three coupled bugs in the entering-
 *                        combat path:
 *                        (a) handleEnteringCombat "Joining existing combat"
 *                            branch only set state=FIGHTING_CORP without
 *                            ever calling corp.interact("Attack") — the bot
 *                            stood there while the pre-activated spec sat
 *                            queued. The branch is gone; we always attack
 *                            now and just log differently based on whether
 *                            Corp was already in combat.
 *                        (b) When the pre-activated spec eventually fired
 *                            (via the FIGHTING_CORP tail re-engage on the
 *                            next eat), it consumed energy outside the
 *                            USING_SPECIAL_ATTACK state, so queueSpecWeaponSwitchBack
 *                            never ran — bot stayed on Elder maul the rest of
 *                            the kill. New detector at the top of handleFightingCorp
 *                            notices: specWeaponReadyForUse=true +
 *                            !Combat.isSpecialAttackEnabled() + spec weapon
 *                            equipped + spec% < 100 → records the spec for
 *                            team phase tracking and queues switch back to Fang.
 *                        (c) corpMinHpForSpec default 1200 was the historical
 *                            value before 1.7.3 actually wired the gate. With
 *                            the gate enforced, a 1200 floor blocks Phase 2/3
 *                            specs once the team drops Corp past 60%. Lowered
 *                            default to 600 — keeps "don't spec a near-dead
 *                            Corp" intent while letting team rotations finish.
 *   1.8.6 (2026-05-15) - Two more bugs surfaced in production: Ferox-tele loop
 *                        and spec-weapon stuck-on after the first spec.
 *                        - isAtFeroxEnclave: tile-coord check is now primary
 *                          (x:3120-3160, y:3620-3650, plane 0). Old check
 *                          relied on "Ferox" NPC / "Pool of Refreshment" /
 *                          "Bank chest" being in render distance, but the
 *                          Ring of Dueling drop point doesn't see any of
 *                          those until the player walks a few tiles. The
 *                          banking flow's "if (!isAtFeroxEnclave()) tele"
 *                          loop then burned through every ring charge in
 *                          inventory in a few ticks (the bug report said
 *                          "teleported to ferrox using all the rings over
 *                          and over without walking to the bank"). Object
 *                          detection retained as fallback.
 *                        - equipMainWeaponFast: now actually verifies the
 *                          wield. The old `success = true` initial was never
 *                          overwritten — the function always returned true
 *                          even when getAvailableMainWeapon() returned null
 *                          or the click failed silently. handleSpecWeaponSwitchTiming
 *                          cleared the switch-back queue on that bogus
 *                          "success", so a failed Fang re-wield after a spec
 *                          would leave the bot stuck on the spec weapon for
 *                          the rest of the kill. Now returns false on any
 *                          failure path with a specific log line; defender
 *                          wield remains best-effort so accounts that don't
 *                          carry one aren't penalised.
 *   1.8.5 (2026-05-15) - Two combat-loop bug fixes surfaced in production logs.
 *                        - comboEatToFreeSlot / ensureInventorySlotsFree:
 *                          The old "Inventory.isFull is now false" success
 *                          check returned immediately whenever the inventory
 *                          wasn't full to begin with — so we logged "Combo-
 *                          eating Cooked karambwan" but never actually ate
 *                          anything, came back 1 slot short, and bailed with
 *                          "Cannot free 2 slots for 2H swap". Net effect: bot
 *                          never executed its initial Elder maul / DWH spec
 *                          when starting a kill with a near-full inventory.
 *                          Both helpers now compare inventory count before
 *                          and after eating. ensureInventorySlotsFree also
 *                          loops until the target is reached (or food runs
 *                          out, capped at 28 attempts) instead of running
 *                          only `toFree` iterations and stopping.
 *                        - stepAwayFromCore: the 8 candidate offsets were all
 *                          1-tile neighbours, which land inside Corp's 5x5
 *                          hitbox when the bot is adjacent to Corp — every
 *                          tile got rejected and the bot stayed on the core's
 *                          spawn tile while ESC-eating in a loop ("STEP-AWAY:
 *                          no walkable target away from core" repeated in the
 *                          log). Now we try 2/3/4-tile offsets in the away
 *                          direction first, fall back to perpendicular/short
 *                          steps last, and explicitly skip any tile that
 *                          intersects Corp's hitbox (corp.getArea().contains).
 *   1.8.4 (2026-05-15) - Friend-house dialog: corrected widget path for the
 *                        "Last name: <rsn>" shortcut from [162, 38, 0] to
 *                        [162, 39, 0]. Old path silently fell through to the
 *                        typed-name path on every entry — slightly slower but
 *                        worked. Now the one-click shortcut fires when we've
 *                        visited the host before. Text comparison is now
 *                        case-insensitive + color-tag-stripped so "TimeToAFK"
 *                        matches the widget's "timetoafk" text.
 *                        (Reference: typed-name input field is at [162, 44]
 *                        — Keyboard.typeString routes to it via chat focus.)
 *   1.8.3 (2026-05-15) - Friends-house entry timing tightened to match the new
 *                        in-game mechanic. Entering a friend's house no longer
 *                        requires the host to have their house "open" — same-
 *                        world presence is enough. Failure modes are now real
 *                        (typo, offline, wrong world) rather than timing, so
 *                        retries should fail fast:
 *                            MAX_HOUSE_ENTRY_ATTEMPTS: 5 -> 3
 *                            HOUSE_ENTRY_RETRY_DELAY:  5000-7000ms -> 1500-3000ms
 *                        Worst-case retry window drops from ~30s to ~9s.
 *                        Operationally: bot teammates / friend partners no
 *                        longer need to coordinate "open my house" calls.
 *   1.8.2 (2026-05-15) - Bug fix bundle: jewelry charge floor + hard-stop.
 *                        - Charged-jewelry top-up: hasChargedRingOfDueling /
 *                          hasChargedGamesNecklace returned true even for a
 *                          (1)-charge variant, so the bot would leave the bank
 *                          with a 1-charge ring, burn it on the next Ferox
 *                          trip, and strand itself (the log showed exactly this
 *                          chain ending in "No Ring of Dueling found"). New
 *                          ringOfDuelingNeedsTopUp / gamesNecklaceNeedsTopUp
 *                          checks return true when the highest dose in
 *                          inventory is below JEWELRY_TOP_UP_THRESHOLD (4).
 *                          withdrawEssentialItems now uses these — a low-dose
 *                          ring/necklace triggers a fresh (8) withdraw.
 *                        - Hard-stop on supply exhaustion: the "STOPPING SCRIPT"
 *                          path used to call Login.logout() and return without
 *                          flipping the `running` flag. The main while-loop
 *                          would continue and the bot would try to bank again,
 *                          fail again, and so on. Now we signalSessionEnd()
 *                          to notify coordinator-aware teammates, set
 *                          running=false, then logout. Clean exit.
 *   1.8.1 (2026-05-15) - Real-teammate awareness for mixed bot + human teams.
 *                        - teamPhaseNeeded() now works with coordinator off:
 *                          buildSoloAggregate() constructs an aggregate from
 *                          mySnapshot when no coordinator file exists, so the
 *                          bot's own per-kill spec counts feed back into the
 *                          phase logic instead of being ignored.
 *                        - Real-teammate boost: getRealTeammateRSNs() derives
 *                          "human partner" RSNs as acceptableTeammates MINUS
 *                          botTeammates MINUS self. countRealTeammatesNearby()
 *                          checks which of those are visible. teamPhaseNeeded
 *                          then multiplies phase1Specs / phase2Specs /
 *                          phase3BgsDamage by (1 + nearbyHumans) so a 1 bot +
 *                          1 human pair stops getting stuck on Phase 1 forever.
 *                        - No new GUI fields. The derivation means users who
 *                          want a bot teammate to count as a bot put it in
 *                          BOTH acceptableTeammates AND botTeammates; humans
 *                          go only in acceptableTeammates.
 *                        - Approximation caveat: the multiplier assumes each
 *                          human contributes proportionally to bot output.
 *                          Fine for partners doing similar specs; off for
 *                          partners using a wildly different rotation. Users
 *                          who want precise control can leave the human off
 *                          acceptableTeammates so no boost is applied.
 *   1.8.0 (2026-05-15) - Big customization sweep. Strips out fake-customization
 *                        settings and replaces hand-maintained checkboxes with
 *                        auto-detection. Only the truly user-facing levers
 *                        remain in the GUI.
 *                        - Spec-weapon auto-detection: getOwnedSpecWeapons()
 *                          scans Equipment + Inventory + Bank (when open) for
 *                          every name in ALL_SPEC_WEAPONS and caches the
 *                          result. Cache is invalidated after each bank trip.
 *                          Replaces the Per-account tab's checkbox map; all
 *                          consumers (shouldKeepItem, pickSpecWeaponForCurrentPhase,
 *                          detectAndSetSpecWeapon, mySnapshot.availableWeapons)
 *                          now read from the detected list.
 *                        - Mode-aware spec budget: getTripSpecBudget() returns
 *                          per-trip spec capacity. FEROX_ONLY = current bar +
 *                          1 (regen approximation); POH modes = current +
 *                          cyclesRemaining * fullBar. shouldDumpSpecsAggressively()
 *                          is true in FEROX_ONLY and flips shouldSpecNowConsideringTeam
 *                          into "every spec is worthwhile DPS" mode when team
 *                          phases are done or our weapon doesn't match the
 *                          current phase.
 *                        - getMinSpecEnergy() now derives from the cheapest
 *                          owned spec weapon's cost instead of a user spinner.
 *                        - Moved to internal constants (no longer user-tweakable):
 *                            INTERNAL_PHASE1_TARGET (4), INTERNAL_PHASE2_TARGET (20),
 *                            INTERNAL_PHASE3_BGS_DAMAGE (200), INTERNAL_EAT_BELOW_MAX_HP (21),
 *                            INTERNAL_EMERGENCY_HP (15), INTERNAL_DRINK_PRAYER_THRESHOLD (20),
 *                            INTERNAL_CORP_LOW_HP_VENG_STOP (85),
 *                            INTERNAL_COORD_WRITE_INTERVAL_TICKS (5),
 *                            INTERNAL_COORD_STALE_THRESHOLD_MS (10000),
 *                            INTERNAL_SPECS_PER_CYCLE (2), INTERNAL_TARGET_SHARKS (10),
 *                            INTERNAL_TARGET_KARAMBWANS (9), INTERNAL_TARGET_SUPER_RESTORES (2),
 *                            INTERNAL_TARGET_SUPER_COMBAT (1), INTERNAL_MIN_FOOD_COUNT (10),
 *                            INTERNAL_MIN_PRAYER_DOSES (4). These are derived from
 *                            Corp game mechanics, not user preference.
 *                        - accountRole is now forced to "auto" — the role
 *                          dropdown was non-functional anyway. Existing roles
 *                          in saved profiles are ignored.
 *                        - GUI trimmed to four tabs and the truly user-facing
 *                          levers only:
 *                            Combat:     mainWeapon, food1, food2, useVengeance,
 *                                        combatPotion, showOverlay
 *                            Spec:       corpMinHpForSpec, totalRestorationCycles,
 *                                        useLegacyDarkCoreLogic
 *                            POH / Team: pohSource, friendName, isPohHost, poolName,
 *                                        jewelleryBoxName, coordinatorEnabled,
 *                                        waitForTeammateSpec, designatedWorld,
 *                                        w330MaxHostAttempts, acceptableTeammates,
 *                                        botTeammates
 *                            Loot:       valuableLoot
 *                        - Removed tabs: Inventory targets, Per-account.
 *                          Removed controls: minSpec spinner, specsPerCycle,
 *                          phase target spinners, HP threshold spinners, low-HP
 *                          veng-stop spinner, role dropdown, spec-weapon
 *                          checkboxes.
 *                        - CorpSettings keeps the deprecated fields
 *                          (availableSpecWeapons, accountRole, eatBelowMaxHp,
 *                          etc.) for back-compat profile loading — Gson tolerates
 *                          extra fields. They're never read by runtime code now.
 *   1.7.4 (2026-05-15) - Vengeance robustness: widget guard + rune pouch gate.
 *                        - isVengeanceSelfWidget() filter requires the widget's
 *                          display text to contain "Vengeance" but NOT "Other",
 *                          plus the "Cast" action. Applied to both the cast
 *                          path and the spellbook probe so a stray Vengeance
 *                          Other widget can't satisfy either.
 *                        - hasVengeanceRunes() gates handleVengeanceLogic on
 *                          Rune pouch / Divine rune pouch / all loose runes
 *                          (Astral + Death + Earth) being present. One-shot
 *                          warning if missing; auto-recovers if the pouch is
 *                          re-acquired mid-session via a bank trip.
 *   1.7.3 (2026-05-15) - isCorpHealthAboveSpecThreshold now actually honors
 *                        settings.corpMinHpForSpec. Original implementation just
 *                        returned isHealthBarVisible() — the GUI setting was dead.
 *                        Maps Corp's visible health-bar % to absolute HP (Corp
 *                        has 2000 max) and compares against the setting. Removes
 *                        the long-standing OPEN-block note about the 1200/1700
 *                        value/comment mismatch.
 *   1.7.2 (2026-05-15) - Robustness pass: spellbook gate, inventory-full handling,
 *                        status overlay.
 *                        - Spellbook check: handleVengeanceLogic now probes for
 *                          the Vengeance widget at [218, 142] before casting.
 *                          If the player isn't on Lunars the cast widget won't
 *                          exist; we cache the result, log a one-shot warning,
 *                          and skip vengeance for the rest of the session
 *                          instead of spamming silent failures.
 *                        - Inventory-full handling: new ensureInventorySlotsFree(n)
 *                          helper combo-eats Cooked karambwan (falls back to a
 *                          primary food) until n slots are free.
 *                            * equipSpecWeapon now calls it before wielding a
 *                              2H spec weapon (Elder maul / DWH / BGS / Crystal
 *                              halberd / Dragon halberd). Stops the silent
 *                              "wield failed because inventory is full" case
 *                              where the defender + previous main weapon both
 *                              need to come back to inventory.
 *                            * handleLooting calls it on Inventory.isFull() so
 *                              a Corp drop landing during a full inventory still
 *                              gets picked up.
 *                        - Status overlay: small always-on-top Swing window
 *                          (settings.showOverlay) showing state / spec weapon /
 *                          kills / deaths / runtime / coordinator status / team
 *                          phase needed / session-end pending flag. killCount
 *                          increments in coordinatorOnKillEnded(); deathCount
 *                          increments at DeathRecovery DONE. Toggled via the
 *                          Combat tab's "Show live status overlay window".
 *   1.7.1 (2026-05-15) - Session-end signaling on supply exhaustion.
 *                        - AccountSnapshot gains sessionEndRequested + sessionEndReason
 *                          so a bot that can't recover can broadcast through the
 *                          coordinator that the whole team should wrap up.
 *                        - Death recovery now checks bankHasGamesNecklace() before
 *                          the withdraw attempt. If no necklaces of any dose are
 *                          present, the bot calls signalSessionEnd("Out of Games
 *                          necklaces ..."), force-publishes the snapshot, and
 *                          transitions to EMERGENCY_ESCAPE for a clean logout
 *                          instead of looping forever between WITHDRAW and
 *                          TELE_TO_CORP.
 *                        - Pre-dispatch in executeCurrentState: when any live
 *                          teammate's snapshot has sessionEndRequested=true,
 *                          set local sessionEndPending. handleLooting checks
 *                          this flag and routes the bot to EMERGENCY_ESCAPE
 *                          after the current kill rather than starting a new
 *                          one. Teammate filter (botTeammates) is honored so
 *                          unrelated players in the same coordinator file
 *                          don't trigger a cascade.
 *   1.7.0 (2026-05-15) - W330 random POH mode (Phase I, complete).
 *                        - pohSource=W330_RANDOM now operational. The bot:
 *                          captures the current world, hops to W330, uses the
 *                          standard "Teleport to house" tab "Outside" option
 *                          (the bot's own POH is set to Rimmington as part of
 *                          its gear setup, so this lands at the Rimmington
 *                          portal — no walking), picks a random nearby player
 *                          as a host candidate, enters their house via the
 *                          friend's-house portal dialog, validates that
 *                          settings.poolName is present, uses the pool, then
 *                          teleports back to Corp (host's jewellery box
 *                          preferred, Games necklace fallback) and hops back
 *                          to the captured world.
 *                        - New BotState.W330_RESTORATION with an inner FSM:
 *                          CAPTURE_HOME -> HOP_TO_W330 -> TELE_TO_HOUSE_OUTSIDE
 *                          -> ENTER_HOUSE -> VALIDATE_POOL -> USE_POOL ->
 *                          TELE_TO_CORP -> HOP_HOME -> DONE.
 *                        - Bad-host retry: settings.w330MaxHostAttempts (default
 *                          3) caps how many random advertisers to try in a
 *                          cycle. After the cap we bail and resume Corp DPS
 *                          without restoration (we'll try again next cycle).
 *                        - settings.designatedWorld: explicit return world.
 *                          0 means "remember whichever world we were on when
 *                          restoration started" — works out of the box for
 *                          most setups.
 *                        - detectDeath() ignores W330_RESTORATION so death
 *                          recovery doesn't trigger while we're hopping worlds.
 *                        - GUI: POH/Team tab gains "W330 return world" and
 *                          "W330 max host tries" spinners.
 *   1.6.0 (2026-05-15) - POH-less modes (Phase I, partial).
 *                        - New settings.pohSource enum-as-string with five values:
 *                            * OWN_HOUSE     - this account's own ornate pool.
 *                            * FRIEND_HOUSE  - friend's house by manual RSN.
 *                            * BOT_HOST      - resolve teammate-bot host via
 *                                              coordinator (publishes isPohHost flag).
 *                            * W330_RANDOM   - reserved for 1.7.0 (random POH from
 *                                              the public-house world, Rimmington
 *                                              portal entry).
 *                            * FEROX_ONLY    - skip POH entirely. HP/prayer get
 *                                              restored via Ferox Pool of Refreshment
 *                                              during normal bank trips; spec only
 *                                              refills from natural regen.
 *                        - settings.useOwnHouse (1.5.x) is now legacy; migration
 *                          path in migrateLegacySettings() maps true -> OWN_HOUSE,
 *                          false -> FRIEND_HOUSE so existing profiles keep working.
 *                        - settings.isPohHost: when set on the host bot, other
 *                          teammates configured pohSource=BOT_HOST will discover
 *                          this account's RSN from the coordinator and use it as
 *                          their friend's-house entry name.
 *                        - resolveBotHostName() reads the coordinator's TeamState,
 *                          filters by live + isPohHost + (optional) botTeammates
 *                          allow-list, and returns the host's RSN. Falls back to
 *                          settings.friendName if no host is found.
 *                        - getEffectiveFriendName() now drives enterFriendHouse()
 *                          and handleFriendNameDialog() — bot-host mode types the
 *                          dynamically-resolved RSN into the portal dialog.
 *                        - shouldStartRestorationCycle() short-circuits to false
 *                          for FEROX_ONLY and W330_RANDOM (no crash on unimplemented).
 *                        - GUI: POH/Team tab replaces the useOwnHouse checkbox
 *                          with a pohSource dropdown + a "POH host role" checkbox.
 *   1.5.2 (2026-05-15) - Customization audit + bug fixes for public release.
 *                        - Spec-weapon initial detection rewritten: iterates
 *                          settings.availableSpecWeapons in phase order (1->2->3)
 *                          instead of the hardcoded Elder maul / Darklight check.
 *                          DWH-only and BGS-only setups now work out of the box.
 *                        - hasRequiredItemsWithPOH / hasRequiredItems use
 *                          hasAnyOwnedSpecWeapon() + hasMinimumFood() instead of
 *                          the hardcoded Elder maul + Shark + Karambwan checks.
 *                        - Defender selection: equipAnyDefender now iterates a
 *                          tier priority list (Avernic > Dragon > Rune > Adamant > ...
 *                          > Bronze) so it picks the best defender carried, with a
 *                          name-contains fallback for custom servers.
 *                        - Combat potion configurable: new settings.combatPotionType
 *                          + COMBAT_POTION_OPTIONS dropdown (Divine super combat /
 *                          Super combat / Crystalised super combat / custom).
 *                          getCombatPotionNames() builds (4)/(3)/(2)/(1) variants;
 *                          SUPER_COMBAT_NAMES static constant is gone.
 *                        - Pre-trip gear verification: verifyTripGear() blocks
 *                          leaving the bank without main weapon + defender + spec
 *                          weapon + necklace + ring + minimum food. Stays at the
 *                          bank to retry instead of tripping out under-geared.
 *                        - PoH ownership: new settings.useOwnHouse toggle. Own
 *                          house uses "Inside" teleport + skips ENTERING_FRIEND_HOUSE.
 *                          settings.poolName / settings.jewelleryBoxName let users
 *                          point at fancy/teak/marble pools or other jewellery boxes.
 *                        - Coordinator: new settings.waitForTeammateSpec. After
 *                          using the pool, the bot holds at the pool while bot
 *                          teammates with specPct < 100 are still in restoration
 *                          states (90s hard cap). Lets a single-POH party share
 *                          access in turn.
 *                        - Death detection: now gated on observing HP=0 within the
 *                          last 60s instead of "gravestone exists anywhere". Rules
 *                          out false positives from stray gravestones at random
 *                          banks. Gravestone confirmation moves to the LOOT_GRAVE
 *                          step where it's the actual loot target.
 *   1.5.1 (2026-05-15) - Phase G follow-ons + Phase H (death recovery).
 *                        - Death recovery: new BotState.DEATH_RECOVERY plus a 6-step
 *                          inner FSM (TO_BANK -> WITHDRAW -> TELE_TO_CORP -> LOOT_GRAVE
 *                          -> REEQUIP -> DONE). detectDeath() fires the transition
 *                          when we find ourselves outside Corp/Ferox with a gravestone
 *                          visible. Recovery walks to Ferox, withdraws Games necklace
 *                          + 10 food + 1 super restore + 1 super combat, tele's back,
 *                          loots the gravestone, re-wields main weapon + defender,
 *                          and resumes FIGHTING_CORP. Protect-from-magic is kept on
 *                          throughout.
 *                        - Vengeance: new settings.useVengeance toggle (some accounts
 *                          don't have Lunars). Stop-vengeance threshold is now a
 *                          configurable absolute-HP value (INTERNAL_CORP_LOW_HP_VENG_STOP,
 *                          default 85) instead of the hardcoded < 10% (= 200 HP).
 *                          Removed unused VENGEANCE_COOLDOWN_MS and BOSS_LOW_HEALTH_THRESHOLD
 *                          dead constants.
 *                        - Main weapon: Combat-tab field is now a JComboBox sourced from
 *                          MAIN_WEAPON_OPTIONS (editable for future weapons). New
 *                          getMainWeaponVariants() expands "Osmumten's fang" to match
 *                          both the regular and (or) ornament variants. The static
 *                          MAIN_WEAPON_NAMES array is gone; settings.mainWeapon is the
 *                          single source of truth.
 *                        - Banking keep-list: dead ITEMS_TO_KEEP constant removed.
 *                          shouldKeepItem() now keeps every owned spec weapon (not just
 *                          the currently chosen one), the main weapon's variants,
 *                          any defender, and the configured food list — so a banking
 *                          run can't accidentally deposit our Fang or any spec gear.
 *   1.5.0 (2026-05-14) - Phase G: modern dark-core "attack-and-step-away" meta.
 *                        - Legacy on-tile sidestep dodge moved into
 *                          handleAdvancedDarkCoreLegacy() and kept behind a toggle
 *                          (settings.useLegacyDarkCoreLogic, default false).
 *                        - handleAdvancedDarkCoreModern() now drives core handling
 *                          by default: bot equips Elder maul / DWH on core spawn,
 *                          the bot the core jumped to attacks it, then steps 2-3
 *                          tiles away so the core dies mid-air (no respawn).
 *                          Other bots hold the kill weapon ready while watching.
 *                          On core despawn, equipMainWeaponFast() re-wields Fang
 *                          and state returns to FIGHTING_CORP.
 *                        - GUI: Spec tab gets a "Use legacy dodge logic (fallback)"
 *                          checkbox so we can A/B the two strategies.
 *   1.4.0 (2026-05-14) - Phase D: dynamic spec-weapon rotation by team phase, plus
 *                        real BGS damage tracking via Hitsplat.isMine().
 *                        - pickSpecWeaponForCurrentPhase() chooses the best owned
 *                          weapon for whichever phase the team currently needs (1: Elder
 *                          maul/DWH, 2: Emberlight/Arclight/Darklight, 3: BGS).
 *                        - refreshSpecWeaponForPhase() updates chosenSpecWeapon at the
 *                          top of each spec handler. equipSpecWeapon() handles the
 *                          actual gear swap. Skips spec entirely if no usable weapon.
 *                        - getMyLargestRecentHitOnCorp() reads corp.getHitsplats() and
 *                          filters by isMine() to get accurate BGS damage. The +30
 *                          approximation is now only the fallback when no hitsplat
 *                          is visible.
 *   1.3.0 (2026-05-14) - Phase C: wire coordinator into spec-decision logic.
 *                        - teamPhaseNeeded() returns 1/2/3/0 based on team's aggregate.
 *                        - shouldSpecNowConsideringTeam() gates spec attempts: if my
 *                          weapon's phase is already complete team-wide, skip and DPS.
 *                        - recordSpecUsed() updates mySnapshot.specsThisKill per spec.
 *                        - coordinatorOnKillEnded() called from handleLooting() to
 *                          advance kill_id and reset per-kill counters.
 *   1.2.0 (2026-05-14) - Phase B: team coordinator plumbing.
 *                        - CorpCoordinator class: read/write/aggregate of a shared
 *                          JSON file in the ScriptSettings directory. Atomic-rename
 *                          writes for crash safety. Peer model — each bot publishes
 *                          its own snapshot, reads team aggregate.
 *                        - AccountSnapshot / TeamState / TeamAggregate data classes.
 *                        - SPEC_COST and SPEC_PHASE static maps for weapon metadata.
 *                        - GUI: phase target spinners on the Spec tab.
 *                        - Tick integration: coordinatorPublish() at top of main loop,
 *                          throttled by coordinatorWriteIntervalTicks.
 *   1.1.0 (2026-05-14) - Bulk refactor (clone from F drive):
 *                        * Bug fix: EAT_HEALTH_THRESHOLD was a static final evaluated at
 *                          class-load time (Skill.HITPOINTS.getActualLevel() - 21), which
 *                          read 1 or 0 when the player wasn't logged in. Replaced with
 *                          eatHealthThreshold() method called at runtime.
 *                        * Bug fix: while(true) main loop -> while(running). Adds clean
 *                          shutdown path (no behavior change beyond the loop guard).
 *                        * Configurability: 17 hardcoded constants moved into a
 *                          CorpSettings class -- friendName, acceptableTeammates list,
 *                          mainWeapon, foodNames, target inventory counts, HP/prayer
 *                          thresholds, spec settings, valuable loot list.
 *                        * GUI: 5-tab Swing settings dialog (Combat / Spec / POH+Team /
 *                          Inventory / Loot) with profile row at top (Load/Save as/
 *                          Delete). Profiles namespaced under "corp_<name>".
 *                        * Args: profile name; if non-empty and matches a saved profile,
 *                          loads it and skips the dialog. See FUNDAMENTALS section 17.
 *   1.0.0 (n.d.) -        Original script as imported from F:\Corp.java. Existing logic
 *                          preserved verbatim except for the bug fixes above.
 *
 * KNOWN-FIX
 *   - EAT_HEALTH_THRESHOLD class-load-time bug: see 1.1.0.
 *   - while(true) without running flag: see 1.1.0.
 *
 * OPEN
 *   - CORP_SPAWN_LOCATION = WorldTile(2978, 4384, 2) carries the original author's
 *     "TODO: Update with actual coordinates" comment. Verify in-game and bake the
 *     correct value (or move it to CorpSettings if it should be user-configurable).
 *   - Single FRIEND_NAME for POH entry (now settings.friendName). To support
 *     multiple friends with fallback, change to List<String> and iterate.
 *   - Many tuning constants (camera angles, core dodge distances, state timeouts,
 *     vengeance cooldowns, valuableLoot, items-to-keep) NOT yet in CorpSettings.
 *     Add to GUI on demand.
 *   - SUPER_RESTORE_NAMES/SUPER_COMBAT_NAMES are dose-suffixed (4)(3)(2)(1) variants
 *     hardcoded as constants. If you switch to a different prayer/combat potion in
 *     the future, those need updating.
 */
@TribotScriptManifest(name = "Corp", author = "Me", category = "Combat", description = "Corporeal Beast team fighter (modernized)")

public class Corp implements TribotScript {

	// ========== SETTINGS / RUNTIME ==========
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
    public static final int INTERNAL_CORP_LOW_HP_VENG_STOP = 85;
    public static final int INTERNAL_COORD_WRITE_INTERVAL_TICKS = 5;
    public static final long INTERNAL_COORD_STALE_THRESHOLD_MS = 10_000L;
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
            "Osmumten's fang"
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
    private static final WorldTile CORP_SPAWN_LOCATION = new WorldTile(2978, 4384, 2); //  TODO: Update with actual coordinates
    // Relative positioning around Corp (not fixed coordinates, but relative offsets)
    // These offsets ensure proper spacing around Corp regardless of where it roams
	// NEW - Designed for 5x5 NPC
	// KEEP THESE - They put you 1 tile from Corp edge, which is perfect for melee
	private static final List<int[]> CORP_POSITION_OFFSETS = Arrays.asList(
			new int[]{-3, 0},  // 3 tiles from center = 1 tile from edge - PERFECT for melee
			new int[]{3, 0},   // 3 tiles from center = 1 tile from edge - PERFECT for melee
			new int[]{0, -3},  // 3 tiles from center = 1 tile from edge - PERFECT for melee
			new int[]{0, 3}    // 3 tiles from center = 1 tile from edge - PERFECT for melee
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
    private static final int MAX_CAMERA_ANGLE = 100; // Maximum camera angle (highest view)
    private static final int MIN_ACCEPTABLE_ANGLE = 80; // 20 degrees below max (100 - 20 = 80)
    private static final int CAMERA_CHECK_INTERVAL_MS = 5000; // Check camera every 5 seconds
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
    // 1.9.73: latched per core-engagement so we only attempt the
    // auto-retaliate disable once. Reset when the core grace expires.
    private boolean autoRetaliateDisabledForThisCore = false;
    private final WorldTile lastSafePosition = null;
    // Vengeance tracking
    private boolean hasUsedVengeanceThisTrip = false;
    private boolean vengeanceQueued = false;
    private long vengeanceUseTime = 0;
    private final long lastVengeanceCastTime = 0;
    // Prayer tracking
    private boolean prayerActivationQueued = false;
    private long prayerActivationTime = 0;
    private boolean prayerDeactivationQueued = false;
    private long prayerDeactivationTime = 0;
    private boolean corpWasAliveLastCheck = false;
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
    // Weapon switching tracking
    private boolean specWeaponSwitchQueued = false;
    private long specWeaponSwitchTime = 0;
    private boolean needsToSwitchBackFromSpec = false;
    private boolean specWeaponReadyForUse = false; // NEW: Track if spec weapon is ready
    // 1.9.34: debounce timestamp for spec-button activation. Combat.activateSpecialAttack()
    // is a CLICK on the spec button; if the button is already ON, clicking
    // it TURNS IT OFF. Multiple sites in the code call activate-with-guard,
    // but the guard (isSpecialAttackEnabled) returns stale data right after
    // a click. So 4 sites firing back-to-back in 2 seconds can produce an
    // unpredictable final state. Use this timestamp to skip activation if
    // we clicked recently — trust the prior click instead.
    private long lastSpecActivateAt = 0;
    // 1.9.78: randomized spec pre-activation timing. Each restoration
    // trip the bot rolls 50% at 3 stages (POH-before-tele, Corp-lobby,
    // boss-room). First "yes" wins and activates spec; final stage is
    // forced (guaranteed activation by the time we engage Corp). Once
    // a stage is rolled, we don't re-roll it this trip — pattern stays
    // varied across trips instead of activating at the same moment
    // every cycle.
    private boolean specPreActivatedThisTrip = false;
    private boolean preActivateStageARolled = false;
    private boolean preActivateStageBRolled = false;
    private final java.util.Random preActivateRng = new java.util.Random();

    /** 1.9.78: stage A — after pool drink, before tele back to Corp.
     *  50% chance to pre-activate. Logged result either way so we can
     *  see the dice rolls in the log. */
    private void maybePreActivateSpecStageA() {
        if (specPreActivatedThisTrip || preActivateStageARolled) return;
        preActivateStageARolled = true;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        if (preActivateRng.nextBoolean()) {
            Log.info("Spec pre-activate stage A (POH): rolled YES");
            if (tryActivateSpec()) {
                specPreActivatedThisTrip = true;
                lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                xpAtSpec = getMeleeCombatXp();
            }
        } else {
            Log.info("Spec pre-activate stage A (POH): rolled NO");
        }
    }

    /** 1.9.78: stage B — in Corp lobby, before walking to boss room.
     *  Another 50% chance if stage A didn't activate. */
    private void maybePreActivateSpecStageB() {
        if (specPreActivatedThisTrip || preActivateStageBRolled) return;
        preActivateStageBRolled = true;
        if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;
        if (preActivateRng.nextBoolean()) {
            Log.info("Spec pre-activate stage B (lobby): rolled YES");
            if (tryActivateSpec()) {
                specPreActivatedThisTrip = true;
                lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
                xpAtSpec = getMeleeCombatXp();
            }
        } else {
            Log.info("Spec pre-activate stage B (lobby): rolled NO");
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
            }
        } else {
            // already on (e.g. from a prior tick) — flag it as done
            specPreActivatedThisTrip = true;
        }
    }

    /** 1.9.78: reset the pre-activation state machine. Called after a
     *  successful pool drink so the next restoration cycle gets fresh
     *  dice rolls. */
    private void resetSpecPreActivationRolls() {
        specPreActivatedThisTrip = false;
        preActivateStageARolled = false;
        preActivateStageBRolled = false;
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
    private String pendingHitWeapon = null;
    private long pendingHitXpBaseline = -1;
    private long pendingHitDeadline = 0;
    private static final long HIT_CONFIRM_TIMEOUT_MS = 2000;
    // ========== STATE HANDLERS ==========
    // State machine timeouts to prevent infinite loops (different timeouts per state)
    private final long lastStateChangeTime = 0;
    private final BotState lastState = null;
    // Team coordination tracking
    private final long lastTeammateSeenTime = 0;
    private final long vengeanceActiveTime = 0;
    private long lastVengeanceCast = 0;
    private VengeanceState vengeanceState = VengeanceState.READY_FOR_FIRST_CAST;
    private final long lastHealthCheck = 0;

    // ========== MAIN CAMERA MANAGEMENT SYSTEM ==========
    private int previousHealth = 0;
    private boolean bossWasAlive = false;
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

                // Always check for maintenance needs (unless in emergency)
                if (currentState != BotState.EMERGENCY_ESCAPE) {
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

        Log.info("Starting Improved Corporeal Beast Team Fighter");
		initializeCameraSetup();
		scriptStartTime = System.currentTimeMillis();
		overlayInit();

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
                if (antiStompTick()) {
                    Waiting.waitUniform(23, 75);
                    continue; // skip the rest of this iteration; step needs to land
                }

                // Execute current state - ONLY ONE STATE RUNS PER ITERATION
                executeCurrentState();

                // Always check for maintenance needs (unless in emergency)
                if (currentState != BotState.EMERGENCY_ESCAPE) {
                    handleHealthAndPrayer();
                    handlePrayerActivationTiming();
                    handlePrayerDeactivationTiming();
                    handleSpecWeaponSwitchTiming();
                }

                Waiting.waitUniform(23,75);

                overlayUpdate();

            } catch (Exception e) {
                Log.error("Error in main loop: " + e.getMessage());
                currentState = BotState.EMERGENCY_ESCAPE;
            }
        }
        overlayClose();
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

	private void handleStarting() {
		Log.info("Initializing bot with simplified POH restoration...");

		detectAndSetSpecWeapon();
		resetTripTracking();

		if (!hasRequiredItemsWithPOH()) {
			Log.info("Missing required items, going to Ferox Enclave");
			currentState = BotState.BANKING_AND_HEALING;
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
            "Dragon warhammer",
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
				success = Waiting.waitUntil(3000, () -> isSpecWeaponEquipped());
				if (!success && attempt < MAX_WIELD_ATTEMPTS) {
					Log.warn("Spec weapon wield attempt " + attempt + "/"
							+ MAX_WIELD_ATTEMPTS + " timed out — retrying");
					Waiting.waitNormal(400, 150);
				}
			}
		}
		if (success) {
			Log.info("Successfully equipped spec weapon: " + chosenSpecWeapon);
			if (!isTwoHandedSpec(chosenSpecWeapon) && !hasDefenderEquipped()) {
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
        boolean bankActuallyOpen;
        try {
            bankActuallyOpen = Bank.isOpen()
                    || Query.widgets()
                        .filter(w -> {
                            String raw = w.getText().orElse("");
                            if (raw.isEmpty()) return false;
                            String clean = raw.replaceAll("<[^>]*>", "")
                                    .trim().toLowerCase();
                            return clean.startsWith("the bank of runescape")
                                    || clean.startsWith("bank of runescape")
                                    || clean.equals("rearrange mode")
                                    || clean.startsWith("search:")
                                    || clean.startsWith("deposit inventory")
                                    || clean.startsWith("deposit worn");
                        })
                        .findFirst()
                        .isPresent();
        } catch (Exception e) {
            bankActuallyOpen = Bank.isOpen();
        }

        // Step 4: Open bank
        if (!bankActuallyOpen) {
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
            } else {
                Log.error("Insufficient supplies in bank - STOPPING SCRIPT");
                // Signal teammates first so coordinator-aware bots wrap up,
                // then hard-stop ourselves. Login.logout() alone doesn't exit
                // the main loop — running must flip to false.
                signalSessionEnd("Bank out of supplies during banking trip");
                running = false;
                Login.logout();
                return; // Exit the method completely
            }
        }

        // Pre-trip gear verification. Catch silent withdraw-failures before
        // we leave the bank under-geared. Skipped in DEATH_RECOVERY flow which
        // has its own minimal requirements.
        if (!verifyTripGear()) {
            Log.error("Pre-trip gear check failed — staying at bank to retry");
            return;
        }

        // Only proceed if we successfully got supplies
        Bank.close();
        Waiting.waitUntil(2000, () -> !Bank.isOpen());
        resetTripTracking();
        // Bank trip changed our inventory — re-scan owned spec weapons on next access.
        invalidateOwnedSpecWeaponsCache();

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

        boolean defenderPresent = false;
        for (String d : DEFENDER_PRIORITY) {
            if (Inventory.contains(d) || Equipment.contains(d)) { defenderPresent = true; break; }
        }
        if (!defenderPresent) {
            // Honor the fallback path (any "defender" substring) too.
            defenderPresent = Query.inventory().nameContains("defender").isAny()
                    || Query.equipment().nameContains("defender").isAny();
        }
        if (!defenderPresent) {
            Log.error("Trip gear: no defender present");
            return false;
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
        chosenSpecWeapon = ELDER_MAUL; // last-ditch default
    }

    private void handleWaitingForTeam() {
        Log.info("Waiting for team in lobby area...");

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

        // Check if acceptable teammates are in the boss room
        if (hasAcceptableTeammatesInBossRoom()) {
            Log.info("Acceptable teammates detected in boss room, joining them");
            // NOW we move to boss room to join them
            if (!isInCorpBossRoom()) {
                if (moveToCorpBossRoom()) {
                    Waiting.waitUntil(5000, () -> isInCorpBossRoom());
                }
            }
            currentState = BotState.ENTERING_COMBAT;
            return;
        }

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
        Log.info("No acceptable teammates found AND Corp not visible — staying in lobby...");
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
        corpSeenAtZeroHp = false;

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

        // 1.9.70: prayer ON before the walk, not after. Pre-1.9.70 we
        // kicked off moveToCorpBossRoom THEN activated PfM — the walk
        // starts immediately but PfM takes a tick to register, so Corp's
        // first magic hit landed against unprotected HP. Now prayer
        // setup happens FIRST, then the walk kicks off; PfM is live
        // before we're in render range of Corp.
        if (!Prayer.isQuickPrayerEnabled()) {
            Log.info("Activating quick prayer (entering combat)");
            Prayer.enableQuickPrayer();
        }
        try {
            if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
                Log.info("Activating Protect from Magic (entering combat)");
                Prayer.PROTECT_FROM_MAGIC.enable();
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
            Waiting.waitUntil(8000, () -> isInCorpBossRoom());
            return;
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

            // IMMEDIATE prayer activation now that Corp is visible
            if (!Prayer.isQuickPrayerEnabled()) {
                Log.info("IMMEDIATELY activating prayers - Corp is visible");
                Prayer.enableQuickPrayer();
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
            if (corp.interact("Attack")) {
                if (Waiting.waitUntil(5000, () -> isPlayerInCombat())) {
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
                // Equip spec weapon while waiting for spawn (if not visible yet)
                if (!isElderMaulEquipped()) {
                    Log.info("Equipping Elder Maul while searching for Corp");
                    equipElderMaul();
                }

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
            if (corpEarly.get().interact("Attack")) {
                return Waiting.waitUntil(6000, () ->
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
        if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
            Log.info("Activating Protect from Magic");
            Prayer.PROTECT_FROM_MAGIC.enable();
        }
    }

    private void handleFightingCorp() {
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

        // PRIORITY 1: Handle Dark Core (most dangerous) - Updated detection
        if (isDarkCorePresent()) {
            darkCoreLastSeen = System.currentTimeMillis();

            // Initialize core tracking if this is first detection
            if (chosenDodgeAxis == CoreDodgeAxis.NOT_SET) {
                Log.info("First dark core detection - initializing tracking");
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
            WorldTile myPos = MyPlayer.getTile();
            Area corpArea = corp.getArea();
            if (myPos != null && corpArea != null && corpArea.contains(myPos)) {
                Log.warn("Player on Corp's hitbox — emergency step away to avoid stomp damage");
                if (moveToNearestCorpPosition(corp)) {
                    lastRepositionCheck = System.currentTimeMillis();
                    return; // skip rest of tick — we just clicked-to-walk
                }
            } else if (System.currentTimeMillis() - lastRepositionCheck > 3000) {
                lastRepositionCheck = System.currentTimeMillis();
                if (!isInGoodCorpPosition(corp)) {
                    Log.info("Drifted from Corp position (Corp roamed) — repositioning");
                    if (moveToNearestCorpPosition(corp)) return;
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
                pendingHitWeapon = chosenSpecWeapon;
                pendingHitXpBaseline = getMeleeCombatXp();
                pendingHitDeadline = System.currentTimeMillis() + HIT_CONFIRM_TIMEOUT_MS;
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
		if (shouldUseSpecialAttack() && !Combat.isSpecialAttackEnabled()) {
			Log.info("Special attack conditions met - PRE-ACTIVATING for next attack");
			if (tryActivateSpec()) { // 1.9.34
				lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); // 1.9.2 + 1.9.9: seed detector floor
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

		// 1.9.58: in kill phase with Fang equipped, fire Fang's 25% spec.
		// User: 'while we are killing corp with the fang after actually
		// getting all debuff specs off we should be able to use our 25%
		// fang specs on the corp.' Fang spec hits harder + applies a
		// bleed for extra DPS. Energy gate is 25 (Fang cost), not the
		// generic getMinSpecEnergy() (50 for Arclight/Maul).
		if (teamPhaseNeeded() == 0
				&& Equipment.contains("Osmumten's fang")
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

		handleVengeanceLogic();

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
            if (!isPlayerAttackingCorp(corp)) {
                corp.interact("Attack");
            }

            // 1.9.24 / 1.9.28: only declare Corp dead when we observe HP
            // at 0 AFTER having previously seen HP > 5%. The SDK reports
            // 0% on a freshly-engaged Corp because the bar exists but
            // hasn't been populated yet — without the tracker, the bot
            // transitions from FIGHTING_CORP straight to LOOTING on the
            // first tick of combat and wastes the kill.
            if (corp.isHealthBarVisible()) {
                double currentHpPercent = corp.getHealthBarPercent();
                if (currentHpPercent > maxCorpHpPercentThisKill) {
                    maxCorpHpPercentThisKill = currentHpPercent;
                }
                if (currentHpPercent <= 1.0 && maxCorpHpPercentThisKill > 5.0) {
                    Log.info("Corp HP observed at 0 (peak this kill: "
                            + maxCorpHpPercentThisKill + "%) — looking for loot");
                    corpSeenAtZeroHp = true;
                    currentState = BotState.LOOTING;
                }
            }
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
            if (corpSeenAtZeroHp) {
                Log.info("Corp not found AND we saw HP at 0 — looking for loot");
                currentState = BotState.LOOTING;
            } else {
                Log.debug("Corp not in render but no confirmed 0 HP — not transitioning to LOOTING yet");
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
        if (!autoRetaliateDisabledForThisCore) {
            try {
                if (Combat.isAutoRetaliateOn()) {
                    Log.info("Dark core present — disabling auto-retaliate "
                            + "(one-shot for this core engagement)");
                    Combat.setAutoRetaliate(false);
                }
                autoRetaliateDisabledForThisCore = true;
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
        Optional<Npc> coreOpt = findDarkCore();
        if (!coreOpt.isPresent()) {
            long sinceSeen = System.currentTimeMillis() - darkCoreLastSeen;
            if (darkCoreLastSeen > 0 && sinceSeen < CORE_GRACE_MS) {
                Log.debug("Dark core not visible (since="
                        + sinceSeen + "ms < " + CORE_GRACE_MS
                        + "ms grace) - holding kill weapon");
                return;
            }
            Log.info("Dark core gone (>" + CORE_GRACE_MS
                    + "ms grace expired) - re-equipping main weapon");
            equipMainWeaponFast();
            // 1.9.64: re-enable auto-retaliate now that the core's gone.
            try {
                if (!Combat.isAutoRetaliateOn()) {
                    Log.info("Core gone — re-enabling auto-retaliate");
                    Combat.setAutoRetaliate(true);
                }
            } catch (Exception ignored) {}
            // 1.9.73: reset latch so the next core triggers a fresh disable.
            autoRetaliateDisabledForThisCore = false;
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
            // Core is far / on a teammate — keep Fang on for Corp DPS.
            // If we accidentally have the maul equipped from a previous
            // tick, swap back to Fang.
            if (isCoreKillWeaponEquipped() && !specWeaponSwitchQueued) {
                Log.info("Dark core present but distant (dist=" + dist
                        + ") - swapping back to Fang for Corp DPS");
                queueSpecWeaponSwitchBack();
            }
            return;
        }

        // Core is on us / approaching — swap to maul for the jump kill.
        if (!isCoreKillWeaponEquipped()) {
            if (!equipCoreKillWeapon()) {
                Log.warn("Dark core close (dist=" + dist
                        + ") but no Elder maul / DWH available - falling back to legacy dodge");
                handleAdvancedDarkCoreLegacy();
                return;
            }
            return; // gear swap costs a tick; reassess next tick.
        }

        if (dist > 1.5) {
            // 1.9.69: while core is close but not adjacent (1.5 < dist <= 2.5),
            // KEEP ATTACKING CORP with the maul. User log showed the bot
            // froze for 70s straight at 'Dark core close (dist=2.0) -
            // kill weapon ready' — auto-retaliate is OFF (1.9.64) and
            // we weren't clicking anything, so the bot just stood there
            // until food ran out. Now: click Corp explicitly so we
            // DPS while waiting for the core to land adjacent. The
            // next-tick check will switch to attacking the core when
            // dist hits <= 1.5.
            Optional<Npc> corpForWait = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpForWait.isPresent() && isCorpAlive(corpForWait.get())) {
                corpForWait.get().interact("Attack");
            }
            Log.debug("Dark core close (dist=" + dist + ") - "
                    + "DPS'ing Corp while waiting for core to land");
            return;
        }

        // PRIORITY 4: attack the core, then step away so it jumps mid-air.
        Log.info("Dark core adjacent (dist=" + dist + ") - attacking with kill weapon");
        if (core.interact("Attack")) {
            Waiting.waitNormal(350, 120);
            boolean stepped = stepAwayFromCore(core);
            // 1.9.63: if step-away fails AND HP is low, we're going to
            // die to the core landing. Bail to EMERGENCY_ESCAPE while
            // we still have a Ring of Dueling charge. User log showed
            // bot stuck for ~30s in a step-away-fails -> eat loop,
            // burning all food, until emergency escape finally fired
            // at HP <= 8 — by which point Ferox tele was racing the
            // next core land. Better to tele out at HP 30 with food
            // still in inventory than HP 8 with nothing left.
            if (!stepped && MyPlayer.getCurrentHealth() <= INTERNAL_COMBO_EAT_HP) {
                Log.warn("Step-away failed at low HP — bail to EMERGENCY_ESCAPE "
                        + "before the core lands and finishes us");
                currentState = BotState.EMERGENCY_ESCAPE;
            }
        } else {
            Log.warn("Failed to interact with dark core - retrying next tick");
        }
    }

    /** True if Elder maul or Dragon warhammer is currently wielded. */
    private boolean isCoreKillWeaponEquipped() {
        return Equipment.contains(ELDER_MAUL) || Equipment.contains("Dragon warhammer");
    }

    /** Wield Elder maul (preferred) or Dragon warhammer from inventory.
     *  Returns true if a kill weapon is equipped after the call. */
    private boolean equipCoreKillWeapon() {
        if (isCoreKillWeaponEquipped()) return true;
        String[] candidates = { ELDER_MAUL, "Dragon warhammer" };
        for (String name : candidates) {
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

        // Corp's hitbox — we want to step OUTSIDE it. May be null if Corp
        // isn't visible (shouldn't happen during dark core but defensive).
        Area corpArea = null;
        try {
            Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
            if (corpOpt.isPresent()) corpArea = corpOpt.get().getArea();
        } catch (Exception ignored) {}

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
        for (int[] o : offsets) {
            if (o[0] == 0 && o[1] == 0) continue;
            WorldTile target = new WorldTile(myPos.getX() + o[0], myPos.getY() + o[1], myPos.getPlane());
            if (corpArea != null && corpArea.contains(target)) continue; // inside Corp = stomp damage
            if (!corpCave.contains(target)) continue; // outside cave polygon = wall
            try {
                if (LocalWalking.walkTo(target)) {
                    Log.info("STEP-AWAY: moving to " + target);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        Log.warn("STEP-AWAY: every candidate walk failed (core may be cornering us)");
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
                    return;
                }
                if (teleportToFeroxEnclave()) {
                    Waiting.waitUntil(8000, () -> isAtFeroxEnclave());
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
                if (!hasDefenderEquipped()) equipAnyDefender();
                deathStep = DeathRecoveryStep.DONE;
                return;

            case DONE:
                Log.info("Death recovery complete — resuming combat");
                deathStep = DeathRecoveryStep.TO_BANK;
                deathCount++; // overlay counter
                lastHpZeroAt = 0;
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
				Waiting.waitUntil(2000, () -> !MyPlayer.isMoving() || MyPlayer.getTile().distanceTo(dodgePosition) <= 1);
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
                if (corp.interact("Attack")) {
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
            List<WorldTile> filtered = dynamicPositions.stream()
                    .filter(p -> !lineCrossesCorp(myPos, p, corpArea))
                    .collect(Collectors.toList());
            if (!filtered.isEmpty()) {
                reachable = filtered;
                if (filtered.size() != dynamicPositions.size()) {
                    Log.info("Filtered Corp positions: "
                            + filtered.size() + "/" + dynamicPositions.size()
                            + " reachable without crossing Corp's hitbox");
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
                return null;
            }
        }

        List<WorldTile> allPlayerPositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // 1.8.8 / 1.9.20: score factors —
        //   + separation from other players (capped at 6)
        //   - distance from self (prefer closer)
        //   + bonus when position is on SAME SIDE of Corp as the player
        //     (we don't have to cross Corp's hitbox to get there)
        // Pre-1.9.20 the self-distance alone could pick a tile that was
        // technically close in Euclidean terms but on the opposite side
        // of Corp — the walker then routed through Corp's hitbox.
        WorldTile bestPosition = null;
        double bestScore = -Double.MAX_VALUE;

        for (WorldTile position : reachable) {
            double minDistanceToPlayer = allPlayerPositions.stream()
                    .mapToDouble(playerPos -> playerPos.distanceTo(position))
                    .min()
                    .orElse(Double.MAX_VALUE);
            double selfDistance = myPos == null ? 0 : myPos.distanceTo(position);
            double separationScore = Math.min(minDistanceToPlayer, 6.0);

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

            double score = separationScore - selfDistance + sameSideBonus;

            if (score > bestScore) {
                bestScore = score;
                bestPosition = position;
            }
        }

        if (bestPosition == null) {
            bestPosition = reachable.get(0); // safety net, prefer pre-filtered list
        }
        Log.info("Picked Corp position " + bestPosition + " (score=" + bestScore
                + ") from " + reachable.size() + " candidates"
                + (reachable.size() != dynamicPositions.size()
                        ? " (filtered from " + dynamicPositions.size() + ")"
                        : ""));
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
        if (settings == null || settings.acceptableTeammates == null) return false;
        return Query.players()
                .stream()
                .filter(p -> p.getName() != null
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
        return Query.players()
                .stream()
                .anyMatch(player -> settings.acceptableTeammates.contains(player.getName()));
    }

    /**
     * Check if we can cast vengeance spell
     */
    private boolean canCastVengeance() {
        // Check magic level
        if (Skill.MAGIC.getCurrentLevel() < 94) {
            return false;
        }

        // Check if vengeance is still protecting us (smart detection)
        if (isVengeanceStillActive()) {
            Log.debug("Cannot cast vengeance - still active and protecting us");
            return false;
        }

        // Check basic cooldown
        return !isVengeanceOnCooldown();
    }

    private boolean isCorpLowHealth(Npc corp) {
        // Corp has 2000 total HP. Translate the configured absolute threshold
        // (INTERNAL_CORP_LOW_HP_VENG_STOP) into a % comparison against the visible bar.
        if (corp.isHealthBarVisible()) {
            double healthPercent = corp.getHealthBarPercent();
            double thresholdPercent = (INTERNAL_CORP_LOW_HP_VENG_STOP / 2000.0) * 100.0;
            return healthPercent < thresholdPercent;
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
        try {
            castVengeanceWidget(142);
            lastVengeanceCast = before;
            hasUsedVengeanceThisTrip = true;
            vengeanceQueued = false;
            Log.info("Vengeance cast via widget (sprite 564)");
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
        // Update health tracking for state transitions
        updateHealthTracking();

        // User toggle: skip vengeance entirely on accounts that don't have Lunars / runes.
        if (!settings.useVengeance) return;

        // 1.9.18: explicit kill-phase gate. Vengeance only fires when we've
        // either dumped enough specs (teamPhaseNeeded() == 0) OR Corp HP
        // has dropped below corpMinHpForSpec (1700). Pre-1.9.18 the
        // READY_FOR_FIRST_CAST branch would cast in the lobby and
        // ACTIVE_CASTING would fire as soon as we took ANY damage —
        // including the damage we take while spec-dumping. The heal got
        // wasted. Now we explicitly skip veng during pre-engagement and
        // spec-dump phases.
        if (!isInKillPhase()) {
            return;
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
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return false;
        Npc corp = corpOpt.get();
        if (!corp.isHealthBarVisible()) return false;
        double hpPercent = corp.getHealthBarPercent();
        // 1.9.28: require we've previously seen Corp HP > 5% before
        // trusting a low-HP reading. The bar reads 0% on a freshly-
        // engaged Corp; without this guard, isInKillPhase returns true
        // on the first tick of combat and the bot prematurely queues a
        // Fang swap thinking Corp is dying.
        if (hpPercent > maxCorpHpPercentThisKill) {
            maxCorpHpPercentThisKill = hpPercent;
        }
        if (maxCorpHpPercentThisKill <= 5.0) {
            return false; // bar not yet populated — don't trust the reading
        }
        int approxHp = (int) ((hpPercent / 100.0) * 2000);
        return approxHp < settings.corpMinHpForSpec;
    }

    private boolean hasRecentVengeanceCastWithoutDamage() {
        // If we cast vengeance recently (within 60 seconds) and haven't been in combat yet
        long timeSinceLastCast = System.currentTimeMillis() - lastVengeanceCast;
        boolean recentCast = timeSinceLastCast < 60000; // 60 seconds

        // If recent cast and we haven't been in combat with Corp yet, don't recast
		return recentCast && !hasUsedVengeanceThisTrip;
	}

    // Also update this method in the ACTIVE_CASTING handler:
    private void handleActiveCasting(boolean bossAlive, boolean bossLowHealth) {
        // Handle boss death -> cast once after delay
        if (bossWasAlive && !bossAlive) {
            long delay = TribotRandom.uniform(BOSS_DEATH_VENG_MIN_DELAY, BOSS_DEATH_VENG_MAX_DELAY);

            if (System.currentTimeMillis() - lastVengeanceCast >= delay && canCastVengeance()) {
                if (castVengeance()) {
                    Log.info("Cast vengeance after boss death, returning to ready state");
                    vengeanceState = VengeanceState.READY_FOR_FIRST_CAST;
                    return;
                }
            }
        }

        // Cast every 31-37 seconds while boss alive (but not low health)
        if (bossAlive && !bossLowHealth) {
            long timeSinceLastCast = System.currentTimeMillis() - lastVengeanceCast;
            long randomCooldown = TribotRandom.uniform(VENG_MIN_COOLDOWN, VENG_MAX_COOLDOWN);

            if (timeSinceLastCast >= randomCooldown && canCastVengeance()) {
                Log.info("Attempting to cast vengeance during combat (active casting mode)");
                if (castVengeance()) {
                    Log.info("Successfully cast vengeance during combat");
                } else {
                    Log.warn("Failed to cast vengeance during combat - will retry");
                }
            } else if (timeSinceLastCast >= randomCooldown) {
                Log.debug("Vengeance cooldown ready but cannot cast - checking conditions...");
                Log.debug("Magic level: " + Skill.MAGIC.getCurrentLevel());
                Log.debug("Vengeance still active: " + isVengeanceStillActive());
            }
        }
    }

    // ========== HEALTH TRACKING ==========
    private void updateHealthTracking() {
        int currentHealth = MyPlayer.getCurrentHealth();

        // Check if HP went down (transition to active casting)
        if (previousHealth > 0 && currentHealth < previousHealth) {
            if (vengeanceState == VengeanceState.READY_FOR_FIRST_CAST) {
                Log.info("HP went down, switching to active vengeance casting");
                vengeanceState = VengeanceState.ACTIVE_CASTING;
            }
        }

        previousHealth = currentHealth;
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
        // If there are other acceptable teammates in the boss room, we can leave
        long acceptableTeammatesInBossRoom = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .filter(player -> settings.acceptableTeammates.contains(player.getName()))
                .filter(player -> isInCorpBossRoom()) // This would need proper implementation
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
        hasUsedVengeanceThisTrip = false;
        vengeanceQueued = false;
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
        pendingHitWeapon = null;
        pendingHitXpBaseline = -1;
        pendingHitDeadline = 0;

        startedFightingWithTeammates = false;
        fightStartTime = 0;

        // Reset core dodging tracking
        resetCoreDodgeTracking();
		resetRestorationTracking();

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
            boolean reachable = Query.tiles()
                    .filter(t -> t.equals(tile))
                    .isReachable()
                    .findFirst()
                    .isPresent();

            if (!reachable) {
                Log.debug("Tile not reachable via query: " + tile);
                return false;
            }

            // Additional check: make sure it's not wildly out of range
            WorldTile myPos = MyPlayer.getTile();
            if (myPos.distanceTo(tile) > 15) {
                Log.debug("Tile too far away: " + tile + " (distance: " + myPos.distanceTo(tile) + ")");
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
        int dx = myPos.getX() - corpCenter.getX();
        int dy = myPos.getY() - corpCenter.getY();
        if (dx == 0 && dy == 0) return null;
        // 1.9.61: step 4 tiles from corp center (was 3). Corp moves a tile
        // or two between snapshot and walk arrival; a 3-tile offset put
        // us 1 tile outside hitbox at compute time but inside the new
        // hitbox by the time we got there. User died from stomp damage
        // because of this — log showed corp center shifting from
        // (2989,4391) to (2987,4389) between getDynamicCorpPositions
        // and isPositionSafeFromCorpHitbox. 4-tile offset gives 2 tiles
        // of buffer, so Corp can move up to 2 tiles and we're still
        // outside.
        int sx = (dx == 0) ? 0 : Integer.signum(dx);
        int sy = (dy == 0) ? 0 : Integer.signum(dy);
        WorldTile candidate;
        if (Math.abs(dx) >= Math.abs(dy)) {
            candidate = new WorldTile(corpCenter.getX() + 4 * sx,
                    corpCenter.getY(), corpCenter.getPlane());
        } else {
            candidate = new WorldTile(corpCenter.getX(),
                    corpCenter.getY() + 4 * sy, corpCenter.getPlane());
        }
        if (corpArea.contains(candidate)) return null;
        if (lineCrossesCorp(myPos, candidate, corpArea)) {
            if (Math.abs(dx) >= Math.abs(dy) && sy != 0) {
                candidate = new WorldTile(corpCenter.getX(),
                        corpCenter.getY() + 4 * sy, corpCenter.getPlane());
            } else if (sx != 0) {
                candidate = new WorldTile(corpCenter.getX() + 4 * sx,
                        corpCenter.getY(), corpCenter.getPlane());
            }
            if (corpArea.contains(candidate)) return null;
            if (lineCrossesCorp(myPos, candidate, corpArea)) return null;
        }
        return candidate;
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
        Area corpArea = corp.getArea();
        WorldTile corpCenter = corpArea.getCenter();

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
		// Phase E: try a coordinator-claimed offset first. Each bot claims a
		// different cardinal direction (N/S/E/W) around Corp so the team is
		// spread out. The claim is on the OFFSET, not the world tile — as Corp
		// moves around the cave, the actual target tile recomputes from
		// corp.getTile() + offset. Distance tolerance 2 means the bot doesn't
		// need to land exactly on the offset, just on Corp's claimed side.
		WorldTile claimed = pickCoordinatedCorpPosition(corp);
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
					Waiting.waitUntil(8000, () -> {
						WorldTile cur = MyPlayer.getTile();
						if (cur == null) return false;
						if (corpAreaClaim.contains(cur)) return false;
						if (cur.distanceTo(corner) <= 1) return true;
						return !lineCrossesCorp(cur, claimed, corpAreaClaim);
					});
				} else {
					Log.info("Claimed tile requires crossing Corp, no safe "
							+ "corner — corp.interact('Attack')");
					if (corp.interact("Attack")) {
						return Waiting.waitUntil(6000, () ->
								isPlayerInCombat() || MyPlayer.isAnimating());
					}
				}
			}
			if (LocalWalking.walkTo(claimed)) {
				return Waiting.waitUntil(5000, () ->
						MyPlayer.getTile().distanceTo(claimed) <= 2);
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
		if (myPos != null && isPlayerAlreadyInCorpMeleeRange(myPos, corp)) {
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

		// 1.9.67: assignUniqueCorpPosition now returns null when all
		// cardinals cross Corp (synthesize path removed in 1.9.67 because
		// the synthesized tile could be inside Corp's hitbox by walk
		// arrival when Corp roamed). Delegate to game pathfinder via
		// click-attack.
		if (bestPosition == null) {
			Log.info("No safe walkable Corp position — corp.interact('Attack') "
					+ "(let game pathfinder handle approach)");
			if (corp.interact("Attack")) {
				return Waiting.waitUntil(6000, () ->
						isPlayerInCombat() || MyPlayer.isAnimating());
			}
			return false;
		}

		if (bestPosition != null) {
			Area corpArea = corp.getArea();
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
			if (myPos != null && lineCrossesCorp(myPos, bestPosition, corpArea)) {
				WorldTile corner = pickCornerWaypoint(myPos, bestPosition,
						corp.getArea().getCenter(), corpArea);
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
					Log.warn("No safe corner waypoint found — falling back "
							+ "to click-Attack on Corp (game pathfinder)");
					if (corp.interact("Attack")) {
						return Waiting.waitUntil(6000, () ->
								isPlayerInCombat() || MyPlayer.isAnimating());
					}
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
					Log.warn("Corp moved INTO target tile " + bestPosition
							+ " — aborting walk, click-Attack instead");
					if (corp.interact("Attack")) {
						return Waiting.waitUntil(6000, () ->
								isPlayerInCombat() || MyPlayer.isAnimating());
					}
					return false;
				}
				if (liveArea != null && myPos != null
						&& lineCrossesCorp(myPos, bestPosition, liveArea)) {
					Log.warn("Corp shifted — straight line to "
							+ bestPosition + " crosses hitbox, "
							+ "click-Attack instead");
					if (corp.interact("Attack")) {
						return Waiting.waitUntil(6000, () ->
								isPlayerInCombat() || MyPlayer.isAnimating());
					}
					return false;
				}
			}
			// At this point either the original path didn't cross OR we've
			// L-shaped past the hitbox; safe to walk straight to target.
			if (LocalWalking.walkTo(bestPosition)) {
				Log.info("Moving to safe Corp position: " + bestPosition);
				return Waiting.waitUntil(5000, () ->
						MyPlayer.getTile().distanceTo(bestPosition) <= 2);
			}
		}
		return false;
	}

	/** 1.9.21: would a straight-line walk from `from` to `to` pass through
	 *  the corpArea? Samples points along the line and checks each against
	 *  corpArea.contains(). This is a conservative check — RuneScape's
	 *  actual A* pathfinder may route around, but we want to FORCE the
	 *  bot to never cross Corp regardless of A*'s choices. */
	private boolean lineCrossesCorp(WorldTile from, WorldTile to, Area corpArea) {
		if (corpArea == null) return false;
		int dx = to.getX() - from.getX();
		int dy = to.getY() - from.getY();
		int steps = Math.max(Math.abs(dx), Math.abs(dy));
		if (steps == 0) return false;
		for (int i = 1; i < steps; i++) {
			int x = from.getX() + dx * i / steps;
			int y = from.getY() + dy * i / steps;
			WorldTile inter = new WorldTile(x, y, from.getPlane());
			if (corpArea.contains(inter)) return true;
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
            "Bronze defender"
    };

    private boolean equipAnyDefender() {
        for (String name : DEFENDER_PRIORITY) {
            Optional<InventoryItem> def = Query.inventory().nameEquals(name).findFirst();
            if (def.isPresent()) {
                Log.info("Equipping defender (priority): " + name);
                return def.get().click("Wield");
            }
        }
        // Last resort: any item whose name contains "defender" (catches custom
        // server variants we haven't listed in DEFENDER_PRIORITY).
        Optional<InventoryItem> fallback = Query.inventory().nameContains("defender").findFirst();
        if (fallback.isPresent()) {
            Log.info("Equipping defender (fallback): " + fallback.get().getName());
            return fallback.get().click("Wield");
        }
        Log.debug("No defender found in inventory");
        return false;
    }

    // New method to check if any defender is equipped
    private boolean hasDefenderEquipped() {
        return Query.equipment()
                .nameContains("defender")
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
                    return Waiting.waitUntil(5000, () ->
                            MyPlayer.getTile().distanceTo(position) <= 2);
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
            return Waiting.waitUntil(5000, () ->
                    MyPlayer.getTile().distanceTo(fallbackPosition) <= 2);
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
		if (!hasDefenderEquipped()) {
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
	private static final int MIN_DISTANCE_FROM_CORP_EDGE = 3;   // Minimum 3 tiles from edge
	private static final int MAX_ATTACK_DISTANCE_FROM_CORP_CENTER = 12; // Can attack from 12 tiles from center

	private double getDistanceToCorpHitboxEdge(WorldTile playerPos, Npc corp) {
		WorldTile corpCenter = corp.getTile();

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
			int energyBefore = Combat.getSpecialAttackPercent();
			Log.info("Spec " + (specsPerformed + 1) + " - Energy before: " + energyBefore + "%");
			// 1.9.48: capture XP and hitsplat-counter baselines BEFORE the spec
			// click. We'll check post-spec for ANY of the three signals:
			//   - melee XP delta (most reliable; spec animation locks out
			//     auto-attacks so any post-fire XP is from the spec)
			//   - new hitsplat on Corp not present in the baseline list
			//   - non-zero damage from getMyLargestRecentHitOnCorp
			// Pre-1.9.48 we relied on getMyLargestRecentHitOnCorp alone with
			// a flat 600ms wait. User: '2 specs hit and it only counted one'
			// — that happens when the hitsplat for spec N has already
			// disappeared (~6 ticks) before spec N+1's check, or when
			// network/render lag pushes the splat past our wait window.
			long preSpecXp = getMeleeCombatXp();

			if (corp.interact("Attack")) {
				// 🔥 WAIT FOR ENERGY DROP INSTEAD OF TIME
				boolean specExecuted = Waiting.waitUntil(5000, () -> {
					int currentEnergy = Combat.getSpecialAttackPercent();
					boolean energyDropped = currentEnergy < energyBefore;

					if (energyDropped) {
						Log.info("Special attack confirmed - Energy dropped from " + energyBefore + "% to " + currentEnergy + "%");
						return true;
					}
					return false;
				});

				if (specExecuted) {
					specsPerformed++;
					// 1.9.48: poll up to 2s for hit signal (XP delta OR hitsplat).
					// Either confirms a hit; absence of both = miss.
					final long preXp = preSpecXp;
					boolean hitSignal = Waiting.waitUntil(2000, () -> {
						if (getMeleeCombatXp() > preXp) return true;
						return getMyLargestRecentHitOnCorp(corp) > 0;
					});
					int dmg = getMyLargestRecentHitOnCorp(corp);
					if ("Bandos godsword".equalsIgnoreCase(chosenSpecWeapon)) {
						recordSpecUsed(chosenSpecWeapon, dmg);
						Log.info("BGS spec dealt ~" + dmg
								+ " damage (recorded for team phase 3)");
					} else if (hitSignal) {
						recordSpecUsed(chosenSpecWeapon);
						long xpDelta = getMeleeCombatXp() - preXp;
						Log.info("Spec HIT confirmed for " + chosenSpecWeapon
								+ " (xpDelta=" + xpDelta
								+ ", hitsplat=" + dmg + ")");
					} else {
						Log.info("Spec MISS for " + chosenSpecWeapon
								+ " (no XP delta and no hitsplat within 2s)"
								+ " — not counted");
					}
					int energyAfter = Combat.getSpecialAttackPercent();
					Log.info("Special attack " + specsPerformed + "/" + maxSpecs + " executed successfully! Energy: " + energyBefore + "% → " + energyAfter + "%");

					// Brief pause before next spec if doing multiple
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

		specWeaponReadyForUse = false;
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
            Log.info("Ready for next kill");
            currentState = BotState.WAITING_FOR_TEAM;
        }
    }

    private boolean hasEmergencySupplies() {
        return Inventory.getCount(settings.foodNames) >= 5 && // Minimum 5 food
                getPrayerDoses() >= 2;                  // Minimum 2 prayer doses
    }

    private void handleEmergencyEscape() {
        Log.warn("Emergency escape activated!");

        // Method 1: Ferox Enclave teleport (fastest and safest)
        if (attemptFeroxEscape()) {
            Log.info("Successfully escaped to Ferox Enclave");
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // Method 2: Games Necklace teleport
        if (attemptNecklaceEscape()) {
            Log.info("Successfully escaped using Games Necklace");
            currentState = BotState.BANKING_AND_HEALING;
            return;
        }

        // Method 3: Run to entrance
        if (attemptRunEscape()) {
            Log.info("Successfully escaped by running");
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
        return MyPlayer.getCurrentHealth() < Skill.HITPOINTS.getActualLevel() ||
                Prayer.getPrayerPoints() < Skill.PRAYER.getActualLevel();
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
        // Check if bank chest is visible or if we're near banking area
        return Query.gameObjects().nameContains("Bank chest").findFirst().isPresent() ||
                Bank.isNearby();
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
        if (Query.gameObjects().nameContains("Bank chest").findFirst().isPresent()) {
            Log.info("Bank chest already visible — no walk needed");
            return;
        }
        Log.info("Walking to Ferox bank/fountain tile (3135, 3630)...");
        WorldTile feroxBankTile = new WorldTile(3135, 3630, 0);
        try {
            org.tribot.script.sdk.walking.GlobalWalking.walkTo(feroxBankTile);
            Waiting.waitUntil(15000, () -> isNearFeroxBank()
                    || MyPlayer.getTile().distanceTo(feroxBankTile) <= 3);
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

        Optional<InventoryItem> necklaceOpt = Query.inventory().nameEquals("Games necklace(").findFirst();
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
        Optional<GameObject> exitOpt = Query.gameObjects()
                .filter(obj -> obj.getName().contains("Exit") ||
                        obj.getName().contains("Cave entrance") ||
                        obj.getName().contains("Entrance"))
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
                Waiting.waitUntil(5000, () ->
                        Query.gameObjects().filter(obj ->
                                obj.getName().contains("Exit") ||
                                        obj.getName().contains("Cave entrance")).findFirst().isPresent());

                // Try to find and click exit again after walking
                Optional<GameObject> exitAfterWalk = Query.gameObjects()
                        .filter(obj -> obj.getName().contains("Exit") ||
                                obj.getName().contains("Cave entrance"))
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

		// Keep any defender (Avernic / Dragon / etc.).
		if (itemName.toLowerCase().contains("defender")) {
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
			Log.info("No house tabs available - skipping POH restoration");
			return false;
		}

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
		Log.info("Starting banking withdrawal (ignoring house tabs)...");
		boolean overallSuccess = true;

		// Phase 1: Essential items (no house tabs)
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

		// Check minimum supplies (no POH requirement)
		if (!overallSuccess && !hasMinimumSupplies()) {
			Log.error("Banking failed and insufficient minimum supplies");
			return false;
		}

		Log.info("Banking withdrawal complete");
		return true;
	}

    private boolean withdrawEssentialItems() {
        List<String> essentialItems = new ArrayList<>();

        // Build list of needed essential items. For charged jewelry, top up
        // whenever the highest dose drops below threshold — not just when
        // missing — so a (1)-charge ring doesn't strand us next trip.
        if (!hasElderMaul()) essentialItems.add("Elder maul");
        if (!hasRunePouch()) essentialItems.add("Rune pouch");
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

    private boolean withdrawRunePouch() {
        // Try divine rune pouch first, then regular
        if (Bank.getCount(DIVINE_RUNE_POUCH) > 0) {
            Log.info("Withdrawing Divine rune pouch");
            return Bank.withdraw(DIVINE_RUNE_POUCH, 1);
        } else if (Bank.getCount(RUNE_POUCH) > 0) {
            Log.info("Withdrawing Rune pouch");
            return Bank.withdraw(RUNE_POUCH, 1);
        }

        Log.error("No rune pouch (regular or divine) found in bank");
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

		// Already prepped? Spec weapon equipped + spec button active → no work.
		if (isSpecWeaponEquipped() && Combat.isSpecialAttackEnabled()) {
			specWeaponReadyForUse = true;
			return;
		}

		// Energy gate: don't waste prep on a near-empty bar.
		if (Combat.getSpecialAttackPercent() < getMinSpecEnergy()) return;

		// Make inventory space if needed (without potion drink — we're not
		// taking damage out here so it's safe to use the slow path).
		if (Inventory.isFull() && !isStatsBoosted()) {
			Log.info("Lobby prep: stats not boosted - drinking super combat");
			if (drinkSuperCombat()) {
				Waiting.waitUntil(2000, this::isStatsBoosted);
			}
		}
		if (Inventory.isFull()) {
			Log.info("Lobby prep: eating karambwan for inventory space");
			eatKarambwan();
		}

		if (!isSpecWeaponEquipped()) {
			Log.info("Lobby prep: equipping spec weapon " + chosenSpecWeapon);
			if (!equipSpecWeapon()) {
				Log.warn("Lobby prep: failed to equip spec weapon - will retry in-room");
				return;
			}
		}

		specWeaponReadyForUse = true;
		// 1.9.78: stage B — roll for spec pre-activate in the lobby
		// (only if stage A at the pool didn't already activate).
		// The 50% miss case falls through to stage C in handleFightingCorp.
		maybePreActivateSpecStageB();
		// If neither stage A nor B activated, leave the energy floor /
		// xp baseline in their pre-activate state for stage C to handle.
		if (Combat.isSpecialAttackEnabled() && specPreActivatedThisTrip) {
			lastSeenSpecEnergy = Combat.getSpecialAttackPercent();
			xpAtSpec = getMeleeCombatXp();
		}
	}

	private void prepareSpecWeaponForCorp(Npc corp) {
		if (chosenSpecWeapon == null) {
			detectAndSetSpecWeapon();
		}

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
		if (currentHp <= INTERNAL_COMBO_EAT_HP) {
			Log.warn("HP " + currentHp + " <= " + INTERNAL_COMBO_EAT_HP +
					" — skipping spec prep, combo-eating instead");
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
							lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); // 1.9.2 + 1.9.9
							Log.info("Special attack pre-activated successfully!");
						} else {
							Log.warn("Failed to pre-activate special attack");
						}
					} else {
						lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); // 1.9.2 + 1.9.9
					}
				}
			} else {
				Log.info("Chosen spec weapon already equipped: " + chosenSpecWeapon);
				specWeaponReadyForUse = true;

				// Pre-activate if not already active
				if (!Combat.isSpecialAttackEnabled()) {
					Log.info("PRE-ACTIVATING special attack - weapon ready");
					if (tryActivateSpec()) { // 1.9.34
						lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); // 1.9.2 + 1.9.9
						Log.info("Special attack pre-activated successfully!");
					}
				} else {
					lastSeenSpecEnergy = Combat.getSpecialAttackPercent(); xpAtSpec = getMeleeCombatXp(); // 1.9.2 + 1.9.9
				}
			}

			// 🔥 FINAL HEALTH CHECK
			int finalHealth = MyPlayer.getCurrentHealth();
			if (finalHealth <= eatHealthThreshold()) {
				Log.info("Health low after spec prep (" + finalHealth + ") - eating before combat");
				normalEat();
			}
		}
	}

    private boolean isStatsBoosted() {
        int currentAttack = Skill.ATTACK.getCurrentLevel();
        int baseAttack = Skill.ATTACK.getActualLevel();
        int currentStrength = Skill.STRENGTH.getCurrentLevel();
        int baseStrength = Skill.STRENGTH.getActualLevel();

        boolean attackBoosted = currentAttack > baseAttack;
        boolean strengthBoosted = currentStrength > baseStrength;

        Log.info("Stat check - Attack: " + currentAttack + "/" + baseAttack + " (boosted: " + attackBoosted +
                "), Strength: " + currentStrength + "/" + baseStrength + " (boosted: " + strengthBoosted + ")");

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

                    // Wait for stats to update
                    Waiting.waitUntil(3000, () -> isStatsBoosted());
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

		String fourDosePotion = getCombatPotionType() + " potion(4)";
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
                || currentState == BotState.W330_RESTORATION) {
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

        // 1.9.84: when restoration is pending AND HP isn't critical
        // (<= INTERNAL_PANIC_TELE_HP = 25), skip BOTH combo eat AND
        // normal eat. User: 'we are eating a lot vs just double specing
        // and teleporting out.' Pre-1.9.84 the combo-eat path still
        // fired at HP <= 50 during the brief window between deciding
        // to restore and the state transition, costing 2-3 food items
        // per restoration cycle. The pool at the POH restores HP to
        // full, so any food eaten now is wasted. Only the true panic
        // threshold (HP <= 25 = one Corp hit from death) still triggers
        // emergency eating to survive the tele animation.
        if (currentHealth <= INTERNAL_PANIC_TELE_HP) {
            // Critical — eat regardless of restoration intent.
            emergencyComboEat();
        } else if (currentHealth <= INTERNAL_COMBO_EAT_HP && !restorationPending) {
            emergencyComboEat();
        } else if (!restorationPending && currentHealth <= eatHealthThreshold()) {
            normalEat();
        }

        if (!restorationPending && Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD) {
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
        return Inventory.getCount(settings.foodNames) < INTERNAL_MIN_FOOD_COUNT ||
                getPrayerDoses() < INTERNAL_MIN_PRAYER_DOSES ||
                (!hasChargedGamesNecklace() || !hasChargedRingOfDueling());
    }

    // ========== UTILITY METHODS ==========

    private boolean isAtCorp() {
        // Check if we're anywhere in the Corp area (lobby or boss room)
        return isInCorpLobby() || isInCorpBossRoom();
    }

    private boolean hasAcceptableTeammatesNearby() {
        return Query.players()
                .stream()
                .anyMatch(player -> settings.acceptableTeammates.contains(player.getName()));
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
        // 1.9.30: dropped the Corp-HP gate from this check. User report:
        // bot wouldn't fire its 4th Elder maul spec because Corp HP was
        // already below 1700, even though phase 1 (4 phase-1 specs)
        // wasn't done yet. Stat-reducing specs are valuable for the
        // melee finish regardless of Corp's current HP — the right
        // gate is "phase target met", which shouldSpecNowConsideringTeam
        // checks inside handleSpecialAttack. Spec firing here just needs
        // energy + in-combat + Corp present.
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
        if (pickSpecWeaponForCurrentPhase() == null) return false;
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
    private void processPendingSpecHit() {
        if (pendingHitWeapon == null) return;
        long nowXp = getMeleeCombatXp();
        if (nowXp > pendingHitXpBaseline) {
            recordSpecUsed(pendingHitWeapon);
            Log.info("Spec HIT confirmed via XP delta +"
                    + (nowXp - pendingHitXpBaseline) + " for " + pendingHitWeapon);
            pendingHitWeapon = null;

            // 1.9.17: phase rotation moved here from the in-line detector.
            // recordSpecUsed has now incremented the per-kill phase counter
            // (was previously deferred to this tick), so refreshSpecWeaponForPhase
            // sees the updated count and rotates correctly when the 4th
            // phase-1 hit confirms. Pre-1.9.17 the rotation ran BEFORE
            // recordSpecUsed (one tick early), seeing count=3 instead of 4,
            // and the bot stayed on Elder maul forever.
            String previous = chosenSpecWeapon;
            refreshSpecWeaponForPhase();
            if (chosenSpecWeapon != null && previous != null
                    && !chosenSpecWeapon.equals(previous)) {
                Log.info("Phase target met for " + previous
                        + " — rotating spec weapon to " + chosenSpecWeapon);
                // 1.9.35: only equip the rotated weapon if we still have
                // energy to fire its spec right now. Otherwise we'll hit
                // PREPARING_RESTORATION_CYCLE on the next tick — teleport
                // FIRST, equip on return (handleSpecialAttack equips at
                // line ~6216 when the spec weapon isn't on yet). Pre-1.9.35
                // we equipped the new weapon at 0% energy, immediately ran
                // a POH cycle, and if the host was offline the bot was
                // already stuck with the wrong weapon for the fallback
                // melee finish. User rule: "teleport before switching
                // weapons. as soon as our specs are dumped we insta tele
                // UNLESS we are going from our 2nd/3rd tier to our non
                // special weapons." Going to non-spec is the desired==null
                // case (handled by refreshSpecWeaponForPhase returning
                // false elsewhere), not this branch.
                if (Combat.getSpecialAttackPercent() >= getMinSpecEnergy()
                        && Inventory.contains(chosenSpecWeapon)) {
                    equipSpecWeapon();
                } else {
                    Log.info("Deferring " + chosenSpecWeapon
                            + " equip until after POH restoration (energy "
                            + Combat.getSpecialAttackPercent() + "%)");
                }
            }
        } else if (System.currentTimeMillis() > pendingHitDeadline) {
            Log.info("Spec MISSED (no XP delta after "
                    + HIT_CONFIRM_TIMEOUT_MS + "ms) for " + pendingHitWeapon
                    + " — not advancing phase counter");
            pendingHitWeapon = null;
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
        if (now - lastSpecActivateAt < SPEC_ACTIVATE_DEBOUNCE_MS) {
            // Recent activate — trust it, don't click again.
            return true;
        }
        if (Combat.isSpecialAttackEnabled()) {
            // Already on per the SDK; don't click, just record we trust it.
            lastSpecActivateAt = now;
            return true;
        }
        if (Combat.activateSpecialAttack()) { // 1.9.34.1: actual SDK call (was recursive)
            lastSpecActivateAt = now;
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
        return Collections.singletonList(chosen);
    }

    /**
     * Check if any acceptable main weapon is equipped
     */
    private boolean isMainWeaponEquipped() {
        for (String weaponName : getMainWeaponVariants()) {
            if (Equipment.contains(weaponName)) {
                Log.debug("Main weapon equipped: " + weaponName);
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
        if (maxCorpHpPercentThisKill <= 5.0) {
            // Bar not yet populated this kill — assume Corp is at full HP,
            // i.e. above threshold (allow spec).
            return true;
        }
        int approxHp = (int) ((healthPercent / 100.0) * 2000);
        return approxHp >= settings.corpMinHpForSpec;
	}

    /**
     * Withdraw our chosen spec weapon from bank
     */
    private boolean withdrawSpecWeapon() {
        if (chosenSpecWeapon == null) {
            detectAndSetSpecWeapon();
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
			Log.warn("No house tabs found - skipping restoration and going to combat");
			emergencyResetPOHSystem();
			currentState = BotState.WAITING_FOR_TEAM;
			return;
		}

		currentSpecialAttacksUsed = 0;
		isInRestorationPhase = true;
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

		// 🔥 USE ENERGY-BASED CONFIRMATION
		if (useSpecialAttackOnCorpPreActivated(corp)) {
			currentSpecialAttacksUsed++;
			// Phase C+D: record for coordinator with real BGS damage when applicable
			if ("Bandos godsword".equalsIgnoreCase(chosenSpecWeapon)) {
				Waiting.waitNormal(600, 200);
				int dmg = getMyLargestRecentHitOnCorp(corp);
				recordSpecUsed(chosenSpecWeapon, dmg);
				Log.info("BGS spec dealt ~" + dmg + " damage (recorded for team phase 3)");
			} else {
				recordSpecUsed(chosenSpecWeapon);
			}
			Log.info("Used special attack " + currentSpecialAttacksUsed + "/" + specsPerFullBar() +
					" - Current energy: " + Combat.getSpecialAttackPercent() + "%");
		} else {
			Log.warn("Failed to use special attack, continuing anyway");
			currentSpecialAttacksUsed++;
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

			if (!corp.interact("Attack")) {
				Log.warn("Failed to attack Corp with special");
				return false;
			}

			// 🔥 WAIT FOR ENERGY DROP
			boolean specExecuted = Waiting.waitUntil(5000, () -> {
				int currentEnergy = Combat.getSpecialAttackPercent();
				boolean energyDropped = currentEnergy < energyBefore;

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

		if (teleportToHouse()) {
			Log.info("Successfully teleported to house");
			currentHouseEntryAttempts = 0;
			// Own house: tab lands us inside, skip the friend's-house portal step.
			// Friend / bot-host: walk to the portal and authenticate / resolve host.
			currentState = isOwnHouseMode()
					? BotState.USING_ORNATE_POOL
					: BotState.ENTERING_FRIEND_HOUSE;
		} else {
			Log.error("Failed to teleport to house - ending restoration");
			emergencyResetPOHSystem();
			currentState = BotState.WAITING_FOR_TEAM;
		}
	}

	private void handleEnteringFriendHouse() {
		String hostName = getEffectiveFriendName();
		// 1.9.7: this method ran every main-loop tick (~50ms). Pre-1.9.7
		// every entry logged "Attempting to enter..." even when the throttle
		// blocked the actual attempt — 10+ identical log lines per second
		// of waiting. Move the log INSIDE the actual-attempt branch.

		if (hostName == null || hostName.trim().isEmpty()) {
			Log.error("No host name resolved (pohSource=" + getPohSource()
					+ ") - aborting restoration cycle");
			emergencyResetPOHSystem();
			currentState = BotState.WAITING_FOR_TEAM;
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
				Waiting.waitUntil(10000, () -> isAtCorp());
				currentState = BotState.WAITING_FOR_TEAM;
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

	private void handleUsingOrnatePool() {
		// Step 1: drink the pool if we haven't yet.
		boolean specFull = Combat.getSpecialAttackPercent() >= 100;
		if (!specFull) {
			Log.info("Using ornate pool for restoration");
			if (useOrnatePool()) {
				Log.info("Successfully used ornate pool (including 0.6s wait)");
				poolWaitStartedAt = 0; // reset wait timer for next time
				// 1.9.78: fresh dice rolls for the new restoration trip.
				resetSpecPreActivationRolls();
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
				Log.info("Holding at pool — waiting for teammates to refresh spec ("
						+ (waitedMs / 1000) + "s)");
				return;
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

		if (useOrnateJewelryBox()) {
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

			if (!corp.interact("Attack")) {
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

		if (!houseTabOpt.isPresent()) {
			Log.error("No 'Teleport to house' found in inventory!");
			return false;
		}

		InventoryItem houseTab = houseTabOpt.get();
		// Own house: use "Inside" so we land on the pool floor directly.
		// Friend / bot-host: use "Outside" so we can click the portal.
		boolean ownHouse = isOwnHouseMode();
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
		return Query.gameObjects()
				.filter(o -> o.getActions().contains("Drink"))
				.findFirst().isPresent();
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
			Log.error("No Portal with 'Friend's house' action found!");
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
		// handleFriendNameDialog ran — then handleFriendNameDialog saw
		// input populated, looked for shortcut, still not ready, fell
		// back to a doomed Enter. Just always wait for the shortcut. For
		// a previously-visited host (the usual case) it appears within
		// 1-2 game ticks of the dialog opening. Bumped to 8s because the
		// shortcut RENDERING is what we actually depend on; first-time
		// hosts (no prior visit) will fall through and use Enter, but
		// that's not the common case.
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
				// reliably submits even with the same "Timetoafk" already
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
		// matched text containing "timetoafk" — but that also matches the
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
		boolean hasBasicItems = hasAnyOwnedSpecWeapon() &&
				(Inventory.contains(RUNE_POUCH) || Inventory.contains(DIVINE_RUNE_POUCH)) &&
				hasChargedRingOfDueling() &&
				hasChargedGamesNecklace() &&
				Inventory.getCount(getCombatPotionNames()) >= INTERNAL_TARGET_SUPER_COMBAT &&
				Inventory.getCount(SUPER_RESTORE_NAMES) >= INTERNAL_TARGET_SUPER_RESTORES &&
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
			Optional<GameObject> poolOpt = Query.gameObjects()
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
		Optional<GameObject> jewelryBoxOpt = Query.gameObjects()
				.filter(o -> o.getActions().contains("Corporeal Beast"))
				.findFirst();

		if (!jewelryBoxOpt.isPresent()) {
			Log.error("No jewellery box with 'Corporeal Beast' action found");
			return false;
		}
		Log.info("Using " + jewelryBoxOpt.get().getName() + " to teleport to Corp...");

		if (jewelryBoxOpt.get().interact("Corporeal Beast")) {
			Log.info("Selected Corporeal Beast teleport, waiting for arrival...");
			return Waiting.waitUntil(10000, () -> isAtCorp());
		}

		Log.error("Failed to interact with jewellery box");
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
		// actions). The actual object names use varied word order
		// ("Ornate pool of Rejuvenation" vs "Ornate Jewellery Box") which
		// kept tripping the name-based match. Actions are stable across
		// tiers.
		return Query.gameObjects()
				.filter(o -> {
					java.util.List<String> actions = o.getActions();
					return actions.contains("Drink") || actions.contains("Corporeal Beast");
				})
				.findFirst().isPresent();
	}


	/**
	 * Check if we have house tabs (simplified - no need to count)
	 */
	private boolean hasHouseTeleportTab() {
		return Query.inventory().nameEquals("Teleport to house").findFirst().isPresent();
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
        public String friendName = "TimeToAFK";
        public List<String> acceptableTeammates = new ArrayList<>(Arrays.asList(
                "TimeToAFK", "RicoSuave32", "ahoyzfharem", "Nathan Lee", "In The Way"));
        // 1.8.8: no longer the primary loop driver — it's now a safety upper
        // bound. The real termination is phase targets met OR Corp HP <
        // corpMinHpForSpec. Bumped 3 → 10 so it never triggers in practice;
        // 10 cycles per kill is well above the realistic ceiling.
        public int totalRestorationCycles = 10;
        public int specialAttacksPerCycle = 2;

        // Combat
        public String mainWeapon = "Osmumten's fang (or)";
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

        // Spec
        public int minSpecEnergy = 50;
        // 1.8.8: this is the restoration-loop termination floor, not a per-spec
        // cooldown. Corp's stat reductions persist for the whole kill but its
        // HP regens, so the right time to stop dumping defense/attack-reducer
        // specs and join melee is when Corp's HP has already dropped (a real
        // teammate is actively damaging it). 1700 means "Corp lost ~15% HP →
        // stop spec dumping, start meleeing."
        public int corpMinHpForSpec = 1700;

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
        public int coordinatorStaleThresholdMs = 10_000;

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

        // ===== Multi-phase spec targets (Phase B) =====
        // Phase 1: total DWH+Elder maul specs across the bot team before moving on.
        public int phase1TargetSpecs = 4;
        // Phase 2: total Arclight+Darklight+Emberlight specs across the team.
        // Default 20 assumes Emberlight; with only Darklight, bump to 30-40.
        public int phase2TargetSpecs = 20;
        // Phase 3: total BGS damage drained across the team.
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

    /** Coordinator handles read/write of the shared team-state file. Peer model:
     *  every bot writes its own snapshot, every bot reads & aggregates the file. */
    private static class CorpCoordinator {
        private final java.io.File file;
        private final long staleThresholdMs;

        CorpCoordinator(java.io.File file, long staleThresholdMs) {
            this.file = file;
            this.staleThresholdMs = staleThresholdMs;
        }

        synchronized TeamState read() {
            if (!file.exists()) return new TeamState();
            try {
                String json = new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (json.trim().isEmpty()) return new TeamState();
                return new com.google.gson.Gson().fromJson(json, TeamState.class);
            } catch (Exception e) {
                Log.warn("Coordinator read failed: " + e.getMessage());
                return new TeamState();
            }
        }

        synchronized void publish(String accountName, AccountSnapshot snap, long killId, Set<String> liveBots) {
            TeamState state = read();
            if (state == null) state = new TeamState();
            if (state.accounts == null) state.accounts = new LinkedHashMap<>();

            // If kill_id advanced, reset everyone's per-kill counters.
            if (killId > state.killId) {
                state.killId = killId;
                state.killStartedAt = System.currentTimeMillis();
                for (AccountSnapshot a : state.accounts.values()) {
                    a.specsThisKill = new LinkedHashMap<>();
                    a.bgsDamageDealt = 0;
                }
            }

            snap.lastUpdate = System.currentTimeMillis();
            state.accounts.put(accountName, snap);

            // Garbage-collect entries that aren't in our bot-teammate list anymore.
            if (liveBots != null && !liveBots.isEmpty()) {
                state.accounts.keySet().retainAll(liveBots);
            }

            String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(state);
            java.io.File tmp = new java.io.File(file.getParentFile(), file.getName() + ".tmp");
            try {
                java.nio.file.Files.write(tmp.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Log.warn("Coordinator publish failed: " + e.getMessage());
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

        private final com.google.gson.Gson gson = new com.google.gson.Gson();
        private volatile boolean running = true;

        CorpPortCoordinator(boolean isHost, String hostIp, int portId,
                            long staleThresholdMs, CorpCoordinator fileBackup) {
            this.isHost = isHost;
            this.hostIp = hostIp;
            this.port = 45000 + portId;
            this.staleThresholdMs = staleThresholdMs;
            this.fileBackup = fileBackup;
            start();
        }

        private void start() {
            if (isHost) startHost();
            else startClient();
        }

        private void startHost() {
            Thread t = new Thread(() -> {
                try {
                    serverSocket = new java.net.ServerSocket(port);
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
                                synchronized (stateLock) {
                                    if (latestState.accounts == null) {
                                        latestState.accounts = new LinkedHashMap<>();
                                    }
                                    if (msg.killId > latestState.killId) {
                                        latestState.killId = msg.killId;
                                        latestState.killStartedAt = System.currentTimeMillis();
                                        for (AccountSnapshot a : latestState.accounts.values()) {
                                            a.specsThisKill = new LinkedHashMap<>();
                                            a.bgsDamageDealt = 0;
                                        }
                                    }
                                    msg.snapshot.lastUpdate = System.currentTimeMillis();
                                    latestState.accounts.put(msg.name, msg.snapshot);
                                }
                                broadcastState();
                            }
                        } catch (Exception e) {
                            Log.warn("[Coord/HOST] parse error: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    // disconnect — normal
                } finally {
                    clientSockets.remove(sock);
                    try { sock.close(); } catch (Exception ignored) {}
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
            // Mirror to file backup so a host restart can recover state.
            if (fileBackup != null) {
                try {
                    synchronized (stateLock) {
                        for (Map.Entry<String, AccountSnapshot> e : latestState.accounts.entrySet()) {
                            fileBackup.publish(e.getKey(), e.getValue(),
                                    latestState.killId, latestState.accounts.keySet());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        private void startClient() {
            Thread t = new Thread(() -> {
                while (running) {
                    try {
                        clientSocket = new java.net.Socket(hostIp, port);
                        clientWriter = new java.io.BufferedWriter(
                                new java.io.OutputStreamWriter(clientSocket.getOutputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        clientReader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(clientSocket.getInputStream(),
                                        java.nio.charset.StandardCharsets.UTF_8));
                        Log.info("[Coord/CLIENT] Connected to " + hostIp + ":" + port);
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
                                + " — reconnecting in 5s");
                    }
                    // Close and back off before reconnect.
                    try { if (clientSocket != null) clientSocket.close(); } catch (Exception ignored) {}
                    clientWriter = null;
                    clientReader = null;
                    try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
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

        synchronized void publish(String accountName, AccountSnapshot snap,
                                  long killId, Set<String> liveBots) {
            snap.lastUpdate = System.currentTimeMillis();
            if (isHost) {
                // Host: write directly into local state, then broadcast.
                synchronized (stateLock) {
                    if (latestState.accounts == null) {
                        latestState.accounts = new LinkedHashMap<>();
                    }
                    if (killId > latestState.killId) {
                        latestState.killId = killId;
                        latestState.killStartedAt = System.currentTimeMillis();
                        for (AccountSnapshot a : latestState.accounts.values()) {
                            a.specsThisKill = new LinkedHashMap<>();
                            a.bgsDamageDealt = 0;
                        }
                    }
                    latestState.accounts.put(accountName, snap);
                    if (liveBots != null && !liveBots.isEmpty()) {
                        latestState.accounts.keySet().retainAll(liveBots);
                    }
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
    private long localKillId = 0;
    private int coordTickCounter = 0;
    private final AccountSnapshot mySnapshot = new AccountSnapshot();

    private void ensureCoordinator() {
        if (coordinator != null) return;
        try {
            java.io.File dir = ScriptSettings.getDefault().getDirectory();
            if (!dir.exists()) dir.mkdirs();
            coordinator = new CorpCoordinator(new java.io.File(dir, "corp_team_state.json"),
                    INTERNAL_COORD_STALE_THRESHOLD_MS);
            // 1.9.76: also spin up port coordinator if enabled.
            if (settings.useCoordinatorPort && portCoordinator == null) {
                try {
                    portCoordinator = new CorpPortCoordinator(
                            settings.coordinatorIsHost,
                            settings.coordinatorHostIp,
                            settings.coordinatorPortId,
                            INTERNAL_COORD_STALE_THRESHOLD_MS,
                            settings.coordinatorIsHost ? coordinator : null);
                    Log.info("Port coordinator started: "
                            + (settings.coordinatorIsHost ? "HOST" : "CLIENT")
                            + " on " + settings.coordinatorHostIp
                            + ":" + (45000 + settings.coordinatorPortId));
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
        if (!settings.coordinatorEnabled) return;
        ensureCoordinator();
        if (coordinator == null) return;

        coordTickCounter++;
        if (coordTickCounter < INTERNAL_COORD_WRITE_INTERVAL_TICKS) return;
        coordTickCounter = 0;

        String name = MyPlayer.getUsername();
        if (name == null || name.isEmpty()) return;

        // Fresh snapshot of what we look like right now.
        mySnapshot.specPct = MyPlayer.getCurrentHealthPercent() >= 0 ? Combat.getSpecialAttackPercent() : 0;
        mySnapshot.botState = currentState == null ? "UNKNOWN" : currentState.name();
        mySnapshot.isPohHost = settings.isPohHost;
        mySnapshot.availableWeapons = new ArrayList<>(getOwnedSpecWeapons());
        // specsThisKill and bgsDamageDealt are updated by Phase C wiring; we just publish.

        Set<String> live = new HashSet<>();
        if (settings.botTeammates != null) live.addAll(settings.botTeammates);
        live.add(name);  // always include ourselves

        // 1.9.76: publish to port coordinator (if active), file always.
        if (portCoordinator != null) {
            try { portCoordinator.publish(name, mySnapshot, localKillId, live); }
            catch (Exception e) { Log.warn("Port publish failed: " + e.getMessage()); }
        }
        coordinator.publish(name, mySnapshot, localKillId, live);
    }

    /** Read team aggregate. Returns null if disabled or unavailable. */
    private TeamAggregate coordinatorAggregate() {
        if (!settings.coordinatorEnabled) return null;
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

    /** Call when our bot confirms a Corp kill, so the local kill id advances and
     *  per-kill counters reset. Triggers `kill_id` bump on next publish. */
    private void coordinatorOnKillEnded() {
        localKillId++;
        killCount++; // overlay counter
        mySnapshot.specsThisKill = new LinkedHashMap<>();
        mySnapshot.bgsDamageDealt = 0;
        mySnapshot.claimedCorpOffset = null;  // release positional claim for next kill
        committedSpecPhase = 0; // 1.9.37: clear per-kill ratchet
        maxRealCountThisKill = 0; // 1.9.38: clear per-kill realCount ratchet
    }

    // ========== SESSION-END SIGNALING (1.7.1) ==========

    /** Local flag set when we either originated a session-end signal or
     *  observed one from a teammate. handleLooting() reads this and routes
     *  to EMERGENCY_ESCAPE after the current kill instead of starting a new one. */
    private boolean sessionEndPending = false;

    // ========== STATUS OVERLAY (1.7.2) ==========

    private StatusOverlay overlay;
    private int killCount = 0;
    private int deathCount = 0;
    private long scriptStartTime = 0;

    /** Small always-on-top Swing window showing live bot status. Runs on the
     *  EDT; the main loop calls update() each tick — cheap because we only
     *  flush label text when values change. */
    private static class StatusOverlay {
        private final JFrame frame;
        private final JLabel stateLabel = new JLabel("State: ?");
        private final JLabel weaponLabel = new JLabel("Spec weapon: ?");
        private final JLabel specCountsLabel = new JLabel("Specs this kill: -"); // 1.9.39
        private final JLabel killsLabel = new JLabel("Kills: 0");
        private final JLabel deathsLabel = new JLabel("Deaths: 0");
        private final JLabel runtimeLabel = new JLabel("Runtime: 0:00");
        private final JLabel coordLabel = new JLabel("Coordinator: off");
        private final JLabel phaseLabel = new JLabel("Phase needed: -");
        private final JLabel sessionLabel = new JLabel("Session end: no");

        StatusOverlay() {
            frame = new JFrame("Corp");
            frame.setAlwaysOnTop(true);
            frame.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
            JPanel panel = new JPanel(new GridLayout(0, 1, 2, 2));
            panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            panel.add(stateLabel);
            panel.add(weaponLabel);
            panel.add(specCountsLabel);
            panel.add(killsLabel);
            panel.add(deathsLabel);
            panel.add(runtimeLabel);
            panel.add(coordLabel);
            panel.add(phaseLabel);
            panel.add(sessionLabel);
            frame.add(panel);
            frame.pack();
            frame.setLocation(20, 20);
            frame.setVisible(true);
        }

        void update(String state, String weapon, String specCounts,
                    int kills, int deaths, long runtimeMs,
                    boolean coordEnabled, int phaseNeeded, boolean sessionEnd) {
            SwingUtilities.invokeLater(() -> {
                stateLabel.setText("State: " + state);
                weaponLabel.setText("Spec weapon: " + (weapon == null ? "-" : weapon));
                specCountsLabel.setText("Specs this kill: "
                        + (specCounts == null || specCounts.isEmpty() ? "-" : specCounts));
                killsLabel.setText("Kills: " + kills);
                deathsLabel.setText("Deaths: " + deaths);
                long sec = runtimeMs / 1000;
                runtimeLabel.setText(String.format("Runtime: %d:%02d:%02d",
                        sec / 3600, (sec % 3600) / 60, sec % 60));
                coordLabel.setText("Coordinator: " + (coordEnabled ? "on" : "off"));
                phaseLabel.setText("Phase needed: "
                        + (phaseNeeded == 0 ? "done"
                          : phaseNeeded == -1 ? "-"
                          : String.valueOf(phaseNeeded)));
                sessionLabel.setText("Session end: " + (sessionEnd ? "PENDING" : "no"));
            });
        }

        void close() {
            try { SwingUtilities.invokeLater(() -> frame.dispose()); } catch (Exception ignored) {}
        }
    }

    private void overlayInit() {
        if (overlay != null || !settings.showOverlay) return;
        try { SwingUtilities.invokeAndWait(() -> overlay = new StatusOverlay()); }
        catch (Exception e) { Log.warn("Overlay init failed: " + e.getMessage()); }
    }

    private void overlayUpdate() {
        if (overlay == null || !settings.showOverlay) return;
        int phaseNeeded = -1;
        try {
            // 1.9.39: always show phase, not just when coordinator on —
            // teamPhaseNeeded works solo via buildSoloAggregate. The
            // coordinator gate was a leftover from when phase rotation
            // was coordinator-only.
            phaseNeeded = teamPhaseNeeded();
        } catch (Exception ignored) {}
        overlay.update(
                currentState == null ? "?" : currentState.name(),
                chosenSpecWeapon,
                formatSpecCountsThisKill(), // 1.9.39
                killCount,
                deathCount,
                System.currentTimeMillis() - scriptStartTime,
                settings.coordinatorEnabled,
                phaseNeeded,
                sessionEndPending
        );
    }

    /** 1.9.39: compact "weapon=count, weapon=count" string for the overlay.
     *  Reads mySnapshot.specsThisKill (per-kill, cleared in coordinatorOnKillEnded).
     *  User flagged: bot kept Arclighting for 10+ minutes — counter on the
     *  overlay will make it obvious whether specs are being RECORDED or
     *  whether the phase target itself is unreachable. */
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
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void overlayClose() {
        if (overlay != null) overlay.close();
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
    private boolean isUnderCorp(Npc corp) {
        if (corp == null) return false;
        try {
            WorldTile myPos = MyPlayer.getTile();
            if (myPos == null) return false;
            Area corpArea = corp.getArea();
            return corpArea != null && corpArea.contains(myPos);
        } catch (Exception e) { return false; }
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

            // 2) Minimap click — bypasses pathfinder start-tile checks.
            try {
                if (candidate.clickOnMinimap()) {
                    if (waitForOutsideCorp(corpArea, 2000)) return true;
                }
            } catch (Exception ignored) {}

            // 3) On-screen tile click — last resort, only if visible.
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
        TeamState ts = coordinator.read();
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
     *  TimeToAFK walks behind a wall, multiplier goes 2x → 1x, phase1Specs
     *  agg drops back below target, "natural" phase regresses 2 → 1, bot
     *  re-equips Elder maul mid-Arclight-rotation. Reset in
     *  coordinatorOnKillEnded(). */
    private int committedSpecPhase = 0;

    /** 1.9.38: per-kill ratchet on the real-teammate count. Once we've
     *  seen N real teammates nearby this kill, we keep treating "team
     *  output" as if N were still present, even if the SDK loses sight
     *  of them later. Without this ratchet a teammate stepping behind
     *  Corp regresses the multiplier 2x → 1x, doubling the effective
     *  spec target for the rest of the kill (10 Arclight → 20). Reset
     *  in coordinatorOnKillEnded(). */
    private int maxRealCountThisKill = 0;

    private int teamPhaseNeeded() {
        // Base aggregate: coordinator if enabled, otherwise just our own snapshot.
        TeamAggregate agg;
        if (settings.coordinatorEnabled) {
            agg = coordinatorAggregate();
            if (agg == null) agg = new TeamAggregate();
        } else {
            agg = buildSoloAggregate();
        }

        // Real-teammate multiplier: assume each visible human partner contributes
        // roughly the same per-kill output as the bot. Approximation, but fixes
        // the "1 bot + 1 human stays on Phase 1 forever" trap.
        // 1.9.38: ratchet on max realCount seen this kill (see field comment).
        int realCount = countRealTeammatesNearby();
        if (realCount > maxRealCountThisKill) maxRealCountThisKill = realCount;
        int effectiveRealCount = maxRealCountThisKill;
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
            mySnapshot.bgsDamageDealt += (actualBgsDamage >= 0 ? actualBgsDamage : 30);
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
                JPanel specP = new JPanel(new GridLayout(0, 2, 4, 4));
                specP.setBorder(BorderFactory.createTitledBorder("Spec"));
                specP.add(new JLabel("Spec only if Corp HP >="));specP.add(corpMinHp);
                specP.add(new JLabel("Restoration cycles per trip:")); specP.add(restoreCycles);
                specP.add(new JLabel("Dark core strategy:")); specP.add(legacyDarkCore);
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

                JPanel teamP = new JPanel(new BorderLayout(4, 4));
                teamP.setBorder(BorderFactory.createTitledBorder("POH / Team"));

                JPanel teamTop = new JPanel(new GridLayout(0, 2, 4, 4));
                teamTop.add(new JLabel("PoH source:"));        teamTop.add(pohSource);
                teamTop.add(new JLabel("Friend's RSN:"));      teamTop.add(friendName);
                teamTop.add(new JLabel("POH host role:"));     teamTop.add(isPohHost);
                teamTop.add(new JLabel("Coordinator:"));       teamTop.add(coordEnabled);
                teamTop.add(new JLabel("Coordinator wait:"));  teamTop.add(waitForTeammateSpec);
                teamTop.add(new JLabel("W330 return world (0 = remember):")); teamTop.add(designatedWorld);
                teamTop.add(new JLabel("W330 max host tries:")); teamTop.add(w330MaxHostAttempts);

                JPanel teamLists = new JPanel(new GridLayout(1, 2, 6, 6));
                JPanel acceptablePanel = new JPanel(new BorderLayout());
                acceptablePanel.setBorder(BorderFactory.createTitledBorder("Acceptable teammates (one RSN per line)"));
                acceptablePanel.add(new JScrollPane(teammates), BorderLayout.CENTER);
                JPanel botListPanel = new JPanel(new BorderLayout());
                botListPanel.setBorder(BorderFactory.createTitledBorder("Bot teammate RSNs (coordinator filter)"));
                botListPanel.add(new JScrollPane(botList), BorderLayout.CENTER);
                teamLists.add(acceptablePanel);
                teamLists.add(botListPanel);

                teamP.add(teamTop, BorderLayout.NORTH);
                teamP.add(teamLists, BorderLayout.CENTER);
                tabs.addTab("POH / Team", teamP);

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
                };
                Runnable collect = () -> {
                    Object selectedWeapon = mainWeapon.getSelectedItem();
                    settings.mainWeapon = selectedWeapon == null
                            ? "Osmumten's fang"
                            : selectedWeapon.toString().trim();
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
                    settings.useLegacyDarkCoreLogic = legacyDarkCore.isSelected();
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