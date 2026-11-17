#include "InkEraEngine.h"

#include "LayerManager.hpp"
#include "Pencil.hpp"

#include <algorithm>
#include <cstdint>
#include <iostream>
#include <memory>
#include <string>
#include <utility>
#include <vector>

namespace {

void logInfo(const std::string& message) {
    std::cout << "[InkEra][C++] " << message << std::endl;
}

void logWarn(const std::string& message) {
    std::cerr << "[InkEra][C++][WARN] " << message << std::endl;
}

void logError(const std::string& message) {
    std::cerr << "[InkEra][C++][ERROR] " << message << std::endl;
}

}

class InkEraEngine {
private:
    std::unique_ptr<LayerManager> layerManager;
    Pencil pencilTool;
    int nextLayerSerial = 2;

public:
    InkEraEngine(int width, int height)
        : layerManager(std::make_unique<LayerManager>(width, height)),
          nextLayerSerial(2) {

        pencilTool.setSize(6);

        logInfo(
            "Engine oluşturuldu: "
            + std::to_string(width)
            + "x"
            + std::to_string(height)
            + " (Varsayılan fırça çapı: 6px)"
        );
    }

    ~InkEraEngine() {
        logInfo("Engine yok ediliyor.");
    }

    LayerManager* getLayerManager() {
        return layerManager.get();
    }

    const LayerManager* getLayerManager() const {
        return layerManager.get();
    }

    Pencil& getPencil() {
        return pencilTool;
    }

    int consumeNextLayerSerial() {
        int currentCount = layerManager ? (layerManager->getLayerCount() + 1) : nextLayerSerial;
        nextLayerSerial = std::max(nextLayerSerial, currentCount);
        return nextLayerSerial++;
    }

    void resetCanvas(int width, int height) {
        layerManager = std::make_unique<LayerManager>(width, height);
        nextLayerSerial = 2;

        logInfo(
            "Engine canvas sıfırlandı: "
            + std::to_string(width)
            + "x"
            + std::to_string(height)
        );
    }

    bool loadPageFile(const char* filePath) {
        if (filePath == nullptr || filePath[0] == '\0') {
            logWarn("loadPage: Dosya yolu boş.");
            return false;
        }

        if (layerManager == nullptr) {
            logWarn("loadPage: LayerManager mevcut değil.");
            return false;
        }

        int fileWidth = 0;
        int fileHeight = 0;

        if (!LayerManager::readFileDimensions(
                std::string(filePath),
                fileWidth,
                fileHeight)) {
            logWarn("loadPage: Dosya boyutları okunamadı: " + std::string(filePath));
            return false;
        }

        auto candidateManager = std::make_unique<LayerManager>(fileWidth, fileHeight);
        if (!candidateManager->loadFromFile(std::string(filePath))) {
            logWarn("loadPage: Dosya yüklenemedi: " + std::string(filePath));
            return false;
        }

        layerManager = std::move(candidateManager);
        nextLayerSerial = layerManager->getLayerCount() + 1;

        logInfo(
            "Sayfa yüklendi: "
            + std::string(filePath)
            + " ("
            + std::to_string(fileWidth)
            + "x"
            + std::to_string(fileHeight)
            + ")"
        );

        return true;
    }
};

extern "C" {

InkEraEngineHandle createEngine(int width, int height) {
    if (width <= 0 || height <= 0) {
        logError("createEngine: Geçersiz canvas boyutu.");
        return nullptr;
    }

    try {
        InkEraEngine* engine = new InkEraEngine(width, height);
        return static_cast<InkEraEngineHandle>(engine);
    } catch (const std::exception& e) {
        logError(std::string("createEngine exception: ") + e.what());
        return nullptr;
    } catch (...) {
        logError("createEngine: Bilinmeyen exception.");
        return nullptr;
    }
}

void destroyEngine(InkEraEngineHandle engineHandle) {
    if (engineHandle == nullptr) {
        return;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    delete engine;
}

void beginStrokeSnapshot(InkEraEngineHandle engineHandle) {
    if (engineHandle == nullptr) {
        return;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager != nullptr) {
        manager->pushUndoState();
    }
}

bool undoAction(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr || !manager->undo()) {
        logInfo("Undo: Geri alınacak işlem yok.");
        return false;
    }

    manager->render(pixelBuffer);
    logInfo(
        "Undo uygulandı (Kalan Undo: " + std::to_string(manager->getUndoCount())
        + ", Redo: " + std::to_string(manager->getRedoCount()) + ")"
    );
    return true;
}

bool redoAction(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr || !manager->redo()) {
        logInfo("Redo: İleri alınacak işlem yok.");
        return false;
    }

    manager->render(pixelBuffer);
    logInfo(
        "Redo uygulandı (Undo: " + std::to_string(manager->getUndoCount())
        + ", Kalan Redo: " + std::to_string(manager->getRedoCount()) + ")"
    );
    return true;
}

