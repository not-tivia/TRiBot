# Corporeal Beast — Strategy & Feature Roadmap

Research compiled from OSRS Wiki, RuneNation, TRiBot community guides, RPGStash, and
OSRSMoneyMaking guides (2026). Use this as the master reference when expanding
`Corp.java` features and GUI options.

---

## 1. Core Mechanics (relevant to any bot)

- **Location**: Corporeal Beast Cave (north of Edgeville, near KBD). Cave interior is
  NOT wilderness, but the cave entrance is at wilderness level ~21.
- **Size**: 5×5 NPC.
- **HP**: 2000.
- **Stomp**: If you stand on Corp's tile, takes 30–51 damage every 7 ticks (4.2s).
  Unblockable by prayer.
- **Damage reduction**: **50% reduction against any non-corpbane weapon**, plus the
  weapon must be on the **stab** attack style. Magic deals full damage but is wildly
  inaccurate. Cannonballs deal full damage.
- **Dark Energy Core**: ~1/8 chance to spawn on hits of 32+. Steals HP/prayer from
  adjacent players, returns to Corp and heals it. Killing it midair (e.g., with cannon)
  prevents respawn for the rest of the kill.
- **Vengeance**: Damage reflected back via Vengeance ignores Corp's 50% reduction.
- **Entry**: Games necklace ("Corporeal Beast"), or walk via Edgeville → wilderness
  obelisks/lever.

---

## 2. Corpbane Weapons (full damage when on stab)

| Weapon | Hand | Notes |
|---|---|---|
| **Osmumten's fang** | 1h | Best DPS, ToA reward, +105 stab |
| **Zamorakian spear** | 2h | OG corpbane, +85 stab |
| **Crystal halberd** | 2h | Special hits multiple times on large NPCs |
| **Dragon halberd** | 2h | Cheaper crystal halberd alternative |
| **Dragon spear / 3rd age spear** | 2h | Other corpbane options |
| **Cannonballs** | n/a | Full damage, mid-air core kills |
| **Magic spells** | n/a | Full damage but very inaccurate |

**Important non-corpbane trap**: `Zamorakian hasta` does NOT deal full damage to Corp,
despite same base stats as the spear. The hasta is one-handed (better with shield in
most other PVM), but the spear is the corpbane variant.

---

## 3. Stat-Drain Spec Rotation (current meta)

Order matters because each spec drains Corp's defense progressively.

### Modern meta (Elder maul + Emberlight)
1. **Elder maul** — 3-4 specs. -35% current defense per hit.
2. **Emberlight** (Arclight upgrade) — 19-20 specs. Reduces Att/Str/Def by 5% of
   total level per hit. Most efficient defense-reducer.
3. **Bandos godsword (BGS)** — until 200 damage drained.
4. Main weapon (Fang / Spear) for kill phase.

### Legacy meta (DWH route)
1. **Dragon warhammer (DWH)** — 3-4 specs. -30% current defense per hit.
2. **Arclight / Darklight** — 4-6 specs. Same -5% mechanic as Emberlight, weaker.
3. **BGS** — 300-500 damage of halberd/spear in between, then BGS to 200.
4. Main weapon.

### Spec switching detail
- Each spec costs 50% (DWH/Elder maul/BGS) or 25% (Darklight/Arclight) or 60% (crystal
  halberd) special attack energy.
- Restoration loop is required to hit the rotation cleanly within one kill.

---

## 4. Builds & Group Sizes

### Solo
- **Min stats**: 75 Atk, 82 HP, 37 Prayer.
- **Full Spec Out** (RuneNation): Elder maul → Emberlight → BGS → Fang.
- **No-food Korasi/Fang ironman**: 7.5-8 KPH, no Ely/Spectral needed.
- **Crystal halberd ironman**: spec-heavy, multi-hit DPS substitute.
- **Vengeance specialist**: spell + Lunar spellbook swap, thralls, runs between
  phases to maintain vengeance uptime.

### Duo
- Both bring spec rotation. Karambwans recommended for combo eating.
- Cooked karambwans MANDATORY for sub-3.

### Trio
- Same as duo but drop cannonballs + Arclight if not needed.

