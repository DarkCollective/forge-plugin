// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLFileType;
import com.forge.fl.run.FLDebugProcessHandler;
import com.forge.fl.run.FLRunConfiguration;
import com.forge.fl.run.FLUnitFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A debug session of the FL runner (forge-runner.mjs in debug mode): line breakpoints, pause, resume, step
 * over/into/out, run to cursor, frames with their locals and the data stack. The protocol is described at the
 * top of the runner.
 */
final class FLDebugProcess extends XDebugProcess {
    private final FLDebugProcessHandler handler;
    private final ConsoleView console;
    private final FLUnitFiles files;
    private final Set<XLineBreakpoint<XBreakpointProperties<?>>> breakpoints = ConcurrentHashMap.newKeySet();
    private final XBreakpointHandler<?>[] handlers;
    private volatile boolean started;
    /** "unit:line" of a pending run to cursor, or null. */
    private volatile String runToTarget;

    FLDebugProcess(@NotNull XDebugSession session, @NotNull FLDebugProcessHandler handler, @NotNull ConsoleView console,
                   @NotNull FLRunConfiguration configuration) {
        super(session);
        this.handler = handler;
        this.console = console;
        this.files = configuration.unitFiles();
        this.handlers = new XBreakpointHandler<?>[]{new LineHandler()};
        handler.setEventListener(this::onEvent);
    }

    @NotNull
    @Override
    public XDebuggerEditorsProvider getEditorsProvider() {
        return EDITORS;
    }

