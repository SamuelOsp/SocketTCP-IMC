# Sistema Distribuido Cliente-Servidor con Sockets TCP — Cálculo de IMC

Autor: **Samuel David Ospina De Avila**

## Estructura

```
GuiaSocketTCP-IMC/
├── ServidorTcpImc/src/johnarrieta/imc/
│   ├── Principal.java                  (main del servidor)
│   ├── modelo/CalculoImc.java          (lógica del IMC)
│   ├── servidor/ServidorTcp.java       (ServerSocket, acepta clientes)
│   ├── servidor/SubProcesoCliente.java (un hilo por cliente)
│   └── vistas/VentanaPrincipal.java    (GUI Swing del servidor)
├── ClienteTcpImc/src/johnarrieta/imc/cliente/
│   ├── Principal.java                       (main del cliente)
│   └── vistas/VentanaPrincipal.java         (GUI Swing del cliente)
├── PasarelaWebImc/src/johnarrieta/imc/web/   (AÑADIDO — ver sección ngrok)
│   ├── PasarelaHttp.java                (servidor HTTP + página web)
│   └── ClienteTcpGateway.java           (cliente TCP interno de la pasarela)
└── ngrok.yml                            (definición de los dos túneles)
```

## Compilar y ejecutar por consola

Servidor:
```
cd ServidorTcpImc
javac -d build $(find src -name "*.java")
java -cp build johnarrieta.imc.Principal
```

Cliente:
```
cd ClienteTcpImc
javac -d build $(find src -name "*.java")
java -cp build johnarrieta.imc.cliente.Principal
```

## Abrir en NetBeans

`File > New Project > Java with Ant > Java Project with Existing Sources`,
y apuntar la carpeta de fuentes a `ServidorTcpImc/src` (y otro proyecto para
`ClienteTcpImc/src`).

## Cómo probar

1. Ejecutar el **Servidor**, pestaña CONEXION, puerto `9007`, botón **INICIAR**
   (el estado pasa a ONLINE).
2. Ejecutar el **Cliente**, IP `localhost`, puerto `9007`, botón **Conectar**.
3. En el cliente ir a la pestaña **CALCULAR IMC**, escribir peso (ej. `81`) y
   altura en metros (ej. `1.7`), botón **CALCULAR**.
4. El resultado aparece en el cliente y el detalle en la pestaña
   **LOG DE CONEXIONES** del servidor.

## Protocolo de comunicación

| Sentido | Datos |
|---|---|
| Cliente → Servidor | `writeFloat(peso)`, `writeFloat(altura)` |
| Servidor → Cliente | `writeFloat(imc)`, `writeUTF(mensaje)` |

---

# Exponer la aplicación a Internet con ngrok

Hasta aquí el sistema solo funciona en `localhost` o dentro de la misma red WiFi. Esta
sección lo publica en Internet para que **cualquier persona, desde otro computador o
desde el navegador de un celular**, pueda usarlo.

## 1. El problema: NAT

Este computador tiene una dirección IP **privada** (`192.168.x.x`), asignada por el
router de la casa. Esa dirección no existe en Internet: nadie desde afuera puede
escribir `192.168.1.15:9007` y llegar hasta aquí. El router hace **NAT** (Network
Address Translation) y solo deja pasar el tráfico de vuelta de conexiones que
**salieron** desde adentro. Las conexiones entrantes se descartan.

## 2. La solución: un túnel inverso (reverse tunnel)

ngrok le da la vuelta al problema:

1. El agente `ngrok`, corriendo en **este** computador, abre una conexión **saliente**
   (el NAT sí la permite) hacia un servidor de ngrok en la nube.
2. Ese servidor de ngrok **sí** tiene IP pública. Se pone a escuchar en una dirección
   como `0.tcp.ngrok.io:17482`.
3. Cuando alguien en Internet se conecta a esa dirección, ngrok **reenvía cada byte**
   por el túnel ya establecido hasta `localhost:9007` de este computador, y devuelve
   la respuesta por el mismo camino.

