package com.riseup.cli.command;
import com.riseup.cli.api.ApiClient;
public interface Command {
    int execute(ApiClient api, Io io);
}
