"""Agent escalation (F04-F05) including the stage-1 behavioural tests BR-01..BR-03."""

from helpers import create_alarm, escalations, ring_and_timeout, solve_challenge


def test_llm_decides_first_method(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [attempt] = escalations(h, alarm["id"])
    assert attempt["method"] == "call"
    assert attempt["decided_by"].startswith("llm:mock")
    assert not attempt["fallback_used"]
    assert attempt["status"] == "sent"
    assert h.twilio.sent[-1]["channel"] == "call"


def test_no_response_moves_to_next_method(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    h.advance(seconds=h.container.settings.escalation_response_seconds)
    attempts = escalations(h, alarm["id"])
    assert [a["method"] for a in attempts] == ["call", "sms"]
    assert attempts[0]["status"] == "no_response"


def test_br01_only_enabled_channels_are_chosen(h):
    h.client.patch("/api/prefs", json={"enabled_channels": ["sms", "alexa"]})
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    for _ in range(2):
        h.advance(seconds=h.container.settings.escalation_response_seconds)
    methods = [a["method"] for a in escalations(h, alarm["id"])]
    assert methods == ["sms", "alexa"]


def test_br01_invalid_llm_pick_is_rejected(make_harness):
    h = make_harness(llm_mode="disabled_method")
    h.client.patch("/api/prefs", json={"enabled_channels": ["sms", "alexa"]})
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [attempt] = escalations(h, alarm["id"])
    assert attempt["fallback_used"] and attempt["decided_by"] == "fallback"
    assert attempt["method"] == "sms"  # LLM said "call", which is disabled
    assert not [s for s in h.twilio.sent if s["channel"] == "call"]


def test_br02_invalid_json_retries_once_then_falls_back(make_harness):
    h = make_harness(llm_mode="invalid_json")
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [attempt] = escalations(h, alarm["id"])
    assert len(h.llm.calls) == 2
    assert attempt["fallback_used"] and attempt["decided_by"] == "fallback"
    assert attempt["method"] == "call"
    assert "Rule-based fallback" in attempt["reasoning"]


def test_br03_prefers_method_with_best_history(h):
    h.client.patch("/api/prefs", json={"escalation_order": ["sms", "call", "alexa", "push"]})
    with h.services() as s:
        profile = s.repos.profiles.for_user(1)
        profile.escalation_stats = {
            "call": {"attempts": 20, "successes": 19, "success_rate": 0.95},
            "sms": {"attempts": 20, "successes": 8, "success_rate": 0.4},
        }
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [attempt] = escalations(h, alarm["id"])
    assert attempt["method"] == "call"
    assert not attempt["fallback_used"]
    assert "95%" in attempt["reasoning"]


def test_llm_outage_uses_fallback_ladder(make_harness):
    h = make_harness(llm_mode="error")
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [attempt] = escalations(h, alarm["id"])
    assert attempt["fallback_used"]
    assert "LLM unavailable" in attempt["reasoning"]


def test_failed_tool_triggers_immediate_redecision(make_harness):
    h = make_harness(failures={"call"})
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    attempts = escalations(h, alarm["id"])
    assert [(a["method"], a["status"]) for a in attempts] == [("call", "failed"), ("sms", "sent")]
    assert "simulated call failure" in attempts[0]["error"]


def test_quiet_hours_skip_intrusive_methods(h):
    h.client.patch("/api/prefs", json={"quiet_hours_start": "22:00", "quiet_hours_end": "07:00"})
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    assert escalations(h, alarm["id"])[0]["method"] == "alexa"  # call and sms are muted


def test_disabled_channels_are_never_used(h):
    h.client.patch("/api/prefs", json={"enabled_channels": ["push"]})
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    assert escalations(h, alarm["id"])[0]["method"] == "push"
    h.advance(seconds=61)
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "missed"


def test_verification_during_escalation_acknowledges(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    h.advance(seconds=20)
    result = h.client.post(
        f"/api/alarms/{alarm['id']}/verify", json=solve_challenge(h, alarm["id"])
    )
    assert result.json()["success"]
    [attempt] = escalations(h, alarm["id"])
    assert attempt["status"] == "confirmed"
    assert attempt["response_time_seconds"] == 20
    h.advance(seconds=120)
    assert len(escalations(h, alarm["id"])) == 1  # follow-up was cancelled


def test_sms_reply_acknowledges_via_webhook(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    reply = h.client.post(
        "/api/webhooks/twilio/sms", data={"From": "+15555550100", "Body": "AWAKE"}
    )
    assert reply.status_code == 200
    assert h.client.get(f"/api/alarms/{alarm['id']}").json()["status"] == "completed"


def test_emergency_contact_requires_consent(h):
    h.client.patch("/api/prefs", json={"enabled_channels": ["emergency_contact"]})
    contact = h.client.post("/api/contacts", json={"name": "Sam", "phone": "+14165550123"}).json()
    assert contact["consent_status"] == "pending"

    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    [skipped] = escalations(h, alarm["id"])
    assert skipped["status"] == "skipped" and skipped["method"] is None

    token = contact["consent_link"].rsplit("/", 1)[1]
    assert h.client.get(f"/api/contacts/consent/{token}?answer=yes").status_code == 200
    second = create_alarm(h, time="08:00")
    ring_and_timeout(h, second["id"])
    attempt = escalations(h, second["id"])[0]
    assert attempt["method"] == "emergency_contact" and attempt["status"] == "sent"
    assert h.twilio.sent[-1]["to"] == "+14165550123"


def test_history_explains_the_decision(h):
    alarm = create_alarm(h)
    ring_and_timeout(h, alarm["id"])
    items = h.client.get("/api/history", params={"type": "escalation"}).json()["items"]
    assert items and items[0]["explainable"]
    why = h.client.get(f"/api/history/escalation/{items[0]['ref_id']}").json()
    assert why["method"] == "call"
    assert why["inputs"]["available_methods"] == ["call", "sms", "alexa", "push"]
    assert why["label"].startswith("AI decision")
