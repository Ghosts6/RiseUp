"""Domain errors mapped to HTTP responses by the API layer."""

from __future__ import annotations

from typing import Any


class DomainError(Exception):
    status_code = 400
    code = "bad_request"

    def __init__(self, message: str, **extra: Any) -> None:
        super().__init__(message)
        self.message = message
        self.extra = extra


class ValidationFailed(DomainError):
    status_code = 422
    code = "validation_error"


class NotFound(DomainError):
    status_code = 404
    code = "not_found"


class Conflict(DomainError):
    status_code = 409
    code = "conflict"


class LimitReached(DomainError):
    status_code = 409
    code = "limit_reached"


class ServiceUnavailable(DomainError):
    status_code = 503
    code = "service_unavailable"
