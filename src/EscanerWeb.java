import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.function.Function;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.swing.SwingUtilities;

/**
 * Página /recepcion para escanear el QR de la cédula con la cámara del teléfono.
 * Va por HTTPS porque el navegador solo deja usar la cámara en páginas seguras; el certificado
 * es propio (lo crea keytool la primera vez), así que cada teléfono lo acepta una vez.
 */
final class EscanerWeb {
    static final int PUERTO = 8443;
    private static final String ALMACEN = "datos" + File.separator + "recepcion.p12";
    // Solo protege el archivo del certificado local; no es la clave de acceso de la página.
    private static final char[] CONTRASENA = "turnos-local".toCharArray();
    private static final String JSON = "application/json; charset=UTF-8";
    private static HttpsServer servidor;

    private EscanerWeb() {
    }

    /** alRecibir corre en el hilo de la ventana: recibe el texto del QR y devuelve el mensaje, o null si no lo reconoce. */
    static void iniciar(Function<String, String> alRecibir) throws IOException, GeneralSecurityException {
        if (servidor != null) {
            return;
        }
        String jsqr = new String(Files.readAllBytes(Paths.get("lib", "jsQR-1.4.0.js")), StandardCharsets.UTF_8);
        HttpsServer nuevo = HttpsServer.create(new InetSocketAddress(PUERTO), 0);
        nuevo.setHttpsConfigurator(new HttpsConfigurator(contexto()));
        nuevo.createContext("/recepcion", x -> PantallaWeb.responder(x, 200, "text/html; charset=UTF-8", PAGINA));
        nuevo.createContext("/recepcion/jsQR.js", x -> PantallaWeb.responder(x, 200, "text/javascript; charset=UTF-8", jsqr));
        nuevo.createContext("/recepcion/api/clave", x -> {
            if (claveValida(x)) {
                PantallaWeb.responder(x, 200, JSON, "{}");
            }
        });
        nuevo.createContext("/recepcion/api/cedula", x -> recibir(x, alRecibir));
        nuevo.start();
        servidor = nuevo;
    }

    static String direcciones() throws IOException {
        return PantallaWeb.direcciones("recepcion")
                .replace("http://", "https://").replace(":" + PantallaWeb.PUERTO + "/", ":" + PUERTO + "/");
    }

