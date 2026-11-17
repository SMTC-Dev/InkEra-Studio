#ifndef TOOL_HPP
#define TOOL_HPP

#include "Layer.hpp"
#include <stdint.h>
#include <cmath>
#include <algorithm>

class Tool {
protected:
    uint32_t color;
    int size;
    float currentPressure;
    float distAccumulator; // Kesik kesik (köşeli) lekeleri önlemek için mesafe hafızası

public:
    Tool() : color(0xFF000000), size(4), currentPressure(1.0f), distAccumulator(0.0f) {}
    virtual ~Tool() = default;

    void setColor(uint32_t c) { color = c; }
    uint32_t getColor() const { return color; }

    void setSize(int s) { size = std::clamp(s, 1, 200); }
    int getSize() const { return size; }

    void setPressure(float p) { currentPressure = std::clamp(p, 0.0f, 1.0f); }
    float getPressure() const { return currentPressure; }

    virtual void apply(Layer& layer, int cx, int cy) = 0;

    // Kusursuz spacing (adım) ve interpolasyon algoritması
    virtual void applyLine(Layer& layer, float x0, float y0, float x1, float y1, float pressureStart, float pressureEnd) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float totalDist = std::hypot(dx, dy);

        // Mesafe 0 ise (Ekrana ilk dokunuş), tek bir damga vur ve akümülatörü SIFIRLA.
        if (totalDist == 0.0f) {
            setPressure(pressureEnd);
            apply(layer, static_cast<int>(std::round(x0)), static_cast<int>(std::round(y0)));
            distAccumulator = 0.0f; // Yeni çizgi başlangıcında hafızayı temizle
            return;
        }

        // Fırça adım aralığı (Spacing). Fırça çapının %10'u kadar. (Min 0.5px)
        float currentSize = size * ((pressureStart + pressureEnd) / 2.0f);
        float spacing = std::max(0.5f, currentSize * 0.1f);

        float t = 0.0f;
        float distanceToNextStamp = spacing - distAccumulator;

        // Noktalar arası mesafeyi (spacing) koruyarak fraksiyonel adımlarla ilerle
        while (distanceToNextStamp <= totalDist) {
            t = distanceToNextStamp / totalDist;
            
            float px = x0 + t * dx;
            float py = y0 + t * dy;
            float pp = pressureStart + t * (pressureEnd - pressureStart);

            setPressure(pp);
            apply(layer, static_cast<int>(std::round(px)), static_cast<int>(std::round(py)));

            distAccumulator = 0.0f;
            totalDist -= distanceToNextStamp;
            
            x0 = px;
            y0 = py;
            pressureStart = pp;
            dx = x1 - x0;
            dy = y1 - y0;
            
            distanceToNextStamp = spacing;
        }

        // Kalan mesafeyi bir sonraki çizgi segmenti için hafızaya al
        distAccumulator += totalDist;
    }
};

#endif // TOOL_HPP
