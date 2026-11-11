import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

/** Pantalla de la TV de la sala de espera. */
public class PantallaTurnos extends JFrame {
    private static final int VISIBLES = 6;
    private static final Color FONDO = new Color(0x132E52);
    private static final Color PANEL = new Color(0x193A66);
    private static final Color LINEA = new Color(0x2B5084);
    private static final Color BLANCO = Color.WHITE;
    private static final Color SUAVE = new Color(0xA8BAD4);
    private static final Color CLARO = new Color(0xDCE5F1);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final GestorTurnos gestor;
    private final JLabel reloj = new JLabel("", JLabel.RIGHT);
    private final JPanel consultorios = new JPanel(new GridLayout(0, 1, 0, 18));
    private final JPanel espera = new JPanel(new GridLayout(0, 1, 0, 6));
    private final Timer temporizador = new Timer(2_000, e -> refrescar());

    private PantallaTurnos(GestorTurnos gestor) {
        super("Turnos");
        this.gestor = gestor;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getContentPane().setBackground(FONDO);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                temporizador.stop();
            }
        });
        consultorios.setOpaque(false);
        espera.setOpaque(false);

        JPanel cuerpo = new JPanel(new GridLayout(1, 2, 40, 0));
        cuerpo.setOpaque(false);
        cuerpo.setBorder(new EmptyBorder(0, 40, 0, 40));
        cuerpo.add(columna("Pase al consultorio", consultorios));
        cuerpo.add(columna("Próximos turnos", espera));

        add(cabecera(), BorderLayout.NORTH);
        add(cuerpo, BorderLayout.CENTER);
        add(pie(), BorderLayout.SOUTH);
        refrescar();
        temporizador.start();
    }

    /** Con dos pantallas va a pantalla completa en la segunda; si no, en ventana. Esc la cierra. */
    static void abrir(GestorTurnos gestor) {
        GraphicsDevice[] pantallas = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        PantallaTurnos ventana = new PantallaTurnos(gestor);
        if (pantallas.length > 1) {
            ventana.setUndecorated(true);
            ventana.setVisible(true);
            pantallas[1].setFullScreenWindow(ventana);
        } else {
            ventana.setSize(1280, 720);
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        }
    }

    private JPanel cabecera() {
        JLabel titulo = new JLabel("Turnos");
        titulo.setFont(Estilo.titulo(Font.BOLD, 44));
        titulo.setForeground(BLANCO);
        JLabel lugar = new JLabel("Hospital Primario Héroes y Mártires, El Cuá");
        lugar.setFont(Estilo.texto(Font.PLAIN, 20));
        lugar.setForeground(SUAVE);
        JPanel marca = new JPanel(new GridLayout(2, 1, 0, 4));
        marca.setOpaque(false);
        marca.add(titulo);
        marca.add(lugar);

        reloj.setFont(Estilo.titulo(Font.BOLD, 40));
        reloj.setForeground(BLANCO);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(28, 40, 24, 40));
        p.add(marca, BorderLayout.WEST);
        p.add(reloj, BorderLayout.EAST);
        return p;
    }

    private static JPanel columna(String titulo, JComponent contenido) {
        JLabel t = new JLabel(titulo);
        t.setFont(Estilo.texto(Font.BOLD, 24));
        t.setForeground(SUAVE);
        t.setBorder(new EmptyBorder(0, 0, 18, 0));
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PANEL);
        p.setBorder(new EmptyBorder(28, 32, 28, 32));
        p.add(t, BorderLayout.NORTH);
        p.add(contenido, BorderLayout.CENTER);
        return p;
    }

    private static JPanel pie() {
        JLabel texto = new JLabel("Cuando aparezca su turno, diríjase al consultorio indicado.", JLabel.CENTER);
        texto.setFont(Estilo.texto(Font.PLAIN, 24));
        texto.setForeground(SUAVE);
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(20, 40, 28, 40));
        p.add(texto, BorderLayout.CENTER);
        return p;
    }

    /** Cada 2 s. */
    private void refrescar() {
        reloj.setText(LocalDateTime.now().format(HORA));
        consultorios.removeAll();
        for (int c = 1; c <= gestor.getConsultorios(); c++) {
            consultorios.add(tarjeta(c, gestor.getEnAtencion(c)));
        }
        espera.removeAll();
        if (gestor.totalEnEspera() == 0) {
            JLabel vacio = new JLabel("No hay turnos en espera.");
            vacio.setFont(Estilo.texto(Font.PLAIN, 26));
            vacio.setForeground(SUAVE);
            espera.add(vacio);
        }
        int mostrados = 0;
        for (Turno t : gestor.turnosEnEspera()) {
            if (mostrados == VISIBLES) {
                break;
            }
            espera.add(fila(t, mostrados == 0));
            mostrados++;
        }
        consultorios.revalidate();
        consultorios.repaint();
        espera.revalidate();
        espera.repaint();
    }

    /** Fila de consultorio: turno y paciente, o "Libre". */
    private static JPanel tarjeta(int consultorio, Turno actual) {
        JLabel nombre = new JLabel("Consultorio " + consultorio);
        nombre.setFont(Estilo.texto(Font.PLAIN, 30));
        nombre.setForeground(BLANCO);
        JLabel paciente = new JLabel(actual == null ? "" : actual.getPaciente().getNombre());
        paciente.setFont(Estilo.texto(Font.BOLD, 26));
        paciente.setForeground(CLARO);
        JPanel izquierda = new JPanel(new GridLayout(2, 1, 0, 4));
        izquierda.setOpaque(false);
        izquierda.add(nombre);
        izquierda.add(paciente);
        JLabel codigo = new JLabel(actual == null ? "Libre" : actual.getCodigo(), JLabel.RIGHT);
        codigo.setFont(actual == null ? Estilo.texto(Font.BOLD, 40) : Estilo.titulo(Font.BOLD, 60));
        codigo.setForeground(actual == null ? SUAVE : colorClaro(actual.getPrioridad()));
        JPanel p = new JPanel(new BorderLayout(20, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, LINEA), new EmptyBorder(12, 0, 12, 0)));
        p.add(izquierda, BorderLayout.WEST);
        p.add(codigo, BorderLayout.EAST);
        return p;
    }

    /** Fila de espera; la primera más grande. */
    private static JPanel fila(Turno t, boolean primera) {
        JLabel codigo = new JLabel(t.getCodigo());
        codigo.setFont(Estilo.titulo(Font.BOLD, primera ? 54 : 40));
        codigo.setForeground(BLANCO);
        JLabel paciente = new JLabel(t.getPaciente().getNombre());
        paciente.setFont(Estilo.texto(Font.PLAIN, 28));
        paciente.setForeground(CLARO);
        JLabel prioridad = new JLabel(t.getPrioridad().getNombre(), JLabel.RIGHT);
        prioridad.setFont(Estilo.texto(Font.BOLD, 22));
        prioridad.setForeground(colorClaro(t.getPrioridad()));
        JPanel p = new JPanel(new BorderLayout(20, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, LINEA), new EmptyBorder(10, 0, 10, 0)));
        p.add(codigo, BorderLayout.WEST);
        p.add(paciente, BorderLayout.CENTER);
        p.add(prioridad, BorderLayout.EAST);
        return p;
    }

    /** Versión clara de los colores para fondo oscuro. */
    static Color colorClaro(Prioridad p) {
        return switch (p) {
            case EMERGENCIA -> new Color(0xF0676D);
            case EMBARAZADA -> new Color(0xC39BEB);
            case ADULTO_MAYOR -> new Color(0xF2B25A);
            case GENERAL -> CLARO;
        };
    }
}
