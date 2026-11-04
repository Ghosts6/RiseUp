package com.riseup.cli.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.cli.ExitCodes;
import com.riseup.cli.api.ApiClient;
import com.riseup.cli.api.ApiException;
import com.riseup.cli.command.Command;
import com.riseup.cli.command.CommandRegistry;
import com.riseup.cli.command.Io;
import com.riseup.cli.command.Result;
import com.riseup.cli.command.UserInputError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registers concrete {@link Command} implementations for every CLI verb. */
public final class ConcreteCommands {
    private ConcreteCommands() {}

    public static void registerAll(CommandRegistry registry) {
        registry.register("alarm.list", (args, yes) -> (api, io) -> print(api, io, api.get("/api/alarms")));
        registry.register("alarm.add", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("time", args[0]); body.put("label", blank(args,1,"Alarm"));
            body.put("verification_task_type", blank(args,2,"math"));
            body.put("repeat_pattern", blank(args,3,"once"));
            body.put("snooze_enabled", args.length > 4 && Boolean.parseBoolean(args[4]));
            body.put("sound", blank(args,5,"default")); body.put("vibration", blank(args,6,"medium"));
            if (args.length > 7 && !args[7].isBlank()) body.put("custom_task_text", args[7]);
            JsonNode alarm = api.post("/api/alarms", body);
            return print(api, io, Result.ok(alarm, "Alarm set for " + alarm.path("time").asText()));
        });
        registry.register("alarm.edit", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            putIf(body, "time", args, 1); putIf(body, "label", args, 2); putIf(body, "verification_task_type", args, 3);
            putIf(body, "repeat_pattern", args, 4); putIf(body, "sound", args, 5); putIf(body, "vibration", args, 6);
            putIf(body, "custom_task_text", args, 7);
            if (args.length > 8 && !args[8].isBlank()) body.put("snooze_enabled", Boolean.parseBoolean(args[8]));
            if (body.isEmpty()) throw new UserInputError("Nothing to change. Pass at least one option, e.g. --time 06:30");
            return print(api, io, Result.ok(api.patch("/api/alarms/" + args[0], body), "Alarm updated."));
        });
        registry.register("alarm.delete", (args, yes) -> (api, io) -> {
            if (!io.confirm("Delete alarm " + args[0] + "?", false)) return print(api, io, Result.message("Cancelled."));
            api.delete("/api/alarms/" + args[0]);
            return print(api, io, Result.ok(Map.of("deleted", Integer.parseInt(args[0])), "Alarm " + args[0] + " deleted."));
        });
        registry.register("alarm.toggle", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            if (args.length > 1 && !args[1].isBlank()) body.put("active", Boolean.parseBoolean(args[1]));
            else body.put("active", null);
            JsonNode alarm = api.post("/api/alarms/" + args[0] + "/toggle", body);
            String state = alarm.path("is_active").asBoolean(false) ? "enabled" : "disabled";
            return print(api, io, Result.ok(alarm, "Alarm " + alarm.path("id").asInt() + " " + state + "."));
        });
        registry.register("alarm.trigger", (args, yes) -> (api, io) -> {
            api.post("/api/alarms/" + args[0] + "/trigger", Map.of());
            JsonNode challenge = api.get("/api/alarms/" + args[0] + "/challenge");
            return print(api, io, Result.ok(challenge, renderChallenge(challenge, "Alarm is ringing!")));
        });
        registry.register("alarm.challenge", (args, yes) -> (api, io) -> {
            JsonNode challenge = api.get("/api/alarms/" + args[0] + "/challenge");
            return print(api, io, Result.ok(challenge, renderChallenge(challenge, "")));
        });
        registry.register("alarm.verify", (args, yes) -> (api, io) -> {
            JsonNode challenge = api.get("/api/alarms/" + args[0] + "/challenge");
            if ("object_detection".equals(challenge.path("challenge").path("type").asText()))
                throw new UserInputError("Photo verification is GUI-only (needs a camera). Open the web app to take the photo.");
            String answer = args.length > 1 ? args[1] : "";
            boolean done = args.length > 2 && Boolean.parseBoolean(args[2]);
            if (answer.isBlank() && !done) throw new UserInputError("Pass --answer <value> (math/trivia) or --done (custom task)");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("answer", answer.isBlank() ? null : answer);
            body.put("completed", done);
            JsonNode result = api.post("/api/alarms/" + args[0] + "/verify", body);
            if (result.path("success").asBoolean(false)) {
                String extra = result.hasNonNull("checkin_at") ? " Check-in at " + result.path("checkin_at").asText() + "." : "";
                return print(api, io, Result.ok(result, result.path("message").asText() + extra));
            }
            return print(api, io, Result.error(result, result.path("message").asText() + "\n" + renderChallenge(result, "")));
        });
        registry.register("alarm.snooze", (args, yes) -> (api, io) -> {
            JsonNode alarm = api.post("/api/alarms/" + args[0] + "/snooze", Map.of());
            return print(api, io, Result.ok(alarm, "Snoozed (" + alarm.path("snooze_count").asInt() + " used). It will ring again soon."));
        });

        registry.register("checkin.confirm", (args, yes) -> (api, io) -> {
            JsonNode r = api.post("/api/checkins/confirm", Map.of());
            return print(api, io, Result.ok(r, r.path("message").asText()));
        });
        registry.register("checkin.list", (args, yes) -> (api, io) -> print(api, io, api.get("/api/checkins")));

        registry.register("escalation.explain", (args, yes) -> (api, io) -> {
            JsonNode d = api.get("/api/escalations/" + args[0]);
            return print(api, io, Result.ok(d, explainEscalation(d)));
        });
        registry.register("escalation.status", (args, yes) -> (api, io) -> {
            JsonNode status = api.get("/api/alarms/" + args[0] + "/escalation");
            List<String> chain = new ArrayList<>();
            for (JsonNode a : status.path("attempts")) chain.add(a.path("method").asText() + " (" + a.path("status").asText() + ")");
            String msg = "Alarm " + status.path("alarm_id").asInt() + " is " + status.path("status").asText()
                + ". Escalation chain: " + (chain.isEmpty() ? "none" : String.join(" -> ", chain));
            return print(api, io, Result.ok(status.path("attempts"), msg));
        });
        registry.register("escalation.list", (args, yes) -> (api, io) -> print(api, io, api.get("/api/escalations")));
        registry.register("escalation.ack", (args, yes) -> (api, io) ->
            print(api, io, Result.ok(api.post("/api/escalations/acknowledge", Map.of("via", "cli")),
                "Confirmed awake. Escalation stopped.")));

        registry.register("prefs.show", (args, yes) -> (api, io) -> print(api, io, api.get("/api/prefs")));
        registry.register("prefs.set", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            if (args.length > 0 && !args[0].isBlank()) body.put("enabled_channels", args[0].split(","));
            if (args.length > 1 && !args[1].isBlank()) body.put("escalation_order", args[1].split(","));
            if (args.length > 2 && !args[2].isBlank()) body.put("notification_methods", args[2].split(","));
            if (args.length > 3 && !args[3].isBlank()) {
                String q = args[3];
                if (q.equalsIgnoreCase("off") || q.equalsIgnoreCase("none")) {
                    body.put("quiet_hours_start", null); body.put("quiet_hours_end", null);
                } else {
                    String[] parts = q.split("-", 2);
                    if (parts.length != 2) throw new UserInputError("--quiet must look like 22:00-07:00 or 'off'");
                    body.put("quiet_hours_start", parts[0].trim()); body.put("quiet_hours_end", parts[1].trim());
                }
            }
            if (args.length > 4 && !args[4].isBlank()) body.put("checkin_enabled", Boolean.parseBoolean(args[4]));
            if (args.length > 5 && !args[5].isBlank()) body.put("checkin_delay_minutes", Integer.parseInt(args[5]));
            if (args.length > 6 && !args[6].isBlank()) body.put("default_task_type", args[6]);
            if (args.length > 7 && !args[7].isBlank()) body.put("phone", args[7]);
            if (args.length > 8 && !args[8].isBlank()) body.put("timezone", args[8]);
            if (args.length > 9 && !args[9].isBlank()) body.put("name", args[9]);
            if (args.length > 10 && !args[10].isBlank()) body.put("email", args[10]);
            if (body.isEmpty()) throw new UserInputError("Nothing to change. Example: riseup prefs set --channels call,sms");
            return print(api, io, Result.ok(api.patch("/api/prefs", body), "Preferences saved."));
        });
        registry.register("prefs.test", (args, yes) -> (api, io) -> {
            JsonNode r = api.post("/api/prefs/test", Map.of("channel", args[0]));
            String verdict = r.path("success").asBoolean(false) ? "sent" : "FAILED";
            return print(api, io, Result.ok(r, "Test " + r.path("channel").asText() + " " + verdict + ": " + r.path("detail").asText()));
        });

        registry.register("contact.add", (args, yes) -> (api, io) -> {
            JsonNode c = api.post("/api/contacts", Map.of("name", args[0], "phone", args[1]));
            return print(api, io, Result.ok(c, "Contact #" + c.path("id").asInt()
                + " added. A consent request was sent; they are only used after confirming.\nConsent link: "
                + c.path("consent_link").asText()));
        });
        registry.register("contact.list", (args, yes) -> (api, io) -> print(api, io, api.get("/api/contacts")));
        registry.register("contact.remove", (args, yes) -> (api, io) -> {
            api.delete("/api/contacts/" + args[0]);
            return print(api, io, Result.ok(Map.of("deleted", Integer.parseInt(args[0])), "Contact " + args[0] + " removed."));
        });
        registry.register("contact.test", (args, yes) -> (api, io) -> {
            JsonNode r = api.post("/api/contacts/" + args[0] + "/test", Map.of());
            return print(api, io, Result.ok(r, "Test message " + (r.path("success").asBoolean(false)?"sent":"FAILED") + ": " + r.path("detail").asText()));
        });

        registry.register("remind", (args, yes) -> (api, io) -> {
            String text = args[0];
            JsonNode parsed = api.post("/api/reminders/parse", Map.of("text", text));
            for (int i = 0; i < 2; i++) {
                if (!"needs_clarification".equals(parsed.path("status").asText())) break;
                text = text + " " + io.prompt(parsed.path("question").asText());
                parsed = api.post("/api/reminders/parse", Map.of("text", text));
            }
            String status = parsed.path("status").asText();
            if ("failed".equals(status)) throw new UserInputError(parsed.path("message").asText());
            if ("needs_clarification".equals(status)) throw new UserInputError("Still unclear: " + parsed.path("question").asText());
            JsonNode draft = parsed.path("draft");
            io.echo("Parsed: \"" + draft.path("title").asText() + "\" | "
                + (draft.hasNonNull("due") ? draft.path("due").asText().replace('T',' ') : "?"));
            if ("past_time".equals(status)) {
                if (!io.confirm(parsed.path("question").asText(), true)) return print(api, io, Result.message("Cancelled. Nothing saved."));
            } else if (!io.confirm("Save this reminder?", true)) return print(api, io, Result.message("Cancelled. Nothing saved."));
            JsonNode reminder = api.post("/api/reminders?source=nl", draft);
            return print(api, io, Result.ok(reminder, "Reminder #" + reminder.path("id").asInt()
                + " scheduled for " + reminder.path("due_local").asText()));
        });

        registry.register("task.add", (args, yes) -> (api, io) -> {
            if (args[0].isBlank() || args[1].isBlank())
                throw new UserInputError("Both --title and --due are required, e.g. --due \"2026-10-20 09:00\"");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", args[0]); body.put("due", args[1]);
            body.put("priority", blank(args,2,"medium")); body.put("category", blank(args,3,"personal"));
            body.put("repeat_pattern", blank(args,4,"none")); body.put("notification_type", blank(args,5,"push"));
            if (args.length > 6 && !args[6].isBlank()) body.put("notes", args[6]);
            JsonNode reminder;
            try { reminder = api.post("/api/reminders", body); }
            catch (ApiException exc) {
                JsonNode data = exc.getData();
                String suggestion = data != null && data.hasNonNull("suggestion") ? data.path("suggestion").asText() : null;
                if (suggestion == null || !io.confirm(exc.getMessage() + " (" + suggestion.replace('T',' ') + ")", true)) throw exc;
                body.put("due", suggestion); reminder = api.post("/api/reminders", body);
            }
            return print(api, io, Result.ok(reminder, "Reminder #" + reminder.path("id").asInt()
                + " scheduled for " + reminder.path("due_local").asText()));
        });
        registry.register("task.list", (args, yes) -> (api, io) -> {
            String status = (args.length == 0 || args[0].isBlank()) ? "open" : args[0];
            String path = "/api/reminders" + ("all".equals(status) ? "" : ("?status=" + status));
            if (args.length > 1 && !args[1].isBlank()) path += (path.contains("?")?"&":"?") + "category=" + args[1];
            if (args.length > 2 && !args[2].isBlank()) path += (path.contains("?")?"&":"?") + "priority=" + args[2];
            return print(api, io, api.get(path));
        });
        registry.register("task.edit", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            putIf(body, "title", args, 1); putIf(body, "due", args, 2); putIf(body, "priority", args, 3);
            putIf(body, "category", args, 4); putIf(body, "repeat_pattern", args, 5); putIf(body, "notification_type", args, 6);
            putIf(body, "notes", args, 7);
            if (body.isEmpty()) throw new UserInputError("Nothing to change. Pass at least one option.");
            String scope = args.length > 8 && !args[8].isBlank() ? args[8] : "this";
            return print(api, io, Result.ok(api.patch("/api/reminders/" + args[0] + "?scope=" + scope, body), "Reminder updated."));
        });
        registry.register("task.done", (args, yes) -> (api, io) -> {
            boolean already = args.length > 1 && Boolean.parseBoolean(args[1]);
            JsonNode r = api.post("/api/reminders/" + args[0] + "/complete", Map.of("already_done", already));
            return print(api, io, Result.ok(r, "Done: " + r.path("title").asText() + ". Reminder sequence stopped."));
        });
        registry.register("task.snooze", (args, yes) -> (api, io) -> {
            Map<String, Object> body = new LinkedHashMap<>();
            if (args.length > 1 && !args[1].isBlank()) body.put("minutes", Integer.parseInt(args[1]));
            else body.put("minutes", null);
            JsonNode r = api.post("/api/reminders/" + args[0] + "/snooze", body);
            return print(api, io, Result.ok(r, "Snoozed until " + r.path("due_local").asText() + "."));
        });
        registry.register("task.delete", (args, yes) -> (api, io) -> {
            if (!io.confirm("Delete reminder " + args[0] + "?", false)) return print(api, io, Result.message("Cancelled."));
            api.delete("/api/reminders/" + args[0]);
            return print(api, io, Result.ok(Map.of("deleted", Integer.parseInt(args[0])), "Reminder " + args[0] + " deleted."));
        });
        registry.register("task.priority-preview", (args, yes) -> (api, io) -> {
            JsonNode preview = api.get("/api/reminders/priority-preview");
            String label = preview.path("fallback_used").asBoolean(false) ? "rule-based fallback" : preview.path("decided_by").asText();
            return print(api, io, Result.ok(preview.path("order"),
                "Why: " + preview.path("reasoning").asText() + "\nDecided by: " + label));
        });

        registry.register("profile.show", (args, yes) -> (api, io) -> print(api, io, api.get("/api/profile")));
        registry.register("profile.recompute", (args, yes) -> (api, io) -> {
            api.post("/api/profile/recompute", Map.of());
            return print(api, io, Result.ok(api.get("/api/profile"), "Profile recomputed."));
        });
        registry.register("stats", (args, yes) -> (api, io) ->
            print(api, io, api.get("/api/stats?range=" + (args.length==0||args[0].isBlank()?"30d":args[0]))));
        registry.register("recommend", (args, yes) -> (api, io) -> {
            JsonNode result = api.post("/api/recommendations/generate", Map.of());
            String message = result.path("message").asText("");
            if (result.path("items").isArray() && !result.path("items").isEmpty())
                message = (message + "\nApply one with: riseup recommend --apply <#>").strip();
            return print(api, io, Result.ok(result.path("items"), message));
        });
        registry.register("recommend.apply", (args, yes) -> (api, io) -> {
            JsonNode rec = api.post("/api/recommendations/" + args[0] + "/accept", Map.of());
            return print(api, io, Result.ok(rec, "Applied: " + rec.path("title").asText()));
        });
        registry.register("recommend.dismiss", (args, yes) -> (api, io) -> {
            JsonNode rec = api.post("/api/recommendations/" + args[0] + "/dismiss", Map.of());
            return print(api, io, Result.ok(rec, "Dismissed: " + rec.path("title").asText()));
        });
        registry.register("history", (args, yes) -> (api, io) -> {
            String type = args.length > 0 ? args[0] : "";
            String since = args.length > 1 ? args[1] : "";
            String page = args.length > 2 && !args[2].isBlank() ? args[2] : "1";
            if (!type.isBlank() && !List.of("alarm","escalation","reminder","checkin","prioritization").contains(type))
                throw new UserInputError("--type must be alarm, escalation, reminder, checkin, or prioritization");
            StringBuilder path = new StringBuilder("/api/history?page=").append(page);
            if (!type.isBlank()) path.append("&type=").append(type);
            if (!since.isBlank()) path.append("&since=").append(since);
            JsonNode pageNode = api.get(path.toString());
            String hint = "Page " + pageNode.path("page").asInt() + "/" + pageNode.path("pages").asInt()
                + " (" + pageNode.path("total").asInt() + " events). Explain an AI decision: riseup escalation explain <ref>";
            return print(api, io, Result.ok(pageNode.path("items"), hint));
        });
        registry.register("health", (args, yes) -> (api, io) -> print(api, io, api.get("/api/health")));
    }

    private static int print(ApiClient api, Io io, Object data) {
        if (data instanceof Result r) { io.print(r); return r.exitCode(); }
        io.print(Result.ok(data));
        return ExitCodes.SUCCESS;
    }
    private static String blank(String[] args, int i, String dflt) {
        return args.length > i && args[i] != null && !args[i].isBlank() ? args[i] : dflt;
    }
    private static void putIf(Map<String, Object> body, String key, String[] args, int i) {
        if (args.length > i && args[i] != null && !args[i].isBlank()) body.put(key, args[i]);
    }
    private static String renderChallenge(JsonNode challenge, String prefix) {
        JsonNode task = challenge.has("challenge") ? challenge.path("challenge") : challenge;
        List<String> lines = new ArrayList<>();
        if (prefix != null && !prefix.isBlank()) lines.add(prefix);
        lines.add("Challenge (" + task.path("type").asText() + "): " + task.path("question").asText());
        JsonNode choices = task.get("choices");
        if (choices != null && choices.isArray()) {
            int i = 0; for (JsonNode c : choices) lines.add("  [" + (i++) + "] " + c.asText());
        }
        String type = task.path("type").asText();
        int alarmId = challenge.path("alarm_id").asInt(0);
        if ("object_detection".equals(type)) lines.add("Photo verification is GUI-only (needs a camera). Open the web app.");
        else if ("custom".equals(type)) lines.add("When done: riseup alarm verify " + alarmId + " --done");
        else lines.add("Answer with: riseup alarm verify " + alarmId + " --answer <value>");
        return String.join("\n", lines);
    }
    private static String explainEscalation(JsonNode d) {
        String conf = d.hasNonNull("confidence") ? String.format(Locale.ROOT, "%.2f", d.path("confidence").asDouble()) : "n/a";
        List<String> lines = new ArrayList<>();
        lines.add("Escalation #" + d.path("id").asInt() + " (" + d.path("at").asText() + ")");
        lines.add("  Method chosen : " + (d.hasNonNull("method") ? d.path("method").asText() : "none") + "        Confidence: " + conf);
        lines.add("  Reasoning     : " + d.path("reasoning").asText());
        lines.add("  Decided by    : " + d.path("decided_by").asText() + "        Fallback used: "
            + (d.path("fallback_used").asBoolean(false) ? "yes" : "no"));
        lines.add("  Trigger       : " + d.path("trigger").asText() + " (attempt " + d.path("attempt").asInt() + ")");
        String outcome = "  Outcome       : " + d.path("outcome").asText();
        if (d.hasNonNull("response_time_seconds"))
            outcome += " after " + String.format(Locale.ROOT, "%.0f", d.path("response_time_seconds").asDouble()) + " s";
        if (d.hasNonNull("error")) outcome += " (" + d.path("error").asText() + ")";
        lines.add(outcome);
        return String.join("\n", lines);
    }
}
