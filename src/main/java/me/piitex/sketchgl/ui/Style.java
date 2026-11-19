package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.color.Color;
import me.piitex.engine.ui.overlays.TextOverlay;

/**
 * SketchGL's look in one place: a dark, quiet frame around the picture, with a blue accent.
 */
public final class Style {
    public static final Color WORKSPACE = rgb(0x14161a);
    public static final Color BACKGROUND = rgb(0x1b1e23);
    public static final Color PANEL = rgb(0x22262c);
    public static final Color RAISED = rgb(0x2c313a);
    public static final Color RAISED_HOVER = rgb(0x383e49);
    public static final Color FIELD = rgb(0x191c21);
    public static final Color FIELD_HOVER = rgb(0x1e2228);
    public static final Color BORDER = rgb(0x3a404a);

    public static final Color TEXT = rgb(0xdde1e7);
    public static final Color LABEL = rgb(0x949dab);
    public static final Color DIM = rgb(0x656d7a);
    public static final Color WARNING = rgb(0xf08a70);

    public static final Color ACCENT = rgb(0x4d9bf0);
    public static final Color ACCENT_SOFT = ACCENT.withAlpha(0.24f);
    public static final Color ACCENT_SOFT_HOVER = ACCENT.withAlpha(0.34f);
    public static final Color SELECTION = ACCENT.withAlpha(0.42f);

    public static final float FONT_SMALL = 12f;
    public static final float FONT = 13f;
    public static final float FONT_HEADING = 14f;
    public static final float FONT_DIALOG_TITLE = 17f;

    public static final float CORNER = 6f;

    private Style() {
    }

    public static TextOverlay text(String text, float size, Color color) {
        TextOverlay overlay = new TextOverlay(text, size, color);
        overlay.setTextColor(color);
        return overlay;
    }

    public static Color rgb(int rgb) {
        return new Color(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
    }

    public static int argb(Color color) {
        return 0xFF000000 | Math.round(color.getR() * 255) << 16 | Math.round(color.getG() * 255) << 8 | Math.round(color.getB() * 255);
    }

    public static Color fromArgb(int argb) {
        return new Color((argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f, 1f);
    }
}
