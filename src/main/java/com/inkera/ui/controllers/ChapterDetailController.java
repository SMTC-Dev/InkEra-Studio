package com.inkera.ui.controllers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.inkera.App;
import com.inkera.core.models.ProjectModel;
import com.inkera.services.ProjectService;
import com.inkera.util.AppLogger;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import java.io.File;
import java.nio.file.StandardCopyOption;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChapterDetailController {

    private static final int DEFAULT_MANGA_PAGE_WIDTH = 2480;
    private static final int DEFAULT_MANGA_PAGE_HEIGHT = 3508;

    @FXML private Label headerTitleLabel;
    @FXML private Label chapterTitleLabel;
    @FXML private Label layoutInfoLabel;
    @FXML private StackPane coverPreviewPane;
    @FXML private Button addPageHeaderBtn;
    @FXML private VBox pagesVBox;

    private ProjectModel project;
    private ProjectModel.Chapter chapter;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ProjectService projectService = new ProjectService();

    public void setChapterContext(ProjectModel project, ProjectModel.Chapter chapter) {
        this.project = project;
        this.chapter = chapter;

        AppLogger.info(
            "Bölüm detayı açıldı -> Proje: '" + project.getTitle()
            + "', Bölüm: '" + chapter.getTitle()
            + "', Okuma Yönü: " + chapter.getPageLayout()
        );

        headerTitleLabel.setText(project.getTitle() + " - " + chapter.getTitle());
        chapterTitleLabel.setText(chapter.getTitle());

        boolean isRTL = "RTL".equals(chapter.getPageLayout());
        layoutInfoLabel.setText(isRTL ? "Okuma Yönü: Sağdan Sola" : "Okuma Yönü: Soldan Sağa");

        loadLocalChapterMeta();

        renderPages(isRTL);
    }

    private Path getChapterDirPath() {
        return Paths.get(project.getProjectPath(), "Episodes", chapter.getTitle());
    }

    private Path getPageDirPath(int pageNum) {
        return getChapterDirPath().resolve(String.valueOf(pageNum));
    }

    private Path getPageInkFilePath(int pageNum) {
        return getPageDirPath(pageNum).resolve("page.inkpage");
    }

    private Path getPagePreviewFilePath(int pageNum) {
        return getPageDirPath(pageNum).resolve("preview.png");
    }

    private Path getPageMetaFilePath(int pageNum) {
        return getPageDirPath(pageNum).resolve("page.meta.json");
    }

    private void loadLocalChapterMeta() {
        Path metaPath = getChapterDirPath().resolve(".inkepisode").resolve("meta.json");
        if (Files.exists(metaPath)) {
            try (Reader reader = Files.newBufferedReader(metaPath)) {
                ProjectModel.Chapter localData = gson.fromJson(reader, ProjectModel.Chapter.class);
                if (localData != null) {
                    this.chapter.setStartsOnLeft(localData.getStartsOnLeft());
                    this.chapter.setPages(localData.getPages() != null ? localData.getPages() : new ArrayList<>());
                    this.chapter.setPageCount(this.chapter.getPages().size());
                    AppLogger.debug(
                        "Bölüm yerel meta.json yüklendi: " + metaPath
                        + " (Toplam sayfa: " + this.chapter.getPages().size()
                        + ", startsOnLeft: " + this.chapter.getStartsOnLeft() + ")"
                    );
                }
            } catch (IOException e) {
                AppLogger.error("Bölüm yerel meta.json okunamadı: " + metaPath, e);
            }
        } else {
            AppLogger.debug("Bölüm için henüz yerel meta.json yok: " + metaPath);
        }
    }

    private void saveLocalChapterMeta() {
        chapter.setPageCount(chapter.getPages() != null ? chapter.getPages().size() : 0);

        Path metaPath = getChapterDirPath().resolve(".inkepisode").resolve("meta.json");
        try {
            Files.createDirectories(metaPath.getParent());
            try (Writer writer = Files.newBufferedWriter(metaPath)) {
                gson.toJson(this.chapter, writer);
            }
            AppLogger.info("Bölüm yerel meta.json kaydedildi: " + metaPath + " (pageCount=" + chapter.getPageCount() + ")");
        } catch (IOException e) {
            AppLogger.error("Bölüm yerel meta.json kaydedilemedi: " + metaPath, e);
        }

        projectService.saveProject(project);
    }

    private void initializePageDirectoryAndMeta(int pageNum) {
        Path pageDir = getPageDirPath(pageNum);
        Path pageMeta = getPageMetaFilePath(pageNum);

        try {
            Files.createDirectories(pageDir);

            if (!Files.exists(pageMeta)) {
                Map<String, Object> metaData = new LinkedHashMap<>();
                metaData.put("pageNumber", pageNum);
                metaData.put("chapterTitle", chapter.getTitle());
                metaData.put("projectTitle", project.getTitle());
                metaData.put("defaultWidth", DEFAULT_MANGA_PAGE_WIDTH);
                metaData.put("defaultHeight", DEFAULT_MANGA_PAGE_HEIGHT);
                metaData.put("canvasFile", "page.inkpage");
                metaData.put("previewFile", "preview.png");
                metaData.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                try (Writer writer = Files.newBufferedWriter(pageMeta)) {
                    gson.toJson(metaData, writer);
                }
            }

            AppLogger.info("Sayfa klasörü ve page.meta.json hazırlandı: " + pageDir);
        } catch (IOException e) {
            AppLogger.error("Sayfa klasörü/meta oluşturulurken hata oluştu: " + pageDir, e);
        }
    }

    private void updateLeftCoverPreview() {
        if (coverPreviewPane == null) {
            return;
        }

        coverPreviewPane.getChildren().clear();

        boolean hasPages = chapter.getPages() != null && !chapter.getPages().isEmpty();
        Path page1Preview = getPagePreviewFilePath(1);
        Path page1Ink = getPageInkFilePath(1);

        if (hasPages) {
            coverPreviewPane.setCursor(Cursor.HAND);
            coverPreviewPane.setOnMouseClicked(e -> openPageInWorkspace(1));
        } else {
            coverPreviewPane.setCursor(Cursor.DEFAULT);
            coverPreviewPane.setOnMouseClicked(null);
        }

        if (Files.exists(page1Preview)) {
            coverPreviewPane.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-border-color: #fdb547;" +
                "-fx-border-width: 2;" +
                "-fx-border-style: solid;" +
                "-fx-background-radius: 6;" +
                "-fx-border-radius: 6;" +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.55), 14, 0, 0, 4);"
            );

            String uri = page1Preview.toUri().toString() + "?t=" + System.currentTimeMillis();
            Image img = new Image(uri, 196, 278, true, true);
            ImageView imgView = new ImageView(img);
            imgView.setFitWidth(196);
            imgView.setFitHeight(278);
            imgView.setPreserveRatio(true);
            imgView.setSmooth(true);

            Rectangle clip = new Rectangle(196, 278);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            imgView.setClip(clip);

            Label badge = new Label("Kapak (Sayfa 1)");
            badge.setStyle(
                "-fx-background-color: rgba(20, 20, 20, 0.85);" +
                "-fx-text-fill: #ffe07d;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 11px;" +
                "-fx-padding: 3 8 3 8;" +
                "-fx-background-radius: 4;"
            );
            StackPane.setAlignment(badge, Pos.BOTTOM_CENTER);
            StackPane.setMargin(badge, new Insets(8));

            coverPreviewPane.getChildren().addAll(imgView, badge);
        } else {
            coverPreviewPane.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-border-color: #fdb547;" +
                "-fx-border-width: 2;" +
                "-fx-border-style: dashed;" +
                "-fx-background-radius: 5;" +
                "-fx-border-radius: 5;"
            );

            Label lbl = new Label(Files.exists(page1Ink) ? "Sayfa 1 (Kayıtlı)" : "Sayfa 1 (Kapak)");
            lbl.setStyle("-fx-text-fill: #777; -fx-font-weight: bold;");
            coverPreviewPane.getChildren().add(lbl);
        }
    }

    private void renderPages(boolean isRTL) {
        pagesVBox.getChildren().clear();
        updateLeftCoverPreview();

        if (chapter.getStartsOnLeft() == null || chapter.getPages() == null || chapter.getPages().isEmpty()) {
            HBox initialSpread = new HBox(20);
            initialSpread.setStyle("-fx-alignment: center;");

            StackPane leftBtn = createAddPageButton("Buradan Başla\n(Sol Sayfa)");
            StackPane rightBtn = createAddPageButton("Buradan Başla\n(Sağ Sayfa)");

            leftBtn.setOnMouseClicked(e -> handleInitialPagePlacement(true));
            rightBtn.setOnMouseClicked(e -> handleInitialPagePlacement(false));

            if (isRTL) {
                initialSpread.getChildren().addAll(rightBtn, leftBtn);
            } else {
                initialSpread.getChildren().addAll(leftBtn, rightBtn);
            }

            pagesVBox.getChildren().add(initialSpread);
            return;
        }

        boolean startsLeft = chapter.getStartsOnLeft();
        int totalPages = chapter.getPages().size();

        int currentIdx = 0;

        HBox firstSpread = new HBox(20);
        firstSpread.setStyle("-fx-alignment: center;");

        StackPane firstLeft;
        StackPane firstRight;

        if (startsLeft) {
            firstLeft = createPageCard(1);
            currentIdx++;
            if (currentIdx < totalPages) {
                firstRight = createPageCard(2);
                currentIdx++;
            } else {
                firstRight = createAddPageButton("+");
            }
        } else {
            firstLeft = createEmptyPlaceholder("Önceki Bölüm");
            firstRight = createPageCard(1);
            currentIdx++;
        }

        if (isRTL) firstSpread.getChildren().addAll(firstRight, firstLeft);
        else firstSpread.getChildren().addAll(firstLeft, firstRight);

        pagesVBox.getChildren().add(firstSpread);

        while (currentIdx < totalPages) {
            HBox spread = new HBox(20);
            spread.setStyle("-fx-alignment: center;");

            StackPane leftPage = createPageCard(currentIdx + 1);
            currentIdx++;

            StackPane rightPage;
            if (currentIdx < totalPages) {
                rightPage = createPageCard(currentIdx + 1);
                currentIdx++;
            } else {
                rightPage = createAddPageButton("+");
            }

            if (isRTL) spread.getChildren().addAll(rightPage, leftPage);
            else spread.getChildren().addAll(leftPage, rightPage);

            pagesVBox.getChildren().add(spread);
        }

        if (currentIdx == totalPages) {
            boolean lastRowIsFull = false;

            if (startsLeft && totalPages % 2 == 0) lastRowIsFull = true;
            if (!startsLeft && totalPages % 2 != 0) lastRowIsFull = true;

            if (lastRowIsFull) {
                HBox addSpread = new HBox(20);
                addSpread.setStyle("-fx-alignment: center;");
                StackPane addBtn = createAddPageButton("+");
                StackPane empty = createEmptyPlaceholder("");

                if (isRTL) addSpread.getChildren().addAll(empty, addBtn);
                else addSpread.getChildren().addAll(addBtn, empty);

                pagesVBox.getChildren().add(addSpread);
            }
        }
    }

    private void handleInitialPagePlacement(boolean isLeft) {
        if (chapter.getPages() == null) chapter.setPages(new ArrayList<>());

        chapter.setStartsOnLeft(isLeft);
        chapter.getPages().add(1);

        initializePageDirectoryAndMeta(1);
        saveLocalChapterMeta();
        renderPages("RTL".equals(chapter.getPageLayout()));
    }

    @FXML
    private void handleAddNewPageFromHeader() {
        if (chapter.getStartsOnLeft() == null || chapter.getPages() == null || chapter.getPages().isEmpty()) {
            boolean isRTL = "RTL".equals(chapter.getPageLayout());
            handleInitialPagePlacement(!isRTL);
        } else {
            addNewPage();
        }
    }

    private void addNewPage() {
        int newPageNum = chapter.getPages().size() + 1;
        chapter.getPages().add(newPageNum);

        initializePageDirectoryAndMeta(newPageNum);
        saveLocalChapterMeta();
        renderPages("RTL".equals(chapter.getPageLayout()));
    }

    private StackPane createPageCard(int pageNum) {
        StackPane pane = new StackPane();
        pane.setPrefSize(180, 250);
        pane.setMinSize(180, 250);
        pane.setMaxSize(180, 250);
        pane.setCursor(Cursor.HAND);

        Path inkPageFile = getPageInkFilePath(pageNum);
        Path previewFile = getPagePreviewFilePath(pageNum);
        boolean existsOnDisk = Files.exists(inkPageFile);
        boolean hasPreview = Files.exists(previewFile);

        String borderColor = existsOnDisk ? "#fdb547" : "#555";
        pane.setStyle(
            "-fx-background-color: #333;" +
            "-fx-background-radius: 6;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 6;"
        );

        if (hasPreview) {
            String uri = previewFile.toUri().toString() + "?t=" + System.currentTimeMillis();
            Image previewImg = new Image(uri, 176, 246, true, true);
            ImageView imgView = new ImageView(previewImg);
            imgView.setFitWidth(176);
            imgView.setFitHeight(246);
            imgView.setPreserveRatio(true);
            imgView.setSmooth(true);

            Rectangle clip = new Rectangle(176, 246);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            imgView.setClip(clip);

            Label badge = new Label("Sayfa " + pageNum);
            badge.setStyle(
                "-fx-background-color: rgba(20, 20, 20, 0.82);" +
                "-fx-text-fill: #e0e0e0;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 11px;" +
                "-fx-padding: 3 8 3 8;" +
                "-fx-background-radius: 4;"
            );
            StackPane.setAlignment(badge, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(badge, new Insets(6));

            pane.getChildren().addAll(imgView, badge);
        } else {
            VBox contentBox = new VBox(8);
            contentBox.setAlignment(Pos.CENTER);

            Label label = new Label("Sayfa " + pageNum);
            label.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");

            Label statusLabel = new Label(existsOnDisk ? "✓ Kayıtlı (.inkpage)" : "Boş Sayfa (Tıkla & Çiz)");
            statusLabel.setStyle(
                existsOnDisk
                    ? "-fx-text-fill: #ffe07d; -fx-font-size: 11px;"
                    : "-fx-text-fill: #aaa; -fx-font-size: 11px;"
            );

            contentBox.getChildren().addAll(label, statusLabel);
            pane.getChildren().add(contentBox);
        }

        ContextMenu contextMenu = new ContextMenu();

        if (hasPreview) {
            MenuItem setPosterItem = new MenuItem("🖼 Manga Kapağı (Poster) Yap");
            setPosterItem.setStyle("-fx-text-fill: #ffe07d; -fx-font-weight: bold;");
            setPosterItem.setOnAction(e -> setPageAsMangaCover(pageNum));
            contextMenu.getItems().add(setPosterItem);
        }

        if (chapter.getPages() != null && pageNum == chapter.getPages().size()) {
            MenuItem deletePageItem = new MenuItem("🗑 Bu Sayfayı Sil (Sayfa " + pageNum + ")");
            deletePageItem.setStyle("-fx-text-fill: #ff5252; -fx-font-weight: bold;");
            deletePageItem.setOnAction(e -> deleteLastPage(pageNum));
            contextMenu.getItems().add(deletePageItem);
        }

        pane.setOnContextMenuRequested(e -> {
            if (!contextMenu.getItems().isEmpty()) {
                contextMenu.show(pane, e.getScreenX(), e.getScreenY());
            }
        });

        pane.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                openPageInWorkspace(pageNum);
            }
        });

        return pane;
    }

    private void setPageAsMangaCover(int pageNum) {
        Path previewFile = getPagePreviewFilePath(pageNum);
        if (!Files.exists(previewFile)) {
            return;
        }

        try {
            Path posterPath = Paths.get(project.getProjectPath(), ".inkera", "poster.png");
            Files.createDirectories(posterPath.getParent());
            Files.copy(previewFile, posterPath, StandardCopyOption.REPLACE_EXISTING);

            project.setCoverImagePath(posterPath.toString());
            projectService.saveProject(project);

            AppLogger.info("Sayfa " + pageNum + " manga ana kapağı (.inkera/poster.png) olarak ayarlandı: " + posterPath);
        } catch (IOException e) {
            AppLogger.error("Manga kapağı güncellenirken hata oluştu.", e);
        }
    }

    private void deleteLastPage(int pageNum) {
        HomeController.showConfirmPanel(
            "Sayfa " + pageNum + " Silinsin mi?",
            "Bu sayfanın klasörü ve içindeki çizim (.inkpage) kalıcı olarak silinecektir.",
            "Sayfayı Sil",
            () -> {
                Path pageDir = getPageDirPath(pageNum);
                deleteDirRecursively(pageDir.toFile());

                if (chapter.getPages() != null && !chapter.getPages().isEmpty()) {
                    chapter.getPages().remove(Integer.valueOf(pageNum));
                }

                saveLocalChapterMeta();
                AppLogger.info("Sayfa silindi: Sayfa " + pageNum + " (" + pageDir + ")");
                renderPages("RTL".equals(chapter.getPageLayout()));
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

    private void openPageInWorkspace(int pageNum) {
        initializePageDirectoryAndMeta(pageNum);

        Path inkPageFile = getPageInkFilePath(pageNum);
        String workspaceTitle = project.getTitle() + " - " + chapter.getTitle() + " - Sayfa " + pageNum;

        AppLogger.info(
            "Sayfa kartına tıklandı -> Sayfa " + pageNum
            + " Workspace'te açılıyor: " + inkPageFile
        );

        App.showWorkspace(
            workspaceTitle,
            DEFAULT_MANGA_PAGE_WIDTH,
            DEFAULT_MANGA_PAGE_HEIGHT,
            inkPageFile,
            () -> renderPages("RTL".equals(chapter.getPageLayout()))
        );
    }

    private StackPane createAddPageButton(String text) {
        StackPane pane = new StackPane();
        pane.setPrefSize(180, 250);
        pane.setStyle("-fx-background-color: transparent; -fx-border-color: #fdb547; -fx-border-width: 2; -fx-border-style: dashed; -fx-border-radius: 5;");
        pane.setCursor(Cursor.HAND);

        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #fdb547; -fx-font-weight: bold;");
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        pane.getChildren().add(label);

        if ("+".equals(text)) {
            pane.setOnMouseClicked(e -> addNewPage());
        }

        return pane;
    }

    private StackPane createEmptyPlaceholder(String text) {
        StackPane pane = new StackPane();
        pane.setPrefSize(180, 250);
        pane.setStyle("-fx-background-color: transparent;");

        if (!text.isEmpty()) {
            Label label = new Label(text);
            label.setStyle("-fx-text-fill: #555; -fx-font-style: italic;");
            pane.getChildren().add(label);
        }
        return pane;
    }

    @FXML
    private void backToMangaDetail() {
        try {
            URL resourceUrl = getClass().getResource("/com/inkera/fxml/manga_detail.fxml");
            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setResources(com.inkera.services.LocaleManager.getInstance().getBundle());
            Parent detailView = loader.load();

            MangaDetailController controller = loader.getController();
            controller.setProject(project);

            HomeController.setCenterView(detailView);
        } catch (IOException e) {
            AppLogger.error("Manga detay ekranına dönülürken hata oluştu.", e);
        }
    }
}
