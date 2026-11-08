package com.riseup.cli.commands;

import com.riseup.cli.command.UserInputError;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueParserTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 14, 20);

    @Test
    void exactDatesPassThrough() {
        assertThat(DueParser.normalize("2026-11-08 07:30", NOW)).isEqualTo("2026-11-08 07:30");
    }

    @Test
    void tomorrowDefaultsToMorning() {
        assertThat(DueParser.normalize("tomorrow", NOW)).isEqualTo("2026-11-08 09:00");
    }

    @Test
    void dayWithClockTime() {
        assertThat(DueParser.normalize("tomorrow 9am", NOW)).isEqualTo("2026-11-08 09:00");
        assertThat(DueParser.normalize("today 18:30", NOW)).isEqualTo("2026-11-07 18:30");
        assertThat(DueParser.normalize("Tomorrow at 12pm", NOW)).isEqualTo("2026-11-08 12:00");
        assertThat(DueParser.normalize("today 12am", NOW)).isEqualTo("2026-11-07 00:00");
    }

    @Test
    void relativeOffsets() {
        assertThat(DueParser.normalize("in 30 minutes", NOW)).isEqualTo("2026-11-07 14:50");
        assertThat(DueParser.normalize("in 2h", NOW)).isEqualTo("2026-11-07 16:20");
    }

    @Test
    void rejectsGibberish() {
        assertThatThrownBy(() -> DueParser.normalize("someday", NOW)).isInstanceOf(UserInputError.class);
        assertThatThrownBy(() -> DueParser.normalize("today 25:00", NOW)).isInstanceOf(UserInputError.class);
    }
}
