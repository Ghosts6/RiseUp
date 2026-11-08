"""WebSocket fan-out used by `DashboardNotifier` (thread-safe from scheduler jobs)."""

from __future__ import annotations

import asyncio
import logging
import threading
from typing import Any

from fastapi import WebSocket

log = logging.getLogger(__name__)


class WebSocketHub:
    def __init__(self) -> None:
        self._connections: dict[int, set[WebSocket]] = {}
        self._lock = threading.Lock()
        self._loop: asyncio.AbstractEventLoop | None = None

    def set_loop(self, loop: asyncio.AbstractEventLoop) -> None:
        self._loop = loop

    async def connect(self, user_id: int, websocket: WebSocket) -> None:
        await websocket.accept()
        with self._lock:
            self._connections.setdefault(user_id, set()).add(websocket)

    def disconnect(self, user_id: int, websocket: WebSocket) -> None:
        with self._lock:
            self._connections.get(user_id, set()).discard(websocket)

    def connection_count(self, user_id: int) -> int:
        return len(self._connections.get(user_id, set()))

    def broadcast(self, user_id: int, message: dict[str, Any]) -> None:
        if self._loop is None or not self._connections.get(user_id):
            return
        try:
            asyncio.run_coroutine_threadsafe(self._send(user_id, message), self._loop)
        except RuntimeError:  # loop closed during shutdown
            pass

    async def _send(self, user_id: int, message: dict[str, Any]) -> None:
        with self._lock:
            targets = list(self._connections.get(user_id, set()))
        for ws in targets:
            try:
                await ws.send_json(message)
            except Exception:
                self.disconnect(user_id, ws)
