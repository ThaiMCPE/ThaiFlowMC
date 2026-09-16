# Installs the `thaiflow` module into sys.modules for a single mod's Python
# context. ThaiFlowMC evaluates this once per mod, right before the mod's own
# main.py, so `from thaiflow import *` finds this already-built module
# instead of needing a real package on a filesystem path.
#
# This file's last expression is `_install_thaiflow_module` itself (not a
# call): dev.thaiflowmc.python.PythonRuntime evaluates this source to get
# that function as a host Value, then calls it with the bridge (a host/Java
# object) as its only argument. That bridge is the ONLY way this module
# reaches back into Java - mods never see it directly.

import sys as _sys
import types as _types


def _install_thaiflow_module(bridge):
    module = _types.ModuleType("thaiflow")
    on_load_callbacks = []

    class Player:
        """The ThaiFlowMC-native player a mod's code interacts with."""

        __slots__ = ("_java",)

        def __init__(self, java_player):
            self._java = java_player

        @property
        def name(self):
            return self._java.getName()

        @property
        def health(self):
            return self._java.getHealth()

        def say(self, message):
            self._java.say(str(message))

        def teleport(self, x, y, z):
            self._java.teleport(float(x), float(y), float(z))

    class Server:
        """The ThaiFlowMC-native running server a mod's code interacts with."""

        __slots__ = ("_java",)

        def __init__(self, java_server):
            self._java = java_server

        def broadcast(self, message):
            self._java.broadcast(str(message))

        @property
        def online_player_count(self):
            return self._java.getOnlinePlayerCount()

    # Event payloads that should be wrapped in a friendlier Python type
    # before a mod's handler sees them. Events not listed here (e.g. the
    # generic "test" event) pass their payload through unchanged.
    _event_wrappers = {
        "player_join": Player,
        "player_leave": Player,
        "server_start": Server,
        "server_stop": Server,
    }

    def _wrap_payload(event_name, payload):
        wrapper = _event_wrappers.get(event_name)
        if wrapper is None or payload is None:
            return payload
        return wrapper(payload)

    def load(fn):
        """Registers `fn` to run once, right after this mod finishes loading."""
        on_load_callbacks.append(fn)
        return fn

    def event(name):
        """Registers `fn` to run whenever the named ThaiFlowMC event fires."""

        def decorator(fn):
            def handler(payload):
                fn(_wrap_payload(name, payload))

            bridge.subscribe(name, handler)
            return fn

        return decorator

    def item(id, stack=64):
        """Declares a new item, auto-detecting its texture if one exists."""
        bridge.registerItem(id, stack)

    module.load = load
    module.event = event
    module.item = item
    module.Player = Player
    module.Server = Server

    # Sugar decorators, all just named wrappers around the generic event().
    module.player_join = event("player_join")
    module.player_leave = event("player_leave")
    module.server_start = event("server_start")
    module.server_stop = event("server_stop")
    module.tick = event("tick")
    module.join = module.player_join

    _sys.modules["thaiflow"] = module
    return on_load_callbacks


_install_thaiflow_module
