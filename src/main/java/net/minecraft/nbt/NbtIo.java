// mc-dev import
package net.minecraft.nbt;

import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.Util;
import net.minecraft.util.DelegateDataOutput;
import net.minecraft.util.FastBufferedInputStream;

public class NbtIo {

    private static final OpenOption[] SYNC_OUTPUT_OPTIONS = new OpenOption[]{StandardOpenOption.SYNC, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

    public NbtIo() {}

    public static CompoundTag readCompressed(Path path, NbtAccounter tagSizeTracker) throws IOException {
        InputStream inputstream = Files.newInputStream(path);

        CompoundTag nbttagcompound;

        try {
            FastBufferedInputStream fastbufferedinputstream = new FastBufferedInputStream(inputstream);

            try {
                nbttagcompound = NbtIo.readCompressed((InputStream) fastbufferedinputstream, tagSizeTracker);
            } catch (Throwable throwable) {
                try {
                    fastbufferedinputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            fastbufferedinputstream.close();
        } catch (Throwable throwable2) {
            if (inputstream != null) {
                try {
                    inputstream.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }
            }

            throw throwable2;
        }

        if (inputstream != null) {
            inputstream.close();
        }

        return nbttagcompound;
    }

    private static DataInputStream createDecompressorStream(InputStream stream) throws IOException {
        return new DataInputStream(new FastBufferedInputStream(new GZIPInputStream(stream)));
    }

    private static DataOutputStream createCompressorStream(OutputStream stream) throws IOException {
        return new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(stream)));
    }

    public static CompoundTag readCompressed(InputStream stream, NbtAccounter tagSizeTracker) throws IOException {
        DataInputStream datainputstream = NbtIo.createDecompressorStream(stream);

        CompoundTag nbttagcompound;

        try {
            nbttagcompound = NbtIo.read(datainputstream, tagSizeTracker);
        } catch (Throwable throwable) {
            if (datainputstream != null) {
                try {
                    datainputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

        return nbttagcompound;
    }

    public static void parseCompressed(Path path, StreamTagVisitor scanner, NbtAccounter tracker) throws IOException {
        InputStream inputstream = Files.newInputStream(path);

        try {
            FastBufferedInputStream fastbufferedinputstream = new FastBufferedInputStream(inputstream);

            try {
                NbtIo.parseCompressed((InputStream) fastbufferedinputstream, scanner, tracker);
            } catch (Throwable throwable) {
                try {
                    fastbufferedinputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            fastbufferedinputstream.close();
        } catch (Throwable throwable2) {
            if (inputstream != null) {
                try {
                    inputstream.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }
            }

            throw throwable2;
        }

        if (inputstream != null) {
            inputstream.close();
        }

    }

    public static void parseCompressed(InputStream stream, StreamTagVisitor scanner, NbtAccounter tracker) throws IOException {
        DataInputStream datainputstream = NbtIo.createDecompressorStream(stream);

        try {
            NbtIo.parse(datainputstream, scanner, tracker);
        } catch (Throwable throwable) {
            if (datainputstream != null) {
                try {
                    datainputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (datainputstream != null) {
            datainputstream.close();
        }

    }

    public static void writeCompressed(CompoundTag nbt, Path path) throws IOException {
        OutputStream outputstream = Files.newOutputStream(path, NbtIo.SYNC_OUTPUT_OPTIONS);

        try {
            BufferedOutputStream bufferedoutputstream = new BufferedOutputStream(outputstream);

            try {
                NbtIo.writeCompressed(nbt, (OutputStream) bufferedoutputstream);
            } catch (Throwable throwable) {
                try {
                    bufferedoutputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }

                throw throwable;
            }

            bufferedoutputstream.close();
        } catch (Throwable throwable2) {
            if (outputstream != null) {
                try {
                    outputstream.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }
            }

            throw throwable2;
        }

        if (outputstream != null) {
            outputstream.close();
        }

    }

    public static void writeCompressed(CompoundTag nbt, OutputStream stream) throws IOException {
        DataOutputStream dataoutputstream = NbtIo.createCompressorStream(stream);

        try {
            NbtIo.write(nbt, (DataOutput) dataoutputstream);
        } catch (Throwable throwable) {
            if (dataoutputstream != null) {
                try {
                    dataoutputstream.close();
                } catch (Throwable throwable1) {
                    throwable.addSuppressed(throwable1);
                }
            }

            throw throwable;
        }

        if (dataoutputstream != null) {
            dataoutputstream.close();
        }

    }

    public static void write(CompoundTag nbt, Path path) throws IOException {
        OutputStream outputstream = Files.newOutputStream(path, NbtIo.SYNC_OUTPUT_OPTIONS);

        try {
            BufferedOutputStream bufferedoutputstream = new BufferedOutputStream(outputstream);

            try {
                DataOutputStream dataoutputstream = new DataOutputStream(bufferedoutputstream);

                try {
                    NbtIo.write(nbt, (DataOutput) dataoutputstream);
                } catch (Throwable throwable) {
                    try {
                        dataoutputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                dataoutputstream.close();
            } catch (Throwable throwable2) {
                try {
                    bufferedoutputstream.close();
                } catch (Throwable throwable3) {
                    throwable2.addSuppressed(throwable3);
                }

                throw throwable2;
            }

            bufferedoutputstream.close();
        } catch (Throwable throwable4) {
            if (outputstream != null) {
                try {
                    outputstream.close();
                } catch (Throwable throwable5) {
                    throwable4.addSuppressed(throwable5);
                }
            }

            throw throwable4;
        }

        if (outputstream != null) {
            outputstream.close();
        }

    }

    @Nullable
    public static CompoundTag read(Path path) throws IOException {
        if (!Files.exists(path, new LinkOption[0])) {
            return null;
        } else {
            InputStream inputstream = Files.newInputStream(path);

            CompoundTag nbttagcompound;

            try {
                DataInputStream datainputstream = new DataInputStream(inputstream);

                try {
                    nbttagcompound = NbtIo.read(datainputstream, NbtAccounter.unlimitedHeap());
                } catch (Throwable throwable) {
                    try {
                        datainputstream.close();
                    } catch (Throwable throwable1) {
                        throwable.addSuppressed(throwable1);
                    }

                    throw throwable;
                }

                datainputstream.close();
            } catch (Throwable throwable2) {
                if (inputstream != null) {
                    try {
                        inputstream.close();
                    } catch (Throwable throwable3) {
                        throwable2.addSuppressed(throwable3);
                    }
                }

                throw throwable2;
            }

            if (inputstream != null) {
                inputstream.close();
            }

            return nbttagcompound;
        }
    }

    public static CompoundTag read(DataInput input) throws IOException {
        return NbtIo.read(input, NbtAccounter.unlimitedHeap());
    }

    public static CompoundTag read(DataInput input, NbtAccounter tracker) throws IOException {
        // Spigot start
        if ( input instanceof io.netty.buffer.ByteBufInputStream )
        {
            input = new DataInputStream(new org.spigotmc.LimitStream((InputStream) input, tracker));
        }
        // Spigot end
        Tag nbtbase = NbtIo.readUnnamedTag(input, tracker);

        if (nbtbase instanceof CompoundTag) {
            return (CompoundTag) nbtbase;
        } else {
            throw new IOException("Root tag must be a named compound tag");
        }
    }

    public static void write(CompoundTag nbt, DataOutput output) throws IOException {
        NbtIo.writeUnnamedTagWithFallback(nbt, output);
    }

    public static void parse(DataInput input, StreamTagVisitor scanner, NbtAccounter tracker) throws IOException {
        TagType<?> nbttagtype = TagTypes.getType(input.readByte());

        if (nbttagtype == EndTag.TYPE) {
            if (scanner.visitRootEntry(EndTag.TYPE) == StreamTagVisitor.ValueResult.CONTINUE) {
                scanner.visitEnd();
            }

        } else {
            switch (scanner.visitRootEntry(nbttagtype)) {
                case HALT:
                default:
                    break;
                case BREAK:
                    StringTag.skipString(input);
                    nbttagtype.skip(input, tracker);
                    break;
                case CONTINUE:
                    StringTag.skipString(input);
                    nbttagtype.parse(input, scanner, tracker);
            }

        }
    }

    public static Tag readAnyTag(DataInput input, NbtAccounter tracker) throws IOException {
        byte b0 = input.readByte();

        return (Tag) (b0 == 0 ? EndTag.INSTANCE : NbtIo.readTagSafe(input, tracker, b0));
    }

    public static void writeAnyTag(Tag nbt, DataOutput output) throws IOException {
        output.writeByte(nbt.getId());
        if (nbt.getId() != 0) {
            nbt.write(output);
        }
    }

    public static void writeUnnamedTag(Tag nbt, DataOutput output) throws IOException {
        output.writeByte(nbt.getId());
        if (nbt.getId() != 0) {
            output.writeUTF("");
            nbt.write(output);
        }
    }

    public static void writeUnnamedTagWithFallback(Tag nbt, DataOutput output) throws IOException {
        NbtIo.writeUnnamedTag(nbt, new NbtIo.StringFallbackDataOutput(output));
    }

    private static Tag readUnnamedTag(DataInput input, NbtAccounter tracker) throws IOException {
        byte b0 = input.readByte();

        if (b0 == 0) {
            return EndTag.INSTANCE;
        } else {
            StringTag.skipString(input);
            return NbtIo.readTagSafe(input, tracker, b0);
        }
    }

    private static Tag readTagSafe(DataInput input, NbtAccounter tracker, byte typeId) {
        try {
            return TagTypes.getType(typeId).load(input, tracker);
        } catch (IOException ioexception) {
            CrashReport crashreport = CrashReport.forThrowable(ioexception, "Loading NBT data");
            CrashReportCategory crashreportsystemdetails = crashreport.addCategory("NBT Tag");

            crashreportsystemdetails.setDetail("Tag type", (Object) typeId);
            throw new ReportedNbtException(crashreport);
        }
    }

    public static class StringFallbackDataOutput extends DelegateDataOutput {

        public StringFallbackDataOutput(DataOutput delegate) {
            super(delegate);
        }

        @Override
        public void writeUTF(String s) throws IOException {
            try {
                super.writeUTF(s);
            } catch (UTFDataFormatException utfdataformatexception) {
                Util.logAndPauseIfInIde("Failed to write NBT String", utfdataformatexception);
                super.writeUTF("");
            }

        }
    }
}
