package me.piitex.sketchgl.canvas;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the user is drawing with: the tool picked, the color, and the settings each tool has kept. The panels change
 * it, the canvas reads it, and listeners hear about every change so the panels stay in step with each other.
 */
public final class Pen {
    private static final int RECENT = 14;

    private final Map<Tool, BrushSettings> settings = new EnumMap<>(Tool.class);
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Integer> recent = new ArrayList<>();
    private Tool tool = Tool.BRUSH;
    private int color = 0xFF000000;

    public Pen() {
        for (Tool tool : Tool.values()) settings.put(tool, tool.defaults());
    }

    public Tool getTool() {
        return tool;
    }

    public void setTool(Tool tool) {
        if (this.tool == tool) return;
        this.tool = tool;
        changed();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int argb) {
        argb |= 0xFF000000;
        if (color == argb) return;
        color = argb;
        changed();
    }

    public BrushSettings settings(Tool tool) {
        return settings.get(tool);
    }

    public BrushSettings settings() {
        return settings.get(tool);
    }

    public List<Integer> recent() {
        return List.copyOf(recent);
    }

    public void used() {
        Integer used = color;
        if (!recent.isEmpty() && recent.getFirst().equals(used)) return;
        recent.remove(used);
        recent.addFirst(used);
        if (recent.size() > RECENT) recent.removeLast();
        changed();
    }

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }

    public void changed() {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
