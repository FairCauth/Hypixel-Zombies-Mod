package com.example.client.integration;

import com.example.client.skia.render.dynamicisland.IslandLiveData;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.ByteArrayInputStream;

/** Bounded cover sampling on the media worker; no image decoding on each game frame. */
public final class CoverPalette {
    private CoverPalette() {}

    public static IslandLiveData.Palette extract(byte[] cover) {
        if (cover == null) return IslandLiveData.Palette.DEFAULT;
        try {
            var image = ImageIO.read(new ByteArrayInputStream(cover));
            if (image == null || image.getWidth() < 1 || image.getHeight() < 1) return IslandLiveData.Palette.DEFAULT;
            double[] weights = new double[24], reds = new double[24], greens = new double[24], blues = new double[24];
            for (int iy = 0; iy < 32; iy++) for (int ix = 0; ix < 32; ix++) {
                int pixel = image.getRGB(Math.min(image.getWidth() - 1, ix * image.getWidth() / 32),
                        Math.min(image.getHeight() - 1, iy * image.getHeight() / 32));
                if ((pixel >>> 24) < 128) continue;
                int r = pixel >> 16 & 255, g = pixel >> 8 & 255, b = pixel & 255;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                if (hsb[1] < .12f || hsb[2] < .10f || hsb[2] > .96f) continue;
                int bin = Math.min(23, (int) (hsb[0] * 24));
                double weight = .2 + hsb[1] * hsb[1];
                weights[bin] += weight; reds[bin] += r * weight; greens[bin] += g * weight; blues[bin] += b * weight;
            }
            int first = 0;
            for (int i = 1; i < 24; i++) if (weights[i] > weights[first]) first = i;
            if (weights[first] == 0) return IslandLiveData.Palette.DEFAULT;
            int second = first;
            for (int i = 0; i < 24; i++) {
                int distance = Math.min(Math.abs(i - first), 24 - Math.abs(i - first));
                if (distance >= 3 && (second == first || weights[i] > weights[second])) second = i;
            }
            int start = color(first, weights, reds, greens, blues);
            int end = weights[second] >= weights[first] * .18 ? color(second, weights, reds, greens, blues) : soften(start);
            return new IslandLiveData.Palette(start, end);
        } catch (Exception ignored) { return IslandLiveData.Palette.DEFAULT; }
    }

    private static int color(int bin, double[] weights, double[] reds, double[] greens, double[] blues) {
        float[] hsb = Color.RGBtoHSB((int) (reds[bin] / weights[bin]), (int) (greens[bin] / weights[bin]), (int) (blues[bin] / weights[bin]), null);
        return Color.HSBtoRGB(hsb[0], Math.clamp(hsb[1], .28f, .65f), Math.max(.85f, hsb[2]));
    }
    private static int soften(int color) {
        float[] hsb = Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
        return Color.HSBtoRGB(hsb[0], hsb[1] * .55f, .98f);
    }
}
