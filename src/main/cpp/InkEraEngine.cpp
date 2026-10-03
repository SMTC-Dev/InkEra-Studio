#include "InkEraEngine.h"

#include "LayerManager.hpp"
#include "Pencil.hpp"

#include <algorithm>
#include <cstdint>
#include <iostream>
#include <memory>
#include <string>
#include <vector>

/* ============================================================
 * YARDIMCI LOG
 * ============================================================ */

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

/* ============================================================
 * GERÇEK ENGINE NESNESİ
 * ============================================================ */

class InkEraEngine {
private:
    std::unique_ptr<LayerManager> layerManager;
    Pencil pencilTool;

public:
    InkEraEngine(int width, int height)
        : layerManager(std::make_unique<LayerManager>(width, height)) {

        logInfo(
            "Engine oluşturuldu: "
            + std::to_string(width)
            + "x"
            + std::to_string(height)
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

    void resetCanvas(int width, int height) {
        layerManager = std::make_unique<LayerManager>(width, height);

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

        /*
         * Sayfa dosyasının boyutlarını önce okuyoruz.
         * Böylece Engine doğru boyuttaki LayerManager ile
         * yeniden oluşturulabilir.
         */
        int fileWidth = 0;
        int fileHeight = 0;

        if (!LayerManager::readFileDimensions(
                std::string(filePath),
                fileWidth,
                fileHeight)) {

            logWarn(
                "loadPage: Dosya boyutları okunamadı: "
                + std::string(filePath)
            );

            return false;
        }

        layerManager = std::make_unique<LayerManager>(
            fileWidth,
            fileHeight
        );

        if (!layerManager->loadFromFile(std::string(filePath))) {
            logWarn(
                "loadPage: Dosya yüklenemedi: "
                + std::string(filePath)
            );

            return false;
        }

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

/* ============================================================
 * JAVA / FFM C ABI
 * ============================================================ */

extern "C" {

/* ============================================================
 * ENGINE YAŞAM DÖNGÜSÜ
 * ============================================================ */

InkEraEngineHandle createEngine(int width, int height) {

    if (width <= 0 || height <= 0) {
        logError("createEngine: Geçersiz canvas boyutu.");
        return nullptr;
    }

    try {
        InkEraEngine* engine =
            new InkEraEngine(width, height);

        return static_cast<InkEraEngineHandle>(engine);

    } catch (const std::exception& e) {

        logError(
            std::string("createEngine exception: ")
            + e.what()
        );

        return nullptr;

    } catch (...) {

        logError(
            "createEngine: Bilinmeyen exception."
        );

        return nullptr;
    }
}

void destroyEngine(InkEraEngineHandle engineHandle) {

    if (engineHandle == nullptr) {
        return;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    delete engine;
}

/* ============================================================
 * CANVAS
 * ============================================================ */

void clearCanvas(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    uint32_t colorArgb
) {

    (void) colorArgb;

    if (engineHandle == nullptr) {
        logWarn("clearCanvas: Engine mevcut değil.");
        return;
    }

    if (pixelBuffer == nullptr) {
        logWarn("clearCanvas: Pixel buffer mevcut değil.");
        return;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    engine->resetCanvas(width, height);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager != nullptr) {
        manager->render(pixelBuffer);
    }
}

/* ============================================================
 * LAYER
 * ============================================================ */

bool addNewLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height
) {
    if (engineHandle == nullptr) {
        logWarn("addNewLayer: Engine mevcut değil.");
        return false;
    }

    if (pixelBuffer == nullptr) {
        logWarn("addNewLayer: Pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("addNewLayer: LayerManager mevcut değil.");
        return false;
    }

    bool success =
        manager->addLayer("Yeni Katman");

    if (!success) {
        logWarn("addNewLayer: Katman oluşturulamadı.");
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Yeni katman oluşturuldu. Toplam: "
        + std::to_string(manager->getLayerCount())
    );

    (void) width;
    (void) height;

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

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("setActiveLayer: LayerManager mevcut değil.");
        return false;
    }

    bool success =
        manager->setActiveLayer(index);

    if (success) {
        logInfo(
            "Aktif katman değiştirildi: "
            + std::to_string(index)
        );
    } else {
        logWarn(
            "setActiveLayer: Geçersiz index: "
            + std::to_string(index)
        );
    }

    return success;
}

/* ============================================================
 * PENCIL
 * ============================================================ */

void usePencil(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int x0,
    int y0,
    int x1,
    int y1,
    uint32_t colorArgb
) {

    if (engineHandle == nullptr) {
        return;
    }

    if (pixelBuffer == nullptr) {
        return;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return;
    }

    Layer* activeLayer =
        manager->getActiveLayer();

    if (activeLayer == nullptr) {
        return;
    }

    Pencil& pencil =
        engine->getPencil();

    pencil.setColor(colorArgb);

    if (x0 == x1 && y0 == y1) {

        pencil.apply(
            *activeLayer,
            x1,
            y1
        );

    } else {

        pencil.applyLine(
            *activeLayer,
            x0,
            y0,
            x1,
            y1
        );
    }

    /*
     * Şimdilik mevcut dirty-region davranışını koruyoruz.
     * Brush sistemi geliştirildiğinde burası yeniden ele alınacak.
     */
    int padding = 5;

    manager->renderRegion(
        pixelBuffer,
        std::min(x0, x1) - padding,
        std::min(y0, y1) - padding,
        std::max(x0, x1) + padding,
        std::max(y0, y1) + padding
    );

    (void) width;
    (void) height;
}

/* ============================================================
 * LAYER VISIBILITY
 * ============================================================ */

bool setLayerVisibility(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index,
    bool isVisible
) {
    if (
        engineHandle == nullptr ||
        pixelBuffer == nullptr
    ) {
        logWarn("setLayerVisibility: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("setLayerVisibility: LayerManager mevcut değil.");
        return false;
    }

    bool success =
        manager->setLayerVisibility(
            index,
            isVisible
        );

    if (!success) {
        logWarn(
            "setLayerVisibility: Geçersiz index: "
            + std::to_string(index)
        );
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Katman görünürlüğü değiştirildi: index="
        + std::to_string(index)
        + " visible="
        + std::string(isVisible ? "true" : "false")
    );

    (void) width;
    (void) height;

    return true;
}

/* ============================================================
 * DELETE LAYER
 * ============================================================ */

bool deleteLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index
) {
    if (
        engineHandle == nullptr ||
        pixelBuffer == nullptr
    ) {
        logWarn("deleteLayer: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("deleteLayer: LayerManager mevcut değil.");
        return false;
    }

    bool success =
        manager->removeLayer(index);

    if (!success) {
        logWarn(
            "deleteLayer: İşlem başarısız. index="
            + std::to_string(index)
        );
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Katman silindi: index="
        + std::to_string(index)
        + " toplam="
        + std::to_string(manager->getLayerCount())
    );

    (void) width;
    (void) height;

    return true;
}

/* ============================================================
 * MOVE LAYER
 * ============================================================ */

bool moveLayer(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int fromIndex,
    int toIndex
) {
    if (
        engineHandle == nullptr ||
        pixelBuffer == nullptr
    ) {
        logWarn("moveLayer: Engine veya pixel buffer mevcut değil.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("moveLayer: LayerManager mevcut değil.");
        return false;
    }

    bool success =
        manager->moveLayer(
            fromIndex,
            toIndex
        );

    if (!success) {
        logWarn(
            "moveLayer: İşlem başarısız. from="
            + std::to_string(fromIndex)
            + " to="
            + std::to_string(toIndex)
        );
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Katman taşındı: "
        + std::to_string(fromIndex)
        + " -> "
        + std::to_string(toIndex)
    );

    (void) width;
    (void) height;

    return true;
}

/* ============================================================
 * SAVE PAGE
 * ============================================================ */

bool savePage(
    InkEraEngineHandle engineHandle,
    const char* filePath
) {

    if (engineHandle == nullptr) {
        logWarn("savePage: Engine mevcut değil.");
        return false;
    }

    if (filePath == nullptr || filePath[0] == '\0') {
        logWarn("savePage: Dosya yolu boş.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        logWarn("savePage: LayerManager mevcut değil.");
        return false;
    }

    try {

        bool success =
            manager->saveToFile(
                std::string(filePath)
            );

        if (success) {

            logInfo(
                "Sayfa kaydedildi: "
                + std::string(filePath)
            );

        } else {

            logWarn(
                "savePage: Dosya kaydedilemedi: "
                + std::string(filePath)
            );
        }

        return success;

    } catch (const std::exception& e) {

        logError(
            std::string("savePage exception: ")
            + e.what()
        );

        return false;

    } catch (...) {

        logError(
            "savePage: Bilinmeyen exception."
        );

        return false;
    }
}

/* ============================================================
 * LOAD PAGE
 * ============================================================ */

bool loadPage(
    InkEraEngineHandle engineHandle,
    uint32_t* pixelBuffer,
    int width,
    int height,
    const char* filePath
) {

    if (engineHandle == nullptr) {
        logWarn("loadPage: Engine mevcut değil.");
        return false;
    }

    if (pixelBuffer == nullptr) {
        logWarn("loadPage: Pixel buffer mevcut değil.");
        return false;
    }

    if (filePath == nullptr || filePath[0] == '\0') {
        logWarn("loadPage: Dosya yolu boş.");
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    int fileWidth = 0;
    int fileHeight = 0;

    if (!LayerManager::readFileDimensions(
            std::string(filePath),
            fileWidth,
            fileHeight)) {

        logWarn(
            "loadPage: Dosya boyutları okunamadı: "
            + std::string(filePath)
        );

        return false;
    }

    /*
     * Java tarafındaki PixelBuffer'ın boyutu ile dosyanın
     * gerçek boyutunun aynı olması gerekiyor.
     *
     * Farklıysa Java önce yeni Arena + PixelBuffer oluşturacak,
     * sonra loadPage çağıracak.
     */
    if (fileWidth != width || fileHeight != height) {

        logWarn(
            "loadPage: Canvas boyutu uyuşmuyor. "
            "Dosya="
            + std::to_string(fileWidth)
            + "x"
            + std::to_string(fileHeight)
            + " Buffer="
            + std::to_string(width)
            + "x"
            + std::to_string(height)
        );

        return false;
    }

    if (!engine->loadPageFile(filePath)) {
        return false;
    }

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return false;
    }

    manager->render(pixelBuffer);

    logInfo(
        "Sayfa render edildi: "
        + std::string(filePath)
    );

    return true;
}

/* ============================================================
 * PAGE SIZE
 * ============================================================ */

int64_t getPageSize(
    const char* filePath
) {

    if (filePath == nullptr || filePath[0] == '\0') {
        return 0;
    }

    int width = 0;
    int height = 0;

    if (!LayerManager::readFileDimensions(
            std::string(filePath),
            width,
            height)) {

        return 0;
    }

    int64_t packed =
        (static_cast<int64_t>(
            static_cast<uint32_t>(width)
        ) << 32)
        |
        static_cast<uint32_t>(height);

    return packed;
}

/* ============================================================
 * LAYER STATE
 * ============================================================ */

int getLayerCount(
    InkEraEngineHandle engineHandle
) {

    if (engineHandle == nullptr) {
        return 0;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return 0;
    }

    return manager->getLayerCount();
}

int getActiveLayerIndex(
    InkEraEngineHandle engineHandle
) {

    if (engineHandle == nullptr) {
        return -1;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return -1;
    }

    return manager->getActiveLayerIndex();
}

bool getLayerVisibility(
    InkEraEngineHandle engineHandle,
    int index
) {

    if (engineHandle == nullptr) {
        return false;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return false;
    }

    return manager->getLayerVisibility(index);
}

float getLayerOpacity(
    InkEraEngineHandle engineHandle,
    int index
) {

    if (engineHandle == nullptr) {
        return 0.0f;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return 0.0f;
    }

    return manager->getLayerOpacity(index);
}

int copyLayerName(
    InkEraEngineHandle engineHandle,
    int index,
    uint8_t* outputBuffer,
    int bufferSize
) {

    if (engineHandle == nullptr ||
        outputBuffer == nullptr ||
        bufferSize <= 0) {

        return 0;
    }

    InkEraEngine* engine =
        static_cast<InkEraEngine*>(engineHandle);

    LayerManager* manager =
        engine->getLayerManager();

    if (manager == nullptr) {
        return 0;
    }

    return manager->copyLayerName(
        index,
        outputBuffer,
        bufferSize
    );
}

} // extern "C"
