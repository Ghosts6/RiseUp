"""Learning, analytics and recommendations (F11-F13) plus preferences (F06)."""

from datetime import datetime
from types import SimpleNamespace

from helpers import create_alarm, ring_and_timeout, solve_challenge
from riseup.models import Recommendation
from riseup.user_profile.analytics import AnalyticsEngine


def esc(method, status, rt=None):
    return SimpleNamespace(method=method, status=status, response_time_seconds=rt)


def alarm_log(event, hour, task_type="math", seconds=None):
    return SimpleNamespace(
        event=event,
        created_at=datetime(2026, 10, 19, hour, 0),
        task_type=task_type,
        details={"seconds_to_verify": seconds} if seconds else {},
    )


def reminder_log(event, category):
    return SimpleNamespace(event=event, category=category, created_at=datetime(2026, 10, 19, 15, 0))


def test_compute_metrics_is_pure_and_skips_bad_rows():
    metrics = AnalyticsEngine.compute_metrics(
        escalations=[
            esc("call", "confirmed", 30),
            esc("call", "no_response"),
            esc("sms", "confirmed", 10),
            esc(None, "confirmed"),
            esc("push", "sent"),
        ],
        alarm_logs=[
            alarm_log("triggered", 11),
            alarm_log("verified", 11, seconds=40),
            alarm_log("verified", 12, "trivia", seconds=20),
            alarm_log("missed", 11),
        ],
        reminder_logs=[
            reminder_log("completed", "work"),
            reminder_log("missed", "work"),
            reminder_log("completed", None),
        ],
        tz_name="America/Toronto",
    )
    assert metrics["escalation_stats"]["call"] == {
        "attempts": 2,
        "successes": 1,
        "success_rate": 0.5,
        "avg_response_time": 30.0,
    }
    assert "push" not in metrics["escalation_stats"]  # still pending, not an outcome
    assert metrics["skipped_rows"] == 2
    assert metrics["verification_stats"]["fastest_task_type"] == "trivia"
    assert metrics["task_completion_rates"]["work"]["rate"] == 0.5
    assert metrics["alarm_patterns"]["avg_wake_time"] == "07:30"
    assert metrics["data_status"] == "ok"


def test_stats_endpoint_reflects_activity(h):
    empty = h.client.get("/api/stats", params={"range": "7d"}).json()
    assert empty["empty"]
    alarm = create_alarm(h)
    h.client.post(f"/api/alarms/{alarm['id']}/trigger")
    h.advance(seconds=45)
    h.client.post(f"/api/alarms/{alarm['id']}/verify", json=solve_challenge(h, alarm["id"]))
    stats = h.client.get("/api/stats", params={"range": "7d"}).json()
    assert stats["verification"]["verified"] == 1
    assert stats["wake_times"][0]["time"] == "06:00"
    assert h.client.get("/api/stats", params={"range": "nope"}).status_code == 422


def test_profile_learns_escalation_success(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    h.advance(seconds=10)
    h.client.post("/api/escalations/acknowledge", json={"via": "dashboard"})
    profile = h.client.post("/api/profile/recompute").json()
    assert profile["escalation_stats"]["call"]["successes"] == 1
    assert profile["optimal_settings"]["best_escalation_method"] == "call"


def test_recommendations_accept_and_dismiss(h):
    h.client.patch(
        "/api/prefs",
        json={"escalation_order": ["push", "alexa", "sms", "call", "emergency_contact"]},
    )
    for minute in range(3):  # call works every time; push is first in the order
        alarm = create_alarm(h, time=f"07:0{minute}")
        h.client.patch("/api/prefs", json={"enabled_channels": ["call"]})
        ring_and_timeout(h, alarm["id"])
        h.advance(seconds=15)
        h.client.post("/api/escalations/acknowledge", json={})
        h.client.patch("/api/prefs", json={"enabled_channels": ["call", "sms", "alexa", "push"]})
    h.client.post("/api/profile/recompute")

    generated = h.client.post("/api/recommendations/generate").json()
    assert generated["items"], generated
    rec = generated["items"][0]
    assert rec["status"] == "pending"
    accepted = h.client.post(f"/api/recommendations/{rec['id']}/accept").json()
    assert accepted["status"] == "accepted"
    assert rec["id"] not in [r["id"] for r in h.client.get("/api/recommendations").json()]
    setting, value = rec["change"]["setting"], rec["change"]["value"]
    assert h.client.get("/api/prefs").json()["preferences"][setting] == value


def test_type_is_suppressed_after_two_dismissals(h):
    with h.services() as s:
        for _ in range(2):
            rec = Recommendation(
                user_id=1,
                type="quiet_hours",
                title="Add quiet hours",
                rationale="test",
                change={"setting": "quiet_hours_start", "value": "23:00"},
                source="test",
                status="pending",
            )
            s.db.add(rec)
            s.db.flush()
            s.recommendations.dismiss(1, rec.id)
    prefs = h.client.get("/api/prefs").json()["preferences"]
    assert "quiet_hours" in prefs["suppressed_recommendation_types"]


def test_preferences_validation_and_warnings(h):
    bad = h.client.patch("/api/prefs", json={"enabled_channels": ["telepathy"]})
    assert bad.status_code == 422
    bad_quiet = h.client.patch("/api/prefs", json={"quiet_hours_start": "9pm"})
    assert bad_quiet.status_code == 422
    none = h.client.patch("/api/prefs", json={"enabled_channels": []}).json()
    assert none["warnings"]
    assert h.client.post("/api/prefs/test", json={"channel": "sms"}).json()["success"]
