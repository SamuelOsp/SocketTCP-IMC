package johnarrieta.imc.web;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Cliente TCP que habla el mismo protocolo binario de la guia contra el
 * ServidorTcpImc. Es el adaptador entre el mundo HTTP y el mundo socket.
 *
 * Protocolo (identico al ClienteTcpImc de Swing):
 *   Pasarela -> Servidor : writeFloat(peso), writeFloat(altura)
 *   Servidor -> Pasarela : readFloat(imc),   readUTF(mensaje)
 *
 * @author Samuel David Ospina De Avila
 */
public class ClienteTcpGateway {

    private final String host;
    private final int puerto;

    public ClienteTcpGateway(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
    }

    /** Resultado devuelto por el servidor TCP. */
    public static class Resultado {

        public final float imc;
        public final String mensaje;

        Resultado(float imc, String mensaje) {
            this.imc = imc;
            this.mensaje = mensaje;
        }
    }

    /**
     * Abre una conexion nueva por cada peticion HTTP, envia peso y altura,
     * lee la respuesta y cierra el socket.
     */
    public Resultado calcular(float peso, float altura) throws IOException {
        try (Socket servidor = new Socket()) {
            servidor.connect(new InetSocketAddress(host, puerto), 5000);
            servidor.setSoTimeout(5000);

            DataOutputStream out = new DataOutputStream(servidor.getOutputStream());
            out.writeFloat(peso);
            out.writeFloat(altura);
            out.flush();

            DataInputStream in = new DataInputStream(servidor.getInputStream());
            float imc = in.readFloat();
            String mensaje = in.readUTF();
            return new Resultado(imc, mensaje);
        }
    }

    public String getHost() {
        return host;
    }

    public int getPuerto() {
        return puerto;
    }
}
