import com.example.client.skia.render.dynamicisland.DynamicIsland;
import com.example.client.skia.render.dynamicisland.DynamicIslandHudRenderer;
import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the transparent HUD renderer without a running Minecraft client. */
public final class DynamicIslandHudPreview {
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/hud");
        Files.createDirectories(output);
        try (DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer();
             Surface surface = Surface.makeRasterN32Premul(1280, 720)) {
            hud.configure(DynamicIsland.Activity.MUSIC, false, false);
            render(hud, surface, 2, output.resolve("compact.png"));
            for (DynamicIsland.Activity activity : DynamicIsland.Activity.values()) {
                hud.configure(activity, true, false);
                hud.advance(1);
                render(hud, surface, 2, output.resolve(activity.label.toLowerCase() + ".png"));
            }
            hud.configure(DynamicIsland.Activity.MUSIC, true, false);
            hud.advance(1);
            hud.configure(DynamicIsland.Activity.MUSIC, false, false);
            hud.advance(0.08);
            render(hud, surface, 2, output.resolve("collapse.png"));
            for (int i = 0; i < 60; i++) {
                hud.configure(DynamicIsland.Activity.values()[i % 3], i % 2 == 0, false);
                hud.advance(1.0 / 60);
                draw(hud, surface, 2);
            }
            hud.configure(DynamicIsland.Activity.CALL, true, true);
            hud.advance(19);
            render(hud, surface, 2, output.resolve("autoplay.png"));
            // Drawing into the world target must preserve existing pixels and transforms.
            Canvas canvas = surface.getCanvas();
            canvas.clear(0xFF6A91C8);
            hud.draw(canvas, 1280, 720, 0.65f, 8);
            try (Paint paint = new Paint().setColor(0xFFFF0000)) {
                canvas.drawRect(Rect.makeXYWH(0, 710, 10, 10), paint);
            }
            BufferedImage image = decode(surface);
            require(image.getRGB(100, 700) == 0xFF6A91C8, "preserves world background");
            require(image.getRGB(5, 715) == 0xFFFF0000, "restores canvas transform");
        }
        try (DynamicIslandHudRenderer hud = new DynamicIslandHudRenderer();
             Surface small = Surface.makeRasterN32Premul(150, 110)) {
            hud.configure(DynamicIsland.Activity.MUSIC, true, false);
            hud.advance(1);
            render(hud, small, 1, output.resolve("small-window.png"));
        }
        System.out.println("HUD checks passed: transparency, top centering, GUI scaling, small window, interrupted animation, autoplay, canvas state, background preservation.");
    }

    private static void draw(DynamicIslandHudRenderer hud, Surface surface, int guiScale) {
        Canvas canvas = surface.getCanvas();
        canvas.clear(0);
        int saved = canvas.save();
        canvas.scale(guiScale, guiScale);
        hud.draw(canvas, surface.getWidth() / (float) guiScale, surface.getHeight() / (float) guiScale, 0.65f, 8);
        canvas.restoreToCount(saved);
        require(canvas.getSaveCount() == 1, "balanced canvas stack");
    }

    private static void render(DynamicIslandHudRenderer hud, Surface surface, int guiScale, Path path) throws Exception {
        draw(hud, surface, guiScale);
        BufferedImage image = decode(surface);
        int width = image.getWidth(), height = image.getHeight();
        int shellAlpha = image.getRGB(width / 2, 10 * guiScale) >>> 24;
        require(shellAlpha > 0 && shellAlpha < 255, "translucent shell at top center");
        require(image.getRGB(width / 2, height - 1) == 0, "no fullscreen background");
        require(image.getRGB(0, height / 2) == 0, "left side stays transparent");
        require(image.getRGB(width - 1, height / 2) == 0, "right side stays transparent");
        ImageIO.write(image, "png", path.toFile());
    }

    private static BufferedImage decode(Surface surface) throws Exception {
        try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
            return ImageIO.read(new ByteArrayInputStream(data.getBytes()));
        }
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
