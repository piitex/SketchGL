package me.piitex.sketchgl;

import me.piitex.engine.Window;
import me.piitex.engine.input.FileDialog;
import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.menu.MenuItem;
import me.piitex.engine.ui.menu.MenuBarOverlay;
import me.piitex.sketchgl.canvas.BrushSettings;
import me.piitex.sketchgl.canvas.Drawing;
import me.piitex.sketchgl.canvas.Pen;
import me.piitex.sketchgl.canvas.Tool;
import me.piitex.sketchgl.ui.CanvasView;
import me.piitex.sketchgl.ui.Dialogs;
import me.piitex.sketchgl.ui.Shortcuts;
import me.piitex.sketchgl.ui.Workspace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.skia.Image;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The app: owns the window and the picture being drawn, and handles New, Open, Save and the questions they raise.
 */
public class Sketch {
    private static final Logger log = LogManager.getLogger("SketchGL");
    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final int DEFAULT_WIDTH = 1280, DEFAULT_HEIGHT = 800;

    private final Window window;
    private final Pen pen = new Pen();
    private final Workspace workspace;
    private final CanvasView canvas;
    private final MenuItem undoItem, redoItem;
    private Path file;

    public Sketch(Window window) {
        this.window = window;
        MenuBarOverlay menuBar = new MenuBarOverlay(window.getWindowOptions().getWidth());
        undoItem = shortcut(new MenuItem("Undo", this::undo), "Ctrl+Z");
        redoItem = shortcut(new MenuItem("Redo", this::redo), "Ctrl+Shift+Z");
        menuBar.addMenu("File",
                shortcut(new MenuItem("New...", this::newPicture), "Ctrl+N"),
                shortcut(new MenuItem("Open...", this::open), "Ctrl+O"),
                MenuItem.separator(),
                shortcut(new MenuItem("Save", () -> save(null)), "Ctrl+S"),
                shortcut(new MenuItem("Save As...", () -> saveAs(null)), "Ctrl+Shift+S"));
        menuBar.addMenu("Edit", undoItem, redoItem, MenuItem.separator(),
                new MenuItem("Canvas Size...", this::canvasSize), new MenuItem("Clear Picture", this::clear));
        menuBar.addMenu("View",
                new MenuItem("Zoom In", () -> canvas().zoomBy(1.25f)),
                new MenuItem("Zoom Out", () -> canvas().zoomBy(1 / 1.25f)),
                shortcut(new MenuItem("Fit to Window", () -> canvas().fit()), "Ctrl+0"),
                shortcut(new MenuItem("Actual Size", () -> canvas().actualSize()), "Ctrl+1"));

        workspace = new Workspace(window, pen, menuBar);
        canvas = workspace.canvas();
        canvas.onChange(this::changed);
        shortcuts(workspace.shortcuts(), workspace.isMenuNative());
        canvas.setDrawing(new Drawing(DEFAULT_WIDTH, DEFAULT_HEIGHT, false));
        window.addContainer(workspace);
        window.onResize((_, _) -> workspace.layout());
        window.onCloseRequest(() -> guard(window::close));
        workspace.layout();
        changed();
    }

    private CanvasView canvas() {
        return canvas;
    }

    private Drawing drawing() {
        return canvas.getDrawing();
    }

    private static MenuItem shortcut(MenuItem item, String shortcut) {
        item.setShortcut(MAC ? shortcut.replace("Ctrl+", "Cmd+") : shortcut);
        return item;
    }

    private void shortcuts(Shortcuts keys, boolean nativeMenu) {
        if (!nativeMenu) {
            keys.add(GLFW.GLFW_KEY_Z, false, this::undo);
            keys.add(GLFW.GLFW_KEY_Z, true, this::redo);
            keys.add(GLFW.GLFW_KEY_N, false, this::newPicture);
            keys.add(GLFW.GLFW_KEY_O, false, this::open);
            keys.add(GLFW.GLFW_KEY_S, false, () -> save(null));
            keys.add(GLFW.GLFW_KEY_S, true, () -> saveAs(null));
            keys.add(GLFW.GLFW_KEY_0, false, () -> canvas.fit());
            keys.add(GLFW.GLFW_KEY_1, false, () -> canvas.actualSize());
        }
        keys.add(GLFW.GLFW_KEY_Y, false, this::redo);
        for (Tool tool : Tool.values()) {
            keys.addKey(tool.key().charAt(0), () -> pen.setTool(tool)); // GLFW's letter keys are the capital letters
        }
        keys.addKey(GLFW.GLFW_KEY_LEFT_BRACKET, () -> resize(1 / 1.15f, -1));
        keys.addKey(GLFW.GLFW_KEY_RIGHT_BRACKET, () -> resize(1.15f, 1));
        keys.addKey(GLFW.GLFW_KEY_EQUAL, () -> canvas.zoomBy(1.25f));
        keys.addKey(GLFW.GLFW_KEY_MINUS, () -> canvas.zoomBy(1 / 1.25f));
        keys.addKey(GLFW.GLFW_KEY_KP_ADD, () -> canvas.zoomBy(1.25f));
        keys.addKey(GLFW.GLFW_KEY_KP_SUBTRACT, () -> canvas.zoomBy(1 / 1.25f));
    }

