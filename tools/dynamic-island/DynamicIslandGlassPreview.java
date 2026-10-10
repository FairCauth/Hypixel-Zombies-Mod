import com.example.client.skia.render.LiquidGlassShader;
import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.DynamicIslandHudRenderer;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Runs the actual glass shader on an immutable background using raster Skia. */
public final class DynamicIslandGlassPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/glass");
        Files.createDirectories(output);
        verifySnapshotMapping();
        verifyGlobalEdges(output);
        try (Surface world = Surface.makeRasterN32Premul(1280, 720);
             Surface target = Surface.makeRasterN32Premul(1280, 720);
             Surface blurTarget = Surface.makeRasterN32Premul(1280, 720);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            Canvas canvas = world.getCanvas();
            try (Shader sky = Shader.makeLinearGradient(0, 0, 1280, 720,
                    new int[]{0xFF30508D, 0xFF81BCC0, 0xFFE7A786});
                 Paint paint = new Paint().setShader(sky)) {
                canvas.drawRect(Rect.makeXYWH(0, 0, 1280, 720), paint);
                paint.setShader(null).setColor(0x50FFFFFF).setStrokeWidth(2);
                for (int x = 0; x < 1280; x += 24) canvas.drawLine(x, 0, x, 720, paint);
                for (int y = 0; y < 720; y += 24) canvas.drawLine(0, y, 1280, y, paint);
            }
            try (Image sharp = world.makeImageSnapshot();
                 Image blurred = blur(sharp, blurTarget, 8)) {
                hud.configure(DynamicIsland.Activity.MUSIC, false, false);
                draw(hud, target, sharp, blurred, 2);
                save(target, output.resolve("compact-grid.png"));
                for (DynamicIsland.Activity activity : DynamicIsland.Activity.values()) {
                    hud.configure(activity, true, false);
                    hud.advance(1);
                    draw(hud, target, sharp, blurred, 2);
                    save(target, output.resolve(activity.label.toLowerCase() + "-grid.png"));
                }
                hud.configure(DynamicIsland.Activity.MUSIC, true, false);
                hud.advance(1);
                draw(hud, target, sharp, blurred, 2);
                BufferedImage before = decode(world), after = decode(target);
                require(before.getRGB(100, 500) == after.getRGB(100, 500), "outside shell unchanged");
                require(before.getRGB(640, 120) != after.getRGB(640, 120), "glass refracts its background");
                require((after.getRGB(640, 120) & 0x00FFFFFF) > 0x00202020, "body is transparent, not black");
                for (int frame = 0; frame < 80; frame++) {
                    if (frame % 9 == 0) hud.configure(DynamicIsland.Activity.values()[frame % 3], frame % 2 == 0, false);
                    hud.advance(1.0 / 60);
                    draw(hud, target, sharp, blurred, 2);
                    require(target.getCanvas().getSaveCount() == 1, "balanced glass canvas stack");
                }
                require(!sharp.isClosed() && !blurred.isClosed(), "borrowed snapshots remain usable");
            }
        }
        if (args.length > 0) renderScreenshot(Path.of(args[0]), output);
        System.out.println("Glass checks passed: colored global rims on dark/gray/bright backgrounds, clear interiors, compact/expanded shapes, shader compilation, translated snapshot mapping, GUI scaling, refraction, background preservation, animation interruptions, image ownership.");
    }

    private static void verifyGlobalEdges(Path output) throws Exception {
        // Each row has an uncolored control, a global panel, and compact/expanded shapes.
        // Neutral backgrounds make this a material check, independent of colorful scenery.
        for (int scale : new int[]{1, 2, 3}) {
            try (Surface world = Surface.makeRasterN32Premul(720 * scale, 450 * scale);
                 Surface target = Surface.makeRasterN32Premul(720 * scale, 450 * scale);
                 Paint paint = new Paint(); LiquidGlassShader shader = new LiquidGlassShader()) {
                int[] backgrounds = {0xFF17202A, 0xFF808080, 0xFFF0F0F0};
                for (int row = 0; row < backgrounds.length; row++) {
                    paint.setColor(backgrounds[row]);
                    world.getCanvas().drawRect(Rect.makeXYWH(0, row * 150 * scale, 720 * scale, 150 * scale), paint);
                }
                try (Image sharp = world.makeImageSnapshot()) {
                    target.getCanvas().drawImage(sharp, 0, 0);
                    int saved = target.getCanvas().save();
                    target.getCanvas().scale(scale, scale);
                    for (int row = 0; row < backgrounds.length; row++) {
                        float y = row * 150 + 18;
                        drawMaterial(shader, target.getCanvas(), sharp, 12, y, 236, 112, 18, 0, scale);
                        drawMaterial(shader, target.getCanvas(), sharp, 260, y, 236, 112, 18,
                                LiquidGlassShader.DEFAULT_EDGE_DISPERSION, scale);
                        drawMaterial(shader, target.getCanvas(), sharp, 528, y, 154, 36, 18,
                                LiquidGlassShader.DEFAULT_EDGE_DISPERSION, scale);
                        drawMaterial(shader, target.getCanvas(), sharp, 528, y + 48, 154, 64, 22,
                                LiquidGlassShader.DEFAULT_EDGE_DISPERSION, scale);
                    }
                    target.getCanvas().restoreToCount(saved);
                    BufferedImage rendered = decode(target);
                    for (int row = 0; row < backgrounds.length; row++) {
                        int y = row * 150 + 18;
                        int control = rendered.getRGB(130 * scale, (y + 56) * scale);
                        int body = rendered.getRGB(378 * scale, (y + 56) * scale);
                        require(control == body, "colored edge leaves the global panel center unchanged");
                        require(rendered.getRGB(130 * scale, (y + 10) * scale)
                                == rendered.getRGB(378 * scale, (y + 10) * scale), "rainbow stays within the narrow rim");
                        int[] edges = {
                                rendered.getRGB(378 * scale, (y + 1) * scale),
                                rendered.getRGB(378 * scale, (y + 110) * scale),
                                rendered.getRGB(261 * scale, (y + 56) * scale),
                                rendered.getRGB(494 * scale, (y + 56) * scale),
                                rendered.getRGB(605 * scale, (y + 1) * scale),
                                rendered.getRGB(605 * scale, (y + 49) * scale)
                        };
                        for (int edge : edges) {
                            // On tinted scenery the rim may neutralize the background hue.
                            // Use neutral gray to measure the material's own saturation.
                            if (row == 1) require(channelRange(edge) >= 6 && channelRange(edge) <= 55,
                                    "neutral background retains a subtle colored rim at scale " + scale
                                            + ": " + Integer.toHexString(edge) + " (range " + channelRange(edge) + ")");
                        }
                        int[] plainEdges = {
                                rendered.getRGB(130 * scale, (y + 1) * scale),
                                rendered.getRGB(130 * scale, (y + 110) * scale),
                                rendered.getRGB(13 * scale, (y + 56) * scale),
                                rendered.getRGB(246 * scale, (y + 56) * scale)
                        };
                        for (int i = 0; i < plainEdges.length; i++) require(colorDifference(edges[i], plainEdges[i]) >= 8,
                                "subtle rim remains visible relative to uncolored glass on background " + row);
                        require(rendered.getRGB(710 * scale, (y + 56) * scale) == backgrounds[row],
                                "global material does not color surrounding pixels");
                    }
                    if (scale == 2) save(target, output.resolve("global-colored-edges.png"));
                }
            }
        }
    }

    private static void drawMaterial(LiquidGlassShader shader, Canvas canvas, Image sharp,
                                     float x, float y, float width, float height, float radius,
                                     float dispersion, int scale) {
        shader.draw(canvas, sharp, sharp, x, y, width, height,
                radius, 8, 0, 3.35f, dispersion, 1, 1, 1, 0.065f, 0.12f, 0.94f,
                scale, scale, 0, 0);
    }

    private static int channelRange(int color) {
        int r = (color >>> 16) & 255, g = (color >>> 8) & 255, b = color & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private static int colorDifference(int a, int b) {
        int r = Math.abs(((a >>> 16) & 255) - ((b >>> 16) & 255));
        int g = Math.abs(((a >>> 8) & 255) - ((b >>> 8) & 255));
        int blue = Math.abs((a & 255) - (b & 255));
        return Math.max(r, Math.max(g, blue));
    }

    private static void verifySnapshotMapping() throws Exception {
        try (Surface world = Surface.makeRasterN32Premul(600, 200);
             Surface target = Surface.makeRasterN32Premul(600, 200);
             Paint paint = new Paint(); LiquidGlassShader shader = new LiquidGlassShader()) {
            world.getCanvas().clear(0xFFFF0000);
            paint.setColor(0xFF0000FF);
            world.getCanvas().drawRect(Rect.makeXYWH(300, 0, 300, 200), paint);
            try (Image sharp = world.makeImageSnapshot()) {
                target.getCanvas().clear(0);
                int saved = target.getCanvas().save();
                target.getCanvas().translate(300, 20).scale(2, 2);
                shader.draw(target.getCanvas(), sharp, sharp, 0, 0, 100, 60,
                        20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 2, 300, 20);
                target.getCanvas().restoreToCount(saved);
                int pixel = decode(target).getRGB(380, 80);
                require((pixel & 255) > 240 && ((pixel >>> 16) & 255) < 10,
                        "translated shell samples local blue world, not red origin");
            }
        }
    }

    private static void renderScreenshot(Path path, Path output) throws Exception {
        BufferedImage source = ImageIO.read(path.toFile());
        // Strip the window title so source coordinates are game framebuffer coordinates.
        BufferedImage game = source.getSubimage(0, 31, source.getWidth(), source.getHeight() - 31);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        ImageIO.write(game, "png", png);
        try (Image sharp = Image.makeFromEncoded(png.toByteArray());
             Surface blurTarget = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             Surface target = Surface.makeRasterN32Premul(game.getWidth(), game.getHeight());
             Image blurred = blur(sharp, blurTarget, 12);
             DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer()) {
            hud.configure(DynamicIsland.Activity.MUSIC, false, false);
            draw(hud, target, sharp, blurred, 3);
            save(target, output.resolve("game-compact-preview.png"));
            hud.configure(DynamicIsland.Activity.MUSIC, true, false);
            hud.advance(1);
            draw(hud, target, sharp, blurred, 3);
            save(target, output.resolve("game-expanded-preview.png"));
            hud.configure(DynamicIsland.Activity.MUSIC, false, false);
            hud.advance(0.08);
            draw(hud, target, sharp, blurred, 3);
            save(target, output.resolve("game-collapse-preview.png"));
        }
    }

    private static Image blur(Image sharp, Surface target, float sigma) {
        try (ImageFilter filter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP);
             Paint paint = new Paint().setImageFilter(filter)) {
            target.getCanvas().drawImage(sharp, 0, 0, paint);
        }
        return target.makeImageSnapshot();
    }

    private static void draw(DynamicIslandHudRenderer hud, Surface target, Image sharp, Image blurred, int guiScale) {
        Canvas canvas = target.getCanvas();
        canvas.clear(0);
        canvas.drawImage(sharp, 0, 0);
        int saved = canvas.save();
        canvas.scale(guiScale, guiScale);
        hud.draw(canvas, target.getWidth() / (float) guiScale, target.getHeight() / (float) guiScale,
                0.65f, 8, sharp, blurred, guiScale, guiScale);
        canvas.restoreToCount(saved);
    }

    private static BufferedImage decode(Surface surface) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
            return ImageIO.read(new ByteArrayInputStream(data.getBytes()));
        }
    }

    private static void save(Surface surface, Path path) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
            Files.write(path, data.getBytes());
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
