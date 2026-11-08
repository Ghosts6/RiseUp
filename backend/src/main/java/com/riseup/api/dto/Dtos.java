package com.riseup.api.dto;

import java.util.List;
import java.util.Map;

public final class Dtos {
    private Dtos() {}

    public static class AlarmIn {
        public String time;
        public String label;
        public String sound;
        public String vibration;
        public String repeat_pattern;
        public Boolean snooze_enabled;
        public String verification_task_type;
        public String custom_task_text;
        public Integer priority;
        public Boolean is_active;
    }

    public static class ToggleIn {
        public Boolean active;
    }

    public static class VerifyIn {
        public String answer;
        public boolean completed;
        public String image_base64;
        public boolean camera_error;
    }

    public static class AcknowledgeIn {
        public String via = "dashboard";
    }

    public static class PreferencesIn {
        public List<String> enabled_channels;
        public List<String> escalation_order;
        public List<String> notification_methods;
        public String quiet_hours_start;
        public String quiet_hours_end;
        public Boolean checkin_enabled;
        public Integer checkin_delay_minutes;
        public String default_task_type;
        public Map<String, Object> category_preferred_times;
        public String push_token;
        public String phone;
        public String name;
        public String email;
        public String timezone;
    }

    public static class ContactIn {
        public String name;
        public String phone;
    }

    public static class TestChannelIn {
        public String channel;
    }

    public static class ReminderIn {
        public String title;
        public String notes;
        public String due;
        public String due_at;
        public String priority;
        public String category;
        public String repeat_pattern;
        public String notification_type;
        public List<String> extra_times;
        public boolean allow_past;
    }

    public static class ParseIn {
        public String text;
    }

    public static class CompleteIn {
        public boolean already_done;
    }

    public static class SnoozeIn {
        public Integer minutes;
    }
}
