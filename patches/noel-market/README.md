# Noel market overlay

These classes build against the existing NERO gameplay overlay and the target
server JAR. The public repository does not contain that private overlay.

The automated ruby stock retains its existing categories and original pricing
formula. New stock costs half the old calculated price. Equipment rolls
upgrade levels and open sockets from 5 through 8, the previous maximum.
Each of the eight stock generators has ten reserved listings. Standard stock
refills after the existing delay. The four featured costumes sort first and
are replaced in the same transaction when bought.

Featured costumes scale the generated VIP options by ten, capped at the
signed 16-bit item-option limit. Verify item IDs 2158, 2159, 2161 and the Noel
costume ID before integration. Existing generated rows and player-set prices
are left intact. Pending older restock rows continue to refill.

Posting or buying requires an activated session. Returning existing escrow
or collecting previously earned proceeds remains available to its owner.
The consignment restriction honors item option 30 (trade lock).

Compiled against the local reference JARs. Requires integration testing with
MariaDB, the client and actual item templates before release.
