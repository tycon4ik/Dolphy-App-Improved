package com.droid.dolphy.util;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * GifDecoder — простая утилита для разбития анимированного GIF на кадры.
 * Основана на открытой реализации (GifDecoder.java by gumble).
 *
 * Использование:
 *   GifDecoder decoder = new GifDecoder();
 *   decoder.read(inputStream);
 *   for (int i = 0; i < decoder.getFrameCount(); i++) {
 *       Bitmap frame = decoder.getFrame(i);
 *       int delayMs = decoder.getDelay(i);
 *   }
 */
public class GifDecoder {

    private static final int MAX_STACK_SIZE = 4096;
    private static final int NULL_CODE = -1;

    private final List<Bitmap> frames = new ArrayList<>();
    private final List<Integer> delays = new ArrayList<>();
    private int width;
    private int height;

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getFrameCount() { return frames.size(); }

    public Bitmap getFrame(int index) {
        if (index < 0 || index >= frames.size()) return null;
        return frames.get(index);
    }

    public int getDelay(int index) {
        if (index < 0 || index >= delays.size()) return 100;
        return delays.get(index);
    }

    /** Читает GIF из потока и декодирует все кадры. */
    public int read(InputStream is) {
        try {
            byte[] data = readAll(is);
            return parse(data);
        } catch (Exception e) {
            return -1;
        }
    }

    private byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    // ==================== GIF PARSER ====================

