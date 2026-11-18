# Removing stat ceilings and scaling bosses

1. Inventory every narrowing conversion at persistence, combat, and packet
   boundaries. Audit HP, KI, damage, defense, crit, and item options separately.
2. Introduce versioned wide-value packets and dual-read persistence. Keep old
   clients on their existing bounded protocol until all supported clients can
   display and consume the new fields.
3. Move arithmetic away from signed 32-bit intermediates. Use checked wide
   calculations and explicit conversion at compatibility boundaries. A true
   unbounded progression model would need an arbitrary-precision or tiered
   representation rather than a larger fixed-width integer.
4. Add per-boss-family progression profiles, with configurable HP, damage,
   defense and growth curves. Capture tier at spawn from eligible players'
   progression, keep it stable for that encounter, and fall back to existing
   stats when no profile is configured. Test loot attribution, reconnects,
   and mixed-strength parties before enabling it.
5. Exercise boundary values, combat, saving and reloading, and Android,
   desktop, and browser compatibility. Roll out each stat group behind a
   server flag with a rollback path.
