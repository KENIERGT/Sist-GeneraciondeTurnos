import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Datos del paciente. Adulto mayor sale de la edad; la emergencia va en el turno, no aquí.
 */
public class Paciente {
    public static final String SIN_CONDICION = "Ninguna";
    public static final String EMBARAZADA = "Embarazada";
    public static final String ADULTO_MAYOR = "Adulto mayor";
    public static final String FEMENINO = "Femenino";
    public static final String MASCULINO = "Masculino";

    /** Edad desde la que se sugiere Adulto mayor. */
    public static final int EDAD_ADULTO_MAYOR = 60;

    /** Cédula nicaragüense: 13 dígitos y una letra. */
    private static final String FORMATO_CEDULA = "\\d{13}[A-Z]";

    private final String primerNombre;
    private final String segundoNombre;
    private final String primerApellido;
    private final String segundoApellido;
    private final String cedula;
    private final String sexo;
    private final LocalDate nacimiento; // null en pacientes registrados antes de pedir la fecha
    private final int edad;
    private final Set<String> condiciones;

    public Paciente(String primerNombre, String segundoNombre, String primerApellido, String segundoApellido,
                    String cedula, String sexo, LocalDate nacimiento, int edad, Collection<String> condiciones) {
        this.primerNombre = primerNombre;
        this.segundoNombre = segundoNombre;
        this.primerApellido = primerApellido;
        this.segundoApellido = segundoApellido;
        this.cedula = cedula;
        this.sexo = sexo;
        this.nacimiento = nacimiento;
        this.edad = edad;
        this.condiciones = new LinkedHashSet<>(condiciones);
    }

    /** La cédula debe venir en mayúsculas. */
    public static boolean cedulaValida(String cedula) {
        return cedula.matches(FORMATO_CEDULA);
    }

    public String getPrimerNombre() {
        return primerNombre;
    }

    public String getSegundoNombre() {
        return segundoNombre;
    }

    public String getPrimerApellido() {
        return primerApellido;
    }

    public String getSegundoApellido() {
        return segundoApellido;
    }

    /** Nombre completo sin espacios dobles. */
    public String getNombre() {
        return String.join(" ", primerNombre, segundoNombre, primerApellido, segundoApellido)
                .replaceAll("\\s+", " ").trim();
    }

    public String getCedula() {
        return cedula;
    }

    public String getSexo() {
        return sexo;
    }

    public LocalDate getNacimiento() {
        return nacimiento;
    }

    /** Años cumplidos a hoy; sin fecha de nacimiento, la edad que se registró. */
    public int getEdad() {
        return nacimiento == null ? edad : edadA(nacimiento, LocalDate.now());
    }

    static int edadA(LocalDate nacimiento, LocalDate dia) {
        return Period.between(nacimiento, dia).getYears();
    }

    public boolean esEmbarazada() {
        return condiciones.contains(EMBARAZADA);
    }

    public boolean esAdultoMayor() {
        return getEdad() >= EDAD_ADULTO_MAYOR;
    }

    /** Condiciones para mostrar, incluido Adulto mayor. */
    public String getCondicionesTexto() {
        List<String> lista = new ArrayList<>(condiciones);
        if (esAdultoMayor()) {
            lista.add(ADULTO_MAYOR);
        }
        return lista.isEmpty() ? SIN_CONDICION : String.join(", ", lista);
    }

    @Override
    public String toString() {
        return getNombre() + " (cédula " + cedula + ", " + getEdad() + " años)";
    }
}
