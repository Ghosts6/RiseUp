package com.riseup.cli.command;
public class UserInputError extends RuntimeException {
    public UserInputError(String message) { super(message); }
}
