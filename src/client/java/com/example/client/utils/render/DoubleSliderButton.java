package com.example.client.utils.render;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class DoubleSliderButton extends AbstractSliderButton {
    private final String name;
    private final double min;
    private final double max;
    private final double step;
    private final Consumer<Double> onChanged;

    public DoubleSliderButton(
            int x,
            int y,
            int width,
            int height,
            String name,
            double min,
            double max,
            double currentValue,
            double step,
            Consumer<Double> onChanged
    ) {
        super(x, y, width, height, Component.empty(), toSliderValue(min, max, currentValue));

        this.name = name;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChanged = onChanged;

        this.updateMessage();
    }

    @Override
    protected void updateMessage() {
        double realValue = getRealValue();

        this.setMessage(Component.literal(
                name + ": " + String.format("%.2f", realValue)
        ));
    }

    @Override
    protected void applyValue() {
        double realValue = getRealValue();

        if (onChanged != null) {
            onChanged.accept(realValue);
        }
    }

    private double getRealValue() {
        double raw = min + (max - min) * this.value;

        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }

        return clamp(raw, min, max);
    }

    private static double toSliderValue(double min, double max, double value) {
        return clamp((value - min) / (max - min), 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
