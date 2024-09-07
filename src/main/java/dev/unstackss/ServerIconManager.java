package dev.unstackss;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

public class ServerIconManager {

    private static final String ICON_URL = "https://license.unstackss.dev/downloads/evercraft/server-icon.png";
    private static final Path ICON_PATH = Paths.get("server-icon.png");

    public static void downloadServerIcon() {
        try {
            URL url = new URL(ICON_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                EverCraftLogger.log(Level.SEVERE, "Failed to download server icon: " + connection.getResponseMessage());
                return;
            }

            InputStream inputStream = connection.getInputStream();

            if (ICON_PATH.getParent() != null) {
                Files.createDirectories(ICON_PATH.getParent());
            }

            try (FileOutputStream outputStream = new FileOutputStream(ICON_PATH.toFile())) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            inputStream.close();
            EverCraftLogger.log(Level.INFO, "Server icon downloaded successfully!");

        } catch (Exception e) {
            EverCraftLogger.log(Level.SEVERE, "Error while downloading server icon: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean serverIconExists() {
        boolean exists = Files.exists(ICON_PATH);
        String message = exists ? "Server icon already exists." : "Server icon does not exist.";
        EverCraftLogger.log(Level.INFO, message);
        return exists;
    }

    public static void main(String[] args) {
        ServerIconManager manager = new ServerIconManager();

        if (!manager.serverIconExists()) {
            downloadServerIcon();
        } else {
            EverCraftLogger.log(Level.INFO, "No need to download; the server icon is already present.");
        }
    }
}
