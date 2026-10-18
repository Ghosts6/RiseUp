from __future__ import annotations

from contextlib import AbstractContextManager

from fastapi import Header, HTTPException, Query, Request

from riseup.container import AppServices, Container
from riseup.repositories import UserRepository


def get_container(request: Request) -> Container:
    return request.app.state.container


def uow(request: Request) -> AbstractContextManager[AppServices]:
    """Open a unit of work; commit happens before the endpoint returns its payload."""
    return get_container(request).unit_of_work()


def resolve_user_id(container: Container, token: str | None) -> int:
    db = container.session_factory()
    try:
        repo = UserRepository(db)
        user = repo.by_token(token) if token else None
        if user is not None:
            return user.id
        if container.settings.require_auth:
            raise HTTPException(status_code=401, detail="Invalid or missing API token")
        fallback = repo.first()
        if fallback is None:
            raise HTTPException(status_code=401, detail="No users configured")
        return fallback.id
    finally:
        db.close()


def current_user_id(
    request: Request,
    authorization: str | None = Header(default=None),
    token: str | None = Query(default=None),
) -> int:
    bearer = None
    if authorization and authorization.lower().startswith("bearer "):
        bearer = authorization[7:].strip()
    return resolve_user_id(get_container(request), bearer or token)
