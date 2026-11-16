// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * One field per input of the word, deepest first, filled with the last values used or the header defaults.
 * A {@code choice} input is a combo box of its options and a {@code bool} input a true/false box. When the header
 * is not understood, one field takes the inputs as FL source.
 */
final class FLRunWordDialog extends DialogWrapper {
    private static final String MEMORY_PREFIX = "com.forge.fl.run.input.";

    private final Project project;
    private final String qname;
    private final @Nullable List<FLRunInputs.Slot> slots;
    private final List<JComponent> fields = new ArrayList<>();
    private final JBTextField rawInputs = new JBTextField(40);
    private final JBLabel preview = new JBLabel();

    FLRunWordDialog(@NotNull Project project, @NotNull String qname, @Nullable List<FLRunInputs.Slot> slots, @NotNull String verb) {
        super(project);
        this.project = project;
        this.qname = qname;
        this.slots = slots;
        setTitle(verb + " " + qname);
        setOKButtonText(verb);
        init();
        updatePreview();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        FormBuilder form = FormBuilder.createFormBuilder();
        PropertiesComponent memory = PropertiesComponent.getInstance(project);
        if (slots == null) {
            rawInputs.setText(memory.getValue(MEMORY_PREFIX + qname + ".raw", ""));
            rawInputs.getEmptyText().setText("FL literals, deepest first, e.g. \"hello\" 3");
            listen(rawInputs);
            form.addLabeledComponent("Inputs:", rawInputs)
                    .addTooltip("The header was not understood; the inputs are FL source pushed before the word");
        } else {
            for (int i = 0; i < slots.size(); i++) {
                FLRunInputs.Slot slot = slots.get(i);
                String remembered = memory.getValue(MEMORY_PREFIX + qname + "." + i);
                JComponent field = field(slot, remembered != null ? remembered : FLRunInputs.initialText(slot));
                fields.add(field);
                String label = slot.label() + (slot.defaultText() == null ? "" : " = " + slot.defaultText());
                form.addLabeledComponent(label + ":", field);
                if (FLRunInputs.isStringType(slot) && slot.options() == null) {
                    form.addTooltip("Taken as text; start with \" to write an FL string literal");
                }
            }
        }
        preview.setFont(UIUtil.getLabelFont(UIUtil.FontSize.SMALL));
        preview.setCopyable(true);
        form.addSeparator().addLabeledComponent("Program:", preview);
        return form.getPanel();
    }

    private JComponent field(FLRunInputs.Slot slot, String text) {
        List<String> options = slot.options();
        if (options == null && slot.type().equals("bool")) options = List.of("true", "false");
        if (options != null && !options.isEmpty()) {
            ComboBox<String> box = new ComboBox<>(options.toArray(String[]::new));
            String selected = FLRunInputs.unquote(text);
            if (options.contains(selected)) box.setSelectedItem(selected);
            box.addActionListener(e -> updatePreview());
            return box;
        }
        JBTextField f = new JBTextField(text, 40);
        if (!FLRunInputs.isStringType(slot)) f.getEmptyText().setText(hint(slot.type()));
        listen(f);
        return f;
    }

    private static String hint(String type) {
        return switch (type) {
            case "int" -> "e.g. 42 or 0xff";
            case "num", "number" -> "e.g. 1.5";
            case "bytes" -> "e.g. #[ 68 69 ] or \"text\"";
            case "list" -> "e.g. [ 1 2 3 ]";
            case "record" -> "e.g. { \"k\" 1 } as an FL literal";
            default -> "an FL literal";
        };
    }

    private void listen(JBTextField f) {
        f.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updatePreview();
            }
        });
    }

    private String textOf(int i) {
        JComponent c = fields.get(i);
        if (c instanceof ComboBox<?> box) {
            String option = String.valueOf(box.getSelectedItem());
            return slots != null && FLRunInputs.isStringType(slots.get(i)) ? FLRunInputs.quote(option) : option;
        }
        return ((JBTextField) c).getText();
    }

    /** The FL program for the current fields. */
    @NotNull
    String program() {
        if (slots == null) {
            String raw = rawInputs.getText().strip();
            return raw.isEmpty() ? qname : raw + " " + qname;
        }
        List<String> literals = new ArrayList<>();
        for (int i = 0; i < slots.size(); i++) literals.add(FLRunInputs.literal(slots.get(i), textOf(i)));
        return FLRunInputs.program(qname, literals);
    }

    private void updatePreview() {
        String text;
        try {
            text = program();
        } catch (IllegalArgumentException e) {
            text = "(" + e.getMessage() + ")";
        }
        preview.setText(text.length() > 200 ? text.substring(0, 197) + "..." : text);
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (slots == null) return null;
        for (int i = 0; i < slots.size(); i++) {
            try {
                FLRunInputs.literal(slots.get(i), textOf(i));
            } catch (IllegalArgumentException e) {
                return new ValidationInfo(e.getMessage(), fields.get(i));
            }
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        PropertiesComponent memory = PropertiesComponent.getInstance(project);
        if (slots == null) {
            memory.setValue(MEMORY_PREFIX + qname + ".raw", rawInputs.getText());
        } else {
            for (int i = 0; i < slots.size(); i++) memory.setValue(MEMORY_PREFIX + qname + "." + i, textOf(i));
        }
        super.doOKAction();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return fields.isEmpty() ? rawInputs : fields.get(0);
    }
}
