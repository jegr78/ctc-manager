package org.ctc.trailer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.service.SandboxedHtmlRenderer;
import org.ctc.media.job.MediaJobContext;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders every scene layer as a 1920×1080 PNG in the sandboxed browser; all layers except the background are transparent. */
@Component
@RequiredArgsConstructor
public class TrailerSceneRenderer {

    public static final int WIDTH = 1920;
    public static final int HEIGHT = 1080;

    private final TemplateEngine templates;
    private final TrailerSceneModel model;

    public List<TrailerLayer> render(TrailerPlan plan, Path workDir, MediaJobContext ctx) throws IOException {
        Files.createDirectories(workDir);
        var scenes = model.scenes(plan);
        var layers = new ArrayList<TrailerLayer>();
        for (var scene : scenes) {
            ctx.checkNotAbandoned();
            ctx.progress(scene.index() + 1, scenes.size(), "Rendering scene " + (scene.index() + 1) + " of " + scenes.size());
            for (var layer : scene.layers()) {
                var context = new Context();
                context.setVariables(scene.variables());
                context.setVariable("layer", layer);
                String html = templates.process("admin/trailer/" + scene.name(), context);
                Path png = workDir.resolve("%02d-%s-%s.png".formatted(scene.index(), scene.name(), layer));
                SandboxedHtmlRenderer.screenshot(html, WIDTH, HEIGHT, !"background".equals(layer), png);
                layers.add(new TrailerLayer(scene.name(), scene.index(), layer, png, scene.length()));
            }
        }
        return List.copyOf(layers);
    }

    public static Path zipLayers(List<TrailerLayer> layers, Path zipFile) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            for (var layer : layers) {
                Path name = layer.png().getFileName();
                if (name == null) throw new IOException("Layer path has no file name: " + layer.png());
                zip.putNextEntry(new ZipEntry(name.toString()));
                Files.copy(layer.png(), zip);
                zip.closeEntry();
            }
        }
        return zipFile;
    }
}
