package com.riseup.cli.command;
import com.riseup.cli.ExitCodes;
public record Result(Object data, String message, int exitCode) {
    public static Result ok(Object data) { return new Result(data, "", ExitCodes.SUCCESS); }
    public static Result ok(Object data, String message) { return new Result(data, message, ExitCodes.SUCCESS); }
    public static Result message(String message) { return new Result(null, message, ExitCodes.SUCCESS); }
    public static Result error(Object data, String message) { return new Result(data, message, ExitCodes.ERROR); }
}
