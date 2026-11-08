package com.riseup.cli.commands;

import com.riseup.cli.command.UserInputError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Reads a verification photo from disk; same JPEG/PNG + 5 MB rules as the GUI. */
final class PhotoFiles {
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private PhotoFiles() {}

    static String readBase64(String file) {
        Path path = Path.of(file);
        byte[] b;
        try {
            b = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UserInputError("Cannot read photo: " + path);
        }
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        if (!jpeg && !png) throw new UserInputError("Only JPEG or PNG photos are supported: " + path.getFileName());
        if (b.length > MAX_BYTES) throw new UserInputError("Photo is larger than 5 MB: " + path.getFileName());
        return Base64.getEncoder().encodeToString(b);
    }
}
