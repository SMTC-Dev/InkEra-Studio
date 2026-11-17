package com.inkera.ui.controllers;

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
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class MangaDetailController {

    @FXML private ImageView coverImageView;
    @FXML private Label titleLabel;
    @FXML private Label authorLabel;
    @FXML private FlowPane tagsFlowPane;
    @FXML private Label totalChaptersLabel;
    @FXML private VBox chaptersVBox;

    private ProjectModel currentProject;
    private final ProjectService projectService = new ProjectService();

    @FXML
    public void initialize() {
        if (titleLabel != null) {
            ContextMenu titleMenu = new ContextMenu();
            MenuItem renameSeriesItem = new MenuItem("✏️ Seri Adını & Yazarı Düzenle");
            renameSeriesItem.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold;");
            renameSeriesItem.setOnAction(e -> showEditMangaSettingsPanel());
            titleMenu.getItems().add(renameSeriesItem);
            titleLabel.setContextMenu(titleMenu);
            titleLabel.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    showEditMangaSettingsPanel();
                }
            });
        }
    }

    public void setProject(ProjectModel project) {
        this.currentProject = project;

        titleLabel.setText(project.getTitle());
        authorLabel.setText(project.getAuthor() != null ? project.getAuthor() : "Bilinmeyen Yazar");

        loadCoverImage();
        refreshTagsUI();
        renderChaptersList();
    }

    private void renderChaptersList() {
        chaptersVBox.getChildren().clear();
        List<ProjectModel.Chapter> chapters = currentProject.getChapters();
        int total = chapters != null ? chapters.size() : 0;
        totalChaptersLabel.setText("Toplam " + total + " Bölüm (Sıralamak için sürükleyip bırakın)");

        if (total > 0) {
            for (int i = 0; i < total; i++) {
                ProjectModel.Chapter chapter = chapters.get(i);
                chapter.setId(String.valueOf(i + 1));
                chaptersVBox.getChildren().add(createChapterItem(chapter, i));
            }
        } else {
            Label noChapLabel = new Label("Henüz bölüm eklenmemiş. '+ Yeni Bölüm' butonuyla ilk bölümü oluştur!");
            noChapLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
            chaptersVBox.getChildren().add(noChapLabel);
        }
    }

    private void refreshTagsUI() {
        tagsFlowPane.getChildren().clear();
        if (currentProject.getTags() != null && !currentProject.getTags().isEmpty()) {
            for (String tag : currentProject.getTags()) {
                Label tagLabel = new Label(tag);
                tagLabel.getStyleClass().add("meta-tag");
                tagsFlowPane.getChildren().add(tagLabel);
            }
        } else {
            Label noTagLabel = new Label("Etiket Ekle +");
            noTagLabel.getStyleClass().add("meta-tag");
            noTagLabel.setStyle("-fx-border-style: dashed; -fx-text-fill: #888;");
            tagsFlowPane.getChildren().add(noTagLabel);
        }
    }

    @FXML
    private void handleEditTags(MouseEvent event) {
        if (!tagsFlowPane.getChildren().isEmpty() && tagsFlowPane.getChildren().get(0) instanceof TextField) {
            return;
        }

        tagsFlowPane.getChildren().clear();

        if (currentProject.getTags() != null) {
            for (String tag : currentProject.getTags()) {
                tagsFlowPane.getChildren().add(createMiniTagInput(tag));
            }
        }

        TextField emptyInput = createMiniTagInput("");
        tagsFlowPane.getChildren().add(emptyInput);
        emptyInput.requestFocus();
    }

    private TextField createMiniTagInput(String text) {
        TextField field = new TextField(text);
        field.getStyleClass().add("mini-tag-input");

        field.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                e.consume();
                if (!field.getText().trim().isEmpty()) {
                    TextField newInput = createMiniTagInput("");
                    int currentIndex = tagsFlowPane.getChildren().indexOf(field);
                    tagsFlowPane.getChildren().add(currentIndex + 1, newInput);
                    newInput.requestFocus();
                }
            } else if (e.getCode() == KeyCode.ENTER) {
                handleSaveTags();
            } else if (e.getCode() == KeyCode.BACK_SPACE) {
                if (field.getText().isEmpty() && tagsFlowPane.getChildren().size() > 1) {
                    e.consume();
                    int currentIndex = tagsFlowPane.getChildren().indexOf(field);
                    tagsFlowPane.getChildren().remove(field);
                    if (currentIndex > 0) {
                        tagsFlowPane.getChildren().get(currentIndex - 1).requestFocus();
                    } else {
                        tagsFlowPane.getChildren().get(0).requestFocus();
                    }
                }
            }
        });
        return field;
    }

    private void handleSaveTags() {
        List<String> newTags = new ArrayList<>();
        for (javafx.scene.Node node : tagsFlowPane.getChildren()) {
            if (node instanceof TextField tf) {
                String val = tf.getText().trim();
                if (!val.isEmpty()) {
                    newTags.add(val);
                }
            }
        }
        currentProject.setTags(newTags);
        projectService.saveProject(currentProject);
        refreshTagsUI();
    }

    private void loadCoverImage() {
        if (currentProject != null && currentProject.getProjectPath() != null) {
            File posterFile = new File(currentProject.getProjectPath() + "/.inkera/poster.png");
            if (posterFile.exists()) {
                String imageUri = posterFile.toURI().toString() + "?time=" + System.currentTimeMillis();

                Image coverImage = new Image(imageUri, 250, 352, false, true);
                coverImageView.setImage(coverImage);
                coverImageView.setSmooth(true);
                coverImageView.setCache(true);

                Rectangle clip = new Rectangle(250, 352);
                clip.setArcWidth(16);
                clip.setArcHeight(16);
                coverImageView.setClip(clip);
            } else {
                coverImageView.setImage(null);
            }
        }
    }

    @FXML
    private void handleChangeCover() {
        if (currentProject == null || currentProject.getProjectPath() == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Kapak Resmi Seç");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Resim Dosyaları", "*.png", "*.jpg", "*.jpeg")
        );

        File selectedFile = fileChooser.showOpenDialog(coverImageView.getScene().getWindow());
        if (selectedFile != null) {
            try {
                Path targetPath = Paths.get(currentProject.getProjectPath(), ".inkera", "poster.png");
                Files.createDirectories(targetPath.getParent());
                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                loadCoverImage();
            } catch (IOException e) {
                AppLogger.error("Kapak resmi değiştirilemedi.", e);
            }
        }
    }

    private HBox createChapterItem(ProjectModel.Chapter chapter, int chapterIndex) {
        HBox hbox = new HBox(14);
        hbox.getStyleClass().add("chapter-item");
        hbox.setStyle("-fx-alignment: center-left;");

        Label dragHandle = new Label("⋮⋮");
        dragHandle.setStyle("-fx-text-fill: #777; -fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: open_hand; -fx-padding: 0 4 0 0;");

        Label idLabel = new Label("#" + (chapterIndex + 1));
        idLabel.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold; -fx-font-size: 16px; -fx-min-width: 28;");

        VBox infoBox = new VBox(2);
        Label chapterTitleLabel = new Label(chapter.getTitle());
        chapterTitleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        String layoutStr = "RTL".equals(chapter.getPageLayout()) ? "Manga (Sağdan Sola)" : "Çizgi Roman (Soldan Sağa)";
        Label pageLabel = new Label(chapter.getPageCount() + " Sayfa • " + layoutStr);
        pageLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");
        infoBox.getChildren().addAll(chapterTitleLabel, pageLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button editBtn = new Button("Sayfaları Aç →");
        editBtn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-cursor: hand;");

        hbox.getChildren().addAll(dragHandle, idLabel, infoBox, spacer, editBtn);

        /* ============================================================
         * BÖLÜMLER ARASI SÜRÜKLE-BIRAK (DRAG & DROP) HİYERARŞİSİ
         * ============================================================ */
        hbox.setOnDragDetected(event -> {
            Dragboard db = hbox.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(chapterIndex));
            db.setContent(content);
            hbox.setOpacity(0.55);
            event.consume();
        });

        hbox.setOnDragDone(event -> {
            hbox.setOpacity(1.0);
            event.consume();
        });

        hbox.setOnDragOver(event -> {
            if (event.getGestureSource() != hbox && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        hbox.setOnDragEntered(event -> {
            if (event.getGestureSource() != hbox && event.getDragboard().hasString()) {
                // Sürüklenen öğe bu satırın üzerindeyken sarı alt çizgiyle göster
                hbox.setStyle("-fx-alignment: center-left; -fx-border-color: #fdb547; -fx-border-width: 0 0 2 0;");
            }
        });

        hbox.setOnDragExited(event -> {
            // Öğe bu satırdan ayrıldığında orijinal CSS stiline dön
            hbox.setStyle("-fx-alignment: center-left;");
            event.consume();
        });

        hbox.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString() && currentProject.getChapters() != null) {
                try {
                    int fromIdx = Integer.parseInt(db.getString());
                    int toIdx = chapterIndex;
                    List<ProjectModel.Chapter> list = currentProject.getChapters();

                    if (fromIdx >= 0 && fromIdx < list.size() && toIdx >= 0 && toIdx < list.size() && fromIdx != toIdx) {
                        ProjectModel.Chapter moving = list.remove(fromIdx);
                        list.add(toIdx, moving);

                        for (int i = 0; i < list.size(); i++) {
                            list.get(i).setId(String.valueOf(i + 1));
                        }

                        projectService.saveProject(currentProject);
                        AppLogger.info(
                            "Bölüm sırası değiştirildi: '" + moving.getTitle()
                            + "' (" + (fromIdx + 1) + " -> " + (toIdx + 1) + ")"
                        );
                        renderChaptersList();
                        success = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        hbox.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1) {
                openChapterDetail(chapter);
            }
        });
        editBtn.setOnAction(e -> openChapterDetail(chapter));

        ContextMenu contextMenu = new ContextMenu();

        MenuItem renameItem = new MenuItem("✏️ Bölüm Adını & Yönünü Düzenle");
        renameItem.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold;");
        renameItem.setOnAction(e -> showRenameChapterPanel(chapter));

        MenuItem deleteItem = new MenuItem("🗑 Bölümü Sil");
        deleteItem.setStyle("-fx-text-fill: #ff5252; -fx-font-weight: bold;");
        deleteItem.setOnAction(e -> deleteChapterWithPanel(chapter));

        contextMenu.getItems().addAll(renameItem, deleteItem);

        hbox.setOnContextMenuRequested(e -> contextMenu.show(hbox, e.getScreenX(), e.getScreenY()));

        return hbox;
    }

    private void showRenameChapterPanel(ProjectModel.Chapter chapter) {
        VBox card = HomeController.createStyledPanelCard(420);

        Label header = new Label("Bölümü Düzenle (#" + chapter.getId() + ")");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        TextField titleField = new TextField(chapter.getTitle());
        titleField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        ComboBox<String> layoutCombo = new ComboBox<>();
        layoutCombo.getItems().addAll("RTL (Manga - Sağdan Sola)", "LTR (Comic - Soldan Sağa)");
        layoutCombo.setValue("LTR".equals(chapter.getPageLayout())
            ? "LTR (Comic - Soldan Sağa)"
            : "RTL (Manga - Sağdan Sola)");
        layoutCombo.setMaxWidth(Double.MAX_VALUE);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button saveBtn = new Button("Kaydet");
        saveBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        saveBtn.setOnAction(e -> {
            String oldTitle = chapter.getTitle();
            String newTitle = titleField.getText().trim();
            if (newTitle.isEmpty()) return;

            String newLayout = layoutCombo.getValue().startsWith("LTR") ? "LTR" : "RTL";

            try {
                if (!newTitle.equals(oldTitle) && currentProject.getProjectPath() != null) {
                    Path oldFolder = Paths.get(currentProject.getProjectPath(), "Episodes", oldTitle);
                    Path newFolder = Paths.get(currentProject.getProjectPath(), "Episodes", newTitle);
                    if (Files.exists(oldFolder) && !Files.exists(newFolder)) {
                        Files.move(oldFolder, newFolder, StandardCopyOption.ATOMIC_MOVE);
                    }
                }

                chapter.setTitle(newTitle);
                chapter.setPageLayout(newLayout);
                projectService.saveProject(currentProject);

                AppLogger.info("Bölüm güncellendi: '" + oldTitle + "' -> '" + newTitle + "' [" + newLayout + "]");
                HomeController.hideOverlayPanel();
                setProject(currentProject);
            } catch (IOException ex) {
                AppLogger.error("Bölüm klasörü yeniden adlandırılamadı.", ex);
            }
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);

        card.getChildren().addAll(
            header,
            new Label("Bölüm Başlığı:"), titleField,
            new Label("Okuma Yönü:"), layoutCombo,
            actions
        );

        HomeController.showOverlayPanel(card);
    }

    private void openChapterDetail(ProjectModel.Chapter chapter) {
        try {
            URL resourceUrl = getClass().getResource("/com/inkera/fxml/chapter_detail.fxml");
            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setResources(com.inkera.services.LocaleManager.getInstance().getBundle());
            Parent detailView = loader.load();

            ChapterDetailController controller = loader.getController();
            controller.setChapterContext(currentProject, chapter);

            HomeController.setCenterView(detailView);
        } catch (IOException e) {
            AppLogger.error("Bölüm detay ekranı açılamadı.", e);
        }
    }

    private void deleteChapterWithPanel(ProjectModel.Chapter chapter) {
        HomeController.showConfirmPanel(
            "'" + chapter.getTitle() + "' Bölümü Silinsin mi?",
            "Bu işlem geri alınamaz. Bölüm klasörü ve içindeki tüm sayfalar (.inkpage) kalıcı olarak silinecektir.",
            "Bölümü Sil",
            () -> {
                File chapterFolder = new File(currentProject.getProjectPath() + "/Episodes/" + chapter.getTitle());
                if (chapterFolder.exists()) {
                    deleteDirectoryRecursively(chapterFolder);
                }

                currentProject.getChapters().remove(chapter);
                projectService.saveProject(currentProject);
                AppLogger.info("Bölüm silindi: " + chapter.getTitle());
                setProject(currentProject);
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

    @FXML
    private void backToMangaGallery() {
        try {
            URL resourceUrl = getClass().getResource("/com/inkera/fxml/manga_gallery.fxml");
            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setResources(com.inkera.services.LocaleManager.getInstance().getBundle());
            Parent galleryView = loader.load();

            HomeController.setCenterView(galleryView);
            HomeController.setSidebarVisible(true);
        } catch (IOException e) {
            AppLogger.error("Manga galerisine dönülemedi.", e);
        }
    }

    /* ============================================================
     * PENCERE İÇİ HİKAYE ÖZETİ (SİNOPSİS) PANELİ
     * ============================================================ */

    @FXML
    private void showFullSummary() {
        if (currentProject == null) return;

        VBox card = HomeController.createStyledPanelCard(560);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Hikaye Özeti (Sinopsis) — " + currentProject.getTitle());
        title.setStyle("-fx-text-fill: white; -fx-font-size: 17px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> HomeController.hideOverlayPanel());
        header.getChildren().addAll(title, spacer, closeBtn);

        TextArea synopsisArea = new TextArea(currentProject.getSynopsis() != null ? currentProject.getSynopsis() : "");
        synopsisArea.setWrapText(true);
        synopsisArea.setPrefRowCount(10);
        synopsisArea.setPromptText("Manganın konusunu, karakter notlarını veya dünya kurgusunu buraya yazabilirsin...");
        synopsisArea.setStyle(
            "-fx-control-inner-background: #1e1e1e;" +
            "-fx-background-color: #1e1e1e;" +
            "-fx-text-fill: #e0e0e0;" +
            "-fx-border-color: #444;" +
            "-fx-border-radius: 6;" +
            "-fx-font-size: 13.5px;"
        );

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("Kapat");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button saveBtn = new Button("Özeti Kaydet");
        saveBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        saveBtn.setOnAction(e -> {
            currentProject.setSynopsis(synopsisArea.getText());
            projectService.saveProject(currentProject);
            AppLogger.info("Hikaye özeti (Sinopsis) kaydedildi: " + currentProject.getTitle());
            HomeController.hideOverlayPanel();
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);

        card.getChildren().addAll(header, synopsisArea, actions);
        HomeController.showOverlayPanel(card);
    }

    public void showEditMangaSettingsPanel() {
        if (currentProject == null) return;

        VBox card = HomeController.createStyledPanelCard(440);

        Label header = new Label("Seri Bilgilerini Düzenle");
        header.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");

        TextField titleField = new TextField(currentProject.getTitle());
        titleField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField authorField = new TextField(currentProject.getAuthor() != null ? currentProject.getAuthor() : "");
        authorField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

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

            currentProject.setTitle(newTitle);
            currentProject.setAuthor(newAuthor.isEmpty() ? "Bilinmeyen Yazar" : newAuthor);
            projectService.saveProject(currentProject);

            HomeController.hideOverlayPanel();
            setProject(currentProject);
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);
        card.getChildren().addAll(header, new Label("Seri Adı:"), titleField, new Label("Yazar:"), authorField, actions);
        HomeController.showOverlayPanel(card);
    }

    /* ============================================================
     * PENCERE İÇİ YENİ BÖLÜM EKLEME PANELİ
     * ============================================================ */

    @FXML
    private void handleAddChapter() {
        if (currentProject == null) return;

        VBox card = HomeController.createStyledPanelCard(440);

        int nextId = (currentProject.getChapters() != null ? currentProject.getChapters().size() : 0) + 1;

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Yeni Bölüm Ekle (#" + nextId + ")");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> HomeController.hideOverlayPanel());
        header.getChildren().addAll(title, spacer, closeBtn);

        TextField chapterTitleField = new TextField("Bölüm " + nextId);
        chapterTitleField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        ComboBox<String> layoutCombo = new ComboBox<>();
        layoutCombo.getItems().addAll("RTL (Manga - Sağdan Sola)", "LTR (Çizgi Roman - Soldan Sağa)");
        layoutCombo.setValue("RTL (Manga - Sağdan Sola)");
        layoutCombo.setMaxWidth(Double.MAX_VALUE);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> HomeController.hideOverlayPanel());

        Button createBtn = new Button("Bölümü Oluştur");
        createBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        createBtn.setOnAction(e -> {
            String chapName = chapterTitleField.getText().trim();
            if (chapName.isEmpty()) return;

            String layout = layoutCombo.getValue().startsWith("LTR") ? "LTR" : "RTL";

            if (currentProject.getChapters() == null) {
                currentProject.setChapters(new ArrayList<>());
            }

            ProjectModel.Chapter newChap = new ProjectModel.Chapter();
            newChap.setId(String.valueOf(nextId));
            newChap.setTitle(chapName);
            newChap.setPageCount(0);
            newChap.setPageLayout(layout);
            newChap.setPages(new ArrayList<>());

            if (currentProject.getProjectPath() != null) {
                try {
                    Path epDir = Paths.get(currentProject.getProjectPath(), "Episodes", chapName, ".inkepisode");
                    Files.createDirectories(epDir);
                } catch (IOException ex) {
                    AppLogger.error("Bölüm klasörü oluşturulamadı.", ex);
                }
            }

            currentProject.getChapters().add(newChap);
            projectService.saveProject(currentProject);

            AppLogger.info("Yeni bölüm eklendi: " + chapName + " [" + layout + "]");
            HomeController.hideOverlayPanel();
            setProject(currentProject);
        });

        actions.getChildren().addAll(cancelBtn, createBtn);

        card.getChildren().addAll(
            header,
            new Label("Bölüm Adı:"), chapterTitleField,
            new Label("Okuma ve Matbaa Dizilim Yönü:"), layoutCombo,
            actions
        );

        HomeController.showOverlayPanel(card);
    }
}
