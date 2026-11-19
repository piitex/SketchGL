package me.piitex.sketchgl.ui;

import me.piitex.engine.Window;
import me.piitex.engine.input.Input;
import me.piitex.engine.ui.containers.Container;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Keyboard shortcuts, such as Ctrl+Z or the letter that picks a tool. Ctrl or Cmd works on every platform. They fire
 * only while nothing in the window has the keyboard and no box is open, so typing a size in a field does not change
 * the tool. Holding a key repeats it.
 * <p>
 * It is an empty container added to the window so that it is checked every frame; it draws nothing.
 */
public class Shortcuts extends Container {
    private final List<Shortcut> shortcuts = new ArrayList<>();

    private record Shortcut(int key, boolean command, boolean shift, Runnable action) {
    }

    public Shortcuts() {
        super(0, 0, 0, 0);
    }

    public void add(int key, boolean shift, Runnable action) {
        shortcuts.add(new Shortcut(key, true, shift, action));
    }

    public void addKey(int key, Runnable action) {
        shortcuts.add(new Shortcut(key, false, false, action));
    }

    private Shortcut find(int key, int mods) {
        boolean command = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        for (Shortcut shortcut : shortcuts) {
            if (shortcut.key() == key && shortcut.command() == command && (!command || shortcut.shift() == shift)) return shortcut;
        }
        return null;
    }

    @Override
    public void update(float delta) {
        super.update(delta);
        Window window = getWindow();
        if (window == null || window.getFocusedElement() != null || Dialogs.isOpen()) return;
        Input input = window.getInput();
        int mods = input.getModifiers();
        for (Shortcut shortcut : shortcuts) {
            if (!input.isKeyJustPressed(shortcut.key()) && !input.isKeyRepeated(shortcut.key())) continue;
            if (find(shortcut.key(), mods) == shortcut) shortcut.action().run();
        }
    }
}
