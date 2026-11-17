package com.inkera.ui.controllers;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import com.inkera.App;
import com.inkera.services.AuthService;
import com.inkera.services.LocaleManager;
import com.inkera.util.AppLogger;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class HomeController implements Initializable {

    @FXML private BorderPane mainPane;
    @FXML private StackPane contentStack;
    @FXML private Label authStatusLabel;
    @FXML private Button loginSidebarBtn;

    private static BorderPane rootPane;
    private static StackPane centerStack;
    private static StackPane activeOverlayBackdrop;

    private final AuthService authService = new AuthService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (mainPane != null) {
            rootPane = mainPane;
            centerStack = contentStack;

            boolean useCustomTitleBar = com.inkera.core.config.ConfigService.getInstance().isCustomTitleBar();
            if (!useCustomTitleBar) {
                mainPane.setTop(null);
                mainPane.setPrefHeight(mainPane.getPrefHeight() - 38);
            }

            try {
                showDashboard();
            } catch (Exception e) {
                AppLogger.error("Başlangıç ekranı (Dashboard) yüklenemedi.", e);
            }
        }
    }

    public static void setCenterView(Parent view) {
        hideOverlayPanel();
        if (centerStack != null) {
            centerStack.getChildren().setAll(view);
        } else if (rootPane != null) {
            rootPane.setCenter(view);
        }
    }

    public static void setSidebarVisible(boolean visible) {
        if (rootPane != null && rootPane.getLeft() != null) {
            rootPane.getLeft().setVisible(visible);
            rootPane.getLeft().setManaged(visible);
        }
    }

    public static void showOverlayPanel(Region cardNode) {
        if (centerStack == null) return;
        hideOverlayPanel();

        StackPane backdrop = new StackPane();
        backdrop.setStyle("-fx-background-color: rgba(12, 12, 14, 0.76);");
        backdrop.setAlignment(Pos.CENTER);

        cardNode.setOnMouseClicked(e -> e.consume());
        backdrop.setOnMouseClicked(e -> hideOverlayPanel());

        backdrop.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                hideOverlayPanel();
            }
        });

        backdrop.getChildren().add(cardNode);
        activeOverlayBackdrop = backdrop;
        centerStack.getChildren().add(backdrop);
        cardNode.requestFocus();
    }

    public static void hideOverlayPanel() {
        if (centerStack != null && activeOverlayBackdrop != null) {
            centerStack.getChildren().remove(activeOverlayBackdrop);
            activeOverlayBackdrop = null;
        }
    }

    public static void showConfirmPanel(
        String title,
        String subtitle,
        String confirmButtonText,
        Runnable onConfirm
    ) {
        VBox card = createStyledPanelCard(440);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-text-fill: #ff5252; -fx-font-size: 18px; -fx-font-weight: bold;");

        Label subLbl = new Label(subtitle);
        subLbl.setWrapText(true);
        subLbl.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 13px;");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("İptal");
        cancelBtn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-padding: 8 18; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> hideOverlayPanel());

        Button confirmBtn = new Button(confirmButtonText);
        confirmBtn.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 18; -fx-cursor: hand;");
        confirmBtn.setOnAction(e -> {
            hideOverlayPanel();
            if (onConfirm != null) onConfirm.run();
        });

        actions.getChildren().addAll(cancelBtn, confirmBtn);
        card.getChildren().addAll(titleLbl, subLbl, actions);

        showOverlayPanel(card);
    }

    public static VBox createStyledPanelCard(double width) {
        VBox card = new VBox(16);
        card.setPrefWidth(width);
        card.setMaxWidth(width);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setStyle(
            "-fx-background-color: #252526;" +
            "-fx-border-color: #fdb547;" +
            "-fx-border-width: 1.5;" +
            "-fx-background-radius: 10;" +
            "-fx-border-radius: 10;" +
            "-fx-padding: 24;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.75), 25, 0, 0, 8);"
        );
        return card;
    }

    private void loadView(String fxmlPath) {
        try {
            URL resourceUrl = getClass().getResource(fxmlPath);
            if (resourceUrl == null) {
                AppLogger.error("FXML dosyası bulunamadı: " + fxmlPath);
                return;
            }

            FXMLLoader loader = new FXMLLoader(resourceUrl);
            loader.setResources(LocaleManager.getInstance().getBundle());
            Parent view = loader.load();
            setCenterView(view);
        } catch (IOException e) {
            AppLogger.error("Dosya yüklenirken G/Ç hatası oluştu: " + fxmlPath, e);
        }
    }

    @FXML
    private void showDashboard() {
        setSidebarVisible(true);
        loadView("/com/inkera/fxml/dashboard.fxml");
    }

    @FXML
    private void showGallery() {
        setSidebarVisible(true);
        loadView("/com/inkera/fxml/gallery.fxml");
    }

    @FXML
    private void showMangaGallery() {
        setSidebarVisible(true);
        loadView("/com/inkera/fxml/manga_gallery.fxml");
    }

    @FXML
    private void showSettings() {
        setSidebarVisible(true);
        loadView("/com/inkera/fxml/settings.fxml");
    }

    @FXML
    private void openMangaDetail() {
        loadView("/com/inkera/fxml/manga_detail.fxml");
    }

    @FXML
    private void backToMangaGallery() {
        showMangaGallery();
    }

    @FXML
    private void openLastProject() {
        App.showWorkspace("Hızlı_Eskiz", 1920, 1080);
    }

    @FXML
    private void handleNewProject(ActionEvent event) {
        VBox card = createStyledPanelCard(460);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Yeni Kanvas Aç");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> hideOverlayPanel());
        header.getChildren().addAll(title, spacer, closeBtn);

        TextField nameField = new TextField("Hızlı_Çizim");
        nameField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        TextField widthField = new TextField("1920");
        widthField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");
        TextField heightField = new TextField("1080");
        heightField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        HBox sizeBox = new HBox(10,
            new VBox(4, new Label("Genişlik (px):"), widthField),
            new VBox(4, new Label("Yükseklik (px):"), heightField)
        );

        HBox presets = new HBox(8);
        Button fhdBtn = new Button("FHD (1920x1080)");
        fhdBtn.setOnAction(e -> { widthField.setText("1920"); heightField.setText("1080"); });
        Button a4Btn = new Button("Manga A4 (2480x3508)");
        a4Btn.setOnAction(e -> { widthField.setText("2480"); heightField.setText("3508"); });
        Button sqBtn = new Button("Kare (1080x1080)");
        sqBtn.setOnAction(e -> { widthField.setText("1080"); heightField.setText("1080"); });
        presets.getChildren().addAll(fhdBtn, a4Btn, sqBtn);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("İptal");
        cancelBtn.setOnAction(e -> hideOverlayPanel());
        Button createBtn = new Button("Kanvası Başlat");
        createBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18;");
        createBtn.setOnAction(e -> {
            try {
                String name = nameField.getText().trim().isEmpty() ? "Hızlı_Çizim" : nameField.getText().trim();
                int w = Integer.parseInt(widthField.getText().trim());
                int h = Integer.parseInt(heightField.getText().trim());
                if (w > 0 && h > 0) {
                    hideOverlayPanel();
                    App.showWorkspace(name, w, h);
                }
            } catch (NumberFormatException ignored) {
            }
        });
        actions.getChildren().addAll(cancelBtn, createBtn);

        card.getChildren().addAll(
            header,
            new Label("Çalışma Adı:"), nameField,
            new Label("Hazır Şablonlar:"), presets,
            sizeBox,
            actions
        );

        showOverlayPanel(card);
    }

    @FXML
    private void showLoginDialog() {
        VBox card = createStyledPanelCard(400);

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("InkEra Bulut Hesabı");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> hideOverlayPanel());
        header.getChildren().addAll(title, spacer, closeBtn);

        Label sub = new Label("Fırça paketlerini ve projelerini senkronize etmek için giriş yap.");
        sub.setWrapText(true);
        sub.setStyle("-fx-text-fill: #999; -fx-font-size: 12px;");

        TextField emailField = new TextField();
        emailField.setPromptText("E-posta adresi");
        emailField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Şifre (en az 6 karakter)");
        passField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: white; -fx-border-color: #444; -fx-padding: 8;");

        Label statusLbl = new Label();
        statusLbl.setStyle("-fx-font-size: 12px;");

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Button regBtn = new Button("Kayıt Ol");
        regBtn.setStyle("-fx-background-color: #3c3f41; -fx-text-fill: white; -fx-padding: 8 16; -fx-cursor: hand;");
        regBtn.setOnAction(e -> {
            statusLbl.setText("Kayıt olunuyor...");
            statusLbl.setStyle("-fx-text-fill: #fdb547;");
            boolean ok = authService.register(emailField.getText().trim(), passField.getText());
            if (ok) {
                statusLbl.setText("✓ Kayıt başarılı! Şimdi giriş yapabilirsin.");
                statusLbl.setStyle("-fx-text-fill: #fdb547;");
            } else {
                statusLbl.setText("✕ Kayıt başarısız (şifre en az 6 hane olmalı).");
                statusLbl.setStyle("-fx-text-fill: #ff5252;");
            }
        });

        Button loginBtn = new Button("Giriş Yap");
        loginBtn.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-weight: bold; -fx-padding: 8 18; -fx-cursor: hand;");
        loginBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            boolean ok = authService.login(email, passField.getText());
            if (ok) {
                if (authStatusLabel != null) {
                    authStatusLabel.setText("Aktif: " + email);
                    authStatusLabel.setStyle("-fx-text-fill: #ffe07d; -fx-font-size: 11px; -fx-font-weight: bold;");
                }
                if (loginSidebarBtn != null) {
                    loginSidebarBtn.setText("Hesap Değiştir");
                }
                hideOverlayPanel();
            } else {
                statusLbl.setText("✕ Giriş başarısız! Bilgileri kontrol et.");
                statusLbl.setStyle("-fx-text-fill: #ff5252;");
            }
        });

        buttons.getChildren().addAll(regBtn, loginBtn);

        card.getChildren().addAll(
            header,
            sub,
            new Label("E-posta:"), emailField,
            new Label("Şifre:"), passField,
            statusLbl,
            buttons
        );

        showOverlayPanel(card);
    }
}
