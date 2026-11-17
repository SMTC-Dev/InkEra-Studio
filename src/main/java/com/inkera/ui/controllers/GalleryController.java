package com.inkera.ui.controllers;

import com.inkera.App;
import com.inkera.core.config.ConfigService;
import com.inkera.util.AppLogger;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GalleryController {

    private static final int DEFAULT_IMAGE_WIDTH = 1920;
    private static final int DEFAULT_IMAGE_HEIGHT = 1080;

    @FXML private FlowPane projectsFlowPane;
    @FXML private VBox newProjectCard;

    @FXML
    public void initialize() {
        if (newProjectCard != null) {
            newProjectCard.setOnMouseClicked(this::handleNewProjectClick);
        }
        loadProjectsToUI();
    }

    private void handleNewProjectClick(MouseEvent event) {
        VBox card = HomeController.createStyledPanelCard(480);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLbl = new Label("Yeni Çizim Projesi Oluştur");
        titleLbl.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> HomeController.hideOverlayPanel());
        header.getChildren().addAll(titleLbl, spacer, closeBtn);

        TextField nameField = new TextField("Adsız_Çizim");
        nameField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField authorField = new TextField("Samet Cırık");
        authorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField widthField = new TextField("1920");
        widthField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");
        TextField heightField = new TextField("1080");
        heightField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        HBox presetsBox = new HBox(6);
        Button fhdBtn = new Button("FHD (1920x1080)");
        fhdBtn.setStyle("-fx-font-size: 11px;");
        fhdBtn.setOnAction(e -> { widthField.setText("1920"); heightField.setText("1080"); });

        Button a4Btn = new Button("A4 (2480x3508)");
        a4Btn.setStyle("-fx-font-size: 11px;");
        a4Btn.setOnAction(e -> { widthField.setText("2480"); heightField.setText("3508"); });

        Button sqBtn = new Button("Kare (1080x1080)");
        sqBtn.setStyle("-fx-font-size: 11px;");
        sqBtn.setOnAction(e -> { widthField.setText("1080"); heightField.setText("1080"); });

        Button uhdBtn = new Button("4K (3840x2160)");
        uhdBtn.setStyle("-fx-font-size: 11px;");
        uhdBtn.setOnAction(e -> { widthField.setText("3840"); heightField.setText("2160"); });

        presetsBox.getChildren().addAll(fhdBtn, a4Btn, sqBtn, uhdBtn);

        HBox dimensionsBox = new HBox(12,
            new VBox(4, new Label("Genişlik (px):"), widthField),
            new VBox(4, new Label("Yükseklik (px):"), heightField)
        );

        String defaultParentDir = Paths.get(System.getProperty("user.home"), "Masaüstü").toString();
        TextField locationField = new TextField(defaultParentDir);
        locationField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #ccc; -fx-border-color: #444; -fx-padding: 8;");
        HBox.setHgrow(locationField, Priority.ALWAYS);

        Button browseBtn = new Button("📁 Seç");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Proje Konumu Seç");
            File initDir = new File(locationField.getText());
            if (initDir.exists() && initDir.isDirectory()) {
                chooser.setInitialDirectory(initDir);
            }
            File selected = chooser.showDialog(projectsFlowPane.getScene().getWindow());
            if (selected != null) {
                locationField.setText(selected.getAbsolutePath());
            }
        });

        HBox locationBox = new HBox(8, locationField, browseBtn);
        locationBox.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button createBtn = new Button("Oluştur ve Kanvası Aç");
        createBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        createBtn.setOnAction(e -> {
            String pName = nameField.getText().trim();
            String aName = authorField.getText().trim().isEmpty() ? "Bilinmeyen Yazar" : authorField.getText().trim();
            if (pName.isEmpty()) return;

            int w = DEFAULT_IMAGE_WIDTH;
            int h = DEFAULT_IMAGE_HEIGHT;
            try {
                w = Math.max(64, Integer.parseInt(widthField.getText().trim()));
                h = Math.max(64, Integer.parseInt(heightField.getText().trim()));
            } catch (NumberFormatException ignored) {
            }

            File parentDir = new File(locationField.getText().trim());
            HomeController.hideOverlayPanel();
            createProjectScaffolding(pName, aName, w, h, parentDir, true);
        });

        actions.getChildren().addAll(cancelBtn, createBtn);

        card.getChildren().addAll(
            header,
            new Label("Çizim Adı:"), nameField,
            new Label("Yazar:"), authorField,
            new Label("Kanvas Boyutu Şablonları:"), presetsBox,
            dimensionsBox,
            new Label("Kayıt Klasörü:"), locationBox,
            actions
        );

        HomeController.showOverlayPanel(card);
    }

    private void createProjectScaffolding(
        String projectName,
        String authorName,
        int width,
        int height,
        File parentDir,
        boolean openImmediately
    ) {
        try {
            File projectDir = new File(parentDir, projectName);
            File inkeraDir = new File(projectDir, ".inkera");

            if (!projectDir.exists()) projectDir.mkdirs();
            if (!inkeraDir.exists()) inkeraDir.mkdirs();

            File userDataImagesDir = ConfigService.getInstance().getImagesDataDir().toFile();

            String projectId = UUID.randomUUID().toString();
            long currentTime = System.currentTimeMillis();
            String resolutionStr = width + "x" + height;

            File registryJson = new File(userDataImagesDir, projectName + ".json");
            writeImageRegistryJson(registryJson, projectId, projectName, authorName, projectDir.getAbsolutePath(), resolutionStr, currentTime);

            File localMetaJson = new File(inkeraDir, "meta.json");
            try (FileWriter writer = new FileWriter(localMetaJson)) {
                writer.write("{\n");
                writer.write("  \"id\": \"" + projectId + "\",\n");
                writer.write("  \"title\": \"" + projectName + "\",\n");
                writer.write("  \"author\": \"" + authorName + "\",\n");
                writer.write("  \"type\": \"IMAGE\",\n");
                writer.write("  \"resolution\": \"" + resolutionStr + "\"\n");
                writer.write("}");
            }

            AppLogger.info("Tekli çizim projesi oluşturuldu: " + projectDir.getAbsolutePath() + " [" + resolutionStr + "]");
            loadProjectsToUI();

            if (openImmediately) {
                Path inkFilePath = projectDir.toPath().resolve("canvas.inkpage");
                App.showWorkspace(projectName, width, height, inkFilePath, this::loadProjectsToUI);
            }

        } catch (IOException e) {
            AppLogger.error("Tekli çizim projesi oluşturulurken hata oluştu.", e);
        }
    }

    private void writeImageRegistryJson(
        File registryJson,
        String projectId,
        String title,
        String author,
        String path,
        String resolution,
        long timestamp
    ) throws IOException {
        try (FileWriter writer = new FileWriter(registryJson)) {
            writer.write("{\n");
            writer.write("  \"id\": \"" + projectId + "\",\n");
            writer.write("  \"title\": \"" + title + "\",\n");
            writer.write("  \"author\": \"" + author + "\",\n");
            writer.write("  \"path\": \"" + path.replace("\\", "/") + "\",\n");
            writer.write("  \"resolution\": \"" + resolution + "\",\n");
            writer.write("  \"createdAt\": " + timestamp + ",\n");
            writer.write("  \"lastModified\": " + System.currentTimeMillis() + "\n");
            writer.write("}");
        }
    }

    private void loadProjectsToUI() {
        projectsFlowPane.getChildren().clear();

        if (newProjectCard != null) {
            projectsFlowPane.getChildren().add(newProjectCard);
        }

        File userDataImagesDir = ConfigService.getInstance().getImagesDataDir().toFile();
        if (userDataImagesDir.exists() && userDataImagesDir.isDirectory()) {
            File[] jsonFiles = userDataImagesDir.listFiles((dir, name) -> name.endsWith(".json"));

            if (jsonFiles != null) {
                for (File file : jsonFiles) {
                    try {
                        String content = new String(Files.readAllBytes(file.toPath()));

                        String id = extractJsonString(content, "id");
                        String title = extractJsonString(content, "title");
                        String path = extractJsonString(content, "path");
                        String author = extractJsonString(content, "author");
                        String resolution = extractJsonString(content, "resolution");
                        if (author.isEmpty()) author = "Bilinmeyen Yazar";
                        if (resolution.isEmpty()) resolution = "1920x1080";

                        if (!title.isEmpty() && !path.isEmpty()) {
                            VBox card = createDrawingCard(file, id, title, author, path, resolution);
                            projectsFlowPane.getChildren().add(card);
                        }

                    } catch (IOException e) {
                        AppLogger.error("Çizim kaydı okunamadı: " + file.getAbsolutePath(), e);
                    }
                }
                AppLogger.debug("Çizim galerisi yüklendi: Toplam " + jsonFiles.length + " kayıt (" + userDataImagesDir + ")");
            }
        }
    }

    private VBox createDrawingCard(
        File registryFile,
        String id,
        String title,
        String author,
        String projectPath,
        String resolution
    ) {
        VBox card = new VBox(12);
        card.getStyleClass().add("manga-card");
        card.setStyle("-fx-alignment: top-center;");

        StackPane cover = new StackPane();
        cover.getStyleClass().add("manga-card-cover");
        cover.setPrefSize(230, 230);
        cover.setMinSize(230, 230);
        cover.setMaxSize(230, 230);
        cover.setStyle("-fx-min-height: 230px; -fx-max-height: 230px;");

        File posterFile = new File(projectPath + "/.inkera/poster.png");
        if (posterFile.exists()) {
            String imageUri = posterFile.toURI().toString() + "?time=" + System.currentTimeMillis();
            Image hqImage = new Image(imageUri, 230, 230, false, true);

            Rectangle clip = new Rectangle(230, 230);
            clip.setArcWidth(12);
            clip.setArcHeight(12);
            clip.setFill(new ImagePattern(hqImage));
            cover.getChildren().add(clip);
        } else {
            Path inkFile = Paths.get(projectPath, "canvas.inkpage");
            Label noCoverLabel = new Label(Files.exists(inkFile) ? "✓ Çizim Kayıtlı" : "Resim Yok");
            noCoverLabel.setStyle("-fx-text-fill: " + (Files.exists(inkFile) ? "#ffe07d" : "#555") + "; -fx-font-weight: bold;");
            cover.getChildren().add(noCoverLabel);
        }

        VBox infoBox = new VBox(4);
        infoBox.setStyle("-fx-alignment: center;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("manga-title");
        titleLabel.setMaxWidth(200);
        titleLabel.setStyle("-fx-alignment: center;");
        titleLabel.setWrapText(false);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        Label authorLabel = new Label(author + " • " + resolution);
        authorLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px; -fx-alignment: center;");
        authorLabel.setMaxWidth(200);
        authorLabel.setWrapText(false);
        authorLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        infoBox.getChildren().addAll(titleLabel, authorLabel);
        card.getChildren().addAll(cover, infoBox);

        ContextMenu contextMenu = new ContextMenu();

        MenuItem renameItem = new MenuItem("✏️ Çizim Adını & Yazarı Düzenle");
        renameItem.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold;");
        renameItem.setOnAction(e -> showRenameDrawingPanel(registryFile, id, title, author, projectPath, resolution));

        MenuItem deleteItem = new MenuItem("🗑 Resmi Sil");
        deleteItem.setStyle("-fx-text-fill: #ff5252; -fx-font-weight: bold;");
        deleteItem.setOnAction(e -> deleteProjectWithPanel(title, projectPath));

        contextMenu.getItems().addAll(renameItem, deleteItem);

        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                int w = DEFAULT_IMAGE_WIDTH;
                int h = DEFAULT_IMAGE_HEIGHT;
                String[] parts = resolution.split("x");
                if (parts.length == 2) {
                    try {
                        w = Integer.parseInt(parts[0].trim());
                        h = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }

                Path inkFilePath = Paths.get(projectPath, "canvas.inkpage");
                AppLogger.info("Çizim kartına tıklandı -> '" + title + "' açılıyor: " + inkFilePath);
                App.showWorkspace(title, w, h, inkFilePath, this::loadProjectsToUI);
            }
        });

        return card;
    }

    private void showRenameDrawingPanel(
        File registryFile,
        String id,
        String oldTitle,
        String oldAuthor,
        String oldPath,
        String resolution
    ) {
        VBox card = HomeController.createStyledPanelCard(420);

        Label header = new Label("Çizim Bilgilerini Düzenle");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        TextField titleField = new TextField(oldTitle);
        titleField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField authorField = new TextField(oldAuthor);
        authorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button saveBtn = new Button("Kaydet");
        saveBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        saveBtn.setOnAction(e -> {
            String newTitle = titleField.getText().trim();
            String newAuthor = authorField.getText().trim().isEmpty() ? "Bilinmeyen Yazar" : authorField.getText().trim();
            if (newTitle.isEmpty()) return;

            try {
                Path currentDir = Paths.get(oldPath);
                Path targetDir = currentDir;

                if (!newTitle.equals(oldTitle) && Files.exists(currentDir) && currentDir.getParent() != null) {
                    Path candidate = currentDir.getParent().resolve(newTitle);
                    if (!Files.exists(candidate)) {
                        Files.move(currentDir, candidate, StandardCopyOption.ATOMIC_MOVE);
                        targetDir = candidate;
                    }
                }

                if (registryFile.exists() && !newTitle.equals(oldTitle)) {
                    registryFile.delete();
                }

                File newRegistryFile = ConfigService.getInstance().getImagesDataDir().resolve(newTitle + ".json").toFile();
                writeImageRegistryJson(
                    newRegistryFile,
                    id.isEmpty() ? UUID.randomUUID().toString() : id,
                    newTitle,
                    newAuthor,
                    targetDir.toString(),
                    resolution,
                    System.currentTimeMillis()
                );

                AppLogger.info("Çizim yeniden adlandırıldı: '" + oldTitle + "' -> '" + newTitle + "'");
                HomeController.hideOverlayPanel();
                loadProjectsToUI();
            } catch (IOException ex) {
                AppLogger.error("Çizim yeniden adlandırılamadı.", ex);
            }
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);

        card.getChildren().addAll(
            header,
            new Label("Çizim Adı:"), titleField,
            new Label("Yazar:"), authorField,
            actions
        );

        HomeController.showOverlayPanel(card);
    }

    private void deleteProjectWithPanel(String projectName, String projectPath) {
        HomeController.showConfirmPanel(
            "'" + projectName + "' Silinsin mi?",
            "Bu işlem geri alınamaz. Çizim klasörü ve içindeki tüm dosyalar kalıcı olarak silinecektir.",
            "Kalıcı Olarak Sil",
            () -> {
                File projectFolder = new File(projectPath);
                if (projectFolder.exists()) {
                    deleteDirectoryRecursively(projectFolder);
                }

                File userDataImagesDir = ConfigService.getInstance().getImagesDataDir().toFile();
                File registryJson = new File(userDataImagesDir, projectName + ".json");
                if (registryJson.exists()) {
                    registryJson.delete();
                }

                AppLogger.info("Tekli çizim projesi silindi: " + projectName + " (" + projectPath + ")");
                loadProjectsToUI();
            }
        );
    }

    private void deleteDirectoryRecursively(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectoryRecursively(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    private String extractJsonString(String content, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(content);
        if (m.find()) return m.group(1);
        return "";
    }
}
