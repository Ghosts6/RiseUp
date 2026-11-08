package com.riseup.cli.command;
import com.riseup.cli.format.OutputFormatter;
import java.io.Console;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Scanner;

public class Io {
    private final boolean assumeYes;
    private final boolean interactive;
    private final OutputFormatter formatter;
    private final PrintStream out;
    private final PrintStream err;
    private final Scanner scanner;

    public Io(boolean assumeYes, boolean interactive, OutputFormatter formatter) {
        this(assumeYes, interactive, formatter, System.out, System.err);
    }
    public Io(boolean assumeYes, boolean interactive, OutputFormatter formatter, PrintStream out, PrintStream err) {
        this.assumeYes = assumeYes; this.interactive = interactive; this.formatter = formatter;
        this.out = out; this.err = err; this.scanner = new Scanner(System.in);
    }
    public void echo(String text) { out.println(text); }
    public void error(String text) { err.println(text); }
    public void print(Result result) {
        String formatted = formatter.format(result);
        if (formatted != null && !formatted.isBlank()) out.println(formatted);
    }
    public void printRaw(Object data) { out.println(formatter.format(Result.ok(data))); }
    public boolean confirm(String text, boolean defaultValue) {
        if (assumeYes) return true;
        if (!interactive) return defaultValue;
        out.print(text + (defaultValue ? " [Y/n] " : " [y/N] ")); out.flush();
        String line = readLine();
        if (line == null || line.isBlank()) return defaultValue;
        String a = line.trim().toLowerCase(Locale.ROOT);
        if (a.startsWith("y")) return true;
        if (a.startsWith("n")) return false;
        return defaultValue;
    }
    public boolean isJson() { return formatter instanceof com.riseup.cli.format.JsonFormatter; }
    /** Prompt that returns null (instead of failing) when input ends or the shell is non-interactive. */
    public String readOptional(String text) {
        out.print(text + "> "); out.flush();
        return readLine();
    }
    public String prompt(String text) {
        if (!interactive) throw new UserInputError(text + " (re-run with more detail)");
        out.print(text + ": "); out.flush();
        String line = readLine();
        if (line == null) throw new UserInputError(text + " (re-run with more detail)");
        return line.trim();
    }
    private String readLine() {
        Console c = System.console();
        if (c != null) return c.readLine();
        if (scanner.hasNextLine()) return scanner.nextLine();
        return null;
    }
}
