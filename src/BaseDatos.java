import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guarda pacientes y turnos en SQLite (datos/turnos.db).
 * Turnos usa la llave (dia, codigo) porque los códigos se reinician cada día.
 */
final class BaseDatos {
    static final String ARCHIVO = "datos" + File.separator + "turnos.db";

    private final Connection conexion;

    BaseDatos() throws SQLException {
        new File("datos").mkdirs();
        conexion = DriverManager.getConnection("jdbc:sqlite:" + ARCHIVO);
        try (Statement s = conexion.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("CREATE TABLE IF NOT EXISTS pacientes ("
                    + " cedula TEXT PRIMARY KEY,"
                    + " primer_nombre TEXT NOT NULL,"
                    + " segundo_nombre TEXT NOT NULL,"
                    + " primer_apellido TEXT NOT NULL,"
                    + " segundo_apellido TEXT NOT NULL,"
                    + " sexo TEXT NOT NULL,"
                    + " edad INTEGER NOT NULL,"
                    + " embarazada INTEGER NOT NULL,"
                    + " registrado TEXT NOT NULL,"
                    + " fecha_nacimiento TEXT)");
            // Bases creadas antes de pedir la fecha de nacimiento.
            try (ResultSet r = s.executeQuery("SELECT 1 FROM pragma_table_info('pacientes') WHERE name = 'fecha_nacimiento'")) {
                if (!r.next()) {
                    s.execute("ALTER TABLE pacientes ADD COLUMN fecha_nacimiento TEXT");
                }
            }
            s.execute("CREATE TABLE IF NOT EXISTS turnos ("
                    + " dia TEXT NOT NULL,"
                    + " codigo TEXT NOT NULL,"
                    + " cedula TEXT NOT NULL REFERENCES pacientes(cedula),"
                    + " prioridad TEXT NOT NULL,"
                    + " estado TEXT NOT NULL,"
                    + " orden INTEGER NOT NULL,"     // en espera: orden de llamado
                    + " consultorio INTEGER NOT NULL,"
                    + " ausencias INTEGER NOT NULL,"
                    + " llegada TEXT NOT NULL,"
                    + " llamado TEXT,"
                    + " fin TEXT,"
                    + " PRIMARY KEY (dia, codigo))");
        }
    }

