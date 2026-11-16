// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.debug;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A paused run: one execution stack, the frames innermost first. */
final class FLSuspendContext extends XSuspendContext {
    private final Stack stack;

    FLSuspendContext(@NotNull List<FLStackFrame> frames) {
        this.stack = new Stack(frames);
    }

    List<FLStackFrame> frames() {
        return stack.frames;
    }

    @Nullable
    @Override
    public XExecutionStack getActiveExecutionStack() {
        return stack;
    }

    @Override
    public XExecutionStack @NotNull [] getExecutionStacks() {
        return new XExecutionStack[]{stack};
    }

    private static final class Stack extends XExecutionStack {
        final List<FLStackFrame> frames;

        Stack(List<FLStackFrame> frames) {
            super("FL");
            this.frames = frames;
        }

        @Nullable
        @Override
        public XStackFrame getTopFrame() {
            return frames.isEmpty() ? null : frames.get(0);
        }

        @Override
        public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
            container.addStackFrames(firstFrameIndex >= frames.size() ? List.of() : frames.subList(firstFrameIndex, frames.size()), true);
        }
    }
}
