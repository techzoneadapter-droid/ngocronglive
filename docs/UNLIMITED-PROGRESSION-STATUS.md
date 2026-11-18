# Unlimited progression development status

Development branch: `codex/noel-market-kaizen`. Excluded from the Noel deployment.

## Implemented and tested

- Existing `NPoint` reward/cost arithmetic guards and percentage multiplication overflow correction.
- Immutable `UnlimitedProgression` with arbitrary precision POWER, POTENTIAL, HP, KI, ATTACK, DEFENSE.
- Exact HP/KI/attack arithmetic-series costs. Defense batches charge each point; this differs from the old multi-point defense bug and must be reviewed before integration.
- Allocate the largest affordable count within a requested potential budget, retaining the exact unused balance. Uses binary search rather than a per-point loop.
- Decimal-string snapshot/restore without floating point or 64-bit conversion.
- Immutable `BossProgression` with independent HP/attack/defense rates in basis points. Rates used by tests are fixtures, not a selected production balance.
- Core tests: 40,000 comparisons against exact arithmetic; 800 allocation comparisons against independent per-point purchases; values of 10^100; overspend refusal; exact restore.
- JDBC progression repository with a versioned decimal snapshot, original `data_point` migration backup, caller-owned transactions and stale-write rejection. Inventory and original stats columns are not modified.
- Durable per-boss state and spawn-key deduplication in the same transaction as strength advancement. Replaying a confirmed spawn does not increment again.
- Tested both repositories on a disposable localhost MariaDB, including database shutdown/restart, huge exact values, rollback, unknown format refusal and concurrent duplicate defeat reports.
- `ProgressionCommands` provides authenticated character open/reconnect, revision-checked budget allocation and internal power/potential reward operations. All work in caller-owned transactions; update live state only after commit.
- Durable per-character request keys prevent recharging a retried allocation or duplicating an internal reward. Reusing a key with different inputs is refused. Replays return current state rather than rolling a client back to an old snapshot.
- MariaDB tests cover simultaneous duplicate allocation requests, account ownership refusal, stale revision refusal, rollback and request/reward replay after database restart. These call the service directly, not the game login UI or Controller.
- Real `NPoint` equipment formula bodies now compute exact HP/KI/attack/defense, preserving their existing condition/order, including percentage bonuses, transformations and fusion. Flat item options are accumulated exactly too. Compatibility int fields saturate rather than wrap; they are not the authoritative unlimited result.
- Real `NPoint` attack formulas expose BigInteger results for normal/focused and unfocused skills, critical hits and KI-based damage, with uniform integer damage variation. Legacy callers still receive bounded int projections.
- Exact defense subtraction and armor consumable reductions have a BigInteger overload. This is not yet an end-to-end HP injury migration.
- Explicitly enabled exact HP/KI state now supports clamped recovery, KI affordability, full restore, and synchronized raw damage. Only one simultaneous lethal hit reports the alive-to-dead transition. Legacy vital methods route to exact state only after activation. Gameplay injury dispatch and reward handling are still not wired.
- `ExactCombat` connects exact defense/effect reduction and hit caps to synchronized NPoint HP deduction. Tests cover damage beyond long, simultaneous lethal hits, dead-target retries and resurrection. Encounter-specific eligibility, shields and special effects remain the responsibility of gameplay adapters.
- `BossEncounter` connects a durable spawn identity to NPoint base stats, exact lethal results and the per-spawn defeat ledger. A MariaDB integration test attaches real NPoint formulas, kills a synthetic encounter, advances its next strength, rejects a repeated death and attaches the grown next spawn; data survives database restart. It does not yet hook existing Boss/SkillService dispatch or distribute boss loot.
- Base `Boss.respawn`/`Boss.die` now include optional `BossProgressionRuntime` hooks. Runtime installation is explicit, profiles select exact class names, and no production profiles/startup installation are enabled. Tests execute the modified base methods on a fixture subclass with MariaDB: initial spawn, nonlethal scripted die, exact kill, duplicate death, grown respawn, repeated respawn and restart all pass. Failure to record a death is retried before respawn rather than silently resetting progression.
- Java exact-stats extension codec and shared C# decoder roundtrip 10^100 values; every truncated prefix is refused. This is a cross-language codec test, not client transport/UI integration.
- Seven fixed Kaizen recipes and immutable forging plans exist. Transaction adapter locks authenticated player state, refuses a stale live snapshot, saves bag/gold and request receipt atomically and preserves other currencies. Real MariaDB tests exercise all seven crafts, rollback/retry and restart receipt replay.
- Seven optional equipment slots append at indices 13..19. Actual InventoryService private equip routing is tested: original 13 slots remain untouched and swapping returns the old item. Real catalog bindings are required before activation; GodGK adds missing slots after loading saved body data when configured.
- KaizenCombine delegates upgrade/socket/socket-creation menus directly to existing CombineServiceNew APIs. The original +7 and eight-star limits remain in that implementation. These routes compile but are not NPC/client-tested yet.
- Real catalog migration now allocates seven fragment/equipment pairs without reusing original IDs and verifies mappings on restart. MariaDB tests verify 14 new entries and preserve all original fixtures; icons currently reuse existing resources and are not final custom artwork.
- KaizenNpc and KaizenPlayerForge compile against the actual runtime: native menus route to durable crafting and original combine APIs. Bootstrap verifies Làng Aru map 0, chooses ground away from existing NPCs/waypoints, and allocates a native NPC template reusing existing sprites. Bootstrap is not yet executed against the local game and placement/rendering remain unverified.
- CombineServiceNew confirmation menus resolve to the selected Kaizen NPC when used there, retaining Ba Hat Mit for original callers. Upgrade/socket algorithms are unchanged. Craft/exchange currently share the fragment recipe selector; a separate original-equipment exchange rule is not implemented.
- SkillService now routes eligible configured standard bosses through ExactSkillDamage/Boss.injuredExact. Base half-damage, armor and shielding order is preserved; custom injury handlers and multi-phase bosses are refused. Actual adapter tests apply 10^100 damage, reward once and grow the next spawn. Existing PvP/mob/special skills and exact reflection/lifesteal still require migration; int-side products have overflow guards.
- ExactStatsChannel is hooked before Controller dispatch with a separate magic prefix, polling throttling and zone-scoped actor snapshots. Tests verify opt-in-only delivery, Noel packet reader preservation, handshake, combat broadcast and zone isolation. PC plugin compiles against real G1 interop, polls the channel and replaces inventory header labels with compact exact values; it has not been loaded in an actual client session yet.
- PlayerProgressionRuntime now attaches to opt-in subscription and persists NPoint power/potential reward and HP/KI/attack/defense purchases. Actual player hook tests with MariaDB verify HP ratio, reward, purchase, new-player-instance restore and database restart. It is not installed in a game startup yet. Critical-point purchases are not implemented in this runtime; production enablement must wait for that path rather than silently creating split balances.
- `scripts/TEST-NPOINT-EXACT.ps1 -RuntimeJar <existing-runtime.jar>` compiles the actual modified NPoint and checks VIP equipment beyond int, equip/unequip/recalculate, 1,000 ordinary equipment comparisons, 10^100 base stats, normal/critical/KI attacks and defense buffs. Synthetic player fixtures avoid starting the database/server; no actual client battle has been tested.

