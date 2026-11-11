package com.forge.fl.index;

import com.forge.fl.FLFileType;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * File-based index over FL files. A file based index (not a stub index) because the facts are few, flat and
 * come from the token stream alone; there is no need for a stub tree, and a stub index would force the PSI
 * elements to carry stubs for names such as {@code +} that no word index can hold.
 *
 * <p>Key families (a one-letter prefix, then the content; a vocabulary and a word name never contain ':'):
 * <ul>
 *   <li>{@code D<vocab>:<name>} declarations of {@code name} into {@code vocab}: words, constants,
 *       variables (both {@code n} and {@code n!}), primitives and aliases, private ones included;</li>
 *   <li>{@code U<name>} uses of a word by bare name (any {@code vocab:} prefix removed; a use of
 *       {@code counter!} is keyed {@code counter!});</li>
 *   <li>{@code V<vocab>} the file declares into or switches to the vocabulary.</li>
 * </ul>
 * Bump {@link #VERSION} whenever the keys, the values or the scanning rules change.
 */
public final class FLIndex extends FileBasedIndexExtension<String, List<FLIndexEntry>> {
    public static final ID<String, List<FLIndexEntry>> NAME = ID.create("com.forge.fl.index");
    public static final int VERSION = 1;

    public static String declKey(String vocab, String name) {
        return "D" + vocab + ':' + name;
    }

    public static String declVocabPrefix(String vocab) {
        return "D" + vocab + ':';
    }

    public static String usageKey(String bareName) {
        return "U" + bareName;
    }

    public static String vocabKey(String vocab) {
        return "V" + vocab;
    }

    @NotNull
    @Override
    public ID<String, List<FLIndexEntry>> getName() {
        return NAME;
    }

    @NotNull
    @Override
    public DataIndexer<String, List<FLIndexEntry>, FileContent> getIndexer() {
        return inputData -> FLLightScan.scan(inputData.getContentAsText());
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<List<FLIndexEntry>> getValueExternalizer() {
        return new DataExternalizer<>() {
            @Override
            public void save(@NotNull DataOutput out, List<FLIndexEntry> value) throws IOException {
                DataInputOutputUtil.writeINT(out, value.size());
                for (FLIndexEntry e : value) {
                    out.writeByte(e.kind());
                    DataInputOutputUtil.writeINT(out, e.offset());
                    if (e.isDeclaration()) {
                        out.writeBoolean(e.detail() != null);
                        if (e.detail() != null) IOUtil.writeUTF(out, e.detail());
                    }
                }
            }

            @Override
            public List<FLIndexEntry> read(@NotNull DataInput in) throws IOException {
                int n = DataInputOutputUtil.readINT(in);
                List<FLIndexEntry> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    byte kind = in.readByte();
                    int offset = DataInputOutputUtil.readINT(in);
                    String detail = null;
                    if (FLIndexEntry.Kind.isDeclaration(kind) && in.readBoolean()) detail = IOUtil.readUTF(in);
                    list.add(new FLIndexEntry(kind, offset, detail));
                }
                return list;
            }
        };
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(FLFileType.INSTANCE);
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
