import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.Timer;

/**
 * Servidor web para la TV y las tablets. Usa el HttpServer de Java.
 * Los datos se leen en el hilo de la ventana y se sirve la última copia.
 */
final class PantallaWeb {
    static final int PUERTO = 8080;
    private static final int VISIBLES = 6;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Color SUAVE = new Color(0xA9B7C1);

    private static HttpServer servidor;
    private static Timer temporizador;
    private static volatile String datos = "{}";

    private PantallaWeb() {
    }

    /** Arranca el servidor una vez: TV en "/" y tablets en "/doctor". */
    static void iniciar(GestorTurnos gestor, Runnable alCambiar) throws IOException {
        if (servidor == null) {
            HttpServer nuevo = HttpServer.create(new InetSocketAddress(PUERTO), 0);
            nuevo.createContext("/", intercambio -> responder(intercambio, 200, "text/html; charset=UTF-8", PAGINA));
            nuevo.createContext("/datos", intercambio -> responder(intercambio, 200, "application/json; charset=UTF-8", datos));
            AppDoctores.registrar(nuevo, gestor, alCambiar);
            nuevo.start();
            servidor = nuevo;
            datos = construirJson(gestor);
            temporizador = new Timer(2_000, e -> datos = construirJson(gestor));
            temporizador.start();
        }
    }

    static String construirJson(GestorTurnos gestor) {
        StringBuilder j = new StringBuilder("{\"hora\":\"").append(LocalTime.now().format(HORA))
                .append("\",\"consultorios\":[");
        for (int c = 1; c <= gestor.getConsultorios(); c++) {
            Turno t = gestor.getEnAtencion(c);
            if (c > 1) {
                j.append(',');
            }
            j.append("{\"numero\":").append(c)
                    .append(",\"codigo\":\"").append(t == null ? "Libre" : t.getCodigo())
                    .append("\",\"paciente\":\"").append(t == null ? "" : escapar(t.getPaciente().getNombre()))
                    .append("\",\"color\":\"").append(color(t == null ? null : t.getPrioridad())).append("\"}");
        }
        j.append("],\"espera\":[");
        int mostrados = 0;
        for (Turno t : gestor.turnosEnEspera()) {
            if (mostrados == VISIBLES) {
                break;
            }
            if (mostrados > 0) {
                j.append(',');
            }
            j.append("{\"codigo\":\"").append(t.getCodigo())
                    .append("\",\"paciente\":\"").append(escapar(t.getPaciente().getNombre()))
                    .append("\",\"prioridad\":\"").append(t.getPrioridad().getNombre())
                    .append("\",\"color\":\"").append(color(t.getPrioridad())).append("\"}");
            mostrados++;
        }
        j.append("],\"total\":").append(gestor.totalEnEspera()).append('}');
        return j.toString();
    }

