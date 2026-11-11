import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Diálogos con el estilo de la app, en lugar de JOptionPane. */
final class Dialogos {
    private static final int SOMBRA = 12;
    private static final int ARCO = 6;

    private Dialogos() {
    }

    static void informar(Component padre, String titulo, String mensaje) {
        new Cuadro(padre, titulo, mensaje, null, "Entendido", null).mostrar();
    }

    static void advertir(Component padre, String titulo, String mensaje) {
        new Cuadro(padre, titulo, mensaje, null, "Entendido", null).mostrar();
    }

    /** True solo si se pulsa confirmar. */
    static boolean confirmar(Component padre, String titulo, String mensaje, String textoSi, String textoNo) {
        Cuadro c = new Cuadro(padre, titulo, mensaje, null, textoSi, textoNo);
        c.mostrar();
        return Boolean.TRUE.equals(c.resultado);
    }

    /**
     * Lista de opciones; null si se cancela.
     * color y nota pueden ser null.
     */
    static <T> T elegir(Component padre, String titulo, String mensaje, T[] opciones, T inicial,
                        Function<T, String> texto, Function<T, Color> color, Function<T, String> nota,
                        String textoAceptar) {
        Cuadro c = crearSelector(padre, titulo, mensaje, opciones, inicial, texto, color, nota, textoAceptar);
        c.mostrar();
        @SuppressWarnings("unchecked")
        T elegido = (T) c.resultado;
        return elegido;
    }

    /** Arma el diálogo de elegir sin mostrarlo. */
    static <T> Cuadro crearSelector(Component padre, String titulo, String mensaje, T[] opciones, T inicial,
                                    Function<T, String> texto, Function<T, Color> color, Function<T, String> nota,
                                    String textoAceptar) {
        List<JRadioButton> radios = new ArrayList<>();
        JPanel lista = new JPanel();
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setOpaque(false);
        ButtonGroup grupo = new ButtonGroup();
        for (T opcion : opciones) {
            JRadioButton radio = new JRadioButton(color == null ? texto.apply(opcion) : "");
            radio.setOpaque(false);
            radio.setFont(Estilo.texto(Font.PLAIN, 15));
            radio.setForeground(Estilo.TINTA);
            radio.setSelected(opcion.equals(inicial));
            radio.getAccessibleContext().setAccessibleName(texto.apply(opcion));
            grupo.add(radio);
            radios.add(radio);

            Fila fila = new Fila(radio);
            if (color != null) {
                JPanel pastilla = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                pastilla.setOpaque(false);
                pastilla.add(Estilo.chip(texto.apply(opcion), color.apply(opcion)));
                fila.add(pastilla, BorderLayout.CENTER);
            }
            String aclaracion = nota == null ? null : nota.apply(opcion);
            if (aclaracion != null) {
                fila.add(Estilo.ayuda(aclaracion), BorderLayout.EAST);
            }
            radio.addItemListener(e -> lista.repaint());
            lista.add(fila);
            lista.add(Box.createVerticalStrut(6));
        }
        if (grupo.getSelection() == null && !radios.isEmpty()) {
            radios.get(0).setSelected(true);
        }
        Cuadro cuadro = new Cuadro(padre, titulo, mensaje, lista, textoAceptar, "Cancelar");
        cuadro.alAceptar = () -> {
            for (int i = 0; i < radios.size(); i++) {
                if (radios.get(i).isSelected()) {
                    return opciones[i];
                }
            }
            return null;
        };
        cuadro.enfocarAlAbrir = radios.stream().filter(JRadioButton::isSelected).findFirst().orElse(null);
        return cuadro;
    }

    /** Fila de opción; se resalta al elegirla. */
    private static final class Fila extends JPanel {
        private final JRadioButton radio;

