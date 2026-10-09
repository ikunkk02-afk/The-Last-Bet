// SPDX-License-Identifier: MIT
package com.shouyun.lastbet.bank;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Checked, read-back verified writes using vanilla's compressed NBT format. */
public final class BankPersistence {
    private BankPersistence() {}

    public static CompoundTag read(Path file) throws IOException {
        return NbtIo.readCompressed(file, NbtAccounter.create(64L * 1024 * 1024));
    }

    public static void write(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            NbtIo.writeCompressed(tag, temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (!read(temporary).equals(tag)) throw new IOException("NBT verification failed: " + temporary);
            if (Files.exists(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + "_old"), StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // The previous file is retained as .dat_old on filesystems without atomic rename.
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!read(file).equals(tag)) throw new IOException("Saved NBT verification failed: " + file);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
