package com.riseup.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.riseup.cli.ExitCodes;
import com.riseup.cli.api.ApiClient;
import com.riseup.cli.commands.ConcreteCommands;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;

/**
 * Maps command names to factories that bind argv into {@link Command} instances.
 */
public class CommandRegistry {
    private final Map<String, BiFunction<String[], Boolean, Command>> factories = new LinkedHashMap<>();

    public CommandRegistry() {
        ConcreteCommands.registerAll(this);
    }

    public void register(String name, BiFunction<String[], Boolean, Command> factory) {
        if (factories.containsKey(name)) throw new IllegalArgumentException("Command '" + name + "' already registered");
        factories.put(name, factory);
    }

    public Command bind(String name, String[] args, boolean yes) {
        BiFunction<String[], Boolean, Command> factory = factories.get(name);
        if (factory == null) throw new UserInputError("Unknown command: " + name);
        return factory.apply(args == null ? new String[0] : args, yes);
    }

    public Set<String> names() { return new TreeSet<>(factories.keySet()); }

    public static int runBound(Command command, ApiClient api, Io io) {
        try {
            return command.execute(api, io);
        } catch (UserInputError e) {
            io.error("Error: " + e.getMessage());
            return ExitCodes.USAGE;
        } catch (RuntimeException e) {
            io.error("Error: " + e.getMessage());
            return ExitCodes.ERROR;
        }
    }
}
