import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ZipUtils {
    private ZipUtils() {}

    public static boolean isZipFile(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] sig = new byte[4];
            if (in.read(sig) != 4) {
                return false;
            }
            return sig[0] == 0x50 && sig[1] == 0x4B && ((sig[2] == 0x03 && sig[3] == 0x04) || (sig[2] == 0x05 && sig[3] == 0x06));
        } catch (IOException e) {
            return false;
        }
    }

    public static File defaultDestination(File zipFile) {
        String name = zipFile.getName();
        if (name.toLowerCase().endsWith(".zip")) {
            name = name.substring(0, name.length() - 4);
        } else {
            name = name + "_extracted";
        }
        return new File(zipFile.getParentFile(), name);
    }

    public static void extract(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Gagal membuat folder tujuan: " + destDir);
        }
        String destCanonical = destDir.getCanonicalPath();

        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)),
                StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());

                // Proteksi zip slip
                String outCanonical = outFile.getCanonicalPath();
                if (!outCanonical.equals(destCanonical)
                        && !outCanonical.startsWith(destCanonical + File.separator)) {
                    throw new IOException("Entry ZIP tidak valid (di luar folder tujuan): "
                            + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}