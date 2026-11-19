package me.piitex.sketchgl.ui;

import me.piitex.engine.ui.icons.Feather;
import me.piitex.engine.ui.image.IconAsset;
import me.piitex.sketchgl.canvas.Tool;

/**
 * The icons: the engine's Feather set where it has one that fits, SketchGL's own for the drawing tools.
 */
final class Icons {
    static final IconAsset BRUSH = tool("brush"), MARKER = tool("marker"), SPRAY = tool("spray"),
            ERASER = tool("eraser"), PICKER = tool("picker"), BUCKET = tool("paint-bucket");

    private Icons() {
    }

    private static IconAsset tool(String name) {
        return new IconAsset("icons/tools/" + name + ".svg");
    }

    static IconAsset of(Tool tool) {
        return switch (tool) {
            case BRUSH -> BRUSH;
            case PENCIL -> Feather.EDIT_2;
            case MARKER -> MARKER;
            case SPRAY -> SPRAY;
            case ERASER -> ERASER;
            case FILL -> BUCKET;
            case PICKER -> PICKER;
            case HAND -> Feather.MOVE;
        };
    }
}
