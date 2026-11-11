import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.MatteBorder;

/** Pestaña Inicio: lo que va del día en números, por prioridad y por hora de llegada. */
final class PanelInicio extends JPanel {
    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-NI"));

    private final JLabel fecha = Estilo.ayuda("");
    private final JPanel cifras = new JPanel(new GridLayout(0, 1));
    private final Barras porPrioridad = new Barras();
    private final Horas porHora = new Horas();

    PanelInicio() {
        super(new BorderLayout(0, 16));
        setBackground(Estilo.PAPEL);
        setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));

        JPanel cabeza = new JPanel(new GridLayout(2, 1, 0, 4));
        cabeza.setOpaque(false);
        cabeza.add(Estilo.subtitulo("Resumen de hoy"));
        cabeza.add(fecha);

        cifras.setOpaque(false);
        JPanel numeros = Estilo.superficie(new BorderLayout(0, 8));
        numeros.setPreferredSize(new Dimension(340, 0));
        numeros.add(Estilo.subtitulo("El día en números"), BorderLayout.NORTH);
        numeros.add(cifras, BorderLayout.CENTER);

        JPanel prioridad = Estilo.superficie(new BorderLayout(0, 8));
        prioridad.add(Estilo.subtitulo("Turnos por prioridad"), BorderLayout.NORTH);
        prioridad.add(porPrioridad, BorderLayout.CENTER);
        JPanel horas = Estilo.superficie(new BorderLayout(0, 8));
        horas.add(Estilo.subtitulo("Llegadas por hora"), BorderLayout.NORTH);
        horas.add(porHora, BorderLayout.CENTER);
        JPanel graficos = new JPanel(new GridLayout(2, 1, 0, 16));
        graficos.setOpaque(false);
        graficos.add(prioridad);
        graficos.add(horas);

        JPanel cuerpo = new JPanel(new BorderLayout(20, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(numeros, BorderLayout.WEST);
        cuerpo.add(graficos, BorderLayout.CENTER);
        add(cabeza, BorderLayout.NORTH);
        add(cuerpo, BorderLayout.CENTER);
    }

    /** registradosHoy negativo si la BD no respondió. */
    void actualizar(GestorTurnos gestor, int registradosHoy) {
        fecha.setText(DIA.format(LocalDate.now()));
        List<Turno> todos = new ArrayList<>();
        List<Turno> llamados = new ArrayList<>(); // ya pasaron a consulta: su espera es definitiva
        int enConsulta = 0;
        for (Turno t : gestor.turnosEnEspera()) {
            todos.add(t);
        }
        for (int c = 1; c <= gestor.getConsultorios(); c++) {
            if (gestor.getEnAtencion(c) != null) {
                todos.add(gestor.getEnAtencion(c));
                llamados.add(gestor.getEnAtencion(c));
                enConsulta++;
            }
        }
        for (Turno t : gestor.turnosAtendidos()) {
            todos.add(t);
            llamados.add(t);
        }
        for (Turno t : gestor.turnosAusentes()) {
            todos.add(t);
        }

        cifras.removeAll();
        fila("Turnos entregados", String.valueOf(todos.size()));
        fila("En espera", String.valueOf(gestor.totalEnEspera()));
        fila("En consulta", String.valueOf(enConsulta));
        fila("Atendidos", String.valueOf(gestor.totalAtendidos()));
        fila("No se presentaron", String.valueOf(gestor.turnosAusentes().tamano()));
        fila("Pacientes nuevos", registradosHoy < 0 ? "—" : String.valueOf(registradosHoy));
        fila("Espera promedio", llamados.isEmpty() ? "—" : promedio(llamados) + " min");
        fila("Consultorios abiertos", String.valueOf(gestor.getConsultorios()));
        cifras.revalidate();
        cifras.repaint();

        porPrioridad.datos(todos, llamados);
        porHora.datos(todos);
    }

    private void fila(String nombre, String valor) {
        JPanel f = new JPanel(new BorderLayout());
        f.setOpaque(false);
        f.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, Estilo.ENCABEZADO),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        JLabel n = new JLabel(nombre);
        n.setFont(Estilo.texto(Font.PLAIN, 14));
        n.setForeground(Estilo.TINTA_SUAVE);
        JLabel v = new JLabel(valor);
        v.setFont(Estilo.titulo(Font.BOLD, 20));
        v.setForeground(Estilo.TINTA);
        f.add(n, BorderLayout.WEST);
        f.add(v, BorderLayout.EAST);
        cifras.add(f);
    }

    static long promedio(List<Turno> turnos) {
        long total = 0;
        for (Turno t : turnos) {
            total += t.minutosDeEspera();
        }
        return Math.round((double) total / turnos.size());
    }

    private static void suave(Graphics2D g2) {
        Estilo.suavizar(g2);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    private static void vacio(Graphics2D g2) {
        g2.setFont(Estilo.texto(Font.PLAIN, 14));
        g2.setColor(Estilo.TINTA_SUAVE);
        g2.drawString("Todavía no hay turnos hoy.", 0, 24);
    }

    /** Una barra horizontal por nivel, con su color, la cantidad y la espera promedio. */
    private static final class Barras extends JComponent {
        private final int[] cuenta = new int[Prioridad.values().length];
        private final String[] espera = new String[Prioridad.values().length];
        private int total;

        Barras() {
            setPreferredSize(new Dimension(300, 4 * 34));
        }

        void datos(List<Turno> todos, List<Turno> llamados) {
            total = todos.size();
            for (Prioridad p : Prioridad.values()) {
                List<Turno> delNivel = new ArrayList<>();
                for (Turno t : llamados) {
                    if (t.getPrioridad() == p) {
                        delNivel.add(t);
                    }
                }
                int n = 0;
                for (Turno t : todos) {
                    n += t.getPrioridad() == p ? 1 : 0;
                }
                cuenta[p.ordinal()] = n;
                espera[p.ordinal()] = delNivel.isEmpty() ? "" : "espera " + promedio(delNivel) + " min";
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            suave(g2);
            if (total == 0) {
                vacio(g2);
                g2.dispose();
                return;
            }
            int max = 1;
            for (int n : cuenta) {
                max = Math.max(max, n);
            }
            int alto = Math.min(34, getHeight() / 4);
            int xBarra = 130, ancho = getWidth() - xBarra - 130;
            g2.setFont(Estilo.texto(Font.PLAIN, 14));
            FontMetrics fm = g2.getFontMetrics();
            for (Prioridad p : Prioridad.values()) {
                int y = p.ordinal() * alto;
                int base = y + (alto + fm.getAscent()) / 2 - 2;
                g2.setColor(Estilo.TINTA);
                g2.drawString(p.getNombre(), 0, base);
                int largo = (int) Math.round((double) ancho * cuenta[p.ordinal()] / max);
                g2.setColor(Estilo.ENCABEZADO);
                g2.fillRect(xBarra, y + 7, ancho, alto - 14);
                g2.setColor(Estilo.colorDe(p));
                g2.fillRect(xBarra, y + 7, largo, alto - 14);
                g2.setColor(Estilo.TINTA);
                g2.setFont(Estilo.titulo(Font.BOLD, 16));
                g2.drawString(String.valueOf(cuenta[p.ordinal()]), xBarra + ancho + 10, base);
                g2.setFont(Estilo.texto(Font.PLAIN, 13));
                g2.setColor(Estilo.TINTA_SUAVE);
                g2.drawString(espera[p.ordinal()], xBarra + ancho + 40, base);
                g2.setFont(Estilo.texto(Font.PLAIN, 14));
            }
            g2.dispose();
        }
    }

    /** Barras verticales con los turnos que llegaron en cada hora del día. */
    private static final class Horas extends JComponent {
        private final int[] porHora = new int[24];
        private int desde = 7, hasta = 16, total;

        Horas() {
            setPreferredSize(new Dimension(300, 140));
        }

        void datos(List<Turno> todos) {
            java.util.Arrays.fill(porHora, 0);
            desde = 7;
            hasta = 16;
            for (Turno t : todos) {
                int h = t.getHoraLlegada().getHour();
                porHora[h]++;
                desde = Math.min(desde, h);
                hasta = Math.max(hasta, h);
            }
            total = todos.size();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            suave(g2);
            if (total == 0) {
                vacio(g2);
                g2.dispose();
                return;
            }
            int max = 1;
            for (int h = desde; h <= hasta; h++) {
                max = Math.max(max, porHora[h]);
            }
            int columnas = hasta - desde + 1;
            int abajo = getHeight() - 22, arriba = 18;
            double paso = (double) getWidth() / columnas;
            int barra = (int) Math.max(6, paso * 0.6);
            g2.setColor(Estilo.TRAZO);
            g2.drawLine(0, abajo, getWidth(), abajo);
            for (int h = desde; h <= hasta; h++) {
                int x = (int) ((h - desde) * paso + (paso - barra) / 2);
                int alto = (int) Math.round((double) (abajo - arriba) * porHora[h] / max);
                g2.setColor(Estilo.ACENTO);
                g2.fillRect(x, abajo - alto, barra, alto);
                g2.setFont(Estilo.texto(Font.PLAIN, 12));
                FontMetrics fm = g2.getFontMetrics();
                String hora = h + ":00";
                g2.setColor(Estilo.TINTA_SUAVE);
                g2.drawString(hora, x + (barra - fm.stringWidth(hora)) / 2, abajo + 16);
                if (porHora[h] > 0) {
                    String n = String.valueOf(porHora[h]);
                    g2.setColor(Estilo.TINTA);
                    g2.setFont(Estilo.titulo(Font.BOLD, 13));
                    g2.drawString(n, x + (barra - g2.getFontMetrics().stringWidth(n)) / 2, abajo - alto - 4);
                }
            }
            g2.dispose();
        }
    }
}
