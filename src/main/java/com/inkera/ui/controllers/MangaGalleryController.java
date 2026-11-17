package com.inkera.ui.controllers;

import com.inkera.core.config.ConfigService;
import com.inkera.core.models.ProjectModel;
import com.inkera.services.ProjectService;
import com.inkera.util.AppLogger;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class MangaGalleryController {

    @FXML private FlowPane projectsFlowPane;
    @FXML private VBox newProjectCard;

    private final ProjectService projectService = new ProjectService();

    @FXML
    public void initialize() {
        if (newProjectCard != null) {
            newProjectCard.setOnMouseClicked(this::openNewMangaProjectPanel);
        }
        loadProjectsToUI();
    }

    public void loadProjectsToUI() {
        projectsFlowPane.getChildren().clear();

        List<ProjectModel> projects = projectService.loadAllProjects();

        for (ProjectModel project : projects) {
            VBox card = createProjectCard(project);
            projectsFlowPane.getChildren().add(card);
        }

        if (newProjectCard != null) {
            projectsFlowPane.getChildren().add(newProjectCard);
        }
    }

    private VBox createProjectCard(ProjectModel project) {
        VBox card = new VBox(12);
        card.getStyleClass().add("manga-card");

        StackPane cover = new StackPane();
        cover.getStyleClass().add("manga-card-cover");
        cover.setPrefSize(230, 324);
        cover.setMinSize(230, 324);
        cover.setMaxSize(230, 324);

        boolean hasCover = false;
        if (project.getProjectPath() != null) {
            File posterFile = new File(project.getProjectPath() + "/.inkera/poster.png");
            if (posterFile.exists()) {
                String imageUri = posterFile.toURI().toString() + "?time=" + System.currentTimeMillis();

                Image hqImage = new Image(imageUri, 230, 324, false, true);
                ImageView imageView = new ImageView(hqImage);

                imageView.setSmooth(true);
                imageView.setCache(true);
                imageView.setFitWidth(230);
                imageView.setFitHeight(324);

                Rectangle clip = new Rectangle(230, 324);
                clip.setArcWidth(12);
                clip.setArcHeight(12);
                imageView.setClip(clip);

                cover.getChildren().add(imageView);
                hasCover = true;
            }
        }

        if (!hasCover) {
            Label noCoverLabel = new Label("Kapak Yok");
            noCoverLabel.setStyle("-fx-text-fill: #555;");
            cover.getChildren().add(noCoverLabel);
        }

        VBox infoBox = new VBox(4);
        infoBox.setStyle("-fx-alignment: center;");

        Label titleLabel = new Label(project.getTitle());
        titleLabel.getStyleClass().add("manga-title");
        titleLabel.setMaxWidth(220);
        titleLabel.setWrapText(false);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setStyle("-fx-alignment: center;");

        int chapterCount = project.getChapters() != null ? project.getChapters().size() : 0;
        String authorStr = (project.getAuthor() != null && !project.getAuthor().isBlank()) ? project.getAuthor() : "Bilinmeyen Yazar";
        Label subLabel = new Label(authorStr + " • " + chapterCount + " Bölüm");
        subLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 12px;");

        infoBox.getChildren().addAll(titleLabel, subLabel);
        card.getChildren().addAll(cover, infoBox);

        ContextMenu contextMenu = new ContextMenu();

        MenuItem editMetaItem = new MenuItem("✏️ Manga Adını & Yazarı Düzenle");
        editMetaItem.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold;");
        editMetaItem.setOnAction(e -> showEditMangaMetaPanel(project));

        MenuItem deleteMangaItem = new MenuItem("🗑 Mangayı Sil");
        deleteMangaItem.setStyle("-fx-text-fill: #ff5252; -fx-font-weight: bold;");
        deleteMangaItem.setOnAction(e -> confirmDeleteManga(project));

        contextMenu.getItems().addAll(editMetaItem, deleteMangaItem);

        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                AppLogger.info("Manga projesine tıklandı: " + project.getTitle());
                openMangaDetail(project);
                e.consume();
            }
        });

        return card;
    }

    private void showEditMangaMetaPanel(ProjectModel project) {
        VBox card = HomeController.createStyledPanelCard(440);

        Label header = new Label("Manga Bilgilerini Düzenle");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        TextField titleField = new TextField(project.getTitle());
        titleField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField authorField = new TextField(project.getAuthor() != null ? project.getAuthor() : "");
        authorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        ComboBox<String> statusCombo = new ComboBox<>();
        statusCombo.getItems().addAll("ONGOING", "COMPLETED", "HIATUS");
        statusCombo.setValue(project.getStatus() != null ? project.getStatus() : "ONGOING");
        statusCombo.setMaxWidth(Double.MAX_VALUE);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button saveBtn = new Button("Kaydet");
        saveBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        saveBtn.setOnAction(e -> {
            String newTitle = titleField.getText().trim();
            String newAuthor = authorField.getText().trim();
            if (newTitle.isEmpty()) return;

            project.setTitle(newTitle);
            project.setAuthor(newAuthor.isEmpty() ? "Bilinmeyen Yazar" : newAuthor);
            project.setStatus(statusCombo.getValue());
            projectService.saveProject(project);

            AppLogger.info("Manga üstverisi güncellendi: " + newTitle);
            HomeController.hideOverlayPanel();
            loadProjectsToUI();
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);

        card.getChildren().addAll(
            header,
            new Label("Seri Adı:"), titleField,
            new Label("Yazar / Mangaka:"), authorField,
            new Label("Yayın Durumu:"), statusCombo,
            actions
        );

        HomeController.showOverlayPanel(card);
    }

    private void confirmDeleteManga(ProjectModel project) {
        HomeController.showConfirmPanel(
            "'" + project.getTitle() + "' Serisi Silinsin mi?",
            "Bu işlem geri alınamaz. Tüm bölümler, sayfalar (.inkpage) ve yerel proje klasörü kalıcı olarak silinecektir.",
            "Seriyi Kalıcı Olarak Sil",
            () -> {
                if (project.getProjectPath() != null) {
                    deleteDirRecursively(new File(project.getProjectPath()));
                }
                if (project.getProjectId() != null) {
                    Path regFile = ConfigService.getInstance().getProjectsDataDir().resolve(project.getProjectId() + ".json");
                    try {
                        Files.deleteIfExists(regFile);
                    } catch (IOException ignored) {
                    }
                }
                AppLogger.info("Manga serisi silindi: " + project.getTitle());
                loadProjectsToUI();
            }
        );
    }

    private void deleteDirRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteDirRecursively(f);
            }
        }
        dir.delete();
    }

    private void openMangaDetail(ProjectModel project) {
        try {
            URL resourceUrl = getClass().getResource("/com/inkera/fxml/manga_detail.fxml");
            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setResources(com.inkera.services.LocaleManager.getInstance().getBundle());
            Parent detailView = loader.load();

            MangaDetailController detailController = loader.getController();
            detailController.setProject(project);

            HomeController.setCenterView(detailView);
            HomeController.setSidebarVisible(false);

        } catch (IOException e) {
            AppLogger.error("Manga detay ekranı açılamadı.", e);
        }
    }

    private void openNewMangaProjectPanel(MouseEvent event) {
        VBox card = HomeController.createStyledPanelCard(460);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Yeni Manga Serisi Başlat");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> HomeController.hideOverlayPanel());
        header.getChildren().addAll(title, spacer, closeBtn);

        TextField nameField = new TextField();
        nameField.setPromptText("Örn: Kafir'in Zinciri");
        nameField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField authorField = new TextField("Samet Cırık");
        authorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        String defaultDir = Paths.get(System.getProperty("user.home"), "Masaüstü").toString();
        TextField locationField = new TextField(defaultDir);
        locationField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #ccc; -fx-border-color: #444; -fx-padding: 8;");
        HBox.setHgrow(locationField, Priority.ALWAYS);

        Button browseBtn = new Button("📁 Seç");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Manga Klasörü Konumu Seç");
            File init = new File(locationField.getText());
            if (init.exists() && init.isDirectory()) chooser.setInitialDirectory(init);
            File selected = chooser.showDialog(projectsFlowPane.getScene().getWindow());
            if (selected != null) locationField.setText(selected.getAbsolutePath());
        });

        HBox locBox = new HBox(8, locationField, browseBtn);
        locBox.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button createBtn = new Button("Seriyi Oluştur");
        createBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        createBtn.setOnAction(e -> {
            String seriesName = nameField.getText().trim();
            String author = authorField.getText().trim().isEmpty() ? "Bilinmeyen Yazar" : authorField.getText().trim();
            if (seriesName.isEmpty()) return;

            File dir = new File(locationField.getText().trim());
            projectService.createMangaProjectScaffold(seriesName, author, dir);
            HomeController.hideOverlayPanel();
            loadProjectsToUI();
        });

        actions.getChildren().addAll(cancelBtn, createBtn);

        card.getChildren().addAll(
            header,
            new Label("Manga / Seri Adı:"), nameField,
            new Label("Yazar / Mangaka:"), authorField,
            new Label("Kayıt Dizini:"), locBox,
            actions
        );

        HomeController.showOverlayPanel(card);
    }
}
