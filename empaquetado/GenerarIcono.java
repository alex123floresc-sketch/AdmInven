import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el ícono de la aplicación (el mismo diseño que publico/icono.svg) y genera:
 * - src/main/resources/inventario/ui/fx/icono.png (ícono de la ventana, 256 px)
 * - empaquetado/icono.ico (ícono del ejecutable de Windows, varias resoluciones)
 * <p>
 * Uso, desde la raíz del proyecto: {@code java empaquetado/GenerarIcono.java}
 */
public class GenerarIcono {

    private static final Color AZUL = new Color(0x2458d6);
    private static final Color AZUL_CLARO = new Color(0x9db6f0);

    public static void main(String[] args) throws IOException {
        ImageIO.write(dibujar(256), "png", Path.of("src/main/resources/inventario/ui/fx/icono.png").toFile());
        List<byte[]> pngs = new ArrayList<>();
        int[] tamanos = {16, 24, 32, 48, 64, 128, 256};
        for (int t : tamanos) {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(dibujar(t), "png", salida);
            pngs.add(salida.toByteArray());
        }
        Files.write(Path.of("empaquetado/icono.ico"), ico(tamanos, pngs));
        System.out.println("Íconos generados.");
    }

    /** El diseño está en una rejilla de 64 × 64, como el SVG, y se escala al tamaño pedido. */
    private static BufferedImage dibujar(int tamano) {
        BufferedImage img = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.transform(AffineTransform.getScaleInstance(tamano / 64.0, tamano / 64.0));

        g.setColor(AZUL);
        g.fill(new RoundRectangle2D.Double(0, 0, 64, 64, 28, 28));

        Path2D caja = new Path2D.Double();
        caja.moveTo(32, 12);
        caja.lineTo(50, 21);
        caja.lineTo(50, 43);
        caja.lineTo(32, 52);
        caja.lineTo(14, 43);
        caja.lineTo(14, 21);
        caja.closePath();
        g.setColor(Color.WHITE);
        g.fill(caja);

        g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.setColor(AZUL);
        Path2D aristas = new Path2D.Double();
        aristas.moveTo(14, 21);
        aristas.lineTo(32, 30);
        aristas.lineTo(50, 21);
        aristas.moveTo(32, 30);
        aristas.lineTo(32, 52);
        g.draw(aristas);

        g.setColor(AZUL_CLARO);
        Path2D cinta = new Path2D.Double();
        cinta.moveTo(23, 16.5);
        cinta.lineTo(41, 25.5);
        g.draw(cinta);
        g.dispose();
        return img;
    }

    /** Archivo .ico con imágenes PNG dentro (formato admitido desde Windows Vista). */
    private static byte[] ico(int[] tamanos, List<byte[]> pngs) {
        int cabecera = 6 + 16 * pngs.size();
        int total = cabecera + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        int desplazamiento = cabecera;
        for (int i = 0; i < pngs.size(); i++) {
            int t = tamanos[i];
            b.put((byte) (t >= 256 ? 0 : t)).put((byte) (t >= 256 ? 0 : t)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(desplazamiento);
            desplazamiento += pngs.get(i).length;
        }
        pngs.forEach(b::put);
        return b.array();
    }
}
