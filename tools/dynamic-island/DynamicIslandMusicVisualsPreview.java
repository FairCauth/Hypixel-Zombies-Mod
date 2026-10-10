import com.example.client.integration.CoverPalette;
import com.example.client.skia.render.dynamicisland.*;
import io.github.humbleui.skija.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Real Skia visual verification; audio levels and timeline here are explicit deterministic fixtures. */
public final class DynamicIslandMusicVisualsPreview {
    private static final IslandLiveData.Palette COLORS = new IslandLiveData.Palette(0xFFFF8A73, 0xFFAA9BFF);
    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/dynamic-island-preview/music-visuals"); Files.createDirectories(output);
        verifyPalette();
        try (DynamicIsland island = new DynamicIsland(); Surface target = Surface.makeRasterN32Premul(900, 440)) {
            island.endTask("demo-main");
            island.updateLiveTask("music", song(true, 240, 120), 10);
            island.setMusicVisuals(null, COLORS); island.advance(2);
            BufferedImage quiet = draw(island, target, output.resolve("silent.png"));
            island.advance(.2);
            BufferedImage silentLater = draw(island, target, output.resolve("silent-later.png"));
            require(coloredBarPixels(quiet) == coloredBarPixels(silentLater), "silent music does not run synthetic sine animation");
            for (int i = 0; i < 12; i++) {
                island.setMusicVisuals(new IslandLiveData.Audio(.8f, true, System.nanoTime()), COLORS);
                island.advance(1. / 30);
            }
            BufferedImage loud = draw(island, target, output.resolve("loud.png"));
            require(coloredBarPixels(loud) > coloredBarPixels(quiet) * 2, "real loudness changes bar height");
            for (int i = 0; i < 20; i++) {
                island.setMusicVisuals(new IslandLiveData.Audio(.08f, true, System.nanoTime()), COLORS);
                island.advance(1. / 30);
            }
            BufferedImage low = draw(island, target, output.resolve("beat-low.png"));
            require(coloredBarPixels(loud) > coloredBarPixels(low) * 3, "a real level drop creates a pronounced contraction");
            for (int i = 0; i < 8; i++) {
                island.setMusicVisuals(new IslandLiveData.Audio(.8f, true, System.nanoTime()), COLORS);
                island.advance(1. / 30);
            }
            BufferedImage beat = draw(island, target, output.resolve("beat-rise.png"));
            require(coloredBarPixels(beat) > coloredBarPixels(low) * 2, "rising loudness snaps bars upward");
            require(columnHeight(beat, 560) > columnHeight(beat, 544) + 12, "sample delays make bars visibly different on a beat");
            island.updateLiveTask("music", song(false, 240, 120), 10);
            BufferedImage paused = draw(island, target, output.resolve("paused.png"));
            require(coloredBarPixels(paused) <= coloredBarPixels(quiet) + 10, "paused metadata flattens bars even when meter is loud");
            island.updateLiveTask("music", song(true, 240, 120), 10);
            island.setMusicVisuals(null, COLORS); island.advance(1);
            require(coloredBarPixels(draw(island, target, output.resolve("unavailable.png"))) <= coloredBarPixels(quiet) + 10,
                    "unavailable meter decays to flat bars");
            island.expandTask("music"); island.advance(1);
            BufferedImage known = draw(island, target, output.resolve("progress-known.png"));
            // Expanded width=350, left=50; progress row x=70..380, y=102 in logical coordinates.
            require(isColorful(known.getRGB(220, 204)) && !isColorful(known.getRGB(600, 204)),
                    "120/240s fills half the progress bar");
            require(hasOpaqueText(known, 140, 226, 198, 243), "known timeline displays elapsed time");
            require(!hasOpaqueText(known, 200, 325, 700, 343), "expanded footer has no shortcut text");
            island.updateLiveTask("music", song(true, 0, 0), 10);
            draw(island, target, output.resolve("progress-unknown.png"));
        }
        try (DynamicIsland island = new DynamicIsland(); Surface target = Surface.makeRasterN32Premul(900, 440)) {
            island.endTask("demo-main"); island.updateLiveTask("music", song(true, 240, 120), 10);
            island.setMusicVisuals(null, COLORS); island.advance(2);
            BufferedImage silent = draw(island, target, output.resolve("weak-silent.png"));
            for (int i = 0; i < 20; i++) {
                island.setMusicVisuals(new IslandLiveData.Audio(.008f, true, System.nanoTime()), COLORS);
                island.advance(1. / 30);
            }
            require(coloredBarPixels(draw(island, target, output.resolve("weak-volume.png"))) > coloredBarPixels(silent) * 2,
                    "quiet real audio still produces a readable response");
            for (int i = 0; i < 30; i++) {
                island.setMusicVisuals(new IslandLiveData.Audio(0, true, System.nanoTime()), COLORS);
                island.advance(1. / 30);
            }
            require(coloredBarPixels(draw(island, target, output.resolve("zero-volume.png"))) <= coloredBarPixels(silent) + 10,
                    "an available but silent meter stays flat");
        }
        System.out.println("Music visual checks passed: actual loudness response, silence/pause/stale meter, cover palette, gradient, known/unknown progress and removed footer.");
    }
    private static IslandLiveData.Music song(boolean playing, double duration, double position) {
        return new IslandLiveData.Music("在希望田", "朴树", "", playing, position, duration, System.nanoTime(),
                false, "fixture", null, true, true, true);
    }
    private static void verifyPalette() throws Exception {
        BufferedImage red = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<16;y++) for(int x=0;x<16;x++) red.setRGB(x,y,0x00CC3620);
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(); ImageIO.write(red,"png",encoded);
        var palette = CoverPalette.extract(encoded.toByteArray());
        require((palette.start() >> 16 & 255) > (palette.start() & 255) + 60, "red cover gives red palette");
        require(palette.start() != palette.end(), "monochrome cover still has tonal gradient");
        require(CoverPalette.extract(new byte[]{0,1,2}).equals(IslandLiveData.Palette.DEFAULT), "invalid cover uses neutral fallback");
    }
    private static BufferedImage draw(DynamicIsland island, Surface surface, Path file) throws Exception {
        Canvas canvas=surface.getCanvas();canvas.clear(0);int saved=canvas.save();
        try {canvas.scale(2,2);island.draw(canvas,225,12);} finally{canvas.restoreToCount(saved);}
        try(Image image=surface.makeImageSnapshot();Data data=image.encodeToData()){
            byte[] encoded=data.getBytes();Files.write(file,encoded);return ImageIO.read(new ByteArrayInputStream(encoded));
        }
    }
    private static int coloredBarPixels(BufferedImage pixels){
        int count=0;for(int y=30;y<90;y++)for(int x=540;x<588;x++)if(isColorful(pixels.getRGB(x,y)))count++;return count;
    }
    private static boolean isColorful(int c){
        return(c>>>24)>180 && Math.max(c>>16&255,Math.max(c>>8&255,c&255))-Math.min(c>>16&255,Math.min(c>>8&255,c&255))>35;
    }
    private static int columnHeight(BufferedImage pixels, int x) {
        int count = 0; for (int y = 30; y < 90; y++) if (isColorful(pixels.getRGB(x + 2, y))) count++; return count;
    }
    private static boolean hasOpaqueText(BufferedImage image,int x0,int y0,int x1,int y1){
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){int c=image.getRGB(x,y);if((c>>>24)>210&&(c>>16&255)>180&&(c>>8&255)>180&&(c&255)>180)return true;}return false;
    }
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
}
