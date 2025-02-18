/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package itson.clientearchivos;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;

/**
 *
 * @author asielapodaca
 * 
 * Este cliente permite solicitar archivos desde un servidor utilizando el protocolo UDP.
 * El cliente envía el nombre del archivo solicitado, recibe los paquetes del archivo,
 * y los guarda en el disco como un archivo "archivo_recibido".
 */
public class ClienteArchivos {
    private static final String SERVIDOR = "localhost";  // Dirección del servidor
    private static final int PUERTO_SERVIDOR = 5000;    // Puerto del servidor
    private static final int TAMANO_BUFFER = 1024;      // Tamaño del buffer para recibir datos
    private static final int TIEMPO_ESPERA = 3000;      // Tiempo de espera para la recepción de paquetes (en milisegundos)
    
    /**
     * Método principal que ejecuta el cliente.
     * Solicita un archivo al servidor, recibe los paquetes y guarda el archivo en el sistema.
     */
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {  // Crear un socket UDP para la comunicación
            Scanner scanner = new Scanner(System.in);
            System.out.println("Ingrese el nombre del archivo a solicitar: ");
            String nombreArchivo = scanner.nextLine();  // Leer el nombre del archivo que desea solicitar
            
            // Enviar la solicitud al servidor con el nombre del archivo
            byte[] datosSolicitud = nombreArchivo.getBytes();
            DatagramPacket solicitud = new DatagramPacket(datosSolicitud, datosSolicitud.length, InetAddress.getByName(SERVIDOR), PUERTO_SERVIDOR);
            socket.send(solicitud);
            
            System.out.println("Esperando respuesta del servidor...");
            
            Map<Integer, byte[]> paquetesRecibidos = new TreeMap<>();
            boolean recepcionCompleta = false;
            
            // Recibir los paquetes hasta que el archivo se reciba completo o se agote el tiempo de espera
            while(!recepcionCompleta) {
                byte[] buffer = new byte[TAMANO_BUFFER + 4];  // El tamaño del buffer incluye 4 bytes para el número de paquete
                DatagramPacket paqueteRecibido = new DatagramPacket(buffer, buffer.length);
                socket.setSoTimeout(TIEMPO_ESPERA);  // Establecer el tiempo de espera para la recepción de paquetes
                
                try {
                    socket.receive(paqueteRecibido);  // Recibir el paquete
                    byte[] datos = paqueteRecibido.getData();
                    String mensaje = new String(datos, 0 ,paqueteRecibido.getLength()).trim();
                    
                    // Si el servidor responde con un error, mostrar el mensaje
                    if(mensaje.equals("ERROR: Archivo no encontrado")) {
                        System.out.println("El archivo solicitado no existe en el servidor.");
                        return;
                    }
                    
                    // Si el servidor envía "FIN", significa que el archivo ha sido completamente enviado
                    if(mensaje.equals("FIN")) {
                        recepcionCompleta = true;
                        break;
                    }
                    
                    // Extraer el número de paquete y los datos del paquete
                    int numPaquete = bytesToInt(datos);
                    byte[] datosPaquete = Arrays.copyOfRange(datos, 4, paqueteRecibido.getLength());
                    
                    // Almacenar los paquetes recibidos en un mapa para ordenarlos por número de paquete
                    paquetesRecibidos.put(numPaquete, datosPaquete);
                } catch (SocketTimeoutException e) {
                    System.out.println("Tiempo de espera agotado. No se recibieron más paquetes.");
                    break;
                }
            }
            
            // Si se recibieron paquetes, guardar el archivo en el disco
            if(!paquetesRecibidos.isEmpty()) {
                try (FileOutputStream fos = new FileOutputStream("archivo_recibido")) {
                    for(byte[] datos : paquetesRecibidos.values()) {
                        fos.write(datos);  // Escribir los datos de cada paquete en el archivo
                    }
                    System.out.println("Archivo recibido correctamente y guardado como 'archivo_recibido'.");
                }
            } else {
                System.out.println("No se recibieron paquetes válidos.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Convierte un arreglo de bytes (4 bytes) en un valor entero.
     *
     * @param bytes El arreglo de bytes que contiene el número de paquete.
     * @return El valor entero que representa el número de paquete.
     */
    private static int bytesToInt(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }
}
