# Inventory ops (`inventory.*`)

Every container/menu interaction the vanilla client can send. All ops run on the game main
thread (`ThreadAffinity.MAIN`). They are driven **the way the vanilla screen drives them**:
slot clicks go through the open screen's own `slotClicked` (so creative-screen and crafter
overrides, bundle hooks, client-side prediction and sounds behave exactly as with a mouse),
buttons through `MultiPlayerGameMode`, widgets through the screen's own fields/responders.
Everything works on vanilla servers.

Slots are addressed by **menu slot index** (`menu.slots` order of the open screen). The model
sees the layout visually; `inventory.info` only returns what is needed to address slots.

Errors are returned as `ok: false` with a message, e.g. wrong screen open
(`Op needs a MerchantScreen open (current screen: InventoryScreen)`), slot out of range,
invalid button, empty cursor where one is needed.

## Screen

| Op | Args | Result | Notes |
|---|---|---|---|
| `inventory.open` | – | `null` | The inventory key's in-world action: own inventory (creative screen in creative), or the ridden mount's inventory (horse, llama, boat chest...). Fails if any screen is open. The input module's keybind does the same. |
| `inventory.close` | – | `null` | What Esc does on a container screen (`onClose`, sends `ServerboundContainerClosePacket`). Only for screens that have a menu (containers, lectern, beacon, ...). |
| `inventory.info` | – | map | `screen` (screen class name or `null`), and with a menu screen: `menu_type` (registry id, `null` for player inventory / creative picker), `container_id`, `slot_count`, `carried_empty`; in creative also `creative_tab`. |

## Slot clicks

### `inventory.click` — the primitive

Args: `slot` (int), `action` (default `pickup`), `button` (int, default 0).
Result: `{carried_empty: bool}`.

`slot = -999` means "outside the GUI" and is accepted for `pickup`, `throw`, `quick_craft`.
Other slots must be in `[0, slot_count)` and active.

| `action` | `button` | Vanilla gesture |
|---|---|---|
| `pickup` | 0 left, 1 right | Click a slot. Slot -999 with a carried stack: drop all (0) / one (1). |
| `quick_move` | 0 or 1 | Shift-click. |
| `swap` | 0-8 hotbar slot, 40 offhand | Number key / offhand key while hovering a slot. Needs an empty cursor. |
| `clone` | 0-2 | Middle-click / pick-block key (creative only; ignored by the server otherwise). |
| `throw` | 0 one item, 1 whole stack | Drop key (Ctrl+drop = 1) while hovering a slot. |
| `quick_craft` | mask `stage \| type << 2` | One step of a drag (stage 0 start @ -999, 1 add slot, 2 end @ -999; type 0 split, 1 one each, 2 clone/creative). Prefer `inventory.drag`. |
| `pickup_all` | 0 | Double-click: gathers matching items onto the cursor. |

Crafter: `pickup` on an empty crafter grid slot toggles it disabled/enabled (the screen sends
`ServerboundContainerSlotStateChangedPacket`), `swap` into a disabled slot re-enables it.

### `inventory.drag`

Args: `slots` (list of int), `button`: `left` (split evenly) | `right` (one each) | `middle`
(full stacks, creative). Needs an item on the cursor. Slots the vanilla screen would not add
(wrong item, full, not placeable, more slots than items for left/right) are skipped.
Result: `{slots: [accepted], carried_empty}`. One accepted slot behaves like a plain click.

### `inventory.quick_move_all`

Args: `slot`. Shift-click the slot, then shift-double-click it: every stack of the same item
in the same container is quick-moved. Needs an empty cursor. Result: `{clicks}`.

## Menu buttons

### `inventory.button`

Args: `id`. Sends `ServerboundContainerButtonClickPacket` after the same local check the
screen does (`menu.clickMenuButton`), playing the screen's select sound.

