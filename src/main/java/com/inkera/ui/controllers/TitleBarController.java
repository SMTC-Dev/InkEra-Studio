package com.inkera.ui.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class TitleBarController {

    @FXML private HBox titleBar;
    @FXML private Label titleLabel;
    @FXML private Button minimizeBtn;
    @FXML private Button closeBtn;

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    public void initialize() {
    }

    public void setWindowSubtitle(String subtitle) {
        if (titleLabel != null && subtitle != null) {
            titleLabel.setText(subtitle);
        }
    }

    @FXML
    private void handleMousePressed(MouseEvent event) {
        if (event.isPrimaryButtonDown()) {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        }
    }

    @FXML
    private void handleMouseDragged(MouseEvent event) {
        if (event.isPrimaryButtonDown() && titleBar.getScene() != null) {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            if (stage != null && !stage.isMaximized()) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        }
    }

    @FXML
    private void minimize() {
        if (titleBar.getScene() != null) {
            ((Stage) titleBar.getScene().getWindow()).setIconified(true);
        }
    }

    @FXML
    private void close() {
        if (titleBar.getScene() != null && titleBar.getScene().getWindow() instanceof Stage stage) {
            stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
            stage.close();
        }
    }
}