void clearCanvas(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    uint32_t colorArgb
) {
    (void) colorArgb;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("clearCanvas: Engine veya pixelBuffer mevcut değil.");
        return;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    engine->resetCanvas(width, height);

    LayerManager* manager = engine->getLayerManager();
    if (manager != nullptr) {
        manager->render(pixelBuffer);
    }
}

bool addNewLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("addNewLayer: Engine veya pixelBuffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        logWarn("addNewLayer: LayerManager mevcut değil.");
        return false;
    }

    manager->pushUndoState();

    std::string layerName = "Katman " + std::to_string(engine->consumeNextLayerSerial());
    bool success = manager->addLayer(layerName);
    if (!success) {
        logWarn("addNewLayer: Katman oluşturulamadı.");
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Yeni katman üstte oluşturuldu (" + layerName + ", index="
        + std::to_string(manager->getActiveLayerIndex())
        + "). Toplam: " + std::to_string(manager->getLayerCount())
    );
    return true;
}

bool setActiveLayer(
    InkEraEngineHandle engineHandle,
    int index
) {
    if (engineHandle == nullptr) {
        logWarn("setActiveLayer: Engine mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        logWarn("setActiveLayer: LayerManager mevcut değil.");
        return false;
    }

    bool success = manager->setActiveLayer(index);
    if (success) {
        logInfo("Aktif katman değiştirildi: " + std::to_string(index));
    } else {
        logWarn("setActiveLayer: Geçersiz index: " + std::to_string(index));
    }
    return success;
}

void setBrushSize(
    InkEraEngineHandle engineHandle,
    int size
) {
    if (engineHandle == nullptr) {
        return;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    if (engine->getPencil().getSize() != size) {
        engine->getPencil().setSize(size);
    }
}

int getBrushSize(InkEraEngineHandle engineHandle) {
    if (engineHandle == nullptr) {
        return 1;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    return engine->getPencil().getSize();
}

void usePencil(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int x0,
    int y0,
    float pressureStart,
    int x1,
    int y1,
    float pressureEnd,
    uint32_t colorArgb
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        return;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return;
    }

    Layer* activeLayer = manager->getActiveLayer();
    if (activeLayer == nullptr || !activeLayer->isVisible()) {
        return;
    }

    Pencil& pencil = engine->getPencil();
    pencil.setColor(colorArgb);

    if (x0 == x1 && y0 == y1) {
        pencil.setPressure(pressureEnd);
        pencil.apply(*activeLayer, x1, y1);
    } else {
        pencil.applyLine(*activeLayer, x0, y0, x1, y1, pressureStart, pressureEnd);
    }

    int padding = (pencil.getSize() / 2) + 4;

    manager->renderRegion(
        pixelBuffer,
        std::min(x0, x1) - padding,
        std::min(y0, y1) - padding,
        std::max(x0, x1) + padding,
        std::max(y0, y1) + padding
    );
}

void useEraser(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int x0,
    int y0,
    float pressureStart,
    int x1,
    int y1,
    float pressureEnd
) {
    usePencil(
        engineHandle,
        pixelBuffer,
        width,
        height,
        x0,
        y0,
        pressureStart,
        x1,
        y1,
        pressureEnd,
        0x00000000
    );
}

bool useBucketFill(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int startX,
    int startY,
    uint32_t fillColorArgb
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    Layer* activeLayer = manager->getActiveLayer();
    if (activeLayer == nullptr || !activeLayer->isVisible()) {
        logWarn("useBucketFill: Aktif katman yok veya görünmez durumda.");
        return false;
    }

    int lw = activeLayer->getWidth();
    int lh = activeLayer->getHeight();

    if (startX < 0 || startX >= lw || startY < 0 || startY >= lh) {
        return false;
    }

    uint32_t targetColor = activeLayer->getPixel(startX, startY);
    if (targetColor == fillColorArgb) {
        return false;
    }

    manager->pushUndoState();

    std::vector<uint32_t>& pixels = activeLayer->getPixels();
    std::vector<std::pair<int, int>> stack;
    stack.reserve(4096);
    stack.emplace_back(startX, startY);

    int minX = startX, maxX = startX;
    int minY = startY, maxY = startY;
    size_t filledCount = 0;

    while (!stack.empty()) {
        auto [x, y] = stack.back();
        stack.pop_back();

        int lx = x;
        while (lx >= 0 && pixels[static_cast<size_t>(y) * lw + lx] == targetColor) {
            pixels[static_cast<size_t>(y) * lw + lx] = fillColorArgb;
            filledCount++;
            lx--;
        }
        lx++;

        int rx = x + 1;
        while (rx < lw && pixels[static_cast<size_t>(y) * lw + rx] == targetColor) {
            pixels[static_cast<size_t>(y) * lw + rx] = fillColorArgb;
            filledCount++;
            rx++;
        }
        rx--;

        minX = std::min(minX, lx);
        maxX = std::max(maxX, rx);
        minY = std::min(minY, y);
        maxY = std::max(maxY, y);

        auto scanLine = [&](int ny) {
            bool inSpan = false;
            for (int sx = lx; sx <= rx; sx++) {
                if (pixels[static_cast<size_t>(ny) * lw + sx] == targetColor) {
                    if (!inSpan) {
                        stack.emplace_back(sx, ny);
                        inSpan = true;
                    }
                } else {
                    inSpan = false;
                }
            }
        };

        if (y > 0) scanLine(y - 1);
        if (y + 1 < lh) scanLine(y + 1);
    }

    manager->renderRegion(pixelBuffer, minX, minY, maxX, maxY);

    logInfo(
        "Bucket Fill tamamlandı: (" + std::to_string(startX) + ", " + std::to_string(startY)
        + ") -> Boyanan piksel: " + std::to_string(filledCount)
    );
    return true;
}

bool setLayerVisibility(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index,
    bool isVisible
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("setLayerVisibility: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    if (!manager->setLayerVisibility(index, isVisible)) {
        logWarn("setLayerVisibility: Geçersiz index: " + std::to_string(index));
        return false;
    }

    manager->render(pixelBuffer);
    logInfo(
        "Katman görünürlüğü değiştirildi: index="
        + std::to_string(index)
        + " visible="
        + std::string(isVisible ? "true" : "false")
    );
    return true;
}

bool setLayerOpacity(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index,
    float opacity
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("setLayerOpacity: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    if (!manager->setLayerOpacity(index, opacity)) {
        logWarn("setLayerOpacity: Geçersiz index: " + std::to_string(index));
        return false;
    }

    manager->render(pixelBuffer);
    return true;
}

bool setLayerName(
    InkEraEngineHandle engineHandle,
    int index,
    const char* newName
) {
    if (engineHandle == nullptr || newName == nullptr || newName[0] == '\0') {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    const Layer* target = manager->getLayer(index);
    if (target == nullptr || target->getName() == std::string(newName)) {
        return false;
    }

    manager->pushUndoState();
    bool ok = manager->setLayerName(index, std::string(newName));
    if (ok) {
        logInfo("Katman adı değiştirildi: index=" + std::to_string(index) + " -> '" + std::string(newName) + "'");
    }
    return ok;
}

bool deleteLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("deleteLayer: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr || !manager->canRemoveLayer(index)) {
        logWarn("deleteLayer: İşlem başarısız. index=" + std::to_string(index));
        return false;
    }

    manager->pushUndoState();
    manager->removeLayer(index);
    manager->render(pixelBuffer);

    logInfo(
        "Katman silindi: index="
        + std::to_string(index)
        + " toplam="
        + std::to_string(manager->getLayerCount())
    );
    return true;
}

bool moveLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int fromIndex,
    int toIndex
) {
    (void) width;
    (void) height;

    if (engineHandle == nullptr || pixelBuffer == nullptr) {
        logWarn("moveLayer: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr || !manager->canMoveLayer(fromIndex, toIndex)) {
        logWarn(
            "moveLayer: İşlem başarısız. from="
            + std::to_string(fromIndex)
            + " to="
            + std::to_string(toIndex)
        );
        return false;
    }

    manager->pushUndoState();
    manager->moveLayer(fromIndex, toIndex);
    manager->render(pixelBuffer);

    logInfo(
        "Katman taşındı: "
        + std::to_string(fromIndex)
        + " -> "
        + std::to_string(toIndex)
    );
    return true;
}

bool savePage(
    InkEraEngineHandle engineHandle,
    const char* filePath
) {
    if (engineHandle == nullptr || filePath == nullptr || filePath[0] == '\0') {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    try {
        bool success = manager->saveToFile(std::string(filePath));
        if (success) {
            logInfo("Sayfa kaydedildi: " + std::string(filePath));
        } else {
            logWarn("savePage: Dosya kaydedilemedi: " + std::string(filePath));
        }
        return success;
    } catch (const std::exception& e) {
        logError(std::string("savePage exception: ") + e.what());
        return false;
    } catch (...) {
        logError("savePage: Bilinmeyen exception.");
        return false;
    }
}

bool loadPage(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    const char* filePath
) {
    if (engineHandle == nullptr || pixelBuffer == nullptr || filePath == nullptr || filePath[0] == '\0') {
        return false;
    }

    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);

    int fileWidth = 0;
    int fileHeight = 0;

    if (!LayerManager::readFileDimensions(std::string(filePath), fileWidth, fileHeight)) {
        logWarn("loadPage: Dosya boyutları okunamadı: " + std::string(filePath));
        return false;
    }

    if (fileWidth != width || fileHeight != height) {
        logWarn(
            "loadPage: Canvas boyutu uyuşmuyor. Dosya="
            + std::to_string(fileWidth) + "x" + std::to_string(fileHeight)
            + " Buffer=" + std::to_string(width) + "x" + std::to_string(height)
        );
        return false;
    }

    if (!engine->loadPageFile(filePath)) {
        return false;
    }

    LayerManager* manager = engine->getLayerManager();
    if (manager == nullptr) {
        return false;
    }

    manager->render(pixelBuffer);
    logInfo("Sayfa render edildi: " + std::string(filePath));
    return true;
}

int64_t getPageSize(const char* filePath) {
    if (filePath == nullptr || filePath[0] == '\0') {
        return 0;
    }

    int width = 0;
    int height = 0;

    if (!LayerManager::readFileDimensions(std::string(filePath), width, height)) {
        return 0;
    }

    return (static_cast<int64_t>(static_cast<uint32_t>(width)) << 32)
         | static_cast<uint32_t>(height);
}

int getLayerCount(InkEraEngineHandle engineHandle) {
    if (engineHandle == nullptr) {
        return 0;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    return manager != nullptr ? manager->getLayerCount() : 0;
}

int getActiveLayerIndex(InkEraEngineHandle engineHandle) {
    if (engineHandle == nullptr) {
        return -1;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    return manager != nullptr ? manager->getActiveLayerIndex() : -1;
}

bool getLayerVisibility(InkEraEngineHandle engineHandle, int index) {
    if (engineHandle == nullptr) {
        return false;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    return manager != nullptr ? manager->getLayerVisibility(index) : false;
}

float getLayerOpacity(InkEraEngineHandle engineHandle, int index) {
    if (engineHandle == nullptr) {
        return 0.0f;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    return manager != nullptr ? manager->getLayerOpacity(index) : 0.0f;
}

int copyLayerName(
    InkEraEngineHandle engineHandle,
    int index,
    uint8_t* outputBuffer,
    int bufferSize
) {
    if (engineHandle == nullptr || outputBuffer == nullptr || bufferSize <= 0) {
        return 0;
    }
    InkEraEngine* engine = static_cast<InkEraEngine*>(engineHandle);
    LayerManager* manager = engine->getLayerManager();
    return manager != nullptr ? manager->copyLayerName(index, outputBuffer, bufferSize) : 0;
}

} // extern "C"