Es equivalente a un *port forwarding* del router, pero sin tocar el router y con TLS
entre el agente y la nube de ngrok.

### ¿Es una VPN?

No exactamente, y vale la pena decirlo bien:

| | VPN | ngrok (túnel inverso) |
|---|---|---|
| Qué conecta | Redes completas, a nivel IP | **Un solo puerto** de **un solo servicio** |
| Sentido | Bidireccional | Entrante hacia el servicio expuesto |
| Quién ve qué | El cliente entra a toda la LAN | El cliente solo ve el puerto 9007 / 8080 |

Así que ngrok expone un servicio, no une redes. Para este proyecto es justo lo que se
necesita, y además es más seguro que abrir la LAN entera.

### ¿Por qué `ngrok tcp` y no `ngrok http`?

El protocolo de la guía es **binario**: `writeFloat`, `writeFloat`, `readFloat`,
`readUTF`. No es HTTP. `ngrok tcp` es un tubo de bytes transparente que no interpreta el
contenido, así que la trama llega intacta. `ngrok http` esperaría peticiones HTTP y
rompería la comunicación. **Para el servidor de la guía siempre se usa `tcp`.**

## 3. El segundo problema: el navegador no habla TCP crudo

El `ClienteTcpImc` es una aplicación **Swing**: no corre dentro de un navegador. Y un
navegador solo sabe hablar HTTP/WebSocket, no puede abrir un socket TCP crudo. Por eso
se añadió `PasarelaWebImc`, un **adaptador de protocolo** (patrón *gateway*):

- Sirve una página web en `http://localhost:8080`.
- Recibe `GET /imc?peso=81&altura=1.7`.
- Por dentro abre `new Socket("localhost", 9007)` y habla **exactamente el mismo
  protocolo binario** que el cliente Swing.
- Devuelve `{"imc":28.027681,"mensaje":"Debes bajar un poco de peso"}`.

**El `ServidorTcpImc` no se modificó en absoluto.** Para él, la pasarela es simplemente
otro cliente TCP más, atendido por su propio `SubProcesoCliente`. La pasarela usa
`com.sun.net.httpserver.HttpServer`, que viene incluido en el JDK: cero dependencias
externas, cero Maven.

## 4. Arquitectura resultante

```
  Navegador / celular  ──HTTPS──>  ngrok cloud  ──túnel──>  PasarelaHttp :8080
  (cualquier parte)                                                 │
                                                                    │ TCP binario
                                                                    │ writeFloat/readUTF
                                                                    v
  ClienteTcpImc (Swing) ──TCP───>  ngrok cloud  ──túnel──>   ServidorTcp :9007
  (otro PC)                                                   │
                                                              └─ un SubProcesoCliente
                                                                 (hilo) por conexión
```

Los dos caminos terminan en el mismo `ServidorTcp`, y como éste crea un hilo por
conexión, ambos pueden estar activos **al mismo tiempo**.

## 5. Instalación de ngrok

```bash
brew install ngrok
# Crear cuenta gratuita en https://dashboard.ngrok.com/signup
ngrok config add-authtoken <TU_TOKEN_DEL_DASHBOARD>
```

El authtoken queda guardado en
`~/Library/Application Support/ngrok/ngrok.yml`, **fuera del repositorio**.

## 6. Compilar y ejecutar la pasarela web

```bash
cd PasarelaWebImc
javac -d build $(find src -name "*.java")
java -cp build johnarrieta.imc.web.PasarelaHttp
# opcional: java -cp build johnarrieta.imc.web.PasarelaHttp <puertoWeb> <hostTcp> <puertoTcp>
```

Por defecto: web en `8080`, servidor TCP en `localhost:9007`.

## 7. Levantar los túneles

### Opción A — solo el túnel web (sin tarjeta, funciona ya)

```bash
ngrok start imc-web \
  --config "$HOME/Library/Application Support/ngrok/ngrok.yml" \
  --config ./ngrok.yml
```

