package com.inkera.ui.workspace;

import com.inkera.util.AppLogger;
import com.inkera.util.ThumbnailGenerator;

import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class WorkspaceController implements Initializable {

    public enum ActiveTool {
        PENCIL,
        ERASER,
        BUCKET
    }

    public static class LayerData {
        public final int nativeIndex;
        public String name;
        public boolean visible = true;
        public float opacity = 1.0f;

        public LayerData(int nativeIndex, String name, boolean visible, float opacity) {
            this.nativeIndex = nativeIndex;
            this.name = name;
            this.visible = visible;
            this.opacity = opacity;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @FXML private Button leftMenuBtn;
    @FXML private Button rightMenuBtn;
    @FXML private Button layersQuickBtn;

    @FXML private Button pencilToolBtn;
    @FXML private Button eraserToolBtn;
    @FXML private Button bucketToolBtn;
    @FXML private Button undoBtn;
    @FXML private Button redoBtn;
    @FXML private Label activeToolBadge;

    @FXML private Slider brushSizeSlider;
    @FXML private Label brushSizeValueLabel;
    @FXML private Button sizePreset2Btn;
    @FXML private Button sizePreset6Btn;
    @FXML private Button sizePreset15Btn;
    @FXML private Button sizePreset35Btn;
    @FXML private Button sizePreset70Btn;

    @FXML private ColorPicker brushColorPicker;
    @FXML private Button toneBlackBtn;
    @FXML private Button toneDarkGrayBtn;
    @FXML private Button toneMidGrayBtn;
    @FXML private Button toneLightGrayBtn;
    @FXML private Button toneWhiteBtn;

    @FXML private Slider layerOpacitySlider;
    @FXML private Label layerOpacityValueLabel;

    @FXML private VBox leftFloatingPanel;
    @FXML private VBox rightFloatingPanel;

    @FXML private Pane infiniteWorkspace;
    @FXML private StackPane paperStack;
    @FXML private ImageView canvasImageView;

    @FXML private ListView<LayerData> layerListView;
    @FXML private Button addLayerBtn;
    @FXML private Button exportPngBtn;

    private boolean isLeftOpen = false;
    private boolean isRightOpen = false;
    private boolean userInteracted = false;
    private boolean isSyncingLayers = false;
    private boolean isUpdatingOpacitySlider = false;
    private boolean unsavedChanges = false;

    private boolean isSpacePressed = false; // Canvas Grab kontrolü

    private int canvasWidth = 1920;
    private int canvasHeight = 1080;

    private ActiveTool activeTool = ActiveTool.PENCIL;
    private ActiveTool previousTool = ActiveTool.PENCIL;
    private int currentBrushSize = 6;
    private int currentColorArgb = 0xFF000000;

    private Stage boundStage;
    private String baseStageTitle = "InkEra Studio";

    private final Affine affineTransform = new Affine();
    private double currentScale = 1.0;
    private double translateX = 0.0;
    private double translateY = 0.0;

    private final double ZOOM_FACTOR = 1.15;
    private double dynamicMaxZoom = 1.0;

    private double lastPanX;
    private double lastPanY;

    private double lastDrawX = -1;
    private double lastDrawY = -1;
    private float lastPressure = 1.0f;
    private long lastDrawTimeNs = 0;
    private int currentStrokeSegments = 0;
    private long strokeStartTimeNs = 0L;

    private boolean needsRender = false;
    private AnimationTimer renderTimer;

    private Arena arena;
    private MemorySegment pixelSegment;
    private PixelBuffer<IntBuffer> pixelBuffer;

    private MemorySegment engineHandle;
    private MethodHandle createEngineMH, destroyEngineMH, beginStrokeSnapshotMH, undoActionMH, redoActionMH;
    private MethodHandle clearCanvasMH, setBrushSizeMH, getBrushSizeMH, usePencilMH, useEraserMH, useBucketFillMH;
    private MethodHandle addNewLayerMH, setActiveLayerMH, setLayerVisibilityMH, setLayerOpacityMH, setLayerNameMH;
    private MethodHandle deleteLayerMH, moveLayerMH, savePageMH, loadPageMH, getPageSizeMH;
    private MethodHandle getLayerCountMH, getActiveLayerIndexMH, getLayerVisibilityMH, getLayerOpacityMH, copyLayerNameMH;

    private Path currentPagePath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        AppLogger.info("WorkspaceController başlatılıyor.");

        setupUI();
        paperStack.getTransforms().add(affineTransform);
        setupZoomAndPanEngine();
        loadCEngine();
        setupToolAndBrushUI();
        setupKeyboardShortcuts();
        setupDrawingEngine();
        setupLayerUI();
        setupRenderEngine();

        infiniteWorkspace.widthProperty().addListener((obs, oldV, newV) -> autoCenterCanvas());
        infiniteWorkspace.heightProperty().addListener((obs, oldV, newV) -> autoCenterCanvas());
    }

    public void bindStageTitle(Stage stage, String baseTitle) {
        this.boundStage = stage;
        this.baseStageTitle = baseTitle;
        updateWindowTitle();
    }

    public boolean hasUnsavedChanges() {
        return unsavedChanges;
    }

    private void markDirty(boolean dirty) {
        if (this.unsavedChanges != dirty) {
            this.unsavedChanges = dirty;
            updateWindowTitle();
        }
    }

    private void updateWindowTitle() {
        if (boundStage != null) {
            Platform.runLater(() ->
                boundStage.setTitle(unsavedChanges ? baseStageTitle + " *" : baseStageTitle)
            );
        }
    }

    private void setupUI() {
        leftFloatingPanel.setTranslateX(-280);
        leftFloatingPanel.setVisible(false);
        rightFloatingPanel.setTranslateX(280);
        rightFloatingPanel.setVisible(false);

        leftMenuBtn.setOnAction(event -> {
            isLeftOpen = !isLeftOpen;
            togglePanel(leftFloatingPanel, isLeftOpen, -280);
        });

        Runnable toggleRightAction = () -> {
            isRightOpen = !isRightOpen;
            togglePanel(rightFloatingPanel, isRightOpen, 280);
        };

        rightMenuBtn.setOnAction(event -> toggleRightAction.run());
        if (layersQuickBtn != null) {
            layersQuickBtn.setOnAction(event -> toggleRightAction.run());
        }

        Rectangle clipRect = new Rectangle();
        clipRect.widthProperty().bind(infiniteWorkspace.widthProperty());
        clipRect.heightProperty().bind(infiniteWorkspace.heightProperty());
        infiniteWorkspace.setClip(clipRect);
    }

    private void setupToolAndBrushUI() {
        if (pencilToolBtn != null) pencilToolBtn.setOnAction(e -> selectTool(ActiveTool.PENCIL));
        if (eraserToolBtn != null) eraserToolBtn.setOnAction(e -> selectTool(ActiveTool.ERASER));
        if (bucketToolBtn != null) bucketToolBtn.setOnAction(e -> selectTool(ActiveTool.BUCKET));
        if (undoBtn != null) undoBtn.setOnAction(e -> performUndo());
        if (redoBtn != null) redoBtn.setOnAction(e -> performRedo());

        if (brushSizeSlider != null) {
            brushSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int newSize = Math.max(1, (int) Math.round(newVal.doubleValue()));
                applyBrushSize(newSize, false);
            });
        }

        if (sizePreset2Btn != null) sizePreset2Btn.setOnAction(e -> applyBrushSize(2, true));
        if (sizePreset6Btn != null) sizePreset6Btn.setOnAction(e -> applyBrushSize(6, true));
        if (sizePreset15Btn != null) sizePreset15Btn.setOnAction(e -> applyBrushSize(15, true));
        if (sizePreset35Btn != null) sizePreset35Btn.setOnAction(e -> applyBrushSize(35, true));
        if (sizePreset70Btn != null) sizePreset70Btn.setOnAction(e -> applyBrushSize(70, true));

        if (brushColorPicker != null) {
            brushColorPicker.setValue(Color.BLACK);
            brushColorPicker.setOnAction(e -> setInkColor(colorToArgb(brushColorPicker.getValue())));
        }

        if (toneBlackBtn != null) toneBlackBtn.setOnAction(e -> setPresetColor(Color.web("#000000")));
        if (toneDarkGrayBtn != null) toneDarkGrayBtn.setOnAction(e -> setPresetColor(Color.web("#404040")));
        if (toneMidGrayBtn != null) toneMidGrayBtn.setOnAction(e -> setPresetColor(Color.web("#808080")));
        if (toneLightGrayBtn != null) toneLightGrayBtn.setOnAction(e -> setPresetColor(Color.web("#c0c0c0")));
        if (toneWhiteBtn != null) toneWhiteBtn.setOnAction(e -> setPresetColor(Color.web("#ffffff")));

        if (exportPngBtn != null) exportPngBtn.setOnAction(e -> exportCurrentCanvasAsPng());

        selectTool(ActiveTool.PENCIL);
    }

    private void selectTool(ActiveTool tool) {
        this.activeTool = tool;

        String activeStyle = "-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-cursor: hand; -fx-background-radius: 6; -fx-min-width: 34; -fx-min-height: 34; -fx-font-size: 15px;";
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand; -fx-background-radius: 6; -fx-min-width: 34; -fx-min-height: 34; -fx-font-size: 15px;";

        if (pencilToolBtn != null) pencilToolBtn.setStyle(tool == ActiveTool.PENCIL ? activeStyle : inactiveStyle);
        if (eraserToolBtn != null) eraserToolBtn.setStyle(tool == ActiveTool.ERASER ? activeStyle : inactiveStyle);
        if (bucketToolBtn != null) bucketToolBtn.setStyle(tool == ActiveTool.BUCKET ? activeStyle : inactiveStyle);

        if (activeToolBadge != null) {
            switch (tool) {
                case PENCIL -> {
                    activeToolBadge.setText("KALEM");
                    activeToolBadge.setStyle("-fx-background-color: #fdb547; -fx-text-fill: #181819; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
                }
                case ERASER -> {
                    activeToolBadge.setText("SİLGİ");
                    activeToolBadge.setStyle("-fx-background-color: #e53935; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
                }
                case BUCKET -> {
                    activeToolBadge.setText("KOVA");
                    activeToolBadge.setStyle("-fx-background-color: #1e88e5; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 4;");
                }
            }
        }
        AppLogger.info("Aktif çizim aracı değiştirildi: " + tool.name());
    }

    private void applyBrushSize(int size, boolean updateSlider) {
        int clamped = Math.max(1, Math.min(120, size));
        if (clamped == this.currentBrushSize && !updateSlider) return;
        this.currentBrushSize = clamped;

        if (brushSizeValueLabel != null) brushSizeValueLabel.setText(clamped + " px");
        if (updateSlider && brushSizeSlider != null) brushSizeSlider.setValue(clamped);

        if (engineHandle != null && setBrushSizeMH != null) {
            try {
                setBrushSizeMH.invokeExact(engineHandle, clamped);
            } catch (Throwable t) {
                AppLogger.error("Fırça boyutu native motora iletilemedi.", t);
            }
        }
    }

    private void setPresetColor(Color color) {
        if (brushColorPicker != null) brushColorPicker.setValue(color);
        setInkColor(colorToArgb(color));
        if (activeTool == ActiveTool.ERASER) selectTool(ActiveTool.PENCIL);
    }

    private void setInkColor(int argb) {
        this.currentColorArgb = argb;
    }

    private static int colorToArgb(Color c) {
        int a = (int) Math.round(c.getOpacity() * 255.0);
        int r = (int) Math.round(c.getRed() * 255.0);
        int g = (int) Math.round(c.getGreen() * 255.0);
        int b = (int) Math.round(c.getBlue() * 255.0);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private void performUndo() {
        if (undoActionMH == null || engineHandle == null || pixelSegment == null) return;
        try {
            boolean ok = (boolean) undoActionMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight);
            if (ok) {
                syncLayerUIFromNative();
                needsRender = true;
                markDirty(true);
            }
        } catch (Throwable t) {
            AppLogger.error("Undo hatası.", t);
        }
    }

    private void performRedo() {
        if (redoActionMH == null || engineHandle == null || pixelSegment == null) return;
        try {
            boolean ok = (boolean) redoActionMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight);
            if (ok) {
                syncLayerUIFromNative();
                needsRender = true;
                markDirty(true);
            }
        } catch (Throwable t) {
            AppLogger.error("Redo hatası.", t);
        }
    }

    private void setupRenderEngine() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (needsRender && pixelBuffer != null) {
                    pixelBuffer.updateBuffer(buffer -> null);
                    needsRender = false;
                }
            }
        };
        renderTimer.start();
    }

    private void setupLayerUI() {
        if (layerOpacitySlider != null) {
            layerOpacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                int pct = (int) Math.round(newVal.doubleValue());
                if (layerOpacityValueLabel != null) layerOpacityValueLabel.setText(pct + "%");
                if (isUpdatingOpacitySlider || isSyncingLayers) return;

                LayerData selectedItem = layerListView.getSelectionModel().getSelectedItem();
                if (selectedItem == null || engineHandle == null || setLayerOpacityMH == null) return;

                float opacityFloat = Math.max(0.0f, Math.min(1.0f, pct / 100.0f));
                selectedItem.opacity = opacityFloat;

                try {
                    boolean ok = (boolean) setLayerOpacityMH.invokeExact(
                        engineHandle, pixelSegment, canvasWidth, canvasHeight, selectedItem.nativeIndex, opacityFloat
                    );
                    if (ok) {
                        needsRender = true;
                        markDirty(true);
                        layerListView.refresh();
                    }
                } catch (Throwable t) {
                    AppLogger.error("Opaklık güncellenemedi.", t);
                }
            });
        }

        layerListView.setCellFactory(listView -> new ListCell<>() {
            private final HBox root = new HBox(10);
            private final Button visibilityBtn = new Button("👁");
            private final Label nameLabel = new Label();
            private final TextField inlineNameField = new TextField();
            private final Label opacityBadge = new Label();
            private final Button deleteBtn = new Button("🗑");
            private final Region spacer = new Region();

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                root.setAlignment(Pos.CENTER_LEFT);

                visibilityBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #aaa; -fx-cursor: hand; -fx-padding: 0 5 0 0;");
                deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5555; -fx-cursor: hand; -fx-padding: 0 0 0 5;");
                nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                opacityBadge.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");

                root.getChildren().addAll(visibilityBtn, nameLabel, spacer, opacityBadge, deleteBtn);
                root.setStyle("-fx-padding: 5;");

                visibilityBtn.setOnAction(event -> {
                    LayerData item = getItem();
                    if (item == null) return;

                    boolean targetVisibility = !item.visible;
                    boolean ok = triggerCPPAction(setLayerVisibilityMH, item.nativeIndex, targetVisibility);

                    if (ok) {
                        item.visible = targetVisibility;
                        visibilityBtn.setText(item.visible ? "👁" : "🕶");
                        nameLabel.setOpacity(item.visible ? 1.0 : 0.5);
                        markDirty(true);
                    }
                });

                deleteBtn.setOnAction(event -> {
                    LayerData item = getItem();
                    if (item == null) return;
                    if (getListView().getItems().size() <= 1) {
                        AppLogger.warn("Katman silinemedi: Tuvalde en az 1 katman kalmalıdır.");
                        return;
                    }
                    boolean ok = triggerCPPAction(deleteLayerMH, item.nativeIndex);
                    if (ok) {
                        syncLayerUIFromNative();
                        markDirty(true);
                    }
                });

                inlineNameField.setStyle("-fx-background-color: #1e1e1e; -fx-text-fill: #fdb547; -fx-border-color: #fdb547; -fx-border-radius: 3; -fx-font-size: 12px; -fx-padding: 2 5 2 5;");
                inlineNameField.setPrefWidth(125);

                Runnable commitInlineRename = () -> {
                    if (!root.getChildren().contains(inlineNameField)) return;
                    String newName = inlineNameField.getText();
                    LayerData currentItem = getItem();
                    int pos = root.getChildren().indexOf(inlineNameField);
                    if (pos >= 0) root.getChildren().set(pos, nameLabel);
                    if (currentItem != null && newName != null) {
                        String trimmed = newName.trim();
                        if (!trimmed.isEmpty() && !trimmed.equals(currentItem.name)) {
                            commitLayerRenameToNative(currentItem.nativeIndex, trimmed);
                        }
                    }
                };

                inlineNameField.setOnAction(e -> commitInlineRename.run());
                inlineNameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) commitInlineRename.run();
                });
                inlineNameField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        e.consume();
                        int pos = root.getChildren().indexOf(inlineNameField);
                        if (pos >= 0) root.getChildren().set(pos, nameLabel);
                    }
                });

                setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && getItem() != null) {
                        int pos = root.getChildren().indexOf(nameLabel);
                        if (pos >= 0) {
                            inlineNameField.setText(getItem().name);
                            root.getChildren().set(pos, inlineNameField);
                            inlineNameField.requestFocus();
                            inlineNameField.selectAll();
                        }
                    }
                });

                setOnDragDetected(event -> {
                    LayerData item = getItem();
                    if (item == null) return;
                    Dragboard db = startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(String.valueOf(item.nativeIndex));
                    db.setContent(content);
                    event.consume();
                });

                setOnDragOver(event -> {
                    if (event.getGestureSource() != this && getItem() != null && event.getDragboard().hasString()) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        setStyle("-fx-border-color: #fdb547; -fx-border-width: 2 0 0 0;");
                    }
                    event.consume();
                });

                setOnDragExited(event -> setStyle("-fx-border-width: 0;"));

                setOnDragDropped(event -> {
                    Dragboard db = event.getDragboard();
                    LayerData targetItem = getItem();
                    if (!db.hasString() || targetItem == null) {
                        event.setDropCompleted(false);
                        event.consume();
                        return;
                    }
                    int fromNativeIdx = Integer.parseInt(db.getString());
                    int toNativeIdx = targetItem.nativeIndex;

                    if (fromNativeIdx != toNativeIdx) {
                        boolean moved = triggerCPPMove(fromNativeIdx, toNativeIdx);
                        if (moved) {
                            syncLayerUIFromNative();
                            markDirty(true);
                        }
                    }
                    event.setDropCompleted(true);
                    event.consume();
                });
            }

            @Override
            protected void updateItem(LayerData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.name);
                    opacityBadge.setText(Math.round(item.opacity * 100) + "%");
                    visibilityBtn.setText(item.visible ? "👁" : "🕶");
                    nameLabel.setOpacity(item.visible ? 1.0 : 0.5);
                    setGraphic(root);
                }
            }
        });

        layerListView.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, selectedLayer) -> {
            if (selectedLayer != null && layerOpacitySlider != null) {
                isUpdatingOpacitySlider = true;
                int pct = Math.round(selectedLayer.opacity * 100.0f);
                layerOpacitySlider.setValue(pct);
                if (layerOpacityValueLabel != null) layerOpacityValueLabel.setText(pct + "%");
                isUpdatingOpacitySlider = false;
            }

            if (isSyncingLayers) return;

            if (selectedLayer != null && engineHandle != null && setActiveLayerMH != null) {
                try {
                    boolean ok = (boolean) setActiveLayerMH.invokeExact(engineHandle, selectedLayer.nativeIndex);
                    AppLogger.debug("Java UI -> Native aktif katman seçimi: nativeIndex=" + selectedLayer.nativeIndex + " ('" + selectedLayer.name + "', sonuç=" + ok + ")");
                } catch (Throwable throwable) {
                    AppLogger.error("Aktif katman native motora aktarılamadı.", throwable);
                }
            }
        });

        addLayerBtn.setOnAction(event -> {
            if (engineHandle == null || pixelSegment == null || addNewLayerMH == null) return;
            try {
                boolean created = (boolean) addNewLayerMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight);
                if (created) {
                    syncLayerUIFromNative();
                    needsRender = true;
                    markDirty(true);
                }
            } catch (Throwable throwable) {
                AppLogger.error("Yeni katman oluşturulamadı.", throwable);
            }
        });
    }

    private boolean triggerCPPAction(MethodHandle handle, int nativeIndex) {
        if (handle == null || engineHandle == null || pixelSegment == null) return false;
        try {
            boolean result = (boolean) handle.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, nativeIndex);
            needsRender = true;
            return result;
        } catch (Throwable throwable) {
            AppLogger.error("Native layer işlemi başarısız.", throwable);
            return false;
        }
    }

    private boolean triggerCPPAction(MethodHandle handle, int nativeIndex, boolean flag) {
        if (handle == null || engineHandle == null || pixelSegment == null) return false;
        try {
            boolean result = (boolean) handle.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, nativeIndex, flag);
            needsRender = true;
            return result;
        } catch (Throwable throwable) {
            AppLogger.error("Native layer görünürlük işlemi başarısız.", throwable);
            return false;
        }
    }

    private boolean triggerCPPMove(int fromNativeIndex, int toNativeIndex) {
        if (moveLayerMH == null || engineHandle == null || pixelSegment == null) return false;
        try {
            boolean result = (boolean) moveLayerMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, fromNativeIndex, toNativeIndex);
            needsRender = true;
            return result;
        } catch (Throwable throwable) {
            AppLogger.error("Native katman taşıma işlemi başarısız.", throwable);
            return false;
        }
    }

    /* ============================================================
     * KEYBOARD SHORTCUTS & KRITA-STYLE MODES (B, E, Space Pan, Zoom)
     * ============================================================ */

    private void setupKeyboardShortcuts() {
        Platform.runLater(() -> {
            if (infiniteWorkspace.getScene() == null) return;

            infiniteWorkspace.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.isControlDown()) {
                    if (event.getCode() == KeyCode.S) {
                        event.consume();
                        saveCurrentPage();
                    } else if (event.getCode() == KeyCode.O) {
                        event.consume();
                        promptAndOpenPage();
                    } else if (event.getCode() == KeyCode.E) {
                        event.consume();
                        exportCurrentCanvasAsPng();
                    } else if (event.getCode() == KeyCode.Z) {
                        event.consume();
                        if (event.isShiftDown()) performRedo();
                        else performUndo();
                    } else if (event.getCode() == KeyCode.Y) {
                        event.consume();
                        performRedo();
                    }
                    return;
                }

                if (event.getTarget() instanceof javafx.scene.control.TextInputControl) return;

                // SPACE TUŞU: Canvas Grab (Pan) modu
                if (event.getCode() == KeyCode.SPACE && !isSpacePressed) {
                    isSpacePressed = true;
                    infiniteWorkspace.setStyle("-fx-background-color: #2e2e2e; -fx-cursor: closed_hand;");
                    event.consume();
                    return;
                }

                // B: Kalem Aracı
                if (event.getCode() == KeyCode.B) {
                    event.consume();
                    selectTool(ActiveTool.PENCIL);
                }
                // E: Silgi Modu (KRITA TARZI TOGGLE: Basınca değiştirir)
                else if (event.getCode() == KeyCode.E) {
                    event.consume();
                    if (activeTool == ActiveTool.ERASER) {
                        selectTool(previousTool != ActiveTool.ERASER ? previousTool : ActiveTool.PENCIL);
                    } else {
                        previousTool = activeTool;
                        selectTool(ActiveTool.ERASER);
                    }
                }
                // G: Kova
                else if (event.getCode() == KeyCode.G) {
                    event.consume();
                    selectTool(ActiveTool.BUCKET);
                } else if (event.getCode() == KeyCode.OPEN_BRACKET) {
                    event.consume();
                    applyBrushSize(Math.max(1, currentBrushSize - 2), true);
                } else if (event.getCode() == KeyCode.CLOSE_BRACKET) {
                    event.consume();
                    applyBrushSize(Math.min(120, currentBrushSize + 2), true);
                }
            });

            infiniteWorkspace.getScene().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
                if (event.getCode() == KeyCode.SPACE) {
                    isSpacePressed = false;
                    infiniteWorkspace.setStyle("-fx-background-color: #2e2e2e; -fx-cursor: default;");
                    event.consume();
                }
                // E tuşu bırakıldığında (release) aracı GERİ DÖNDÜRME MANTIĞI SİLİNDİ!
                // Artık sadece tuşa basıldığında Toggle yapacak.
            });
        });
    }

    /* ============================================================
     * ZOOM / PAN & CANVAS GRAB (SPACE + MOUSE)
     * ============================================================ */

    private void updateMatrix() {
        affineTransform.setToIdentity();
        affineTransform.appendTranslation(translateX, translateY);
        affineTransform.appendScale(currentScale, currentScale);
    }

    private void setupZoomAndPanEngine() {
        infiniteWorkspace.setOnScroll(event -> {
            event.consume();
            if (event.getDeltaY() == 0) return;

            userInteracted = true;
            double scaleMultiplier = event.getDeltaY() > 0 ? ZOOM_FACTOR : 1.0 / ZOOM_FACTOR;
            double newScale = Math.max(0.05, Math.min(currentScale * scaleMultiplier, dynamicMaxZoom));

            translateX = event.getX() - ((event.getX() - translateX) / currentScale * newScale);
            translateY = event.getY() - ((event.getY() - translateY) / currentScale * newScale);

            currentScale = newScale;
            updateMatrix();
        });

        infiniteWorkspace.setOnMousePressed(event -> {
            // Space basılıysa VEYA orta tuşa basıldıysa Canvas Grab (Pan) yap
            if (isSpacePressed || event.isMiddleButtonDown()) {
                userInteracted = true;
                infiniteWorkspace.setStyle("-fx-background-color: #2e2e2e; -fx-cursor: closed_hand;");
                lastPanX = event.getX();
                lastPanY = event.getY();
                event.consume();
            }
        });

        infiniteWorkspace.setOnMouseDragged(event -> {
            if (isSpacePressed || event.isMiddleButtonDown()) {
                translateX += event.getX() - lastPanX;
                translateY += event.getY() - lastPanY;
                lastPanX = event.getX();
                lastPanY = event.getY();
                updateMatrix();
                event.consume();
            }
        });

        infiniteWorkspace.setOnMouseReleased(event -> {
            if (isSpacePressed || event.isMiddleButtonDown()) {
                infiniteWorkspace.setStyle("-fx-background-color: #2e2e2e; -fx-cursor: open_hand;");
                event.consume();
            }
        });
    }

    private double p0X, p0Y, p1X, p1Y, p2X, p2Y;
    private float pr0, pr1, pr2;

    /* ============================================================
     * DRAWING (Hız/Dinamik Hesaplama Entegre & Bezier Smoothing)
     * ============================================================ */

    private void setupDrawingEngine() {
        canvasImageView.setOnMousePressed(event -> {
            if (isSpacePressed || !event.isPrimaryButtonDown()) return;

            if (activeTool == ActiveTool.BUCKET) {
                applyBucketAtPixel(event.getX(), event.getY());
                return;
            }

            if (beginStrokeSnapshotMH != null && engineHandle != null) {
                try {
                    beginStrokeSnapshotMH.invokeExact(engineHandle);
                } catch (Throwable t) {
                    AppLogger.error("Undo snapshot alınamadı.", t);
                }
            }

            // Bezier noktalarını ilk vuruş (nokta) ile başlat
            p0X = p1X = p2X = event.getX();
            p0Y = p1Y = p2Y = event.getY();
            pr0 = pr1 = pr2 = 1.0f;
            lastPressure = 1.0f;

            lastDrawTimeNs = System.nanoTime();
            currentStrokeSegments = 1;
            strokeStartTimeNs = System.nanoTime();

            // İlk dokunuş noktası: C++ tarafına 0 mesafeli çizgi yollar. (Spacing'i sıfırlar ve nokta atar)
            drawAtPixel(p0X, p0Y, p0X, p0Y, pr0, pr0);
            markDirty(true);
        });

        canvasImageView.setOnMouseDragged(event -> {
            if (isSpacePressed || activeTool == ActiveTool.BUCKET) return;

            if (event.isPrimaryButtonDown() && p2X != -1) {
                long nowNs = System.nanoTime();
                float newPressure = calculateSpeedBasedPressure(
                    p2X, p2Y, event.getX(), event.getY(), nowNs - lastDrawTimeNs
                );

                // Noktaları bir kaydır
                p0X = p1X; p0Y = p1Y; pr0 = pr1;
                p1X = p2X; p1Y = p2Y; pr1 = pr2;
                p2X = event.getX(); p2Y = event.getY(); pr2 = newPressure;
                lastPressure = newPressure;

                // Spline (Bezier) Interpolation: Doğrusal (köşeli) çizgiler yerine pürüzsüz kavis oluşturur
                double mid1X = (p0X + p1X) / 2.0;
                double mid1Y = (p0Y + p1Y) / 2.0;
                float mid1P = (pr0 + pr1) / 2.0f;

                double mid2X = (p1X + p2X) / 2.0;
                double mid2Y = (p1Y + p2Y) / 2.0;
                float mid2P = (pr1 + pr2) / 2.0f;

                drawBezierCurve(mid1X, mid1Y, mid1P, p1X, p1Y, pr1, mid2X, mid2Y, mid2P);

                lastDrawTimeNs = nowNs;
                currentStrokeSegments++;
            }
        });

        canvasImageView.setOnMouseReleased(event -> {
            if (p2X != -1 && currentStrokeSegments > 1) {
                // Fırça kuyruğunun (kavisin son kısmı) yarım kalmaması için ucu bağla
                drawAtPixel((p1X + p2X) / 2.0, (p1Y + p2Y) / 2.0, p2X, p2Y, (pr1 + pr2) / 2.0f, pr2);
            }
            p0X = p1X = p2X = -1;
            currentStrokeSegments = 0;
        });
    }

    // Doğrusal olmayan Quadratic Bezier kavis eğrisini hesaplar ve C++'a küçük adımlar (line'lar) olarak yedirir
    private void drawBezierCurve(
        double x0, double y0, float p0,
        double cx, double cy, float pc,
        double x1, double y1, float p1
    ) {
        // Eğrinin tahmini uzunluğunu bul
        double dist = Math.hypot(x1 - x0, y1 - y0) + Math.hypot(cx - x0, cy - y0) + Math.hypot(x1 - cx, y1 - cy);
        
        // Her 3 pikselde 1 eğri adımı oluştur (JNI'yi boğmadan maksimum pürüzsüzlük)
        int steps = (int) Math.max(3, dist / 3.0); 

        double prevStepX = x0;
        double prevStepY = y0;
        float prevStepP = p0;

        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float invT = 1.0f - t;
            
            // Quadratic Bezier formülü
            double qx = invT * invT * x0 + 2 * invT * t * cx + t * t * x1;
            double qy = invT * invT * y0 + 2 * invT * t * cy + t * t * y1;
            float qp = invT * invT * p0 + 2 * invT * t * pc + t * t * p1;

            // Oluşturulan kavis parçasını C++ motoruna yolla
            drawAtPixel(prevStepX, prevStepY, qx, qy, prevStepP, qp);

            prevStepX = qx;
            prevStepY = qy;
            prevStepP = qp;
        }
    }

    private float calculateSpeedBasedPressure(double startX, double startY, double endX, double endY, long timeDiffNs) {
        if (timeDiffNs <= 0) return lastPressure;
        double dist = Math.hypot(endX - startX, endY - startY);
        double velocity = dist / (timeDiffNs / 1_000_000.0); // px/ms

        float targetPressure = 1.0f - (float) Math.min(0.85, velocity * 0.12);
        float smoothPressure = lastPressure * 0.65f + targetPressure * 0.35f;
        return Math.max(0.1f, Math.min(1.0f, smoothPressure));
    }

    private void drawAtPixel(double startX, double startY, double endX, double endY, float pStart, float pEnd) {
        if (engineHandle == null || pixelSegment == null) return;
        try {
            if (activeTool == ActiveTool.ERASER && useEraserMH != null) {
                useEraserMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, (int) startX, (int) startY, pStart, (int) endX, (int) endY, pEnd);
            } else if (usePencilMH != null) {
                usePencilMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, (int) startX, (int) startY, pStart, (int) endX, (int) endY, pEnd, currentColorArgb);
            }
            needsRender = true;
        } catch (Throwable throwable) {
            AppLogger.error("Çizim hatası.", throwable);
        }
    }

    private void applyBucketAtPixel(double x, double y) {
        if (useBucketFillMH == null || engineHandle == null || pixelSegment == null) return;
        try {
            boolean filled = (boolean) useBucketFillMH.invokeExact(
                engineHandle, pixelSegment, canvasWidth, canvasHeight, (int) x, (int) y, currentColorArgb
            );
            if (filled) {
                needsRender = true;
                markDirty(true);
            }
        } catch (Throwable t) {
            AppLogger.error("Bucket fill hatası.", t);
        }
    }

    public void setCanvasSize(int width, int height) {
        allocateCanvasAndEngine(width, height, true);
    }

    private void allocateCanvasAndEngine(int width, int height, boolean clearAndSyncInitialState) {
        this.canvasWidth = Math.max(width, 64);
        this.canvasHeight = Math.max(height, 64);

        if (!createNativeEngine(canvasWidth, canvasHeight)) return;

        if (arena != null) arena.close();
        arena = Arena.ofShared();

        long totalBytes = (long) canvasWidth * canvasHeight * 4L;
        pixelSegment = arena.allocate(totalBytes, 4);

        IntBuffer intBuf = pixelSegment.asByteBuffer().order(ByteOrder.nativeOrder()).asIntBuffer();
        pixelBuffer = new PixelBuffer<>(canvasWidth, canvasHeight, intBuf, PixelFormat.getIntArgbPreInstance());

        canvasImageView.setImage(new WritableImage(pixelBuffer));
        canvasImageView.setViewport(new javafx.geometry.Rectangle2D(0, 0, canvasWidth, canvasHeight));

        paperStack.setPrefSize(canvasWidth, canvasHeight);
        paperStack.setMinSize(canvasWidth, canvasHeight);
        paperStack.setMaxSize(canvasWidth, canvasHeight);

        dynamicMaxZoom = Math.max(1.0, 8192.0 / Math.max(canvasWidth, canvasHeight));

        if (clearAndSyncInitialState) {
            if (clearCanvasMH != null && engineHandle != null) {
                try {
                    clearCanvasMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, 0xFFFFFFFF);
                    needsRender = true;
                } catch (Throwable t) {
                    AppLogger.error("Canvas temizlenemedi.", t);
                }
            }
            syncLayerUIFromNative();
            Platform.runLater(this::autoCenterCanvas);
        }
    }

    private void autoCenterCanvas() {
        if (userInteracted || paperStack.getPrefWidth() <= 0) return;
        double vw = infiniteWorkspace.getWidth();
        double vh = infiniteWorkspace.getHeight();
        if (vw <= 0 || vh <= 0) return;

        double cw = paperStack.getPrefWidth();
        double ch = paperStack.getPrefHeight();

        currentScale = Math.min(Math.min((vw - 100) / cw, (vh - 100) / ch), dynamicMaxZoom);
        translateX = (vw - (cw * currentScale)) / 2.0;
        translateY = (vh - (ch * currentScale)) / 2.0;
        updateMatrix();
    }

    private void togglePanel(VBox panel, boolean open, double offset) {
        TranslateTransition transition = new TranslateTransition(Duration.millis(250), panel);
        if (open) {
            panel.setVisible(true);
            transition.setToX(0);
        } else {
            transition.setToX(offset);
            transition.setOnFinished(event -> panel.setVisible(false));
        }
        transition.play();
    }

    private void loadCEngine() {
        try {
            String libraryPath = System.getProperty("user.dir") + "/src/main/cpp/build/libinkera_engine.so";
            System.load(libraryPath);
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup = SymbolLookup.loaderLookup();

            createEngineMH = linker.downcallHandle(lookup.find("createEngine").orElseThrow(), FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            destroyEngineMH = linker.downcallHandle(lookup.find("destroyEngine").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            beginStrokeSnapshotMH = linker.downcallHandle(lookup.find("beginStrokeSnapshot").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            undoActionMH = linker.downcallHandle(lookup.find("undoAction").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            redoActionMH = linker.downcallHandle(lookup.find("redoAction").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            clearCanvasMH = linker.downcallHandle(lookup.find("clearCanvas").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            setBrushSizeMH = linker.downcallHandle(lookup.find("setBrushSize").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            getBrushSizeMH = linker.downcallHandle(lookup.find("getBrushSize").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            addNewLayerMH = linker.downcallHandle(lookup.find("addNewLayer").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            setActiveLayerMH = linker.downcallHandle(lookup.find("setActiveLayer").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            usePencilMH = linker.downcallHandle(lookup.find("usePencil").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT));
            useEraserMH = linker.downcallHandle(lookup.find("useEraser").orElseThrow(), FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
            useBucketFillMH = linker.downcallHandle(lookup.find("useBucketFill").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            setLayerVisibilityMH = linker.downcallHandle(lookup.find("setLayerVisibility").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_BOOLEAN));
            setLayerOpacityMH = linker.downcallHandle(lookup.find("setLayerOpacity").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT));
            setLayerNameMH = linker.downcallHandle(lookup.find("setLayerName").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            deleteLayerMH = linker.downcallHandle(lookup.find("deleteLayer").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            moveLayerMH = linker.downcallHandle(lookup.find("moveLayer").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            savePageMH = linker.downcallHandle(lookup.find("savePage").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            loadPageMH = linker.downcallHandle(lookup.find("loadPage").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            getPageSizeMH = linker.downcallHandle(lookup.find("getPageSize").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            getLayerCountMH = linker.downcallHandle(lookup.find("getLayerCount").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            getActiveLayerIndexMH = linker.downcallHandle(lookup.find("getActiveLayerIndex").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            getLayerVisibilityMH = linker.downcallHandle(lookup.find("getLayerVisibility").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            getLayerOpacityMH = linker.downcallHandle(lookup.find("getLayerOpacity").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            copyLayerNameMH = linker.downcallHandle(lookup.find("copyLayerName").orElseThrow(), FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            AppLogger.info("Native engine FFM API başarıyla bağlandı.");
        } catch (Throwable t) {
            AppLogger.error("Native engine yüklenemedi.", t);
        }
    }

    private boolean createNativeEngine(int width, int height) {
        if (createEngineMH == null) return false;
        try {
            if (engineHandle != null) destroyNativeEngine();
            engineHandle = (MemorySegment) createEngineMH.invokeExact(width, height);
            if (engineHandle == null || engineHandle.address() == 0) return false;
            if (setBrushSizeMH != null) setBrushSizeMH.invokeExact(engineHandle, currentBrushSize);
            return true;
        } catch (Throwable t) {
            engineHandle = null;
            return false;
        }
    }

    private void destroyNativeEngine() {
        if (destroyEngineMH == null || engineHandle == null) return;
        try {
            destroyEngineMH.invokeExact(engineHandle);
        } catch (Throwable t) {
            AppLogger.error("Engine yok edilemedi.", t);
        }
        engineHandle = null;
    }

    private void commitLayerRenameToNative(int nativeIndex, String trimmedName) {
        if (engineHandle == null || setLayerNameMH == null || trimmedName == null || trimmedName.isEmpty()) {
            return;
        }
        try (Arena nameArena = Arena.ofConfined()) {
            MemorySegment nameSeg = nameArena.allocateFrom(trimmedName);
            boolean ok = (boolean) setLayerNameMH.invokeExact(engineHandle, nativeIndex, nameSeg);
            if (ok) {
                syncLayerUIFromNative();
                markDirty(true);
                AppLogger.info("Katman yeniden adlandırıldı: nativeIndex=" + nativeIndex + " -> '" + trimmedName + "'");
            }
        } catch (Throwable t) {
            AppLogger.error("Katman adı değiştirilemedi.", t);
        }
    }

    private void syncLayerUIFromNative() {
        if (engineHandle == null || getLayerCountMH == null || layerListView == null) return;
        isSyncingLayers = true;
        try {
            int nativeLayerCount = (int) getLayerCountMH.invokeExact(engineHandle);
            int nativeActiveIndex = (int) getActiveLayerIndexMH.invokeExact(engineHandle);

            layerListView.getItems().clear();
            LayerData itemToSelect = null;

            for (int i = nativeLayerCount - 1; i >= 0; i--) {
                boolean visible = (boolean) getLayerVisibilityMH.invokeExact(engineHandle, i);
                float opacity = (float) getLayerOpacityMH.invokeExact(engineHandle, i);
                String layerName = readNativeLayerName(i);
                if (layerName.isEmpty()) layerName = "Katman " + (i + 1);

                LayerData layer = new LayerData(i, layerName, visible, opacity);
                layerListView.getItems().add(layer);
                if (i == nativeActiveIndex) itemToSelect = layer;
            }

            if (itemToSelect != null) layerListView.getSelectionModel().select(itemToSelect);
        } catch (Throwable t) {
            AppLogger.error("Layer UI senkronize edilemedi.", t);
        } finally {
            isSyncingLayers = false;
        }
    }

    private String readNativeLayerName(int nativeIndex) {
        if (engineHandle == null || copyLayerNameMH == null) return "";
        try (Arena nameArena = Arena.ofConfined()) {
            MemorySegment outputBuffer = nameArena.allocate(512);
            int copied = (int) copyLayerNameMH.invokeExact(engineHandle, nativeIndex, outputBuffer, 512);
            if (copied <= 0) return "";
            return outputBuffer.getString(0, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        }
    }

    public void openOrCreatePage(Path pageFilePath, int defaultWidth, int defaultHeight) {
        if (pageFilePath == null) {
            setCanvasSize(defaultWidth, defaultHeight);
            return;
        }
        this.currentPagePath = pageFilePath.toAbsolutePath().normalize();
        if (Files.exists(this.currentPagePath)) {
            loadPageFromFile(this.currentPagePath);
        } else {
            setCanvasSize(defaultWidth, defaultHeight);
            saveCurrentPage();
        }
    }

    private void promptAndOpenPage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("InkEra Sayfası Aç (.inkpage)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("InkEra Page (*.inkpage)", "*.inkpage"));
        File f = chooser.showOpenDialog(infiniteWorkspace.getScene().getWindow());
        if (f != null) loadPageFromFile(f.toPath());
    }

    public boolean loadPageFromFile(Path filePath) {
        if (loadPageMH == null || getPageSizeMH == null) return false;
        try (Arena pathArena = Arena.ofConfined()) {
            MemorySegment pathSegment = pathArena.allocateFrom(filePath.toString());
            long packedSize = (long) getPageSizeMH.invokeExact(pathSegment);
            if (packedSize == 0L) return false;

            int fw = (int) (packedSize >>> 32);
            int fh = (int) (packedSize & 0xFFFFFFFFL);

            allocateCanvasAndEngine(fw, fh, false);
            boolean loaded = (boolean) loadPageMH.invokeExact(engineHandle, pixelSegment, canvasWidth, canvasHeight, pathSegment);
            if (!loaded) return false;

            this.currentPagePath = filePath.toAbsolutePath().normalize();
            this.needsRender = true;
            markDirty(false);
            syncLayerUIFromNative();
            ensureThumbnailExists(this.currentPagePath);
            Platform.runLater(this::autoCenterCanvas);
            return true;
        } catch (Throwable t) {
            AppLogger.error("Sayfa yüklenemedi.", t);
            return false;
        }
    }

    public void saveCurrentPage() {
        if (savePageMH == null || engineHandle == null) return;
        try {
            Path target = currentPagePath;
            if (target == null) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Sayfayı Kaydet");
                chooser.setInitialFileName("page.inkpage");
                chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("InkEra Page (*.inkpage)", "*.inkpage"));
                File selected = chooser.showSaveDialog(infiniteWorkspace.getScene().getWindow());
                if (selected == null) return;
                target = selected.toPath().toAbsolutePath().normalize();
            }
            if (target.getParent() != null) Files.createDirectories(target.getParent());

            try (Arena pathArena = Arena.ofConfined()) {
                MemorySegment pathSegment = pathArena.allocateFrom(target.toString());
                boolean success = (boolean) savePageMH.invokeExact(engineHandle, pathSegment);
                if (success) {
                    currentPagePath = target;
                    markDirty(false);
                    exportPageThumbnail(target);
                }
            }
        } catch (Throwable t) {
            AppLogger.error("Kayıt hatası.", t);
        }
    }

    public void exportCurrentCanvasAsPng() {
        if (pixelSegment == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("PNG Olarak Dışa Aktar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Image (*.png)", "*.png"));
        File selected = chooser.showSaveDialog(infiniteWorkspace.getScene().getWindow());
        if (selected != null) {
            ThumbnailGenerator.exportFullResolutionPng(pixelSegment, canvasWidth, canvasHeight, selected.toPath());
        }
    }

    private void ensureThumbnailExists(Path path) { exportPageThumbnail(path); }
    private void exportPageThumbnail(Path path) {
        if (pixelSegment == null || path.getParent() == null) return;
        Path thumb = path.getParent().resolve("preview.png");
        ThumbnailGenerator.saveThumbnailPng(pixelSegment, canvasWidth, canvasHeight, 360, thumb);
    }

    public void dispose() {
        if (renderTimer != null) { renderTimer.stop(); renderTimer = null; }
        destroyNativeEngine();
        if (arena != null) { arena.close(); arena = null; }
    }
}
