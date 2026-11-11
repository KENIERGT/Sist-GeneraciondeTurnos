import java.sql.SQLException;
import javax.swing.SwingUtilities;

/**
 * Sistema de turnos con prioridad, Hospital Primario Héroes y Mártires, El Cuá.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Estilo.aplicarTema();
            VentanaTurnos ventana;
            try {
                ventana = new VentanaTurnos(new BaseDatos());
            } catch (SQLException e) {
                Dialogos.advertir(null, "No se pudo abrir la base de datos",
                        "No se pudo abrir " + BaseDatos.ARCHIVO + " (" + e.getMessage() + ").\n\n"
                        + "Revise que el programa se ejecute desde su carpeta con ejecutar.bat y que "
                        + "la carpeta lib tenga el archivo sqlite-jdbc.");
                System.exit(1);
                return;
            }
            ventana.setVisible(true);
            ventana.comprobarDia();
        });
    }
}
