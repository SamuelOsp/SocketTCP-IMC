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
├── PasarelaWebImc/src/johnarrieta/imc/web/   (AÑADIDO — anexo opcional)
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

Hasta aquí el sistema solo funciona en `localhost`. El objetivo de esta sección es que el
**ClienteTcpImc corriendo en otro computador, en cualquier parte del mundo**, pueda
escribir una dirección en su campo *DIRECCION IP* y conectarse a este servidor.

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

## 3. Arquitectura resultante

```
  ClienteTcpImc (Swing)          ngrok cloud            este computador
  en otro computador       tcp://N.tcp.ngrok.io:PPPPP
  ┌─────────────────┐            ┌──────────┐          ┌──────────────────┐
  │ DIRECCION IP:   │──TCP──────>│ IP       │═════════>│ ServidorTcp :9007│
  │  N.tcp.ngrok.io │            │ pública  │  túnel   │                  │
  │ PUERTO: PPPPP   │<───────────│          │<═════════│ SubProcesoCliente│
  └─────────────────┘            └──────────┘          │ (un hilo cada uno)│
                                        ▲              └──────────────────┘
                                        ║ conexión SALIENTE
                                        ║ que abre el agente ngrok
                                   (así se esquiva el NAT)
```

El cliente **no sabe** que hay un túnel de por medio: para él, `N.tcp.ngrok.io` es
simplemente la dirección del servidor. Por eso no hubo que tocar ni una línea del código
de la guía — solo se cambian los dos campos de la ventana de conexión.

## 4. Instalación y configuración de ngrok

```bash
brew install ngrok
# Crear cuenta gratuita en https://dashboard.ngrok.com/signup
ngrok config add-authtoken <TU_TOKEN_DEL_DASHBOARD>
```

El authtoken queda guardado en `~/Library/Application Support/ngrok/ngrok.yml`,
**fuera del repositorio**.

> **Los endpoints TCP exigen verificar una tarjeta** en
> https://dashboard.ngrok.com/settings#id-verification. Sin ese paso, el túnel TCP falla
> con `ERR_NGROK_8013`; ngrok indica que la tarjeta no se cobra, es solo antiabuso.

## 5. Levantar el túnel TCP

Con el `ServidorTcpImc` ya en **ONLINE**:

```bash
ngrok start imc-tcp \
  --config "$HOME/Library/Application Support/ngrok/ngrok.yml" \
  --config ./ngrok.yml
```

ngrok imprime la línea que contiene los dos datos que hacen falta:

```
Forwarding    tcp://6.tcp.ngrok.io:27008 -> localhost:9007
```

## 6. Conectar el cliente desde otro computador

En el otro equipo basta con el proyecto del cliente:

```bash
git clone https://github.com/SamuelOsp/SocketTCP-IMC.git
cd SocketTCP-IMC/ClienteTcpImc
javac -d build $(find src -name "*.java")
java -cp build johnarrieta.imc.cliente.Principal
```

En la ventana **CLIENTE IMC**, pestaña CONEXION:

| Campo | Valor |
|---|---|
| DIRECCION IP | `6.tcp.ngrok.io` ← el host que dio ngrok |
| PUERTO DE RED | `27008` ← el puerto que dio ngrok |

**Conectar** → pestaña **CALCULAR IMC** → peso `81`, altura `1.7` → **CALCULAR**.
El resultado (`28.03`) aparece en el cliente y la traza completa en el
**LOG DE CONEXIONES** del servidor.

> ⚠️ El host y el puerto **cambian en cada reinicio de ngrok** — incluso el número del
> subdominio (`0.tcp`, `6.tcp`, …). Hay que leerlos de la pantalla en el momento.

### En la misma red WiFi (sin túnel)

`new ServerSocket(9007)` escucha en **todas** las interfaces, así que dentro de la LAN
no hace falta ngrok: se pone la IP local del servidor (`ipconfig getifaddr en0`, p. ej.
`192.168.1.44`) y el puerto `9007`. Es la misma arquitectura, sin salir a Internet.

## 7. Seguridad

Mientras el túnel esté arriba, **el servicio es público en Internet**: cualquiera con esa
dirección puede conectarse. Hay que cerrar ngrok con `Ctrl-C` al terminar la
demostración, y nunca subir el authtoken al repositorio.

`http://localhost:4040` es el inspector de ngrok, útil para ver el tráfico en vivo.

## 8. Cómo probarlo todo

1. Arrancar el **ServidorTcpImc** → pestaña CONEXION, puerto `9007`, botón **INICIAR**
   (estado ONLINE).
2. Local: arrancar el **ClienteTcpImc** con `localhost` : `9007` y calcular `81` / `1.7`
   → `28.03`. Verificar la traza en LOG DE CONEXIONES.
3. Levantar el túnel TCP y anotar host y puerto.
4. Desde **otro computador**, abrir el ClienteTcpImc, poner el host y puerto de ngrok,
   **Conectar** y calcular.
5. Concurrencia: dejar dos clientes conectados a la vez y calcular en ambos; el log del
   servidor muestra las dos conexiones con IPs distintas, cada una con su hilo.

---

## Anexo: cliente web (opcional)

El `ClienteTcpImc` es Swing y no corre en un navegador; un navegador tampoco puede abrir
un socket TCP crudo. Como extra al ejercicio, `PasarelaWebImc` es un **adaptador de
protocolo** (patrón *gateway*) que permite usar el sistema desde un navegador o un
celular:

- Sirve una página web en `http://localhost:8080` y recibe `GET /imc?peso=81&altura=1.7`.
- Por dentro abre `new Socket("localhost", 9007)` y habla **el mismo protocolo binario**
  que el cliente Swing.
- Devuelve `{"imc":28.027681,"mensaje":"Debes bajar un poco de peso"}`.

Para el `ServidorTcpImc` es simplemente otro cliente TCP más, atendido por su propio
`SubProcesoCliente`; **el servidor no se modificó en absoluto**. Usa
`com.sun.net.httpserver.HttpServer`, incluido en el JDK: cero dependencias externas.

```bash
cd PasarelaWebImc
javac -d build $(find src -name "*.java")
java -cp build johnarrieta.imc.web.PasarelaHttp
# opcional: ... PasarelaHttp <puertoWeb> <hostTcp> <puertoTcp>
```

Para exponerla a Internet se usa el otro túnel, este sí HTTP:
`ngrok start imc-web --config ... --config ./ngrok.yml`.
Con `ngrok start --all` se levantan ambos túneles a la vez.

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
