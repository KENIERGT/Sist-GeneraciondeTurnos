import java.time.Duration;
import java.time.LocalDateTime;

/** Turno de un paciente: código (E-001), prioridad y horas. */
public class Turno {
    private final String codigo;
    private final Paciente paciente;
    private final Prioridad prioridad;
    private final LocalDateTime horaLlegada;
    private LocalDateTime horaLlamado;
    private LocalDateTime horaFin;
    private int consultorio; // 0 mientras espera
    private int ausencias;   // veces que no se presentó

    public Turno(String codigo, Paciente paciente, Prioridad prioridad) {
        this.codigo = codigo;
        this.paciente = paciente;
        this.prioridad = prioridad;
        this.horaLlegada = LocalDateTime.now();
    }

    /** Para cargar desde la BD. */
    Turno(String codigo, Paciente paciente, Prioridad prioridad, LocalDateTime horaLlegada,
          LocalDateTime horaLlamado, LocalDateTime horaFin, int consultorio, int ausencias) {
        this.codigo = codigo;
        this.paciente = paciente;
        this.prioridad = prioridad;
        this.horaLlegada = horaLlegada;
        this.horaLlamado = horaLlamado;
        this.horaFin = horaFin;
        this.consultorio = consultorio;
        this.ausencias = ausencias;
    }

    public String getCodigo() {
        return codigo;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public Prioridad getPrioridad() {
        return prioridad;
    }

    public LocalDateTime getHoraLlegada() {
        return horaLlegada;
    }

    /** null mientras espera. */
    public LocalDateTime getHoraLlamado() {
        return horaLlamado;
    }

    /** null mientras no termine. */
    public LocalDateTime getHoraFin() {
        return horaFin;
    }

    /** Minutos de espera hasta ahora o hasta que lo llamaron. */
    public long minutosDeEspera() {
        LocalDateTime hasta = (horaLlamado != null) ? horaLlamado : LocalDateTime.now();
        return Duration.between(horaLlegada, hasta).toMinutes();
    }

    public int getConsultorio() {
        return consultorio;
    }

    /** Segundos en consulta; 0 si no lo han llamado. */
    public long segundosEnConsulta() {
        if (horaLlamado == null) {
            return 0;
        }
        LocalDateTime hasta = (horaFin != null) ? horaFin : LocalDateTime.now();
        return Duration.between(horaLlamado, hasta).getSeconds();
    }

    public void marcarEnAtencion(int consultorio) {
        this.consultorio = consultorio;
        this.horaLlamado = LocalDateTime.now();
    }

    public int getAusencias() {
        return ausencias;
    }

    public void marcarAusencia() {
        ausencias++;
        this.horaFin = LocalDateTime.now();
    }

    /** Vuelve a espera; conserva llegada y ausencias. */
    public void volverAEspera() {
        this.consultorio = 0;
        this.horaLlamado = null;
        this.horaFin = null;
    }

    public void marcarAtendido() {
        this.horaFin = LocalDateTime.now();
    }
}
