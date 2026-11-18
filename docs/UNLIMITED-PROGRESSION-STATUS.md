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

Run `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/TEST-PROGRESSION.ps1`.

## Not yet implemented or verified

- Unlimited state is not connected to a live character. Database persistence is tested independently; actual login/logout and combat integration remain pending.
- `NPoint` HP/KI/attack/defense remain int; power/potential remain long. BossData, damage APIs and skill/mob/companion/fusion computations require migration.
- Service.point, HP updates, companion stats and combat notifications currently transmit int fields. Existing packets must remain compatible; new clients need negotiated extended decimal fields and rendering.
- Runtime boss death lifecycle and per-type configuration have not been wired to the tested durable repository. Bosses do not yet grow automatically in the running game.
- Preserve scripted encounter mechanics and non-attacking Noel boss behavior. Do not apply one global boss multiplier.
- No new equipment slots, Kaizen NPC, fragments, drop hooks or CombineServiceNew adapters are implemented yet. Existing equipment occupies body slots 0 through 12; adding seven slots requires client/server inventory compatibility work.
- Do not publish this as an unlimited gameplay release before integration, restart/relogin tests and PC/Android/iOS gameplay checks.

## Next integration order

1. Connect the tested command service to login and progression packets after capability negotiation; retain original inventory and legacy stats. Do not enable allocations while combat still uses a different legacy potential/stat balance.
2. Server combat arithmetic and client protocol negotiation; include companions, mounts, mobs and boss HP.
3. Actual boss types and exactly-once durable defeat advancement; apply new stats only at respawn.
4. Seven equipment slots and Kaizen adapters to existing CombineServiceNew. Use original inventory/trade/currency rules and configured source-specific drops.
5. Runtime and regression checks, commit and PR. No merge or live deployment without the user's instruction.
