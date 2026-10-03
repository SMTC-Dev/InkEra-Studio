package com.inkera.ui.workspace;

import com.inkera.util.AppLogger;

import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.IntBuffer;
import java.nio.file.Path;
import java.util.ResourceBundle;

public class WorkspaceController implements Initializable {

    /* ============================================================
     * LAYER DATA
     * ============================================================ */

    public static class LayerData {
        public String name;
        public boolean visible = true;
        public float opacity = 1.0f;

        public LayerData(String name) {
            this.name = name;
        }

        public LayerData(
            String name,
            boolean visible,
            float opacity
        ) {
            this.name = name;
            this.visible = visible;
            this.opacity = opacity;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /* ============================================================
     * FXML
     * ============================================================ */

    @FXML private Button leftMenuBtn;
    @FXML private Button rightMenuBtn;

    @FXML private VBox leftFloatingPanel;
    @FXML private VBox rightFloatingPanel;

    @FXML private Pane infiniteWorkspace;
    @FXML private StackPane paperStack;
    @FXML private ImageView canvasImageView;

    @FXML private ListView<LayerData> layerListView;
    @FXML private Button addLayerBtn;

    /* ============================================================
     * UI STATE
     * ============================================================ */

    private boolean isLeftOpen = false;
    private boolean isRightOpen = false;
    private boolean userInteracted = false;

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

    /* ============================================================
     * RENDER STATE
     * ============================================================ */

    private boolean needsRender = false;

    private AnimationTimer renderTimer;

    /* ============================================================
     * JAVA SHARED MEMORY
     * ============================================================ */

    private Arena arena;

    private MemorySegment pixelSegment;

    private PixelBuffer<IntBuffer> pixelBuffer;

    /* ============================================================
     * NATIVE ENGINE INSTANCE
     * ============================================================ */

    /*
     * Artık LayerManager global değil.
     *
     * Her WorkspaceController kendi EngineHandle'ına sahip.
     */
    private MemorySegment engineHandle;

    private MethodHandle createEngineMH;
    private MethodHandle destroyEngineMH;

    private MethodHandle clearCanvasMH;
    private MethodHandle usePencilMH;
    private MethodHandle addNewLayerMH;
    private MethodHandle setActiveLayerMH;
    private MethodHandle setLayerVisibilityMH;
    private MethodHandle deleteLayerMH;
    private MethodHandle moveLayerMH;

    private MethodHandle savePageMH;
    private MethodHandle loadPageMH;
    private MethodHandle getPageSizeMH;

    private MethodHandle getLayerCountMH;
    private MethodHandle getActiveLayerIndexMH;
    private MethodHandle getLayerVisibilityMH;
    private MethodHandle getLayerOpacityMH;
    private MethodHandle copyLayerNameMH;

    /* ============================================================
     * PAGE PATH
     * ============================================================ */

    private Path currentPagePath;

    /* ============================================================
     * INITIALIZATION
     * ============================================================ */

    @Override
    public void initialize(
        URL location,
        ResourceBundle resources
    ) {

        AppLogger.info(
            "WorkspaceController başlatılıyor."
        );

        setupUI();

        paperStack.getTransforms().add(
            affineTransform
        );

        setupZoomAndPanEngine();

        loadCEngine();

        setupKeyboardShortcuts();

        setupDrawingEngine();

        setupLayerUI();

        setupRenderEngine();

        /*
         * Workspace penceresi kapandığında native Engine ve
         * Java Arena temizlenecek.
         */
        infiniteWorkspace.sceneProperty().addListener(
            (observable, oldScene, newScene) -> {

                if (newScene == null) {
                    return;
                }

                newScene.windowProperty().addListener(
                    (windowObservable, oldWindow, newWindow) -> {

                        if (newWindow == null) {
                            return;
                        }

                        newWindow.setOnHidden(
                            event -> dispose()
                        );
                    }
                );
            }
        );

        infiniteWorkspace.widthProperty().addListener(
            (observable, oldValue, newValue) ->
                autoCenterCanvas()
        );

        infiniteWorkspace.heightProperty().addListener(
            (observable, oldValue, newValue) ->
                autoCenterCanvas()
        );
    }

    /* ============================================================
     * BASIC UI
     * ============================================================ */

    private void setupUI() {

        leftFloatingPanel.setTranslateX(-280);
        leftFloatingPanel.setVisible(false);

        rightFloatingPanel.setTranslateX(280);
        rightFloatingPanel.setVisible(false);

        leftMenuBtn.setOnAction(
            event ->
                togglePanel(
                    leftFloatingPanel,
                    isLeftOpen = !isLeftOpen,
                    -280
                )
        );

        rightMenuBtn.setOnAction(
            event ->
                togglePanel(
                    rightFloatingPanel,
                    isRightOpen = !isRightOpen,
                    280
                )
        );

        Rectangle clipRect = new Rectangle();

        clipRect.widthProperty().bind(
            infiniteWorkspace.widthProperty()
        );

        clipRect.heightProperty().bind(
            infiniteWorkspace.heightProperty()
        );

        infiniteWorkspace.setClip(clipRect);
    }

    /* ============================================================
     * RENDER ENGINE
     * ============================================================ */

    private void setupRenderEngine() {

        renderTimer = new AnimationTimer() {

            @Override
            public void handle(long now) {

                if (needsRender &&
                    pixelBuffer != null) {

                    pixelBuffer.updateBuffer(
                        buffer -> null
                    );

                    needsRender = false;
                }
            }
        };

        renderTimer.start();
    }

    /* ============================================================
     * LAYER UI
     * ============================================================ */

    private void setupLayerUI() {

        layerListView.setCellFactory(
            listView -> new ListCell<>() {

                private final HBox root =
                    new HBox(10);

                private final Button visibilityBtn =
                    new Button("👁");

                private final Label nameLabel =
                    new Label();

                private final Button deleteBtn =
                    new Button("🗑");

                private final Region spacer =
                    new Region();

                {
                    HBox.setHgrow(
                        spacer,
                        Priority.ALWAYS
                    );

                    root.setAlignment(
                        Pos.CENTER_LEFT
                    );

                    visibilityBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-text-fill: #aaa;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 5 0 0;"
                    );

                    deleteBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-text-fill: #ff5555;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 0 0 5;"
                    );

                    nameLabel.setStyle(
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 13px;"
                    );

                    root.getChildren().addAll(
                        visibilityBtn,
                        nameLabel,
                        spacer,
                        deleteBtn
                    );

                    root.setStyle(
                        "-fx-padding: 5;"
                    );

                    visibilityBtn.setOnAction(
                        event -> {

                            LayerData item =
                                getItem();

                            if (item == null) {
                                return;
                            }

                            item.visible =
                                !item.visible;

                            visibilityBtn.setText(
                                item.visible
                                    ? "👁"
                                    : "🕶"
                            );

                            nameLabel.setOpacity(
                                item.visible
                                    ? 1.0
                                    : 0.5
                            );

                            triggerCPPAction(
                                setLayerVisibilityMH,
                                getIndex(),
                                item.visible
                            );
                        }
                    );

                    deleteBtn.setOnAction(
                        event -> {

                            if (getItem() == null) {
                                return;
                            }

                            if (getListView()
                                    .getItems()
                                    .size() <= 1) {
                                return;
                            }

                            int index =
                                getIndex();

                            triggerCPPAction(
                                deleteLayerMH,
                                index
                            );

                            getListView()
                                .getItems()
                                .remove(getItem());
                        }
                    );

                    /* --------------------------------------------
                     * DRAG & DROP
                     * -------------------------------------------- */

                    setOnDragDetected(
                        event -> {

                            if (getItem() == null) {
                                return;
                            }

                            Dragboard db =
                                startDragAndDrop(
                                    TransferMode.MOVE
                                );

                            ClipboardContent content =
                                new ClipboardContent();

                            content.putString(
                                String.valueOf(
                                    getIndex()
                                )
                            );

                            db.setContent(content);

                            event.consume();
                        }
                    );

                    setOnDragOver(
                        event -> {

                            if (
                                event.getGestureSource()
                                    != this
                                &&
                                event.getDragboard()
                                    .hasString()
                            ) {

                                event.acceptTransferModes(
                                    TransferMode.MOVE
                                );

                                setStyle(
                                    "-fx-border-color: #66afeb;" +
                                    "-fx-border-width: 0 0 2 0;"
                                );
                            }

                            event.consume();
                        }
                    );

                    setOnDragExited(
                        event ->
                            setStyle(
                                "-fx-border-width: 0;"
                            )
                    );

                    setOnDragDropped(
                        event -> {

                            Dragboard db =
                                event.getDragboard();

                            if (!db.hasString()) {

                                event.setDropCompleted(
                                    false
                                );

                                event.consume();
                                return;
                            }

                            int fromIdx =
                                Integer.parseInt(
                                    db.getString()
                                );

                            int toIdx =
                                getIndex();

                            if (fromIdx != toIdx) {

                                LayerData movingItem =
                                    getListView()
                                        .getItems()
                                        .get(fromIdx);

                                getListView()
                                    .getItems()
                                    .remove(fromIdx);

                                getListView()
                                    .getItems()
                                    .add(
                                        toIdx,
                                        movingItem
                                    );

                                getListView()
                                    .getSelectionModel()
                                    .select(toIdx);

                                triggerCPPMove(
                                    fromIdx,
                                    toIdx
                                );
                            }

                            event.setDropCompleted(
                                true
                            );

                            event.consume();
                        }
                    );
                }

                @Override
                protected void updateItem(
                    LayerData item,
                    boolean empty
                ) {

                    super.updateItem(
                        item,
                        empty
                    );

                    if (empty || item == null) {

                        setGraphic(null);

                    } else {

                        nameLabel.setText(
                            item.name
                        );

                        visibilityBtn.setText(
                            item.visible
                                ? "👁"
                                : "🕶"
                        );

                        nameLabel.setOpacity(
                            item.visible
                                ? 1.0
                                : 0.5
                        );

                        setGraphic(root);
                    }
                }
            }
        );

