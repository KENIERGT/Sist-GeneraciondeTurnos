/** Niveles de prioridad, de más a menos urgente. */
public enum Prioridad {
    EMERGENCIA('E', "Emergencia"),
    EMBARAZADA('P', "Embarazada"),
    ADULTO_MAYOR('A', "Adulto mayor"),
    GENERAL('G', "General");

    private final char prefijo;   // E, P, A o G al inicio del código
    private final String nombre;

    Prioridad(char prefijo, String nombre) {
        this.prefijo = prefijo;
        this.nombre = nombre;
    }

    public char getPrefijo() {
        return prefijo;
    }

    public String getNombre() {
        return nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
