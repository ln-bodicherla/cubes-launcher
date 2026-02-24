# Cubes Launcher - Project Memory

## Tile Modes

The three tile behavior modes are **mutually exclusive** (enabling one disables the other two):

- **Tile Mode** — all apps shown as resizable tiles (manual sizing)
- **Minimal Tiles** — pinned + notification apps only
- **Usage Tracking** — auto-resizes tiles based on launch frequency

Mutual exclusion + per-mode tile size memory is enforced in `SettingsActivity.kt`.

## Per-Mode Tile Size Memory

Each mode stores its own tile sizes independently in DataStore so switching modes preserves layouts:

| DataStore Key | Purpose |
|---------------|---------|
| `tile_sizes` | Active/live tile sizes used by the UI |
| `tile_sizes_tile_mode` | Saved tile sizes for tile mode |
| `tile_sizes_minimal_tiles` | Saved tile sizes for minimal tiles |
| `tile_sizes_usage_tracking` | Saved tile sizes for usage tracking |
| `tile_sizes_default` | Saved tile sizes for default (no mode) |

On mode switch: save `tile_sizes` → outgoing mode's key, load incoming mode's key → `tile_sizes`.
