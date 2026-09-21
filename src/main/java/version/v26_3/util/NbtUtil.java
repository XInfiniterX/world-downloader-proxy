package version.v26_3.util;

import org.apache.commons.io.IOUtils;
import se.llbit.nbt.NamedTag;
import se.llbit.nbt.Tag;
import version.v26_3.proxy.CompressionManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

public class NbtUtil {

    public static Tag read(File f) throws IOException {
        return read(new FileInputStream(f));
    }

    public static Tag read(InputStream input) throws IOException {
        byte[] fileContent = IOUtils.toByteArray(input);
        return NamedTag.read(
                new DataInputStream(new ByteArrayInputStream(CompressionManager.gzipDecompress(fileContent)))
        );
    }

   public static void write(Tag nbt, Path destination) throws IOException {
       // Stream directly to a GZIP-compressed file instead of buffering the entire NBT
       // in memory. For large schematics (200M+ blocks), the previous approach allocated
       // two ByteArrayOutputStreams (~800MB+ combined) and caused OutOfMemoryError.
       try (OutputStream fos = Files.newOutputStream(destination);
            GZIPOutputStream gzip = new GZIPOutputStream(fos);
            DataOutputStream out = new DataOutputStream(gzip)) {
           nbt.write(out);
       }
   }
}
