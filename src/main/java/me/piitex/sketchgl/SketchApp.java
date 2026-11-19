package me.piitex.sketchgl;

import me.piitex.engine.Engine;
import me.piitex.engine.Window;
import me.piitex.engine.WindowOptions;
import me.piitex.engine.io.AppEnvironment;
import me.piitex.engine.ui.image.ImageLoader;
import me.piitex.engine.ui.text.Font;
import me.piitex.engine.ui.theme.ThemeManager;
import me.piitex.sketchgl.ui.SketchTheme;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Starts SketchGL, the drawing app.
 */
public class SketchApp {
    static void main() throws IOException {
        Font.setDefaultFont(bundledFont());
        ThemeManager.setCurrent(new SketchTheme());
        Window window = new Window(new WindowOptions("SketchGL")
                .setDimensions(1280, 720)
                .setSplashEnabled(false)
                .setVsync(true)
        );
        new Sketch(window);
        window.setMaximized(true);
        new Engine().start(window);
    }

    /**
     * The font that ships inside the jar, copied to the app's data folder (once) because Skia loads fonts from files. A
     * path into {@code src/main/resources} would only exist when running from the project.
     */
    private static File bundledFont() throws IOException {
        File font = AppEnvironment.create("SketchGL").getDataPath("fonts/Roboto-Regular.ttf").toFile();
        if (!font.exists()) {
            try (InputStream in = SketchApp.class.getResourceAsStream("/Roboto-Regular.ttf")) {
                if (in == null) throw new IOException("Roboto-Regular.ttf is missing from the jar");
                Files.createDirectories(font.toPath().getParent());
                Files.copy(in, font.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return font;
    }
}
