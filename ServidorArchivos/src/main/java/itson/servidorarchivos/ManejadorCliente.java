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
 * @author asielapodaca
 */
public class ManejadorCliente implements Runnable {
    private static final String DIRECTORIO_BASE = "./archivos/";
    private DatagramPacket paquete;
    private DatagramSocket socket;

    public ManejadorCliente(DatagramPacket paquete, DatagramSocket socket) {
        this.paquete = paquete;
        this.socket = socket;
    }
    
    @Override
    public void run() {
        try {
            String nombreArchivo = new String(paquete.getData(), 0, paquete.getLength()).trim();
            InetAddress direccionCliente = paquete.getAddress();
            int puertoCliente = paquete.getPort();
            
            File archivo = new File(DIRECTORIO_BASE + nombreArchivo); // Buscar el archivo en la carpeta "archivos"
            if(!archivo.exists() || archivo.isDirectory()) {
                enviarMensaje("ERROR: Archivo no encontrado", direccionCliente, puertoCliente);
                return;
            }
            
            try (FileInputStream fis = new FileInputStream(archivo)) {
                byte[] buffer = new byte[ServidorArchivos.TAMANO_BUFFER];
                int bytesLeidos;
                int numPaquete = 0;
                
                while ((bytesLeidos = fis.read(buffer)) != -1) {
                    byte[] datosPaquete = new byte[bytesLeidos + 4];
                    System.arraycopy(intToBytes(numPaquete), 0, datosPaquete, 0, 4);
                    System.arraycopy(buffer, 0, datosPaquete, 4, bytesLeidos);
                    
                    DatagramPacket paqueteEnvio = new DatagramPacket(datosPaquete, datosPaquete.length, direccionCliente, puertoCliente);
                    socket.send(paqueteEnvio);
                    
                    numPaquete++;
                }
            }
            
            enviarMensaje("FIN", direccionCliente, puertoCliente);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void enviarMensaje(String mensaje, InetAddress direccion, int puerto) throws IOException {
        byte[] datos = mensaje.getBytes();
        DatagramPacket paquete = new DatagramPacket(datos, datos.length, direccion, puerto);
        socket.send(paquete);
    }
    
    private byte[] intToBytes(int valor) {
        return new byte[] {
            (byte) (valor >> 24),
            (byte) (valor >> 16),
            (byte) (valor >> 8),
            (byte) valor
        };
    }
}
