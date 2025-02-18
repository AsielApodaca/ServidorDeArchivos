/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package itson.servidorarchivos;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 *
 * 
 * Esta clase se encarga de gestionar la solicitud de archivos por parte de un cliente en un servidor que
 * utiliza el protocolo UDP para transferir los archivos. El archivo solicitado se busca en la carpeta 
 * "archivos" y se envía al cliente en paquetes. En caso de que el archivo no se encuentre, se envía un 
 * mensaje de error.
 */
public class ManejadorCliente implements Runnable {
    private static final String DIRECTORIO_BASE = "./archivos/";  // Carpeta donde se buscan los archivos
    private DatagramPacket paquete;  // Paquete recibido del cliente
    private DatagramSocket socket;   // Socket utilizado para la comunicación

    /**
     * Constructor que inicializa el manejador con el paquete recibido y el socket de comunicación.
     *
     * @param paquete El paquete recibido del cliente que contiene la solicitud de archivo.
     * @param socket El socket de comunicación para enviar los paquetes al cliente.
     */
    public ManejadorCliente(DatagramPacket paquete, DatagramSocket socket) {
        this.paquete = paquete;
        this.socket = socket;
    }
    
    /**
     * Método que se ejecuta cuando se inicia el hilo. Maneja la recepción de la solicitud de archivo,
     * la búsqueda del archivo y su envío al cliente en paquetes.
     */
    @Override
    public void run() {
        try {
            // Extraer el nombre del archivo solicitado por el cliente
            String nombreArchivo = new String(paquete.getData(), 0, paquete.getLength()).trim();
            InetAddress direccionCliente = paquete.getAddress();
            int puertoCliente = paquete.getPort();
            
            // Buscar el archivo en la carpeta "archivos"
            File archivo = new File(DIRECTORIO_BASE + nombreArchivo); 
            if (!archivo.exists() || archivo.isDirectory()) {
                // Si el archivo no existe o es un directorio, enviar mensaje de error
                enviarMensaje("ERROR: Archivo no encontrado", direccionCliente, puertoCliente);
                return;
            }
            
            // Abrir el archivo y enviar su contenido en paquetes
            try (FileInputStream fis = new FileInputStream(archivo)) {
                byte[] buffer = new byte[ServidorArchivos.TAMANO_BUFFER];
                int bytesLeidos;
                int numPaquete = 0;
                
                // Enviar el archivo en paquetes
                while ((bytesLeidos = fis.read(buffer)) != -1) {
                    byte[] datosPaquete = new byte[bytesLeidos + 4];
                    // Incluir el número de paquete en los primeros 4 bytes
                    System.arraycopy(intToBytes(numPaquete), 0, datosPaquete, 0, 4);
                    // Copiar los datos del archivo al paquete
                    System.arraycopy(buffer, 0, datosPaquete, 4, bytesLeidos);
                    
                    // Enviar el paquete al cliente
                    DatagramPacket paqueteEnvio = new DatagramPacket(datosPaquete, datosPaquete.length, direccionCliente, puertoCliente);
                    socket.send(paqueteEnvio);
                    
                    numPaquete++;  // Incrementar el número de paquete
                }
            }
            
            // Enviar mensaje de fin cuando se haya enviado todo el archivo
            enviarMensaje("FIN", direccionCliente, puertoCliente);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Método para enviar un mensaje a un cliente.
     *
     * @param mensaje El mensaje a enviar.
     * @param direccion La dirección del cliente.
     * @param puerto El puerto del cliente.
     * @throws IOException Si ocurre un error al enviar el mensaje.
     */
    private void enviarMensaje(String mensaje, InetAddress direccion, int puerto) throws IOException {
        byte[] datos = mensaje.getBytes();
        DatagramPacket paquete = new DatagramPacket(datos, datos.length, direccion, puerto);
        socket.send(paquete);
    }
    
    /**
     * Convierte un valor entero en un arreglo de bytes (Big Endian).
     *
     * @param valor El valor entero a convertir.
     * @return El arreglo de bytes correspondiente al valor entero.
     */
    private byte[] intToBytes(int valor) {
        return new byte[] {
            (byte) (valor >> 24),
            (byte) (valor >> 16),
            (byte) (valor >> 8),
            (byte) valor
        };
    }
}
