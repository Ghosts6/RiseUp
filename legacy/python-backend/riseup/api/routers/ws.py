from __future__ import annotations

from fastapi import APIRouter, HTTPException, WebSocket, WebSocketDisconnect

from riseup.api.deps import resolve_user_id

router = APIRouter(tags=["realtime"])


@router.websocket("/ws")
async def events_socket(websocket: WebSocket, token: str | None = None) -> None:
    container = websocket.app.state.container
    try:
        user_id = resolve_user_id(container, token)
    except HTTPException:
        await websocket.close(code=4401)
        return
    await container.hub.connect(user_id, websocket)
    try:
        await websocket.send_json({"type": "hello", "user_id": user_id})
        while True:
            message = await websocket.receive_text()
            if message == "ping":
                await websocket.send_json({"type": "pong"})
    except WebSocketDisconnect:
        pass
    finally:
        container.hub.disconnect(user_id, websocket)
