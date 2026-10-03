#ifndef LAYER_MANAGER_HPP
#define LAYER_MANAGER_HPP

#include "Layer.hpp"

#include <vector>
#include <string>
#include <algorithm>
#include <cstring>
#include <cstdint>

#include <zlib.h>

#include <fstream>
#include <limits>

class LayerManager {
private:
    int width;
    int height;

    std::vector<Layer*> layers;
    int activeLayerIndex;

    static constexpr char FILE_MAGIC[8] = {
        'I', 'N', 'K', 'P', 'A', 'G', 'E', '1'
    };

    static constexpr uint32_t FILE_VERSION = 1;

    static bool writeU8(std::ofstream& out, uint8_t value) {
        out.write(reinterpret_cast<const char*>(&value), sizeof(value));
        return out.good();
    }

    static bool writeU32LE(std::ofstream& out, uint32_t value) {
        uint8_t bytes[4] = {
            static_cast<uint8_t>(value & 0xFF),
            static_cast<uint8_t>((value >> 8) & 0xFF),
            static_cast<uint8_t>((value >> 16) & 0xFF),
            static_cast<uint8_t>((value >> 24) & 0xFF)
        };

        out.write(reinterpret_cast<const char*>(bytes), 4);
        return out.good();
    }

    static bool writeI32LE(std::ofstream& out, int32_t value) {
        return writeU32LE(out, static_cast<uint32_t>(value));
    }

    static bool writeFloatLE(std::ofstream& out, float value) {
        uint32_t bits = 0;
        static_assert(sizeof(float) == sizeof(uint32_t));

        std::memcpy(&bits, &value, sizeof(bits));
        return writeU32LE(out, bits);
    }

    static bool readU8(std::ifstream& in, uint8_t& value) {
        in.read(reinterpret_cast<char*>(&value), sizeof(value));
        return in.good();
    }

    static bool readU32LE(std::ifstream& in, uint32_t& value) {
        uint8_t bytes[4]{};

        in.read(reinterpret_cast<char*>(bytes), 4);
        if (!in.good()) {
            return false;
        }

        value =
            static_cast<uint32_t>(bytes[0]) |
            (static_cast<uint32_t>(bytes[1]) << 8) |
            (static_cast<uint32_t>(bytes[2]) << 16) |
            (static_cast<uint32_t>(bytes[3]) << 24);

        return true;
    }

    static bool readI32LE(std::ifstream& in, int32_t& value) {
        uint32_t raw = 0;

        if (!readU32LE(in, raw)) {
            return false;
        }

        value = static_cast<int32_t>(raw);
        return true;
    }

    static bool readFloatLE(std::ifstream& in, float& value) {
        uint32_t bits = 0;

        if (!readU32LE(in, bits)) {
            return false;
        }

        std::memcpy(&value, &bits, sizeof(value));
        return true;
    }

    static bool readAndValidateMagic(std::ifstream& in) {
        char magic[8]{};

        in.read(magic, sizeof(magic));

        if (!in.good()) {
            return false;
        }

        return std::memcmp(magic, FILE_MAGIC, sizeof(FILE_MAGIC)) == 0;
    }

public:
    LayerManager(int w, int h)
        : width(w),
          height(h),
          activeLayerIndex(-1) {

        addLayer("Katman 1");
    }

    ~LayerManager() {
        clearLayers();
    }

    void clearLayers() {
        for (Layer* layer : layers) {
            delete layer;
        }

        layers.clear();
        activeLayerIndex = -1;
    }

    bool addLayer(std::string name) {
        layers.push_back(new Layer(width, height, name));
        activeLayerIndex = static_cast<int>(layers.size()) - 1;
        return true;
    }

    bool removeLayer(int index) {
        // Tuvalde en az 1 katman kalmalı.
        if (
            index < 0 ||
            index >= static_cast<int>(layers.size()) ||
            layers.size() <= 1
        ) {
            return false;
        }

        delete layers[index];
        layers.erase(layers.begin() + index);

        // Silinen katman aktif katmanın önündeyse
        // aktif katmanın index'i 1 azalmalı.
        if (activeLayerIndex > index) {
            activeLayerIndex--;
        }

        // Aktif index artık liste dışındaysa son katmana geç.
        if (activeLayerIndex >= static_cast<int>(layers.size())) {
            activeLayerIndex = static_cast<int>(layers.size()) - 1;
        }

        return true;
    }

