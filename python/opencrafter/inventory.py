"""Thin wrappers for the ``inventory.*`` ops (see docs/ops/inventory.md).

Every function takes a client object exposing ``call(op, **args)`` and returns its result.
Slots are menu slot indices of the open screen; ``OUTSIDE`` (-999) means outside the GUI.
"""

from __future__ import annotations

from typing import Any, Iterable, Literal, Optional, Protocol

OUTSIDE = -999
OFFHAND = 40

ClickActionName = Literal["pickup", "quick_move", "swap", "clone", "throw", "quick_craft", "pickup_all"]


class Client(Protocol):
    def call(self, op: str, **args: Any) -> Any: ...


# Screen

def open(client: Client) -> None:
    """Open the player's (or ridden mount's) inventory, like the inventory key."""
    return client.call("inventory.open")


def close(client: Client) -> None:
    """Close the open container screen, like Esc."""
    return client.call("inventory.close")


def info(client: Client) -> dict:
    """Minimal slot-addressing metadata: screen, menu_type, container_id, slot_count, carried_empty."""
    return client.call("inventory.info")


# Slot clicks

def click(client: Client, slot: int, action: ClickActionName = "pickup", button: int = 0) -> dict:
    """Raw vanilla container click (ContainerInput + button), see the doc table."""
    return client.call("inventory.click", slot=slot, action=action, button=button)


def left_click(client: Client, slot: int) -> dict:
    return click(client, slot, "pickup", 0)


def right_click(client: Client, slot: int) -> dict:
    return click(client, slot, "pickup", 1)


def quick_move(client: Client, slot: int) -> dict:
    """Shift-click."""
    return click(client, slot, "quick_move", 0)


def swap(client: Client, slot: int, hotbar: int) -> dict:
    """Swap with hotbar slot 0-8, or ``OFFHAND``."""
    return click(client, slot, "swap", hotbar)


def throw(client: Client, slot: int, whole_stack: bool = False) -> dict:
    """Drop key over a slot (Ctrl = whole stack)."""
    return click(client, slot, "throw", 1 if whole_stack else 0)


def drop_carried(client: Client, one: bool = False) -> dict:
    """Click outside the GUI with the carried stack (right click drops one)."""
    return click(client, OUTSIDE, "pickup", 1 if one else 0)


def clone(client: Client, slot: int) -> dict:
    """Middle-click (creative)."""
    return click(client, slot, "clone", 2)


def pickup_all(client: Client, slot: int) -> dict:
    """Double-click: gather matching items onto the cursor."""
    return click(client, slot, "pickup_all", 0)


def drag(client: Client, slots: Iterable[int], button: Literal["left", "right", "middle"] = "left") -> dict:
    """Drag the carried stack across slots (left split, right one each, middle full stacks)."""
    return client.call("inventory.drag", slots=list(slots), button=button)


def quick_move_all(client: Client, slot: int) -> dict:
    """Shift-click then shift-double-click: move every matching stack of that container."""
    return client.call("inventory.quick_move_all", slot=slot)


# Menu buttons and widgets

def button(client: Client, id: int) -> None:
    """Menu button: enchant option 0-2, stonecutter recipe, loom pattern, lectern 1/2/3/100+n."""
    return client.call("inventory.button", id=id)


def select_trade(client: Client, index: int) -> None:
    return client.call("inventory.select_trade", index=index)


def beacon(client: Client, primary: str, secondary: Optional[str] = None) -> None:
    args = {"primary": primary}
    if secondary is not None:
        args["secondary"] = secondary
    return client.call("inventory.beacon", **args)


def rename(client: Client, name: str) -> dict:
    """Set the anvil's item name ("" restores the default)."""
    return client.call("inventory.rename", name=name)


def bundle_select(client: Client, slot: int, index: int) -> None:
    """Select the bundle item at ``index`` (-1 clears)."""
    return client.call("inventory.bundle_select", slot=slot, index=index)


def place_recipe(client: Client, item: str, max: bool = False) -> dict:
    """Fill the grid from the recipe book with the recipe for ``item``."""
    return client.call("inventory.place_recipe", item=item, max=max)


# Creative

def creative_tab(client: Client, tab: str) -> dict:
    return client.call("inventory.creative_tab", tab=tab)


def creative_scroll(client: Client, position: float) -> None:
    return client.call("inventory.creative_scroll", position=position)


def creative_search(client: Client, text: str) -> dict:
    return client.call("inventory.creative_search", text=text)


def creative_hotbar(client: Client, index: int, action: Literal["load", "save"]) -> None:
    return client.call("inventory.creative_hotbar", index=index, action=action)