    void guardarPaciente(Paciente p) throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "INSERT INTO pacientes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, p.getCedula());
            ps.setString(2, p.getPrimerNombre());
            ps.setString(3, p.getSegundoNombre());
            ps.setString(4, p.getPrimerApellido());
            ps.setString(5, p.getSegundoApellido());
            ps.setString(6, p.getSexo());
            ps.setInt(7, p.getEdad());
            ps.setInt(8, p.esEmbarazada() ? 1 : 0);
            ps.setString(9, LocalDateTime.now().toString());
            ps.setString(10, p.getNacimiento() == null ? null : p.getNacimiento().toString());
            ps.executeUpdate();
        }
    }

    /** Carga los pacientes en orden de registro. */
    void cargarPacientes(RegistroPacientes registro) throws SQLException {
        try (Statement s = conexion.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM pacientes ORDER BY rowid")) {
            while (r.next()) {
                List<String> condiciones = r.getInt("embarazada") == 1
                        ? List.of(Paciente.EMBARAZADA) : Collections.emptyList();
                registro.registrar(new Paciente(r.getString("primer_nombre"), r.getString("segundo_nombre"),
                        r.getString("primer_apellido"), r.getString("segundo_apellido"), r.getString("cedula"),
                        r.getString("sexo"), fecha(r.getString("fecha_nacimiento")), r.getInt("edad"), condiciones));
            }
        }
    }

    /**
     * Borra un paciente registrado por error y su turno en espera de hoy. Devuelve false, sin borrar nada,
     * si tiene cualquier otro turno: atendido, ausente, en consulta o de otro día, para no perder historial.
     */
    boolean borrarPaciente(String cedula) throws SQLException {
        String hoy = LocalDate.now().toString();
        conexion.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conexion.prepareStatement(
                    "SELECT COUNT(*) FROM turnos WHERE cedula = ? AND NOT (dia = ? AND estado = ?)")) {
                ps.setString(1, cedula);
                ps.setString(2, hoy);
                ps.setString(3, GestorTurnos.ESPERA);
                try (ResultSet r = ps.executeQuery()) {
                    if (r.next() && r.getInt(1) > 0) {
                        conexion.rollback();
                        return false;
                    }
                }
            }
            try (PreparedStatement turnos = conexion.prepareStatement("DELETE FROM turnos WHERE cedula = ?");
                 PreparedStatement paciente = conexion.prepareStatement("DELETE FROM pacientes WHERE cedula = ?")) {
                turnos.setString(1, cedula);
                turnos.executeUpdate();
                paciente.setString(1, cedula);
                paciente.executeUpdate();
            }
            conexion.commit();
            return true;
        } catch (SQLException e) {
            conexion.rollback();
            throw e;
        } finally {
            conexion.setAutoCommit(true);
        }
    }

    /** Pacientes nuevos registrados hoy. */
    int registradosHoy() throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT COUNT(*) FROM pacientes WHERE registrado LIKE ? || '%'")) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet r = ps.executeQuery()) {
                return r.next() ? r.getInt(1) : 0;
            }
        }
    }

    private static LocalDate fecha(String texto) {
        return texto == null ? null : LocalDate.parse(texto);
    }

    /** Guarda todos los turnos en memoria en una sola transacción. */
    void guardarTurnos(GestorTurnos gestor) throws SQLException {
        conexion.setAutoCommit(false);
        try (PreparedStatement ps = conexion.prepareStatement(
                "INSERT OR REPLACE INTO turnos VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int orden = 0;
            for (Turno t : gestor.turnosEnEspera()) {
                agregar(ps, t, GestorTurnos.ESPERA, orden++);
            }
            for (int c = 1; c <= gestor.getConsultorios(); c++) {
                if (gestor.getEnAtencion(c) != null) {
                    agregar(ps, gestor.getEnAtencion(c), GestorTurnos.ATENCION, 0);
                }
            }
            orden = 0;
            for (Turno t : gestor.turnosAtendidos()) {
                agregar(ps, t, GestorTurnos.ATENDIDO, orden++);
            }
            orden = 0;
            for (Turno t : gestor.turnosAusentes()) {
                agregar(ps, t, GestorTurnos.AUSENTE, orden++);
            }
            ps.executeBatch();
            conexion.commit();
        } catch (SQLException e) {
            conexion.rollback();
            throw e;
        } finally {
            conexion.setAutoCommit(true);
        }
    }

    private static void agregar(PreparedStatement ps, Turno t, String estado, int orden) throws SQLException {
        ps.setString(1, t.getHoraLlegada().toLocalDate().toString());
        ps.setString(2, t.getCodigo());
        ps.setString(3, t.getPaciente().getCedula());
        ps.setString(4, t.getPrioridad().name());
        ps.setString(5, estado);
        ps.setInt(6, orden);
        ps.setInt(7, t.getConsultorio());
        ps.setInt(8, t.getAusencias());
        ps.setString(9, t.getHoraLlegada().toString());
        ps.setString(10, t.getHoraLlamado() == null ? null : t.getHoraLlamado().toString());
        ps.setString(11, t.getHoraFin() == null ? null : t.getHoraFin().toString());
        ps.addBatch();
    }

    /** Carga los turnos de hoy. Hay que cargar los pacientes antes. */
    void cargarTurnosDeHoy(GestorTurnos gestor, RegistroPacientes registro) throws SQLException {
        Map<String, Paciente> porCedula = new HashMap<>();
        for (Paciente p : registro.getPacientes()) {
            porCedula.put(p.getCedula(), p);
        }
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT * FROM turnos WHERE dia = ? ORDER BY estado, orden")) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet r = ps.executeQuery()) {
                while (r.next()) {
                    Turno t = new Turno(r.getString("codigo"), porCedula.get(r.getString("cedula")),
                            Prioridad.valueOf(r.getString("prioridad")), hora(r.getString("llegada")),
                            hora(r.getString("llamado")), hora(r.getString("fin")),
                            r.getInt("consultorio"), r.getInt("ausencias"));
                    gestor.restaurar(t, r.getString("estado"));
                }
            }
        }
    }

    private static LocalDateTime hora(String texto) {
        return texto == null ? null : LocalDateTime.parse(texto);
    }
}
