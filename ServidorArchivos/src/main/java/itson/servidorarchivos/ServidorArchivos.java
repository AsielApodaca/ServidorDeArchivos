/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package itson.servidorarchivos;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * @author asielapodaca
 */
public class ServidorArchivos {
    private static final int PUERTO = 5000;
    public static final int TAMANO_BUFFER = 1024;
    private static final ExecutorService ejecutor = Executors.newFixedThreadPool(5);

    public static void main(String[] args) {
        try(DatagramSocket socket = new DatagramSocket(PUERTO)) {
            System.out.println("Servidor de archivos UDP escuchando en el puerto " + PUERTO);
            
            while(true) {
                byte[] buffer = new byte[TAMANO_BUFFER];
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                
                ejecutor.execute(new ManejadorCliente(paquete, socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