    private void resize(float factor, int least) {
        if (!pen.getTool().hasSize()) return;
        BrushSettings settings = pen.settings();
        float size = settings.getSize();
        float next = size * factor;
        settings.setSize(Math.abs(next - size) < 1f ? size + least : next);
        pen.changed();
    }

    private String name() {
        return file == null ? "Untitled" : file.getFileName().toString();
    }

    private void changed() {
        window.setTitle((drawing().isModified() ? "* " : "") + name() + " — SketchGL");
        undoItem.setEnabled(drawing().canUndo());
        redoItem.setEnabled(drawing().canRedo());
    }

    private void undo() {
        if (drawing().isStroking()) return;
        drawing().undo();
        changed();
    }

    private void redo() {
        if (drawing().isStroking()) return;
        drawing().redo();
        changed();
    }

    private void clear() {
        drawing().clear();
        changed();
    }

    private void canvasSize() {
        if (drawing().isStroking()) return;
        Drawing now = drawing();
        Dialogs.canvasSize(window, now.getWidth(), now.getHeight(),
                (width, height) -> canvas.resizePicture(new Drawing.Box(0, 0, width, height)));
    }

    private void guard(Runnable proceed) {
        if (!drawing().isModified()) {
            proceed.run();
            return;
        }
        Dialogs.unsaved(window, name(), () -> save(proceed), proceed);
    }

    private void replace(Drawing next, Path path) {
        Drawing old = drawing();
        canvas.setDrawing(next);
        old.close();
        file = path;
        changed();
    }

    private void newPicture() {
        guard(() -> {
            Drawing now = drawing();
            Dialogs.newPicture(window, now.getWidth(), now.getHeight(),
                    made -> replace(new Drawing(made.width(), made.height(), made.transparent()), null));
        });
    }

    private void open() {
        guard(() -> FileDialog.open()
                .filter("Images", "png", "jpg", "jpeg", "webp", "bmp", "gif")
                .onSelect(path -> {
                    try {
                        Image image = Image.Companion.makeFromEncoded(Files.readAllBytes(path));
                        if (image.getWidth() > Drawing.MAX_SIDE || image.getHeight() > Drawing.MAX_SIDE
                                || (long) image.getWidth() * image.getHeight() > Drawing.MAX_PIXELS) {
                            image.close();
                            Dialogs.tell(window, "That picture is too big", "SketchGL opens pictures up to "
                                    + Drawing.MAX_SIDE + " pixels on a side and " + Drawing.MAX_PIXELS / 1_000_000 + " megapixels in all.");
                            return;
                        }
                        // Only a PNG is saved back to its own file; anything else asks for a new name.
                        replace(Drawing.of(image), path.getFileName().toString().toLowerCase().endsWith(".png") ? path : null);
                        image.close();
                    } catch (IOException | RuntimeException exception) {
                        log.error("Could not open {}", path, exception);
                        Dialogs.tell(window, "Could not open the picture", path.getFileName() + " could not be read as a picture.");
                    }
                })
                .show());
    }

    private void save(Runnable then) {
        if (file == null) {
            saveAs(then);
        } else {
            write(file, then);
        }
    }

    private void saveAs(Runnable then) {
        FileDialog.save()
                .filter("PNG picture", "png")
                .defaultName(file == null ? "Untitled.png" : file.getFileName().toString())
                .onSelect(path -> {
                    Path target = path.getFileName().toString().toLowerCase().endsWith(".png")
                            ? path : path.resolveSibling(path.getFileName() + ".png");
                    write(target, then);
                })
                .show();
    }

    private void write(Path target, Runnable then) {
        try {
            Files.write(target, drawing().toPng());
            file = target;
            drawing().markSaved();
            changed();
            if (then != null) then.run();
        } catch (IOException exception) {
            log.error("Could not save {}", target, exception);
            Dialogs.tell(window, "Could not save the picture", String.valueOf(exception.getMessage()));
        }
    }
}