Da una URL tipo `https://xxxx-xxxx-xxx.ngrok-free.dev`, que se abre desde **cualquier
navegador del mundo**. El cliente Swing se demuestra entonces por **LAN**: en el otro
equipo se pone la IP local de este Mac (`ipconfig getifaddr en0`) y el puerto `9007`.
La arquitectura es idéntica; lo único que cambia es que el tramo TCP no sale a Internet.

### Opción B — los dos túneles (requiere verificar tarjeta)

```bash
ngrok start --all \
  --config "$HOME/Library/Application Support/ngrok/ngrok.yml" \
  --config ./ngrok.yml
```

Da además `tcp://0.tcp.ngrok.io:XXXXX`, que se pone en el **ClienteTcpImc** de Swing
(IP = `0.tcp.ngrok.io`, PUERTO = `XXXXX`).

> **Importante — comprobado en este proyecto:** el plan gratuito de ngrok **no permite
> endpoints TCP** hasta verificar una tarjeta en
> https://dashboard.ngrok.com/settings#id-verification (error `ERR_NGROK_8013`; ngrok
> indica que la tarjeta no se cobra, es solo antiabuso). Además, si el túnel TCP falla,
> `ngrok start --all` **cierra la sesión completa** y tampoco levanta el web — por eso
> existe la Opción A, que arranca únicamente `imc-web`.

### Notas del plan gratuito

- La dirección TCP y el subdominio **cambian cada vez que se reinicia ngrok**.
- La URL HTTPS gratuita muestra una **página intersticial de advertencia** la primera
  visita: basta pulsar *"Visit Site"*.
- `http://localhost:4040` es el inspector de ngrok: muestra el tráfico en vivo. Muy útil
  para el video.

### Seguridad

Mientras el túnel esté arriba, **el servicio es público en Internet**. Hay que cerrar
ngrok con `Ctrl-C` al terminar la demostración, y nunca subir el authtoken al repo.

## 8. Cómo probarlo todo

1. Arrancar el **ServidorTcpImc** → pestaña CONEXION, puerto `9007`, botón **INICIAR**
   (estado ONLINE).
2. Arrancar la **PasarelaWebImc**.
3. Local: abrir `http://localhost:8080`, peso `81`, altura `1.7` → IMC `28.03` y
   "Debes bajar un poco de peso". Verificar la traza en **LOG DE CONEXIONES** del servidor.
4. Por consola: `curl "http://localhost:8080/imc?peso=81&altura=1.7"`.
5. Levantar los túneles.
6. **Desde el celular con datos móviles** (no el WiFi de la casa, para demostrar que
   realmente sale a Internet), abrir la URL `https://...ngrok-free.app` y calcular.
7. Desde otro PC, abrir el **ClienteTcpImc**, poner `0.tcp.ngrok.io` y el puerto que dio
   ngrok, **Conectar**, y calcular.
8. Concurrencia: dejar el navegador y el cliente Swing conectados a la vez y calcular en
   ambos; el log del servidor muestra las dos conexiones.
9. Manejo de errores: detener el ServidorTcpImc y recargar la web → responde un error
   controlado (`502`, "No hay conexión con el servidor TCP") en vez de colgarse.

## 9. API de la pasarela

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/` | Página HTML del cliente web |
| GET | `/imc?peso=<float>&altura=<float>` | `200 {"imc":28.027681,"mensaje":"..."}` |
| GET | `/imc` (parámetros inválidos) | `400 {"error":"..."}` |
| GET | `/imc` (servidor TCP caído) | `502 {"error":"..."}` |

---

## Corrección aplicada respecto a la guía

En `CalculoImc.java` la clase interna `Imc` estaba declarada como
`static class Imc` (visibilidad de paquete). Como se usa desde el paquete
`johnarrieta.imc.servidor`, el código **no compilaba**. Se corrigió a
`public static class Imc`.

## Limitaciones conocidas del diseño de la guía

- `detenerServicio()` no interrumpe el `servicio.accept()` bloqueado, por lo que
  el puerto puede quedar ocupado si se reinicia rápidamente.
- El botón LIMPIAR del servidor no tiene listener asignado en la guía.
- La GUI se actualiza desde hilos que no son el EDT (Event Dispatch Thread).