        layerListView
            .getSelectionModel()
            .selectedIndexProperty()
            .addListener(
                (observable, oldValue, newValue) -> {

                    if (
                        newValue != null &&
                        newValue.intValue() >= 0 &&
                        engineHandle != null
                    ) {

                        try {

                            setActiveLayerMH.invokeExact(
                                engineHandle,
                                newValue.intValue()
                            );

                        } catch (Throwable throwable) {

                            AppLogger.error(
                                "Aktif katman native motora aktarılamadı.",
                                throwable
                            );
                        }
                    }
                }
            );

        addLayerBtn.setOnAction(
            event -> {

                if (engineHandle == null ||
                    pixelSegment == null) {
                    return;
                }

                int newIndex =
                    layerListView
                        .getItems()
                        .size();

                try {

                    int width =
                        Math.max(
                            (int) paperStack.getPrefWidth(),
                            64
                        );

                    int height =
                        Math.max(
                            (int) paperStack.getPrefHeight(),
                            64
                        );

                    addNewLayerMH.invokeExact(
                        engineHandle,
                        pixelSegment,
                        width,
                        height
                    );

                    layerListView
                        .getItems()
                        .add(
                            new LayerData(
                                "Yeni Katman"
                            )
                        );

                    layerListView
                        .getSelectionModel()
                        .selectLast();

                    needsRender = true;

                    AppLogger.info(
                        "Yeni katman oluşturuldu: "
                        + newIndex
                    );

                } catch (Throwable throwable) {

                    AppLogger.error(
                        "Yeni katman oluşturulamadı.",
                        throwable
                    );
                }
            }
        );
    }

