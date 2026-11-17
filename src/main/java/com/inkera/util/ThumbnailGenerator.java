package com.inkera.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Shared Memory (MemorySegment ARGB) üzerinden hiçbir harici görüntü kütüphanesine
 * ihtiyaç duymadan hem ince manga çizgilerini koruyan (Area Min-Luminance Pooling)
 * küçük resim (thumbnail) hem de 1:1 tam çözünürlüklü PNG çıktısı üretir.
 */
public final class ThumbnailGenerator {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 137, 80, 78, 71, 13, 10, 26, 10
    };

    private ThumbnailGenerator() {
    }

    /**
     * Tuvali 1:1 tam çözünürlükte (kayıpsız) PNG dosyası olarak dışa aktarır.
     */
    public static boolean exportFullResolutionPng(
        MemorySegment pixelSegment,
        int width,
        int height,
        Path outputPath
    ) {
        if (pixelSegment == null || width <= 0 || height <= 0 || outputPath == null) {
            return false;
        }

        long startTimeNs = System.nanoTime();

        try {
            int stride = 1 + width * 3;
            byte[] rawScanlines = new byte[stride * height];

            for (int y = 0; y < height; y++) {
                int rowOffset = y * stride;
                rawScanlines[rowOffset] = 0; // Filter type 0 (None)
                int pixelOffset = rowOffset + 1;
                long rowBase = (long) y * width;

                for (int x = 0; x < width; x++) {
                    int argb = pixelSegment.get(
                        ValueLayout.JAVA_INT_UNALIGNED,
                        (rowBase + x) * 4L
                    );

                    rawScanlines[pixelOffset++] = (byte) ((argb >> 16) & 0xFF);
                    rawScanlines[pixelOffset++] = (byte) ((argb >> 8) & 0xFF);
                    rawScanlines[pixelOffset++] = (byte) (argb & 0xFF);
                }
            }

            writePngFile(rawScanlines, width, height, outputPath);

            long sizeBytes = Files.size(outputPath);
            long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000L;

            AppLogger.info(
                "Tam çözünürlüklü PNG dışa aktarıldı: " + outputPath
                + " [" + width + "x" + height
                + ", " + String.format("%.2f KB", sizeBytes / 1024.0)
                + ", " + elapsedMs + " ms]"
            );

            return true;

        } catch (Exception e) {
            AppLogger.error("Tam çözünürlüklü PNG dışa aktarılamadı: " + outputPath, e);
            return false;
        }
    }

    public static boolean saveThumbnailPng(
        MemorySegment pixelSegment,
        int srcWidth,
        int srcHeight,
        int maxThumbDimension,
        Path outputPath
    ) {
        if (pixelSegment == null || srcWidth <= 0 || srcHeight <= 0 || outputPath == null) {
            return false;
        }

        long startTimeNs = System.nanoTime();

        try {
            double scale = Math.min(
                1.0,
                (double) maxThumbDimension / Math.max(srcWidth, srcHeight)
            );

            int dstWidth = Math.max(1, (int) Math.round(srcWidth * scale));
            int dstHeight = Math.max(1, (int) Math.round(srcHeight * scale));

            int stride = 1 + dstWidth * 3;
            byte[] rawScanlines = new byte[stride * dstHeight];

            for (int dy = 0; dy < dstHeight; dy++) {
                int y0 = (dy * srcHeight) / dstHeight;
                int y1 = Math.min(srcHeight, ((dy + 1) * srcHeight) / dstHeight);
                if (y1 <= y0) y1 = Math.min(srcHeight, y0 + 1);

                int stepY = Math.max(1, (y1 - y0) / 4);

                int rowOffset = dy * stride;
                rawScanlines[rowOffset] = 0;
                int pixelOffset = rowOffset + 1;

                for (int dx = 0; dx < dstWidth; dx++) {
                    int x0 = (dx * srcWidth) / dstWidth;
                    int x1 = Math.min(srcWidth, ((dx + 1) * srcWidth) / dstWidth);
                    if (x1 <= x0) x1 = Math.min(srcWidth, x0 + 1);

                    int stepX = Math.max(1, (x1 - x0) / 4);

                    int sumR = 0, sumG = 0, sumB = 0, count = 0;
                    int darkestLum = 255 * 3 + 1;
                    int darkestR = 255, darkestG = 255, darkestB = 255;

                    for (int sy = y0; sy < y1; sy += stepY) {
                        long rowBase = (long) sy * srcWidth;
                        for (int sx = x0; sx < x1; sx += stepX) {
                            int argb = pixelSegment.get(
                                ValueLayout.JAVA_INT_UNALIGNED,
                                (rowBase + sx) * 4L
                            );

                            int r = (argb >> 16) & 0xFF;
                            int g = (argb >> 8) & 0xFF;
                            int b = argb & 0xFF;

                            sumR += r;
                            sumG += g;
                            sumB += b;
                            count++;

                            int lum = r + g + b;
                            if (lum < darkestLum) {
                                darkestLum = lum;
                                darkestR = r;
                                darkestG = g;
                                darkestB = b;
                            }
                        }
                    }

                    int avgR = sumR / count;
                    int avgG = sumG / count;
                    int avgB = sumB / count;

                    int outR = (darkestR * 65 + avgR * 35) / 100;
                    int outG = (darkestG * 65 + avgG * 35) / 100;
                    int outB = (darkestB * 65 + avgB * 35) / 100;

                    rawScanlines[pixelOffset++] = (byte) outR;
                    rawScanlines[pixelOffset++] = (byte) outG;
                    rawScanlines[pixelOffset++] = (byte) outB;
                }
            }

            writePngFile(rawScanlines, dstWidth, dstHeight, outputPath);

            long sizeBytes = Files.size(outputPath);
            long elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000L;

            AppLogger.info(
                "Önizleme (Thumbnail) üretildi: " + outputPath
                + " [" + dstWidth + "x" + dstHeight
                + ", " + String.format("%.2f KB", sizeBytes / 1024.0)
                + ", " + elapsedMs + " ms]"
            );

            return true;

        } catch (Exception e) {
            AppLogger.error("Önizleme (Thumbnail) üretilemedi: " + outputPath, e);
            return false;
        }
    }

    private static void writePngFile(byte[] rawScanlines, int width, int height, Path outputPath) throws IOException {
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        try (OutputStream fileOut = Files.newOutputStream(outputPath)) {
            fileOut.write(PNG_SIGNATURE);

            ByteArrayOutputStream ihdrData = new ByteArrayOutputStream(13);
            DataOutputStream ihdrOut = new DataOutputStream(ihdrData);
            ihdrOut.writeInt(width);
            ihdrOut.writeInt(height);
            ihdrOut.writeByte(8);
            ihdrOut.writeByte(2);
            ihdrOut.writeByte(0);
            ihdrOut.writeByte(0);
            ihdrOut.writeByte(0);
            writeChunk(fileOut, "IHDR", ihdrData.toByteArray());

            byte[] compressedData = compressZlib(rawScanlines);
            writeChunk(fileOut, "IDAT", compressedData);

            writeChunk(fileOut, "IEND", new byte[0]);
        }
    }

    private static byte[] compressZlib(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(input);
        deflater.finish();

        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length / 4);
        byte[] buffer = new byte[16384];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            out.write(buffer, 0, count);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream dataOut = new DataOutputStream(out);
        dataOut.writeInt(data.length);

        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        dataOut.write(typeBytes);
        dataOut.write(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dataOut.writeInt((int) crc.getValue());
    }
}
