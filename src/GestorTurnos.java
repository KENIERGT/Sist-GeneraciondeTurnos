import java.util.Arrays;

/** Genera, llama y cierra turnos. Un paciente por consultorio a la vez. */
public class GestorTurnos {
    /** Llamados antes de darlo por ausente. */
    public static final int OPORTUNIDADES = 2;

    /** Estados guardados en la BD. */
    static final String ESPERA = "espera", ATENCION = "atencion", ATENDIDO = "atendido", AUSENTE = "ausente";

    /** Se ejecuta tras cada cambio (para guardar en la BD). */
    private Runnable alCambiar = () -> { };

    private ColaPrioridad espera = new ColaPrioridad();
    private final int[] consecutivo = new int[Prioridad.values().length];
    private ListaEnlazada<Turno> atendidos = new ListaEnlazada<>();
    private ListaEnlazada<Turno> ausentes = new ListaEnlazada<>();

    private Turno[] enAtencion = new Turno[0];

    /** Nivel sugerido por embarazo o edad. */
    public Prioridad sugerirPrioridad(Paciente paciente) {
        if (paciente.esEmbarazada()) {
            return Prioridad.EMBARAZADA;
        }
        if (paciente.esAdultoMayor()) {
            return Prioridad.ADULTO_MAYOR;
        }
        return Prioridad.GENERAL;
    }

    /** En espera o en atención. */
    public boolean tieneTurnoActivo(Paciente paciente) {
        for (Turno turno : enAtencion) {
            if (turno != null && turno.getPaciente().getCedula().equalsIgnoreCase(paciente.getCedula())) {
                return true;
            }
        }
        return espera.contienePaciente(paciente.getCedula());
    }

    /** No puede quitar un consultorio que está atendiendo. */
    public void configurarConsultorios(int cantidad) {
        if (cantidad < consultorioMasAlto()) {
            throw new IllegalStateException("Hay un consultorio atendiendo por encima de esa cantidad.");
        }
        enAtencion = Arrays.copyOf(enAtencion, cantidad);
    }

    public int getConsultorios() {
        return enAtencion.length;
    }

    public int consultorioMasAlto() {
        for (int i = enAtencion.length; i > 0; i--) {
            if (enAtencion[i - 1] != null) {
                return i;
            }
        }
        return 0;
    }

    public int turnosPorDelante(Prioridad prioridad) {
        return espera.turnosPorDelante(prioridad);
    }

    public Turno generarTurno(Paciente paciente, Prioridad prioridad) {
        if (tieneTurnoActivo(paciente)) {
            throw new IllegalStateException("El paciente ya tiene un turno activo.");
        }
        int numero = ++consecutivo[prioridad.ordinal()];
        String codigo = String.format("%c-%03d", prioridad.getPrefijo(), numero);
        Turno turno = new Turno(codigo, paciente, prioridad);
        espera.encolar(turno);
        alCambiar.run();
        return turno;
    }

    /** Consultorio desde 1. null si no hay nadie en espera. */
    public Turno llamarSiguiente(int consultorio) {
        if (enAtencion[consultorio - 1] != null) {
            throw new IllegalStateException("El consultorio " + consultorio + " ya está atendiendo.");
        }
        Turno siguiente = espera.desencolar();
        if (siguiente != null) {
            siguiente.marcarEnAtencion(consultorio);
            enAtencion[consultorio - 1] = siguiente;
            alCambiar.run();
        }
        return siguiente;
    }

    /** null si el consultorio estaba libre. */
    public Turno finalizarAtencion(int consultorio) {
        Turno terminado = enAtencion[consultorio - 1];
        if (terminado == null) {
            return null;
        }
        terminado.marcarAtendido();
        atendidos.agregar(terminado);
        enAtencion[consultorio - 1] = null;
        alCambiar.run();
        return terminado;
    }

    /** Primera falta: vuelve al final de su nivel. Segunda: ausente. null si estaba libre. */
    public Turno marcarAusente(int consultorio) {
        Turno turno = enAtencion[consultorio - 1];
        if (turno == null) {
            return null;
        }
        enAtencion[consultorio - 1] = null;
        turno.marcarAusencia();
        if (turno.getAusencias() < OPORTUNIDADES) {
            turno.volverAEspera();
            espera.encolar(turno);
        } else {
            ausentes.agregar(turno);
        }
        alCambiar.run();
        return turno;
    }

    void alCambiar(Runnable accion) {
        alCambiar = accion;
    }

    /** Carga un turno de la BD. Los de espera deben venir en orden de llamado. */
    void restaurar(Turno turno, String estado) {
        int nivel = turno.getPrioridad().ordinal();
        consecutivo[nivel] = Math.max(consecutivo[nivel], Integer.parseInt(turno.getCodigo().substring(2)));
        switch (estado) {
            case ESPERA -> espera.encolar(turno);
            case ATENCION -> {
                if (turno.getConsultorio() > enAtencion.length) {
                    enAtencion = Arrays.copyOf(enAtencion, turno.getConsultorio());
                }
                enAtencion[turno.getConsultorio() - 1] = turno;
            }
            case ATENDIDO -> atendidos.agregar(turno);
            default -> ausentes.agregar(turno);
        }
    }

    /** True si una falta más lo deja ausente. */
    public static boolean esUltimaOportunidad(Turno turno) {
        return turno.getAusencias() == OPORTUNIDADES - 1;
    }

    /** Día nuevo: vacía cola, historial y consecutivos. */
    public void reiniciarDia() {
        espera = new ColaPrioridad();
        atendidos = new ListaEnlazada<>();
        ausentes = new ListaEnlazada<>();
        Arrays.fill(consecutivo, 0);
        enAtencion = new Turno[enAtencion.length];
    }

    public Turno getEnAtencion(int consultorio) {
        return enAtencion[consultorio - 1];
    }

    public ListaEnlazada<Turno> turnosEnEspera() {
        return espera.enOrdenDeLlamado();
    }

    public Turno enEspera(Paciente paciente) {
        return espera.buscar(paciente.getCedula());
    }

    /** Quita de la cola el turno del paciente (al borrar un registro hecho por error). */
    public Turno quitarDeEspera(Paciente paciente) {
        Turno turno = espera.quitar(paciente.getCedula());
        if (turno != null) {
            alCambiar.run();
        }
        return turno;
    }

    public int totalEnEspera() {
        return espera.tamano();
    }

    /** En el orden en que terminaron. */
    public ListaEnlazada<Turno> turnosAtendidos() {
        return atendidos;
    }

    public int totalAtendidos() {
        return atendidos.tamano();
    }

    /** Los que faltaron dos veces. */
    public ListaEnlazada<Turno> turnosAusentes() {
        return ausentes;
    }
}