    /* ============================================================
     * COMMON NATIVE HELPERS
     * ============================================================ */

    private void triggerCPPAction(
        MethodHandle handle,
        int index
    ) {

        if (
            handle == null ||
            engineHandle == null ||
            pixelSegment == null
        ) {
            return;
        }

        try {

            int width =
                Math.max(
                    (int) paperStack.getPrefWidth(),
                    64
                );

            int height =
                Math.max(
                    (int) paperStack.getPrefHeight(),
                    64
                );

            handle.invokeExact(
                engineHandle,
                pixelSegment,
                width,
                height,
                index
            );

            needsRender = true;

        } catch (Throwable throwable) {

            AppLogger.error(
                "Native layer işlemi başarısız.",
                throwable
            );
        }
    }

    private void triggerCPPAction(
        MethodHandle handle,
        int index,
        boolean flag
    ) {

        if (
            handle == null ||
            engineHandle == null ||
            pixelSegment == null
        ) {
            return;
        }

        try {

            int width =
                Math.max(
                    (int) paperStack.getPrefWidth(),
                    64
                );

            int height =
                Math.max(
                    (int) paperStack.getPrefHeight(),
                    64
                );

            handle.invokeExact(
                engineHandle,
                pixelSegment,
                width,
                height,
                index,
                flag
            );

            needsRender = true;

        } catch (Throwable throwable) {

            AppLogger.error(
                "Native layer işlemi başarısız.",
                throwable
            );
        }
    }