    static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String color(Prioridad p) {
        Color c = p == null ? SUAVE : PantallaTurnos.colorClaro(p);
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    static void responder(HttpExchange intercambio, int codigo, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    /** IPv4 de esta computadora; las de adaptadores virtuales van al final. */
    static String direcciones(String ruta) throws IOException {
        List<String> lineas = new ArrayList<>();
        for (NetworkInterface red : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!red.isUp() || red.isLoopback()) {
                continue;
            }
            for (InetAddress d : Collections.list(red.getInetAddresses())) {
                if (d instanceof Inet4Address && !d.isLoopbackAddress() && !d.isLinkLocalAddress()) {
                    lineas.add("http://" + d.getHostAddress() + ":" + PUERTO + "/" + ruta + "   (" + red.getDisplayName() + ")");
                }
            }
        }
        lineas.sort(Comparator.comparingInt(PantallaWeb::prioridad));
        return lineas.isEmpty() ? "http://localhost:" + PUERTO + "/" + ruta : String.join("\n", lineas);
    }

    /** La dirección que va primero (la del WiFi), sin protocolo ni puerto. */
    static String ipPrincipal() throws IOException {
        String primera = direcciones("").split("\n")[0];
        return primera.substring("http://".length(), primera.indexOf(':', "http://".length()));
    }

    private static int prioridad(String linea) {
        String minusculas = linea.toLowerCase();
        if (minusculas.contains("virtual") || minusculas.contains("hyper-v") || minusculas.contains("vethernet")) {
            return 3;
        }
        if (minusculas.contains("wi-fi") || minusculas.contains("wireless") || minusculas.contains("wlan")) {
            return 0;
        }
        return linea.contains("192.168.") ? 1 : 2;
    }

    /** Página de la TV; consulta /datos cada 2 s. */
    private static final String PAGINA = String.join("\n",
            "<!doctype html>",
            "<html lang=es>",
            "<head>",
            "<meta charset=utf-8>",
            "<meta name=viewport content='width=device-width, initial-scale=1'>",
            "<title>Turnos</title>",
            "<style>",
            "  * { box-sizing: border-box; }",
            "  body { margin: 0; min-height: 100vh; display: flex; flex-direction: column;",
            "         background: #132E52; color: #FFFFFF; font-family: 'Segoe UI', Arial, sans-serif; }",
            "  header { display: flex; justify-content: space-between; align-items: center; padding: 28px 40px 20px; }",
            "  h1 { margin: 0; font-family: Bahnschrift, 'Segoe UI', sans-serif; font-size: 48px; }",
            "  .lugar { margin-top: 6px; color: #A8BAD4; font-size: 20px; }",
            "  #hora { font-family: Bahnschrift, 'Segoe UI', sans-serif; font-size: 42px; font-weight: 700; }",
            "  main { flex: 1; display: grid; grid-template-columns: 1fr 1fr; gap: 40px; padding: 0 40px; }",
            "  section { padding: 28px 32px; background: #193A66; }",
            "  h2 { margin: 0 0 18px; font-size: 24px; color: #A8BAD4; }",
            "  .fila { display: flex; justify-content: space-between; align-items: center;",
            "          padding: 14px 0; border-bottom: 1px solid #2B5084; gap: 24px; }",
            "  .nombre { font-size: 30px; }",
            "  .paciente { font-size: 26px; font-weight: 600; color: #DCE5F1; margin-top: 4px; }",
            "  .codigo { font-family: Bahnschrift, 'Segoe UI', sans-serif; font-size: 60px; font-weight: 700; white-space: nowrap; }",
            "  .libre { font-size: 40px; color: #A8BAD4; }",
            "  .espera .fila { justify-content: flex-start; }",
            "  .espera .codigo { font-size: 40px; color: #FFFFFF; }",
            "  .espera .fila:first-child .codigo { font-size: 54px; }",
            "  .espera .paciente { flex: 1; margin: 0; font-size: 28px; }",
            "  .etiqueta { font-size: 22px; font-weight: 700; }",
            "  footer { padding: 20px 40px 28px; text-align: center; color: #A8BAD4; font-size: 24px; }",
            "  #estado { color: #F2B25A; }",
            "  @media (max-width: 900px) {",
            "    main { grid-template-columns: 1fr; gap: 24px; }",
            "    .codigo { font-size: 44px; }",
            "    .espera .codigo { font-size: 32px; }",
            "    .espera .fila:first-child .codigo { font-size: 40px; }",
            "    .nombre { font-size: 24px; }",
            "    h1 { font-size: 36px; }",
            "  }",
            "</style>",
            "</head>",
            "<body>",
            "<header>",
            "  <div><h1>Turnos</h1><div class=lugar>Hospital Primario Héroes y Mártires, El Cuá</div></div>",
            "  <div id=hora>--:--</div>",
            "</header>",
            "<main>",
            "  <section><h2>Pase al consultorio</h2><div id=consultorios></div></section>",
            "  <section class=espera><h2>Próximos turnos</h2><div id=espera></div></section>",
            "</main>",
            "<footer>Cuando aparezca su turno, diríjase al consultorio indicado. <span id=estado></span></footer>",
            "<script>",
            "  function esc(s) {",
            "    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');",
            "  }",
            "  async function actualizar() {",
            "    try {",
            "      const r = await fetch('/datos', { cache: 'no-store' });",
            "      const d = await r.json();",
            "      document.getElementById('hora').textContent = d.hora;",
            "      document.getElementById('consultorios').innerHTML = d.consultorios.map(c =>",
            "        '<div class=fila><div><div class=nombre>Consultorio ' + c.numero + '</div>' +",
            "        '<div class=paciente>' + esc(c.paciente) + '</div></div>' +",
            "        '<span class=\"codigo' + (c.codigo === 'Libre' ? ' libre' : '') + '\" style=color:' + c.color + '>' + esc(c.codigo) + '</span></div>'",
            "      ).join('');",
            "      document.getElementById('espera').innerHTML = d.total === 0",
            "        ? '<p class=nombre>No hay turnos en espera.</p>'",
            "        : d.espera.map(t => '<div class=fila><span class=codigo>' + esc(t.codigo) + '</span>' +",
            "          '<span class=paciente>' + esc(t.paciente) + '</span>' +",
            "          '<span class=etiqueta style=color:' + t.color + '>' + esc(t.prioridad) + '</span></div>').join('');",
            "      document.getElementById('estado').textContent = '';",
            "    } catch (e) {",
            "      document.getElementById('estado').textContent = '(sin conexión con la computadora)';",
            "    }",
            "  }",
            "  actualizar();",
            "  setInterval(actualizar, 2000);",
            "</script>",
            "</body>",
            "</html>");
}
