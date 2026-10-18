"""Rule-based sentence parser used by `MockProvider` to imitate LLM reminder extraction."""

from __future__ import annotations

import re
from datetime import datetime, timedelta

WEEKDAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
CATEGORY_WORDS = {
    "health": [
        "gym",
        "medicine",
        "pill",
        "pills",
        "doctor",
        "dentist",
        "run",
        "workout",
        "water",
        "vitamin",
        "yoga",
        "walk",
        "meds",
    ],
    "work": [
        "meeting",
        "email",
        "report",
        "boss",
        "client",
        "project",
        "deadline",
        "standup",
        "presentation",
        "submit",
        "assignment",
        "lab",
        "work",
    ],
    "personal": [
        "mom",
        "dad",
        "mother",
        "father",
        "friend",
        "birthday",
        "groceries",
        "call",
        "laundry",
        "clean",
        "pay",
        "bill",
        "rent",
        "family",
    ],
}


def _parse_time(text: str) -> tuple[int, int] | None:
    if re.search(r"\bnoon\b", text):
        return 12, 0
    if re.search(r"\bmidnight\b", text):
        return 0, 0
    match = re.search(r"\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm|a\.m\.|p\.m\.)\b", text)
    if match:
        hour, minute = int(match.group(1)), int(match.group(2) or 0)
        meridiem = match.group(3).replace(".", "")
        if hour > 12:
            return None
        if meridiem == "pm" and hour != 12:
            hour += 12
        if meridiem == "am" and hour == 12:
            hour = 0
        return hour, minute
    match = re.search(r"\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b", text)
    if match:
        return int(match.group(1)), int(match.group(2))
    match = re.search(r"\bat\s+(\d{1,2})\b", text)
    if match and int(match.group(1)) <= 23:
        hour = int(match.group(1))
        return (hour + 12 if 1 <= hour <= 6 else hour), 0
    return None


def parse_reminder_text(text: str, now_local_iso: str) -> dict:
    now = datetime.fromisoformat(now_local_iso) if now_local_iso else datetime.now()
    lowered = text.lower().strip()
    due: datetime | None = None

    relative = re.search(r"\bin\s+(\d+)\s*(minutes?|mins?|hours?|hrs?)\b", lowered)
    if relative:
        amount = int(relative.group(1))
        unit = relative.group(2)
        due = now + (timedelta(hours=amount) if unit.startswith("h") else timedelta(minutes=amount))
    else:
        day = now.date()
        explicit_day = False
        date_match = re.search(r"\b(\d{4}-\d{2}-\d{2})\b", lowered)
        if date_match:
            day = datetime.fromisoformat(date_match.group(1)).date()
            explicit_day = True
        elif "tomorrow" in lowered:
            day = day + timedelta(days=1)
            explicit_day = True
        else:
            for index, name in enumerate(WEEKDAYS):
                if re.search(rf"\b{name}\b", lowered):
                    delta = (index - day.weekday()) % 7 or 7
                    day = day + timedelta(days=delta)
                    explicit_day = True
                    break
        hm = _parse_time(lowered)
        if hm is None and "tonight" in lowered:
            hm = (20, 0)
        if hm is None and "morning" in lowered:
            hm = (9, 0)
        if hm is None and "evening" in lowered:
            hm = (19, 0)
        if hm is not None:
            due = datetime.combine(day, datetime.min.time()).replace(hour=hm[0], minute=hm[1])
            if not explicit_day and due < now:
                due += timedelta(days=1)

    title = re.sub(
        r"^(please\s+)?(remind me to|remind me|remember to|don't forget to)\s+",
        "",
        text.strip(),
        flags=re.I,
    )
    patterns = [
        r"\bin\s+\d+\s*(minutes?|mins?|hours?|hrs?)\b",
        r"\btomorrow\b",
        r"\btoday\b",
        r"\btonight\b",
        r"\b(this\s+)?(morning|evening)\b",
        r"\b(on\s+|next\s+)?(" + "|".join(WEEKDAYS) + r")\b",
        r"\b(at\s+)?\d{1,2}(:\d{2})?\s*(am|pm|a\.m\.|p\.m\.)",
        r"\bat\s+\d{1,2}(:\d{2})?\b",
        r"\b\d{4}-\d{2}-\d{2}\b",
        r"\b(every\s+day|daily|every\s+week|weekly|on\s+weekdays|weekdays)\b",
        r"\b(urgent(ly)?|asap|important|high priority|low priority)\b",
        r"\bat noon\b",
        r"\bnoon\b",
    ]
    for pattern in patterns:
        title = re.sub(pattern, "", title, flags=re.I)
    title = re.sub(r"\s{2,}", " ", title).strip(" ,.!-")
    title = title[:1].upper() + title[1:] if title else ""

    priority = "medium"
    if re.search(r"\b(urgent(ly)?|asap|critical)\b", lowered):
        priority = "critical"
    elif re.search(r"\b(important|high priority)\b", lowered):
        priority = "high"
    elif "low priority" in lowered:
        priority = "low"

    category = "other"
    words = set(re.findall(r"[a-z']+", lowered))
    for name, keys in CATEGORY_WORDS.items():
        if words & set(keys):
            category = name
            break

    repeat = "none"
    if re.search(r"\b(every\s+day|daily)\b", lowered):
        repeat = "daily"
    elif re.search(r"\b(every\s+week|weekly)\b", lowered):
        repeat = "weekly"
    elif "weekdays" in lowered:
        repeat = "weekdays"

    question = None
    if not title:
        question = "What should I remind you about?"
    elif due is None:
        question = "At what time?"
    return {
        "title": title,
        "due": due.strftime("%Y-%m-%dT%H:%M") if due else None,
        "priority": priority,
        "category": category,
        "repeat": repeat,
        "needs_clarification": question is not None,
        "clarification_question": question,
    }
