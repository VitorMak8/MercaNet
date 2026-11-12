package com.minimercado;

import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Reduz a foto enviada para caber no limite de 1 MB por documento do Firestore. */
public final class Imagens {
    private static final int MAX = 640;
    private Imagens() {}

    public static String reduzir(MultipartFile f) throws Exception {
        if (f == null || f.isEmpty()) return null;
        BufferedImage in = ImageIO.read(f.getInputStream());
        if (in == null) return null; // formato não suportado
        double k = Math.min(1.0, (double) MAX / Math.max(in.getWidth(), in.getHeight()));
        int w = Math.max(1, (int) (in.getWidth() * k)), h = Math.max(1, (int) (in.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(in, 0, 0, w, h, null);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bos);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
    }
}
