package itson.clientearchivos;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.util.Scanner;

public class ClienteArchivos {
    private static final String SERVIDOR = "localhost";
    private static final int PUERTO_SERVIDOR = 5000;
    private static final int TAMANO_BUFFER = 1024;
    private static final int TIEMPO_ESPERA = 2000; // 2 segundos
    private static final int MAX_INTENTOS = 5;
    
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Ingrese el nombre del archivo a solicitar: ");
            String nombreArchivo = scanner.nextLine();
            
            TransferenciaProxy proxy = new TransferenciaProxy(socket, SERVIDOR, PUERTO_SERVIDOR);
            byte[] fileData = proxy.solicitarArchivo(nombreArchivo);
            
            if (fileData != null) {
                try (FileOutputStream fos = new FileOutputStream("archivo_recibido_" + nombreArchivo)) {
                    fos.write(fileData);
                    System.out.println("Archivo recibido correctamente y guardado como 'archivo_recibido_" + nombreArchivo + "'.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}