    @Override
    public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
        return handlers;
    }

    @Nullable
    @Override
    protected ProcessHandler doGetProcessHandler() {
        return handler;
    }

    @NotNull
    @Override
    public ExecutionConsole createConsole() {
        return console;
    }

    @Override
    public void sessionInitialized() {
        started = true;
        handler.send("{\"cmd\":\"start\",\"breakpoints\":" + breakpointsJson() + "}");
    }

    @Override
    public void startPausing() {
        handler.send("{\"cmd\":\"pause\"}");
    }

    @Override
    public void resume(@Nullable XSuspendContext context) {
        handler.send("{\"cmd\":\"resume\"}");
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        handler.send("{\"cmd\":\"stepOver\"}");
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        handler.send("{\"cmd\":\"stepInto\"}");
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        handler.send("{\"cmd\":\"stepOut\"}");
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
        String unit = files.unitOf(position.getFile());
        if (unit == null) {
            resume(context);
            return;
        }
        runToTarget = unit + ":" + (position.getLine() + 1);
        sendBreakpoints();
        resume(context);
    }

    @Override
    public void stop() {
        handler.send("{\"cmd\":\"stop\"}");
    }

    // ---- breakpoints ----------------------------------------------------------------------------------------

    private final class LineHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<?>>> {
        LineHandler() {
            super(FLLineBreakpointType.class);
        }

        @Override
        public void registerBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint) {
            breakpoints.add(breakpoint);
            VirtualFile file = fileOf(breakpoint);
            if (file != null && files.unitOf(file) == null) {
                getSession().setBreakpointInvalid(breakpoint, "This file is not loaded by the run: it is neither in "
                        + "packages/ops/vocab of the Forge checkout nor one of the extra files");
            } else {
                getSession().setBreakpointVerified(breakpoint);
            }
            if (started) sendBreakpoints();
        }

        @Override
        public void unregisterBreakpoint(@NotNull XLineBreakpoint<XBreakpointProperties<?>> breakpoint, boolean temporary) {
            breakpoints.remove(breakpoint);
            if (started) sendBreakpoints();
        }
    }

    @Nullable
    private static VirtualFile fileOf(XLineBreakpoint<?> breakpoint) {
        XSourcePosition p = breakpoint.getSourcePosition();
        return p == null ? null : p.getFile();
    }

    private void sendBreakpoints() {
        handler.send("{\"cmd\":\"setBreakpoints\",\"breakpoints\":" + breakpointsJson() + "}");
    }

    /** {@code [{"unit":"x.fth","line":12}, ...]}, lines 1-based; nothing while breakpoints are muted. */
    private String breakpointsJson() {
        JsonArray out = new JsonArray();
        if (!getSession().areBreakpointsMuted()) {
            for (XLineBreakpoint<?> bp : breakpoints) {
                if (!bp.isEnabled()) continue;
                VirtualFile file = fileOf(bp);
                String unit = file == null ? null : files.unitOf(file);
                if (unit == null) continue;
                out.add(entry(unit, bp.getLine() + 1));
            }
        }
        String target = runToTarget;
        if (target != null) {
            int colon = target.lastIndexOf(':');
            out.add(entry(target.substring(0, colon), Integer.parseInt(target.substring(colon + 1))));
        }
        return out.toString();
    }

    private static JsonObject entry(String unit, int line) {
        JsonObject o = new JsonObject();
        o.addProperty("unit", unit);
        o.addProperty("line", line);
        return o;
    }

    // ---- events ---------------------------------------------------------------------------------------------

    private void onEvent(String json) {
        JsonObject event;
        try {
            event = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        if (!"paused".equals(str(event, "event"))) return;
        FLSuspendContext context = ApplicationManager.getApplication().runReadAction(
                (Computable<FLSuspendContext>) () -> suspendContext(event, files));
        String reason = str(event, "reason");
        FLStackFrame.Info top = context.frames().isEmpty() ? null : context.frames().get(0).info();
        if (runToTarget != null) {
            runToTarget = null;
            sendBreakpoints();
        }
        XLineBreakpoint<?> hit = "breakpoint".equals(reason) && top != null ? breakpointAt(top) : null;
        if (hit != null) {
            if (!getSession().breakpointReached(hit, null, context)) resume(context);
        } else {
            getSession().positionReached(context);
        }
    }

    @Nullable
    private XLineBreakpoint<?> breakpointAt(FLStackFrame.Info frame) {
        if (frame.unit() == null || frame.line() == null) return null;
        for (XLineBreakpoint<?> bp : breakpoints) {
            VirtualFile file = fileOf(bp);
            if (file != null && bp.getLine() + 1 == frame.line() && frame.unit().equals(files.unitOf(file))) return bp;
        }
        return null;
    }

    /** The frames of a paused event (innermost first) with their source positions. */
    static FLSuspendContext suspendContext(@NotNull JsonObject event, @NotNull FLUnitFiles files) {
        List<FLValue> stack = new ArrayList<>();
        JsonArray stackJson = event.getAsJsonArray("stack");
        if (stackJson != null) {
            for (int i = stackJson.size() - 1, k = 0; i >= 0; i--, k++) {
                JsonObject v = stackJson.get(i).getAsJsonObject();
                stack.add(new FLValue("[" + k + "]", str(v, "type"), str(v, "value")));
            }
        }
        List<FLStackFrame> frames = new ArrayList<>();
        JsonArray framesJson = event.getAsJsonArray("frames");
        if (framesJson != null) {
            for (JsonElement e : framesJson) {
                JsonObject f = e.getAsJsonObject();
                List<FLValue> locals = new ArrayList<>();
                JsonArray localsJson = f.getAsJsonArray("locals");
                if (localsJson != null) {
                    for (JsonElement l : localsJson) {
                        JsonObject o = l.getAsJsonObject();
                        locals.add(new FLValue(str(o, "name"), str(o, "type"), str(o, "value")));
                    }
                }
                FLStackFrame.Info info = new FLStackFrame.Info(str(f, "word"), "quot".equals(str(f, "kind")),
                        nullableStr(f, "unit"), integer(f, "line"), integer(f, "col"), locals);
                frames.add(new FLStackFrame(info, position(info, files), frames.isEmpty() ? stack : null));
            }
        }
        return new FLSuspendContext(frames);
    }

    @Nullable
    private static XSourcePosition position(FLStackFrame.Info info, FLUnitFiles files) {
        if (info.unit() == null || info.line() == null) return null;
        VirtualFile file = files.fileOf(info.unit());
        if (file == null) return null;
        int col = info.col() == null ? 0 : info.col() - 1;
        return XDebuggerUtil.getInstance().createPosition(file, info.line() - 1, col);
    }

    private static String str(JsonObject o, String key) {
        String s = nullableStr(o, key);
        return s == null ? "" : s;
    }

    @Nullable
    private static String nullableStr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    @Nullable
    private static Integer integer(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsInt();
    }

    /** No expression evaluation yet: the editors provider only has to exist. */
    private static final XDebuggerEditorsProvider EDITORS = new XDebuggerEditorsProvider() {
        @NotNull
        @Override
        public FileType getFileType() {
            return FLFileType.INSTANCE;
        }

        @NotNull
        @Override
        public Document createDocument(@NotNull Project project, @NotNull XExpression expression,
                                       @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
            return EditorFactory.getInstance().createDocument(expression.getExpression());
        }
    };
}
