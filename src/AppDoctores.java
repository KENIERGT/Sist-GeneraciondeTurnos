import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * App web de tablet para los doctores (/doctor): llamar, terminar y marcar ausentes.
 * Todo acceso al gestor va por invokeAndWait porque el gestor no es seguro entre hilos.
 */
final class AppDoctores {
    /** Clave de 6 dígitos; cambia cada vez que se abre el programa. */
    static final String CLAVE = String.format("%06d", new SecureRandom().nextInt(1_000_000));
    private static final int COLA_VISIBLE = 30;
    private static final int RECIENTES = 5;
    private static final String JSON = "application/json; charset=UTF-8";
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private AppDoctores() {
    }

    /** Registra la página y la API en el servidor de PantallaWeb. */
    static void registrar(HttpServer servidor, GestorTurnos gestor, Runnable alCambiar) {
        servidor.createContext("/doctor", x -> PantallaWeb.responder(x, 200, "text/html; charset=UTF-8", PAGINA));
        servidor.createContext("/doctor/manifest.json",
                x -> PantallaWeb.responder(x, 200, "application/manifest+json; charset=UTF-8", MANIFIESTO));
        for (String accion : new String[]{"estado", "llamar", "terminar", "ausente"}) {
            servidor.createContext("/doctor/api/" + accion, x -> atender(x, accion, gestor, alCambiar));
        }
    }

