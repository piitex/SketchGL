package me.piitex.sketchgl.canvas;

/**
 * The numbers a tool draws with. Size is in canvas pixels; the rest run from 0 to 1.
 */
public final class BrushSettings {
    public static final float MIN_SIZE = 1f, MAX_SIZE = 300f;

    private float size;
    private float opacity;
    private float hardness;
    private float smoothing;

    public BrushSettings(float size, float opacity, float hardness, float smoothing) {
        setSize(size);
        setOpacity(opacity);
        setHardness(hardness);
        setSmoothing(smoothing);
    }

    public BrushSettings copy() {
        return new BrushSettings(size, opacity, hardness, smoothing);
    }

    public float getSize() {
        return size;
    }

    public void setSize(float size) {
        this.size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = Math.clamp(opacity, 0.01f, 1f);
    }

    /**
     * 1 is a crisp edge, 0 fades from the middle. For the fill tool this is the tolerance.
     */
    public float getHardness() {
        return hardness;
    }

    public void setHardness(float hardness) {
        this.hardness = Math.clamp(hardness, 0f, 1f);
    }

    /**
     * 0 follows the mouse exactly, 1 trails behind it and rounds off wobbles.
     */
    public float getSmoothing() {
        return smoothing;
    }

    public void setSmoothing(float smoothing) {
        this.smoothing = Math.clamp(smoothing, 0f, 1f);
    }
}
