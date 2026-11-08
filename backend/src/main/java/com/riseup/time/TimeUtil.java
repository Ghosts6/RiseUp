package com.riseup.time;

import com.riseup.error.ValidationFailedException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {
    private static final Pattern HHMM = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)$");
    private static final String[] WEEKDAYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    private TimeUtil() {}

    public static ZoneId getZone(String tzName) {
        try {
            return ZoneId.of(tzName == null || tzName.isBlank() ? "UTC" : tzName);
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    public static LocalTime parseHhmm(String value) {
        Matcher m = HHMM.matcher(value == null ? "" : value);
        if (!m.matches()) {
            throw new ValidationFailedException("Please enter time in HH:MM format", "time");
        }
        return LocalTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    public static boolean isValidHhmm(String value) {
        return value != null && HHMM.matcher(value).matches();
    }

    public static LocalDateTime toLocal(LocalDateTime utc, String tzName) {
        if (utc == null) return null;
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(getZone(tzName)).toLocalDateTime();
    }

    public static LocalDateTime toUtcNaive(LocalDateTime local, String tzName) {
        if (local == null) return null;
        ZoneId zone = getZone(tzName);
        ZonedDateTime zdt = local.atZone(zone);
        return zdt.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static Set<Integer> repeatDays(String pattern) {
        String p = (pattern == null ? "once" : pattern).toLowerCase(Locale.ROOT);
        if ("once".equals(p)) return null;
        if ("daily".equals(p)) return Set.of(0, 1, 2, 3, 4, 5, 6);
        if ("weekdays".equals(p)) return Set.of(0, 1, 2, 3, 4);
        if ("weekends".equals(p)) return Set.of(5, 6);
        if (p.startsWith("custom:")) {
            Set<Integer> days = new HashSet<>();
            for (String part : p.substring(7).split(",")) {
                String d = part.trim();
                for (int i = 0; i < WEEKDAYS.length; i++) {
                    if (WEEKDAYS[i].equals(d)) days.add(i);
                }
            }
            if (days.isEmpty()) {
                throw new ValidationFailedException(
                        "Custom repeat needs days, e.g. custom:mon,wed,fri", "repeat_pattern");
            }
            return days;
        }
        throw new ValidationFailedException(
                "Repeat must be once, daily, weekdays, weekends, or custom:mon,wed", "repeat_pattern");
    }

    public static void validateRepeat(String pattern) {
        repeatDays(pattern);
    }

    /** Monday=0 .. Sunday=6 */
    private static int pyWeekday(LocalDate day) {
        return day.getDayOfWeek().getValue() - 1;
    }

    public static LocalDateTime nextAlarmOccurrence(
            String hhmm, String pattern, String tzName, LocalDateTime nowUtc) {
        LocalTime alarmTime = parseHhmm(hhmm);
        Set<Integer> days = repeatDays(pattern);
        LocalDateTime nowLocal = toLocal(nowUtc, tzName);
        ZoneId zone = getZone(tzName);
        for (int offset = 0; offset < 8; offset++) {
            LocalDate day = nowLocal.toLocalDate().plusDays(offset);
            if (days != null && !days.contains(pyWeekday(day))) continue;
            ZonedDateTime candidate = ZonedDateTime.of(day, alarmTime, zone);
            if (candidate.toLocalDateTime().isAfter(nowLocal)) {
                return candidate.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            }
        }
        throw new IllegalStateException("Could not compute next alarm occurrence");
    }

    public static LocalDateTime nextReminderOccurrence(
            LocalDateTime previousDueUtc, String pattern, String seriesTime, String tzName) {
        String p = (pattern == null ? "none" : pattern).toLowerCase(Locale.ROOT);
        if ("none".equals(p)) return null;
        LocalDateTime prevLocal = toLocal(previousDueUtc, tzName);
        LocalTime at =
                (seriesTime != null && isValidHhmm(seriesTime))
                        ? parseHhmm(seriesTime)
                        : prevLocal.toLocalTime();
        ZoneId zone = getZone(tzName);
        int stepDays =
                switch (p) {
                    case "daily" -> 1;
                    case "weekly" -> 7;
                    case "weekdays" -> 1;
                    default -> throw new ValidationFailedException(
                            "Repeat must be none, daily, weekly, or weekdays", "repeat_pattern");
                };
        LocalDate day = prevLocal.toLocalDate();
        while (true) {
            day = day.plusDays(stepDays);
            if ("weekdays".equals(p) && pyWeekday(day) >= 5) continue;
            ZonedDateTime local = ZonedDateTime.of(day, at, zone);
            return local.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
    }

    public static boolean inQuietHours(
            LocalDateTime nowUtc, String tzName, String start, String end) {
        if (!(isValidHhmm(start) && isValidHhmm(end))) return false;
        LocalTime local = toLocal(nowUtc, tzName).toLocalTime().withSecond(0).withNano(0);
        LocalTime s = parseHhmm(start);
        LocalTime e = parseHhmm(end);
        if (!s.isAfter(e)) {
            return !local.isBefore(s) && local.isBefore(e);
        }
        return !local.isBefore(s) || local.isBefore(e);
    }

    public static int parseRange(String value) {
        Matcher m = Pattern.compile("^(\\d+)\\s*d?$")
                .matcher((value == null ? "" : value).trim().toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            throw new ValidationFailedException("Range must look like 7d, 30d, or 90d", "range");
        }
        int days = Integer.parseInt(m.group(1));
        if (days < 1 || days > 365) {
            throw new ValidationFailedException("Range must be between 1d and 365d", "range");
        }
        return days;
    }

    public static String isoZ(LocalDateTime dt) {
        return dt == null ? null : dt.toString() + "Z";
    }

    public static String localFmt(LocalDateTime utc, String tz) {
        if (utc == null) return null;
        LocalDateTime local = toLocal(utc, tz);
        return String.format(
                "%04d-%02d-%02d %02d:%02d",
                local.getYear(),
                local.getMonthValue(),
                local.getDayOfMonth(),
                local.getHour(),
                local.getMinute());
    }
}