    private int parse(byte[] data) {
        int p = 0;
        // Header (6 bytes)
        if (data.length < 13) return -1;
        String header = new String(data, 0, 6);
        if (!header.startsWith("GIF")) return -1;
        p = 6;

        // Logical Screen Descriptor
        width = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
        height = (data[p + 2] & 0xFF) | ((data[p + 3] & 0xFF) << 8);
        int packed = data[p + 4] & 0xFF;
        boolean gctFlag = (packed & 0x80) != 0;
        int gctSize = 2 << (packed & 7);
        int bgColorIndex = data[p + 5] & 0xFF;
        p += 7;

        int[] gct = null;
        if (gctFlag) {
            gct = new int[gctSize];
            for (int i = 0; i < gctSize; i++) {
                int r = data[p++] & 0xFF;
                int g = data[p++] & 0xFF;
                int b = data[p++] & 0xFF;
                gct[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        int[] act = gct;
        int[] lct = null;
        int[] lastImage = null;
        int[] canvas = new int[width * height];
        int[] previous = null;

        int transIndex = -1;
        int disposal = 0;
        int[] frameBefore = null;

        while (p < data.length) {
            int block = data[p++] & 0xFF;
            if (block == 0x3B) break; // Trailer

            if (block == 0x21) { // Extension
                int label = data[p++] & 0xFF;
                if (label == 0xF9) { // Graphic Control Extension
                    int size = data[p++] & 0xFF;
                    int flags = data[p++] & 0xFF;
                    disposal = (flags >> 2) & 7;
                    int delay = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
                    delays.add(delay * 10); // в миллисекунды (delay в сотых долях секунды)
                    int tIdx = data[p + 2] & 0xFF;
                    transIndex = ((flags & 1) != 0) ? tIdx : -1;
                    p += 3;
                    if (data[p] == 0) p++; // Block terminator
                } else {
                    // Skip sub-blocks
                    while (p < data.length && data[p] != 0) {
                        int size = data[p++] & 0xFF;
                        p += size;
                    }
                    if (p < data.length) p++;
                }
            } else if (block == 0x2C) { // Image Descriptor
                int ix = (data[p] & 0xFF) | ((data[p + 1] & 0xFF) << 8);
                int iy = (data[p + 2] & 0xFF) | ((data[p + 3] & 0xFF) << 8);
                int iw = (data[p + 4] & 0xFF) | ((data[p + 5] & 0xFF) << 8);
                int ih = (data[p + 6] & 0xFF) | ((data[p + 7] & 0xFF) << 8);
                int ipacked = data[p + 8] & 0xFF;
                p += 9;

                boolean lctFlag = (ipacked & 0x80) != 0;
                boolean interlace = (ipacked & 0x40) != 0;
                int lctSize = 2 << (ipacked & 7);
                int[] lctLocal = null;
                if (lctFlag) {
                    lctLocal = new int[lctSize];
                    for (int i = 0; i < lctSize; i++) {
                        int r = data[p++] & 0xFF;
                        int g = data[p++] & 0xFF;
                        int b = data[p++] & 0xFF;
                        lctLocal[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    }
                }

                // LZW min code size
                int lzwMinCodeSize = data[p++] & 0xFF;

                // Read LZW sub-blocks
                java.io.ByteArrayOutputStream lzwOut = new java.io.ByteArrayOutputStream();
                while (p < data.length) {
                    int sz = data[p++] & 0xFF;
                    if (sz == 0) break;
                    lzwOut.write(data, p, sz);
                    p += sz;
                }
                byte[] lzwData = lzwOut.toByteArray();

                // LZW decode
                int[] pixels = lzwDecode(lzwMinCodeSize, lzwData, iw * ih);
                if (interlace) pixels = deinterlace(pixels, iw, ih);

                // Apply color table
                int[] colorTable = lctLocal != null ? lctLocal : (act != null ? act : gct);

                // Save canvas state for disposal
                int[] framePixels = new int[iw * ih];
                for (int i = 0; i < iw * ih; i++) {
                    int idx = pixels[i];
                    int argb = (idx >= 0 && idx < colorTable.length) ? colorTable[idx] : 0;
                    if (idx == transIndex) argb = 0; // transparent
                    framePixels[i] = argb;
                }

                // Compose into canvas
                if (disposal == 3 && frameBefore != null) {
                    System.arraycopy(frameBefore, 0, canvas, 0, canvas.length);
                }
                for (int y = 0; y < ih; y++) {
                    for (int x = 0; x < iw; x++) {
                        int px = ix + x;
                        int py = iy + y;
                        if (px < width && py < height) {
                            int argb = framePixels[y * iw + x];
                            if ((argb >>> 24) != 0) {
                                canvas[py * width + px] = argb;
                            }
                        }
                    }
                }

                Bitmap bmp = Bitmap.createBitmap(canvas, width, height, Config.ARGB_8888);
                frames.add(bmp);

                // Save for disposal method 3 (restore to previous)
                if (disposal == 3) {
                    frameBefore = new int[canvas.length];
                    System.arraycopy(canvas, 0, frameBefore, 0, canvas.length);
                } else if (disposal == 2) {
                    // restore to background color
                    for (int y = 0; y < ih; y++) {
                        for (int x = 0; x < iw; x++) {
                            int px = ix + x;
                            int py = iy + y;
                            if (px < width && py < height) {
                                canvas[py * width + px] = 0;
                            }
                        }
                    }
                }
            } else {
                // Unknown block, skip
                p++;
            }
        }

        return 0;
    }

    // ==================== LZW DECODE ====================

    private int[] lzwDecode(int minCodeSize, byte[] data, int pixelCount) {
        int clearCode = 1 << minCodeSize;
        int eoiCode = clearCode + 1;
        int codeSize = minCodeSize + 1;
        int nextCode = eoiCode + 1;

        int[][] prefix = new int[4096][];
        byte[][] suffix = new byte[4096][];
        byte[][] firstByte = new byte[4096][];

        for (int i = 0; i < clearCode; i++) {
            prefix[i] = new int[1];
            suffix[i] = new byte[]{(byte) i};
            firstByte[i] = new byte[]{(byte) i};
        }
        prefix[clearCode] = new int[0];
        suffix[clearCode] = new byte[0];
        firstByte[clearCode] = new byte[0];
        prefix[eoiCode] = new int[0];
        suffix[eoiCode] = new byte[0];
        firstByte[eoiCode] = new byte[0];

        int[] pixels = new int[pixelCount];
        int pixIdx = 0;

        int code = 0;
        int codeMask = (1 << codeSize) - 1;
        int codeBuffer = 0;
        int codeBufferSize = 0;
        int dataIdx = 0;
        int oldCode = -1;

        byte[] buf = new byte[4096];
        int bufLen = 0;

        while (pixIdx < pixelCount) {
            // Read code
            while (codeBufferSize < codeSize) {
                if (dataIdx >= data.length) break;
                codeBuffer |= (data[dataIdx++] & 0xFF) << codeBufferSize;
                codeBufferSize += 8;
            }
            if (codeBufferSize < codeSize) break;
            code = codeBuffer & codeMask;
            codeBuffer >>= codeSize;
            codeBufferSize -= codeSize;

            if (code == clearCode) {
                codeSize = minCodeSize + 1;
                codeMask = (1 << codeSize) - 1;
                nextCode = eoiCode + 1;
                oldCode = -1;
                continue;
            }
            if (code == eoiCode) break;

            if (oldCode == -1) {
                if (code < nextCode) {
                    // Output suffix of code
                    for (byte b : suffix[code]) pixels[pixIdx++] = b & 0xFF;
                    oldCode = code;
                } else {
                    break;
                }
                continue;
            }

            if (code < nextCode) {
                // Output suffix of code
                for (byte b : suffix[code]) {
                    if (pixIdx < pixelCount) pixels[pixIdx++] = b & 0xFF;
                }
                // New entry
                prefix[nextCode] = prefix[oldCode];
                byte[] newSuffix = new byte[1];
                newSuffix[0] = suffix[code][0];
                suffix[nextCode] = concat(suffix[oldCode], newSuffix);
                if (nextCode < 4096) nextCode++;
            } else {
                // Code not yet defined (KwKwK case)
                byte[] newSuffix = new byte[suffix[oldCode].length + 1];
                System.arraycopy(suffix[oldCode], 0, newSuffix, 0, suffix[oldCode].length);
                newSuffix[newSuffix.length - 1] = suffix[oldCode][0];
                for (byte b : newSuffix) {
                    if (pixIdx < pixelCount) pixels[pixIdx++] = b & 0xFF;
                }
                prefix[nextCode] = prefix[oldCode];
                suffix[nextCode] = newSuffix;
                if (nextCode < 4096) nextCode++;
            }

            if (nextCode > codeMask && codeSize < 12) {
                codeSize++;
                codeMask = (1 << codeSize) - 1;
            }

            oldCode = code;
        }

        return pixels;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private int[] deinterlace(int[] pixels, int w, int h) {
        int[] result = new int[pixels.length];
        int pass = 0;
        int[] starts = {0, 4, 2, 1};
        int[] steps = {8, 8, 4, 2};
        int idx = 0;
        for (int i = 0; i < 4; i++) {
            int y = starts[i];
            int step = steps[i];
            for (; y < h; y += step) {
                for (int x = 0; x < w; x++) {
                    if (idx < pixels.length) {
                        result[y * w + x] = pixels[idx++];
                    }
                }
            }
        }
        return result;
    }
}
