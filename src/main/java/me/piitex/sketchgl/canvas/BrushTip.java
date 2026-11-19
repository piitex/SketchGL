package me.piitex.sketchgl.canvas;

import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.Image;
import org.jetbrains.skia.ImageInfo;

/**
 * The soft round dab that a brush stamps along its path. It is made pixel by pixel, so no gradient shader is needed.
 */
final class BrushTip {

    private record Key(float size, float hardness, int argb) {

    }

    private static Key lastKey;
    private static Image last;

    private BrushTip() {
    }

    /**
     * The dab for a brush this wide, its edge fading from {@code hardness} (0 to 1) of the radius outwards.
     */
    static synchronized Image soft(float size, float hardness, int argb) {
        Key key = new Key(size, hardness, argb);
        if (!key.equals(lastKey)) {
            if (last != null) last.close();
            last = build(size, hardness, argb);
            lastKey = key;
        }
        return last;
    }

    static int side(float size) {
        return (int) Math.ceil(size) + 4;
    }

    private static Image build(float size, float hardness, int argb) {
        int side = side(size);
        float radius = size / 2f;
        float centre = side / 2f;
        int red = argb >> 16 & 0xFF, green = argb >> 8 & 0xFF, blue = argb & 0xFF;
        byte[] pixels = new byte[side * side * 4];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                float distance = (float) Math.hypot(x + 0.5f - centre, y + 0.5f - centre);
                float edge = Math.clamp((radius - distance) + 0.5f, 0f, 1f); // one pixel of anti-aliasing
                float ratio = distance / radius;
                float fade = 1f;
                if (ratio > hardness) {
                    float blend = Math.min(1f, (ratio - hardness) / Math.max(0.0001f, 1f - hardness));
                    fade = 1f - blend * blend * (3f - 2f * blend);
                }
                float alpha = edge * fade;
                int index = (y * side + x) * 4;
                pixels[index] = (byte) Math.round(red * alpha);
                pixels[index + 1] = (byte) Math.round(green * alpha);
                pixels[index + 2] = (byte) Math.round(blue * alpha);
                pixels[index + 3] = (byte) Math.round(255 * alpha);
            }
        }
        ImageInfo info = new ImageInfo(side, side, ColorType.RGBA_8888, ColorAlphaType.PREMUL);
        return Image.Companion.makeRaster(info, pixels, side * 4);
    }
}
