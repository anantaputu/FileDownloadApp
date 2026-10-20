import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.*;

public class DownloadWorker extends SwingWorker<File, Long> {

    private static final long UPDATE_INTERVAL_MS = 100;

    private final URL url;
    private final File target;
    private final BiConsumer<Long, Long> onProgress; 
    private final Runnable onUnknownSize;
    private final Consumer<File> onSuccess;
    private final Consumer<Throwable> onFailure;

    private volatile long totalSize = -1;

    public DownloadWorker(URL url, File target,
                          BiConsumer<Long, Long> onProgress,
                          Runnable onUnknownSize,
                          Consumer<File> onSuccess,
                          Consumer<Throwable> onFailure) {
        this.url = url;
        this.target = target;
        this.onProgress = onProgress;
        this.onUnknownSize = onUnknownSize;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    @Override
    protected File doInBackground() throws Exception {
        HttpURLConnection conn = HttpUtils.openConnection(url);
        File outputTarget = target;
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server mengembalikan kode HTTP " + code);
            }

            String extension = HttpUtils.extensionForContentType(conn.getContentType());
            if (extension != null && !hasExtension(outputTarget.getName())) {
                outputTarget = new File(outputTarget.getParentFile(),
                        outputTarget.getName() + "." + extension);
            }

            totalSize = conn.getContentLengthLong();
            if (totalSize <= 0) {
                totalSize = -1;
                SwingUtilities.invokeLater(onUnknownSize);
            }

            byte[] buffer = new byte[8192];
            long downloaded = 0;
            long lastUpdate = 0;
            int read;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(outputTarget)) {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    downloaded += read;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
                        publish(downloaded);
                        lastUpdate = now;
                    }
                }
                out.flush();
            }
            publish(downloaded); 
        } catch (Exception ex) {
            outputTarget.delete();
            throw ex;
        } finally {
            conn.disconnect();
        }
        return outputTarget;
    }

    @Override
    protected void process(List<Long> chunks) {
        long downloaded = chunks.get(chunks.size() - 1);
        onProgress.accept(downloaded, totalSize);
    }

    @Override
    protected void done() {
        try {
            onSuccess.accept(get());
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            onFailure.accept(cause);
        }
    }

    private static boolean hasExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1;
    }
}