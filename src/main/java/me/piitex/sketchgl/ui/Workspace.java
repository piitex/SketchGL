package me.piitex.sketchgl.ui;

import me.piitex.engine.Window;
import me.piitex.engine.ui.containers.Container;
import me.piitex.engine.ui.menu.MenuBarOverlay;
import me.piitex.sketchgl.canvas.Pen;

import static me.piitex.sketchgl.ui.Style.*;

/**
 * Everything in the window: the menu, the tools, the picture, the panel, and the status bar, laid out to fill it.
 */
public class Workspace extends Container {
    private final Window window;
    private final MenuBarOverlay menuBar;
    private final Toolbox toolbox;
    private final CanvasView canvas;
    private final SidePanel panel;
    private final StatusBar status;
    private final Shortcuts shortcuts = new Shortcuts();

    public Workspace(Window window, Pen pen, MenuBarOverlay menuBar) {
        super(0, 0, window.getWindowOptions().getWidth(), window.getWindowOptions().getHeight());
        this.window = window;
        this.menuBar = menuBar;
        getStyling().setBackgroundColor(BACKGROUND);
        getStyling().setBorderThickness(0f);
        getStyling().setCornerRadius(0f);
        canvas = new CanvasView(pen);
        toolbox = new Toolbox(pen);
        panel = new SidePanel(pen);
        status = new StatusBar(canvas, pen);
        addElements(canvas, toolbox, panel, status, shortcuts, menuBar);
    }

    public CanvasView canvas() {
        return canvas;
    }

    public Shortcuts shortcuts() {
        return shortcuts;
    }

    public boolean isMenuNative() {
        return menuBar.isNative();
    }

    public void layout() {
        double width = window.getWindowOptions().getWidth(), height = window.getWindowOptions().getHeight();
        setWidth(width);
        setHeight(height);
        menuBar.setWidth(width);
        double top = menuBar.getHeight(), middle = height - top - StatusBar.HEIGHT;
        toolbox.setBounds(0, top, Toolbox.WIDTH, middle);
        panel.setBounds(width - SidePanel.WIDTH, top, SidePanel.WIDTH, middle);
        canvas.layoutIn(Toolbox.WIDTH, top, Math.max(100, width - Toolbox.WIDTH - SidePanel.WIDTH), middle);
        status.setPosition(0, height - StatusBar.HEIGHT);
        status.layout(width);
    }
}
