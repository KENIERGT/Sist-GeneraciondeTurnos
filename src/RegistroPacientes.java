import java.text.Normalizer;

/** Pacientes registrados, en una lista enlazada. Búsquedas secuenciales O(n). */
public class RegistroPacientes {
    private final ListaEnlazada<Paciente> pacientes = new ListaEnlazada<>();
    private int sinCedula;

    /** Id para quien no tiene cédula: SC-001, SC-002... */
    public String nuevoIdSinCedula() {
        return String.format("SC-%03d", ++sinCedula);
    }

    /** False si la cédula ya existe. */
    public boolean registrar(Paciente paciente) {
        if (buscarPorCedula(paciente.getCedula()) != null) {
            return false;
        }
        pacientes.agregar(paciente);
        if (paciente.getCedula().matches("SC-\\d+")) { // seguir la numeración al cargar de la BD
            sinCedula = Math.max(sinCedula, Integer.parseInt(paciente.getCedula().substring(3)));
        }
        return true;
    }

    public Paciente buscarPorCedula(String cedula) {
        for (Paciente paciente : pacientes) {
            if (paciente.getCedula().equalsIgnoreCase(cedula)) {
                return paciente;
            }
        }
        return null;
    }

    /** Busca por cédula o nombre, sin importar mayúsculas ni tildes. */
    public ListaEnlazada<Paciente> buscar(String texto) {
        ListaEnlazada<Paciente> encontrados = new ListaEnlazada<>();
        String buscado = normalizar(texto);
        if (buscado.isEmpty()) {
            return encontrados;
        }
        for (Paciente paciente : pacientes) {
            if (normalizar(paciente.getCedula() + " " + paciente.getNombre()).contains(buscado)) {
                encontrados.agregar(paciente);
            }
        }
        return encontrados;
    }

    /** "perez" encuentra a "Pérez". */
    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim().toLowerCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public boolean eliminar(Paciente paciente) {
        return pacientes.eliminar(paciente);
    }

    public ListaEnlazada<Paciente> getPacientes() {
        return pacientes;
    }

    public int cantidad() {
        return pacientes.tamano();
    }
}
