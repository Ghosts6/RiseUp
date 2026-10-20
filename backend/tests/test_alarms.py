from helpers import create_alarm, solve_challenge, wrong_answer


def test_create_list_and_next_trigger(h):
    alarm = create_alarm(h, repeat_pattern="weekdays")
    assert alarm["status"] == "scheduled"
    assert alarm["next_trigger_local"] == "2026-10-19 07:00"  # local now is Mon 06:00
    assert [a["id"] for a in h.client.get("/api/alarms").json()] == [alarm["id"]]


def test_validation_errors_name_the_field(h):
    bad_time = h.client.post("/api/alarms", json={"time": "25:00"})
    assert bad_time.status_code == 422 and bad_time.json()["field"] == "time"
    custom = h.client.post(
        "/api/alarms", json={"time": "07:00", "verification_task_type": "custom"}
    )
    assert custom.status_code == 422 and custom.json()["field"] == "custom_task_text"
    repeat = h.client.post("/api/alarms", json={"time": "07:00", "repeat_pattern": "custom:"})
    assert repeat.status_code == 422


def test_alarm_limit(make_harness):
    h = make_harness(max_alarms_per_user=2)
    create_alarm(h)
    create_alarm(h, time="08:00")
    response = h.client.post("/api/alarms", json={"time": "09:00"})
    assert response.status_code == 409


def test_scheduled_alarm_rings_at_its_time(h):
    alarm = create_alarm(h)
    h.advance(minutes=59)
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "scheduled"
    h.advance(minutes=1)
    challenge = h.client.get(f"/api/alarms/{alarm['id']}/challenge").json()
    assert challenge["status"] == "ringing"
    assert challenge["challenge"]["type"] == "math"
    assert "answer" not in challenge["challenge"]


def test_wrong_answer_gives_new_puzzle_then_correct_answer_starts_checkin(h):
    alarm = create_alarm(h)
    h.client.post(f"/api/alarms/{alarm['id']}/trigger")
    before = h.client.get(f"/api/alarms/{alarm['id']}/challenge").json()["challenge"]

    wrong = h.client.post(
        f"/api/alarms/{alarm['id']}/verify", json=wrong_answer(h, alarm["id"])
    ).json()
    assert not wrong["success"] and wrong["attempts"] == 1
    assert wrong["challenge"]["type"] == "math"
    assert before["type"] == "math"

    ok = h.client.post(
        f"/api/alarms/{alarm['id']}/verify", json=solve_challenge(h, alarm["id"])
    ).json()
    assert ok["success"] and ok["checkin_at"]
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "awaiting_checkin"

    h.advance(minutes=7)  # check-in is sent
    confirmed = h.client.post("/api/checkins/confirm").json()
    assert confirmed["status"] == "confirmed"
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "completed"


def test_verify_when_not_ringing_conflicts(h):
    alarm = create_alarm(h)
    assert (
        h.client.post(f"/api/alarms/{alarm['id']}/verify", json={"answer": "1"}).status_code == 409
    )


def test_snooze_rules(h):
    no_snooze = create_alarm(h)
    h.client.post(f"/api/alarms/{no_snooze['id']}/trigger")
    assert h.client.post(f"/api/alarms/{no_snooze['id']}/snooze").status_code >= 400

    snoozy = create_alarm(h, time="08:00", snooze_enabled=True)
    h.client.post(f"/api/alarms/{snoozy['id']}/trigger")
    assert h.client.post(f"/api/alarms/{snoozy['id']}/snooze").json()["status"] == "snoozed"
    h.advance(minutes=h.container.settings.snooze_minutes)
    assert h.client.get(f"/api/alarms/{snoozy['id']}").json()["status"] == "ringing"


def test_missed_checkin_escalates(h):
    alarm = create_alarm(h)
    h.client.post(f"/api/alarms/{alarm['id']}/trigger")
    h.client.post(f"/api/alarms/{alarm['id']}/verify", json=solve_challenge(h, alarm["id"]))
    h.advance(minutes=7)  # check-in sent
    h.advance(minutes=5, seconds=1)  # no response
    state = h.client.get(f"/api/alarms/{alarm['id']}/escalation").json()
    assert state["status"] == "escalating"
    assert state["attempts"][0]["trigger"] == "checkin_missed"


def test_toggle_and_delete(h):
    alarm = create_alarm(h)
    off = h.client.post(f"/api/alarms/{alarm['id']}/toggle", json={"active": False}).json()
    assert not off["is_active"] and off["status"] == "disabled"
    h.advance(hours=2)
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "disabled"
    assert h.client.delete(f"/api/alarms/{alarm['id']}").status_code == 204
    assert h.client.get(f"/api/alarms/{alarm['id']}").status_code == 404
