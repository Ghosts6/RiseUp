"""Timezone and schedule helpers (pure functions)."""

from __future__ import annotations

import re
from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

HHMM_RE = re.compile(r"^([01]\d|2[0-3]):([0-5]\d)$")
WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


def get_zone(tz_name: str | None) -> ZoneInfo:
    try:
        return ZoneInfo(tz_name or "UTC")
    except (ZoneInfoNotFoundError, ValueError):
        return ZoneInfo("UTC")


def parse_hhmm(value: str) -> time:
    match = HHMM_RE.match(value or "")
    if not match:
        raise ValueError("Please enter time in HH:MM format")
    return time(int(match.group(1)), int(match.group(2)))


def is_valid_hhmm(value: str | None) -> bool:
    return bool(value and HHMM_RE.match(value))


def to_local(dt_utc: datetime, tz_name: str | None) -> datetime:
    return dt_utc.replace(tzinfo=timezone.utc).astimezone(get_zone(tz_name))


def to_utc_naive(dt_local: datetime, tz_name: str | None = None) -> datetime:
    if dt_local.tzinfo is None:
        dt_local = dt_local.replace(tzinfo=get_zone(tz_name))
    return dt_local.astimezone(timezone.utc).replace(tzinfo=None)


def repeat_days(pattern: str) -> set[int] | None:
    """Return allowed weekday indexes for a repeat pattern, or None for 'once'."""
    pattern = (pattern or "once").lower()
    if pattern == "once":
        return None
    if pattern == "daily":
        return set(range(7))
    if pattern == "weekdays":
        return {0, 1, 2, 3, 4}
    if pattern == "weekends":
        return {5, 6}
    if pattern.startswith("custom:"):
        days = {WEEKDAYS.index(d.strip()) for d in pattern[7:].split(",") if d.strip() in WEEKDAYS}
        if not days:
            raise ValueError("Custom repeat needs days, e.g. custom:mon,wed,fri")
        return days
    raise ValueError("Repeat must be once, daily, weekdays, weekends, or custom:mon,wed")


def validate_repeat(pattern: str) -> None:
    repeat_days(pattern)


def next_alarm_occurrence(
    hhmm: str, pattern: str, tz_name: str | None, now_utc: datetime
) -> datetime:
    """Next trigger time (naive UTC) strictly after `now_utc`."""
    alarm_time = parse_hhmm(hhmm)
    days = repeat_days(pattern)
    now_local = to_local(now_utc, tz_name)
    zone = get_zone(tz_name)
    for offset in range(0, 8):
        day: date = now_local.date() + timedelta(days=offset)
        if days is not None and day.weekday() not in days:
            continue
        candidate = datetime.combine(day, alarm_time, tzinfo=zone)
        if candidate > now_local:
            return to_utc_naive(candidate)
    raise ValueError("Could not compute next alarm occurrence")


def next_reminder_occurrence(
    previous_due_utc: datetime, pattern: str, series_time: str | None, tz_name: str | None
) -> datetime | None:
    """Next occurrence for a recurring reminder, or None if not recurring."""
    pattern = (pattern or "none").lower()
    if pattern == "none":
        return None
    prev_local = to_local(previous_due_utc, tz_name)
    at = (
        parse_hhmm(series_time)
        if series_time and is_valid_hhmm(series_time)
        else prev_local.timetz()
    )
    zone = get_zone(tz_name)
    step_days = {"daily": 1, "weekly": 7, "weekdays": 1}.get(pattern)
    if step_days is None:
        raise ValueError("Repeat must be none, daily, weekly, or weekdays")
    day = prev_local.date()
    while True:
        day = day + timedelta(days=step_days)
        if pattern == "weekdays" and day.weekday() >= 5:
            continue
        local = datetime.combine(day, time(at.hour, at.minute), tzinfo=zone)
        return to_utc_naive(local)


def in_quiet_hours(
    now_utc: datetime, tz_name: str | None, start: str | None, end: str | None
) -> bool:
    if not (is_valid_hhmm(start) and is_valid_hhmm(end)):
        return False
    local = to_local(now_utc, tz_name).time().replace(second=0, microsecond=0, tzinfo=None)
    s, e = parse_hhmm(start or ""), parse_hhmm(end or "")
    if s <= e:
        return s <= local < e
    return local >= s or local < e


def parse_range(value: str) -> int:
    """Parse '7d' / '30d' / '90d' style ranges into days."""
    match = re.fullmatch(r"(\d+)\s*d?", (value or "").strip().lower())
    if not match:
        raise ValueError("Range must look like 7d, 30d, or 90d")
    days = int(match.group(1))
    if not 1 <= days <= 365:
        raise ValueError("Range must be between 1d and 365d")
    return days