    bool setLayerVisibility(int index, bool visible) {
        if (
            index < 0 ||
            index >= static_cast<int>(layers.size())
        ) {
            return false;
        }

        layers[index]->setVisible(visible);
        return true;
    }

    bool moveLayer(int fromIndex, int toIndex) {
        if (fromIndex == toIndex) {
            return false;
        }

        if (
            fromIndex < 0 ||
            fromIndex >= static_cast<int>(layers.size()) ||
            toIndex < 0 ||
            toIndex >= static_cast<int>(layers.size())
        ) {
            return false;
        }

        Layer* temp = layers[fromIndex];

        layers.erase(
            layers.begin() + fromIndex
        );

        layers.insert(
            layers.begin() + toIndex,
            temp
        );

        // Mevcut davranışı koruyoruz:
        // taşınan katman aktif hale geliyor.
        activeLayerIndex = toIndex;

        return true;
    }

    bool setActiveLayer(int index) {
        if (
            index < 0 ||
            index >= static_cast<int>(layers.size())
        ) {
            return false;
        }

        activeLayerIndex = index;
        return true;
    }

    Layer* getActiveLayer() {
        if (activeLayerIndex >= 0 &&
            activeLayerIndex < static_cast<int>(layers.size())) {

            return layers[activeLayerIndex];
        }

        return nullptr;
    }

    // Aktif katmanın dışarıdan okunabilen görünürlük durumu
    bool getLayerVisibility(int index) const {
        if (index >= 0 && index < static_cast<int>(layers.size())) {
            return layers[index]->isVisible();
        }

        return false;
    }

    // Aktif katmanın opacity değeri
    float getLayerOpacity(int index) const {
        if (index >= 0 && index < static_cast<int>(layers.size())) {
            return layers[index]->getOpacity();
        }

        return 0.0f;
    }

    // Katman adını Java tarafına kopyalar.
    //
    // outputBuffer:
    //     Java tarafından verilen native bellek
    //
    // bufferSize:
    //     Buffer'ın toplam kapasitesi
    //
    // Dönüş:
    //     Kopyalanan karakter sayısı
    //     (sonlandırıcı '\0' dahil değil)
    int copyLayerName(
        int index,
        uint8_t* outputBuffer,
        int bufferSize
    ) const {

        if (
            index < 0 ||
            index >= static_cast<int>(layers.size()) ||
            outputBuffer == nullptr ||
            bufferSize <= 0
        ) {
            return 0;
        }

        const std::string name =
            layers[index]->getName();

        const int copyLength =
            std::min(
                static_cast<int>(name.size()),
                bufferSize - 1
            );

        std::memcpy(
            outputBuffer,
            name.data(),
            static_cast<size_t>(copyLength)
        );

        outputBuffer[copyLength] = '\0';

        return copyLength;
    }

    const Layer* getLayer(int index) const {
        if (index >= 0 &&
            index < static_cast<int>(layers.size())) {

            return layers[index];
        }

        return nullptr;
    }

    int getLayerCount() const {
        return static_cast<int>(layers.size());
    }

    int getActiveLayerIndex() const {
        return activeLayerIndex;
    }

    int getWidth() const {
        return width;
    }

    int getHeight() const {
        return height;
    }

    void render(uint32_t* finalBuffer) {
        renderRegion(finalBuffer, 0, 0, width - 1, height - 1);
    }

    void renderRegion(
        uint32_t* finalBuffer,
        int minX,
        int minY,
        int maxX,
        int maxY
    ) {
        minX = std::max(0, minX);
        minY = std::max(0, minY);

        maxX = std::min(width - 1, maxX);
        maxY = std::min(height - 1, maxY);

        if (minX > maxX || minY > maxY) {
            return;
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                finalBuffer[
                    static_cast<size_t>(y) * static_cast<size_t>(width)
                    + static_cast<size_t>(x)
                ] = 0xFFFFFFFF;
            }
        }

