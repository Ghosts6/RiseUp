package com.riseup.cli.format;
import com.riseup.cli.command.Result;
public interface OutputFormatter {
    String format(Result result);
}
