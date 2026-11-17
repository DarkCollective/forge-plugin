// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.refactoring;

import com.forge.fl.psi.FLDefinition;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.ArrayList;
import java.util.List;

/** Refactor | Change Signature for FL words: a table of inputs and a field for the outputs. */
public final class FLChangeSignatureHandler implements ChangeSignatureHandler {
    @Nullable
    @Override
    public PsiElement findTargetMember(@NotNull PsiElement element) {
        return FLChangeSignature.target(element);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        PsiElement at = editor == null || file == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
        FLDefinition def = FLChangeSignature.target(at);
        if (def == null) {
            CommonRefactoringUtil.showErrorHint(project, editor, getTargetNotFoundMessage(), FLChangeSignature.TITLE, null);
            return;
        }
        new Dialog(project, def).show();
    }

    @Override
    public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable DataContext dataContext) {
        FLDefinition def = elements.length == 1 ? FLChangeSignature.target(elements[0]) : null;
        if (def != null) new Dialog(project, def).show();
        else if (dataContext != null && CommonDataKeys.EDITOR.getData(dataContext) != null) {
            CommonRefactoringUtil.showErrorHint(project, CommonDataKeys.EDITOR.getData(dataContext), getTargetNotFoundMessage(), FLChangeSignature.TITLE, null);
        }
    }

    @NotNull
    @Override
    public String getTargetNotFoundMessage() {
        return "Put the caret on a word definition or a use of a word";
    }

    /** One editable row: the old position (-1 for new), the header item, the value at calls. */
    private static final class RowModel {
        final int oldIndex;
        String item;
        String callValue;

        RowModel(int oldIndex, String item, String callValue) {
            this.oldIndex = oldIndex;
            this.item = item;
            this.callValue = callValue;
        }
    }

    private static final class Dialog extends DialogWrapper {
        private final Project project;
        private final FLDefinition def;
        private final ListTableModel<RowModel> model;
        private final JBTextField outputs = new JBTextField();
        private final JBLabel preview = new JBLabel();

        Dialog(Project project, FLDefinition def) {
            super(project, true);
            this.project = project;
            this.def = def;
            List<RowModel> rows = new ArrayList<>();
            for (FLChangeSignature.Row r : FLChangeSignature.rows(def)) rows.add(new RowModel(r.oldIndex(), r.item(), ""));
            model = new ListTableModel<>(new ColumnInfo[]{
                    new Column("Input (header item)", true),
                    new Column("Value at calls (new inputs)", false)}, rows);
            outputs.setText(FLChangeSignature.outputs(def));
            setTitle(FLChangeSignature.TITLE + ": " + def.getName());
            init();
            updatePreview();
        }

        private final class Column extends ColumnInfo<RowModel, String> {
            private final boolean item;

            Column(String name, boolean item) {
                super(name);
                this.item = item;
            }

            @Override
            public String valueOf(RowModel r) {
                return item ? r.item : r.callValue;
            }

            @Override
            public boolean isCellEditable(RowModel r) {
                return item || r.oldIndex < 0;
            }

            @Override
            public void setValue(RowModel r, String value) {
                if (item) r.item = value;
                else r.callValue = value;
                updatePreview();
            }
        }

        @Override
        protected JComponent createCenterPanel() {
            JBTable table = new JBTable(model);
            JComponent inputs = ToolbarDecorator.createDecorator(table)
                    .setAddAction(b -> {
                        model.addRow(new RowModel(-1, "name:any", ""));
                        updatePreview();
                    })
                    .setRemoveAction(b -> {
                        int row = table.getSelectedRow();
                        if (row >= 0) model.removeRow(row);
                        updatePreview();
                    })
                    .setMoveUpAction(b -> move(table, -1))
                    .setMoveDownAction(b -> move(table, 1))
                    .createPanel();
            outputs.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    updatePreview();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    updatePreview();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    updatePreview();
                }
            });
            return FormBuilder.createFormBuilder()
                    .addLabeledComponent("Inputs, deepest first:", inputs, true)
                    .addLabeledComponent("Outputs:", outputs)
                    .addLabeledComponent("New header:", preview)
                    .addTooltip("Calls get their arguments rewritten where the ones that move are simple values right before the call")
                    .getPanel();
        }

        private void move(JBTable table, int by) {
            int row = table.getSelectedRow();
            int to = row + by;
            if (row < 0 || to < 0 || to >= model.getRowCount()) return;
            model.exchangeRows(row, to);
            table.getSelectionModel().setSelectionInterval(to, to);
            updatePreview();
        }

        private List<FLChangeSignature.Row> rows() {
            List<FLChangeSignature.Row> out = new ArrayList<>();
            for (RowModel r : model.getItems()) out.add(new FLChangeSignature.Row(r.oldIndex, r.item, r.callValue));
            return out;
        }

        private void updatePreview() {
            preview.setText(": " + def.getName() + " " + FLChangeSignature.header(rows(), outputs.getText()));
        }

        @Nullable
        @Override
        protected ValidationInfo doValidate() {
            for (RowModel r : model.getItems()) if (r.item == null || r.item.isBlank()) return new ValidationInfo("An input is empty");
            return null;
        }

        @Override
        protected void doOKAction() {
            FLChangeSignature.Plan plan = FLChangeSignature.plan(def, rows(), outputs.getText());
            if (!plan.errors().isEmpty()) {
                Messages.showErrorDialog(project, String.join("\n", plan.errors()), FLChangeSignature.TITLE);
                return;
            }
            if (!plan.skippedCalls().isEmpty()) {
                int answer = Messages.showYesNoDialog(project, "These calls cannot be updated and will be left as they are:\n"
                        + String.join("\n", plan.skippedCalls()) + "\n\nChange the signature anyway?", FLChangeSignature.TITLE, null);
                if (answer != Messages.YES) return;
            }
            super.doOKAction();
            FLChangeSignature.perform(project, plan);
        }
    }
}
