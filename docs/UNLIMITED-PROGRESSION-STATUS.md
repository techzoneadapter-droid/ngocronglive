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
- `scripts/TEST-NPOINT-EXACT.ps1 -RuntimeJar <existing-runtime.jar>` compiles the actual modified NPoint and checks VIP equipment beyond int, equip/unequip/recalculate, 1,000 ordinary equipment comparisons, 10^100 base stats, normal/critical/KI attacks and defense buffs. Synthetic player fixtures avoid starting the database/server; no actual client battle has been tested.

Run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/TEST-PROGRESSION.ps1`.

## Not yet implemented or verified

- Unlimited state is not connected to a live character. Database persistence is tested independently; actual login/logout and combat integration remain pending.
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