| Screen | Ids |
|---|---|
| Enchanting table | 0, 1, 2 = top, middle, bottom offer |
| Stonecutter | recipe index (row-major, 4 per row, from the first recipe; independent of scrolling) |
| Loom | pattern index (row-major, 4 per row, independent of scrolling) |
| Lectern | 1 previous page, 2 next page, 3 take book (only when allowed to build), 100+n go to page n |

### `inventory.select_trade`

Args: `index` (offer index in the full trade list, 0 = top, independent of scrolling).
Same as clicking the offer button: selects it and auto-fills the payment slots from the inventory.
Then take the result from slot 2 with `inventory.click`.

### `inventory.beacon`

Args: `primary` (effect id, e.g. `minecraft:haste`), optional `secondary` (`minecraft:regeneration`
or the primary again for level II; needs a full pyramid). Needs the payment item in the slot.
Same as selecting the effects and pressing Done (sends `ServerboundSetBeaconPacket`, closes).

### `inventory.rename`

Args: `name` (max 50 chars, `""` restores the default name). Sets the anvil's name field, which
fires the screen's own responder (`ServerboundRenameItemPacket`), exactly as typing into it.
Needs an item in the first slot. Result: `{name}`. Typing key by key via `input.*` also works.

### `inventory.bundle_select`

Args: `slot` (slot holding a bundle), `index` (item index as shown in the bundle tooltip, -1
clears). Same as scrolling the mouse wheel over the bundle (`ServerboundSelectBundleItemPacket`).
A following right-click `pickup` on the bundle takes out the selected item.

## Recipe book

### `inventory.place_recipe`

Args: `item` (result item id), `max` (default false; true = shift-click, as many as possible).
Screens with a recipe book: player inventory (2×2), crafting table, furnace, blast furnace, smoker.
Picks the unlocked recipe-book entry producing `item` that fits the grid, preferring a craftable
one, and sends `ServerboundPlaceRecipePacket` like clicking its recipe button. If nothing is
craftable the server shows the ghost recipe. Result: `{craftable}`.

## Creative inventory

Slot clicks in the creative screen use `inventory.click`; the screen's own creative handling
applies (infinite stacks from the picker, `-999` drops, destroy-item slot).

| Op | Args | Notes |
|---|---|---|
| `inventory.creative_tab` | `tab` (id, e.g. `minecraft:combat`, `minecraft:search`, `minecraft:hotbar`, `minecraft:inventory`) | Selects a shown tab (switches Fabric tab pages as needed). Result `{slot_count}`. |
| `inventory.creative_scroll` | `position` 0 (top) … 1 (bottom) | Like dragging the scrollbar. Fails if the tab cannot scroll. |
| `inventory.creative_search` | `text` (`#tag` searches tags) | Selects the search tab and sets its query. Result `{text, results}`. |
| `inventory.creative_hotbar` | `index` 0-8, `action` `load` \| `save` | Saved toolbars (activator key + number). Creative only, no screen open. |

## Slot layouts (reference)

- Player inventory (`InventoryScreen`): 0 craft result, 1-4 craft grid, 5-8 armor
  (head, chest, legs, feet), 9-35 main inventory, 36-44 hotbar, 45 offhand.
- Creative, inventory tab: the same 0-45, plus 46 = destroy-item slot
  (`pickup` deletes the cursor stack, `quick_move` clears the whole inventory).
- Creative, other tabs: 0-44 the visible 9×5 item grid, 45-53 hotbar.
- Container screens: the container's slots first, then main inventory (27) and hotbar (9).

## Not covered here (by design)

- Typing into text fields (sign, book & quill, command block, creative search key by key),
  Esc on non-container screens, mouse-wheel/cursor movement, keybinds outside GUIs (drop,
  swap offhand, hotbar selection, pick block): `input.*` executors.
- Recipe-book widget toggles (open/close book, craftable filter, tabs, pages): plain GUI
  clicks via `input.*`; they only change client view settings.