    private void triggerCPPMove(
        int fromIndex,
        int toIndex
    ) {

        if (
            moveLayerMH == null ||
            engineHandle == null ||
            pixelSegment == null
        ) {
            return;
        }

        try {

            int width =
                Math.max(
                    (int) paperStack.getPrefWidth(),
                    64
                );

            int height =
                Math.max(
                    (int) paperStack.getPrefHeight(),
                    64
                );

            moveLayerMH.invokeExact(
                engineHandle,
                pixelSegment,
                width,
                height,
                fromIndex,
                toIndex
            );

            needsRender = true;

        } catch (Throwable throwable) {

            AppLogger.error(
                "Native katman taşıma işlemi başarısız.",
                throwable
            );
        }
    }

    /* ============================================================
     * NATIVE ENGINE LOADING
     * ============================================================ */

    private void loadCEngine() {

        try {

            String libraryPath =
                System.getProperty("user.dir")
                + "/src/main/cpp/build/libinkera_engine.so";

            AppLogger.info(
                "Native engine yükleniyor: "
                + libraryPath
            );

            System.load(libraryPath);

            Linker linker =
                Linker.nativeLinker();

            SymbolLookup lookup =
                SymbolLookup.loaderLookup();

            createEngineMH =
                linker.downcallHandle(
                    lookup.find("createEngine").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            destroyEngineMH =
                linker.downcallHandle(
                    lookup.find("destroyEngine").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS
                    )
                );

            clearCanvasMH =
                linker.downcallHandle(
                    lookup.find("clearCanvas").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            addNewLayerMH =
                linker.downcallHandle(
                    lookup.find("addNewLayer").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            setActiveLayerMH =
                linker.downcallHandle(
                    lookup.find("setActiveLayer").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            usePencilMH =
                linker.downcallHandle(
                    lookup.find("usePencil").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            setLayerVisibilityMH =
                linker.downcallHandle(
                    lookup.find("setLayerVisibility").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_BOOLEAN
                    )
                );

            deleteLayerMH =
                linker.downcallHandle(
                    lookup.find("deleteLayer").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            moveLayerMH =
                linker.downcallHandle(
                    lookup.find("moveLayer").get(),
                    java.lang.foreign.FunctionDescriptor.ofVoid(
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    )
                );

            getLayerCountMH =
                linker.downcallHandle(
                    lookup.find("getLayerCount").get(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                    )
                );

            getActiveLayerIndexMH =
                linker.downcallHandle(
                    lookup.find("getActiveLayerIndex").get(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                    )
                );

            getLayerVisibilityMH =
                linker.downcallHandle(
                    lookup.find("getLayerVisibility").get(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            getLayerOpacityMH =
                linker.downcallHandle(
                    lookup.find("getLayerOpacity").get(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_FLOAT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            copyLayerNameMH =
                linker.downcallHandle(
                    lookup.find("copyLayerName").get(),
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            savePageMH =
                linker.downcallHandle(
                    lookup.find("savePage").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS
                    )
                );

            loadPageMH =
                linker.downcallHandle(
                    lookup.find("loadPage").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                    )
                );

            getPageSizeMH =
                linker.downcallHandle(
                    lookup.find("getPageSize").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS
                    )
                );

            getLayerCountMH =
                linker.downcallHandle(
                    lookup.find("getLayerCount").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                    )
                );

            getActiveLayerIndexMH =
                linker.downcallHandle(
                    lookup.find("getActiveLayerIndex").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS
                    )
                );

            getLayerVisibilityMH =
                linker.downcallHandle(
                    lookup.find("getLayerVisibility").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_BOOLEAN,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            getLayerOpacityMH =
                linker.downcallHandle(
                    lookup.find("getLayerOpacity").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_FLOAT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            copyLayerNameMH =
                linker.downcallHandle(
                    lookup.find("copyLayerName").get(),
                    java.lang.foreign.FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT
                    )
                );

            AppLogger.info(
                "Native engine API başarıyla bağlandı."
            );

        } catch (Throwable throwable) {

            AppLogger.error(
                "Native engine yüklenemedi.",
                throwable
            );
        }
    }

    /* ============================================================
     * ENGINE INSTANCE
     * ============================================================ */

    private boolean createNativeEngine(
        int width,
        int height
    ) {

        if (createEngineMH == null) {

            AppLogger.warn(
                "Engine oluşturulamıyor: createEngine bağlı değil."
            );

            return false;
        }

        try {

            if (engineHandle != null) {
                destroyNativeEngine();
            }

            engineHandle =
                (MemorySegment) createEngineMH.invokeExact(
                    width,
                    height
                );

            if (engineHandle == null ||
                engineHandle.address() == 0) {

                engineHandle = null;

                AppLogger.error(
                    "Native Engine oluşturulamadı.",
                    new IllegalStateException(
                        "createEngine() NULL döndürdü."
                    )
                );

                return false;
            }

            AppLogger.info(
                "Native Engine instance oluşturuldu: "
                + width
                + "x"
                + height
            );

            return true;

        } catch (Throwable throwable) {

            engineHandle = null;

            AppLogger.error(
                "Native Engine instance oluşturulurken hata oluştu.",
                throwable
            );

            return false;
        }
    }

    private void destroyNativeEngine() {

        if (
            destroyEngineMH == null ||
            engineHandle == null
        ) {
            return;
        }

        try {

            destroyEngineMH.invokeExact(
                engineHandle
            );

            AppLogger.info(
                "Native Engine instance yok edildi."
            );

        } catch (Throwable throwable) {

            AppLogger.error(
                "Native Engine yok edilirken hata oluştu.",
                throwable
            );
        }

        engineHandle = null;
    }

    /* ============================================================
     * KEYBOARD SHORTCUTS
     * ============================================================ */

    private void setupKeyboardShortcuts() {

        Platform.runLater(() -> {

            if (infiniteWorkspace.getScene() == null) {
                return;
            }

            infiniteWorkspace.getScene().addEventFilter(
                KeyEvent.KEY_PRESSED,
                event -> {

                    if (
                        event.isControlDown() &&
                        event.getCode() == KeyCode.S
                    ) {

                        event.consume();

                        saveCurrentPage();
                    }
                }
            );
        });
    }

    /* ============================================================
     * PAGE SAVE
     * ============================================================ */

    private void saveCurrentPage() {

        if (savePageMH == null) {

            AppLogger.warn(
                "Ctrl+S: savePage native fonksiyonu bağlı değil."
            );

            return;
        }

        if (engineHandle == null) {

            AppLogger.warn(
                "Ctrl+S: Native Engine mevcut değil."
            );

            return;
        }

        if (infiniteWorkspace.getScene() == null) {

            AppLogger.warn(
                "Ctrl+S: Workspace Scene henüz hazır değil."
            );

            return;
        }

        try {

            Path target =
                currentPagePath;

            if (target == null) {

                FileChooser chooser =
                    new FileChooser();

                chooser.setTitle(
                    "InkEra Sayfasını Kaydet"
                );

                chooser.setInitialFileName(
                    "page.inkpage"
                );

                chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                        "InkEra Page (*.inkpage)",
                        "*.inkpage"
                    )
                );

                java.io.File selectedFile =
                    chooser.showSaveDialog(
                        infiniteWorkspace
                            .getScene()
                            .getWindow()
                    );

                if (selectedFile == null) {

                    AppLogger.debug(
                        "Ctrl+S: Kaydetme iptal edildi."
                    );

                    return;
                }

                String absolutePath =
                    selectedFile.getAbsolutePath();

                if (
                    !absolutePath
                        .toLowerCase()
                        .endsWith(".inkpage")
                ) {

                    absolutePath += ".inkpage";
                }

                target =
                    Path.of(
                        absolutePath
                    );
            }

            target =
                target
                    .toAbsolutePath()
                    .normalize();

            AppLogger.info(
                "Sayfa kaydediliyor: "
                + target
            );

            try (Arena pathArena =
                    Arena.ofConfined()) {

                MemorySegment pathSegment =
                    pathArena.allocateFrom(
                        target.toString()
                    );

                boolean success =
                    (boolean)
                    savePageMH.invokeExact(
                        engineHandle,
                        pathSegment
                    );

                if (success) {

                    currentPagePath =
                        target;

                    AppLogger.info(
                        "Sayfa başarıyla kaydedildi: "
                        + target
                    );

                } else {

                    AppLogger.warn(
                        "Sayfa kaydedilemedi: "
                        + target
                    );
                }
            }

        } catch (Throwable throwable) {

            AppLogger.error(
                "Ctrl+S sırasında hata oluştu.",
                throwable
            );
        }
    }

    private void syncLayerUIFromNative() {

        if (
            engineHandle == null ||
            getLayerCountMH == null ||
            getActiveLayerIndexMH == null ||
            getLayerVisibilityMH == null ||
            getLayerOpacityMH == null ||
            copyLayerNameMH == null ||
            layerListView == null
        ) {
            AppLogger.warn(
                "Layer senkronizasyonu atlandı: Native state hazır değil."
            );
            return;
        }

        try {

            int nativeLayerCount =
                (int) getLayerCountMH.invokeExact(
                    engineHandle
                );

            int nativeActiveIndex =
                (int) getActiveLayerIndexMH.invokeExact(
                    engineHandle
                );

            AppLogger.info(
                "Native layer state okunuyor: "
                + nativeLayerCount
                + " katman, aktif="
                + nativeActiveIndex
            );

            layerListView.getItems().clear();

            for (
                int i = 0;
                i < nativeLayerCount;
                i++
            ) {

                boolean visible =
                    (boolean) getLayerVisibilityMH.invokeExact(
                        engineHandle,
                        i
                    );

                float opacity =
                    (float) getLayerOpacityMH.invokeExact(
                        engineHandle,
                        i
                    );

                String layerName =
                    readNativeLayerName(i);

                if (
                    layerName == null ||
                    layerName.isEmpty()
                ) {
                    layerName = "Katman " + (i + 1);
                }

                LayerData layer =
                    new LayerData(
                        layerName,
                        visible,
                        opacity
                    );

                layerListView
                    .getItems()
                    .add(layer);

                AppLogger.debug(
                    "Native layer -> Java UI: index="
                    + i
                    + " name="
                    + layerName
                    + " visible="
                    + visible
                    + " opacity="
                    + opacity
                );
            }

            if (
                nativeActiveIndex >= 0 &&
                nativeActiveIndex < nativeLayerCount
            ) {
                layerListView
                    .getSelectionModel()
                    .select(nativeActiveIndex);
            } else {
                layerListView
                    .getSelectionModel()
                    .clearSelection();
            }

            AppLogger.info(
                "Layer UI native state ile senkronlandı."
            );

        } catch (Throwable t) {

            AppLogger.error(
                "Native layer state okunamadı."
            );

            t.printStackTrace();
        }
    }

    private String readNativeLayerName(int index) {

        if (
            engineHandle == null ||
            copyLayerNameMH == null
        ) {
            return "";
        }

        try (Arena nameArena = Arena.ofConfined()) {

            final int BUFFER_SIZE = 512;

            MemorySegment outputBuffer =
                nameArena.allocate(BUFFER_SIZE);

            int copiedBytes =
                (int) copyLayerNameMH.invokeExact(
                    engineHandle,
                    index,
                    outputBuffer,
                    BUFFER_SIZE
                );

            if (copiedBytes <= 0) {
                return "";
            }

            return outputBuffer.getString(
                0,
                StandardCharsets.UTF_8
            );

        } catch (Throwable t) {

            AppLogger.error(
                "Layer adı okunamadı: index="
                + index
            );

            t.printStackTrace();

            return "";
        }
    }

    /* ============================================================
     * DRAWING
     * ============================================================ */

    private void setupDrawingEngine() {

        canvasImageView.setOnMousePressed(
            event -> {

                if (event.isPrimaryButtonDown()) {

                    lastDrawX =
                        event.getX();

                    lastDrawY =
                        event.getY();

                    drawAtPixel(
                        lastDrawX,
                        lastDrawY,
                        event.getX(),
                        event.getY()
                    );
                }
            }
        );

        canvasImageView.setOnMouseDragged(
            event -> {

                if (
                    event.isPrimaryButtonDown() &&
                    lastDrawX != -1 &&
                    lastDrawY != -1
                ) {

                    drawAtPixel(
                        lastDrawX,
                        lastDrawY,
                        event.getX(),
                        event.getY()
                    );

                    lastDrawX =
                        event.getX();

                    lastDrawY =
                        event.getY();
                }
            }
        );

        canvasImageView.setOnMouseReleased(
            event -> {

                lastDrawX = -1;
                lastDrawY = -1;
            }
        );
    }

    private void drawAtPixel(
        double startX,
        double startY,
        double endX,
        double endY
    ) {

        if (
            usePencilMH == null ||
            engineHandle == null ||
            pixelSegment == null
        ) {
            return;
        }

        try {

            int width =
                Math.max(
                    (int) paperStack.getPrefWidth(),
                    64
                );

            int height =
                Math.max(
                    (int) paperStack.getPrefHeight(),
                    64
                );

            usePencilMH.invokeExact(
                engineHandle,
                pixelSegment,
                width,
                height,
                (int) startX,
                (int) startY,
                (int) endX,
                (int) endY,
                0xFF000000
            );

            needsRender = true;

        } catch (Throwable throwable) {

            AppLogger.error(
                "Pencil native çağrısı başarısız.",
                throwable
            );
        }
    }

    /* ============================================================
     * ZOOM / PAN
     * ============================================================ */

    private void updateMatrix() {

        affineTransform.setToIdentity();

        affineTransform.appendTranslation(
            translateX,
            translateY
        );

        affineTransform.appendScale(
            currentScale,
            currentScale
        );
    }

    private void setupZoomAndPanEngine() {

        infiniteWorkspace.setOnScroll(
            event -> {

                event.consume();

                if (event.getDeltaY() == 0) {
                    return;
                }

                userInteracted = true;

                double scaleMultiplier =
                    event.getDeltaY() > 0
                        ? ZOOM_FACTOR
                        : 1 / ZOOM_FACTOR;

                double newScale =
                    Math.max(
                        0.05,
                        Math.min(
                            currentScale *
                                scaleMultiplier,
                            dynamicMaxZoom
                        )
                    );

                translateX =
                    event.getX()
                    -
                    (
                        (event.getX() - translateX)
                        / currentScale
                        * newScale
                    );

                translateY =
                    event.getY()
                    -
                    (
                        (event.getY() - translateY)
                        / currentScale
                        * newScale
                    );

                currentScale =
                    newScale;

                updateMatrix();
            }
        );

        infiniteWorkspace.setOnMousePressed(
            event -> {

                if (
                    event.isMiddleButtonDown()
                ) {

                    userInteracted = true;

                    infiniteWorkspace.setStyle(
                        "-fx-background-color: #2e2e2e;" +
                        "-fx-cursor: closed_hand;"
                    );

                    lastPanX =
                        event.getX();

                    lastPanY =
                        event.getY();
                }
            }
        );

        infiniteWorkspace.setOnMouseDragged(
            event -> {

                if (
                    event.isMiddleButtonDown()
                ) {

                    translateX +=
                        event.getX()
                        -
                        lastPanX;

                    translateY +=
                        event.getY()
                        -
                        lastPanY;

                    lastPanX =
                        event.getX();

                    lastPanY =
                        event.getY();

                    updateMatrix();
                }
            }
        );

        infiniteWorkspace.setOnMouseReleased(
            event ->
                infiniteWorkspace.setStyle(
                    "-fx-background-color: #2e2e2e;" +
                    "-fx-cursor: open_hand;"
                )
        );
    }

    /* ============================================================
     * CANVAS CREATION
     * ============================================================ */

    public void setCanvasSize(
        int width,
        int height
    ) {

        AppLogger.info(
            "Canvas hazırlanıyor: "
            + width
            + "x"
            + height
        );

        /*
         * Yeni Workspace için yeni Engine.
         *
         * Önceki mimaride clearCanvas global LayerManager'ı
         * değiştiriyordu. Artık her Workspace kendine ait
         * Engine instance'ı oluşturuyor.
         */
        if (!createNativeEngine(
                width,
                height
            )) {

            return;
        }

        if (arena != null) {
            arena.close();
        }

        arena =
            Arena.ofShared();

        int bufferWidth =
            Math.max(width, 64);

        int bufferHeight =
            Math.max(height, 64);

        pixelSegment =
            arena.allocate(
                (long)
                    bufferWidth
                    *
                    bufferHeight
                    *
                    4,
                4
            );

        IntBuffer intBuf =
            pixelSegment
                .asByteBuffer()
                .order(
                    ByteOrder.nativeOrder()
                )
                .asIntBuffer();

        pixelBuffer =
            new PixelBuffer<>(
                bufferWidth,
                bufferHeight,
                intBuf,
                PixelFormat.getIntArgbPreInstance()
            );

        canvasImageView.setImage(
            new WritableImage(
                pixelBuffer
            )
        );

        canvasImageView.setViewport(
            new javafx.geometry.Rectangle2D(
                0,
                0,
                width,
                height
            )
        );

        paperStack.setPrefSize(
            width,
            height
        );

        paperStack.setMinSize(
            width,
            height
        );

        paperStack.setMaxSize(
            width,
            height
        );

        dynamicMaxZoom =
            Math.max(
                1.0,
                8192.0 /
                    Math.max(
                        width,
                        height
                    )
            );

        if (
            clearCanvasMH != null &&
            engineHandle != null &&
            pixelSegment != null
        ) {

            try {

                clearCanvasMH.invokeExact(
                    engineHandle,
                    pixelSegment,
                    bufferWidth,
                    bufferHeight,
                    0xFFFFFFFF
                );

                needsRender = true;

            } catch (Throwable throwable) {

                AppLogger.error(
                    "Canvas temizlenemedi.",
                    throwable
                );
            }
        }

        Platform.runLater(
            this::autoCenterCanvas
        );
    }

    /* ============================================================
     * AUTO CENTER
     * ============================================================ */

    private void autoCenterCanvas() {

        if (
            userInteracted ||
            paperStack.getPrefWidth() <= 0
        ) {
            return;
        }

        double vw =
            infiniteWorkspace.getWidth();

        double vh =
            infiniteWorkspace.getHeight();

        if (
            vw <= 0 ||
            vh <= 0
        ) {
            return;
        }

        double cw =
            paperStack.getPrefWidth();

        double ch =
            paperStack.getPrefHeight();

        currentScale =
            Math.min(
                Math.min(
                    (vw - 100) / cw,
                    (vh - 100) / ch
                ),
                dynamicMaxZoom
            );

        translateX =
            (
                vw -
                (cw * currentScale)
            ) / 2.0;

        translateY =
            (
                vh -
                (ch * currentScale)
            ) / 2.0;

        updateMatrix();
    }

    /* ============================================================
     * PANEL TOGGLE
     * ============================================================ */

    private void togglePanel(
        VBox panel,
        boolean open,
        double offset
    ) {

        TranslateTransition transition =
            new TranslateTransition(
                Duration.millis(250),
                panel
            );

        if (open) {

            panel.setVisible(true);
            transition.setToX(0);

        } else {

            transition.setToX(offset);

            transition.setOnFinished(
                event ->
                    panel.setVisible(false)
            );
        }

        transition.play();
    }

    /* ============================================================
     * CLEANUP
     * ============================================================ */

    public void dispose() {

        /*
         * AnimationTimer durduruluyor.
         */
        if (renderTimer != null) {
            renderTimer.stop();
            renderTimer = null;
        }

        /*
         * Native Engine instance yok ediliyor.
         */
        destroyNativeEngine();

        /*
         * Java shared memory serbest bırakılıyor.
         */
        if (arena != null) {

            try {
                arena.close();
            } catch (Throwable throwable) {

                AppLogger.error(
                    "Java Arena kapatılırken hata oluştu.",
                    throwable
                );
            }

            arena = null;
        }

        pixelSegment = null;
        pixelBuffer = null;

        AppLogger.info(
            "Workspace kaynakları temizlendi."
        );
    }
}
