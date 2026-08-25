package johnarrieta.imc.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Pasarela HTTP -> TCP para el sistema distribuido de calculo de IMC.
 *
 * El navegador no puede abrir sockets TCP crudos, y el protocolo de la guia es
 * binario (writeFloat / writeUTF), no HTTP. Esta clase actua como adaptador de
 * protocolo: expone una pagina web y un endpoint JSON, y por dentro se comporta
 * como un cliente TCP mas del ServidorTcpImc. El servidor de la guia no se
 * modifica en absoluto.
 *
 * Uso:
 *   java -cp build johnarrieta.imc.web.PasarelaHttp [puertoWeb] [hostTcp] [puertoTcp]
 * Por defecto: 8080 localhost 9007
 *
 * @author Samuel David Ospina De Avila
 */
public class PasarelaHttp {

    private static final int PUERTO_WEB_POR_DEFECTO = 8080;
    private static final String HOST_TCP_POR_DEFECTO = "localhost";
    private static final int PUERTO_TCP_POR_DEFECTO = 9007;

    public static void main(String[] args) throws IOException {
        int puertoWeb = args.length > 0 ? Integer.parseInt(args[0]) : PUERTO_WEB_POR_DEFECTO;
        String hostTcp = args.length > 1 ? args[1] : HOST_TCP_POR_DEFECTO;
        int puertoTcp = args.length > 2 ? Integer.parseInt(args[2]) : PUERTO_TCP_POR_DEFECTO;

        ClienteTcpGateway gateway = new ClienteTcpGateway(hostTcp, puertoTcp);

        HttpServer servidorWeb = HttpServer.create(new InetSocketAddress(puertoWeb), 0);
        // Un hilo por peticion, igual que el SubProcesoCliente de la guia.
        servidorWeb.setExecutor(Executors.newCachedThreadPool());

        servidorWeb.createContext("/", intercambio -> {
            if ("/".equals(intercambio.getRequestURI().getPath())) {
                responder(intercambio, 200, "text/html; charset=utf-8", PAGINA);
            } else {
                responder(intercambio, 404, "text/plain; charset=utf-8", "No encontrado");
            }
        });

        servidorWeb.createContext("/imc", intercambio -> manejarImc(intercambio, gateway));

        servidorWeb.start();
        System.out.println(log() + "Pasarela web escuchando en http://localhost:" + puertoWeb);
        System.out.println(log() + "Reenviando al servidor TCP " + hostTcp + ":" + puertoTcp);
    }

    /** GET /imc?peso=81&altura=1.7  ->  {"imc":28.03,"mensaje":"..."} */
    private static void manejarImc(HttpExchange intercambio, ClienteTcpGateway gateway) throws IOException {
        Map<String, String> parametros = parsearQuery(intercambio.getRequestURI().getRawQuery());
        float peso;
        float altura;
        try {
            peso = Float.parseFloat(parametros.getOrDefault("peso", ""));
            altura = Float.parseFloat(parametros.getOrDefault("altura", ""));
        } catch (NumberFormatException ex) {
            responder(intercambio, 400, "application/json; charset=utf-8",
                    "{\"error\":\"Los parametros peso y altura son obligatorios y numericos\"}");
            return;
        }

        String ipCliente = intercambio.getRemoteAddress().getAddress().getHostAddress();
        System.out.println(log() + "Peticion web de " + ipCliente + " -> peso=" + peso + " altura=" + altura);

        try {
            ClienteTcpGateway.Resultado resultado = gateway.calcular(peso, altura);
            String json = "{\"imc\":" + resultado.imc
                    + ",\"mensaje\":\"" + escaparJson(resultado.mensaje) + "\"}";
            System.out.println(log() + "Respuesta del servidor TCP -> " + json);
            responder(intercambio, 200, "application/json; charset=utf-8", json);
        } catch (IOException ex) {
            System.out.println(log() + "ERROR hablando con el servidor TCP: " + ex.getMessage());
            responder(intercambio, 502, "application/json; charset=utf-8",
                    "{\"error\":\"No hay conexion con el servidor TCP ("
                            + gateway.getHost() + ":" + gateway.getPuerto()
                            + "). Verifica que este ONLINE.\"}");
        }
    }

    private static void responder(HttpExchange intercambio, int codigo, String tipo, String cuerpo)
            throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", tipo);
        intercambio.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        intercambio.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static Map<String, String> parsearQuery(String query) {
        Map<String, String> mapa = new HashMap<>();
        if (query == null) {
            return mapa;
        }
        for (String par : query.split("&")) {
            int igual = par.indexOf('=');
            if (igual > 0) {
                mapa.put(URLDecoder.decode(par.substring(0, igual), StandardCharsets.UTF_8),
                        URLDecoder.decode(par.substring(igual + 1), StandardCharsets.UTF_8));
            }
        }
        return mapa;
    }

    private static String escaparJson(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String log() {
        return new SimpleDateFormat("dd-MM-yyyy hh:mm:ss a").format(new Date()) + " - ";
    }

    /** Pagina web servida en "/". Sin frameworks ni CDN: todo va embebido. */
    private static final String PAGINA = """
        <!doctype html>
        <html lang="es">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Cliente Web IMC</title>
          <style>
            body { font-family: system-ui, sans-serif; max-width: 24rem; margin: 3rem auto;
                   padding: 0 1rem; line-height: 1.5; }
            h1 { font-size: 1.4rem; }
            label { display: block; margin-top: 1rem; font-weight: 600; }
            input { width: 100%; padding: .5rem; font-size: 1rem; box-sizing: border-box; }
            button { margin-top: 1.5rem; width: 100%; padding: .7rem; font-size: 1rem;
                     font-weight: 600; cursor: pointer; }
            #salida { margin-top: 1.5rem; padding: 1rem; background: #f2f2f2; border-radius: .4rem;
                      min-height: 3rem; }
            .imc { font-size: 1.8rem; font-weight: 700; }
            .error { color: #b00020; }
          </style>
        </head>
        <body>
          <h1>Cliente Web IMC</h1>
          <p>Esta pagina habla HTTP con la pasarela, y la pasarela habla TCP con el
             <strong>ServidorTcpImc</strong> de la guia.</p>

          <label for="peso">Peso (kg)</label>
          <input id="peso" type="number" step="0.1" value="81">

          <label for="altura">Altura (m)</label>
          <input id="altura" type="number" step="0.01" value="1.70">

          <button id="calcular">CALCULAR</button>

          <div id="salida"></div>

          <script>
            const salida = document.getElementById('salida');
            document.getElementById('calcular').addEventListener('click', async () => {
              const peso = document.getElementById('peso').value;
              const altura = document.getElementById('altura').value;
              salida.textContent = 'Calculando...';
              try {
                const r = await fetch(`imc?peso=${peso}&altura=${altura}`);
                const d = await r.json();
                if (d.error) {
                  salida.innerHTML = `<span class="error">${d.error}</span>`;
                } else {
                  salida.innerHTML =
                    `<div class="imc">IMC: ${d.imc.toFixed(2)}</div><div>${d.mensaje}</div>`;
                }
              } catch (e) {
                salida.innerHTML = `<span class="error">Fallo la peticion: ${e.message}</span>`;
              }
            });
          </script>
        </body>
        </html>
        """;
}
