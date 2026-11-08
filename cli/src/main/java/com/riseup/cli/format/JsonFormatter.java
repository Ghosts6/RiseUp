package com.riseup.cli.format;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.riseup.cli.command.Result;
import java.util.Map;

public class JsonFormatter implements OutputFormatter {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    @Override public String format(Result result) {
        Object payload = result.data() != null ? result.data() : Map.of("message", result.message());
        try { return mapper.writeValueAsString(payload); }
        catch (Exception e) { return String.valueOf(payload); }
    }
}
