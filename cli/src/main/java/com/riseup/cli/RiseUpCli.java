package com.riseup.cli;

import com.riseup.cli.api.ApiClient;
import com.riseup.cli.command.CommandRegistry;
import com.riseup.cli.command.Io;
import com.riseup.cli.config.ConnectionConfig;
import com.riseup.cli.format.JsonFormatter;
import com.riseup.cli.format.OutputFormatter;
import com.riseup.cli.format.TableFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(name = "riseup", mixinStandardHelpOptions = true, version = "riseup 0.2.0",
        description = "RiseUp: intelligent alarms and task reminders (CLI).",
        subcommands = {
                RiseUpCli.AlarmCmd.class, RiseUpCli.CheckinCmd.class, RiseUpCli.EscalationCmd.class,
                RiseUpCli.PrefsCmd.class, RiseUpCli.ContactCmd.class, RiseUpCli.RemindCmd.class,
                RiseUpCli.TaskCmd.class, RiseUpCli.ProfileCmd.class, RiseUpCli.StatsCmd.class,
                RiseUpCli.RecommendCmd.class, RiseUpCli.HistoryCmd.class, RiseUpCli.AgentCmd.class,
                RiseUpCli.HealthCmd.class
        })
public class RiseUpCli implements Callable<Integer> {
    @Option(names = "--json", description = "Machine-readable JSON output")
    static boolean json;

    @Option(names = "--server", description = "Backend URL")
    static String server;

    @Option(names = "--token", description = "API token")
    static String token;

    @Option(names = {"-y", "--yes"}, description = "Answer yes to confirmations")
    static boolean yes;

    private static final CommandRegistry REGISTRY = new CommandRegistry();

    public static void main(String[] args) {
        CommandLine cli = new CommandLine(new RiseUpCli());
        enableHelp(cli);
        System.exit(cli.execute(args));
    }

    /** Every subcommand gets -h/--help (and keeps its own description). */
    static void enableHelp(CommandLine cmd) {
        cmd.getCommandSpec().mixinStandardHelpOptions(true);
        cmd.getSubcommands().values().forEach(RiseUpCli::enableHelp);
    }

    @Override
    public Integer call() {
        new CommandLine(this).usage(System.out);
        return ExitCodes.USAGE;
    }

    static ApiClient api() {
        ConnectionConfig conn = ConnectionConfig.resolve(server, token);
        return new ApiClient(conn.server(), conn.token());
    }

    static OutputFormatter formatter() {
        return json ? new JsonFormatter() : new TableFormatter();
    }

    static Io io() {
        boolean interactive = System.console() != null && !json;
        return new Io(yes, interactive, formatter());
    }

    static int run(String name, String[] args) {
        com.riseup.cli.command.Command command = REGISTRY.bind(name, args, yes);
        return CommandRegistry.runBound(command, api(), io());
    }

    @Command(name = "alarm", description = "Alarms and verification (F01-F02)",
            subcommands = {AlarmAdd.class, AlarmList.class, AlarmEdit.class, AlarmDelete.class,
                    AlarmToggle.class, AlarmTrigger.class, AlarmChallenge.class, AlarmVerify.class, AlarmSnooze.class})
    static class AlarmCmd implements Callable<Integer> {
        public Integer call() { return ExitCodes.USAGE; }
    }

    @Command(name = "add", description = "Create an alarm")
    static class AlarmAdd implements Callable<Integer> {
        @Parameters(index = "0", description = "HH:MM") String time;
        @Option(names = "--label") String label;
        @Option(names = "--task", description = "math | trivia | object_detection | custom") String task;
        @Option(names = "--repeat") String repeat;
        @Option(names = "--snooze", negatable = true) Boolean snooze;
        @Option(names = "--sound") String sound;
        @Option(names = "--vibration") String vibration;
        @Option(names = "--custom-text") String customText;
        public Integer call() {
            return run("alarm.add", new String[]{
                    time, n(label), n(task), n(repeat),
                    snooze == null ? "" : String.valueOf(snooze),
                    n(sound), n(vibration), n(customText)});
        }
        private String n(String s) { return s == null ? "" : s; }
    }

