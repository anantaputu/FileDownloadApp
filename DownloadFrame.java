import java.awt.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import javax.swing.*;

public class DownloadFrame extends JFrame {

    private static final Locale ID = Locale.forLanguageTag("id-ID");

    private final JTextField urlField = new JTextField(40);
    private final JButton downloadButton = new JButton("Download");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Masukkan URL file, lalu klik Download.");

    public DownloadFrame() {
        super("Download File");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildLayout();
        downloadButton.addActionListener(e -> startDownload());
        urlField.addActionListener(e -> startDownload());
    }

    private void buildLayout() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(new JLabel("URL:"), c);

        c.gridx = 1; c.weightx = 1;
        panel.add(urlField, c);

        c.gridx = 2; c.weightx = 0;
        panel.add(downloadButton, c);

        progressBar.setStringPainted(true);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 3; c.weightx = 1;
        panel.add(progressBar, c);

        c.gridy = 2;
        panel.add(statusLabel, c);

        setContentPane(panel);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    private String directLinkConverter(String urlText) {
        if (urlText.contains("pixeldrain.com/u/")) {
            return urlText.replace("/u/", "/api/file/");
        }
        
        if (urlText.contains("dropbox.com/") && urlText.endsWith("dl=0")) {
            return urlText.replace("dl=0", "dl=1");
        }
        
        if (urlText.contains("drive.google.com/file/d/")) {
            try {
                String[] parts = urlText.split("/");
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].equals("d") && i + 1 < parts.length) {
                        String fileId = parts[i + 1];
                        return "https://drive.google.com/uc?export=download&id=" + fileId;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return urlText;
    }

    private void startDownload() {
        String urlText = urlField.getText().trim();
        if (urlText.isEmpty()) {
            showError("URL tidak boleh kosong.");
            return;
        }

        urlText = directLinkConverter(urlText);
        urlField.setText(urlText);

        URL url;
        try {
            url = new URL(urlText);
            String protocol = url.getProtocol();
            if (!protocol.equals("http") && !protocol.equals("https")) {
                showError("Hanya URL http:// atau https:// yang didukung.");
                return;
            }
        } catch (MalformedURLException ex) {
            showError("Format URL tidak valid.");
            return;
        }

        setControlsEnabled(false);
        progressBar.setIndeterminate(true);
        statusLabel.setText("Mencari informasi file...");

        String fileName;
        try {
            fileName = HttpUtils.getFileName(url);
        } catch (Exception ex) {
            setControlsEnabled(true);
            progressBar.setIndeterminate(false);
            statusLabel.setText("Gagal mendapatkan info file.");
            showError("Gagal menghubungi server:\n" + ex.getMessage());
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Simpan file sebagai");
        chooser.setSelectedFile(new File(fileName));
        
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            setControlsEnabled(true);
            progressBar.setIndeterminate(false);
            statusLabel.setText("Masukkan URL file, lalu klik Download.");
            return;
        }
        
        File target = chooser.getSelectedFile();
        
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        statusLabel.setText("Menghubungkan ke server...");
        
        new DownloadWorker(url, target,
                this::onDownloadProgress,
                this::onDownloadUnknownSize,
                this::onDownloadSuccess,
                this::onDownloadFailure
        ).execute();
    }

    private void onDownloadProgress(long downloaded, long total) {
        if (total > 0) {
            int percent = (int) Math.min(100, downloaded * 100 / total);
            progressBar.setValue(percent);
            statusLabel.setText("Mengunduh... " + formatSize(downloaded)
                    + " / " + formatSize(total) + " (" + percent + "%)");
        } else {
            statusLabel.setText("Mengunduh... " + formatSize(downloaded));
        }
    }

    private void onDownloadUnknownSize() {
        progressBar.setIndeterminate(true);
        statusLabel.setText("Mengunduh... (ukuran file tidak diketahui)");
    }

    private void onDownloadFailure(Throwable cause) {
        progressBar.setIndeterminate(false);
        statusLabel.setText("Download gagal.");
        setControlsEnabled(true);
        showError("Download gagal: " + cause.getMessage());
    }

    private void onDownloadSuccess(File target) {
        progressBar.setIndeterminate(false);
        progressBar.setValue(100);
        statusLabel.setText("Download selesai.");
        JOptionPane.showMessageDialog(this,
                "Download telah selesai!\nDisimpan di:\n" + target.getAbsolutePath(),
                "Download Selesai", JOptionPane.INFORMATION_MESSAGE);

        if (ZipUtils.isZipFile(target)) {
            int choice = JOptionPane.showOptionDialog(this,
                    "File yang didownload adalah file ZIP.\nApakah Anda ingin mengekstraknya?",
                    "File ZIP Terdeteksi",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    new Object[]{"Ekstrak file", "Tidak"},
                    "Ekstrak file");
            
            if (choice == 0) {
                startExtract(target);
            }
        }
        setControlsEnabled(true);
    }

    private void startExtract(File zipFile) {
        progressBar.setIndeterminate(true);
        statusLabel.setText("Mengekstrak file...");

        new ExtractWorker(zipFile,
                destDir -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    statusLabel.setText("Ekstraksi selesai.");
                    setControlsEnabled(true);
                    JOptionPane.showMessageDialog(this,
                            "File berhasil diekstrak ke:\n" + destDir.getAbsolutePath(),
                            "Ekstraksi Selesai", JOptionPane.INFORMATION_MESSAGE);
                },
                cause -> {
                    progressBar.setIndeterminate(false);
                    statusLabel.setText("Ekstraksi gagal.");
                    setControlsEnabled(true);
                    showError("Ekstraksi gagal: " + cause.getMessage());
                }
        ).execute();
    }

    private void setControlsEnabled(boolean enabled) {
        downloadButton.setEnabled(enabled);
        urlField.setEnabled(enabled);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(ID, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(ID, "%.1f MB", mb);
        }
        return String.format(ID, "%.2f GB", mb / 1024.0);
    }
}