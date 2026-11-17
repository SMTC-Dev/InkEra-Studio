package com.inkera;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.inkera.core.config.ConfigService;
import com.inkera.services.LocaleManager;
import com.inkera.util.AppLogger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {

    private static Stage splashStage;
    private static Stage currentStage;
    private static boolean useCustomTitleBar;

    // Aynı .inkpage dosyasının iki kez açılmasını önleyen aktif pencere tablosu
    private static final Map<Path, Stage> activeWorkspaces = new HashMap<>();

    @Override
    public void start(Stage stage) throws IOException {
        ConfigService configService = ConfigService.getInstance();

        AppLogger.configure(
            configService.isLoggingEnabled(),
            configService.getLogLevel()
        );

        AppLogger.info("====================================================");
        AppLogger.info("InkEra Studio başlatılıyor...");
        AppLogger.info("Config Dosyası : " + configService.getConfigFile());
        AppLogger.info("User-Data Yolu : " + configService.getUserDataDir());
        AppLogger.info("Projects Yolu  : " + configService.getProjectsDataDir());
        AppLogger.info("Images Yolu    : " + configService.getImagesDataDir());
        AppLogger.info("Log Seviyesi   : " + configService.getLogLevel());
        AppLogger.info("====================================================");

        useCustomTitleBar = configService.isCustomTitleBar();

        splashStage = stage;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/inkera/fxml/splash.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);

        stage.show();
        forceCenterOnScreen(stage);
    }

    public static void showHome() {
        Platform.runLater(() -> {
            try {
                if (splashStage != null) splashStage.close();
                if (currentStage != null) currentStage.close();

                Stage homeStage = new Stage();
                currentStage = homeStage;

                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/inkera/fxml/home.fxml"));
                loader.setResources(LocaleManager.getInstance().getBundle());
                Parent root = loader.load();

                if (useCustomTitleBar) {
                    homeStage.initStyle(StageStyle.UNDECORATED);
                } else {
                    homeStage.initStyle(StageStyle.DECORATED);
                }

                Scene scene = new Scene(root, 1150, 800);
                homeStage.setTitle("InkEra Studio - Home");
                homeStage.setScene(scene);

                homeStage.setMinWidth(1150);
                homeStage.setMaxWidth(1150);
                homeStage.setMinHeight(800);
                homeStage.setMaxHeight(800);

                homeStage.show();
                forceCenterOnScreen(homeStage);

                AppLogger.info("Ana pencere (Home) görüntülendi [1150x800].");

            } catch (IOException e) {
                AppLogger.error("Ana pencere (Home) yüklenirken hata oluştu.", e);
            }
        });
    }

    public static void reloadUI() {
        showHome();
    }

    public static void showWorkspace(String projectName, int width, int height) {
        showWorkspace(projectName, width, height, null, null);
    }

    public static void showWorkspace(String projectName, int defaultWidth, int defaultHeight, Path pageFilePath) {
        showWorkspace(projectName, defaultWidth, defaultHeight, pageFilePath, null);
    }

    public static void showWorkspace(
        String projectName,
        int defaultWidth,
        int defaultHeight,
        Path pageFilePath,
        Runnable onClosedCallback
    ) {
        Platform.runLater(() -> {
            try {
                Path normalizedPath = (pageFilePath != null)
                    ? pageFilePath.toAbsolutePath().normalize()
                    : null;

                if (normalizedPath != null && activeWorkspaces.containsKey(normalizedPath)) {
                    Stage existingStage = activeWorkspaces.get(normalizedPath);
                    if (existingStage != null && existingStage.isShowing()) {
                        AppLogger.info(
                            "Workspace zaten açık, mevcut pencere öne getiriliyor: " + normalizedPath
                        );
                        if (existingStage.isIconified()) {
                            existingStage.setIconified(false);
                        }
                        existingStage.toFront();
                        existingStage.requestFocus();
                        return;
                    } else {
                        activeWorkspaces.remove(normalizedPath);
                    }
                }

                AppLogger.info(
                    "Workspace açılıyor -> Başlık: '" + projectName
                    + "', Varsayılan Boyut: " + defaultWidth + "x" + defaultHeight
                    + ", Dosya Yolu: " + (normalizedPath != null ? normalizedPath : "[Bağımsız / Geçici]")
                );

                FXMLLoader loader = new FXMLLoader(App.class.getResource("/com/inkera/fxml/workspace/workspace_main.fxml"));
                Parent workspaceRoot = loader.load();

                com.inkera.ui.workspace.WorkspaceController controller = loader.getController();

                Stage workspaceStage = new Stage();
                String baseTitle = "InkEra Studio - " + projectName;
                workspaceStage.setTitle(baseTitle);
                controller.bindStageTitle(workspaceStage, baseTitle);

                if (normalizedPath != null) {
                    activeWorkspaces.put(normalizedPath, workspaceStage);
                    controller.openOrCreatePage(normalizedPath, defaultWidth, defaultHeight);
                } else {
                    controller.setCanvasSize(defaultWidth, defaultHeight);
                }

                workspaceStage.setOnCloseRequest(event -> {
                    if (controller.hasUnsavedChanges() && normalizedPath != null) {
                        AppLogger.info("Kapanışta kaydedilmemiş değişiklik algılandı, otomatik kaydediliyor: " + normalizedPath);
                        controller.saveCurrentPage();
                    }
                });

                workspaceStage.setOnHidden(event -> {
                    AppLogger.info("Workspace penceresi kapatıldı -> '" + projectName + "'. Kaynaklar temizleniyor...");
                    if (normalizedPath != null) {
                        activeWorkspaces.remove(normalizedPath);
                    }
                    controller.dispose();
                    if (onClosedCallback != null) {
                        onClosedCallback.run();
                    }
                });

                // AwesomeWM / KWin üzerinde alt diyalog (FileChooser vb.) açıldığında
                // pencerenin 1280x720'ye küçülmesini önlemek için doğrudan ekranın tam çalışma boyutunda başlatıyoruz
                Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
                double fullW = visualBounds.getWidth();
                double fullH = visualBounds.getHeight();

                Scene scene = new Scene(workspaceRoot, fullW, fullH);
                scene.getStylesheets().add(App.class.getResource("/com/inkera/styles/style.css").toExternalForm());

                workspaceStage.setScene(scene);
                workspaceStage.setX(visualBounds.getMinX());
                workspaceStage.setY(visualBounds.getMinY());
                workspaceStage.setWidth(fullW);
                workspaceStage.setHeight(fullH);
                workspaceStage.setMinWidth(800);
                workspaceStage.setMinHeight(600);
                workspaceStage.setResizable(true);
                workspaceStage.setMaximized(true);

                // Eğer bir alt diyalog (FileChooser) kapandığında WM unmaximize ederse boyutu tam ekranda koru
                workspaceStage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
                    if (!isNowMax && workspaceStage.isShowing()) {
                        Platform.runLater(() -> {
                            workspaceStage.setX(visualBounds.getMinX());
                            workspaceStage.setY(visualBounds.getMinY());
                            workspaceStage.setWidth(fullW);
                            workspaceStage.setHeight(fullH);
                        });
                    }
                });

                workspaceStage.show();

            } catch (Exception e) {
                AppLogger.error("Workspace başlatılırken kritik hata oluştu.", e);
            }
        });
    }

    private static void forceCenterOnScreen(Stage stage) {
        Platform.runLater(() -> {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            stage.setX((bounds.getWidth() - stage.getWidth()) / 2);
            stage.setY((bounds.getHeight() - stage.getHeight()) / 2);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
