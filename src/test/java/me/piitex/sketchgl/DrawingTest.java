package me.piitex.sketchgl;

import me.piitex.sketchgl.canvas.BrushSettings;
import me.piitex.sketchgl.canvas.Drawing;
import me.piitex.sketchgl.canvas.Tool;
import org.jetbrains.skia.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrawingTest {
    private static final int RED = 0xFFFF0000, BLUE = 0xFF0000FF, WHITE = 0xFFFFFFFF;

    private static BrushSettings settings(Tool tool) {
        return tool.defaults();
    }

    private static void line(Drawing drawing, Tool tool, BrushSettings brush, int color, float x0, float y0, float x1, float y1) {
        drawing.beginStroke(tool, brush, color, x0, y0);
        for (int i = 1; i <= 10; i++) {
            drawing.strokeTo(x0 + (x1 - x0) * i / 10f, y0 + (y1 - y0) * i / 10f);
        }
        drawing.endStroke();
    }

    @Test
    void hardBrushDrawsAnOpaqueLine() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.BRUSH);
            brush.setHardness(1f);
            brush.setSize(10);
            brush.setSmoothing(0);
            line(drawing, Tool.BRUSH, brush, RED, 10, 50, 90, 50);
            assertEquals(RED, drawing.colorAt(50, 50));
            assertEquals(RED, drawing.colorAt(20, 53));
            assertEquals(WHITE, drawing.colorAt(50, 70));
            assertTrue(drawing.isModified());
        }
    }

    @Test
    void softBrushFadesAtTheEdge() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.BRUSH);
            brush.setHardness(0f);
            brush.setSize(30);
            brush.setSmoothing(0);
            line(drawing, Tool.BRUSH, brush, RED, 20, 50, 80, 50);
            int centre = drawing.colorAt(50, 50), edge = drawing.colorAt(50, 62);
            assertEquals(0xFF, centre >> 16 & 0xFF);
            assertTrue((centre >> 8 & 0xFF) < 40, "centre is nearly pure red");
            assertTrue((edge >> 8 & 0xFF) > (centre >> 8 & 0xFF) + 40, "edge is lighter than the centre");
        }
    }

    @Test
    void opacityDoesNotStackWithinOneStroke() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.MARKER);
            brush.setOpacity(0.5f);
            brush.setSize(10);
            brush.setSmoothing(0);
            // A stroke that goes over the same place twice.
            drawing.beginStroke(Tool.MARKER, brush, 0xFF000000, 10, 50);
            for (int i = 0; i <= 20; i++) drawing.strokeTo(10 + 80 * (i <= 10 ? i : 20 - i) / 10f, 50);
            drawing.endStroke();
            int color = drawing.colorAt(50, 50);
            int grey = color >> 8 & 0xFF;
            assertTrue(grey > 110 && grey < 145, "half black over white is mid grey, was " + grey);
        }
    }

    @Test
    void undoAndRedoRestoreThePixels() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.PENCIL);
            brush.setSize(6);
            line(drawing, Tool.PENCIL, brush, RED, 10, 20, 90, 20);
            line(drawing, Tool.PENCIL, brush, BLUE, 10, 60, 90, 60);
            assertEquals(BLUE, drawing.colorAt(50, 60));
            drawing.undo();
            assertEquals(WHITE, drawing.colorAt(50, 60));
            assertEquals(RED, drawing.colorAt(50, 20));
            drawing.redo();
            assertEquals(BLUE, drawing.colorAt(50, 60));
            drawing.undo();
            drawing.undo();
            assertFalse(drawing.canUndo());
            assertEquals(WHITE, drawing.colorAt(50, 20));
        }
    }

    @Test
    void aNewStrokeForgetsTheRedoHistory() {
        try (var drawing = new Drawing(60, 60, false)) {
            var brush = settings(Tool.PENCIL);
            line(drawing, Tool.PENCIL, brush, RED, 5, 10, 50, 10);
            drawing.undo();
            assertTrue(drawing.canRedo());
            line(drawing, Tool.PENCIL, brush, BLUE, 5, 30, 50, 30);
            assertFalse(drawing.canRedo());
        }
    }

    @Test
    void eraserRevealsThePaper() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.BRUSH);
            brush.setHardness(1f);
            brush.setSize(20);
            line(drawing, Tool.BRUSH, brush, RED, 10, 50, 90, 50);
            var eraser = settings(Tool.ERASER);
            eraser.setHardness(1f);
            eraser.setSize(10);
            line(drawing, Tool.ERASER, eraser, 0, 30, 50, 70, 50);
            assertEquals(WHITE, drawing.colorAt(50, 50), "the paper, not a hole, shows through");
            assertEquals(RED, drawing.colorAt(50, 58));
            assertEquals(RED, drawing.colorAt(15, 50));
        }
    }

    @Test
    void eraserOnTransparentPaperMakesItTransparent() {
        try (var drawing = new Drawing(100, 100, true)) {
            var brush = settings(Tool.BRUSH);
            brush.setHardness(1f);
            brush.setSize(20);
            line(drawing, Tool.BRUSH, brush, RED, 10, 50, 90, 50);
            var eraser = settings(Tool.ERASER);
            eraser.setHardness(1f);
            eraser.setSize(10);
            line(drawing, Tool.ERASER, eraser, 0, 30, 50, 70, 50);
            assertEquals(0, drawing.colorAt(50, 50) >>> 24);
        }
    }

    @Test
    void fillStopsAtLines() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.PENCIL);
            brush.setSize(3);
            // A closed box from (20,20) to (80,80).
            line(drawing, Tool.PENCIL, brush, 0xFF000000, 20, 20, 80, 20);
            line(drawing, Tool.PENCIL, brush, 0xFF000000, 80, 20, 80, 80);
            line(drawing, Tool.PENCIL, brush, 0xFF000000, 80, 80, 20, 80);
            line(drawing, Tool.PENCIL, brush, 0xFF000000, 20, 80, 20, 20);
            drawing.fill(50, 50, BLUE, 1f, 0.1f);
            assertEquals(BLUE, drawing.colorAt(50, 50));
            assertEquals(BLUE, drawing.colorAt(25, 25));
            assertEquals(WHITE, drawing.colorAt(5, 5), "outside the box is untouched");
            assertEquals(0xFF000000, drawing.colorAt(50, 20) | 0, "the line itself is still black");
            drawing.undo();
            assertEquals(WHITE, drawing.colorAt(50, 50));
        }
    }

    @Test
    void sprayMakesSpecklesInsideItsRadius() {
        try (var drawing = new Drawing(200, 200, false)) {
            var brush = settings(Tool.SPRAY);
            brush.setSize(60);
            drawing.beginStroke(Tool.SPRAY, brush, 0xFF000000, 100, 100);
            for (int i = 0; i < 30; i++) drawing.hold(1f / 60f);
            drawing.endStroke();
            int inside = 0;
            for (int x = 70; x <= 130; x++) {
                for (int y = 70; y <= 130; y++) if ((drawing.colorAt(x, y) & 0xFF) < 200) inside++;
            }
            assertTrue(inside > 100, "some speckles, was " + inside);
            assertEquals(WHITE, drawing.colorAt(20, 20));
        }
    }

    @Test
    void clearAndPngRoundTrip() {
        try (var drawing = new Drawing(64, 48, false)) {
            var brush = settings(Tool.PENCIL);
            brush.setSize(8);
            line(drawing, Tool.PENCIL, brush, RED, 8, 24, 56, 24);
            byte[] png = drawing.toPng();
            try (var back = Drawing.of(Image.Companion.makeFromEncoded(png))) {
                assertEquals(64, back.getWidth());
                assertEquals(48, back.getHeight());
                assertEquals(RED, back.colorAt(30, 24));
                assertEquals(WHITE, back.colorAt(30, 5));
            }
            drawing.clear();
            assertEquals(WHITE, drawing.colorAt(30, 24));
            drawing.undo();
            assertEquals(RED, drawing.colorAt(30, 24));
        }
    }

    @Test
    void growingAtTheTopLeftKeepsThePixelsWhereTheyAre() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.PENCIL);
            brush.setSize(6);
            line(drawing, Tool.PENCIL, brush, RED, 10, 50, 90, 50);
            assertTrue(drawing.resize(new Drawing.Box(-40, -20, 100, 100)));
            assertEquals(140, drawing.getWidth());
            assertEquals(120, drawing.getHeight());
            assertEquals(RED, drawing.colorAt(50 + 40, 50 + 20), "the line moved with the picture'brush origin");
            assertEquals(WHITE, drawing.colorAt(5, 5), "the new paper is blank");
            drawing.undo();
            assertEquals(100, drawing.getWidth());
            assertEquals(100, drawing.getHeight());
            assertEquals(RED, drawing.colorAt(50, 50));
            drawing.redo();
            assertEquals(140, drawing.getWidth());
            assertEquals(RED, drawing.colorAt(90, 70));
        }
    }

    @Test
    void cuttingThePictureCanBeUndoneWithItsPixels() {
        try (var drawing = new Drawing(100, 100, false)) {
            var brush = settings(Tool.PENCIL);
            brush.setSize(6);
            line(drawing, Tool.PENCIL, brush, RED, 10, 80, 90, 80);
            assertTrue(drawing.resize(new Drawing.Box(0, 0, 60, 60)));
            assertEquals(60, drawing.getWidth());
            assertEquals(60, drawing.getHeight());
            assertTrue(drawing.isModified());
            drawing.undo();
            assertEquals(100, drawing.getWidth());
            assertEquals(RED, drawing.colorAt(50, 80), "the part that was cut off is back");
            drawing.redo();
            assertEquals(60, drawing.getHeight());
        }
    }

    @Test
    void resizingGrowsOnTheRightAndBottomAndPaintsOnTheNewArea() {
        try (var drawing = new Drawing(64, 64, true)) {
            assertTrue(drawing.resize(new Drawing.Box(0, 0, 200, 150)));
            assertTrue(drawing.isTransparent());
            var brush = settings(Tool.PENCIL);
            brush.setSize(4);
            line(drawing, Tool.PENCIL, brush, RED, 20, 140, 190, 140);
            assertEquals(RED, drawing.colorAt(100, 140));
            assertEquals(0, drawing.colorAt(100, 100) >>> 24);
            assertEquals(200, Drawing.of(Image.Companion.makeFromEncoded(drawing.toPng())).getWidth());
        }
    }

    @Test
    void resizeRefusesWhatDoesNotFit() {
        try (var drawing = new Drawing(64, 64, false)) {
            assertFalse(drawing.resize(new Drawing.Box(0, 0, Drawing.MAX_SIDE + 1, 64)));
            assertFalse(drawing.resize(new Drawing.Box(0, 0, 4, 64)));
            assertFalse(drawing.resize(new Drawing.Box(0, 0, Drawing.MAX_SIDE, Drawing.MAX_SIDE)), "too many pixels");
            assertEquals(64, drawing.getWidth());
            assertFalse(drawing.canUndo());
            assertFalse(drawing.isModified());
        }
    }

    @Test
    void cancelLeavesNoTrace() {
        try (var drawing = new Drawing(60, 60, false)) {
            var brush = settings(Tool.PENCIL);
            drawing.beginStroke(Tool.PENCIL, brush, RED, 10, 10);
            drawing.strokeTo(50, 50);
            drawing.cancelStroke();
            assertEquals(WHITE, drawing.colorAt(30, 30));
            assertFalse(drawing.canUndo());
        }
    }

    @Test
    void longHardStrokeHasNoSeamsAndKeepsItsSquareEnds() {
        try (var drawing = new Drawing(600, 200, false)) {
            var brush = settings(Tool.MARKER);
            brush.setSize(20);
            brush.setOpacity(1f);
            brush.setSmoothing(0);
            drawing.beginStroke(Tool.MARKER, brush, RED, 50, 100);
            for (int i = 1; i <= 220; i++) {
                drawing.strokeTo(50 + i * 2f, 100 + (float) Math.sin(i / 20.0) * 30);
                if (i % 7 == 0) drawing.flush();
            }
            drawing.endStroke();
            // Walk the stroke: every pixel on its centre line is solid, however many parts it was sealed in.
            for (int i = 1; i < 220; i++) {
                float x = 50 + i * 2f, y = 100 + (float) Math.sin(i / 20.0) * 30;
                assertEquals(RED, drawing.colorAt(Math.round(x), Math.round(y)), "gap at " + i);
            }
            // The square end sticks out half the width past the last point; the start likewise.
            assertEquals(RED, drawing.colorAt(50 - 8, 100));
            assertEquals(WHITE, drawing.colorAt(50 - 20, 100));
            int endY = Math.round(100 + (float) Math.sin(220 / 20.0) * 30);
            assertEquals(RED, drawing.colorAt(490 + 6, endY));
            assertEquals(WHITE, drawing.colorAt(490 + 20, endY));
        }
    }
}
