#ifndef INKERA_ENGINE_H
#define INKERA_ENGINE_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void* InkEraEngineHandle;

/* ============================================================
 * ENGINE YAŞAM DÖNGÜSÜ
 * ============================================================ */

InkEraEngineHandle createEngine(int width, int height);

void destroyEngine(InkEraEngineHandle engine);

/* ============================================================
 * UNDO / REDO HISTORY
 * ============================================================ */

void beginStrokeSnapshot(
    InkEraEngineHandle engine
);

bool undoAction(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height
);

bool redoAction(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height
);

/* ============================================================
 * CANVAS / DRAWING / TOOLS
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

void setBrushSize(
    InkEraEngineHandle engine,
    int size
);

int getBrushSize(
    InkEraEngineHandle engine
);

void usePencil(
    InkEraEngineHandle engine,
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
);

void useEraser(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int x0,
    int y0,
    float pressureStart,
    int x1,
    int y1,
    float pressureEnd
);

bool useBucketFill(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int startX,
    int startY,
    uint32_t fillColorArgb
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

bool setLayerOpacity(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    int index,
    float opacity
);

bool setLayerName(
    InkEraEngineHandle engine,
    int index,
    const char* newName
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

bool savePage(
    InkEraEngineHandle engine,
    const char* filePath
);

bool loadPage(
    InkEraEngineHandle engine,
    uint32_t* pixelBuffer,
    int width,
    int height,
    const char* filePath
);

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