        Fila(JRadioButton radio) {
            super(new BorderLayout(10, 0));
            this.radio = radio;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 12, 8, 14));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(radio, BorderLayout.WEST);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    radio.setSelected(true);
                    radio.requestFocusInWindow();
                }
            });
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Estilo.suavizar(g2);
            boolean elegida = radio.isSelected();
            g2.setColor(elegida ? Estilo.ACENTO_SUAVE : Estilo.SUPERFICIE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.setColor(elegida ? Estilo.ACENTO : Estilo.TRAZO);
            g2.setStroke(new BasicStroke(elegida ? 2f : 1f));
            float d = elegida ? 1f : 0.5f;
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(d, d, getWidth() - 2 * d - 1, getHeight() - 2 * d - 1, 12, 12));
            g2.dispose();
        }
    }

    private static String html(String mensaje) {
        String seguro = mensaje.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><body style='width: 400px'>" + seguro.replace("\n", "<br>") + "</body></html>";
    }

    /** Ventana sin marco del sistema, con sombra. */
    static final class Cuadro extends JDialog {
        Object resultado;
        Supplier<Object> alAceptar = () -> Boolean.TRUE;
        JComponent enfocarAlAbrir;
        private final boolean translucido;
        private Point agarre;

        Cuadro(Component padre, String titulo, String mensaje, JComponent contenido,
               String textoAceptar, String textoCancelar) {
            super(padre == null ? null : SwingUtilities.getWindowAncestor(padre), titulo, Dialog.ModalityType.APPLICATION_MODAL);
            GraphicsDevice pantalla = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            translucido = pantalla.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
            setUndecorated(true);
            if (translucido) {
                setBackground(new Color(0, 0, 0, 0));
            }
            int s = translucido ? SOMBRA : 0;
            JPanel fondo = new JPanel(new BorderLayout(0, 16)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    Estilo.suavizar(g2);
                    int arco = translucido ? ARCO : 0;
                    int x = s, y = Math.max(0, s - 2), w = getWidth() - 2 * s, h = getHeight() - 2 * s;
                    for (int i = 1; i <= s; i++) { // sombra
                        g2.setColor(new Color(0x041B2A34, true));
                        g2.fillRoundRect(x - i, y - i + 4, w + 2 * i, h + 2 * i, arco + 2 * i, arco + 2 * i);
                    }
                    g2.setColor(Estilo.SUPERFICIE);
                    g2.fillRoundRect(x, y, w, h, arco, arco);
                    g2.setColor(Estilo.TRAZO);
                    g2.drawRoundRect(x, y, w - 1, h - 1, arco, arco);
                    g2.dispose();
                }
            };
            fondo.setOpaque(false);
            fondo.setBorder(new EmptyBorder(s + 24, s + 26, s + 22, s + 26));
            setContentPane(fondo);

            JLabel rotulo = new JLabel(titulo);
            rotulo.setFont(Estilo.titulo(Font.BOLD, 21));
            rotulo.setForeground(Estilo.TINTA);
            JLabel cuerpo = new JLabel(html(mensaje));
            cuerpo.setFont(Estilo.texto(Font.PLAIN, 14));
            cuerpo.setForeground(Estilo.TINTA_SUAVE);
            JPanel textos = new JPanel(new BorderLayout(0, 8));
            textos.setOpaque(false);
            textos.add(rotulo, BorderLayout.NORTH);
            textos.add(cuerpo, BorderLayout.CENTER);
            JPanel centro = new JPanel(new BorderLayout(0, 16));
            centro.setOpaque(false);
            centro.add(textos, BorderLayout.NORTH);
            if (contenido != null) {
                centro.add(contenido, BorderLayout.CENTER);
            }

            JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            botones.setOpaque(false);
            if (textoCancelar != null) {
                botones.add(Estilo.boton(textoCancelar, e -> cerrar(null), false));
            }
            JButton aceptar = Estilo.boton(textoAceptar, e -> cerrar(alAceptar.get()), true);
            botones.add(aceptar);
            fondo.add(centro, BorderLayout.CENTER);
            fondo.add(botones, BorderLayout.SOUTH);

            getRootPane().setDefaultButton(aceptar);
            getRootPane().registerKeyboardAction(e -> cerrar(null),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            // Sin barra de título: se arrastra desde cualquier parte.
            MouseAdapter mover = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    agarre = e.getPoint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    Point p = e.getLocationOnScreen();
                    setLocation(p.x - agarre.x, p.y - agarre.y);
                }
            };
            fondo.addMouseListener(mover);
            fondo.addMouseMotionListener(mover);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    (enfocarAlAbrir != null ? enfocarAlAbrir : aceptar).requestFocusInWindow();
                }
            });
            pack();
        }

        private void cerrar(Object valor) {
            resultado = valor;
            dispose();
        }

        void mostrar() {
            setLocationRelativeTo(getOwner());
            setVisible(true);
        }
    }
}
