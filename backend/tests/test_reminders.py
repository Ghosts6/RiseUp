"""Task reminders (F07-F10): NL parsing, scheduling, escalation levels, prioritization."""


def add(h, title="Report", due="2026-10-19T06:10", **extra):
    response = h.client.post("/api/reminders", json={"title": title, "due": due, **extra})
    assert response.status_code == 201, response.text
    return response.json()


def get(h, reminder_id):
    return h.client.get(f"/api/reminders/{reminder_id}").json()


def test_parse_returns_draft_without_saving(h):
    parsed = h.client.post("/api/reminders/parse", json={"text": "call mom tomorrow at 9am"}).json()
    assert parsed["status"] == "ok"
    assert parsed["draft"]["due"] == "2026-10-20T09:00"
    assert h.client.get("/api/reminders").json() == []


def test_parse_handles_vague_and_past_times(h):
    vague = h.client.post("/api/reminders/parse", json={"text": "buy milk"}).json()
    assert vague["status"] == "needs_clarification" and vague["question"] == "At what time?"
    past = h.client.post("/api/reminders/parse", json={"text": "standup 2026-10-19 at 5am"}).json()
    assert past["status"] == "past_time"
    assert past["draft"]["due"] == "2026-10-20T05:00"


def test_past_due_is_rejected_with_suggestion(h):
    response = h.client.post("/api/reminders", json={"title": "Late", "due": "2026-10-19T05:00"})
    assert response.status_code == 422
    assert response.json()["suggestion"] == "2026-10-20T05:00"


def test_escalation_levels_until_missed(h):
    reminder = add(h)
    h.advance(minutes=10)
    assert (get(h, reminder["id"])["status"], get(h, reminder["id"])["escalation_level"]) == (
        "notifying",
        1,
    )
    h.advance(minutes=5)
    assert get(h, reminder["id"])["escalation_level"] == 2
    h.advance(minutes=10)
    assert get(h, reminder["id"])["escalation_level"] == 3
    h.advance(minutes=10)
    assert get(h, reminder["id"])["status"] == "missed"
    assert len(h.firebase.sent) == 3


def test_completing_stops_escalation_and_spawns_next_repeat(h):
    reminder = add(h, title="Pills", repeat_pattern="daily", category="health")
    h.advance(minutes=10)
    done = h.client.post(f"/api/reminders/{reminder['id']}/complete", json={}).json()
    assert done["status"] == "completed"
    h.advance(minutes=30)
    assert get(h, reminder["id"])["escalation_level"] == 1
    upcoming = h.client.get("/api/reminders", params={"status": "open"}).json()
    assert [(r["title"], r["due_local"]) for r in upcoming] == [("Pills", "2026-10-20 06:10")]


def test_snooze_reschedules(h):
    reminder = add(h)
    h.advance(minutes=10)
    snoozed = h.client.post(f"/api/reminders/{reminder['id']}/snooze", json={"minutes": 20}).json()
    assert snoozed["status"] == "scheduled" and snoozed["due_local"] == "2026-10-19 06:30"


def test_simultaneous_reminders_are_prioritized_and_logged(h):
    add(h, title="Water plants", priority="low", category="other")
    add(h, title="Submit lab", priority="critical", category="work")
    h.advance(minutes=10)
    items = h.client.get("/api/history", params={"type": "prioritization"}).json()["items"]
    assert len(items) == 1
    why = h.client.get(f"/api/history/prioritization/{items[0]['ref_id']}").json()
    assert why["titles"] == ["Submit lab", "Water plants"]
    assert not why["fallback_used"]


def test_prioritization_fallback_when_llm_down(make_harness):
    h = make_harness(llm_mode="error")
    add(h, title="Low", priority="low")
    add(h, title="High", priority="high")
    h.advance(minutes=10)
    item = h.client.get("/api/history", params={"type": "prioritization"}).json()["items"][0]
    why = h.client.get(f"/api/history/prioritization/{item['ref_id']}").json()
    assert why["fallback_used"] and why["titles"] == ["High", "Low"]


def test_filters_and_delete(h):
    a = add(h, title="A", category="work")
    add(h, title="B", category="health")
    assert [
        r["title"] for r in h.client.get("/api/reminders", params={"category": "work"}).json()
    ] == ["A"]
    assert h.client.delete(f"/api/reminders/{a['id']}").status_code == 204
    assert [r["title"] for r in h.client.get("/api/reminders").json()] == ["B"]
