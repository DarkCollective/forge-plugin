// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * The runner process of a debug session. Lines of stdout that start with {@link #MARKER} are debugger events:
 * they go to the event listener and not to the console. Commands are written to the runner's stdin, one line each.
 */
public final class FLDebugProcessHandler extends KillableProcessHandler {
    public static final String MARKER = "\u0001FL-DEBUG ";

    private final StringBuilder pending = new StringBuilder();
    private volatile Consumer<String> events = e -> { };

    FLDebugProcessHandler(@NotNull GeneralCommandLine cmd) throws ExecutionException {
        super(cmd);
    }

    /** Receives the JSON text of each event, on the output reader thread. */
    public void setEventListener(@NotNull Consumer<String> listener) {
        events = listener;
    }

    /** Writes one line to the runner; false when the runner is gone. */
    public synchronized boolean send(@NotNull String line) {
        try {
            OutputStream in = getProcess().getOutputStream();
            in.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            in.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
        if (outputType != ProcessOutputTypes.STDOUT) {
            super.notifyTextAvailable(text, outputType);
            return;
        }
        StringBuilder out = new StringBuilder();
        synchronized (pending) {
            pending.append(text);
            int nl;
            while ((nl = pending.indexOf("\n")) >= 0) {
                String line = pending.substring(0, nl + 1);
                pending.delete(0, nl + 1);
                if (line.startsWith(MARKER)) {
                    flush(out);
                    events.accept(line.substring(MARKER.length()).strip());
                } else {
                    out.append(line);
                }
            }
            // Text without a line break yet: shown now unless it may be the start of an event line.
            if (pending.length() > 0 && !MARKER.startsWith(pending.substring(0, Math.min(pending.length(), MARKER.length())))
                    && !pending.toString().startsWith(MARKER)) {
                out.append(pending);
                pending.setLength(0);
            }
        }
        flush(out);
    }

    private void flush(StringBuilder out) {
        if (out.length() > 0) {
            super.notifyTextAvailable(out.toString(), ProcessOutputTypes.STDOUT);
            out.setLength(0);
        }
    }
}
