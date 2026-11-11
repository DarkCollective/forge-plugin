package com.forge.fl.builtins;

import com.forge.fl.FLIcons;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Exposes the materialised built-in declaration files as a library, so they are indexed and navigable. */
public class FLBuiltinsLibraryProvider extends AdditionalLibraryRootsProvider {
    @NotNull
    @Override
    public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        VirtualFile root = FLBuiltins.getRoot();
        return root == null ? Collections.emptyList() : List.of(new Library(root));
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getRootsToWatch(@NotNull Project project) {
        return Collections.emptyList();
    }

    static final class Library extends SyntheticLibrary implements ItemPresentation {
        private final VirtualFile root;

        Library(VirtualFile root) {
            this.root = root;
        }

        @NotNull
        @Override
        public Collection<VirtualFile> getSourceRoots() {
            return List.of(root);
        }

        @NotNull
        @Override
        public String getPresentableText() {
            return "FL built-in words";
        }

        @Nullable
        @Override
        public Icon getIcon(boolean unused) {
            return FLIcons.FILE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Library l && l.root.equals(root);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root);
        }
    }
}
