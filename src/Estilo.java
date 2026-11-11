import java.awt.AWTEvent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/** Colores, letras y componentes visuales. Solo apariencia. */
final class Estilo {
    static final Color PAPEL = new Color(0xE9ECE7);
    static final Color SUPERFICIE = Color.WHITE;
    static final Color TINTA = new Color(0x1E2A30);
    static final Color TINTA_SUAVE = new Color(0x5D686C);
    static final Color TRAZO = new Color(0xCBD2CD);
    static final Color ENCABEZADO = new Color(0xEEF1ED);
    static final Color ACENTO = new Color(0x1D4F91);
    static final Color ACENTO_OSCURO = new Color(0x163E73);
    static final Color ACENTO_SUAVE = new Color(0xE3EBF5);
    static final Color LIBRE = new Color(0xB9C1BC);
    static final Color EMERGENCIA = new Color(0xA4262C);
    static final Color EMBARAZADA = new Color(0x6B3FA0);
    static final Color ADULTO_MAYOR = new Color(0x8A5300);
    static final Color GENERAL = new Color(0x4A5866);

    private static final int ARCO = 6;
    private static final Color BORDE_CAMPO = new Color(0x8E9993);
    private static final Color ANILLO = new Color(0x8FAAD3);

    private static final String FAMILIA_TITULO = familia("Bahnschrift");
    private static final String FAMILIA_TEXTO = familia("Segoe UI");

    private Estilo() {
    }