    private static void atender(HttpExchange x, String accion, GestorTurnos gestor, Runnable alCambiar)
            throws IOException {
        String clave = x.getRequestHeaders().getFirst("X-Pin");
        if (clave == null || !MessageDigest.isEqual(clave.getBytes(StandardCharsets.UTF_8),
                CLAVE.getBytes(StandardCharsets.UTF_8))) {
            PantallaWeb.responder(x, 401, JSON, error("Clave incorrecta."));
            return;
        }
        boolean escribe = !accion.equals("estado");
        // X-Pin también bloquea órdenes enviadas desde otras páginas.
        if (escribe && !x.getRequestMethod().equals("POST")) {
            PantallaWeb.responder(x, 405, JSON, error("Use POST."));
            return;
        }
        int consultorio = consultorio(x.getRequestURI().getQuery());
        int[] codigo = {200};
        String[] cuerpo = {null};
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (escribe) {
                        cambiar(gestor, accion, consultorio);
                        alCambiar.run();
                    }
                    cuerpo[0] = estado(gestor, consultorio);
                } catch (IllegalArgumentException e) {
                    codigo[0] = 400;
                    cuerpo[0] = error(e.getMessage());
                } catch (IllegalStateException e) {
                    codigo[0] = 409;
                    cuerpo[0] = error(e.getMessage());
                }
            });
        } catch (InterruptedException | InvocationTargetException e) {
            codigo[0] = 500;
            cuerpo[0] = error("Error interno del programa.");
        }
        PantallaWeb.responder(x, codigo[0], JSON, cuerpo[0]);
    }

    /** Valor de c=N; 0 si no es válido. */
    private static int consultorio(String consulta) {
        if (consulta != null) {
            for (String par : consulta.split("&")) {
                if (par.startsWith("c=")) {
                    try {
                        return Integer.parseInt(par.substring(2));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    private static void cambiar(GestorTurnos gestor, String accion, int consultorio) {
        if (consultorio < 1 || consultorio > gestor.getConsultorios()) {
            throw new IllegalArgumentException("Ese consultorio no existe hoy.");
        }
        Turno turno = switch (accion) {
            case "llamar" -> gestor.llamarSiguiente(consultorio);
            case "terminar" -> gestor.finalizarAtencion(consultorio);
            default -> gestor.marcarAusente(consultorio);
        };
        if (turno == null) {
            throw new IllegalStateException(accion.equals("llamar")
                    ? "No hay pacientes en espera." : "El consultorio ya estaba libre.");
        }
    }

    /** JSON con consultorios, cola y últimos atendidos del consultorio c. */
    static String estado(GestorTurnos gestor, int c) {
        StringBuilder j = new StringBuilder("{\"hora\":").append(texto(LocalTime.now().format(HORA)))
                .append(",\"consultorios\":[");
        for (int i = 1; i <= gestor.getConsultorios(); i++) {
            Turno t = gestor.getEnAtencion(i);
            j.append(i > 1 ? "," : "").append("{\"numero\":").append(i)
                    .append(",\"actual\":").append(t == null ? "null" : paciente(t)).append('}');
        }
        StringBuilder cola = new StringBuilder();
        int mostrados = 0;
        int emergencias = 0;
        for (Turno t : gestor.turnosEnEspera()) {
            if (t.getPrioridad() == Prioridad.EMERGENCIA) {
                emergencias++;
            }
            if (mostrados < COLA_VISIBLE) {
                cola.append(mostrados++ > 0 ? "," : "").append(paciente(t));
            }
        }
        j.append("],\"espera\":[").append(cola).append("],\"totalEspera\":").append(gestor.totalEnEspera())
                .append(",\"emergencias\":").append(emergencias)
                .append(",\"totalAtendidos\":").append(gestor.totalAtendidos());

        List<Turno> propios = new ArrayList<>();
        for (Turno t : gestor.turnosAtendidos()) {
            if (t.getConsultorio() == c) {
                propios.add(t);
            }
        }
        j.append(",\"totalMios\":").append(propios.size()).append(",\"mios\":[");
        for (int i = propios.size() - 1; i >= Math.max(0, propios.size() - RECIENTES); i--) {
            j.append(i < propios.size() - 1 ? "," : "").append(paciente(propios.get(i)));
        }
        return j.append("]}").toString();
    }

    private static String paciente(Turno t) {
        Paciente p = t.getPaciente();
        return "{\"codigo\":" + texto(t.getCodigo())
                + ",\"nombre\":" + texto(p.getNombre())
                + ",\"edad\":" + p.getEdad()
                + ",\"sexo\":" + texto(p.getSexo())
                + ",\"condiciones\":" + texto(p.getCondicionesTexto())
                + ",\"prioridad\":" + texto(t.getPrioridad().getNombre())
                + ",\"emergencia\":" + (t.getPrioridad() == Prioridad.EMERGENCIA)
                + ",\"color\":" + texto(color(Estilo.colorDe(t.getPrioridad())))
                + ",\"ausencias\":" + t.getAusencias()
                + ",\"ultima\":" + GestorTurnos.esUltimaOportunidad(t)
                + ",\"esperaMin\":" + t.minutosDeEspera()
                + ",\"consultaSeg\":" + t.segundosEnConsulta()
                + ",\"llegada\":" + texto(hora(t.getHoraLlegada()))
                + ",\"llamado\":" + texto(hora(t.getHoraLlamado()))
                + ",\"fin\":" + texto(hora(t.getHoraFin())) + "}";
    }

    private static String hora(LocalDateTime h) {
        return h == null ? "" : h.format(HORA);
    }

    private static String color(Color c) {
        return String.format("#%06X", c.getRGB() & 0xFFFFFF);
    }

    private static String texto(String s) {
        return "\"" + PantallaWeb.escapar(s) + "\"";
    }

    private static String error(String mensaje) {
        return "{\"error\":" + texto(mensaje) + "}";
    }

    private static final String MANIFIESTO = """
            {"name":"Consultorio - Turnos","short_name":"Consultorio","start_url":"/doctor","display":"standalone",
             "orientation":"landscape","background_color":"#E9ECE7","theme_color":"#1D4F91","lang":"es"}
            """;

    /** Página de la tablet; consulta /doctor/api/estado cada 2 s. */
    private static final String PAGINA = """
            <!doctype html>
            <html lang=es>
            <head>
            <meta charset=utf-8>
            <meta name=viewport content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
            <meta name=theme-color content="#1D4F91">
            <meta name=mobile-web-app-capable content=yes>
            <meta name=apple-mobile-web-app-capable content=yes>
            <link rel=manifest href=/doctor/manifest.json>
            <title>Consultorio</title>
            <style>
              :root { --papel:#E9ECE7; --sup:#fff; --tinta:#1E2A30; --suave:#5D686C; --trazo:#CBD2CD;
                      --acento:#1D4F91; --oscuro:#163E73; --tenue:#E3EBF5; --rojo:#A4262C; --ambar:#8A5300; }
              * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
              html, body { height: 100%; margin: 0; }
              body { display: flex; flex-direction: column; background: var(--papel); color: var(--tinta);
                     font: 18px/1.35 Bahnschrift, 'Segoe UI', system-ui, Roboto, sans-serif;
                     -webkit-user-select: none; user-select: none; touch-action: manipulation; overflow: hidden; }
              button { font: inherit; border: 0; cursor: pointer; touch-action: manipulation; }
              button:disabled { opacity: .45; cursor: default; }
              .num { font-variant-numeric: tabular-nums; }

              header { display: flex; align-items: center; gap: 16px; padding: 12px 24px; min-height: 76px;
                       background: var(--sup); color: var(--tinta); border-bottom: 1px solid var(--trazo); }
              header h1 { margin: 0; font-size: 30px; line-height: 1.1; }
              header small { display: block; color: var(--suave); font-size: 15px; }
              .espacio { flex: 1; }
              .cifra { display: flex; align-items: baseline; gap: 6px; padding: 0 8px; }
              .cifra b { font-size: 28px; line-height: 1.1; }
              .cifra span { font-size: 15px; color: var(--suave); }
              .claro { min-height: 56px; padding: 0 20px; border-radius: 6px; font-weight: 700; border: 2px solid var(--trazo);
                       background: var(--sup); color: var(--tinta); }
              #hora { min-width: 76px; font-size: 30px; font-weight: 700; text-align: right; }
              #con { width: 14px; height: 14px; border-radius: 50%; background: #2E8B57; }
              #con.off { background: var(--rojo); }

              #alerta { display: none; padding: 12px 24px; background: var(--rojo); color: #fff;
                        font-size: 22px; font-weight: 700; }
              #alerta.ver { display: block; animation: parpadeo 1.4s ease-in-out infinite; }
              @keyframes parpadeo { 50% { background: #7D1A1F; } }

              main { flex: 1; min-height: 0; display: grid; grid-template-columns: 1.45fr 1fr; gap: 20px; padding: 20px 24px; }
              .tarjeta { display: flex; flex-direction: column; min-height: 0; background: var(--sup);
                         border: 1px solid var(--trazo); border-radius: 6px; overflow: hidden; }
              .actual { padding: 28px 32px; overflow: auto; }
              .etq { font-size: 17px; font-weight: 600; color: var(--suave); }
              .codigo { margin: 4px 0; font-size: 118px; font-weight: 700; line-height: 1; color: var(--p); }
              .nombre { font-size: 38px; font-weight: 700; line-height: 1.15; }
              .datos { display: flex; flex-wrap: wrap; gap: 8px 22px; margin: 14px 0; }
              .chip { display: inline-flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 700; }
              .chip::before { content: ""; width: 12px; height: 12px; background: var(--p); }
              .chip.suave { color: var(--suave); font-weight: 600; }
              .chip.suave::before { display: none; }
              .tiempos { display: flex; gap: 44px; color: var(--suave); font-size: 16px; }
              .tiempos b { display: block; font-size: 44px; line-height: 1.1; color: var(--tinta); }
              .vacio { padding: 28px 20px; color: var(--suave); text-align: center; }
              .grande { margin-top: auto; }
              .boton { width: 100%; min-height: 104px; margin-top: 18px; border-radius: 6px; background: var(--acento);
                       color: #fff; font-size: 32px; font-weight: 700; }
              .boton:active:not(:disabled) { background: var(--oscuro); }
              .boton.fin { background: var(--tinta); }
              .boton.fin.seguro { background: var(--ambar); }
              .secundario { width: 100%; min-height: 72px; margin-top: 12px; border: 2px solid var(--trazo); border-radius: 6px;
                            background: var(--sup); color: var(--tinta); font-size: 22px; font-weight: 700; }
              .secundario.seguro { border-color: var(--ambar); background: var(--ambar); color: #fff; }
              .proximo { display: flex; align-items: center; gap: 14px; margin-top: 16px; padding: 12px 16px;
                         border-radius: 6px; background: var(--papel); color: var(--suave); }
              .proximo b { font-size: 26px; }

              .derecha { display: flex; flex-direction: column; gap: 20px; min-height: 0; }
              .cola { flex: 1; }
              .mios { flex: 0 0 250px; }
              h2 { display: flex; justify-content: space-between; margin: 0; padding: 14px 20px; font-size: 20px;
                   border-bottom: 1px solid var(--trazo); }
              h2 span { color: var(--suave); }
              .lista { flex: 1; overflow: auto; -webkit-overflow-scrolling: touch; }
              .fila { display: grid; grid-template-columns: 30px 92px 1fr auto; gap: 10px; align-items: center;
                      min-height: 68px; padding: 8px 20px; border-bottom: 1px solid var(--trazo); }
              .fila.prim { background: var(--tenue); }
              .pos { color: var(--suave); font-size: 16px; }
              .cod { color: var(--p); font-size: 26px; font-weight: 700; }
              .nom { overflow: hidden; font-size: 20px; text-overflow: ellipsis; white-space: nowrap; }
              .sub { color: var(--suave); font-size: 14px; }

              .pantalla { position: fixed; inset: 0; z-index: 5; display: none; flex-direction: column; align-items: center;
                          justify-content: center; gap: 22px; padding: 24px; background: var(--papel); text-align: center; }
              .pantalla.ver { display: flex; }
              .pantalla h2 { display: block; border: 0; padding: 0; font-size: 36px; }
              .losas { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 18px; width: min(960px, 100%); }
              .losa { padding: 24px 12px; border: 2px solid var(--trazo); border-radius: 6px; background: var(--sup);
                      color: var(--tinta); font-size: 22px; }
              .losa b { display: block; color: var(--acento); font-size: 52px; line-height: 1.1; }
              .losa small { display: block; margin-top: 6px; color: var(--suave); font-size: 16px; }
              .losa.ocupado b { color: var(--ambar); }
              #pin { width: 300px; padding: 14px; border: 2px solid var(--trazo); border-radius: 6px; font: inherit;
                     font-size: 40px; letter-spacing: .3em; text-align: center; -webkit-user-select: text; user-select: text; }
              #pinError { min-height: 1.4em; margin: 0; color: var(--rojo); font-weight: 700; }
              #aviso { position: fixed; left: 50%; bottom: 28px; z-index: 9; display: none; max-width: 90%;
                       padding: 14px 26px; border-radius: 6px; background: var(--tinta); color: #fff;
                       font-size: 20px; transform: translateX(-50%); }

              @media (max-width: 900px), (orientation: portrait) {
                body { overflow: auto; }
                main { grid-template-columns: 1fr; }
                .mios { flex-basis: auto; }
                .codigo { font-size: 84px; }
                header { flex-wrap: wrap; }
              }
            </style>
            </head>
            <body>
            <header>
              <div><h1 id=titulo>Consultorio</h1><small>Hospital Primario Héroes y Mártires, El Cuá</small></div>
              <div class=espacio></div>
              <div class=cifra><b id=nEspera class=num>0</b><span>En espera</span></div>
              <div class=cifra><b id=nHoy class=num>0</b><span>Atendidos hoy</span></div>
              <button class=claro id=cambiar>Cambiar consultorio</button>
              <div id=hora class=num>--:--</div>
              <div id=con title="Conexión con el programa"></div>
            </header>
            <div id=alerta></div>
            <main>
              <section class="tarjeta actual" id=actual>
                <div id=info></div>
                <div class=grande></div>
                <button class=boton id=accion disabled>Llamar siguiente</button>
                <button class=secundario id=ausente style="display:none">No se presentó</button>
                <div id=proximo></div>
              </section>
              <div class=derecha>
                <section class="tarjeta cola"><h2>Cola de espera <span id=nCola></span></h2><div class=lista id=cola></div></section>
                <section class="tarjeta mios"><h2>Atendidos en este consultorio <span id=nMios></span></h2><div class=lista id=mios></div></section>
              </div>
            </main>
            <div class=pantalla id=elegir><h2>¿En qué consultorio está?</h2><div class=losas id=losas></div></div>
            <div class=pantalla id=clave>
              <h2>Clave de la tablet</h2>
              <p>Aparece en el programa de recepción al abrir «TV y tablets».</p>
              <input id=pin inputmode=numeric maxlength=6 autocomplete=off>
              <p id=pinError></p>
              <button class=boton id=entrar style="width:300px;min-height:72px;font-size:26px">Entrar</button>
            </div>
            <div id=aviso></div>
            <script>
              const $ = id => document.getElementById(id);
              const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
              function guardado(k, v) {
                try {
                  if (v === undefined) return localStorage.getItem(k);
                  if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v);
                } catch (e) { return null; }
              }
              let pin = guardado('pin') || '', cons = parseInt(guardado('cons')) || 0;
              const enlace = location.hash.match(/pin=([0-9]{6})/);
              if (enlace) { pin = enlace[1]; guardado('pin', pin); history.replaceState(null, '', location.pathname); }
              let d = null, t0 = 0, confirmar = '', plazo = 0, ocupado = false, leyendo = false;
              let seq = 0, aplicada = 0, emerg = -1, audio = null, cartel = 0;

              async function api(ruta, post) {
                const r = await fetch('/doctor/api/' + ruta, { method: post ? 'POST' : 'GET', headers: { 'X-Pin': pin }, cache: 'no-store' });
                if (r.status === 401) { pin = ''; guardado('pin', null); mostrar('clave'); throw new Error('Clave incorrecta.'); }
                const j = await r.json();
                if (!r.ok) throw new Error(j.error || 'Error');
                return j;
              }
              // Descarta respuestas viejas.
              function aplicar(v, estado) {
                if (v < aplicada) return false;
                aplicada = v; d = estado; t0 = Date.now();
                return true;
              }
              async function refrescar() {
                if (!pin || leyendo) return;
                leyendo = true;
                try {
                  const v = ++seq, r = await api('estado?c=' + cons);
                  $('con').className = '';
                  if (aplicar(v, r)) pintar();
                } catch (e) {
                  if (e.message.indexOf('Clave') < 0) $('con').className = 'off';
                }
                leyendo = false;
              }
              function mostrar(id) { ['elegir', 'clave'].forEach(x => $(x).classList.toggle('ver', x === id)); }
              function avisar(texto) {
                const a = $('aviso'); a.textContent = texto; a.style.display = 'block';
                clearTimeout(cartel); cartel = setTimeout(() => { a.style.display = 'none'; }, 3500);
              }
              // Solo redibuja si cambió, para no perder toques.
              function poner(id, html) {
                const e = $(id);
                if (e._h === html) return;
                const s = e.scrollTop; e._h = html; e.innerHTML = html; e.scrollTop = s;
              }
              const dur = s => s < 60 ? s + ' s' : Math.round(s / 60) + ' min';
              const mmss = s => { const m = Math.floor(s / 60), r = s % 60; return (m < 10 ? '0' : '') + m + ':' + (r < 10 ? '0' : '') + r; };

              function pintar() {
                if (!d) return;
                $('hora').textContent = d.hora;
                $('nEspera').textContent = d.totalEspera;
                $('nHoy').textContent = d.totalAtendidos;
                if (cons < 1 || cons > d.consultorios.length) { elegir(); return; }
                mostrar(null);
                $('titulo').textContent = 'Consultorio ' + cons;
                const mi = d.consultorios[cons - 1].actual, sig = d.espera[0] || null;
                $('actual').style.setProperty('--p', mi ? mi.color : sig ? sig.color : '#B8C4CD');
                poner('info', mi ? htmlAtendiendo(mi) : htmlLibre(sig));
                poner('proximo', !mi ? '' : sig
                  ? '<div class=proximo>Siguiente: <b style="color:' + sig.color + '">' + esc(sig.codigo) + '</b> ' + esc(sig.nombre) + '</div>'
                  : '<div class=proximo>No hay más pacientes en espera.</div>');
                const b = $('accion');
                b.className = 'boton' + (mi ? ' fin' + (confirmar === 'terminar' ? ' seguro' : '') : '');
                b.textContent = mi ? (confirmar === 'terminar' ? 'Toque otra vez para confirmar' : 'Terminé de atender') : 'Llamar siguiente';
                b.disabled = ocupado || (!mi && !sig);
                const a = $('ausente');
                a.style.display = mi ? 'block' : 'none';
                a.className = 'secundario' + (confirmar === 'ausente' ? ' seguro' : '');
                a.textContent = confirmar !== 'ausente' ? 'No se presentó'
                  : mi && mi.ultima ? 'Toque otra vez: quedará como ausente' : 'Toque otra vez: vuelve al final de la cola';
                a.disabled = ocupado;

                poner('cola', d.espera.length
                  ? d.espera.map(filaCola).join('') + (d.totalEspera > d.espera.length ? '<div class=vacio>y ' + (d.totalEspera - d.espera.length) + ' más…</div>' : '')
                  : '<div class=vacio>No hay pacientes en espera.</div>');
                $('nCola').textContent = d.totalEspera;
                poner('mios', d.mios.length ? d.mios.map(filaMia).join('') : '<div class=vacio>Todavía no ha atendido a nadie hoy.</div>');
                $('nMios').textContent = d.totalMios;
                alertaEmergencia();
              }
              function chips(t) {
                return '<span class=chip>' + esc(t.prioridad) + '</span><span class="chip suave">' + t.edad + ' años</span>'
                  + '<span class="chip suave">' + esc(t.sexo) + '</span>'
                  + (t.ausencias ? '<span class="chip suave">Segundo llamado</span>' : '')
                  + (t.condiciones === 'Ninguna' || t.condiciones === t.prioridad ? '' :'<span class="chip suave">' + esc(t.condiciones) + '</span>');
              }
              function htmlAtendiendo(t) {
                return '<div class=etq>En atención</div><div class=codigo>' + esc(t.codigo) + '</div>'
                  + '<div class=nombre>' + esc(t.nombre) + '</div><div class=datos>' + chips(t) + '</div>'
                  + '<div class=tiempos><div>En consulta<b class=num id=tcons>' + mmss(t.consultaSeg) + '</b></div>'
                  + '<div>Esperó<b class=num>' + t.esperaMin + ' min</b></div><div>Llamado<b class=num>' + esc(t.llamado) + '</b></div></div>';
              }
              function htmlLibre(t) {
                if (!t) return '<div class=etq>Consultorio libre</div><div class=nombre style="margin-top:36px">No hay pacientes en espera</div>'
                  + '<p class=sub style="font-size:18px">Cuando llegue alguien aparecerá aquí.</p>';
                return '<div class=etq>Consultorio libre. Sigue en la cola:</div><div class=codigo>' + esc(t.codigo) + '</div>'
                  + '<div class=nombre>' + esc(t.nombre) + '</div><div class=datos>' + chips(t) + '</div>'
                  + '<div class=tiempos><div>Lleva esperando<b class=num>' + t.esperaMin + ' min</b></div>'
                  + '<div>Llegó<b class=num>' + esc(t.llegada) + '</b></div></div>';
              }
              function filaCola(t, i) {
                return '<div class="fila' + (i ? '' : ' prim') + '" style="--p:' + esc(t.color) + '"><span class=pos>' + (i + 1) + '</span>'
                  + '<span class=cod>' + esc(t.codigo) + '</span><div class=nom>' + esc(t.nombre)
                  + '<div class=sub>' + esc(t.prioridad) + ', ' + t.edad + ' años' + (t.ausencias ? ', faltó una vez' : '') + '</div></div>'
                  + '<span class="sub num">' + t.esperaMin + ' min</span></div>';
              }
              function filaMia(t) {
                return '<div class=fila style="--p:' + esc(t.color) + '"><span class=pos>✓</span><span class=cod>' + esc(t.codigo) + '</span>'
                  + '<div class=nom>' + esc(t.nombre) + '<div class=sub>' + esc(t.llamado) + ' a ' + esc(t.fin) + '</div></div>'
                  + '<span class="sub num">' + dur(t.consultaSeg) + '</span></div>';
              }
              function alertaEmergencia() {
                const urgentes = d.espera.filter(t => t.emergencia);
                const a = $('alerta');
                a.classList.toggle('ver', urgentes.length > 0);
                a.textContent = urgentes.length ? '⚠ Emergencia en espera: ' + urgentes.map(t => t.codigo + ' ' + t.nombre).join(', ') : '';
                if (emerg >= 0 && d.emergencias > emerg) pitar();
                emerg = d.emergencias;
              }
              function elegir() {
                mostrar('elegir');
                $('losas').innerHTML = d.consultorios.length ? d.consultorios.map(c =>
                  '<button class="losa' + (c.actual ? ' ocupado' : '') + '" data-c="' + c.numero + '"><b>' + c.numero + '</b>Consultorio'
                  + '<small>' + (c.actual ? 'Atendiendo ' + esc(c.actual.codigo) : 'Libre') + '</small></button>').join('')
                  : '<p>El programa de recepción todavía no tiene consultorios configurados.</p>';
              }
              function audioListo() {
                try { audio = audio || new (window.AudioContext || window.webkitAudioContext)(); audio.resume(); } catch (e) { }
              }
              function pitar() {
                if (!audio) return;
                const o = audio.createOscillator(), g = audio.createGain();
                o.frequency.value = 880; g.gain.value = .25; o.connect(g); g.connect(audio.destination);
                o.start(); o.stop(audio.currentTime + .4);
              }

              $('losas').onclick = e => {
                const b = e.target.closest('[data-c]');
                if (!b) return;
                cons = +b.dataset.c; guardado('cons', cons); pintar();
              };
              $('cambiar').onclick = () => { cons = 0; guardado('cons', null); pintar(); };
              async function pulsar(accion) {
                const mi = d.consultorios[cons - 1].actual;
                if (accion !== 'llamar' && confirmar !== accion) { // terminar y ausente piden confirmar
                  confirmar = accion; clearTimeout(plazo);
                  plazo = setTimeout(() => { confirmar = ''; pintar(); }, 4000);
                  pintar(); return;
                }
                clearTimeout(plazo); confirmar = ''; ocupado = true; pintar();
                try {
                  const v = ++seq, r = await api(accion + '?c=' + cons, true);
                  aplicar(v, r);
                  avisar(accion === 'terminar' ? 'Atención de ' + mi.codigo + ' terminada.'
                    : accion === 'ausente' ? mi.codigo + (mi.ultima ? ' quedó como ausente.' : ' vuelve al final de la cola.')
                    : 'Llamó a ' + r.consultorios[cons - 1].actual.codigo + '.');
                } catch (e) { avisar(e.message); }
                ocupado = false; pintar(); refrescar();
              }
              $('accion').onclick = () => pulsar(d.consultorios[cons - 1].actual ? 'terminar' : 'llamar');
              $('ausente').onclick = () => pulsar('ausente');
              $('entrar').onclick = async () => {
                pin = $('pin').value.trim();
                if (!pin) return;
                guardado('pin', pin); $('pinError').textContent = '';
                try { await api('estado?c=' + cons); mostrar(null); refrescar(); }
                catch (e) { $('pinError').textContent = e.message; }
              };
              $('pin').onkeydown = e => { if (e.key === 'Enter') $('entrar').click(); };
              document.addEventListener('pointerdown', audioListo);

              setInterval(() => {
                const el = $('tcons');
                if (el && d && cons >= 1 && cons <= d.consultorios.length && d.consultorios[cons - 1].actual) {
                  el.textContent = mmss(d.consultorios[cons - 1].actual.consultaSeg + Math.floor((Date.now() - t0) / 1000));
                }
              }, 1000);
              setInterval(refrescar, 2000);
              if (pin) refrescar(); else mostrar('clave');
            </script>
            </body>
            </html>
            """;
}
