# TRiBot Modernization Fundamentals

Reference for porting legacy TRiBot scripts (`org.tribot.api`, `api2007`, `ABCUtil`) to the
current SDK (`org.tribot.script.sdk.*`). Captures the gotchas hit and patterns confirmed
across the progressiveCrafter2, progressiveFletcher3, and aBowStringer2 rewrites.

---

## 1. Verified vs. Documented

**The published Dokka docs at `docs.tribot.org/tribotsdk/` describe a different SDK version
than the one shipped in your gradle cache.** Multiple methods listed in the docs do not
exist in the jar (e.g. `Bank.depositAllExcept`). Treat the docs as a starting hint, but
always confirm against the jar bytecode before relying on a method:

```bash
# Locate the jar
find ~/.gradle/caches -name 'tribot-script-sdk-*.jar' | head -1

# Dump method names with `javap` (if JDK installed) or with Python:
python3 - <<'PY'  # paste the jar path
import struct
# ... parses .class files from the jar ...
PY
```

If a method name lookup fails to compile and the docs say it exists, **the jar is the truth**.

---

## 2. Script Skeleton

```java
package scripts.myscript;

import org.tribot.script.sdk.script.TribotScript;
import org.tribot.script.sdk.script.TribotScriptManifest;

@TribotScriptManifest(
    name = "MyScript",
    author = "you",
    category = "Crafting",
    description = "..."
)
public class Main implements TribotScript {
    private volatile boolean running = true;

    @Override
    public void execute(final String args) {
        Antiban.setScriptAiAntibanEnabled(true);
        while (running) {
            try { tick(); } catch (Exception e) { Log.error(e.getMessage()); }
            Waiting.waitNormal(350, 120);
        }
    }
}
```

`execute(String args)` is the entry. There's no separate `onStart()` / `run()` lifecycle —
do everything inside `execute()`.

---

## 3. The Array-vs-Varargs Trap

Most SDK collection-accepting methods take `String[]`/`int[]`, **not** Java varargs.
Calling `Bank.depositAllExcept("Needle", "Thread")` will fail to compile:

```
symbol: method depositAllExcept(String,String)
```

Always wrap:

```java
private static final String[] KNIFE_A = { "Knife" };
Bank.contains(KNIFE_A);
Inventory.contains(KNIFE_A);
Query.inventory().nameEquals(KNIFE_A).findFirst();
MakeScreen.makeAll(new int[]{ itemId });
```

Methods this applies to (verified in 1.0.71):
- `Bank.contains(String[])`, `Bank.getCount(String[])`, `Bank.depositAllExcept(String[])` *(if it ever returns)*, `Bank.contains(int[])`
- `Inventory.contains(String[])`, `Inventory.contains(int[])`, `Inventory.getCount(String[])`
- `Query.inventory().nameEquals(String[])`, `.nameContains(String[])`, `.idEquals(int[])`
- `MakeScreen.contains(String[]/int[])`, `.makeAll(String[]/int[])`, `.make(...)`
- `ChatScreen.containsMessage(String[])`, `.handle(String[])`, `.selectOption(String[])`
- `Widgets.get(int[])` — the path-as-array, e.g. `Widgets.get(new int[]{ 270, 14 })`