    /** La familia si está instalada; si no, SansSerif. */
    private static String familia(String preferida) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equalsIgnoreCase(preferida)) {
                return preferida;
            }
        }
        return Font.SANS_SERIF;
    }

    static void suavizar(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Letra y colores por defecto de los controles. */
    static void aplicarTema() {
        Font letra = texto(Font.PLAIN, 13);
        for (String clave : new String[]{"Label.font", "Button.font", "CheckBox.font", "RadioButton.font",
                "TextField.font", "ComboBox.font", "TabbedPane.font", "Table.font", "TableHeader.font",
                "OptionPane.font", "OptionPane.messageFont", "OptionPane.buttonFont", "List.font"}) {
            UIManager.put(clave, letra);
        }
        UIManager.put("Panel.background", SUPERFICIE);
        UIManager.put("OptionPane.background", SUPERFICIE);
        UIManager.put("OptionPane.messageForeground", TINTA);
        UIManager.put("Button.background", SUPERFICIE);
        UIManager.put("Button.foreground", TINTA);
        UIManager.put("TextField.border", new BordeRedondo(ARCO, new Insets(5, 12, 5, 12)));
        UIManager.put("TextField.background", SUPERFICIE);
        UIManager.put("TextField.foreground", TINTA);
        UIManager.put("TextField.caretForeground", ACENTO);
        UIManager.put("TextField.selectionBackground", ACENTO_SUAVE);
        UIManager.put("TextField.selectionForeground", TINTA);
        UIManager.put("ComboBox.background", SUPERFICIE);
        // El foco se dibuja en el icono, no con el punteado de Java.
        UIManager.put("CheckBox.icon", new IconoCaja(false));
        UIManager.put("RadioButton.icon", new IconoCaja(true));
        UIManager.put("CheckBox.focus", new Color(0, 0, 0, 0));
        UIManager.put("RadioButton.focus", new Color(0, 0, 0, 0));
        // Repinta el borde al cambiar el foco.
        Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
            Object origen = e.getSource();
            if (origen instanceof javax.swing.text.JTextComponent || origen instanceof JComboBox
                    || origen instanceof AbstractButton) {
                ((Component) origen).repaint();
            }
        }, AWTEvent.FOCUS_EVENT_MASK);
    }

    static Font titulo(int estilo, int tam) {
        return new Font(FAMILIA_TITULO, estilo, tam);
    }

    static Font texto(int estilo, int tam) {
        return new Font(FAMILIA_TEXTO, estilo, tam);
    }

    static Color colorDe(Prioridad p) {
        return switch (p) {
            case EMERGENCIA -> EMERGENCIA;
            case EMBARAZADA -> EMBARAZADA;
            case ADULTO_MAYOR -> ADULTO_MAYOR;
            case GENERAL -> GENERAL;
        };
    }

    /** Principal: azul. Secundario: blanco con borde. */
    static JButton boton(String texto, ActionListener accion, boolean principal) {
        return crearBoton(texto, accion,
                principal ? ACENTO : SUPERFICIE,
                principal ? ACENTO_OSCURO : ENCABEZADO,
                principal ? new Color(0x10305A) : TRAZO,
                principal ? null : BORDE_CAMPO,
                principal ? Color.WHITE : TINTA);
    }

    private static JButton crearBoton(String texto, ActionListener accion, Color fondo, Color encima,
                                      Color presionado, Color borde, Color letra) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                suavizar(g2);
                if (!isEnabled()) {
                    g2.setComposite(AlphaComposite.SrcOver.derive(0.45f));
                }
                g2.setColor(getModel().isPressed() ? presionado : getModel().isRollover() ? encima : fondo);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARCO, ARCO);
                if (borde != null) {
                    g2.setColor(borde);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARCO, ARCO);
                }
                if (isFocusOwner()) {
                    g2.setColor(ANILLO);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, ARCO - 2, ARCO - 2);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setFocusPainted(false);
        b.setFont(texto(Font.BOLD, 14));
        b.setForeground(letra);
        b.setMargin(new Insets(10, 14, 10, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (accion != null) {
            b.addActionListener(accion);
        }
        return b;
    }

    /** Borde redondeado; más grueso y azul con el foco. */
    static class BordeRedondo extends AbstractBorder {
        private final int arco;
        private final Insets margen;

        BordeRedondo(int arco, Insets margen) {
            this.arco = arco;
            this.margen = margen;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int ancho, int alto) {
            Graphics2D g2 = (Graphics2D) g.create();
            suavizar(g2);
            boolean foco = c.hasFocus();
            g2.setColor(foco ? ACENTO : TRAZO);
            g2.setStroke(new BasicStroke(foco ? 2f : 1f));
            float d = foco ? 1f : 0.5f;
            g2.draw(new RoundRectangle2D.Float(x + d, y + d, ancho - 2 * d - 1, alto - 2 * d - 1, arco, arco));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return (Insets) margen.clone();
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(margen.top, margen.left, margen.bottom, margen.right);
            return insets;
        }
    }

    /** Desplegable con el mismo borde que los campos. */
    static void aplanar(JComboBox<?> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                JButton flecha = new BasicArrowButton(SwingConstants.SOUTH, SUPERFICIE, SUPERFICIE, TINTA_SUAVE, SUPERFICIE);
                flecha.setBorder(new EmptyBorder(0, 6, 0, 6));
                return flecha;
            }
        });
        combo.setBackground(SUPERFICIE);
        combo.setBorder(new BordeRedondo(ARCO, new Insets(2, 8, 2, 2)));
    }

    static class IconoCaja implements Icon {
        private final boolean redondo;

        IconoCaja(boolean redondo) {
            this.redondo = redondo;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            AbstractButton b = (AbstractButton) c;
            boolean activo = b.isEnabled();
            boolean marcado = b.isSelected();
            Graphics2D g2 = (Graphics2D) g.create();
            suavizar(g2);
            g2.translate(x, y);
            if (b.hasFocus()) {
                g2.setColor(ANILLO);
                g2.setStroke(new BasicStroke(2f));
                if (redondo) {
                    g2.drawOval(0, 0, 19, 19);
                } else {
                    g2.drawRoundRect(0, 0, 19, 19, 8, 8);
                }
            }
            Color relleno = !marcado ? SUPERFICIE : activo ? ACENTO : new Color(0xB8C4CD);
            Color borde = marcado ? relleno : activo ? BORDE_CAMPO : TRAZO;
            g2.setColor(relleno);
            if (redondo) {
                g2.fillOval(2, 2, 16, 16);
            } else {
                g2.fillRoundRect(2, 2, 16, 16, 5, 5);
            }
            g2.setColor(borde);
            g2.setStroke(new BasicStroke(1.6f));
            if (redondo) {
                g2.drawOval(2, 2, 16, 16);
            } else {
                g2.drawRoundRect(2, 2, 16, 16, 5, 5);
            }
            if (marcado) {
                g2.setColor(Color.WHITE);
                if (redondo) {
                    g2.fillOval(7, 7, 6, 6);
                } else {
                    Path2D marca = new Path2D.Float();
                    marca.moveTo(6, 10.5);
                    marca.lineTo(9, 13.5);
                    marca.lineTo(14.5, 7);
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(marca);
                }
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 20;
        }

        @Override
        public int getIconHeight() {
            return 20;
        }
    }

    /** Panel blanco con borde fino. */
    static class Tarjeta extends JPanel {
        Tarjeta(LayoutManager disposicion) {
            super(disposicion);
            setOpaque(false);
            setBorder(new EmptyBorder(14, 20, 16, 20));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            suavizar(g2);
            int ancho = getWidth() - 1;
            int alto = getHeight() - 1;
            g2.setColor(SUPERFICIE);
            g2.fillRoundRect(0, 0, ancho, alto, ARCO, ARCO);
            g2.setColor(TRAZO);
            g2.drawRoundRect(0, 0, ancho, alto, ARCO, ARCO);
            g2.dispose();
        }
    }

    static JPanel superficie(LayoutManager disposicion) {
        return new Tarjeta(disposicion);
    }

    static JPanel pagina(LayoutManager disposicion) {
        JPanel p = new JPanel(disposicion);
        p.setBackground(PAPEL);
        p.setBorder(new EmptyBorder(12, 24, 12, 24));
        return p;
    }

    static JPanel cifra(String titulo, JLabel valor) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);
        JLabel etiqueta = new JLabel(titulo);
        etiqueta.setFont(texto(Font.PLAIN, 13));
        etiqueta.setForeground(TINTA_SUAVE);
        p.add(valor, BorderLayout.WEST);
        p.add(etiqueta, BorderLayout.CENTER);
        return p;
    }

    static JLabel subtitulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(texto(Font.BOLD, 16));
        l.setForeground(TINTA);
        return l;
    }

    static JLabel ayuda(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(texto(Font.PLAIN, 13));
        l.setForeground(TINTA_SUAVE);
        return l;
    }

    static JPanel campo(String etiqueta, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(etiqueta);
        l.setFont(texto(Font.BOLD, 12));
        l.setForeground(TINTA_SUAVE);
        p.add(l, BorderLayout.NORTH);
        p.add(control, BorderLayout.CENTER);
        return p;
    }

    static JScrollPane desplazable(JComponent contenido) {
        JScrollPane sp = new JScrollPane(contenido);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0)); // el panel de afuera ya tiene borde
        sp.getViewport().setBackground(SUPERFICIE);
        sp.getVerticalScrollBar().setUI(new BarraDelgada());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        sp.getHorizontalScrollBar().setUI(new BarraDelgada());
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        return sp;
    }

    /** Barra de desplazamiento fina, sin flechas. */
    static class BarraDelgada extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0xB4C0C9);
            trackColor = SUPERFICIE;
        }

        @Override
        protected JButton createDecreaseButton(int orientacion) {
            return sinFlecha();
        }

        @Override
        protected JButton createIncreaseButton(int orientacion) {
            return sinFlecha();
        }

        private static JButton sinFlecha() {
            JButton b = new JButton();
            Dimension cero = new Dimension(0, 0);
            b.setPreferredSize(cero);
            b.setMinimumSize(cero);
            b.setMaximumSize(cero);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle area) {
            g.setColor(trackColor);
            g.fillRect(area.x, area.y, area.width, area.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle area) {
            Graphics2D g2 = (Graphics2D) g.create();
            suavizar(g2);
            g2.setColor(thumbColor);
            g2.fillRoundRect(area.x + 2, area.y + 2, area.width - 4, area.height - 4, 8, 8);
            g2.dispose();
        }
    }

    /** Se ajusta al ancho para no mostrar barra horizontal. */
    static class AnchoDeVista extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle vista, int orientacion, int direccion) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle vista, int orientacion, int direccion) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
    }

    /** Tabla de solo lectura; muestra textoVacio si no hay filas. */
    static JTable tabla(DefaultTableModel modelo, String textoVacio, int... anchos) {
        JTable t = new JTable(modelo) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0 && textoVacio != null) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setFont(texto(Font.PLAIN, 14));
                    g2.setColor(TINTA_SUAVE);
                    int ancho = g2.getFontMetrics().stringWidth(textoVacio);
                    g2.drawString(textoVacio, (getWidth() - ancho) / 2, 60);
                    g2.dispose();
                }
            }
        };
        t.setRowHeight(36);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setFont(texto(Font.PLAIN, 14));
        t.setForeground(TINTA);
        t.setSelectionBackground(ACENTO_SUAVE);
        t.setSelectionForeground(TINTA);
        t.setFillsViewportHeight(true);
        t.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setDefaultRenderer(new Encabezado());
        t.getTableHeader().setPreferredSize(new Dimension(t.getTableHeader().getPreferredSize().width, 38));
        t.setDefaultRenderer(Object.class, new Celda());
        for (int i = 0; i < anchos.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        return t;
    }

    static class Celda extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, ENCABEZADO), new EmptyBorder(0, 8, 0, 8)));
            setToolTipText(valor == null ? null : valor.toString()); // texto completo si se corta
            return this;
        }
    }

    static class Encabezado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            setBackground(SUPERFICIE);
            setForeground(TINTA_SUAVE);
            setFont(texto(Font.BOLD, 12));
            setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, TRAZO), new EmptyBorder(0, 8, 0, 8)));
            return this;
        }
    }

    /** Nombre del nivel con su color. */
    static class ColumnaPrioridad extends Celda {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                                                       boolean foco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            setIcon(null);
            if (valor instanceof Prioridad p) {
                setText(p.getNombre());
                setIcon(new Muestra(colorDe(p)));
                setIconTextGap(8);
            }
            return this;
        }
    }

    static final class Muestra implements Icon {
        private final Color color;

        Muestra(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillRect(x, y + 1, 10, 10);
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    /** Texto con un cuadrito de color delante. */
    static JLabel chip(String texto, Color color) {
        JLabel l = new JLabel(texto, new Muestra(color), JLabel.LEFT);
        l.setIconTextGap(8);
        l.setForeground(TINTA);
        l.setFont(texto(Font.BOLD, 13));
        return l;
    }

    /** Pestañas planas; la elegida subrayada en azul. */
    static class PestanasPlanas extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            tabAreaInsets = new Insets(0, 14, 0, 14);
            tabInsets = new Insets(10, 10, 10, 10);
            selectedTabPadInsets = new Insets(0, 0, 0, 0);
            contentBorderInsets = new Insets(1, 0, 0, 0);
        }

        @Override
        protected void paintTabArea(Graphics g, int lado, int elegida) {
            g.setColor(SUPERFICIE);
            g.fillRect(0, 0, tabPane.getWidth(), calculateTabAreaHeight(lado, runCount, maxTabHeight));
            super.paintTabArea(g, lado, elegida);
        }

        @Override
        protected void paintTabBackground(Graphics g, int lado, int indice, int x, int y, int ancho, int alto,
                                          boolean elegida) {
            if (elegida) {
                g.setColor(ACENTO);
                g.fillRect(x + 4, y + alto - 3, ancho - 8, 3);
            }
        }

        @Override
        protected void paintTabBorder(Graphics g, int lado, int indice, int x, int y, int ancho, int alto,
                                      boolean elegida) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int lado, int elegida) {
            g.setColor(TRAZO);
            g.fillRect(0, calculateTabAreaHeight(lado, runCount, maxTabHeight), tabPane.getWidth(), 1);
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int lado, Rectangle[] rects, int indice,
                                           Rectangle icono, Rectangle texto, boolean elegida) {
        }

        @Override
        protected void paintText(Graphics g, int lado, Font fuente, FontMetrics medidas, int indice, String titulo,
                                 Rectangle area, boolean elegida) {
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(fuente);
            g.setColor(elegida ? TINTA : TINTA_SUAVE);
            g.drawString(titulo, area.x, area.y + medidas.getAscent());
        }
    }

}