Run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/TEST-PROGRESSION.ps1`.

## Not yet implemented or verified

- Player hook integration is tested on actual Player/NPoint classes with synthetic sessions and a real disposable MariaDB. No real local client login/logout battle has been verified. Extra vitals persistence and power/potential client display remain pending.
- `NPoint` legacy HP/KI/attack/defense fields remain int; power/potential remain long. Exact derived stats and attack APIs exist but current HP, injury dispatch, BossData, mob damage and callers still need migration. QCKK total-map HP currently uses the legacy long aggregate and is not unlimited yet.
- Service.point, HP updates, companion stats and combat notifications currently transmit int fields. Existing packets must remain compatible; new clients need negotiated extended decimal fields and rendering.
- Base boss lifecycle hooks exist but SkillService exact injury dispatch, subclass overrides and per-type production configuration remain pending. Runtime is not installed at startup, so bosses do not yet grow automatically in the running game. Test rates (5% HP/2% attack) are fixtures only.
- Preserve scripted encounter mechanics and non-attacking Noel boss behavior. Do not apply one global boss multiplier.
- Kaizen native menus, catalog and bootstrap exist but are not activated or visually tested. Final icons, distinct original-equipment exchange, source-specific drop hooks and client visual slot names/layout remain pending. Full SkillService exact injury/side effects and live client snapshot transport/UI are still absent; do not describe the project as complete or open the old local game as the finished unlimited test build.
- Do not publish this as an unlimited gameplay release before integration, restart/relogin tests and PC/Android/iOS gameplay checks.

## Next integration order

1. Connect the tested command service to login and progression packets after capability negotiation; retain original inventory and legacy stats. Do not enable allocations while combat still uses a different legacy potential/stat balance.
2. Server combat arithmetic and client protocol negotiation; include companions, mounts, mobs and boss HP.
3. Actual boss types and exactly-once durable defeat advancement; apply new stats only at respawn.
4. Seven equipment slots and Kaizen adapters to existing CombineServiceNew. Use original inventory/trade/currency rules and configured source-specific drops.
5. Runtime and regression checks, commit and PR. No merge or live deployment without the user's instruction.


## Integration evidence — 18/11/2026

- Isolated lab `outputs/NERO-VO-HAN-LOCAL` uses a copied database on loopback port 15484 and game port 14615. Production is untouched. Original QA remains separate.
- Real PC client login succeeded as local fixture player 6 in map 84. ExactStatsPc loaded after handling the older PC-only inventory header signature; `nero_progression_state` contains player 6, proving negotiated runtime attach. Screenshot/log retained in lab logs. This is login/transport evidence, not a completed combat/menu/equipment audit.
- Server startup actually installed Kaizen NPC/catalog and configured Kuku/Rambo growth. Log `KAIZEN_DROP_CONFIG enabled=0 pending=14` confirms future source rules remain disabled per owner clarification. No new map or boss created from ZIP prototypes.
- Owner supplied final three-stat equipment balance on 18/11; all percentages multiplied by 100 in `KaizenStats`. Catalog keeps existing IDs. Forging and whole drops share these options, +0 by omission of upgrade option.
- Source drop integration and test scope are documented in `KAIZEN-DROP-PREPARATION.md`. Native RewardService/ItemMap concurrency test and 140000-draw boundary enumeration pass. MariaDB crafting/progression/boss tests pass after database restart with the approved three-stat gear.
- Old pending statements above are historical: bootstrap/runtime/client transport now run locally, while full seven-slot UI, actual Kaizen upgrade/socket/trade roundtrips, arbitrary-precision combat on all target types and mobile exact-stat synchronization remain unfinished. Do not advertise a complete unlimited release.


### Owner-defined local acceptance tests

- Seed reads actual maximum `power_limit` values and multiplies POWER/HP/KI/ATTACK/DEFENSE by 10 for local player 6 only. Current source table caps: power 250,000,000,000; HP/KI 600,000; attack 32,000; defense 2,100. Local base becomes power 2.5e12, HP/KI 6m, attack 320k, defense 21k. Potential is a local-only test budget of 10^30. Original int overflow limit is separate from this progression table.
- Actual online Player was equipped through InventoryService.putItemBody into slots 13–19 and NPoint recalculated after removal/re-equip of each piece. Exact HP reached 70,178,749,920 and attack 14,035,680,000 with existing local costume/mount and 7 gear. Screenshots confirm 20 received client body slots and native inventory list rows at the tail. Icons still reuse native donor artwork, so this is not final item art.
- Test caught option94 semantics: original game uses it for damage reduction. New Kaizen items now treat their declared armor percentages as armor only, preserving old-item reduction. Actual cape removal reduced armor 504,012 to 252,006; scarf removal to 336,008. Added regression assertions that old option94 still yields reduction and no armor increase.
- Actual SkillService.playerAttackPlayer killed the configured Kuku. Local-only runner bypassed departure cosmetics and called the real Boss.respawn after 2,005ms: HP 10,500,000 -> 11,025,000; attack 30,600 -> 31,212; armor 0 -> 0 (zero base remains zero). This verifies real damage/death/persistence/respawn; normal production timing is unchanged.
- PC Kaizen art now uses owner reference via generated transparent four-frame cyan-flame atlas, subtle code bob, scoped to exact NPC name. Atlas/prompt metadata is under patches/unlimited-client/assets. Local screenshots show the new character. Native name rendering remains in use. Not ported to mobile yet.
- Inventory critical display fixes signed-byte negative formatting for current 200% value. Vital HUD compact-number patch visually confirmed in local-world-hud-kaizen.png: HP 70,17tỷ and KI 7,79tỷ, fitting native bars; old 2147483647 labels are gone. Inventory shows critical 200% without signed-byte wrap.


### Upgrade/socket compatibility and new inventory art

- Fixed real CombineServiceNew stat selection for Kaizen: main percentage options 50/77/103 now use existing success/failure algorithm rather than producing null option; legacy gear keeps original selector. No copied upgrade implementation, original costs/stone pairing/ratios retained.
- TEST-KAIZEN-COMBINE.ps1 executes actual private CombineServiceNew upgrade/holes/socket methods for all seven pieces: 7 upgrade steps, 8 holes and 8 sockets per piece, no cost past cap, unchanged secondary stats and legacy armor upgrade. Success probability is forced only in fixture to test each branch deterministically; this is not client interaction proof.
- New distinct icon IDs 32050–32063 are conflict-checked against unrelated DB templates, existing Kaizen IDs retained, runtime/catalog updated after commit. Item cache version bumped to27. PC icon art scoped to range, 20px equipment and15px fragments; uses native clip to avoid painting over tab headers. Atlas/prompt metadata under patches/unlimited-client/assets/kaizen-equipment-icons.*. Native mobile icon resource distribution remains pending.
- Kaizen socket entry now exposes create-holes and insert-crystal menus through original CombineServiceNew. Client root/upgrade panel verification is ongoing.


### Real client menu evidence

- Local QA 0.1.2 walks to native NPC distance and sends Service.openMenu(81)/confirmMenu(81,2); real server returns native 5-choice menu and combine panel. Screenshots local-kaizen-root-menu.png/local-kaizen-upgrade-menu.png and marker KAIZEN_NATIVE_ROOT_AND_UPGRADE_PANEL_PASS verified.
- Remaining UI defects revealed: combine portrait still uses native donor head resource (looks like an equipment icon); combine instruction still lists only old clothes; full exact current HP/KI after repeated login can reflect legacy stored ratios. These are pending, not release-ready.
- Repeated database test passed including 14 icon bindings and seven persisted equipment recipes, with old templates preserved. Branch remains draft and no production changes made.


### NPC grounding and exact current-vital persistence

- Kaizen atlas visible foot baseline is source row646 of724; sprite origin now uses this baseline instead of transparent image bottom. Bob lift ranges0..0.8px. Screenshot local-world-hud-kaizen.png verified soles on native ground after change; character size unchanged.
- ExactVitalsRepository stores current HP/KI decimal strings in separate table initialized with progression schema. PlayerDAO original save connection writes exact vitals for negotiated players; attach restores saved values and clamps to current equipment maxima, using legacy ratio only for first migration.
- MariaDB tests verify HP1234/KI123 survive new Player attach and database restart;10^100 current-vital serialization and rollback also pass. Full real-client relog with updated PlayerDAO overlay remains under validation; no production rollout.
- Kaizen combine instructions now mention Thần Trang only when invoked from Kaizen. Other NPC instructions remain unchanged. Portrait still needs dedicated NPC resource mapping.


### Grounding correction after user rejection

The previous image-only grounding claim was insufficient. Inspected actual client Npc.paint and Controller map loading: native NPCs receive server y+3, render legs at cy-CharInfo[frame][1][2]+part.dy with native leg-image height. Kaizen now derives the standing foot offset from Bunma (NPC7) using that same data, retains character size, and draws the original TileMap.bong shadow at native NPC origin. Runtime diagnostic confirmed npcY435 and BunmaFootOffset2. New exact plugin version0.1.1 is verified loaded. User visual acceptance remains pending.
