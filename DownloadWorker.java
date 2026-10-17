import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DownloadWorker extends SwingWorker<Void, Long> {

    private static final long UPDATE_INTERVAL_MS = 100;

    private final URL url;
    private final File target;
    private final BiConsumer<Long, Long> onProgress; 
    private final Runnable onUnknownSize;
    private final Runnable onSuccess;
    private final Consumer<Throwable> onFailure;

    private volatile long totalSize = -1;

    public DownloadWorker(URL url, File target,
                          BiConsumer<Long, Long> onProgress,
                          Runnable onUnknownSize,
                          Runnable onSuccess,
                          Consumer<Throwable> onFailure) {
        this.url = url;
        this.target = target;
        this.onProgress = onProgress;
        this.onUnknownSize = onUnknownSize;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    @Override
    protected Void doInBackground() throws Exception {
        HttpURLConnection conn = HttpUtils.openConnection(url);
        try {
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server mengembalikan kode HTTP " + code);
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
                 FileOutputStream out = new FileOutputStream(target)) {
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
            target.delete(); 
            throw ex;
        } finally {
            conn.disconnect();
        }
        return null;
    }

    @Override
    protected void process(List<Long> chunks) {
        long downloaded = chunks.get(chunks.size() - 1);
        onProgress.accept(downloaded, totalSize);
    }

    @Override
    protected void done() {
        try {
            get(); 
        } catch (Exception ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            onFailure.accept(cause);
            return;
        }
        onSuccess.run();
    }
}