    /** Certificado propio en datos/recepcion.p12; si no existe, lo crea con keytool del JDK. */
    private static SSLContext contexto() throws IOException, GeneralSecurityException {
        File almacen = new File(ALMACEN);
        if (!almacen.exists()) {
            String keytool = Paths.get(System.getProperty("java.home"), "bin", "keytool").toString();
            Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "recepcion", "-keyalg", "RSA",
                    "-keysize", "2048", "-validity", "3650", "-dname", "CN=Sistema de turnos",
                    "-storetype", "PKCS12", "-keystore", ALMACEN, "-storepass", new String(CONTRASENA),
                    "-keypass", new String(CONTRASENA), "-noprompt")
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            int salida;
            try {
                salida = p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Se interrumpió la creación del certificado.", e);
            }
            if (salida != 0 || !almacen.exists()) {
                throw new IOException("keytool no pudo crear el certificado (" + keytool + ").");
            }
        }
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(almacen)) {
            ks.load(in, CONTRASENA);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, CONTRASENA);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);
        return ssl;
    }

    /** Responde 401 y devuelve false si la clave no coincide con la de las tablets. */
    private static boolean claveValida(HttpExchange x) throws IOException {
        String clave = x.getRequestHeaders().getFirst("X-Pin");
        if (clave != null && MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8),
                AppDoctores.CLAVE.getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        PantallaWeb.responder(x, 401, JSON, "{\"error\":\"Clave incorrecta.\"}");
        return false;
    }

    private static void recibir(HttpExchange x, Function<String, String> alRecibir) throws IOException {
        if (!claveValida(x)) {
            return;
        }
        if (!x.getRequestMethod().equals("POST")) {
            PantallaWeb.responder(x, 405, JSON, "{\"error\":\"Use POST.\"}");
            return;
        }
        byte[] cuerpo = x.getRequestBody().readNBytes(4096); // el QR de la cédula ocupa unos 150 caracteres
        String texto = new String(cuerpo, StandardCharsets.UTF_8);
        String[] mensaje = {null};
        try {
            SwingUtilities.invokeAndWait(() -> mensaje[0] = alRecibir.apply(texto));
        } catch (InterruptedException | InvocationTargetException e) {
            PantallaWeb.responder(x, 500, JSON, "{\"error\":\"Error interno del programa.\"}");
            return;
        }
        if (mensaje[0] == null) {
            PantallaWeb.responder(x, 400, JSON, "{\"error\":\"Ese código no es el QR de una cédula.\"}");
        } else {
            PantallaWeb.responder(x, 200, JSON, "{\"mensaje\":\"" + PantallaWeb.escapar(mensaje[0]) + "\"}");
        }
    }

    private static final String PAGINA = """
            <!doctype html>
            <html lang=es>
            <head>
            <meta charset=utf-8>
            <meta name=viewport content="width=device-width, initial-scale=1">
            <meta name=theme-color content="#1D4F91">
            <title>Escanear cédula</title>
            <style>
              :root { --papel:#E9ECE7; --sup:#fff; --tinta:#1E2A30; --suave:#5D686C; --trazo:#CBD2CD;
                      --acento:#1D4F91; --verde:#2E7D4F; --rojo:#A4262C; }
              * { box-sizing: border-box; }
              html, body { margin: 0; height: 100%; }
              body { display: flex; flex-direction: column; background: var(--papel); color: var(--tinta);
                     font: 18px/1.4 'Segoe UI', system-ui, Roboto, sans-serif; }
              header { padding: 14px 18px; background: var(--sup); border-bottom: 1px solid var(--trazo); }
              header b { display: block; font-size: 20px; }
              header small { color: var(--suave); }
              main { flex: 1; display: flex; flex-direction: column; gap: 14px; padding: 16px; }
              .visor { position: relative; overflow: hidden; border-radius: 6px; background: #000; aspect-ratio: 3 / 4; }
              video { width: 100%; height: 100%; object-fit: cover; display: block; }
              .marco { position: absolute; inset: 18%; border: 3px solid rgba(255,255,255,.85); border-radius: 6px; }
              #estado { min-height: 3.2em; padding: 12px 14px; border-radius: 6px; background: var(--sup);
                        border: 1px solid var(--trazo); }
              #estado.ok { color: var(--verde); font-weight: 600; }
              #estado.mal { color: var(--rojo); font-weight: 600; }
              .clave { display: flex; flex-direction: column; gap: 12px; }
              main > * { min-width: 0; }
              input { width: 100%; padding: 14px; border: 2px solid var(--trazo); border-radius: 6px; font: inherit;
                      font-size: 32px; letter-spacing: .3em; text-align: center; }
              button { min-height: 56px; border: 0; border-radius: 6px; background: var(--acento); color: #fff;
                       font: inherit; font-weight: 700; }
              [hidden] { display: none !important; }
            </style>
            </head>
            <body>
            <header><b>Escanear cédula</b><small>Hospital Primario Héroes y Mártires, El Cuá</small></header>
            <main>
              <div class=clave id=pantallaClave>
                <span>Escriba la clave de 6 dígitos que muestra el programa en «TV y tablets (WiFi)».</span>
                <input id=pin inputmode=numeric maxlength=6 autocomplete=off>
                <button id=entrar>Entrar</button>
              </div>
              <div class=visor id=visor hidden><video id=video playsinline muted></video><div class=marco></div></div>
              <div id=estado hidden>Apunte la cámara al código QR de la cédula.</div>
            </main>
            <canvas id=lienzo hidden></canvas>
            <script src="/recepcion/jsQR.js"></script>
            <script>
              const $ = id => document.getElementById(id);
              let pin = '', ultimo = '', hasta = 0, enviando = false;
              try { pin = localStorage.getItem('pinRecepcion') || ''; } catch (e) {}
              const enlace = location.hash.match(/pin=([0-9]{6})/);
              if (enlace) {
                pin = enlace[1];
                try { localStorage.setItem('pinRecepcion', pin); } catch (e) {}
                history.replaceState(null, '', location.pathname);
              }

              function estado(texto, clase) {
                $('estado').hidden = false;
                $('estado').textContent = texto;
                $('estado').className = clase || '';
              }

              async function probarClave(p) {
                const r = await fetch('/recepcion/api/clave', { headers: { 'X-Pin': p }, cache: 'no-store' });
                return r.ok;
              }

              $('entrar').onclick = async () => {
                const p = $('pin').value.trim();
                if (await probarClave(p)) {
                  pin = p;
                  try { localStorage.setItem('pinRecepcion', p); } catch (e) {}
                  empezar();
                } else {
                  estado('Clave incorrecta. Revise la que muestra el programa.', 'mal');
                }
              };

              async function empezar() {
                $('pantallaClave').hidden = true;
                estado('Abriendo la cámara…');
                if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
                  estado('El navegador no deja usar la cámara. Abra la página con https:// y acepte el certificado.', 'mal');
                  return;
                }
                try {
                  const flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
                  $('video').srcObject = flujo;
                  await $('video').play();
                } catch (e) {
                  estado('No se pudo abrir la cámara. Permita el acceso a la cámara para esta página.', 'mal');
                  return;
                }
                $('visor').hidden = false;
                estado('Apunte la cámara al código QR de la cédula.');
                requestAnimationFrame(leer);
              }

              function leer() {
                const v = $('video');
                if (v.readyState >= 2 && !enviando) {
                  const ancho = 800, alto = Math.round(v.videoHeight * ancho / v.videoWidth);
                  const c = $('lienzo'); c.width = ancho; c.height = alto;
                  const ctx = c.getContext('2d', { willReadFrequently: true });
                  ctx.drawImage(v, 0, 0, ancho, alto);
                  const codigo = jsQR(ctx.getImageData(0, 0, ancho, alto).data, ancho, alto, { inversionAttempts: 'dontInvert' });
                  // La misma cédula frente a la cámara no se envía dos veces seguidas.
                  if (codigo && codigo.data.includes('<') && (codigo.data !== ultimo || Date.now() > hasta)) {
                    enviar(codigo.data);
                  }
                }
                requestAnimationFrame(leer);
              }

              async function enviar(texto) {
                enviando = true; ultimo = texto; hasta = Date.now() + 5000;
                estado('Enviando…');
                try {
                  const r = await fetch('/recepcion/api/cedula', { method: 'POST', headers: { 'X-Pin': pin, 'Content-Type': 'text/plain; charset=utf-8' }, body: texto });
                  const j = await r.json();
                  if (r.status === 401) { pin = ''; try { localStorage.removeItem('pinRecepcion'); } catch (e) {} location.reload(); return; }
                  estado(r.ok ? j.mensaje : j.error, r.ok ? 'ok' : 'mal');
                  if (r.ok && navigator.vibrate) navigator.vibrate(120);
                } catch (e) {
                  estado('Sin conexión con el programa. Revise el WiFi.', 'mal');
                }
                setTimeout(() => { enviando = false; }, 1500);
              }

              if (pin) {
                probarClave(pin).then(ok => ok ? empezar() : null);
              }
            </script>
            </body>
            </html>
            """;
}
