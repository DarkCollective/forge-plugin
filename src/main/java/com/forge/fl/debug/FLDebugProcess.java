// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.forge.fl.FLFile;
import com.forge.fl.FLFileType;
import com.forge.fl.FLLanguage;
import com.forge.fl.run.FLDebugProcessHandler;
import com.forge.fl.run.FLRunConfiguration;
import com.forge.fl.run.FLUnitFiles;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
    /** Evaluations sent to the runner and not answered yet, by id. */
    private final Map<Integer, CompletableFuture<JsonObject>> evaluations = new ConcurrentHashMap<>();
    private final AtomicInteger nextEvaluation = new AtomicInteger(1);
    /** The context of the current pause, or null. */
    private volatile @Nullable FLSuspendContext current;
    private final FLEvaluator.Backend backend = new FLEvaluator.Backend() {
        @NotNull
        @Override
        public CompletableFuture<JsonObject> evaluate(@NotNull JsonObject command) {
            return FLDebugProcess.this.evaluate(command);
        }

        @Override
        public void valueChanged(@NotNull JsonObject valueSet) {
            FLSuspendContext context = current;
            if (context != null) refresh(context, valueSet);
        }

        @NotNull
        @Override
        public Project project() {
            return getSession().getProject();
        }
    };

    FLDebugProcess(@NotNull XDebugSession session, @NotNull FLDebugProcessHandler handler, @NotNull ConsoleView console,
                   @NotNull FLRunConfiguration configuration) {
        super(session);
        this.handler = handler;
        this.console = console;
        this.files = configuration.unitFiles();
        this.handlers = new XBreakpointHandler<?>[]{new LineHandler()};
        handler.setEventListener(this::onEvent);
        handler.addProcessListener(new ProcessListener() {
            @Override
            public void processTerminated(@NotNull ProcessEvent event) {
                failEvaluations("The FL run has ended");
            }
        });
    }

    // ---- evaluation -----------------------------------------------------------------------------------------

    /** Sends an {@code evaluate} or {@code setValue} command; completes with the runner's answer event. */
    private CompletableFuture<JsonObject> evaluate(JsonObject command) {
        int id = nextEvaluation.getAndIncrement();
        command.addProperty("id", id);
        CompletableFuture<JsonObject> answer = new CompletableFuture<>();
        evaluations.put(id, answer);
        if (handler.isProcessTerminated() || !handler.send(command.toString())) {
            evaluations.remove(id);
            answer.completeExceptionally(new IllegalStateException("The FL run has ended"));
            return answer;
        }
        return answer.orTimeout(EVALUATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((r, e) -> evaluations.remove(id));
    }

    /** The runner limits an evaluation to 5 s of run time; this only guards against a runner that never answers. */
    private static final int EVALUATION_TIMEOUT_SECONDS = 60;

    private void failEvaluations(String message) {
        for (Integer id : List.copyOf(evaluations.keySet())) {
            CompletableFuture<JsonObject> f = evaluations.remove(id);
            if (f != null) f.completeExceptionally(new IllegalStateException(message));
        }
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
        String kind = str(event, "event");
        if ("evaluated".equals(kind) || "valueSet".equals(kind)) {
            JsonElement id = event.get("id");
            CompletableFuture<JsonObject> f = id == null || id.isJsonNull() ? null : evaluations.remove(id.getAsInt());
            if (f != null) f.complete(event);
            return;
        }
        if (!"paused".equals(kind)) return;
        FLSuspendContext context = ApplicationManager.getApplication().runReadAction(
                (Computable<FLSuspendContext>) () -> suspendContext(event, files, backend));
        current = context;
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

    /** The frames of a paused event (innermost first) with their source positions, without evaluation. */
    static FLSuspendContext suspendContext(@NotNull JsonObject event, @NotNull FLUnitFiles files) {
        return suspendContext(event, files, null);
    }

    /** The frames of a paused event (innermost first) with their source positions; each evaluates through {@code backend}. */
    static FLSuspendContext suspendContext(@NotNull JsonObject event, @NotNull FLUnitFiles files,
                                           @Nullable FLEvaluator.Backend backend) {
        boolean canSet = backend != null && event.has("canSetValues") && event.get("canSetValues").getAsBoolean();
        List<FLStackFrame> frames = new ArrayList<>();
        JsonArray framesJson = event.getAsJsonArray("frames");
        if (framesJson != null) {
            for (JsonElement e : framesJson) {
                FLStackFrame.Info info = info(e.getAsJsonObject());
                XSourcePosition position = position(info, files);
                FLEvaluator evaluator = backend == null ? null : new FLEvaluator(backend, frames.size(), info, position);
                List<FLValue> stack = frames.isEmpty() ? stackValues(event) : null;
                if (canSet && evaluator != null) attachModifiers(evaluator, info, stack);
                frames.add(new FLStackFrame(info, position, stack, evaluator));
            }
        }
        return new FLSuspendContext(frames);
    }

    /** The data stack of a paused or valueSet event, top first ({@code [0]} is the top). */
    private static List<FLValue> stackValues(JsonObject event) {
        List<FLValue> stack = new ArrayList<>();
        JsonArray stackJson = event.getAsJsonArray("stack");
        if (stackJson != null) {
            for (int i = stackJson.size() - 1, k = 0; i >= 0; i--, k++) {
                JsonObject v = stackJson.get(i).getAsJsonObject();
                stack.add(new FLValue("[" + k + "]", str(v, "type"), str(v, "value"), i));
            }
        }
        return stack;
    }

    private static FLStackFrame.Info info(JsonObject f) {
        List<FLValue> locals = new ArrayList<>();
        JsonArray localsJson = f.getAsJsonArray("locals");
        if (localsJson != null) {
            for (JsonElement l : localsJson) {
                JsonObject o = l.getAsJsonObject();
                locals.add(new FLValue(str(o, "name"), str(o, "type"), str(o, "value")));
            }
        }
        return new FLStackFrame.Info(str(f, "word"), "quot".equals(str(f, "kind")),
                nullableStr(f, "unit"), integer(f, "line"), integer(f, "col"), locals);
    }

    /** Set Value on the frame's locals and, on the innermost frame, the stack entries. */
    private static void attachModifiers(FLEvaluator evaluator, FLStackFrame.Info info, @Nullable List<FLValue> stack) {
        for (FLValue v : info.locals()) v.setModifier(evaluator);
        if (stack != null) {
            for (FLValue v : stack) v.setModifier(evaluator);
        }
    }

    /**
     * A value was set: the {@code valueSet} event holds the frames and the stack after the change. The paused
     * context keeps its frames (and the selection); their values are replaced, then the views are rebuilt.
     */
    static void refresh(@NotNull FLSuspendContext context, @NotNull JsonObject event) {
        JsonArray framesJson = event.getAsJsonArray("frames");
        List<FLStackFrame> frames = context.frames();
        if (framesJson == null) return;
        for (int i = 0; i < frames.size() && i < framesJson.size(); i++) {
            FLStackFrame frame = frames.get(i);
            FLStackFrame.Info info = info(framesJson.get(i).getAsJsonObject());
            List<FLValue> stack = i == 0 ? stackValues(event) : null;
            if (frame.getEvaluator() instanceof FLEvaluator evaluator) {
                attachModifiers(evaluator, info, stack);
                evaluator.update(info);
            }
            frame.update(info, stack);
        }
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

    /**
     * Expression editors (Evaluate, watches) edit an FL fragment: highlighted and completed like FL, without
     * diagnostics, since the fragment cannot see the paused word's locals.
     */
    static final XDebuggerEditorsProvider EDITORS = new XDebuggerEditorsProvider() {
        @NotNull
        @Override
        public FileType getFileType() {
            return FLFileType.INSTANCE;
        }

        @NotNull
        @Override
        public Document createDocument(@NotNull Project project, @NotNull XExpression expression,
                                       @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
            PsiFile fragment = ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () -> {
                PsiFile f = PsiFileFactory.getInstance(project).createFileFromText("fragment.fl", FLLanguage.INSTANCE,
                        expression.getExpression(), true, false);
                f.putUserData(FLFile.DEBUGGER_FRAGMENT, Boolean.TRUE);
                return f;
            });
            Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
            return document != null ? document : EditorFactory.getInstance().createDocument(expression.getExpression());
        }
    };
}
