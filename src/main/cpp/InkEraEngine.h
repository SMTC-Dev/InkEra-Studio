#ifndef INKERA_ENGINE_H
#define INKERA_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Java tarafında bu değer bir MemorySegment olarak tutulacak.
 *
 * Daha önce bütün native durum globaldi:
 *
 *     static LayerManager* layerManager;
 *
 * Artık her Workspace kendi EngineHandle'ına sahip olacak.
 */
typedef void* InkEraEngineHandle;

/* ============================================================
 * ENGINE YAŞAM DÖNGÜSÜ
 * ============================================================ */

InkEraEngineHandle createEngine(int width, int height);

void destroyEngine(InkEraEngineHandle engine);

/* ============================================================
 * CANVAS / DRAWING
 * ============================================================ */

void clearCanvas(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    uint32_t colorArgb
);

bool addNewLayer(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height
);

bool setActiveLayer(
    InkEraEngineHandle engine,
    int index
);

void usePencil(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int x0,
    int y0,
    int x1,
    int y1,
    uint32_t colorArgb
);

/* ============================================================
 * LAYER İŞLEMLERİ
 * ============================================================ */

bool setLayerVisibility(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index,
    bool isVisible
);

bool deleteLayer(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index
);

bool moveLayer(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int fromIndex,
    int toIndex
);

/* ============================================================
 * PAGE SAVE / LOAD
 * ============================================================ */

/*
 * Engine'in o anki LayerManager durumunu .inkpage dosyasına kaydeder.
 */
bool savePage(
    InkEraEngineHandle engine,
    const char* filePath
);

/*
 * .inkpage dosyasını Engine'e yükler ve pixelBuffer'a render eder.
 */
bool loadPage(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    const char* filePath
);

/*
 * Dosyanın canvas boyutlarını okur.
 *
 * Dönüş:
 *     (width << 32) | height
 *
 * Java tarafında daha sonra dosya açılmadan önce boyutu
 * öğrenmek için kullanılacak.
 *
 * Bu fonksiyon Engine state'ine ihtiyaç duymaz.
 */
int64_t getPageSize(
    const char* filePath
);

/* ============================================================
 * LAYER DURUMU
 * ============================================================ */

int getLayerCount(
    InkEraEngineHandle engine
);

int getActiveLayerIndex(
    InkEraEngineHandle engine
);

bool getLayerVisibility(
    InkEraEngineHandle engine,
    int index
);

float getLayerOpacity(
    InkEraEngineHandle engine,
    int index
);

int copyLayerName(
    InkEraEngineHandle engine,
    int index,
    uint8_t* outputBuffer,
    int bufferSize
);

#ifdef __cplusplus
}
#endif

#endif // INKERA_ENGINE_H
