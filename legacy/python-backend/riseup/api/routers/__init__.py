from riseup.api.routers import (
    alarms,
    escalations,
    history,
    insights,
    preferences,
    reminders,
    system,
    ws,
)

ROUTERS = [
    system.router,
    alarms.router,
    alarms.checkin_router,
    escalations.router,
    preferences.router,
    preferences.contacts_router,
    reminders.router,
    insights.router,
    history.router,
    ws.router,
]
