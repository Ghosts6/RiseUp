"""CLI commands run in-process against the API through FastAPI's TestClient."""

import json

import httpx
import pytest
from typer.testing import CliRunner

import riseup_cli.app as cli
from helpers import solve_challenge
from riseup_cli.api_client import APIClient


@pytest.fixture
def run(h, monkeypatch):
    def client_factory(base_url, token=None, **_):
        api = APIClient(base_url, token)
        api._client = h.client
        return api

    monkeypatch.setattr(cli, "APIClient", client_factory)
    runner = CliRunner()

    def invoke(*args):
        return runner.invoke(cli.app, ["--server", "http://testserver", "-y", *args])

    return invoke


def test_alarm_add_list_and_json_output(run):
    added = run("alarm", "add", "07:00", "--label", "Work", "--repeat", "weekdays")
    assert added.exit_code == 0, added.output
    assert "Alarm set for 07:00" in added.output
    listed = run("alarm", "list")
    assert "Work" in listed.output and "weekdays" in listed.output
    data = json.loads(run("--json", "alarm", "list").output)
    assert data[0]["time"] == "07:00"


def test_user_errors_exit_1(run):
    bad = run("alarm", "add", "7am")
    assert bad.exit_code == 1 and "HH:MM" in bad.output
    assert run("alarm", "delete", "99").exit_code == 1


def test_verify_flow_exit_codes(run, h):
    run("alarm", "add", "07:00")
    assert "Challenge (math)" in run("alarm", "trigger", "1").output
    answer = solve_challenge(h, 1)["answer"]
    wrong = run("alarm", "verify", "1", "--answer", str(int(answer) + 1))
    assert wrong.exit_code == 1 and "Task not valid" in wrong.output
    ok = run("alarm", "verify", "1", "--answer", solve_challenge(h, 1)["answer"])
    assert ok.exit_code == 0 and "Task completed" in ok.output


def test_remind_parses_and_creates(run):
    result = run("remind", "call mom tomorrow at 9am")
    assert result.exit_code == 0, result.output
    assert "Call mom" in result.output and "2026-10-20 09:00" in result.output
    assert "Call mom" in run("task", "list").output


def test_contact_and_prefs_commands(run):
    added = run("contact", "add", "Sam", "+14165550123")
    assert added.exit_code == 0 and "consent" in added.output.lower()
    assert run("prefs", "set", "--channels", "sms,push").exit_code == 0
    assert "sms, push" in run("prefs", "show").output


def test_server_unreachable_exits_2(monkeypatch):
    def broken(base_url, token=None, **_):
        def fail(request):
            raise httpx.ConnectError("refused")

        return APIClient(base_url, token, transport=httpx.MockTransport(fail))

    monkeypatch.setattr(cli, "APIClient", broken)
    result = CliRunner().invoke(cli.app, ["alarm", "list"])
    assert result.exit_code == 2
    assert "Cannot reach RiseUp server" in result.output
