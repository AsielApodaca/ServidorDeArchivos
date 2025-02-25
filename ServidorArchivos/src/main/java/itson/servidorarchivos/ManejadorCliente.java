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
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

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
    private static final int MAX_INTENTOS = 5;
    private static final int TIEMPO_ESPERA_RESPUESTA = 1000; // 1 segundo
    
    // Almacén de solicitudes activas para manejar peticiones de paquetes perdidos
    private static final Map<String, SesionTransferenciaArchivo> sesionesActivas = new HashMap<>();

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
            String mensaje = new String(paquete.getData(), 0, paquete.getLength()).trim();
            InetAddress direccionCliente = paquete.getAddress();
            int puertoCliente = paquete.getPort();
            
            // Identificador unico para la sesión del cliente
            String idSesion = direccionCliente.getHostAddress() + ":" + puertoCliente;
            
            // Si es una solicitud de retransmisión de pauqetes
            if(mensaje.startsWith("REENVIAR:")) {
                manejarSolicitudReenvio(mensaje, direccionCliente, puertoCliente, idSesion);
                return;
            }
            
            if(mensaje.equals("COMPLETADO")) {
                sesionesActivas.remove(idSesion);
                System.out.println("Transferencia completada para cliente " + idSesion);
                return;
            }
            
            // Solicitud de nuevo archivo
            File archivo = new File(DIRECTORIO_BASE + mensaje);
            if (!archivo.exists() || archivo.isDirectory()) {
                enviarMensaje("ERROR: Archivo no encontrado", direccionCliente, puertoCliente);
                return;
            }
            
            long tamanoArchivo = archivo.length();
            int totalPaquetes = (int) Math.ceil((double) tamanoArchivo / ServidorArchivos.TAMANO_BUFFER);
            
            // Enviar información del archivo total
            ByteBuffer metadataBuffer = ByteBuffer.allocate(8);
            metadataBuffer.putInt(totalPaquetes);
            metadataBuffer.putInt((int)tamanoArchivo);
            
            DatagramPacket paqueteMetadata = new DatagramPacket(
                    metadataBuffer.array(),
                    metadataBuffer.array().length,
                    direccionCliente,
                    puertoCliente
            );
            socket.send(paqueteMetadata);
            
            // Crear sesión de transferencia
            SesionTransferenciaArchivo sesion = new SesionTransferenciaArchivo(archivo, totalPaquetes);
            sesionesActivas.put(idSesion, sesion);
            
            //Enviar todos los paquetes
            try(FileInputStream fis = new FileInputStream(archivo)) {
                byte[] buffer = new byte[ServidorArchivos.TAMANO_BUFFER];
                int bytesLeidos;
                int numPaquete = 0;
                
                while ((bytesLeidos = fis.read(buffer)) != -1) {
                    // Almacenar el paquete en el sesion
                    byte[] dataEmpaquetada = new byte[bytesLeidos];
                    System.arraycopy(buffer, 0, dataEmpaquetada, 0, bytesLeidos);
                    sesion.addPaquete(numPaquete, dataEmpaquetada);
                    
                    // Preparar paquete: [número de paquete(4 bytes)][total de paquetes (4bytes)][datos]
                    byte[] datosPaquete = new byte[bytesLeidos + 8];
                    System.arraycopy(intToBytes(numPaquete), 0, datosPaquete, 0, 4);
                    System.arraycopy(intToBytes(totalPaquetes), 0, datosPaquete, 4, 4);
                    System.arraycopy(buffer, 0, datosPaquete, 8, bytesLeidos);
                    
                    DatagramPacket paqueteEnvio = new DatagramPacket(
                            datosPaquete,
                            datosPaquete.length,
                            direccionCliente,
                            puertoCliente
                    );
                    socket.send(paqueteEnvio);
                    
                    numPaquete++;
                    
                    // Pequeña pausa para no saturar la red
                    Thread.sleep(5);
                }
            }
            
            // Enviar mensaje de fin para indicar que se han enviado todos los paquetes
            enviarMensaje("FIN", direccionCliente, puertoCliente);
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void manejarSolicitudReenvio(String mensaje, InetAddress direccionCliente, int puertoCliente, String idSesion) throws IOException {
        // Formato: "REENVIAR:1,2,3,4,5" (números de paquetes faltantes)
        String[] partes = mensaje.split(":");
        if (partes.length != 2) return;
        
        String[] idsPaquetes = partes[1].split(",");
        SesionTransferenciaArchivo sesion = sesionesActivas.get(idSesion);
        
        if (sesion == null) {
            enviarMensaje("ERROR: Sesión no encontrada", direccionCliente, puertoCliente);
            return;
        }
        
        int totalPaquetes = sesion.getTotalPaquetes();
        
        // Reenviar los paquetes solicitados
        for (String stringIdPaquete : idsPaquetes) {
            try {
                int idPaquete = Integer.parseInt(stringIdPaquete.trim());
                byte[] dataEmpaquetada = sesion.getPaquete(idPaquete);
                
                if (dataEmpaquetada != null) {
                    // Preparar paquete: [número de paquete (4 bytes)][total de paquetes (4bytes)][datos]
                    byte[] datosPaquete = new byte[dataEmpaquetada.length + 8];
                    System.arraycopy(intToBytes(idPaquete), 0, datosPaquete, 0, 4);
                    System.arraycopy(intToBytes(totalPaquetes), 0, datosPaquete, 4, 4);
                    System.arraycopy(dataEmpaquetada, 0, datosPaquete, 8, dataEmpaquetada.length);
                    
                    DatagramPacket paqueteEnvio = new DatagramPacket(
                            datosPaquete,
                            datosPaquete.length,
                            direccionCliente,
                            puertoCliente
                    );
                    socket.send(paqueteEnvio);
                    
                    // Pequeña pausa para no saturar la red
                    Thread.sleep(5);
                }
            } catch (NumberFormatException | InterruptedException e) {
                // Ignorar paquetes con formato incorrecto
            }
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
     * Convierte un valor entero en un arreglo de bytes.
     *
     * @param valor El valor entero a convertir.
     * @return El arreglo de bytes correspondiente al valor entero.
     */
    private byte[] intToBytes(int valor) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(valor);
        return buffer.array();
    }
}
