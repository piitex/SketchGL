package me.piitex.sketchgl.canvas;

/**
 * Everything the user can pick up and use on the canvas. Each tool remembers its own {@link BrushSettings}, which
 * start as the numbers given here.
 */
public enum Tool {
    BRUSH("Brush", "B", Kind.STROKE, 14, 1f, 0.8f, 0.35f),
    PENCIL("Pencil", "P", Kind.STROKE, 2, 1f, 1f, 0.1f),
    MARKER("Marker", "M", Kind.STROKE, 26, 0.55f, 1f, 0.25f),
    SPRAY("Spray", "S", Kind.STROKE, 48, 1f, 1f, 0f),
    ERASER("Eraser", "E", Kind.STROKE, 28, 1f, 1f, 0.2f),
    FILL("Fill", "G", Kind.FILL, 1, 1f, 0.12f, 0f),
    PICKER("Color picker", "I", Kind.PICK, 1, 1f, 1f, 0f),
    HAND("Hand", "H", Kind.PAN, 1, 1f, 1f, 0f);

    public enum Kind { STROKE, FILL, PICK, PAN }

    private final String label;
    private final String key;
    private final Kind kind;
    private final float size, opacity, hardness, smoothing;

    Tool(String label, String key, Kind kind, float size, float opacity, float hardness, float smoothing) {
        this.label = label;
        this.key = key;
        this.kind = kind;
        this.size = size;
        this.opacity = opacity;
        this.hardness = hardness;
        this.smoothing = smoothing;
    }

    public String label() {
        return label;
    }

    public String key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    public boolean strokes() {
        return kind == Kind.STROKE;
    }

    public boolean hasSize() {
        return kind == Kind.STROKE;
    }

    public boolean hasOpacity() {
        return kind == Kind.STROKE || kind == Kind.FILL;
    }

    public String hardnessLabel() {
        return switch (this) {
            case BRUSH, ERASER -> "Hardness";
            case FILL -> "Tolerance";
            default -> null;
        };
    }

    public boolean hasSmoothing() {
        return kind == Kind.STROKE && this != SPRAY;
    }

    public boolean usesColor() {
        return kind == Kind.STROKE && this != ERASER || kind == Kind.FILL;
    }

    public BrushSettings defaults() {
        return new BrushSettings(size, opacity, hardness, smoothing);
    }
}
