// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
package com.forge.fl.run;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;

/** The form of an FL run configuration. */
final class FLRunSettingsEditor extends SettingsEditor<FLRunConfiguration> {
    private final TextFieldWithBrowseButton checkout = new TextFieldWithBrowseButton();
    private final TextFieldWithBrowseButton node = new TextFieldWithBrowseButton();
    private final JBTextArea program = new JBTextArea(5, 60);
    private final JBTextField vocab = new JBTextField();
    private final JBTextArea extraFiles = new JBTextArea(3, 60);
    private final JBTextField maxSteps = new JBTextField();
    private final JBTextField maxMillis = new JBTextField();
    private final JComponent panel;

    FLRunSettingsEditor(@NotNull Project project) {
        browse(project, checkout, FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Forge Checkout").withDescription("The Forge repository, built with 'npm run build'"));
        browse(project, node, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                .withTitle("Node.js").withDescription("The node executable (Node 20 or newer)"));
        Path found = FLForgeEnvironment.findCheckout(project, null);
        ((JBTextField) checkout.getTextField()).getEmptyText().setText(found == null ? "Path of the Forge checkout" : "Default: " + found);
        String nodeFound = FLForgeEnvironment.findNode();
        ((JBTextField) node.getTextField()).getEmptyText().setText(nodeFound == null ? "node was not found; set its path" : "Default: " + nodeFound);
        program.setFont(new Font(Font.MONOSPACED, Font.PLAIN, program.getFont().getSize()));
        vocab.getEmptyText().setText("Current vocabulary while running, e.g. base32 (gives access to its private words)");
        extraFiles.getEmptyText().setText("FL files outside the checkout to load as user units, one path per line");
        maxSteps.getEmptyText().setText("Engine default");
        maxMillis.getEmptyText().setText("Engine default");
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Forge checkout:", checkout)
                .addLabeledComponent("Node.js:", node)
                .addLabeledComponent("FL program:", new JBScrollPane(program), true)
                .addTooltip("Console source: inputs as FL literals, then the word, e.g. \"hello\" standard true base32:encode")
                .addLabeledComponent("Vocabulary:", vocab)
                .addLabeledComponent("Extra files:", new JBScrollPane(extraFiles), true)
                .addLabeledComponent("Max steps:", maxSteps)
                .addLabeledComponent("Max milliseconds:", maxMillis)
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
    protected void resetEditorFrom(@NotNull FLRunConfiguration c) {
        checkout.setText(c.getCheckout());
        node.setText(c.getNodePath());
        program.setText(c.getProgram());
        vocab.setText(c.getVocab());
        extraFiles.setText(String.join("\n", c.getExtraFiles()));
        maxSteps.setText(c.getMaxSteps() > 0 ? Integer.toString(c.getMaxSteps()) : "");
        maxMillis.setText(c.getMaxMillis() > 0 ? Integer.toString(c.getMaxMillis()) : "");
    }

    @Override
    protected void applyEditorTo(@NotNull FLRunConfiguration c) {
        c.setCheckout(checkout.getText().strip());
        c.setNodePath(node.getText().strip());
        c.setProgram(program.getText());
        c.setVocab(vocab.getText().strip());
        List<String> files = StringUtil.split(extraFiles.getText(), "\n").stream().map(String::strip).filter(s -> !s.isEmpty()).toList();
        c.setExtraFiles(files);
        c.setMaxSteps(StringUtil.parseInt(maxSteps.getText().strip(), 0));
        c.setMaxMillis(StringUtil.parseInt(maxMillis.getText().strip(), 0));
    }

    @NotNull
    @Override
    protected JComponent createEditor() {
        return panel;
    }
}
