#ifndef LAYER_HPP
#define LAYER_HPP

#include <stdint.h>
#include <string>
#include <vector>
#include <algorithm>

class Layer {
private:
    int width;
    int height;

    // Katmanın gerçek piksel belleği
    std::vector<uint32_t> pixels;

    bool visible;
    float opacity;
    std::string name;

public:
    Layer(int w, int h, std::string layerName)
        : width(w),
          height(h),
          name(std::move(layerName)),
          visible(true),
          opacity(1.0f) {

        pixels.resize(static_cast<size_t>(w) * static_cast<size_t>(h), 0x00000000);
    }

    void clear() {
        std::fill(pixels.begin(), pixels.end(), 0x00000000);
    }

    void setPixel(int x, int y, uint32_t colorArgb) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            pixels[static_cast<size_t>(y) * static_cast<size_t>(width)
                 + static_cast<size_t>(x)] = colorArgb;
        }
    }

    uint32_t getPixel(int x, int y) const {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            return pixels[static_cast<size_t>(y) * static_cast<size_t>(width)
                        + static_cast<size_t>(x)];
        }

        return 0;
    }

    bool isVisible() const {
        return visible;
    }

    void setVisible(bool v) {
        visible = v;
    }

    float getOpacity() const {
        return opacity;
    }

    void setOpacity(float o) {
        opacity = std::clamp(o, 0.0f, 1.0f);
    }

    const std::string& getName() const {
        return name;
    }

    void setName(const std::string& n) {
        name = n;
    }

    int getWidth() const {
        return width;
    }

    int getHeight() const {
        return height;
    }

    const std::vector<uint32_t>& getPixels() const {
        return pixels;
    }

    std::vector<uint32_t>& getPixels() {
        return pixels;
    }

    void replacePixels(std::vector<uint32_t>&& newPixels) {
        const size_t expectedSize =
            static_cast<size_t>(width) * static_cast<size_t>(height);

        if (newPixels.size() != expectedSize) {
            return;
        }

        pixels = std::move(newPixels);
    }
};

#endif // LAYER_HPP
