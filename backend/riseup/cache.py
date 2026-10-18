"""Small cache abstraction: Redis when configured, otherwise in-process (DP-02)."""

from __future__ import annotations

import json
import logging
import threading
import time
from abc import ABC, abstractmethod
from typing import Any

log = logging.getLogger(__name__)


class Cache(ABC):
    @abstractmethod
    def get(self, key: str) -> Any | None: ...

    @abstractmethod
    def set(self, key: str, value: Any, ttl: int = 600) -> None: ...

    @abstractmethod
    def delete_prefix(self, prefix: str) -> None: ...


class InMemoryCache(Cache):
    def __init__(self) -> None:
        self._data: dict[str, tuple[float, Any]] = {}
        self._lock = threading.Lock()

    def get(self, key: str) -> Any | None:
        with self._lock:
            item = self._data.get(key)
            if not item:
                return None
            expires, value = item
            if expires < time.time():
                self._data.pop(key, None)
                return None
            return value

    def set(self, key: str, value: Any, ttl: int = 600) -> None:
        with self._lock:
            self._data[key] = (time.time() + ttl, value)

    def delete_prefix(self, prefix: str) -> None:
        with self._lock:
            for key in [k for k in self._data if k.startswith(prefix)]:
                del self._data[key]


class RedisCache(Cache):
    def __init__(self, url: str) -> None:
        import redis

        self._client = redis.Redis.from_url(url)

    def get(self, key: str) -> Any | None:
        raw = self._client.get(key)
        return json.loads(raw) if raw else None

    def set(self, key: str, value: Any, ttl: int = 600) -> None:
        self._client.set(key, json.dumps(value, default=str), ex=ttl)

    def delete_prefix(self, prefix: str) -> None:
        for key in self._client.scan_iter(f"{prefix}*"):
            self._client.delete(key)


def make_cache(redis_url: str | None) -> Cache:
    if redis_url:
        try:
            cache = RedisCache(redis_url)
            cache.get("riseup:ping")
            return cache
        except Exception as exc:  # redis missing or unreachable
            log.warning("Redis unavailable (%s); using in-memory cache", exc)
    return InMemoryCache()