### Mass (5+)
- Less prep per player. Everyone specs in fixed order: DWH → Arclight → BGS.
- 3-4 DWH specs total across team before kill phase.
- Some clans ban strength armour (DHL, Inquisitor's, etc.) and infernal cape — use
  Masori + fire cape if so.
- Public masses run on specific worlds (e.g. W384 traditionally, may have moved).

---

## 5. Ironman / Cannonball Method

- **Dwarf multicannon** at Corp, steel cannonballs from drops sustain ammo.
- Cannon's primary value: **kill Dark Energy Core in mid-air** to prevent re-spawn.
- Caveat: a current bug causes cannon hits to apply Corp's 50% damage reduction
  TWICE — net 25% effective damage. Verify before relying on it heavily.

---

## 6. Restoration Locations

| Location | Restores | Travel | Cost |
|---|---|---|---|
| **POH Ornate pool** | HP, prayer, run, **spec** | House tab / Friend's POH | Construction + furniture |
| **POH Restoration pool** | Same as above | Same | Lower con req |
| **Ferox Enclave Pool of Refreshment** | HP, stats, prayer, run | Ring of dueling | Free |
| **Resurrection altar** | Prayer only | POH | Construction |

**Key**: Ferox doesn't restore **spec attack** — useless for spec-rotation methods.
You need a POH ornate or restoration pool (yours or a teammate's) to maintain the
DWH/Elder maul/BGS rotation across kills.

The existing Corp.java assumes a **friend's POH** with an ornate pool — that's the
right architecture for the spec-rotation meta. The script's `TELEPORT_TO_HOUSE` →
`USE_ORNATE_POOL` → `TELEPORT_BACK_TO_CORP` cycle is the standard pattern.

---

## 7. Loot Decisions

**Always loot:**
- Spectral sigil (1/512 base)
- Arcane sigil (1/1024)
- Elysian sigil (1/4096)
- Holy elixir
- Spirit shield

**Optional / context-dependent:**
- Steel cannonballs (always-drop, free for cannon ironmen)
- Mystic robes
- Bones (1)
- Coal, runite ore (small drops)
- Charms (alch / ignore)

**Skip:**
- Common runes, low coins, anything below threshold price.

---

## 8. Inventory Variations by Build

| Build | Sharks | Karambs | Super restore | Super combat | Spec weapon | Notes |
|---|---|---|---|---|---|---|
| **Solo full-spec** | 8-12 | 6-8 | 2-3 | 1 | Elder maul + Emberlight + BGS | POH restoration |
| **Mass member** | 6-8 | 0 | 1-2 | 1 | DWH + Arclight + BGS | Less food, team carries |
| **Ironman cannon** | 12-16 | 8-10 | 2-3 | 1 | Crystal halberd | Cannon takes 4 slots |
| **Vengeance specialist** | 6-8 | 4-6 | 2 | 1 | Fang only | Rune pouch needed |

---

## 9. Mapping → Corp.java GUI Tabs

Based on the above, here's a proposed full GUI structure to support all these methods.
What's already in `CorpSettings` is marked ✅; new fields are 🆕.

### Mode tab 🆕
- **Group size**: Solo / Duo / Trio / Mass (dropdown)
- **Build profile**: Full Spec / Ironman Cannon / Vengeance / Mass Member (dropdown)
- **Spec rotation**: Elder maul meta / DWH legacy / Custom (dropdown)
- **Combat mode**: Stat drain + kill / No-spec direct / Cannon-assist

### Weapons tab
- **Main weapon** ✅ (already a text field)
- **Stat-drain weapon 1** 🆕: Elder maul / DWH / None
- **Stat-drain weapon 2** 🆕: Emberlight / Arclight / Darklight / None
- **Finishing spec weapon** 🆕: BGS / None
- **Cannon enabled** 🆕: checkbox
- **Cannon for cores only** 🆕: checkbox (vs. continuous attack)

### Restoration tab
- **Restoration location** 🆕: Friend's POH / Personal POH / Ferox (no spec restore!)
- **Friend's RSN** ✅
- **Restoration cycles** ✅
- **Specs per cycle** ✅
- **Use Ferox for HP/prayer between trips** 🆕: checkbox (separate from spec restoration)

### Food/Prayer tab
- **Primary food** ✅
- **Secondary food (combo)** ✅
- **Use Karambwan combo eating** 🆕: checkbox
- **Eat threshold** ✅
- **Emergency HP** ✅
- **Prayer threshold** ✅
- **Antipoison drink** 🆕: checkbox (Corp doesn't poison but PKers en route can)
- **Vengeance** 🆕: checkbox + on cooldown radio buttons

### Team tab
- **Acceptable teammates** ✅
- **Wait for team before entering** 🆕: checkbox
- **Min team size to start** 🆕: spinner

### Loot tab
- **Always loot list** 🆕: textarea (sigils, holy elixir, etc.)
- **Min price** 🆕: spinner
- **Drop trash threshold** 🆕: spinner (existing pattern from aLooter)
- **Loot cannonballs** 🆕: checkbox (ironman flag)

### Safety tab
- **Stop on death** 🆕: checkbox
- **Tele if HP <** 🆕: spinner (max-hit-aware, default 44)
- **Wilderness escape on PKer (en route to cave)** 🆕: checkbox

### Tuning tab (advanced)
- **Vengeance cooldown min/max ms**
- **State timeouts (short/combat/banking/travel)**
- **Camera angle min**
- **Core dodge distances (danger/emergency/min/max dodge)**
- **Safe distances (core/teammates/Corp area)**

---

## 10. Implementation Phasing

Suggested order to expand features without breaking the existing working logic:

1. **Phase A** (small): Add the four "essential method-defining" settings to CorpSettings:
   - `groupSize` (enum), `buildProfile` (enum), `specRotation` (enum), `restorationLocation` (enum)
   - Wire them to the Mode tab. Default values reproduce current behavior (Mass, Full-Spec,
     Elder-maul-meta, Friend POH).

2. **Phase B** (medium): Stat-drain weapon configurability.
   - Add `statDrainWeapon1`, `statDrainWeapon2`, `finishSpecWeapon` strings + GUI.
   - Refactor the spec-routing code (`detectAndSetSpecWeapon`, `equipSpecWeapon`, etc.)
     to read from these instead of hardcoded ELDER_MAUL / DARKLIGHT.

3. **Phase C** (medium): Cannon support.
   - Add cannon setup/repair/reload states (similar to cannonalcher).
   - Add `useCannon` + `cannonForCoresOnly` settings.
   - Reuse the script's existing cannon logic if any, or port the cannonalcher's.

4. **Phase D** (small): Vengeance toggle.
   - Already has vengeance logic — just expose the on/off to GUI.

5. **Phase E** (small): Loot tab.
   - Existing `valuableLoot` is set up as a CorpSettings field. Add min-price + drop-trash
     fields and the looter-style logic from aLooter MainSwing.

6. **Phase F** (small): Safety tab.
   - `panicTeleHpThreshold`, `escapeOnWildernessPker`, `stopOnDeath`.
   - Reuse panic-escape pattern from aBoner2.

7. **Phase G** (medium): Tuning tab.
   - Move the camera, core-dodge, and state-timeout constants into CorpSettings.
   - These are advanced/scary fields — keep defaults sensible, only surface in the GUI
     for users who know what they're doing.

---

## 11. References

- [OSRS Wiki — Corporeal Beast Strategies](https://oldschool.runescape.wiki/w/Corporeal_Beast/Strategies)
- [OSRS Wiki — Corporeal Beast](https://oldschool.runescape.wiki/w/Corporeal_Beast)
- [OSRS Wiki — Crystal halberd](https://oldschool.runescape.wiki/w/Crystal_halberd)
- [OSRS Wiki — Zamorakian hasta](https://oldschool.runescape.wiki/w/Zamorakian_hasta)
- [OSRS Wiki — Zamorakian spear](https://oldschool.runescape.wiki/w/Zamorakian_spear)
- [OSRS Wiki — Ferox Enclave](https://oldschool.runescape.wiki/w/Ferox_Enclave)
- [OSRS Wiki — Pool of Refreshment](https://oldschool.runescape.wiki/w/Pool_of_Refreshment)
- [OSRS Wiki — Restoration pool](https://oldschool.runescape.wiki/w/Restoration_pool)
- [RuneNation — Corporeal Beast Solo Strategy Guide](https://runenation.org/runescapeguides/osrs/osrs-pvm/10506-corporeal-beast-solo-guide-osrs-pvm-strategy)
- [OSRSMoneyMaking — Corp BGS vs DWH](https://osrsmoneymaking.guide/news/osrs-bgs-or-dwh-which-weapon-should-you-choose/)
- [OSRSMoneyMaking — Corp Solo 2025](https://osrsmoneymaking.guide/news/osrs-corp-solo-guide-master-the-corporeal-beast-solo-in-2025-2/)
- [Theoatrix — Solo Corp Guide (almost 0 damage)](https://www.theoatrix.net/Guides/dc01c66f-2267-4f78-984d-31e4ad96240c)
- [TRiBot Community — BEG Corporeal Beast script reference](https://community.tribot.org/index.php?/topic/115-beg-corporeal-beast-soloteams-ironman-houses-configurable-ge-restock-muling/)

---

## 12. Open Strategy Questions for Discussion

Things the research surfaced that we should decide before implementing:

1. **Spec rotation order**: should we hardcode the Elder-maul-meta order, or let the
   user define a rotation in the GUI (drag-to-reorder list of {weapon, spec count}
   pairs)? Hardcoded is simpler; drag-list is flexible.
2. **Cannon mid-air core kill**: the SDK's `Combat` API doesn't expose cannon control
   directly — we'd need `org.tribot.script.sdk` cannon helpers, or interact with the
   cannon as a `GameObject`. Worth investigating before committing.
3. **Ferox POH-pool dichotomy**: Ferox is faster for HP/prayer-only restoration; POH
   is needed for spec restoration. Should the script auto-select per-cycle, or run
   one mode for the whole session?
4. **Public mass world detection**: should the bot try to hop to known mass worlds
   (e.g., player count check on World 384) when running mass-member mode?
5. **Anti-PK / wilderness escape**: the cave entrance is wilderness-adjacent. The
   existing script has no PK escape logic — should we add one (reuse aBoner2 pattern)?
