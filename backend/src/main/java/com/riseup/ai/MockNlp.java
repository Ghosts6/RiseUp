package com.riseup.ai;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MockNlp {
    private static final String[] WEEKDAYS =
            {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
    private static final Map<String, List<String>> CATEGORY_WORDS =
            Map.of(
                    "health", List.of("gym", "medicine", "pill", "doctor", "workout", "water"),
                    "work", List.of("meeting", "email", "report", "deadline", "project", "lab"),
                    "personal", List.of("mom", "dad", "friend", "birthday", "call", "groceries"));

    private MockNlp() {}

    public static Map<String, Object> parseReminderText(String text, String nowLocalIso) {
        LocalDateTime now =
                (nowLocalIso == null || nowLocalIso.isBlank())
                        ? LocalDateTime.now()
                        : LocalDateTime.parse(nowLocalIso.replace(" ", "T"));
        String lowered = text.toLowerCase(Locale.ROOT).strip();
        LocalDateTime due = null;

        Matcher relative = Pattern.compile("\\bin\\s+(\\d+)\\s*(minutes?|mins?|hours?|hrs?)\\b").matcher(lowered);
        if (relative.find()) {
            int amount = Integer.parseInt(relative.group(1));
            String unit = relative.group(2);
            due = unit.startsWith("h") ? now.plusHours(amount) : now.plusMinutes(amount);
        } else {
            LocalDate day = now.toLocalDate();
            boolean explicitDay = false;
            Matcher dateMatch = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(lowered);
            if (dateMatch.find()) {
                day = LocalDate.parse(dateMatch.group(1));
                explicitDay = true;
            } else if (lowered.contains("tomorrow")) {
                day = now.toLocalDate().plusDays(1);
                explicitDay = true;
            } else {
                for (int i = 0; i < WEEKDAYS.length; i++) {
                    if (lowered.contains(WEEKDAYS[i])) {
                        int target = i; // Monday=0
                        int current = now.getDayOfWeek().getValue() - 1;
                        int delta = (target - current + 7) % 7;
                        if (delta == 0) delta = 7;
                        day = now.toLocalDate().plusDays(delta);
                        explicitDay = true;
                        break;
                    }
                }
            }
            LocalTime time = parseTime(lowered);
            if (time != null) {
                due = LocalDateTime.of(day, time);
                if (!explicitDay && !due.isAfter(now)) due = due.plusDays(1);
            } else if (explicitDay) {
                Map<String, Object> need = new LinkedHashMap<>();
                need.put("status", "needs_clarification");
                need.put("question", "What time should I remind you?");
                need.put("draft", null);
                return need;
            }
        }

        if (due == null) {
            Map<String, Object> need = new LinkedHashMap<>();
            need.put("status", "needs_clarification");
            need.put("question", "When should I remind you?");
            need.put("draft", null);
            return need;
        }

        String title = extractTitle(lowered);
        String category = "other";
        for (var e : CATEGORY_WORDS.entrySet()) {
            for (String w : e.getValue()) {
                if (lowered.contains(w)) {
                    category = e.getKey();
                    break;
                }
            }
        }
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("title", title);
        draft.put("due", due.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        draft.put("priority", "medium");
        draft.put("category", category);
        draft.put("repeat_pattern", "none");
        draft.put("notification_type", "push");

        Map<String, Object> out = new LinkedHashMap<>();
        if (due.isBefore(now.minusMinutes(1))) {
            out.put("status", "past_time");
            out.put("question", "That time has passed. Should I use tomorrow instead?");
            out.put("draft", draft);
            return out;
        }
        out.put("status", "ok");
        out.put("draft", draft);
        return out;
    }

    private static LocalTime parseTime(String text) {
        if (text.contains("noon")) return LocalTime.of(12, 0);
        if (text.contains("midnight")) return LocalTime.of(0, 0);
        Matcher m =
                Pattern.compile("\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)\\b")
                        .matcher(text);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String meridiem = m.group(3).replace(".", "");
            if (hour > 12) return null;
            if ("pm".equals(meridiem) && hour != 12) hour += 12;
            if ("am".equals(meridiem) && hour == 12) hour = 0;
            return LocalTime.of(hour, minute);
        }
        m = Pattern.compile("\\b(?:at\\s+)?([01]?\\d|2[0-3]):([0-5]\\d)\\b").matcher(text);
        if (m.find()) return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        return null;
    }

    private static String extractTitle(String lowered) {
        String t = lowered;
        t = t.replaceFirst("^(remind me to|remind me|set a reminder to|set a reminder)\\s+", "");
        t = t.replaceAll("\\b(tomorrow|today|at\\s+\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", "");
        t = t.replaceAll("\\s+", " ").strip();
        if (t.isBlank()) t = "Reminder";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
