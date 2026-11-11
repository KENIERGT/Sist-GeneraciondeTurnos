/**
 * Cola de prioridad: una cola FIFO por nivel.
 * Se atiende la primera que no esté vacía, empezando por Emergencia.
 */
public class ColaPrioridad {
    private final Cola<Turno>[] colas;
    
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ColaPrioridad() {
        colas = (Cola<Turno>[]) new Cola[Prioridad.values().length];
        for (int i = 0; i < colas.length; i++) {
            colas[i] = new Cola<>();
        }
    }

    public void encolar(Turno turno) {
        colas[turno.getPrioridad().ordinal()].encolar(turno);
    }

    /** Siguiente turno a llamar; null si no hay nadie. */
    public Turno desencolar() {
        for (Cola<Turno> cola : colas) {
            if (!cola.estaVacia()) {
                return cola.desencolar();
            }
        }
        return null;
    }

    public int tamano() {
        int total = 0;
        for (Cola<Turno> cola : colas) {
            total += cola.tamano();
        }
        return total;
    }

    /** Cuántos pasarían antes que un turno nuevo de este nivel. */
    public int turnosPorDelante(Prioridad prioridad) {
        int total = 0;
        for (int i = 0; i <= prioridad.ordinal(); i++) {
            total += colas[i].tamano();
        }
        return total;
    }

    public boolean contienePaciente(String cedula) {
        return buscar(cedula) != null;
    }

    /** El turno en espera de esa cédula, o null. */
    public Turno buscar(String cedula) {
        for (Cola<Turno> cola : colas) {
            for (Turno turno : cola) {
                if (turno.getPaciente().getCedula().equalsIgnoreCase(cedula)) {
                    return turno;
                }
            }
        }
        return null;
    }

    /** Saca de la cola el turno de esa cédula; los demás conservan su orden. */
    public Turno quitar(String cedula) {
        Turno turno = buscar(cedula);
        if (turno != null) {
            colas[turno.getPrioridad().ordinal()].eliminar(turno);
        }
        return turno;
    }

    public ListaEnlazada<Turno> enOrdenDeLlamado() {
        ListaEnlazada<Turno> lista = new ListaEnlazada<>();
        for (Cola<Turno> cola : colas) {
            for (Turno turno : cola) {
                lista.agregar(turno);
            }
        }
        return lista;
    }
}
