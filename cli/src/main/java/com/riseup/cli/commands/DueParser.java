package com.riseup.cli.commands;

import com.riseup.cli.command.UserInputError;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns friendly --due values into the API's "YYYY-MM-DD HH:MM" (local time).
 * Accepts: exact dates, "today 18:30", "tomorrow", "tomorrow 9am", "in 30 minutes", "in 2h".
 */
public final class DueParser {
    static final LocalTime DEFAULT_TIME = LocalTime.of(9, 0);
    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern RELATIVE = Pattern.compile("in\\s+(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours)");
    private static final Pattern DAY_TIME = Pattern.compile("(today|tomorrow)(?:\\s+(?:at\\s+)?(.+))?");
    private static final Pattern CLOCK = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?");

    private DueParser() {}

    public static String normalize(String raw, LocalDateTime now) {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || Character.isDigit(s.charAt(0)) && s.length() >= 10) {
            return raw == null ? "" : raw.trim(); // exact date: let the API validate it
        }
        Matcher rel = RELATIVE.matcher(s);
        if (rel.matches()) {
            long n = Long.parseLong(rel.group(1));
            LocalDateTime due = rel.group(2).startsWith("h") ? now.plusHours(n) : now.plusMinutes(n);
            return due.format(OUT);
        }
        Matcher dt = DAY_TIME.matcher(s);
        if (dt.matches()) {
            LocalDate day = "tomorrow".equals(dt.group(1)) ? now.toLocalDate().plusDays(1) : now.toLocalDate();
            LocalTime time = dt.group(2) == null ? DEFAULT_TIME : clock(dt.group(2), raw);
            return LocalDateTime.of(day, time).format(OUT);
        }
        throw new UserInputError("Unrecognised --due '" + raw
                + "'. Use YYYY-MM-DD HH:MM, today 18:30, tomorrow [9am], or in 30 minutes");
    }

    private static LocalTime clock(String text, String raw) {
        Matcher m = CLOCK.matcher(text.trim());
        if (!m.matches()) throw new UserInputError("Unrecognised time in --due '" + raw + "'");
        int h = Integer.parseInt(m.group(1));
        int min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String ampm = m.group(3);
        if (ampm != null) {
            if (h < 1 || h > 12) throw new UserInputError("Unrecognised time in --due '" + raw + "'");
            h = h % 12 + ("pm".equals(ampm) ? 12 : 0);
        }
        if (h > 23 || min > 59) throw new UserInputError("Unrecognised time in --due '" + raw + "'");
        return LocalTime.of(h, min);
    }
}
