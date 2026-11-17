#ifndef PENCIL_HPP
#define PENCIL_HPP

#include "Tool.hpp"

class Pencil : public Tool {
public:
    void apply(Layer& layer, int cx, int cy) override {
        int dynamicSize = std::max(1, static_cast<int>(size * currentPressure));
        float radius = dynamicSize / 2.0f;

        if (radius <= 0.5f) {
            if (color == 0x00000000) {
                layer.setPixel(cx, cy, 0x00000000);
            } else {
                layer.setPixel(cx, cy, color);
            }
            return;
        }

        int intRadius = static_cast<int>(std::ceil(radius));
        float r2 = radius * radius;

        uint32_t srcColor = color;
        uint32_t srcBaseA = (srcColor >> 24) & 0xFF;
        uint32_t srcR = (srcColor >> 16) & 0xFF;
        uint32_t srcG = (srcColor >> 8) & 0xFF;
        uint32_t srcB = srcColor & 0xFF;
        bool isEraser = (srcColor == 0x00000000);

        for (int dy = -intRadius; dy <= intRadius; dy++) {
            for (int dx = -intRadius; dx <= intRadius; dx++) {
                float distSq = static_cast<float>(dx * dx + dy * dy);

                if (distSq <= r2) {
                    float dist = std::sqrt(distSq);
                    float alphaFactor = 1.0f;

                    // Kenar yumuşatma (Anti-aliasing)
                    if (radius > 1.0f && dist >= radius - 1.0f) {
                        alphaFactor = std::clamp(radius - dist, 0.0f, 1.0f);
                    }

                    if (alphaFactor > 0.02f) { // Çok düşük değerleri ihmal et
                        int px = cx + dx;
                        int py = cy + dy;
                        uint32_t existing = layer.getPixel(px, py);
                        uint32_t dstA = (existing >> 24) & 0xFF;

                        if (isEraser) {
                            // SİLGİ ALGORİTMASI: Yumuşak Silme (Destination Out benzeri)
                            if (dstA > 0) {
                                // alphaFactor 1 ise tam sil, 0 ise hiç silme.
                                float eraseAmount = alphaFactor; // Silme gücü
                                float newA_f = dstA * (1.0f - eraseAmount);
                                uint32_t newA = static_cast<uint32_t>(std::clamp(newA_f, 0.0f, 255.0f));
                                
                                // Çok şeffaflaştıysa tamamen yok et (Gereksiz hesaplamayı önler)
                                if (newA < 5) newA = 0;
                                
                                layer.setPixel(px, py, (newA << 24) | (existing & 0x00FFFFFF));
                            }
                        } else {
                            // KALEM ALGORİTMASI: Gerçek Alpha Blending (SrcOver)
                            uint32_t appliedA = static_cast<uint32_t>(srcBaseA * alphaFactor);
                            
                            if (appliedA > 0) {
                                if (dstA == 0) {
                                    // Altı boşsa direkt yaz
                                    layer.setPixel(px, py, (appliedA << 24) | (srcR << 16) | (srcG << 8) | srcB);
                                } else {
                                    uint32_t dstR = (existing >> 16) & 0xFF;
                                    uint32_t dstG = (existing >> 8) & 0xFF;
                                    uint32_t dstB = existing & 0xFF;

                                    // Klasik SrcOver Alpha Blending denklemi
                                    // outA = srcA + dstA * (1 - srcA)
                                    // outColor = (srcColor * srcA + dstColor * dstA * (1 - srcA)) / outA
                                    
                                    float sA = appliedA / 255.0f;
                                    float dA = dstA / 255.0f;
                                    float outA = sA + dA * (1.0f - sA);
                                    
                                    if (outA > 0.0f) {
                                        float invSa = 1.0f - sA;
                                        uint32_t finalR = static_cast<uint32_t>((srcR * sA + dstR * dA * invSa) / outA);
                                        uint32_t finalG = static_cast<uint32_t>((srcG * sA + dstG * dA * invSa) / outA);
                                        uint32_t finalB = static_cast<uint32_t>((srcB * sA + dstB * dA * invSa) / outA);
                                        uint32_t finalA = static_cast<uint32_t>(outA * 255.0f);
                                        
                                        layer.setPixel(px, py, (finalA << 24) | (finalR << 16) | (finalG << 8) | finalB);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
};

#endif // PENCIL_HPP