    @Command(name = "list") static class AlarmList implements Callable<Integer> {
        public Integer call() { return run("alarm.list", new String[]{}); }
    }

    @Command(name = "edit") static class AlarmEdit implements Callable<Integer> {
        @Parameters(index = "0") int id;
        @Option(names = "--time") String time; @Option(names = "--label") String label;
        @Option(names = "--task") String task; @Option(names = "--repeat") String repeat;
        @Option(names = "--sound") String sound; @Option(names = "--vibration") String vibration;
        @Option(names = "--custom-text") String customText;
        @Option(names = "--snooze", negatable = true) Boolean snooze;
        public Integer call() {
            return run("alarm.edit", new String[]{
                    String.valueOf(id), n(time), n(label), n(task), n(repeat), n(sound), n(vibration), n(customText),
                    snooze == null ? "" : String.valueOf(snooze)});
        }
        private String n(String s) { return s == null ? "" : s; }
    }

    @Command(name = "delete") static class AlarmDelete implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("alarm.delete", new String[]{String.valueOf(id)}); }
    }

    @Command(name = "toggle") static class AlarmToggle implements Callable<Integer> {
        @Parameters(index = "0") int id;
        @Option(names = "--on") boolean on; @Option(names = "--off") boolean off;
        public Integer call() {
            String active = on ? "true" : off ? "false" : "";
            return run("alarm.toggle", new String[]{String.valueOf(id), active});
        }
    }

    @Command(name = "trigger") static class AlarmTrigger implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("alarm.trigger", new String[]{String.valueOf(id)}); }
    }

    @Command(name = "challenge") static class AlarmChallenge implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("alarm.challenge", new String[]{String.valueOf(id)}); }
    }

    @Command(name = "verify") static class AlarmVerify implements Callable<Integer> {
        @Parameters(index = "0") int id;
        @Option(names = "--answer") String answer;
        @Option(names = "--done") boolean done;
        @Option(names = "--photo", description = "JPEG/PNG file for photo challenges (checked by AI vision)") String photo;
        public Integer call() {
            return run("alarm.verify", new String[]{
                    String.valueOf(id), answer == null ? "" : answer, String.valueOf(done), photo == null ? "" : photo});
        }
    }

    @Command(name = "snooze") static class AlarmSnooze implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("alarm.snooze", new String[]{String.valueOf(id)}); }
    }

    @Command(name = "checkin", subcommands = {CheckinConfirm.class, CheckinList.class})
    static class CheckinCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "confirm") static class CheckinConfirm implements Callable<Integer> {
        public Integer call() { return run("checkin.confirm", new String[]{}); }
    }
    @Command(name = "list") static class CheckinList implements Callable<Integer> {
        public Integer call() { return run("checkin.list", new String[]{}); }
    }

    @Command(name = "escalation", subcommands = {EscExplain.class, EscStatus.class, EscList.class, EscAck.class})
    static class EscalationCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "explain") static class EscExplain implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("escalation.explain", new String[]{String.valueOf(id)}); }
    }
    @Command(name = "status") static class EscStatus implements Callable<Integer> {
        @Parameters(index = "0") int alarmId;
        public Integer call() { return run("escalation.status", new String[]{String.valueOf(alarmId)}); }
    }
    @Command(name = "list") static class EscList implements Callable<Integer> {
        public Integer call() { return run("escalation.list", new String[]{}); }
    }
    @Command(name = "ack") static class EscAck implements Callable<Integer> {
        public Integer call() { return run("escalation.ack", new String[]{}); }
    }

    @Command(name = "prefs", subcommands = {PrefsShow.class, PrefsSet.class, PrefsTest.class})
    static class PrefsCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "show") static class PrefsShow implements Callable<Integer> {
        public Integer call() { return run("prefs.show", new String[]{}); }
    }
    @Command(name = "set") static class PrefsSet implements Callable<Integer> {
        @Option(names = "--channels") String channels;
        @Option(names = "--order") String order;
        @Option(names = "--notify") String notify;
        @Option(names = "--quiet") String quiet;
        @Option(names = "--checkin", negatable = true) Boolean checkin;
        @Option(names = "--checkin-delay") Integer checkinDelay;
        @Option(names = "--default-task") String defaultTask;
        @Option(names = "--phone") String phone;
        @Option(names = "--timezone") String timezone;
        @Option(names = "--name") String name;
        @Option(names = "--email") String email;
        public Integer call() {
            return run("prefs.set", new String[]{
                    n(channels), n(order), n(notify), n(quiet),
                    checkin == null ? "" : String.valueOf(checkin),
                    checkinDelay == null ? "" : String.valueOf(checkinDelay),
                    n(defaultTask), n(phone), n(timezone), n(name), n(email)});
        }
        private String n(String s) { return s == null ? "" : s; }
    }
    @Command(name = "test") static class PrefsTest implements Callable<Integer> {
        @Parameters(index = "0") String channel;
        public Integer call() { return run("prefs.test", new String[]{channel}); }
    }

    @Command(name = "contact", subcommands = {ContactAdd.class, ContactList.class, ContactRemove.class, ContactTest.class})
    static class ContactCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "add") static class ContactAdd implements Callable<Integer> {
        @Parameters(index = "0") String name; @Parameters(index = "1") String phone;
        public Integer call() { return run("contact.add", new String[]{name, phone}); }
    }
    @Command(name = "list") static class ContactList implements Callable<Integer> {
        public Integer call() { return run("contact.list", new String[]{}); }
    }
    @Command(name = "remove") static class ContactRemove implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("contact.remove", new String[]{String.valueOf(id)}); }
    }
    @Command(name = "test") static class ContactTest implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("contact.test", new String[]{String.valueOf(id)}); }
    }

    @Command(name = "remind", description = "Create a reminder from natural language (F07)")
    static class RemindCmd implements Callable<Integer> {
        @Parameters(arity = "1..*") String[] text;
        public Integer call() { return run("remind", new String[]{String.join(" ", text)}); }
    }

    @Command(name = "task", subcommands = {
            TaskAdd.class, TaskList.class, TaskEdit.class, TaskDone.class, TaskSnooze.class, TaskDelete.class, TaskPriority.class})
    static class TaskCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }

    @Command(name = "add") static class TaskAdd implements Callable<Integer> {
        @Option(names = "--title") String title;
        @Option(names = "--due", description = "YYYY-MM-DD HH:MM, today 18:30, tomorrow [9am], in 30 minutes") String due;
        @Option(names = "--priority") String priority; @Option(names = "--category") String category;
        @Option(names = "--repeat") String repeat; @Option(names = "--notify") String notify;
        @Option(names = "--notes") String notes;
        public Integer call() {
            return run("task.add", new String[]{n(title), n(due), n(priority), n(category), n(repeat), n(notify), n(notes)});
        }
        private String n(String s) { return s == null ? "" : s; }
    }
    @Command(name = "list") static class TaskList implements Callable<Integer> {
        @Option(names = "--status") String status; @Option(names = "--category") String category; @Option(names = "--priority") String priority;
        public Integer call() { return run("task.list", new String[]{n(status), n(category), n(priority)}); }
        private String n(String s) { return s == null ? "" : s; }
    }
    @Command(name = "edit") static class TaskEdit implements Callable<Integer> {
        @Parameters(index = "0") int id;
        @Option(names = "--title") String title;
        @Option(names = "--due", description = "YYYY-MM-DD HH:MM, today 18:30, tomorrow [9am], in 30 minutes") String due;
        @Option(names = "--priority") String priority; @Option(names = "--category") String category;
        @Option(names = "--repeat") String repeat; @Option(names = "--notify") String notify;
        @Option(names = "--notes") String notes; @Option(names = "--scope", defaultValue = "this") String scope;
        public Integer call() {
            return run("task.edit", new String[]{
                    String.valueOf(id), n(title), n(due), n(priority), n(category), n(repeat), n(notify), n(notes), n(scope)});
        }
        private String n(String s) { return s == null ? "" : s; }
    }
    @Command(name = "done") static class TaskDone implements Callable<Integer> {
        @Parameters(index = "0") int id; @Option(names = "--already") boolean already;
        public Integer call() { return run("task.done", new String[]{String.valueOf(id), String.valueOf(already)}); }
    }
    @Command(name = "snooze") static class TaskSnooze implements Callable<Integer> {
        @Parameters(index = "0") int id;
        @Option(names = "--minutes") Integer minutes;
        @Parameters(index = "1", arity = "0..1") Integer minutesArg;
        public Integer call() {
            Integer m = minutes != null ? minutes : minutesArg;
            return run("task.snooze", new String[]{String.valueOf(id), m == null ? "" : String.valueOf(m)});
        }
    }
    @Command(name = "delete") static class TaskDelete implements Callable<Integer> {
        @Parameters(index = "0") int id;
        public Integer call() { return run("task.delete", new String[]{String.valueOf(id)}); }
    }
    @Command(name = "priority-preview") static class TaskPriority implements Callable<Integer> {
        public Integer call() { return run("task.priority-preview", new String[]{}); }
    }

    @Command(name = "profile", subcommands = {ProfileShow.class, ProfileRecompute.class})
    static class ProfileCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "show") static class ProfileShow implements Callable<Integer> {
        public Integer call() { return run("profile.show", new String[]{}); }
    }
    @Command(name = "recompute") static class ProfileRecompute implements Callable<Integer> {
        public Integer call() { return run("profile.recompute", new String[]{}); }
    }

    @Command(name = "stats") static class StatsCmd implements Callable<Integer> {
        @Option(names = "--range", defaultValue = "30d") String range;
        public Integer call() { return run("stats", new String[]{range}); }
    }

    @Command(name = "recommend") static class RecommendCmd implements Callable<Integer> {
        @Option(names = "--apply") Integer applyId; @Option(names = "--dismiss") Integer dismissId;
        public Integer call() {
            if (applyId != null) return run("recommend.apply", new String[]{String.valueOf(applyId)});
            if (dismissId != null) return run("recommend.dismiss", new String[]{String.valueOf(dismissId)});
            return run("recommend", new String[]{});
        }
    }

    @Command(name = "history") static class HistoryCmd implements Callable<Integer> {
        @Option(names = "--type") String type;
        @Option(names = "--since") String since;
        @Option(names = "--page", defaultValue = "1") int page;
        public Integer call() {
            return run("history", new String[]{type == null ? "" : type, since == null ? "" : since, String.valueOf(page)});
        }
    }

    @Command(name = "agent", description = "Talk to the RiseUp AI agent", subcommands = {AgentChat.class, AgentToday.class})
    static class AgentCmd implements Callable<Integer> { public Integer call() { return ExitCodes.USAGE; } }
    @Command(name = "chat", description = "Ask the agent; it can propose alarms/reminders (you confirm). No message = interactive session")
    static class AgentChat implements Callable<Integer> {
        @Parameters(arity = "0..*") String[] message;
        public Integer call() { return run("agent.chat", new String[]{message == null ? "" : String.join(" ", message)}); }
    }
    @Command(name = "today", description = "Morning briefing: today's alarms and tasks")
    static class AgentToday implements Callable<Integer> {
        public Integer call() { return run("agent.today", new String[]{}); }
    }

    @Command(name = "health", description = "Backend health and integration status")
    static class HealthCmd implements Callable<Integer> {
        public Integer call() {
            return run("health", new String[]{});
        }
    }
}