        for (Layer* layer : layers) {
            if (!layer->isVisible()) {
                continue;
            }

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    uint32_t color = layer->getPixel(x, y);
                    uint8_t alpha = static_cast<uint8_t>((color >> 24) & 0xFF);

                    if (alpha > 0) {
                        finalBuffer[
                            static_cast<size_t>(y) * static_cast<size_t>(width)
                            + static_cast<size_t>(x)
                        ] = color;
                    }
                }
            }
        }
    }

    bool saveToFile(const std::string& filePath) const {
        std::ofstream out(filePath, std::ios::binary | std::ios::trunc);

        if (!out.is_open()) {
            return false;
        }

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        out.write(FILE_MAGIC, sizeof(FILE_MAGIC));

        if (!writeU32LE(out, FILE_VERSION)) {
            return false;
        }

        if (!writeU32LE(out, static_cast<uint32_t>(width))) {
            return false;
        }

        if (!writeU32LE(out, static_cast<uint32_t>(height))) {
            return false;
        }

        if (!writeI32LE(out, activeLayerIndex)) {
            return false;
        }

        if (!writeU32LE(out, static_cast<uint32_t>(layers.size()))) {
            return false;
        }

        // ---------------------------------------------------------
        // LAYERS
        // ---------------------------------------------------------

        for (const Layer* layer : layers) {
            const std::string& name = layer->getName();

            if (name.size() > std::numeric_limits<uint32_t>::max()) {
                return false;
            }

            if (!writeU32LE(out, static_cast<uint32_t>(name.size()))) {
                return false;
            }

            if (!writeU8(
                    out,
                    layer->isVisible() ? static_cast<uint8_t>(1)
                                       : static_cast<uint8_t>(0))) {
                return false;
            }

            if (!writeFloatLE(out, layer->getOpacity())) {
                return false;
            }

            const auto& pixels = layer->getPixels();

            const size_t rawSize = pixels.size() * sizeof(uint32_t);

            if (rawSize > std::numeric_limits<uint32_t>::max()) {
                return false;
            }

            const uint32_t rawSize32 = static_cast<uint32_t>(rawSize);

            const uLongf compressedCapacity =
                compressBound(static_cast<uLong>(rawSize32));

            std::vector<uint8_t> compressed(
                static_cast<size_t>(compressedCapacity)
            );

            uLongf compressedSize = compressedCapacity;

            const int compressionResult = compress2(
                compressed.data(),
                &compressedSize,
                reinterpret_cast<const Bytef*>(pixels.data()),
                static_cast<uLong>(rawSize32),
                Z_BEST_SPEED
            );

            if (compressionResult != Z_OK) {
                return false;
            }

            if (!writeU32LE(out, rawSize32)) {
                return false;
            }

            if (!writeU32LE(
                    out,
                    static_cast<uint32_t>(compressedSize))) {
                return false;
            }

            if (!name.empty()) {
                out.write(name.data(),
                          static_cast<std::streamsize>(name.size()));

                if (!out.good()) {
                    return false;
                }
            }

            out.write(
                reinterpret_cast<const char*>(compressed.data()),
                static_cast<std::streamsize>(compressedSize)
            );

            if (!out.good()) {
                return false;
            }
        }

        out.flush();
        return out.good();
    }

    bool loadFromFile(const std::string& filePath) {
        std::ifstream in(filePath, std::ios::binary);

        if (!in.is_open()) {
            return false;
        }

        if (!readAndValidateMagic(in)) {
            return false;
        }

        uint32_t version = 0;
        uint32_t fileWidth = 0;
        uint32_t fileHeight = 0;
        int32_t fileActiveLayer = -1;
        uint32_t fileLayerCount = 0;

        if (!readU32LE(in, version) ||
            !readU32LE(in, fileWidth) ||
            !readU32LE(in, fileHeight) ||
            !readI32LE(in, fileActiveLayer) ||
            !readU32LE(in, fileLayerCount)) {

            return false;
        }

        if (version != FILE_VERSION) {
            return false;
        }

        if (fileWidth == 0 || fileHeight == 0) {
            return false;
        }

        if (fileLayerCount == 0) {
            return false;
        }

        const uint64_t totalPixels =
            static_cast<uint64_t>(fileWidth) *
            static_cast<uint64_t>(fileHeight);

        const uint64_t totalBytes =
            totalPixels * sizeof(uint32_t);

        if (totalBytes > std::numeric_limits<uint32_t>::max()) {
            return false;
        }

        if (fileActiveLayer < 0 ||
            fileActiveLayer >= static_cast<int32_t>(fileLayerCount)) {

            fileActiveLayer = static_cast<int32_t>(fileLayerCount) - 1;
        }

        std::vector<Layer*> loadedLayers;
        loadedLayers.reserve(fileLayerCount);

        auto cleanupLoadedLayers = [&]() {
            for (Layer* layer : loadedLayers) {
                delete layer;
            }

            loadedLayers.clear();
        };

        for (uint32_t layerIndex = 0;
             layerIndex < fileLayerCount;
             layerIndex++) {

            uint32_t nameLength = 0;
            uint8_t visible = 1;
            float opacity = 1.0f;
            uint32_t rawSize = 0;
            uint32_t compressedSize = 0;

            if (!readU32LE(in, nameLength) ||
                !readU8(in, visible) ||
                !readFloatLE(in, opacity) ||
                !readU32LE(in, rawSize) ||
                !readU32LE(in, compressedSize)) {

                cleanupLoadedLayers();
                return false;
            }

            if (nameLength > 1024 * 1024) {
                cleanupLoadedLayers();
                return false;
            }

            std::string name(nameLength, '\0');

            if (nameLength > 0) {
                in.read(
                    name.data(),
                    static_cast<std::streamsize>(nameLength)
                );

                if (!in.good()) {
                    cleanupLoadedLayers();
                    return false;
                }
            }

            std::vector<uint8_t> compressed(compressedSize);

            if (compressedSize > 0) {
                in.read(
                    reinterpret_cast<char*>(compressed.data()),
                    static_cast<std::streamsize>(compressedSize)
                );

                if (!in.good()) {
                    cleanupLoadedLayers();
                    return false;
                }
            }

            if (rawSize != totalBytes) {
                cleanupLoadedLayers();
                return false;
            }

            std::vector<uint32_t> pixels(totalPixels);

            uLongf decompressedSize =
                static_cast<uLongf>(rawSize);

            const int decompressResult = uncompress(
                reinterpret_cast<Bytef*>(pixels.data()),
                &decompressedSize,
                compressed.data(),
                static_cast<uLong>(compressedSize)
            );

            if (decompressResult != Z_OK ||
                decompressedSize != rawSize) {

                cleanupLoadedLayers();
                return false;
            }

            Layer* layer =
                new Layer(
                    static_cast<int>(fileWidth),
                    static_cast<int>(fileHeight),
                    name
                );

            layer->setVisible(visible != 0);
            layer->setOpacity(opacity);
            layer->replacePixels(std::move(pixels));

            loadedLayers.push_back(layer);
        }

        // ---------------------------------------------------------
        // YENİ DATA BAŞARIYLA OKUNDU.
        // Şimdi mevcut katmanları değiştiriyoruz.
        // ---------------------------------------------------------

        clearLayers();

        width = static_cast<int>(fileWidth);
        height = static_cast<int>(fileHeight);

        layers = std::move(loadedLayers);
        activeLayerIndex = static_cast<int>(fileActiveLayer);

        return true;
    }

    static bool readFileDimensions(
        const std::string& filePath,
        int& outWidth,
        int& outHeight
    ) {
        std::ifstream in(filePath, std::ios::binary);

        if (!in.is_open()) {
            return false;
        }

        if (!readAndValidateMagic(in)) {
            return false;
        }

        uint32_t version = 0;
        uint32_t widthValue = 0;
        uint32_t heightValue = 0;

        if (!readU32LE(in, version) ||
            !readU32LE(in, widthValue) ||
            !readU32LE(in, heightValue)) {

            return false;
        }

        if (version != FILE_VERSION ||
            widthValue == 0 ||
            heightValue == 0) {

            return false;
        }

        if (widthValue > static_cast<uint32_t>(std::numeric_limits<int>::max()) ||
            heightValue > static_cast<uint32_t>(std::numeric_limits<int>::max())) {

            return false;
        }

        outWidth = static_cast<int>(widthValue);
        outHeight = static_cast<int>(heightValue);

        return true;
    }
};

constexpr char LayerManager::FILE_MAGIC[8];

#endif // LAYER_MANAGER_HPP
