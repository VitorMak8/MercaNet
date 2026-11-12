package com.minimercado;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/** Simulação de Pix: gera um "copia e cola" aleatório e o QR Code dele em SVG. Não gera cobrança real. */
public final class Pix {
    private Pix() {}

    public static String payload(double valor) {
        String v = String.format(Locale.US, "%.2f", valor);
        return "00020126580014br.gov.bcb.pix0136" + UUID.randomUUID() + "52040000530398654" + String.format("%02d", v.length()) + v
                + "5802BR5908MERCANET6009SAO PAULO62070503***6304" + Integer.toHexString(new Random().nextInt(0xFFFF)).toUpperCase();
    }

    public static String qrSvg(String texto) throws Exception {
        BitMatrix m = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0);
        int n = m.getWidth();
        StringBuilder sb = new StringBuilder("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 " + n + " " + n + "' shape-rendering='crispEdges'><rect width='100%' height='100%' fill='#fff'/>");
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++)
                if (m.get(x, y)) sb.append("<rect x='").append(x).append("' y='").append(y).append("' width='1' height='1'/>");
        return sb.append("</svg>").toString();
    }
}
