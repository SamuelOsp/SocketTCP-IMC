# Sistema Distribuido Cliente-Servidor con Sockets TCP — Cálculo de IMC

Autor: **Samuel David Ospina De Avila**
Basado en la guía del docente John Carlos Arrieta Arrieta.

## Estructura

```
GuiaSocketTCP-IMC/
├── ServidorTcpImc/src/johnarrieta/imc/
│   ├── Principal.java                  (main del servidor)
│   ├── modelo/CalculoImc.java          (lógica del IMC)
│   ├── servidor/ServidorTcp.java       (ServerSocket, acepta clientes)
│   ├── servidor/SubProcesoCliente.java (un hilo por cliente)
│   └── vistas/VentanaPrincipal.java    (GUI Swing del servidor)
└── ClienteTcpImc/src/johnarrieta/imc/cliente/
    ├── Principal.java                       (main del cliente)
    └── vistas/VentanaPrincipal.java         (GUI Swing del cliente)
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
