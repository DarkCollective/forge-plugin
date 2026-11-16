// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.vectors;

import com.forge.fl.run.FLForgeEnvironment;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.List;

/** The form of an FL Vectors run configuration. */
final class FLVectorsSettingsEditor extends SettingsEditor<FLVectorsRunConfiguration> {
    private final TextFieldWithBrowseButton checkout = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton node = new TextFieldWithBrowseButton();
    private final JBTextField files = new JBTextField();
    private final JBTextField op = new JBTextField();
    private final JBTextField entries = new JBTextField();
    private final JComponent panel;

    FLVectorsSettingsEditor(@NotNull Project project) {
        browse(project, checkout, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Forge Checkout").withDescription("The Forge repository, built with 'npm run build'"));
        browse(project, node, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Node.js").withDescription("The node executable (Node 20 or newer)"));
        Path found = FLForgeEnvironment.findCheckout(project, null);
        ((JBTextField) checkout.getTextField()).getEmptyText().setText(found == null ? "Path of the Forge checkout" : "Default: " + found);
        String nodeFound = FLForgeEnvironment.findNode();
        ((JBTextField) node.getTextField()).getEmptyText().setText(nodeFound == null ? "node was not found; set its path" : "Default: " + nodeFound);
        files.getEmptyText().setText("All files of packages/ops/vocab; or e.g. base32.test.json, hex.test.json");
        op.getEmptyText().setText("All ops; or e.g. base32:encode (an alias works too)");
        entries.getEmptyText().setText("All entries; or e.g. base32.test.json:0,3;hex.test.json:7");
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Forge checkout:", checkout)
                .addLabeledComponent("Node.js:", node)
                .addLabeledComponent("Vector files:", files)
                .addLabeledComponent("Op:", op)
                .addLabeledComponent("Entries:", entries)
                .addTooltip("Entries are array indices per file; a whole file also checks its vocabulary's coverage")
                .getPanel();
    }

    private static void browse(Project project, TextFieldWithBrowseButton field, FileChooserDescriptor descriptor) {
        field.addActionListener(e -> {
            String current = field.getText().strip();
            VirtualFile start = current.isEmpty() ? null : LocalFileSystem.getInstance().findFileByPath(current);
            VirtualFile chosen = FileChooser.chooseFile(descriptor, project, start);
            if (chosen != null) field.setText(chosen.getPath());
        });
    }

    @Override
    protected void resetEditorFrom(@NotNull FLVectorsRunConfiguration c) {
        checkout.setText(c.getCheckout());
        node.setText(c.getNodePath());
        files.setText(String.join(", ", c.getFiles()));
        op.setText(c.getOp());
        entries.setText(FLVectorsRunConfiguration.formatEntries(c.getEntries()));
    }

    @Override
    protected void applyEditorTo(@NotNull FLVectorsRunConfiguration c) {
        c.setCheckout(checkout.getText().strip());
        c.setNodePath(node.getText().strip());
        List<String> list = StringUtil.split(files.getText(), ",").stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        c.setFiles(list);
        c.setOp(op.getText().strip());
        c.setEntries(FLVectorsRunConfiguration.parseEntries(entries.getText()));
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return panel;
    }
}
