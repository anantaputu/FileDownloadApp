import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Locale;

public final class HttpUtils {

    private HttpUtils() {
    }
    public static HttpURLConnection openConnection(URL start) throws IOException {
        URL current = start;
        for (int i = 0; i < 5; i++) {
            HttpURLConnection conn = (HttpURLConnection) current.openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Java DownloadFile)");
            conn.setRequestProperty("Accept-Encoding", "identity");

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM
                    || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) {
                    throw new IOException("Redirect tanpa header Location");
                }
                current = new URL(current, location);
                continue;
            }
            return conn;
        }
        throw new IOException("Terlalu banyak redirect");
    }

    public static String getFileName(URL url) throws IOException {
        HttpURLConnection conn = openConnection(url);
        try {
            String disposition = conn.getHeaderField("Content-Disposition");
            if (disposition != null) {
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    String name = disposition.substring(index + 9);
                    if (name.startsWith("\"") && name.endsWith("\"")) {
                        name = name.substring(1, name.length() - 1);
                    } else if (name.startsWith("'") && name.endsWith("'")) {
                        name = name.substring(1, name.length() - 1);
                    }
                    return name;
                }
            }

            String path = conn.getURL().getPath(); 
            String name = path.substring(path.lastIndexOf('/') + 1);
            try {
                name = URLDecoder.decode(name, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }
            if (name.isEmpty()) {
                name = "download";
            }

            if (name.lastIndexOf('.') == -1) {
                String contentType = conn.getContentType();
                String ext = extensionForContentType(contentType);
                if (ext != null) {
                    name = name + "." + ext;
                }
            }

            return name;

        } finally {
            conn.disconnect();
        }
    }

    public static String extensionForContentType(String contentType) {
        if (contentType == null) {
            return null;
        }

        String mediaType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        switch (mediaType) {
            case "video/mp4": return "mp4";
            case "video/webm": return "webm";
            case "video/quicktime": return "mov";
            case "video/x-msvideo": return "avi";
            case "video/x-matroska": return "mkv";
            case "video/mpeg": return "mpeg";
            case "video/3gpp": return "3gp";
            case "audio/mpeg": return "mp3";
            case "audio/mp4": return "m4a";
            case "audio/wav":
            case "audio/x-wav": return "wav";
            case "audio/ogg": return "ogg";
            case "audio/flac": return "flac";
            case "audio/aac": return "aac";
            case "image/jpeg": return "jpg";
            case "image/png": return "png";
            case "image/gif": return "gif";
            case "image/webp": return "webp";
            case "image/bmp": return "bmp";
            case "image/svg+xml": return "svg";
            case "application/pdf": return "pdf";
            case "application/zip": return "zip";
            case "application/x-7z-compressed": return "7z";
            case "application/x-rar-compressed": return "rar";
            case "application/gzip": return "gz";
            case "application/json": return "json";
            case "application/xml":
            case "text/xml": return "xml";
            case "text/plain": return "txt";
            case "application/msword": return "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return "pptx";
            default: return null;
        }
    }
}