"""APIClient: HTTP + auth + error mapping for CLI commands."""

from __future__ import annotations

from typing import Any

import httpx


class APIError(Exception):
    def __init__(self, status: int, message: str, data: dict | None = None) -> None:
        super().__init__(message)
        self.status = status
        self.message = message
        self.data = data or {}

    @property
    def is_user_error(self) -> bool:
        return 400 <= self.status < 500


class APIClient:
    def __init__(
        self,
        base_url: str,
        token: str | None = None,
        timeout: float = 15,
        transport: httpx.BaseTransport | None = None,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.token = token
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        self._client = httpx.Client(
            base_url=self.base_url, headers=headers, timeout=timeout, transport=transport
        )

    def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        try:
            response = self._client.request(method, path, **kwargs)
        except httpx.HTTPError as exc:
            raise APIError(0, f"Cannot reach RiseUp server at {self.base_url} ({exc})") from exc
        if response.status_code == 204:
            return None
        try:
            body = response.json()
        except ValueError:
            body = {"detail": response.text}
        if response.status_code >= 400:
            detail = body.get("detail") if isinstance(body, dict) else body
            if isinstance(detail, list):  # FastAPI request validation
                detail = "; ".join(
                    f"{'.'.join(map(str, d.get('loc', [])))}: {d.get('msg')}" for d in detail
                )
            raise APIError(
                response.status_code, str(detail), body if isinstance(body, dict) else {}
            )
        return body

    def get(self, path: str, **params: Any) -> Any:
        return self._request("GET", path, params={k: v for k, v in params.items() if v is not None})

    def post(self, path: str, body: dict | None = None, **params: Any) -> Any:
        return self._request(
            "POST", path, json=body or {}, params={k: v for k, v in params.items() if v is not None}
        )

    def patch(self, path: str, body: dict, **params: Any) -> Any:
        return self._request(
            "PATCH", path, json=body, params={k: v for k, v in params.items() if v is not None}
        )

    def delete(self, path: str) -> Any:
        return self._request("DELETE", path)
