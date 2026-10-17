import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
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
    public static String guessFileName(URL url) {
        String path = url.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            name = URLDecoder.decode(name, "UTF-8");
        } catch (UnsupportedEncodingException ignored) {
        }
        return name.isEmpty() ? "download.bin" : name;
    }
}