Single-string overloads that **do** exist (don't need wrapping):
- `Bank.deposit(String, int)`, `Bank.depositAll(String)`, `Bank.withdraw(String, int)`, `Bank.withdrawAll(String)`

---

## 4. Banking

```java
// Open / close
if (!Bank.ensureOpen()) GlobalWalking.walkToBank();
Bank.close();

// Withdraw exactly 1 of a tool (avoids withdrawAll pulling a whole stack)
Bank.withdraw("Knife", 1);
Waiting.waitUntil(1500, () -> Inventory.contains(KNIFE_A));
if (!Inventory.contains(KNIFE_A)) return;  // bail before withdrawing bulk

// Then bulk material
Bank.withdrawAll("Yew logs");

// Selective deposit (since Bank.depositAllExcept may not exist):
// iterate inventory, deposit anything not in the keep set.
while (true) {
    Optional<InventoryItem> next = Query.inventory().stream()
        .filter(i -> !KEEP_NAME_1.equals(i.getName()) && !KEEP_NAME_2.equals(i.getName()))
        .findFirst();
    if (!next.isPresent()) break;
    final String name = next.get().getName();
    if (!Bank.depositAll(name)) break;
    Waiting.waitUntil(1500, () -> !Inventory.contains(new String[]{ name }));
}
```

`Bank.open()` is deprecated — use `Bank.ensureOpen()`. `Bank.depositInventory()` dumps
everything in one click (useful when keep-set is empty, but you'd then re-withdraw tools).

---

## 5. The Make-X Dialog

Modern OSRS make-X master widget is **270**. Structure:
- `270.14` — `BOTTOM` container holding the keyboard-shortcut labels ("Space", "2", "3"...)
- `270.15..270.N` — actual clickable item squares (only unlocked items are shown)

The legacy script convention `Interfaces.get(270, 14)` referenced what was then the first
item slot. That slot has shifted by +1 — items now live at `270.15` onwards.

### Confirmed item slot indices by recipe

| Recipe (tool + material) | 270.15 | 270.16 | 270.17 | 270.18 | 270.19 | 270.20 |
|---|---|---|---|---|---|---|
| Knife + Logs (any wood) | Arrow shafts | Shortbow | Longbow | — | — | — |
| Needle + Leather | Gloves | Boots | Cowl | Vambraces | Body | Chaps |
| (Coif at 38+ likely 270.21 — needs verification) | | | | | | |

These slots are stable as long as the recipe stays the same. Different recipes (e.g.
gem cutting, herblore) will have their own item layouts at 270.15+.

### Progressive selection (highest-tier visible)

```java
Optional<Widget> labels = Widgets.get(new int[]{ 270, 14 });
if (labels.isPresent() && labels.get().isVisible()) {
    Optional<Widget> master = Widgets.get(new int[]{ 270 });
    List<Widget> children = master.get().getChildren();
    for (int i = children.size() - 1; i >= 15; i--) {
        Widget c = children.get(i);
        if (c != null && c.isVisible()) { c.click(); return; }
    }
    // Fallback: dialog stuck with no clickable item -> escape
    Keyboard.pressEscape();
}
```

### Specific item

Click `Widgets.get(new int[]{ 270, childIdx })` where childIdx is the item's slot.

### Why widgets, not `MakeScreen.makeAll(...)`

`MakeScreen.contains(...)` and `MakeScreen.makeAll(...)` look correct on paper but
silently return false in real use for some dialogs. Widget-based clicking has been
reliable across crafter and fletcher rewrites. Stick with it.

**Don't reference `MakeScreen.MakeScreenItem` from Java** — the nested type is marked
private/package-private in the JVM and you'll get `has private access` errors. If you
need a predicate, write the lambda without an explicit parameter type so the compiler
can infer it.

---

## 6. Item IDs vs. Names

Item IDs are stable forever; names go through interface text which can vary (case,
spacing, suffixes). Prefer IDs for the actual click target, names for logging.

Common OSRS IDs (constant since release):
- Knife 946, Bow string 1777, Ball of wool 1759
- Logs 1511, Oak 1521, Willow 1519, Maple 1517, Yew 1515, Magic 1513
- Leather gloves 1059, boots 1061, cowl 1167, vambraces 1063, body 1129, chaps 1095, coif 1169

When picking the right log/material tier for a progressive script, use the **next tier's
shortbow unlock level** as the bracket boundary. Within a tier, the SDK's last-visible
logic auto-upgrades shortbow → longbow at the longbow unlock.

---

## 7. Skill Levels

Use `getActualLevel()`, NOT `getCurrentLevel()`:

```java
int level = Skill.CRAFTING.getActualLevel();  // unboosted
```

`getCurrentLevel()` returns the visible (potentially boosted) level. Stat boosts don't
unlock items, so using boosted level causes the script to try to craft items it can't.

---

## 8. Chat & Click-Continue

```java
if (ChatScreen.isClickContinueOpen()) {
    ChatScreen.clickContinue();
    return;
}
```

`Chatbox` is the wrong class — that's just for the chat tab visibility / trade requests.
`ChatScreen` handles the click-continue dialog used for level-ups and NPC dialogue.

For long-running scripts, consider `ChatScreen.setConfig(...)` to install an automatic
handler (the `Config` class's properties aren't well-documented; inspect the jar before
relying on it).

---

## 9. Antiban

```java
Antiban.setScriptAiAntibanEnabled(true);  // once, in execute()
```

That's the whole modern antiban setup. The AI antiban handles camera rotation, tab
switching, examining players, idle pauses, and breaks. `ABCUtil` (ABC1) and `ABC2Util`
are legacy and worse — don't reach for them.

**Antiban breaks can last several minutes.** Any "stuck detection" timer needs a
generous threshold (5+ minutes) and should reset on any of: animating, bank open,
make-X visible, chat screen open.

---

## 10. Walking

```java
import org.tribot.script.sdk.walking.GlobalWalking;
import org.tribot.script.sdk.types.WorldTile;

GlobalWalking.walkToBank();                     // nearest bank
GlobalWalking.walkTo(new WorldTile(x, y, z));   // specific tile
```

No DaxWalker credentials required anymore. The SDK's `GlobalWalkerAdapter` is wired
internally. Use `LocalWalking.walkTo(...)` for short-range without pathfinding overhead.

---

## 11. Settings Persistence

```java
import org.tribot.script.sdk.util.ScriptSettings;

ScriptSettings.getDefault().save("my_script", settingsObject);
Optional<MySettings> loaded = ScriptSettings.getDefault().load("my_script", MySettings.class);
```

Settings file lands in TRiBot's settings dir as `<name>.json`. Fields must be public for
the default Gson to hit them via reflection.

---

## 12. GUI

Three real choices:

| | When to use |
|---|---|
| **Swing** | Default. Built into JDK. ~50–80 lines for a settings dialog. Ugly but works today. |
| **Compose Desktop** | Modern Kotlin DSL. Needs gradle plugin + dependencies. Use when you want a clean break and don't mind build setup. |
| **JavaFX** | Old way. Needs explicit JavaFX deps post-Java 11. Use only for consistency with existing FXML-based scripts (e.g. cluehuntercollector). |

The Swing dialog pattern that works:

```java
private boolean showSettingsDialog() {
    final MySettings cur = loadSettings();
    final boolean[] ok = { false };
    try {
        SwingUtilities.invokeAndWait(() -> {
            JDialog dlg = new JDialog((Frame) null, "...", true);
            // build UI, OK button sets ok[0]=true and saves
            dlg.pack(); dlg.setLocationRelativeTo(null); dlg.setVisible(true);
        });
    } catch (Exception e) { return false; }
    return ok[0];
}
```

If no args were passed to `execute`, show the dialog; if args were passed, skip it.
That preserves both GUI and CLI workflows.

---

## 13. Common Bugs in Legacy Scripts

When porting an old script, look for these:

1. **Instance fields initialized via `Inventory.find(...)`** — these capture a snapshot
   at construction time and become stale. Move them inside the tick loop.
2. **`String == String` comparison** — works by intern luck but should be `.equals()`.
3. **`while (true)` with no termination check** — replace with a `running` flag the
   loop checks each iteration.
4. **`onPaint` calling `sleep()`** — freezes the renderer. Never sleep on the paint
   thread.
5. **Stale ABC1 idle behaviors** — `abc.leaveGame()` on a random roll is bot-flagged.
   Delete; the AI antiban handles it.
6. **Hard-coded child widget indices for make-X** — old scripts used 270.14–20 directly;
   modern layout puts items at 270.15+ with 270.14 being a container.
7. **`Banking.withdraw(0, name)`** — old API for "withdraw all". Use `Bank.withdrawAll(name)`.

---

## 14. Build / Iteration

- **`./gradlew --stop` + `./gradlew clean build`** when a stale error keeps appearing.
  Gradle's daemon holds compile state in memory between invocations.
- **IntelliJ has its own compiler cache** separate from gradle. After a "weird" stuck
  state, close IntelliJ entirely before retrying from the command line.
- The TribotPlugin gradle setup auto-pulls the SDK from
  `https://gitlab.com/api/v4/projects/20741387/packages/maven`. No JavaFX or Compose
  deps unless you add them.

---

## 15. Legacy → Modern Mapping

| Legacy | Modern |
|---|---|
| `org.tribot.script.Script` + `run()` | `implements TribotScript` + `execute(String args)` |
| `@ScriptManifest` | `@TribotScriptManifest` |
| `org.tribot.api2007.Banking` | `org.tribot.script.sdk.Bank` |
| `org.tribot.api2007.Inventory` | `org.tribot.script.sdk.Inventory` |
| `org.tribot.api2007.Skills` | `org.tribot.script.sdk.Skill` |
| `org.tribot.api2007.Player` | `org.tribot.script.sdk.MyPlayer` |
| `RSItem` / `RSObject` / `RSInterface` | `InventoryItem` / `GameObject` / `Widget` |
| `Interfaces.get(parent, child)` | `Widgets.get(new int[]{ parent, child })` |
| `org.tribot.api.util.abc.ABCUtil` | `org.tribot.script.sdk.antiban.Antiban` |
| `org.tribot.api2007.types.RSTile` | `org.tribot.script.sdk.types.WorldTile` |
| `scripts.api.dax_api.DaxWalker` | `org.tribot.script.sdk.walking.GlobalWalking` |
| `Timing.waitCondition(supplier, ms)` | `Waiting.waitUntil(ms, supplier)` (arg order flipped) |
| `General.sleep(...)` | `Waiting.wait(ms)` / `Waiting.waitNormal(mean, sd)` |
| `General.println(...)` | `Log.info(...)` / `Log.warn(...)` / `Log.error(...)` |
| `NPCChat.getClickContinueInterface()` | `ChatScreen.isClickContinueOpen()` + `clickContinue()` |
| `Keyboard.typeSend(" ")` | `Keyboard.pressEscape()` / `pressEnter()` / `pressBack()` |

---

## 16. Changelog Convention

Every script's `Main.java` gets a block comment **between the imports and the class
declaration**:

```java
package scripts.myscript;

import ...;

/*
 * CHANGELOG
 *   1.0.0 (YYYY-MM-DD) - Initial release. <one-line description of behavior>.
 *   1.0.1 (YYYY-MM-DD) - <what changed and why>.
 *
 * KNOWN-FIX
 *   - Symptom: <what the user sees>
 *     Cause:   <root cause>
 *     Fix:     <where in the code it's handled OR how to recover manually>
 *
 * OPEN
 *   - <issue / TODO that isn't yet handled in code>
 */
@TribotScriptManifest(...)
public class Main implements TribotScript { ... }
```

### Rules

- **CHANGELOG entries are high-signal.** "Fixed compile error" or "renamed variable"
  don't belong here — the git history covers that. Only entries that change behavior,
  surface a new option, or document a class of fix worth remembering.
- **KNOWN-FIX entries are write-once.** When a bug class is fixed in code, document it
  so future-you doesn't re-hit it. Reads like a troubleshooting FAQ.
- **OPEN entries are the to-do list.** Promote to CHANGELOG when resolved, then remove
  from OPEN.
- **Versions follow MAJOR.MINOR.PATCH.** MAJOR for incompatible workflow changes (new
  args, new modes), MINOR for new features, PATCH for fixes.
- **Dates are ISO `YYYY-MM-DD`**, not "yesterday" / "Thursday".

The same block can grow over time. Don't delete old CHANGELOG entries — they're history.
KNOWN-FIX entries can be pruned if a class of bug becomes irrelevant (e.g. SDK API
change makes the workaround unnecessary).
