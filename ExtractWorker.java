import javax.swing.*;
import java.io.File;
import java.util.function.Consumer;

public class ExtractWorker extends SwingWorker<File, Void> {

    private final File zipFile;
    private final Consumer<File> onSuccess;
    private final Consumer<Throwable> onFailure;

    public ExtractWorker(File zipFile, Consumer<File> onSuccess, Consumer<Throwable> onFailure) {
        this.zipFile = zipFile;
        this.onSuccess = onSuccess;
        this.onFailure = onFailure;
    }

    @Override
    protected File doInBackground() throws Exception {
        File destDir = ZipUtils.defaultDestination(zipFile);
        ZipUtils.extract(zipFile, destDir);
        return destDir;
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
}