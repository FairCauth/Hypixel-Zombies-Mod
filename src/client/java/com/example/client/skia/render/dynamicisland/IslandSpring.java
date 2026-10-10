package com.example.client.skia.render.dynamicisland;

/** Exact damped-spring integration; changing the target preserves momentum. */
public final class IslandSpring {
    private double value;
    private double velocity;
    private double target;
    private final double frequency;
    private final double damping;

    public IslandSpring(double value, double frequency, double damping) {
        this.value = this.target = value;
        this.frequency = frequency;
        this.damping = damping;
    }

    public void target(double target) {
        this.target = target;
    }

    public void advance(double seconds) {
        if (seconds <= 0) return;
        double omega = frequency;
        double decay = damping * omega;
        double damped = omega * Math.sqrt(1 - damping * damping);
        double displacement = value - target;
        double a = displacement;
        double b = (velocity + decay * displacement) / damped;
        double sin = Math.sin(damped * seconds);
        double cos = Math.cos(damped * seconds);
        double envelope = Math.exp(-decay * seconds);
        value = target + envelope * (a * cos + b * sin);
        velocity = envelope * ((b * damped - decay * a) * cos
                - (a * damped + decay * b) * sin);
        if (Math.abs(value - target) < 0.0001 && Math.abs(velocity) < 0.0001) {
            value = target;
            velocity = 0;
        }
    }

    public float value() {
        return (float) value;
    }
}
