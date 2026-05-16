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
    // Weapon switching tracking
    private boolean specWeaponSwitchQueued = false;
    private long specWeaponSwitchTime = 0;
    private boolean needsToSwitchBackFromSpec = false;
    private boolean specWeaponReadyForUse = false; // NEW: Track if spec weapon is ready
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
        if (!w.getActions().contains("Cast")) return false;
        String raw = w.getText().orElse("");
        String clean = raw.replaceAll("<[^>]*>", "").trim();
        if (!clean.toLowerCase().contains("vengeance")) return false;
        if (clean.toLowerCase().contains("other")) return false;
        return true;
    }

    private void castVengeanceWidget(int childIndex) {
        // Open magic tab
        GameTab.MAGIC.open();
        Waiting.waitUntil(2000, () -> GameTab.MAGIC.isOpen());


        // Find and click the widget
        Optional<Widget> vengeanceWidget = Query.widgets()
                .inRoots(218)
                .filter(w -> w.getIndexPath().length >= 2 && w.getIndexPath()[1] == childIndex)
                .filter(this::isVengeanceSelfWidget)
                .isVisible()
                .findFirst();

        if (vengeanceWidget.isPresent()) {
            Log.info("Clicking Vengeance widget at [218, " + childIndex + "]");
            vengeanceWidget.get().click("Cast");
            Waiting.waitUntil(3000, () -> !MyPlayer.isAnimating());
        } else {
            Log.warn("Vengeance widget not found at [218, " + childIndex + "]");
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

        boolean equipped = Equipment.contains(chosenSpecWeapon);
        if (equipped) {
            Log.debug("Chosen spec weapon equipped: " + chosenSpecWeapon);
        }
        return equipped;
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

		Optional<InventoryItem> specWeaponOpt = Query.inventory().nameEquals(chosenSpecWeapon).findFirst();
		if (specWeaponOpt.isPresent()) {
			InventoryItem specWeapon = specWeaponOpt.get();
			if (specWeapon.click("Wield")) {
				boolean success = Waiting.waitUntil(3000, () -> isSpecWeaponEquipped());
				if (success) {
					Log.info("Successfully equipped spec weapon: " + chosenSpecWeapon);
				} else {
					Log.error("Failed to equip spec weapon: " + chosenSpecWeapon);
				}
				return success;
			}
		}

		Log.error("Spec weapon not found in inventory: " + chosenSpecWeapon);
		return false;
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

        // Step 2: Use pool to restore health/prayer/spec if needed
        if (needsPoolRestoration()) {
            if (useRestorePool()) {
                Log.info("Successfully used restoration pool");
            } else {
                Log.warn("Failed to use pool, continuing to banking");
            }
        }

        // Step 3: Bank for supplies
        if (!isNearFeroxBank()) {
            // Walk to bank area in Ferox Enclave
            walkToFeroxBank();
            return;
        }

        // Step 4: Open bank
        if (!Bank.isOpen()) {
            // Try to find and left-click bank chest specifically
            Optional<GameObject> bankChestOpt = Query.gameObjects()
                    .nameContains("Bank chest")
                    .findFirst();

            if (bankChestOpt.isPresent()) {
                GameObject bankChest = bankChestOpt.get();
                Log.info("Left-clicking bank chest");
                if (bankChest.interact("Use") || bankChest.interact("Bank")) {
                    if (!Waiting.waitUntil(6000, () -> Bank.isOpen())) {
                        Log.error("Failed to open bank via bank chest");
                        return;
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

        // Handle vengeance logic while waiting
        handleVengeanceLogic();

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

        // STAY IN LOBBY - wait for team
        Log.info("No acceptable teammates found, staying in lobby...");
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

        // Make sure we're in boss room
        if (!isInCorpBossRoom()) {
            Log.info("Moving from lobby to boss room");
            if (moveToCorpBossRoom()) {
                Waiting.waitUntil(5000, () -> isInCorpBossRoom());
            }
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
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (!corpOpt.isPresent()) return;

        Npc corp = corpOpt.get();

        // Check if Corp is targeting us
        if (isCorpTargetingUs(corp)) {
            if (!Prayer.PROTECT_FROM_MELEE.isEnabled()) {
                Log.info("Corp targeting us - activating Protect from Melee");
                Prayer.PROTECT_FROM_MELEE.enable();
            }
        } else {
            // Corp not targeting us, can use offensive prayers
            if (!Prayer.PROTECT_FROM_MAGIC.isEnabled()) {
                Log.info("Corp targeting us - activating Protect from Melee");
                Prayer.PROTECT_FROM_MAGIC.enable();
            }
        }
    }

    private void handleFightingCorp() {
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

        // 1.8.7: detect a pre-activated spec that just fired in-line.
        // prepareSpecWeaponForCorp pre-activates spec before the bot
        // enters combat. The first attack consumes that spec, but it
        // happens outside the USING_SPECIAL_ATTACK state so the normal
        // switch-back path never runs. Notice the energy drop + spec
        // button now off + we have the spec weapon equipped → queue
        // the switch-back so we don't stay on the spec weapon all kill.
        if (specWeaponReadyForUse
                && !Combat.isSpecialAttackEnabled()
                && isSpecWeaponEquipped()
                && Combat.getSpecialAttackPercent() < 100) {
            Log.info("Pre-activated spec fired in-line — queueing switch back to main");
            // Record the spec we used so the team-phase aggregator advances.
            if (chosenSpecWeapon != null) recordSpecUsed(chosenSpecWeapon);
            specWeaponReadyForUse = false;
            queueSpecWeaponSwitchBack();
        }

        // 🔥 PRE-ACTIVATE SPECIAL ATTACK IF CONDITIONS MET
		if (shouldUseSpecialAttack() && !Combat.isSpecialAttackEnabled()) {
			Log.info("Special attack conditions met - PRE-ACTIVATING for next attack");
			if (Combat.activateSpecialAttack()) {
				Log.info("Special attack pre-activated in main combat loop");
			}
		}

		// Use special attack when available
		if (shouldUseSpecialAttack()) {
			currentState = BotState.USING_SPECIAL_ATTACK;
			return;
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

            // Re-engage if not in combat
            if (!isPlayerInCombat()) {
                corp.interact("Attack");
            }

            // Check if Corp is dead
            if (!corp.isHealthBarVisible() && !isPlayerInCombat()) {
                Log.info("Corp appears to be dead, looking for loot");
                currentState = BotState.LOOTING;
            }
        } else {
            // Corp might be dead
            if (corpWasAliveLastCheck) {
                // Corp was alive but now gone - queue prayer deactivation
                queuePrayerDeactivation();
                corpWasAliveLastCheck = false;
            }

            Log.info("Corp not found, looking for loot");
            currentState = BotState.LOOTING;
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
        // PRIORITY 1: keep ourselves alive — same eat/drink guards as legacy.
        int currentHealth = MyPlayer.getCurrentHealth();
        if (currentHealth <= INTERNAL_EMERGENCY_HP) {
            Log.warn("CRITICAL HEALTH during dark core - emergency combo eating!");
            emergencyComboEatDuringMovement();
        } else if (currentHealth <= eatHealthThreshold() + 20) {
            emergencyComboEatDuringMovement();
        }
        if (Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD + 10) {
            drinkPrayerPotionDuringMovement();
        }

        // If the core is gone, swap back to Fang and resume Corp.
        Optional<Npc> coreOpt = findDarkCore();
        if (!coreOpt.isPresent()) {
            Log.info("Dark core gone - re-equipping main weapon and resuming Corp");
            equipMainWeaponFast();
            currentState = BotState.FIGHTING_CORP;
            return;
        }
        Npc core = coreOpt.get();
        darkCoreLastSeen = System.currentTimeMillis();

        // PRIORITY 2: have a kill weapon ready. Elder maul preferred, DWH fallback.
        if (!isCoreKillWeaponEquipped()) {
            if (!equipCoreKillWeapon()) {
                Log.warn("Dark core: no Elder maul / DWH available - falling back to legacy dodge");
                handleAdvancedDarkCoreLegacy();
                return;
            }
            return; // gear swap costs a tick; reassess next tick.
        }

        // PRIORITY 3: am I the bot the core landed on? Adjacency check.
        WorldTile myPos = MyPlayer.getTile();
        if (myPos == null) return;
        double dist = myPos.distanceTo(core.getTile());

        if (dist > 1.5) {
            Log.debug("Dark core present but not on me (dist=" + dist + ") - holding kill weapon");
            return;
        }

        // PRIORITY 4: attack the core, then step away so it jumps mid-air.
        Log.info("Dark core adjacent (dist=" + dist + ") - attacking with kill weapon");
        if (core.interact("Attack")) {
            Waiting.waitNormal(350, 120);
            stepAwayFromCore(core);
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

        for (int[] o : offsets) {
            if (o[0] == 0 && o[1] == 0) continue;
            WorldTile target = new WorldTile(myPos.getX() + o[0], myPos.getY() + o[1], myPos.getPlane());
            if (corpArea != null && corpArea.contains(target)) continue; // inside Corp = stomp damage
            if (!isTileWalkable(target)) continue;

            Log.info("STEP-AWAY: moving to " + target);
            try {
                if (LocalWalking.walkTo(target)) return true;
            } catch (Exception ignored) {}
            try {
                if (target.clickOnMinimap()) return true;
            } catch (Exception ignored) {}
        }
        Log.warn("STEP-AWAY: no walkable target away from core");
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

                Optional<GameObject> portal = Query.gameObjects()
                        .nameEquals("Portal")
                        .filter(p -> p.interact("Friend's house"))
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
                String poolName = settings.poolName == null || settings.poolName.isEmpty()
                        ? "Ornate rejuvenation pool" : settings.poolName;
                if (Query.gameObjects().nameEquals(poolName).isAny()) {
                    Log.info("W330: " + w330CurrentHost + " has " + poolName + " - proceeding");
                    w330Step = W330Step.USE_POOL;
                    return;
                }
                Log.warn("W330: " + w330CurrentHost + " missing " + poolName + " - trying another host");
                exitRandomHouse();
                w330Step = W330Step.ENTER_HOUSE;
                return;

            case USE_POOL:
                if (useOrnatePool()) {
                    w330Step = W330Step.TELE_TO_CORP;
                }
                return;

            case TELE_TO_CORP:
                // Prefer the host's ornate jewellery box (single click to Corp).
                String boxName = settings.jewelleryBoxName == null || settings.jewelleryBoxName.isEmpty()
                        ? "Ornate jewellery box" : settings.jewelleryBoxName;
                if (Query.gameObjects().nameEquals(boxName).isAny()) {
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

        // Get all player positions (including non-teammates to avoid all stacking)
        List<WorldTile> allPlayerPositions = Query.players()
                .stream()
                .filter(player -> !player.getName().equals(MyPlayer.getUsername()))
                .map(Player::getTile)
                .collect(Collectors.toList());

        // 1.8.8: score = separation-from-others MINUS distance-from-self.
        // Pre-1.8.8 only considered separation, so when teammates weren't
        // nearby (team still in lobby) every candidate tied at MAX_VALUE and
        // the FIRST safe offset in CORP_POSITION_OFFSETS order won. With offset
        // list [W, E, S, N], that biased toward East regardless of where the
        // player actually was — picking East when approaching from the NW
        // means the path-finder routes the player UNDER Corp. Weighting by
        // self-distance picks the closest safe tile to the player, which is
        // almost always the natural side to approach from.
        WorldTile bestPosition = null;
        double bestScore = -Double.MAX_VALUE;

        for (WorldTile position : dynamicPositions) {
            double minDistanceToPlayer = allPlayerPositions.stream()
                    .mapToDouble(playerPos -> playerPos.distanceTo(position))
                    .min()
                    .orElse(Double.MAX_VALUE);
            double selfDistance = myPos == null ? 0 : myPos.distanceTo(position);
            // Cap separation so it can't run away with the score: beyond
            // ~6 tiles, more separation doesn't actually help — we just want
            // "not stacked on top of someone".
            double separationScore = Math.min(minDistanceToPlayer, 6.0);
            double score = separationScore - selfDistance;

            if (score > bestScore) {
                bestScore = score;
                bestPosition = position;
            }
        }

        if (bestPosition == null) {
            bestPosition = dynamicPositions.get(0); // safety net
        }

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
        // TODO: This would need to detect if teammates are specifically in boss room
        // For now, check if they're nearby and Corp is present
        if (!isInCorpBossRoom()) {
            return false;
        }

        return Query.players()
                .stream()
                .anyMatch(player -> settings.acceptableTeammates.contains(player.getName()));
    }

    /**
     * Check if acceptable teammates are in the lobby
     */
    private boolean hasAcceptableTeammatesInLobby() {
        if (!isInCorpLobby()) {
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
        try {
            Log.info("Casting Vengeance using widget method...");

            // Use the working widget-based method with index 142
            castVengeanceWidget(142);

            // Update tracking variables
            lastVengeanceCast = System.currentTimeMillis();  // CHANGED: was lastVengeanceCastTime
            hasUsedVengeanceThisTrip = true;
            vengeanceQueued = false;

            Log.info("Vengeance cast successfully via widget method");
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
        if (Inventory.contains(RUNE_POUCH) || Inventory.contains(DIVINE_RUNE_POUCH)) {
            return true;
        }
        return Inventory.contains("Astral rune")
                && Inventory.contains("Death rune")
                && Inventory.contains("Earth rune");
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

        // Spellbook gate: if useVengeance is on but the player isn't on Lunars,
        // the cast widget won't exist and we'd spam silent failures. Detect once,
        // warn once, then skip vengeance for the rest of the session.
        if (!isOnLunarSpellbook()) {
            if (!spellbookWarningLogged) {
                Log.warn("useVengeance is ON but Vengeance widget is not present — "
                        + "are you on Lunars? Skipping vengeance for the rest of this session.");
                spellbookWarningLogged = true;
            }
            return;
        }

        // Rune-pouch gate: we can't introspect pouch contents, but a missing
        // pouch + no loose runes is a definite cast-will-fail signal. One-shot
        // warning, then keep checking each tick (so re-banking with a pouch
        // re-enables casts mid-session).
        if (!hasVengeanceRunes()) {
            if (!runePouchWarningLogged) {
                Log.warn("useVengeance is ON but no Rune pouch (or loose Vengeance runes) in "
                        + "inventory — vengeance casts will fail. Skipping.");
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

        // Additional check: don't cast if we recently cast and no damage taken yet
        boolean recentlyCastWithoutDamage = hasRecentVengeanceCastWithoutDamage();

        if (canCastNow && canCastVengeance() && !recentlyCastWithoutDamage) {
            if (castVengeance()) {
                Log.info("Cast first vengeance (ready state) - will protect until first damage taken");
            }
        } else if (recentlyCastWithoutDamage) {
            Log.debug("Skipping vengeance cast - recently cast and no damage taken yet");
        }
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
        if (sharkOpt.isPresent()) {
            sharkOpt.get().click("Eat");
            Log.info("Emergency: Ate Shark while moving");
        }

        // Immediately eat karambwan (no delay)
        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent()) {
            karambwanOpt.get().click("Eat");
            Log.info("Emergency: Ate Karambwan while moving");
            return true;
        }

        return false;
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
			if (LocalWalking.walkTo(claimed)) {
				return Waiting.waitUntil(5000, () ->
						MyPlayer.getTile().distanceTo(claimed) <= 2);
			}
		}

		WorldTile myPos = MyPlayer.getTile();
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

		if (bestPosition != null) {
			if (LocalWalking.walkTo(bestPosition)) {
				Log.info("Moving to safe Corp position: " + bestPosition);
				return Waiting.waitUntil(5000, () ->
						MyPlayer.getTile().distanceTo(bestPosition) <= 2);
			}
		}
		return false;
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

		if (!isCorpHealthAboveSpecThreshold(corp)) {
			Log.info("Corp health too low for special attack, switching to normal weapon");
			if (specWeaponReadyForUse) {
				queueSpecWeaponSwitchBack();
				specWeaponReadyForUse = false;
			}
			currentState = BotState.FIGHTING_CORP;
			return;
		}

		// Phase D: rotate to the right spec weapon for the team's current phase.
		if (settings.coordinatorEnabled && !refreshSpecWeaponForPhase()) {
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

		if (!Combat.isSpecialAttackEnabled()) {
			Log.warn("Special attack not enabled - activating now as backup");
			if (!Combat.activateSpecialAttack()) {
				Log.error("Failed to activate special attack as backup");
				currentState = BotState.FIGHTING_CORP;
				return;
			}
		}

		// 🔥 ENERGY-BASED SPECIAL ATTACK EXECUTION
		int specsPerformed = 0;
		int maxSpecs = Combat.getSpecialAttackPercent() >= 100 ? 2 : 1;

		while (specsPerformed < maxSpecs && Combat.getSpecialAttackPercent() >= getMinSpecEnergy()) {
			int energyBefore = Combat.getSpecialAttackPercent();
			Log.info("Spec " + (specsPerformed + 1) + " - Energy before: " + energyBefore + "%");

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
					// Phase D: for BGS, capture the actual hitsplat damage rather than the +30 approximation.
					if ("Bandos godsword".equalsIgnoreCase(chosenSpecWeapon)) {
						Waiting.waitNormal(600, 200); // let hitsplat register
						int dmg = getMyLargestRecentHitOnCorp(corp);
						recordSpecUsed(chosenSpecWeapon, dmg);
						Log.info("BGS spec dealt ~" + dmg + " damage (recorded for team phase 3)");
					} else {
						recordSpecUsed(chosenSpecWeapon);
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
		queueSpecWeaponSwitchBack();
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
        // contribution attributed to this account. The pre-1.8.8 model was
        // per-TRIP, which meant a single bank trip's restoration cycles got
        // distributed across N kills; user wants every kill to be able to
        // dump a full multi-cycle spec rotation.
        resetRestorationTracking();

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
        return MyPlayer.getCurrentHealth() < Skill.HITPOINTS.getActualLevel() ||
                Prayer.getPrayerPoints() < Skill.PRAYER.getActualLevel() ||
                Combat.getSpecialAttackPercent() < 100;
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
				// Only check health and prayer restoration
				boolean restored = Waiting.waitUntil(10000, () -> {
					boolean healthRestored = MyPlayer.getCurrentHealth() >= Skill.HITPOINTS.getActualLevel();
					boolean prayerRestored = Prayer.getPrayerPoints() >= Skill.PRAYER.getActualLevel();
					return healthRestored && prayerRestored;
				});

				if (restored) {
					Log.info("Health and prayer restored at Ferox pool");
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
                    Waiting.waitUntil(1000, () -> true); // Max 1 second but could be shorter
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
        Log.info("Walking to Ferox Enclave bank...");

        // Try to find bank chest on screen first
        Optional<GameObject> bankChestOpt = Query.gameObjects()
                .nameContains("Bank chest")
                .findFirst();

        if (bankChestOpt.isPresent()) {
            Log.info("Bank chest already visible");
            return;
        }

        // If not visible, walk to approximate bank location
        WorldTile bankLocation = new WorldTile(3150, 3625, 0); // Adjust coordinates as needed

        if (LocalWalking.walkTo(bankLocation)) {
            Log.info("Walking to bank area");
            // Wait briefly for arrival
            Waiting.waitUntil(3000, () ->
                    Query.gameObjects().nameContains("Bank chest").findFirst().isPresent());
        } else {
            Log.warn("Failed to walk to bank location");
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
		// When Corp's health bar isn't visible, no one is attacking Corp yet,
		// so it's at full HP — assume above floor. The visible-bar check
		// inside isCorpHealthAboveSpecThreshold returns false in that case,
		// which would wrongly block pre-engagement restoration.
		boolean corpHealthAboveFloor;
		if (!corpPresent) {
			corpHealthAboveFloor = false;
		} else {
			Npc corp = corpOpt.get();
			corpHealthAboveFloor = !corp.isHealthBarVisible()
					|| isCorpHealthAboveSpecThreshold(corp);
		}
		boolean safetyCap = currentRestorationCycle < settings.totalRestorationCycles;
		boolean hasHouseTabs = hasHouseTeleportTab();

		if (!hasHouseTabs) {
			Log.info("No house tabs available - skipping POH restoration");
			return false;
		}

		return corpPresent
				&& specDepleted
				&& phaseTargetsNotMet
				&& corpHealthAboveFloor
				&& safetyCap
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
        return true;
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
        List<String> foodTasks = Arrays.asList("karambwans", "sharks");

        // Randomize food withdrawal order
        Collections.shuffle(foodTasks);

        boolean firstFood = true;

        for (String foodType : foodTasks) {
            if (foodType.equals("karambwans")) {
                int amount = firstFood ? 10 : 0; // 10 first, then withdraw-all
                if (!withdrawKarambwans(amount)) return false;
            } else {
                int amount = firstFood ? 10 : 0; // 10 first, then withdraw-all
                if (!withdrawSharks(amount)) return false;
            }

            firstFood = false; // Next iteration will be second food

            // Random delay between food types
            Waiting.waitUniform(400, 900);
        }

        return true;
    }

    // ========== UPDATED PREPARE SPEC WEAPON METHOD ==========
	private void prepareSpecWeaponForCorp(Npc corp) {
		if (chosenSpecWeapon == null) {
			detectAndSetSpecWeapon();
		}

		Log.info("prepareSpecWeaponForCorp called - Corp alive: " + isCorpAlive(corp) +
				", Spec energy: " + Combat.getSpecialAttackPercent() +
				", Chosen spec weapon: " + chosenSpecWeapon);

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
						if (Combat.activateSpecialAttack()) {
							Log.info("Special attack pre-activated successfully!");
						} else {
							Log.warn("Failed to pre-activate special attack");
						}
					}
				}
			} else {
				Log.info("Chosen spec weapon already equipped: " + chosenSpecWeapon);
				specWeaponReadyForUse = true;

				// Pre-activate if not already active
				if (!Combat.isSpecialAttackEnabled()) {
					Log.info("PRE-ACTIVATING special attack - weapon ready");
					if (Combat.activateSpecialAttack()) {
						Log.info("Special attack pre-activated successfully!");
					}
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
        int currentHealth = MyPlayer.getCurrentHealth();

        // Emergency combo eating for critically low health
        if (currentHealth <= INTERNAL_EMERGENCY_HP) {
            emergencyComboEat();
        }
        // Normal eating for regular health management
        else if (currentHealth <= eatHealthThreshold()) {
            normalEat();
        }

        if (Prayer.getPrayerPoints() <= INTERNAL_DRINK_PRAYER_THRESHOLD) {
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

        // Step 1: Eat shark first
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        if (sharkOpt.isPresent() && sharkOpt.get().click("Eat")) {
            ateShark = true;
            Log.info("Emergency: Ate Shark");

            // Brief delay before karambwan (combo eating timing)
            Waiting.waitUniform(100, 300);
        }

        // Step 2: Eat karambwan immediately after
        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
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
        // Priority 1: Try to eat shark
        Optional<InventoryItem> sharkOpt = Query.inventory().nameEquals("Shark").findFirst();
        if (sharkOpt.isPresent() && sharkOpt.get().click("Eat")) {
            Log.info("Ate Shark (normal)");
            return waitForHealthIncrease();
        }

        // Priority 2: Fallback to karambwan if no sharks
        Optional<InventoryItem> karambwanOpt = Query.inventory().nameEquals("Cooked karambwan").findFirst();
        if (karambwanOpt.isPresent() && karambwanOpt.get().click("Eat")) {
            Log.info("Ate Karambwan (fallback - no sharks)");
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

        // Original conditions
        boolean originalEmergency = MyPlayer.getCurrentHealth() <= INTERNAL_EMERGENCY_HP ||
                (isAtCorp() && !hasAcceptableTeammatesWithGracePeriod() && isPlayerInCombat()) ||
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

        // Check if Corp health is above threshold
        Optional<Npc> corpOpt = Query.npcs().nameEquals(CORPOREAL_BEAST).findFirst();
        if (corpOpt.isPresent()) {
            return isCorpHealthAboveSpecThreshold(corpOpt.get());
        }

        return false;
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
			Log.info("Switching back to main weapon before house teleport");
			equipMainWeaponFast();
			currentState = BotState.TELEPORTING_TO_HOUSE;
			return;
		}

		// Phase D: rotate to the right spec weapon for current team phase.
		if (settings.coordinatorEnabled && !refreshSpecWeaponForPhase()) {
			Log.info("No usable spec weapon for current team phase — skipping initial spec cycle.");
			equipMainWeaponFast();
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

			if (!Combat.isSpecialAttackEnabled()) {
				Log.info("PRE-ACTIVATING special attack for POH restoration cycle");
				Combat.activateSpecialAttack();
			}
		} else {
			if (!Combat.isSpecialAttackEnabled()) {
				Log.info("PRE-ACTIVATING special attack for POH cycle");
				Combat.activateSpecialAttack();
			}
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
		Log.info("Attempting to enter " + hostName + "'s house (mode=" + getPohSource() + ")");

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
			emergencyResetPOHSystem();
			currentState = BotState.WAITING_FOR_TEAM;
			return;
		}

		long timeSinceLastAttempt = System.currentTimeMillis() - lastHouseEntryAttempt;
		if (timeSinceLastAttempt < HOUSE_ENTRY_RETRY_DELAY_MIN) {
			Log.info("Waiting before next house entry attempt...");
			return;
		}

		currentHouseEntryAttempts++;
		lastHouseEntryAttempt = System.currentTimeMillis();

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
			Log.info("Completed restoration cycle " + currentRestorationCycle + "/" + settings.totalRestorationCycles);

			if (currentRestorationCycle >= settings.totalRestorationCycles) {
				Log.info("All restoration cycles completed, proceeding to normal combat");
				isInRestorationPhase = false;
				currentState = BotState.WAITING_FOR_TEAM;
			} else {
				Log.info("Starting next restoration cycle");
				currentState = BotState.PREPARING_RESTORATION_CYCLE;
			}
		} else {
			Log.error("Failed to teleport back to Corp - ending restoration");
			emergencyResetPOHSystem();
			currentState = BotState.EMERGENCY_ESCAPE;
		}
	}

	private boolean useSpecialAttackOnCorp(Npc corp) {
		try {
			if (!Combat.activateSpecialAttack()) {
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

	/** True if we're inside our own house — heuristic is the configured
	 *  rejuvenation pool being visible (no friend-portal walk needed). */
	private boolean isInOwnHouse() {
		return Query.gameObjects().nameEquals(settings.poolName).findFirst().isPresent();
	}

	/**
	 * Simple portal interaction for friend's house
	 */
	private boolean enterFriendHouse() {
		String hostName = getEffectiveFriendName();
		Log.info("Attempting to enter " + hostName + "'s house via portal...");

		// Look for Portal with "Friend's house" action
		Optional<GameObject> portalOpt = Query.gameObjects()
				.nameEquals("Portal")
				.filter(portal -> portal.interact("Friend's house"))
				.findFirst();

		if (!portalOpt.isPresent()) {
			Log.error("No Portal with 'Friend's house' action found!");
			return false;
		}

		GameObject portal = portalOpt.get();
		if (portal.interact("Friend's house")) {
			Log.info("Clicked 'Friend's house', waiting for dialog...");

			// Wait for dialog to appear
			if (Waiting.waitUntil(5000, () -> Chatbox.isOpen())) {
				return handleFriendNameDialog();
			}
		}

		Log.error("Failed to interact with portal");
		return false;
	}

	/**
	 * Simple dialog handling for friend name input
	 */
	private boolean handleFriendNameDialog() {
		String hostName = getEffectiveFriendName();
		Log.info("Dialog appeared for host=" + hostName + ", checking widget shortcut first...");

		// Shortcut widget for the previously-visited friend lives at [162, 39, 0]
		// and displays "Last name: <rsn>" (lowercase). We match case-insensitively
		// and strip color tags so styled text doesn't break the comparison.
		final String hostLower = hostName == null ? "" : hostName.toLowerCase();
		Optional<Widget> friendWidgetOpt = Query.widgets()
				.inRoots(162)
				.filter(w -> w.getIndexPath().length >= 3 &&
						w.getIndexPath()[1] == 39 &&
						w.getIndexPath()[2] == 0)
				.filter(w -> {
					String raw = w.getText().orElse("");
					String clean = raw.replaceAll("<[^>]*>", "").toLowerCase();
					return clean.contains(hostLower);
				})
				.findFirst();

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
		Log.info("No widget shortcut found, typing host name: " + hostName);

		try {
			// Type host's name (void method)
			Keyboard.typeString(hostName);
            Waiting.waitUntil(2000, () -> true); // Brief wait for typing

			// Press Enter (void method)
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
		String poolName = settings.poolName == null || settings.poolName.trim().isEmpty()
				? "Ornate rejuvenation pool" : settings.poolName.trim();
		Log.info("Using " + poolName + "...");

		Optional<GameObject> poolOpt = Query.gameObjects()
				.nameEquals(poolName)
				.findFirst();

		if (!poolOpt.isPresent()) {
			Log.error(poolName + " not found!");
			return false;
		}

		GameObject pool = poolOpt.get();
		if (pool.interact("Drink")) {
			Log.info("Used ornate pool, waiting for restoration...");

			// Wait for restoration
			boolean restored = Waiting.waitUntil(10000, () -> {
				boolean specRestored = Combat.getSpecialAttackPercent() >= 100;
				boolean healthRestored = MyPlayer.getCurrentHealth() >= Skill.HITPOINTS.getActualLevel();
				boolean prayerRestored = Prayer.getPrayerPoints() >= Skill.PRAYER.getActualLevel();
				return specRestored && healthRestored && prayerRestored;
			});

			if (restored) {
				Log.info("Successfully restored at ornate pool");
				// Wait additional 0.6 seconds before proceeding to jewelry box
				Waiting.waitUniform(600, 600);
				return true;
			} else {
				Log.warn("Pool restoration timed out, but continuing");
				Waiting.waitUniform(600, 600); // Still wait the 0.6 seconds
				return true;
			}
		}

		Log.error("Failed to interact with ornate pool");
		return false;
	}

	/**
	 * Simple jewelry box interaction
	 */
	private boolean useOrnateJewelryBox() {
		String boxName = settings.jewelleryBoxName == null || settings.jewelleryBoxName.trim().isEmpty()
				? "Ornate jewellery box" : settings.jewelleryBoxName.trim();
		Log.info("Using " + boxName + " to teleport to Corp...");

		Optional<GameObject> jewelryBoxOpt = Query.gameObjects()
				.nameEquals(boxName)
				.findFirst();

		if (!jewelryBoxOpt.isPresent()) {
			Log.error(boxName + " not found!");
			return false;
		}

		GameObject jewelryBox = jewelryBoxOpt.get();
		if (jewelryBox.interact("Corporeal Beast")) {
			Log.info("Selected Corporeal Beast teleport, waiting for arrival...");
			return Waiting.waitUntil(10000, () -> isAtCorp());
		}

		Log.error("Failed to interact with " + boxName);
		return false;
	}


	/**
	 * Check if we're at the house portal (after teleporting "Outside")
	 */
	private boolean isAtHousePortal() {
		// Look for Portal with "Friend's house" action (this is where we teleport to when using "Outside")
		return Query.gameObjects()
				.nameEquals("Portal")
				.filter(portal -> portal.interact("Friend's house"))
				.findFirst()
				.isPresent();
	}

	/**
	 * Check if we're in friend's house (simplified)
	 */
	private boolean isInFriendHouse() {
		String poolName = settings.poolName == null || settings.poolName.trim().isEmpty()
				? "Ornate rejuvenation pool" : settings.poolName.trim();
		return Query.gameObjects().nameEquals(poolName).findFirst().isPresent();
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
        c.put("Arclight", 25);
        c.put("Darklight", 25);
        c.put("Emberlight", 25);
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

        // poolName: name of the rejuvenation pool object in the house we're using.
        // jewelleryBoxName: name of the jewellery-box object used to tele back to Corp.
        public String poolName = "Ornate rejuvenation pool";
        public String jewelleryBoxName = "Ornate jewellery box";

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

    // Runtime coordinator state (only used when settings.coordinatorEnabled).
    private CorpCoordinator coordinator;
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

        coordinator.publish(name, mySnapshot, localKillId, live);
    }

    /** Read team aggregate. Returns null if disabled or unavailable. */
    private TeamAggregate coordinatorAggregate() {
        if (!settings.coordinatorEnabled) return null;
        ensureCoordinator();
        if (coordinator == null) return null;
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

        void update(String state, String weapon, int kills, int deaths, long runtimeMs,
                    boolean coordEnabled, int phaseNeeded, boolean sessionEnd) {
            SwingUtilities.invokeLater(() -> {
                stateLabel.setText("State: " + state);
                weaponLabel.setText("Spec weapon: " + (weapon == null ? "-" : weapon));
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
            if (settings.coordinatorEnabled) phaseNeeded = teamPhaseNeeded();
        } catch (Exception ignored) {}
        overlay.update(
                currentState == null ? "?" : currentState.name(),
                chosenSpecWeapon,
                killCount,
                deathCount,
                System.currentTimeMillis() - scriptStartTime,
                settings.coordinatorEnabled,
                phaseNeeded,
                sessionEndPending
        );
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

        for (WorldTile candidate : candidates) {
            Log.warn("STOMP DEFENSE: stepping off to " + candidate);

            // 1) Try LocalWalking first (fastest when it works).
            try {
                if (isTileWalkable(candidate) && LocalWalking.walkTo(candidate)) {
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
        int realCount = countRealTeammatesNearby();
        if (realCount > 0) {
            int multiplier = 1 + realCount;
            agg.phase1Specs *= multiplier;
            agg.phase2Specs *= multiplier;
            agg.phase3BgsDamage *= multiplier;
        }

        if (agg.phase1Specs < INTERNAL_PHASE1_TARGET) return 1;
        if (agg.phase2Specs < INTERNAL_PHASE2_TARGET) return 2;
        if (agg.phase3BgsDamage < INTERNAL_PHASE3_BGS_DAMAGE) return 3;
        return 0;
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

    /** Phase D: pick the highest-priority spec weapon I own for the team's
     *  currently-needed phase. Returns null if no usable weapon for this phase
     *  (in which case the bot should fall through to DPS). */
    private String pickSpecWeaponForCurrentPhase() {
        int phase = teamPhaseNeeded();
        if (phase == 0) return null;
        // Priority within each phase, best-to-worst
        String[][] preference = {
                null,
                { "Elder maul", "Dragon warhammer" },           // Phase 1 (defense)
                { "Emberlight", "Arclight", "Darklight" },      // Phase 2 (combat levels)
                { "Bandos godsword" }                           // Phase 3 (damage drain)
        };
        if (phase >= preference.length || preference[phase] == null) return null;
        List<String> owned = getOwnedSpecWeapons();
        for (String w : preference[phase]) {
            if (!owned.contains(w)) continue;
            if (Inventory.contains(new String[]{ w })) return w;
        }
        return null;
    }

    /** Phase D: refresh chosenSpecWeapon based on the team's current phase needs.
     *  If coordinator says we need Phase 2 and we currently have Elder maul (Phase 1)
     *  set, switch to our best Phase 2 weapon. Returns true if a usable weapon was
     *  picked; false if we have nothing for the current phase. */
    private boolean refreshSpecWeaponForPhase() {
        if (!settings.coordinatorEnabled) return chosenSpecWeapon != null;
        String desired = pickSpecWeaponForCurrentPhase();
        if (desired == null) {
            chosenSpecWeapon = null;
            return false;
        }
        if (!desired.equals(chosenSpecWeapon)) {
            Log.info("Phase D: switching spec weapon " + chosenSpecWeapon + " -> " + desired);
            chosenSpecWeapon = desired;
            // The equipSpecWeapon() call later in the spec flow will handle the swap.
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
                JTextField poolName = new JTextField(settings.poolName, 22);
                JTextField jewelleryBoxName = new JTextField(settings.jewelleryBoxName, 22);
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
                teamTop.add(new JLabel("Pool object name:"));  teamTop.add(poolName);
                teamTop.add(new JLabel("Jewellery box name:"));teamTop.add(jewelleryBoxName);
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
                    poolName.setText(settings.poolName);
                    jewelleryBoxName.setText(settings.jewelleryBoxName);
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
                    settings.poolName = poolName.getText().trim();
                    settings.jewelleryBoxName = jewelleryBoxName.getText().trim();
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