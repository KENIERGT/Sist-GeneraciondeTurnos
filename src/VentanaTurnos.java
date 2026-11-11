import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Ventana de recepción. La lógica está en RegistroPacientes y GestorTurnos. */
public class VentanaTurnos extends JFrame {
    private static final int MAX_CONSULTORIOS = 5;
    /** Alto del buscador y su botón. */
    private static final int ALTO_CAMPO = 38;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.forLanguageTag("es-NI"));

    private final RegistroPacientes registro = new RegistroPacientes();
    private final GestorTurnos gestor = new GestorTurnos();
    private final BaseDatos bd;
    private LocalDate diaConfigurado;
    private boolean cambiandoDia; // evita dos avisos de día nuevo a la vez

    private final JLabel reloj = Estilo.ayuda("");
    private final JLabel estado = new JLabel(" ");
    private final JLabel alertaEmergencia = new JLabel();

    private final JPanel panelConsultorios = new JPanel(new GridLayout(0, 3, 16, 16));
    private final PanelInicio inicio = new PanelInicio();
    private final JPanel proximo = new JPanel(new BorderLayout());
    private JTabbedPane pestanas;
    private JPanel paginaPacientes;

    private final DefaultTableModel modeloCola = Estilo.modelo(
            "N°", "Turno", "Paciente", "Prioridad", "Espera", "Llegada");
    private final DefaultTableModel modeloPacientes = Estilo.modelo(
            "N°", "Cédula", "Nombre", "Sexo", "Edad", "Condición");
    private final DefaultTableModel modeloHistorial = Estilo.modelo(
            "N°", "Turno", "Paciente", "Prioridad", "Estado", "Consultorio", "Llegó", "Llamado", "Terminó",
            "Espera", "Consulta");
    private final JTable colaTabla = Estilo.tabla(modeloCola,
            "No hay pacientes en espera. Los turnos se dan en la pestaña Pacientes.", 50, 100, 300, 130, 90, 90);
    private final JTable pacientesTabla = Estilo.tabla(modeloPacientes,
            "Todavía no hay pacientes registrados. Use el formulario de la izquierda.", 34, 135, 180, 88, 50, 110);
    private final JTable historialTabla = Estilo.tabla(modeloHistorial,
            "Todavía no hay atenciones terminadas hoy.", 40, 80, 210, 110, 80, 100, 60, 70, 70, 70, 70);

    private final JTextField primerNombreField = new JTextField(12);
    private final JTextField segundoNombreField = new JTextField(12);
    private final JTextField primerApellidoField = new JTextField(12);
    private final JTextField segundoApellidoField = new JTextField(12);
    private final JTextField cedulaField = new JTextField(16);
    private final JTextField nacimientoField = new JTextField(14);
    private final JLabel edadCalculada = new JLabel("—");
    private final JTextField qrField = new JTextField(20);
    private final JRadioButton femenino = new JRadioButton("Femenino");
    private final JRadioButton masculino = new JRadioButton("Masculino");
    private final ButtonGroup grupoSexo = new ButtonGroup();
    private final JCheckBox embarazada = new JCheckBox("Embarazada");
    private final JCheckBox emergencia = new JCheckBox("Emergencia (solo este turno)");
    private final JLabel resumenCondiciones = Estilo.ayuda("");
    private final JTextField busqueda = new JTextField(18);
    /** Paciente de cada fila de la tabla. */
    private final List<Paciente> pacientesMostrados = new ArrayList<>();

    /** Carga pacientes y turnos de hoy desde la BD. */
    public VentanaTurnos(BaseDatos bd) throws SQLException {
        super("Sistema de turnos - Hospital Primario Héroes y Mártires");
        this.bd = bd;
        bd.cargarPacientes(registro);
        bd.cargarTurnosDeHoy(gestor, registro);
        gestor.alCambiar(this::guardarTurnos);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Estilo.PAPEL);

        colaTabla.getColumnModel().getColumn(3).setCellRenderer(new Estilo.ColumnaPrioridad());
        historialTabla.getColumnModel().getColumn(3).setCellRenderer(new Estilo.ColumnaPrioridad());

        busqueda.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { llenarPacientes(); }
            public void removeUpdate(DocumentEvent e) { llenarPacientes(); }
            public void changedUpdate(DocumentEvent e) { llenarPacientes(); }
        });
        busqueda.setPreferredSize(new Dimension(280, ALTO_CAMPO));
        busqueda.addActionListener(e -> generarTurno()); // Enter da el turno
        pacientesTabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    generarTurno();
                }
            }
        });

        configurarFormulario();
        // Franja roja mientras haya una emergencia en espera.
        alertaEmergencia.setOpaque(true);
        alertaEmergencia.setBackground(Estilo.EMERGENCIA);
        alertaEmergencia.setForeground(Color.WHITE);
        alertaEmergencia.setFont(Estilo.texto(Font.BOLD, 16));
        alertaEmergencia.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
        alertaEmergencia.setVisible(false);
        JPanel norte = new JPanel(new BorderLayout());
        norte.add(crearCabecera(), BorderLayout.NORTH);
        norte.add(alertaEmergencia, BorderLayout.SOUTH);
        add(norte, BorderLayout.NORTH);
        add(crearPestanas(), BorderLayout.CENTER);
        add(crearPie(), BorderLayout.SOUTH);
        setSize(1140, 760);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);

        actualizarReloj();
        // Actualiza minutos de espera y consulta.
        new Timer(30_000, e -> {
            actualizarReloj();
            refrescarTurnos();
        }).start();
        new Timer(60_000, e -> comprobarDia()).start();
        refrescar();
    }

    private JPanel crearCabecera() {
        JLabel nombre = new JLabel("Sistema de turnos");
        nombre.setFont(Estilo.titulo(Font.BOLD, 22));
        nombre.setForeground(Estilo.TINTA);
        JLabel lugar = Estilo.ayuda("Hospital Primario Héroes y Mártires, El Cuá");
        JPanel marca = new JPanel(new BorderLayout(14, 0));
        marca.setOpaque(false);
        marca.add(nombre, BorderLayout.WEST);
        marca.add(lugar, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(Estilo.boton("Pantalla de turnos", e -> PantallaTurnos.abrir(gestor), false));
        botones.add(Estilo.boton("TV y tablets (WiFi)", e -> mostrarPantallaWifi(), false));

        JPanel cabecera = new JPanel(new BorderLayout(20, 0));
        cabecera.setBackground(Estilo.SUPERFICIE);
        cabecera.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        cabecera.add(marca, BorderLayout.CENTER);
        cabecera.add(botones, BorderLayout.EAST);
        return cabecera;
    }

    private JTabbedPane crearPestanas() {
        pestanas = new JTabbedPane();
        pestanas.setUI(new Estilo.PestanasPlanas());
        pestanas.setFont(Estilo.texto(Font.BOLD, 14));
        pestanas.setOpaque(true);
        pestanas.setBackground(Estilo.PAPEL);
        pestanas.setForeground(Estilo.TINTA_SUAVE);
        paginaPacientes = paginaPacientes();
        pestanas.addTab("Inicio", inicio);
        pestanas.addTab("Consultorios", paginaConsultorios());
        pestanas.addTab("Pacientes", paginaPacientes);
        pestanas.addTab("Turnos", paginaTurnos());
        pestanas.addTab("Historial del día", paginaHistorial());
        // Al entrar a Pacientes el cursor queda en el campo del lector QR.
        pestanas.addChangeListener(e -> {
            if (pestanas.getSelectedComponent() == paginaPacientes) {
                qrField.requestFocusInWindow();
            }
        });
        return pestanas;
    }

    private JPanel paginaConsultorios() {
        JPanel pagina = Estilo.pagina(new BorderLayout(0, 16));
        JPanel encabezado = new JPanel(new GridLayout(2, 1, 0, 4));
        encabezado.setOpaque(false);
        encabezado.add(Estilo.subtitulo("Consultorios de hoy"));
        encabezado.add(Estilo.ayuda("Llame al siguiente paciente cuando el consultorio esté libre. "
                + "Al terminar, pulse «Terminé de atender». Los doctores también pueden hacerlo desde su tablet."));
        panelConsultorios.setOpaque(false);
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        contenedor.add(panelConsultorios, BorderLayout.NORTH);
        pagina.add(encabezado, BorderLayout.NORTH);
        pagina.add(contenedor, BorderLayout.CENTER);
        return pagina;
    }

    private JPanel paginaPacientes() {
        JPanel nombres = new JPanel(new GridLayout(1, 2, 10, 0));
        nombres.setOpaque(false);
        nombres.add(Estilo.campo("Primer nombre", primerNombreField));
        nombres.add(Estilo.campo("Segundo nombre (opcional)", segundoNombreField));
        JPanel apellidos = new JPanel(new GridLayout(1, 2, 10, 0));
        apellidos.setOpaque(false);
        apellidos.add(Estilo.campo("Primer apellido", primerApellidoField));
        apellidos.add(Estilo.campo("Segundo apellido (opcional)", segundoApellidoField));
        JPanel sexo = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        sexo.setOpaque(false);
        sexo.add(femenino);
        sexo.add(Box.createHorizontalStrut(16));
        sexo.add(masculino);
        edadCalculada.setFont(Estilo.texto(Font.BOLD, 14));
        edadCalculada.setForeground(Estilo.TINTA);
        edadCalculada.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JPanel nacimiento = new JPanel(new BorderLayout(16, 0));
        nacimiento.setOpaque(false);
        nacimiento.add(Estilo.campo("Nacimiento (dd/mm/aaaa)", nacimientoField), BorderLayout.WEST);
        nacimiento.add(Estilo.campo("Edad", edadCalculada), BorderLayout.CENTER);
        JPanel opciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        opciones.setOpaque(false);
        opciones.add(embarazada);
        opciones.add(Box.createHorizontalStrut(16));
        opciones.add(emergencia);

        JComponent campos = columna(nombres, apellidos,
                Estilo.campo("Cédula (opcional)", cedulaField),
                nacimiento,
                Estilo.campo("Sexo", sexo),
                Estilo.campo("Condición especial", opciones));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        botones.setOpaque(false);
        botones.add(Estilo.boton("Registrar y generar turno", e -> registrar(), true));

        // Lector QR: escribe el texto de la cédula en este campo y termina con Enter.
        qrField.addActionListener(e -> leerQR());
        JButton leer = Estilo.boton("Leer", e -> leerQR(), false);
        leer.setPreferredSize(new Dimension(leer.getPreferredSize().width, ALTO_CAMPO));
        qrField.setPreferredSize(new Dimension(200, ALTO_CAMPO));
        JPanel qr = new JPanel(new BorderLayout(10, 0));
        qr.setOpaque(false);
        qr.add(Estilo.campo("Escanear cédula: acerque el código QR al lector", qrField), BorderLayout.CENTER);
        qr.add(Estilo.campo(" ", leer), BorderLayout.EAST);
        JPanel cabezaFormulario = new JPanel(new BorderLayout(0, 10));
        cabezaFormulario.setOpaque(false);
        cabezaFormulario.add(Estilo.subtitulo("Nuevo paciente"), BorderLayout.NORTH);
        cabezaFormulario.add(qr, BorderLayout.CENTER);
        cabezaFormulario.add(Estilo.ayuda("Sin cédula, llene los datos a mano."), BorderLayout.SOUTH);

        JPanel formulario = Estilo.superficie(new BorderLayout(0, 14));
        formulario.setPreferredSize(new Dimension(440, 0));
        formulario.add(cabezaFormulario, BorderLayout.NORTH);
        formulario.add(Estilo.desplazable(campos), BorderLayout.CENTER);
        // Fijos abajo aunque se desplace el formulario.
        JPanel pie = new JPanel(new BorderLayout(0, 10));
        pie.setOpaque(false);
        pie.add(resumenCondiciones, BorderLayout.NORTH);
        pie.add(botones, BorderLayout.CENTER);
        formulario.add(pie, BorderLayout.SOUTH);

        JButton darTurno = Estilo.boton("Dar turno", e -> generarTurno(), true);
        JButton borrar = Estilo.boton("Borrar paciente", e -> borrarPaciente(), false);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        for (JButton b : new JButton[]{borrar, darTurno}) {
            b.setPreferredSize(new Dimension(b.getPreferredSize().width, ALTO_CAMPO));
            acciones.add(b);
        }
        JPanel buscador = new JPanel(new BorderLayout(10, 0));
        buscador.setOpaque(false);
        buscador.add(Estilo.campo("Buscar por cédula o nombre", busqueda), BorderLayout.CENTER);
        buscador.add(Estilo.campo(" ", acciones), BorderLayout.EAST);
        JPanel cabezaLista = new JPanel(new BorderLayout(0, 8));
        cabezaLista.setOpaque(false);
        cabezaLista.add(Estilo.subtitulo("Pacientes registrados (historial)"), BorderLayout.NORTH);
        cabezaLista.add(buscador, BorderLayout.CENTER);
        JPanel lista = Estilo.superficie(new BorderLayout(0, 8));
        lista.add(cabezaLista, BorderLayout.NORTH);
        lista.add(Estilo.desplazable(pacientesTabla), BorderLayout.CENTER);

        JPanel pagina = Estilo.pagina(new BorderLayout(20, 0));
        pagina.add(formulario, BorderLayout.WEST);
        pagina.add(lista, BorderLayout.CENTER);
        return pagina;
    }

    /** Quién sigue y la cola del día en orden de llamado. */
    private JPanel paginaTurnos() {
        proximo.setOpaque(false);
        JPanel cola = Estilo.superficie(new BorderLayout(0, 8));
        cola.add(Estilo.subtitulo("Cola de espera, en orden de llamado"), BorderLayout.NORTH);
        cola.add(Estilo.desplazable(colaTabla), BorderLayout.CENTER);
        JPanel pagina = Estilo.pagina(new BorderLayout(0, 16));
        pagina.add(proximo, BorderLayout.NORTH);
        pagina.add(cola, BorderLayout.CENTER);
        return pagina;
    }

    private void llenarProximo() {
        Turno siguiente = null;
        for (Turno t : gestor.turnosEnEspera()) { // el primero es el siguiente
            siguiente = t;
            break;
        }
        JPanel tarjeta = Estilo.superficie(new BorderLayout(24, 0));
        if (siguiente == null) {
            tarjeta.add(Estilo.subtitulo("No hay nadie en espera."), BorderLayout.CENTER);
        } else {
            JLabel codigo = new JLabel(siguiente.getCodigo());
            codigo.setFont(Estilo.titulo(Font.BOLD, 40));
            codigo.setForeground(Estilo.colorDe(siguiente.getPrioridad()));
            JLabel nombre = new JLabel(siguiente.getPaciente().getNombre());
            nombre.setFont(Estilo.texto(Font.BOLD, 18));
            nombre.setForeground(Estilo.TINTA);
            JPanel datos = new JPanel(new GridLayout(3, 1, 0, 2));
            datos.setOpaque(false);
            datos.add(Estilo.ayuda("Sigue en la cola"));
            datos.add(nombre);
            datos.add(Estilo.ayuda(siguiente.getPrioridad().getNombre() + ", llegó a las "
                    + siguiente.getHoraLlegada().format(HORA) + ", espera " + siguiente.minutosDeEspera() + " min"));
            int detras = gestor.totalEnEspera() - 1;
            tarjeta.add(codigo, BorderLayout.WEST);
            tarjeta.add(datos, BorderLayout.CENTER);
            tarjeta.add(Estilo.ayuda(detras == 0 ? "Nadie más espera" : detras + " más en espera"), BorderLayout.EAST);
        }
        proximo.removeAll();
        proximo.add(tarjeta, BorderLayout.CENTER);
        proximo.revalidate();
        proximo.repaint();
    }

    /** Apila los campos con su altura natural. */
    private static JPanel columna(JComponent... partes) {
        JPanel p = new Estilo.AnchoDeVista();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        for (JComponent c : partes) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            p.add(c);
            p.add(Box.createVerticalStrut(4));
        }
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel paginaHistorial() {
        JPanel tarjeta = Estilo.superficie(new BorderLayout(0, 12));
        tarjeta.add(Estilo.subtitulo("Atenciones terminadas y ausentes del día"), BorderLayout.NORTH);
        tarjeta.add(Estilo.desplazable(historialTabla), BorderLayout.CENTER);
        JPanel pagina = Estilo.pagina(new BorderLayout());
        pagina.add(tarjeta, BorderLayout.CENTER);
        return pagina;
    }

    private JPanel crearPie() {
        estado.setFont(Estilo.texto(Font.PLAIN, 13));
        estado.setForeground(Estilo.TINTA);
        JPanel pie = new JPanel(new BorderLayout());
        pie.setBackground(Estilo.SUPERFICIE);
        pie.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(1, 0, 0, 0, Estilo.TRAZO),
                BorderFactory.createEmptyBorder(10, 24, 10, 24)));
        reloj.setHorizontalAlignment(JLabel.RIGHT);
        pie.add(estado, BorderLayout.CENTER);
        pie.add(reloj, BorderLayout.EAST);
        return pie;
    }

    /** Arranca el servidor y muestra un QR por aparato: al escanearlo entra con la clave ya puesta. */
    private void mostrarPantallaWifi() {
        try {
            PantallaWeb.iniciar(gestor, this::refrescarTurnos);
            String ip = PantallaWeb.ipPrincipal();
            JPanel codigos = new JPanel(new GridLayout(1, 0, 28, 0));
            codigos.setOpaque(false);
            codigos.add(tarjetaQR("Tablets de los doctores",
                    "http://" + ip + ":" + PantallaWeb.PUERTO + "/doctor#pin=" + AppDoctores.CLAVE));
            String escaner = "";
            try {
                EscanerWeb.iniciar(this::recibirCedula);
                codigos.add(tarjetaQR("Teléfono de recepción",
                        "https://" + ip + ":" + EscanerWeb.PUERTO + "/recepcion#pin=" + AppDoctores.CLAVE));
                escaner = "\n\nEl teléfono de recepción avisa una sola vez que la conexión no es privada: "
                        + "toque «Configuración avanzada» y «Continuar».";
            } catch (IOException | GeneralSecurityException e) {
                escaner = "\n\nNo se pudo abrir el escáner de cédulas por teléfono (" + e.getMessage() + ").";
            }
            new Dialogos.Cuadro(this, "TV y tablets por WiFi",
                    "Escanee el código con la cámara de la tablet o del teléfono: entra directo, sin escribir nada. "
                    + "Si pide la clave, es " + AppDoctores.CLAVE + "." + escaner
                    + "\n\nTV de la sala de espera: " + PantallaWeb.direcciones("").split("\n")[0].split(" ")[0]
                    + "\n\nTodos los aparatos deben estar en la misma red WiFi que esta computadora. "
                    + "Si no conectan, desconecte cualquier VPN de esta computadora mientras usa el sistema.",
                    codigos, "Cerrar", null).mostrar();
        } catch (IOException e) {
            avisar("No se pudo iniciar la conexión por WiFi. El puerto " + PantallaWeb.PUERTO + " está ocupado por otro "
                    + "programa u otra ventana de Turnos. Ciérrelo y vuelva a intentar.");
        }
    }

    private static JPanel tarjetaQR(String titulo, String direccion) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(Estilo.subtitulo(titulo), BorderLayout.NORTH);
        p.add(new JLabel(CodigoQR.imagen(direccion, 5)), BorderLayout.CENTER);
        p.add(Estilo.ayuda(direccion.substring(0, direccion.indexOf('#'))), BorderLayout.SOUTH);
        return p;
    }

    private void actualizarReloj() {
        reloj.setText(FECHA_HORA.format(LocalDateTime.now()));
    }

    /** Al abrir y cada día nuevo: reinicia turnos y pide los consultorios. */
    void comprobarDia() {
        if (cambiandoDia || LocalDate.now().equals(diaConfigurado)) {
            return;
        }
        cambiandoDia = true;
        try {
            if (diaConfigurado != null) {
                avisarReinicio();
                gestor.reiniciarDia();
            }
            configurarDia();
            refrescar();
        } finally {
            cambiandoDia = false;
        }
    }

    /** Avisa si se borran turnos sin terminar. */
    private void avisarReinicio() {
        int enAtencion = pacientesEnAtencion();
        if (gestor.totalEnEspera() + enAtencion > 0) {
            Dialogos.advertir(this, "Día nuevo",
                    "Empieza un día nuevo y se reinician los turnos.\n\n"
                    + "Quedaron " + gestor.totalEnEspera() + " en espera y " + enAtencion + " en atención sin terminar: "
                    + "sus turnos se borran. Genere de nuevo el turno de quien siga necesitando atención.");
        }
    }

    private void configurarDia() {
        int minimo = Math.max(1, gestor.consultorioMasAlto());
        Integer[] opciones = new Integer[MAX_CONSULTORIOS - minimo + 1];
        for (int i = 0; i < opciones.length; i++) {
            opciones[i] = minimo + i;
        }
        Integer inicial = Math.max(minimo, gestor.getConsultorios());
        Integer elegido = Dialogos.elegir(this, "Consultorios del día", "¿Cuántos consultorios atienden hoy?",
                opciones, inicial, n -> n + (n == 1 ? " consultorio" : " consultorios"), null, null, "Aceptar");
        if (elegido != null) {
            gestor.configurarConsultorios(elegido);
        } else if (gestor.getConsultorios() == 0) {
            System.exit(0);
        }
        diaConfigurado = LocalDate.now();
    }

    private void configurarFormulario() {
        grupoSexo.add(femenino);
        grupoSexo.add(masculino);
        for (JComponent c : new JComponent[]{femenino, masculino, embarazada, emergencia}) {
            c.setOpaque(false);
        }
        femenino.addActionListener(e -> alCambiarSexo());
        masculino.addActionListener(e -> alCambiarSexo());
        embarazada.addActionListener(e -> actualizarResumen());
        emergencia.addActionListener(e -> actualizarResumen());
        nacimientoField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { actualizarResumen(); }
            public void removeUpdate(DocumentEvent e) { actualizarResumen(); }
            public void changedUpdate(DocumentEvent e) { actualizarResumen(); }
        });
        alCambiarSexo();
    }

    private void alCambiarSexo() {
        embarazada.setEnabled(femenino.isSelected());
        if (!femenino.isSelected()) {
            embarazada.setSelected(false);
        }
        actualizarResumen();
    }

    /** Resumen de condiciones bajo el formulario. */
    private void actualizarResumen() {
        List<String> nombres = condicionesDelFormulario();
        Integer edad = edadDelFormulario();
        edadCalculada.setText(edad == null ? "—" : edad + (edad == 1 ? " año" : " años"));
        if (edad != null && edad >= Paciente.EDAD_ADULTO_MAYOR) {
            nombres.add(Paciente.ADULTO_MAYOR);
        }
        resumenCondiciones.setText("Condición especial: "
                + (nombres.isEmpty() ? Paciente.SIN_CONDICION : String.join(", ", nombres))
                + (emergencia.isSelected() ? " | Turno de emergencia" : ""));
    }

    /** La emergencia no se guarda en el paciente: es solo de este turno. */
    private List<String> condicionesDelFormulario() {
        List<String> condiciones = new ArrayList<>();
        if (embarazada.isSelected()) {
            condiciones.add(Paciente.EMBARAZADA);
        }
        return condiciones;
    }

    private void limpiarFormulario() {
        primerNombreField.setText("");
        segundoNombreField.setText("");
        primerApellidoField.setText("");
        segundoApellidoField.setText("");
        cedulaField.setText("");
        nacimientoField.setText("");
        grupoSexo.clearSelection();
        embarazada.setSelected(false);
        emergencia.setSelected(false);
        alCambiarSexo();
    }

    private void registrar() {
        String primerNombre = texto(primerNombreField);
        String primerApellido = texto(primerApellidoField);
        if (primerNombre.isEmpty() || primerApellido.isEmpty()) {
            avisar("Primer nombre y primer apellido son obligatorios.");
            return;
        }
        LocalDate nacimiento = leerNacimiento();
        if (nacimiento == null) {
            return;
        }
        if (!femenino.isSelected() && !masculino.isSelected()) {
            avisar("Seleccione el sexo del paciente.");
            return;
        }
        String cedula = texto(cedulaField).toUpperCase();
        if (cedula.isEmpty()) {
            cedula = registro.nuevoIdSinCedula(); // al final, para no gastar números
        } else if (!Paciente.cedulaValida(cedula)) {
            avisar("La cédula debe tener 13 números y una letra, por ejemplo 4410512001008E.");
            return;
        } else if (registro.buscarPorCedula(cedula) != null) {
            avisar("Ya existe un paciente registrado con esa cédula.");
            return;
        }
        String sexo = femenino.isSelected() ? Paciente.FEMENINO : Paciente.MASCULINO;
        Paciente paciente = new Paciente(primerNombre, texto(segundoNombreField), primerApellido,
                texto(segundoApellidoField), cedula, sexo, nacimiento, Paciente.edadA(nacimiento, LocalDate.now()),
                condicionesDelFormulario());
        if (!guardarPaciente(paciente)) {
            return;
        }
        Prioridad prioridad = emergencia.isSelected() ? Prioridad.EMERGENCIA : gestor.sugerirPrioridad(paciente);
        limpiarFormulario();
        Turno turno = gestor.generarTurno(paciente, prioridad);
        avisar("Paciente registrado: " + paciente + " | Turno " + turno.getCodigo()
                + " (" + prioridad.getNombre() + ")");
        refrescar();
        qrField.requestFocusInWindow();
    }

    /** Llena el formulario con el QR de la cédula; si ya está registrado, lo deja listo para darle turno. */
    private void leerQR() {
        String texto = qrField.getText();
        qrField.setText("");
        if (recibirCedula(texto) == null) {
            avisar("No se reconoció el código. Escanee de nuevo el QR de la cédula o llene los datos a mano.");
        }
    }

    /** Texto del QR, del lector USB o del teléfono. Devuelve el mensaje mostrado, o null si no es una cédula. */
    private String recibirCedula(String texto) {
        LectorCedula.Datos d = LectorCedula.leer(texto);
        if (d == null) {
            return null;
        }
        pestanas.setSelectedComponent(paginaPacientes);
        Paciente registrado = registro.buscarPorCedula(d.cedula());
        if (registrado != null) {
            busqueda.setText(d.cedula());
            return avisar(registrado.getNombre() + " ya está registrado. Pulse «Dar turno» para darle un turno.");
        }
        limpiarFormulario();
        primerNombreField.setText(d.primerNombre());
        segundoNombreField.setText(d.segundoNombre());
        primerApellidoField.setText(d.primerApellido());
        segundoApellidoField.setText(d.segundoApellido());
        cedulaField.setText(d.cedula());
        nacimientoField.setText(d.nacimiento().format(FECHA));
        (d.sexo().equals(Paciente.FEMENINO) ? femenino : masculino).setSelected(true);
        alCambiarSexo();
        return avisar("Datos de " + d.primerNombre() + " " + d.primerApellido()
                + " en el formulario. Revise la condición especial y pulse «Registrar y generar turno».");
    }

    /** Primero en la BD; si falla, no se agrega. */
    private boolean guardarPaciente(Paciente paciente) {
        try {
            bd.guardarPaciente(paciente);
        } catch (SQLException e) {
            avisar("No se pudo guardar al paciente en la base de datos: " + e.getMessage());
            return false;
        }
        registro.registrar(paciente);
        return true;
    }

    /** Tras cada cambio de turnos, también desde la tablet. */
    private void guardarTurnos() {
        try {
            bd.guardarTurnos(gestor);
        } catch (SQLException e) {
            Dialogos.advertir(this, "No se guardó el cambio",
                    "El turno cambió en pantalla, pero no se pudo guardar en la base de datos ("
                    + e.getMessage() + ").\n\nSi cierra el programa ahora, ese cambio se pierde.");
        }
    }

    private LocalDate nacimientoOpcional() {
        try {
            return LocalDate.parse(texto(nacimientoField), FECHA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private Integer edadDelFormulario() {
        LocalDate n = nacimientoOpcional();
        return n == null || n.isAfter(LocalDate.now()) ? null : Paciente.edadA(n, LocalDate.now());
    }

    private LocalDate leerNacimiento() {
        LocalDate n = nacimientoOpcional();
        Integer edad = edadDelFormulario();
        if (n != null && edad != null && edad <= 120) {
            return n;
        }
        avisar("Escriba la fecha de nacimiento como dd/mm/aaaa, por ejemplo 07/04/2004. No puede ser futura.");
        return null;
    }

    /** Para registros hechos por error: solo si el paciente no tiene atenciones. */
    private void borrarPaciente() {
        int fila = pacientesTabla.getSelectedRow();
        if (fila < 0) {
            avisar("Elija en la tabla al paciente que desea borrar.");
            return;
        }
        Paciente paciente = pacientesMostrados.get(fila);
        Turno turno = gestor.enEspera(paciente);
        boolean confirmado = Dialogos.confirmar(this, "Borrar paciente",
                paciente.getNombre() + " (" + paciente.getCedula() + ")\n\n"
                        + "Se borrará del registro" + (turno == null ? "" : " y su turno " + turno.getCodigo()
                        + " saldrá de la cola") + ". Úselo solo si el paciente se registró por error.",
                "Borrar", "Cancelar");
        if (!confirmado) {
            return;
        }
        try {
            if (!bd.borrarPaciente(paciente.getCedula())) {
                avisar("No se puede borrar a " + paciente.getNombre()
                        + ": ya tiene atenciones registradas o está en consulta.");
                return;
            }
        } catch (SQLException e) {
            avisar("No se pudo borrar al paciente de la base de datos: " + e.getMessage());
            return;
        }
        gestor.quitarDeEspera(paciente);
        registro.eliminar(paciente);
        avisar(paciente.getNombre() + " fue borrado" + (turno == null ? "." : " y su turno " + turno.getCodigo()
                + " salió de la cola."));
        refrescar();
    }

    /** Turno para el paciente elegido en la tabla. */
    private void generarTurno() {
        int fila = pacientesTabla.getSelectedRow();
        if (fila < 0) {
            avisar(pacientesMostrados.isEmpty() && !texto(busqueda).isEmpty()
                    ? "Ningún paciente coincide con la búsqueda. Regístrelo con el formulario."
                    : "Elija en la tabla al paciente que recibirá el turno.");
            return;
        }
        Paciente paciente = pacientesMostrados.get(fila);
        if (gestor.tieneTurnoActivo(paciente)) {
            avisar("El paciente ya tiene un turno activo (en espera o en atención).");
            return;
        }
        Prioridad sugerida = gestor.sugerirPrioridad(paciente);
        Object elegido = Dialogos.elegir(this, "Generar turno",
                "Nivel de prioridad para " + paciente.getNombre() + " (lo decide el personal de salud):",
                Prioridad.values(), sugerida, Prioridad::getNombre, Estilo::colorDe,
                p -> p == sugerida ? "Sugerido" : null, "Generar turno");
        if (elegido == null) {
            return;
        }
        Prioridad prioridad = (Prioridad) elegido;
        int porDelante = gestor.turnosPorDelante(prioridad);
        Turno turno = gestor.generarTurno(paciente, prioridad);
        avisar("Turno " + turno.getCodigo() + " (" + prioridad.getNombre() + ") | Llegada "
                + turno.getHoraLlegada().format(HORA) + " | Pacientes por delante: " + porDelante);
        busqueda.setText("");
        refrescar();
    }

    /** Libre: llama al siguiente. Atendiendo: termina la atención. */
    private void botonConsultorio(int consultorio) {
        if (gestor.getEnAtencion(consultorio) != null) {
            Turno terminado = gestor.finalizarAtencion(consultorio);
            avisar("Atención terminada a las " + terminado.getHoraFin().format(HORA) + " ("
                    + terminado.getCodigo() + " - " + terminado.getPaciente().getNombre()
                    + "). El consultorio " + consultorio + " queda libre.");
        } else {
            Turno siguiente = gestor.llamarSiguiente(consultorio);
            avisar(siguiente == null
                    ? "No hay pacientes en espera."
                    : "Consultorio " + consultorio + ": pase " + siguiente.getCodigo()
                            + " - " + siguiente.getPaciente().getNombre() + ".");
        }
        refrescar();
    }

    private void botonAusente(int consultorio) {
        Turno turno = gestor.getEnAtencion(consultorio);
        if (turno == null) {
            return;
        }
        boolean ultima = GestorTurnos.esUltimaOportunidad(turno);
        boolean confirmado = Dialogos.confirmar(this, "El paciente no se presentó",
                turno.getCodigo() + " - " + turno.getPaciente().getNombre() + "\n\n"
                        + (ultima ? "Ya faltó una vez: quedará como ausente."
                                : "Volverá al final de su nivel de prioridad (tiene una oportunidad más)."),
                "Confirmar", "Cancelar");
        if (!confirmado || gestor.getEnAtencion(consultorio) != turno) {
            return; // cancelado, o la tablet ya lo cerró
        }
        gestor.marcarAusente(consultorio);
        avisar(turno.getCodigo() + (ultima ? " quedó como ausente." : " vuelve al final de la cola.")
                + " El consultorio " + consultorio + " queda libre.");
        refrescar();
    }

    private void refrescar() {
        refrescarTurnos();
        llenarPacientes();
    }

    /** Todo menos la lista de pacientes, para no cambiar la selección de recepción. */
    void refrescarTurnos() {
        panelConsultorios.removeAll();
        for (int c = 1; c <= gestor.getConsultorios(); c++) {
            panelConsultorios.add(tarjetaConsultorio(c, gestor.getEnAtencion(c)));
        }
        panelConsultorios.revalidate();
        panelConsultorios.repaint();

        actualizarAlertaEmergencia();
        llenarProximo();
        llenarCola();
        llenarHistorial();
        int nuevos;
        try {
            nuevos = bd.registradosHoy();
        } catch (SQLException e) {
            nuevos = -1;
        }
        inicio.actualizar(gestor, nuevos);
    }

    /** Franja roja con las emergencias en espera. */
    private void actualizarAlertaEmergencia() {
        StringBuilder lista = new StringBuilder();
        for (Turno t : gestor.turnosEnEspera()) {
            if (t.getPrioridad() == Prioridad.EMERGENCIA) {
                lista.append(lista.length() > 0 ? ", " : "").append(t.getCodigo()).append(' ')
                        .append(t.getPaciente().getNombre());
            }
        }
        alertaEmergencia.setText("Emergencia en espera: " + lista);
        alertaEmergencia.setVisible(lista.length() > 0);
    }

    private int pacientesEnAtencion() {
        int total = 0;
        for (int c = 1; c <= gestor.getConsultorios(); c++) {
            if (gestor.getEnAtencion(c) != null) {
                total++;
            }
        }
        return total;
    }

    private JPanel tarjetaConsultorio(int consultorio, Turno actual) {
        Color color = actual == null ? Estilo.LIBRE : Estilo.colorDe(actual.getPrioridad());
        JPanel tarjeta = Estilo.superficie(new BorderLayout(0, 10));

        JLabel titulo = new JLabel("Consultorio " + consultorio);
        titulo.setFont(Estilo.titulo(Font.BOLD, 20));
        titulo.setForeground(Estilo.TINTA);
        JPanel cabeza = new JPanel(new BorderLayout());
        cabeza.setOpaque(false);
        cabeza.add(titulo, BorderLayout.WEST);
        cabeza.add(Estilo.ayuda(actual == null ? "Libre" : "Atendiendo"), BorderLayout.EAST);

        JPanel detalle = new JPanel();
        detalle.setLayout(new BoxLayout(detalle, BoxLayout.Y_AXIS));
        detalle.setOpaque(false);
        if (actual == null) {
            Turno siguiente = null;
            for (Turno t : gestor.turnosEnEspera()) { // el primero es el siguiente
                siguiente = t;
                break;
            }
            if (siguiente == null) {
                detalle.add(alineado(Estilo.ayuda("Sin paciente en atención y nadie en espera.")));
            } else {
                Color colorSiguiente = Estilo.colorDe(siguiente.getPrioridad());
                JLabel codigo = new JLabel(siguiente.getCodigo());
                codigo.setFont(Estilo.titulo(Font.BOLD, 28));
                codigo.setForeground(colorSiguiente);
                JLabel paciente = new JLabel(siguiente.getPaciente().getNombre());
                paciente.setFont(Estilo.texto(Font.BOLD, 14));
                paciente.setForeground(Estilo.TINTA);
                detalle.add(alineado(Estilo.ayuda("Sigue en la cola:")));
                detalle.add(alineado(codigo));
                detalle.add(alineado(paciente));
                detalle.add(Box.createVerticalStrut(4));
                detalle.add(alineado(Estilo.ayuda(siguiente.getPrioridad().getNombre() + ", espera "
                        + siguiente.minutosDeEspera() + " min")));
            }
        } else {
            JLabel codigo = new JLabel(actual.getCodigo());
            codigo.setFont(Estilo.titulo(Font.BOLD, 44));
            codigo.setForeground(color);
            JLabel paciente = new JLabel(actual.getPaciente().getNombre());
            paciente.setFont(Estilo.texto(Font.BOLD, 15));
            paciente.setForeground(Estilo.TINTA);
            JPanel nivel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            nivel.setOpaque(false);
            nivel.add(Estilo.chip(actual.getPrioridad().getNombre(), color));
            detalle.add(alineado(codigo));
            detalle.add(alineado(paciente));
            detalle.add(Box.createVerticalStrut(8));
            detalle.add(alineado(nivel));
            detalle.add(Box.createVerticalStrut(6));
            detalle.add(alineado(Estilo.ayuda("En consulta desde las " + actual.getHoraLlamado().format(HORA)
                    + " (" + actual.segundosEnConsulta() / 60 + " min)")));
            detalle.add(alineado(Estilo.ayuda("Esperó " + actual.minutosDeEspera() + " min antes de pasar")));
        }
        tarjeta.add(cabeza, BorderLayout.NORTH);
        tarjeta.add(detalle, BorderLayout.CENTER);
        JPanel acciones = new JPanel(new GridLayout(0, 1, 0, 6));
        acciones.setOpaque(false);
        if (actual == null) {
            acciones.add(Estilo.boton("Llamar siguiente", e -> botonConsultorio(consultorio), true));
        } else {
            acciones.add(Estilo.boton("Terminé de atender", e -> botonConsultorio(consultorio), false));
            acciones.add(Estilo.boton("No se presentó", e -> botonAusente(consultorio), false));
        }
        tarjeta.add(acciones, BorderLayout.SOUTH);
        return tarjeta;
    }

    private static JComponent alineado(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private void llenarCola() {
        modeloCola.setRowCount(0);
        int numero = 1;
        for (Turno t : gestor.turnosEnEspera()) {
            modeloCola.addRow(new Object[]{numero++, t.getCodigo(), t.getPaciente().getNombre(),
                    t.getPrioridad(), t.minutosDeEspera() + " min", t.getHoraLlegada().format(HORA)});
        }
    }

    /** Al buscar se selecciona el primero; sin búsqueda, ninguno. */
    private void llenarPacientes() {
        String buscado = texto(busqueda);
        modeloPacientes.setRowCount(0);
        pacientesMostrados.clear();
        int numero = 1;
        for (Paciente p : buscado.isEmpty() ? registro.getPacientes() : registro.buscar(buscado)) {
            pacientesMostrados.add(p);
            modeloPacientes.addRow(new Object[]{numero++, p.getCedula(), p.getNombre(), p.getSexo(),
                    p.getEdad(), p.getCondicionesTexto()});
        }
        if (!buscado.isEmpty() && !pacientesMostrados.isEmpty()) {
            pacientesTabla.setRowSelectionInterval(0, 0);
        }
    }

    private void llenarHistorial() {
        modeloHistorial.setRowCount(0);
        int numero = 1;
        for (Turno t : gestor.turnosAtendidos()) {
            agregarFilaHistorial(numero++, t, "Atendido");
        }
        for (Turno t : gestor.turnosAusentes()) {
            agregarFilaHistorial(numero++, t, "Ausente");
        }
    }

    private void agregarFilaHistorial(int numero, Turno t, String estadoTurno) {
        modeloHistorial.addRow(new Object[]{numero, t.getCodigo(), t.getPaciente().getNombre(),
                t.getPrioridad(), estadoTurno, "Consultorio " + t.getConsultorio(), t.getHoraLlegada().format(HORA),
                t.getHoraLlamado().format(HORA), t.getHoraFin().format(HORA), t.minutosDeEspera() + " min",
                t.segundosEnConsulta() / 60 + " min"});
    }

    private String avisar(String mensaje) {
        estado.setText(mensaje);
        return mensaje;
    }

    private static String texto(JTextField campo) {
        return campo.getText().trim();
    }
}
