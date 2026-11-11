import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * Lee el texto del código QR de la cédula nicaragüense. El lector QR lo escribe como un teclado:
 * 12629919<2460704041000K<JARQUIN<TINOCO<BRADYS<JOANKI<EL_CUA<07/04/2004 00:00<MASCULINO<...
 * Orden: código del documento, cédula, apellidos, nombres, lugar, nacimiento y sexo.
 */
final class LectorCedula {
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    /** Datos que llenan el formulario. Los segundos nombres y apellidos pueden venir vacíos. */
    record Datos(String cedula, String primerNombre, String segundoNombre, String primerApellido,
                 String segundoApellido, LocalDate nacimiento, String sexo) {
    }

    private LectorCedula() {
    }

    /** null si el texto no es el QR de una cédula. */
    static Datos leer(String texto) {
        String[] c = texto.trim().split("<", -1);
        if (c.length < 9) {
            return null;
        }
        String cedula = c[1].trim().toUpperCase();
        String sexo = c[8].trim().equalsIgnoreCase("MASCULINO") ? Paciente.MASCULINO
                : c[8].trim().equalsIgnoreCase("FEMENINO") ? Paciente.FEMENINO : null;
        LocalDate nacimiento;
        try {
            nacimiento = LocalDate.parse(c[7].trim().split(" ")[0], FECHA);
        } catch (DateTimeParseException e) {
            return null;
        }
        if (!Paciente.cedulaValida(cedula) || sexo == null || nombre(c[2]).isEmpty() || nombre(c[4]).isEmpty()) {
            return null;
        }
        return new Datos(cedula, nombre(c[4]), nombre(c[5]), nombre(c[2]), nombre(c[3]), nacimiento, sexo);
    }

    /** JARQUIN → Jarquin; MARIA_JOSE → Maria Jose. El QR no trae tildes. */
    private static String nombre(String s) {
        StringBuilder r = new StringBuilder();
        for (String p : s.trim().replace('_', ' ').toLowerCase(Locale.ROOT).split("\\s+")) {
            if (!p.isEmpty()) {
                r.append(r.length() > 0 ? " " : "").append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return r.toString();
    }
}
