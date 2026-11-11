import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import javax.swing.ImageIcon;

/**
 * Genera códigos QR (modo byte, corrección M, versiones 1 a 6: hasta 106 bytes) para las direcciones
 * del sistema. Sigue la norma ISO/IEC 18004; la estructura es la de Nayuki (QR Code generator library).
 */
final class CodigoQR {
    // Por versión (índice 1 a 6), con corrección M: bytes de datos, bytes de corrección por bloque, bloques.
    private static final int[] DATOS = {0, 16, 28, 44, 64, 86, 108};
    private static final int[] CORRECCION = {0, 10, 16, 26, 18, 24, 16};
    private static final int[] BLOQUES = {0, 1, 1, 1, 2, 2, 4};
    private static final int[] ALINEACION = {0, 0, 18, 22, 26, 30, 34};

    private final int tamano;
    private final boolean[][] negro;
    private final boolean[][] fijo; // patrones que la máscara no toca

    private CodigoQR(int version) {
        tamano = 17 + 4 * version;
        negro = new boolean[tamano][tamano];
        fijo = new boolean[tamano][tamano];
    }

    /** Módulos del QR: true es negro. [fila][columna]. */
    static boolean[][] generar(String texto) {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= 6 && 12 + 8 * bytes.length > DATOS[version] * 8) {
            version++;
        }
        if (version > 6) {
            throw new IllegalArgumentException("Texto demasiado largo para el QR: " + bytes.length + " bytes.");
        }
        CodigoQR qr = new CodigoQR(version);
        qr.patrones(version);
        qr.colocar(intercalar(datos(bytes, DATOS[version]), version));
        int mejor = 0;
        long menor = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            qr.mascara(m);
            qr.formato(m);
            long p = qr.penalidad();
            if (p < menor) {
                menor = p;
                mejor = m;
            }
            qr.mascara(m); // deshace: la máscara es un XOR
        }
        qr.mascara(mejor);
        qr.formato(mejor);
        return qr.negro;
    }

    /** Imagen del QR con su margen blanco de 4 módulos. */
    static ImageIcon imagen(String texto, int pixelesPorModulo) {
        boolean[][] m = generar(texto);
        int lado = (m.length + 8) * pixelesPorModulo;
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < lado; y++) {
            for (int x = 0; x < lado; x++) {
                int fy = y / pixelesPorModulo - 4, fx = x / pixelesPorModulo - 4;
                boolean n = fy >= 0 && fx >= 0 && fy < m.length && fx < m.length && m[fy][fx];
                img.setRGB(x, y, n ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return new ImageIcon(img);
    }

    private static byte[] datos(byte[] bytes, int capacidad) {
        byte[] r = new byte[capacidad];
        int[] bit = {0};
        poner(r, bit, 0b0100, 4); // modo byte
        poner(r, bit, bytes.length, 8);
        for (byte b : bytes) {
            poner(r, bit, b & 0xFF, 8);
        }
        bit[0] += Math.min(4, capacidad * 8 - bit[0]); // terminador
        int i = (bit[0] + 7) / 8;
        for (boolean ec = true; i < capacidad; i++, ec = !ec) {
            r[i] = (byte) (ec ? 0xEC : 0x11);
        }
        return r;
    }

    private static void poner(byte[] r, int[] bit, int valor, int largo) {
        for (int i = largo - 1; i >= 0; i--, bit[0]++) {
            if (((valor >>> i) & 1) != 0) {
                r[bit[0] >>> 3] |= (byte) (0x80 >>> (bit[0] & 7));
            }
        }
    }

    private static byte[] intercalar(byte[] datos, int version) {
        int bloques = BLOQUES[version], ecc = CORRECCION[version], porBloque = datos.length / bloques;
        byte[] divisor = divisor(ecc);
        byte[][] correccion = new byte[bloques][];
        for (int b = 0; b < bloques; b++) {
            byte[] bloque = new byte[porBloque];
            System.arraycopy(datos, b * porBloque, bloque, 0, porBloque);
            correccion[b] = resto(bloque, divisor);
        }
        byte[] r = new byte[datos.length + bloques * ecc];
        int k = 0;
        for (int i = 0; i < porBloque; i++) {
            for (int b = 0; b < bloques; b++) {
                r[k++] = datos[b * porBloque + i];
            }
        }
        for (int i = 0; i < ecc; i++) {
            for (int b = 0; b < bloques; b++) {
                r[k++] = correccion[b][i];
            }
        }
        return r;
    }

    /** Polinomio generador de Reed-Solomon de grado n sobre GF(256). */
    private static byte[] divisor(int n) {
        byte[] r = new byte[n];
        r[n - 1] = 1;
        int raiz = 1;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                r[j] = (byte) multiplicar(r[j] & 0xFF, raiz);
                if (j + 1 < n) {
                    r[j] ^= r[j + 1];
                }
            }
            raiz = multiplicar(raiz, 0x02);
        }
        return r;
    }

    private static byte[] resto(byte[] datos, byte[] divisor) {
        byte[] r = new byte[divisor.length];
        for (byte b : datos) {
            int factor = (b ^ r[0]) & 0xFF;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) {
                r[i] ^= (byte) multiplicar(divisor[i] & 0xFF, factor);
            }
        }
        return r;
    }

    private static int multiplicar(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private void fijar(int x, int y, boolean n) {
        negro[y][x] = n;
        fijo[y][x] = true;
    }

    private void patrones(int version) {
        for (int i = 0; i < tamano; i++) {
            fijar(6, i, i % 2 == 0);
            fijar(i, 6, i % 2 == 0);
        }
        for (int[] c : new int[][]{{3, 3}, {tamano - 4, 3}, {3, tamano - 4}}) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int d = Math.max(Math.abs(dx), Math.abs(dy)), x = c[0] + dx, y = c[1] + dy;
                    if (x >= 0 && y >= 0 && x < tamano && y < tamano) {
                        fijar(x, y, d != 2 && d != 4);
                    }
                }
            }
        }
        if (version >= 2) {
            int p = ALINEACION[version];
            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    fijar(p + dx, p + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                }
            }
        }
        formato(0); // reserva el lugar; se reescribe con la máscara elegida
    }

    /** Bits de formato: corrección M (00) y la máscara, con su BCH(15,5). */
    private void formato(int mascara) {
        int datos = mascara; // nivel M = 0b00
        int resto = datos;
        for (int i = 0; i < 10; i++) {
            resto = (resto << 1) ^ ((resto >>> 9) * 0x537);
        }
        int bits = (datos << 10 | resto) ^ 0x5412;
        for (int i = 0; i <= 5; i++) {
            fijar(8, i, bit(bits, i));
        }
        fijar(8, 7, bit(bits, 6));
        fijar(8, 8, bit(bits, 7));
        fijar(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            fijar(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            fijar(tamano - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            fijar(8, tamano - 15 + i, bit(bits, i));
        }
        fijar(8, tamano - 8, true); // módulo oscuro
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    /** Recorrido en zigzag por pares de columnas, de abajo a la derecha, saltando la columna 6. */
    private void colocar(byte[] codigo) {
        int i = 0;
        for (int derecha = tamano - 1; derecha >= 1; derecha -= 2) {
            if (derecha == 6) {
                derecha = 5;
            }
            for (int v = 0; v < tamano; v++) {
                for (int j = 0; j < 2; j++) {
                    int x = derecha - j;
                    int y = ((derecha + 1) & 2) == 0 ? tamano - 1 - v : v;
                    if (!fijo[y][x] && i < codigo.length * 8) {
                        negro[y][x] = bit(codigo[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void mascara(int m) {
        for (int y = 0; y < tamano; y++) {
            for (int x = 0; x < tamano; x++) {
                boolean invertir = switch (m) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                if (!fijo[y][x] && invertir) {
                    negro[y][x] = !negro[y][x];
                }
            }
        }
    }

    // shortcut: omite la regla de patrones parecidos a los localizadores; el QR es válido igual, solo elige otra máscara.
    private long penalidad() {
        long p = 0;
        int oscuros = 0;
        for (int a = 0; a < tamano; a++) {
            int filaRacha = 1, colRacha = 1;
            for (int b = 0; b < tamano; b++) {
                oscuros += negro[a][b] ? 1 : 0;
                if (b > 0) {
                    filaRacha = negro[a][b] == negro[a][b - 1] ? filaRacha + 1 : 1;
                    colRacha = negro[b][a] == negro[b - 1][a] ? colRacha + 1 : 1;
                    p += (filaRacha == 5 ? 3 : filaRacha > 5 ? 1 : 0) + (colRacha == 5 ? 3 : colRacha > 5 ? 1 : 0);
                }
                if (a > 0 && b > 0 && negro[a][b] == negro[a - 1][b] && negro[a][b] == negro[a][b - 1]
                        && negro[a][b] == negro[a - 1][b - 1]) {
                    p += 3;
                }
            }
        }
        int total = tamano * tamano;
        p += (Math.abs(oscuros * 20L - total * 10L) + total - 1) / total * 10 - 10;
        return p;
    }
}
