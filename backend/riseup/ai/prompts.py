"""PromptBuilder: structured prompts with JSON output contracts (AI-03)."""

from __future__ import annotations

import json
from typing import Any

SYSTEM_PROMPT = (
    "You are RiseUp's decision agent. You help users wake up and finish tasks. "
    "Always answer with a single JSON object that follows the OUTPUT FORMAT exactly. "
    "Never invent options that are not listed in the context."
)


def _render(
    task_type: str, context: dict[str, Any], task: str, output: str, constraints: str
) -> str:
    return (
        f"TASK_TYPE: {task_type}\n"
        f"CONTEXT_JSON:\n{json.dumps(context, default=str, indent=2)}\n"
        f"TASK:\n{task}\n\n"
        f"OUTPUT FORMAT:\n{output}\n\n"
        f"CONSTRAINTS:\n{constraints}\n"
    )


class PromptBuilder:
    system = SYSTEM_PROMPT

    def build_escalation_prompt(self, context: dict[str, Any]) -> str:
        methods = " | ".join(f'"{m}"' for m in context.get("available_methods", []))
        return _render(
            "escalation",
            context,
            "The user did not respond (see `trigger`). Analyze their escalation history and "
            "success rates and recommend ONE escalation method to try next.",
            "{\n"
            f'  "method": {methods},\n'
            '  "reasoning": "<one or two sentences explaining why>",\n'
            '  "confidence": <0.0-1.0>,\n'
            '  "fallback": "<another available method or null>"\n'
            "}",
            "- ONLY use methods in available_methods\n"
            "- Prefer the method with the highest historical success rate\n"
            "- Methods in tried_this_episode already failed or got no response; "
            "do not repeat them\n"
            "- emergency_contact is a last resort\n"
            "- DO NOT make up methods",
        )

    def build_parse_prompt(self, text: str, now_local: str, timezone: str) -> str:
        return _render(
            "parse_reminder",
            {"text": text, "now_local": now_local, "timezone": timezone},
            "Extract a task reminder from the user's sentence. Resolve relative dates "
            "(today, tomorrow, weekdays, 'in 20 minutes') against now_local.",
            "{\n"
            '  "title": "<short imperative title>",\n'
            '  "due": "YYYY-MM-DDTHH:MM" | null,\n'
            '  "priority": "low" | "medium" | "high" | "critical",\n'
            '  "category": "work" | "personal" | "health" | "other",\n'
            '  "repeat": "none" | "daily" | "weekly" | "weekdays",\n'
            '  "needs_clarification": true | false,\n'
            '  "clarification_question": "<question>" | null\n'
            "}",
            "- due is local time in the user's timezone, no offset\n"
            "- If the time is missing or ambiguous set needs_clarification=true and ask\n"
            "- Default priority is medium unless urgency words are used",
        )

    def build_ranking_prompt(self, context: dict[str, Any]) -> str:
        return _render(
            "prioritize",
            context,
            "Several reminders are due at the same time. Order them so the most useful one is "
            "sent first, using priority, time of day, and the user's completion rates by category.",
            '{\n  "order": [<reminder ids, most important first>],\n'
            '  "reasoning": "<short explanation>"\n}',
            "- order must contain every reminder id exactly once\n- DO NOT invent ids",
        )

    def build_insight_prompt(self, context: dict[str, Any]) -> str:
        return _render(
            "recommendations",
            context,
            "Turn the user's statistics into at most 5 concrete setting changes that would help "
            "them wake up and complete tasks.",
            "{\n"
            '  "items": [\n'
            "    {\n"
            '      "type": "<short id, e.g. escalation_order>",\n'
            '      "title": "<one-line suggestion>",\n'
            '      "rationale": "<why, citing the numbers>",\n'
            '      "change": {"setting": "<one of allowed_settings>", "value": <new value>}\n'
            "    }\n  ]\n}",
            "- setting must be one of allowed_settings and value must match its schema\n"
            "- Never disable all escalation channels\n"
            "- Do not suggest values equal to current_settings\n"
            "- Return an empty list if there is not enough data",
        )
