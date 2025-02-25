/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package itson.clientearchivos;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementa el patrón Proxy para la transferencia de archivos. Oculta la
 * complejidad de la comunicación UDP y manejo de paquetes perdidos.
 *
 * @author asielapodaca
 */
public class TransferenciaProxy {

    private final DatagramSocket socket;
    private final String servidorHost;
    private final int servidorPuerto;
    private static final int TAMANO_BUFFER = 1024;
    private static final int TIEMPO_ESPERA = 2000; // 2 segundos
    private static final int MAX_INTENTOS = 5;

    public TransferenciaProxy(DatagramSocket socket, String servidorHost, int servidorPuerto) {
        this.socket = socket;
        this.servidorHost = servidorHost;
        this.servidorPuerto = servidorPuerto;
    }

    /**
     * Solicita un archivo al servidor y maneja la recepción completa incluyendo
     * la solicitud de retransmisión de paquetes perdidos.
     */
    public byte[] solicitarArchivo(String nombreArchivo) throws IOException {
        InetAddress direccionServidor = InetAddress.getByName(servidorHost);

        // Enviar solicitud inicial y reintentar si es necesario
        boolean solicitudEnviada = false;
        int intentos = 0;

        while (!solicitudEnviada && intentos < MAX_INTENTOS) {
            try {
                // Enviar solicitud
                byte[] datosSolicitud = nombreArchivo.getBytes();
                DatagramPacket solicitud = new DatagramPacket(
                        datosSolicitud,
                        datosSolicitud.length,
                        direccionServidor,
                        servidorPuerto
                );
                socket.send(solicitud);
                socket.setSoTimeout(TIEMPO_ESPERA);

                // Esperar metadatos del archivo (total de paquetes y tamaño)
                byte[] metadataBuffer = new byte[8]; // 4 bytes para totalPaquetes + 4 bytes para tamaño
                DatagramPacket metadataPacket = new DatagramPacket(metadataBuffer, metadataBuffer.length);
                socket.receive(metadataPacket);

                ByteBuffer metadataByteBuffer = ByteBuffer.wrap(metadataPacket.getData());
                int totalPaquetes = metadataByteBuffer.getInt();
                int tamanoArchivo = metadataByteBuffer.getInt();

                System.out.println("Archivo encontrado. Total de paquetes: " + totalPaquetes);
                System.out.println("Tamaño del archivo: " + tamanoArchivo + " bytes");

                // Iniciar recepción de paquetes
                byte[] archivoCompleto = recibirPaquetes(totalPaquetes, tamanoArchivo);

                // Notificar al servidor que la transferencia está completa
                enviarConfirmacion();

                return archivoCompleto;
            } catch (SocketTimeoutException e) {
                System.out.println("Tiempo de espera agotado. Reintentando solicitud... (" + (intentos + 1) + "/" + MAX_INTENTOS + ")");
                intentos++;
            }
        }

        if (intentos >= MAX_INTENTOS) {
            System.out.println("No se pudo establecer conexión con el servidor después de " + MAX_INTENTOS + " intentos.");
        }

        return null;
    }

    private byte[] recibirPaquetes(int totalPaquetes, int tamanoArchivo) throws IOException {
        TreeMap<Integer, byte[]> paquetesRecibidos = new TreeMap<>();
        boolean recepcionCompleta = false;
        int tiempoEsperaActual = TIEMPO_ESPERA;

        // Configurar tiempo de espera inicial
        socket.setSoTimeout(tiempoEsperaActual);

        System.out.println("Recibiendo paquetes...");

        while (!recepcionCompleta) {
            try {
                // Recibir paquetes hasta que se agote el tiempo de espera
                recibirPaquetesPendientes(paquetesRecibidos, totalPaquetes);

                // Verificar si se han recibido todos los paquetes
                if (paquetesRecibidos.size() == totalPaquetes) {
                    recepcionCompleta = true;
                    System.out.println("Todos los paquetes recibidos correctamente.");
                } else {
                    // Solicitar paquetes faltantes
                    Set<Integer> paquetesFaltantes = IntStream.range(0, totalPaquetes)
                            .boxed()
                            .filter(i -> !paquetesRecibidos.containsKey(i))
                            .collect(Collectors.toSet());

                    System.out.println("Paquetes faltantes: " + paquetesFaltantes.size() + " de " + totalPaquetes);

                    if (!paquetesFaltantes.isEmpty()) {
                        solicitarPaquetesFaltantes(paquetesFaltantes);

                        // Incrementar tiempo de espera para dar más tiempo a los paquetes rezagados
                        tiempoEsperaActual = Math.min(tiempoEsperaActual * 2, 8000); // Máximo 8 segundos
                        socket.setSoTimeout(tiempoEsperaActual);
                    }
                }
            } catch (SocketTimeoutException e) {
                // Verificar si tenemos suficientes paquetes para terminar
                if (paquetesRecibidos.size() > totalPaquetes * 0.98) { // Si tenemos más del 98%
                    System.out.println("Tiempo de espera agotado, pero tenemos suficientes paquetes para reconstruir el archivo.");
                    recepcionCompleta = true;
                } else if (paquetesRecibidos.isEmpty()) {
                    System.out.println("No se recibió ningún paquete. Abortando.");
                    return null;
                } else {
                    // Solicitar paquetes faltantes nuevamente
                    Set<Integer> paquetesFaltantes = IntStream.range(0, totalPaquetes)
                            .boxed()
                            .filter(i -> !paquetesRecibidos.containsKey(i))
                            .collect(Collectors.toSet());

                    System.out.println("Tiempo de espera agotado. Solicitando " + paquetesFaltantes.size() + " paquetes faltantes.");
                    solicitarPaquetesFaltantes(paquetesFaltantes);

                    // Incrementar tiempo de espera
                    tiempoEsperaActual = Math.min(tiempoEsperaActual * 2, 8000);
                    socket.setSoTimeout(tiempoEsperaActual);
                }
            }
        }

        // Construir el archivo completo a partir de los paquetes recibidos
        return construirArchivo(paquetesRecibidos, tamanoArchivo);
    }

    private void recibirPaquetesPendientes(TreeMap<Integer, byte[]> paquetesRecibidos, int totalPaquetes) throws IOException {
        while (true) {
            byte[] buffer = new byte[TAMANO_BUFFER + 8]; // 4 bytes para numPaquete + 4 bytes para totalPaquetes + datos
            DatagramPacket paqueteRecibido = new DatagramPacket(buffer, buffer.length);

            socket.receive(paqueteRecibido);

            byte[] datos = paqueteRecibido.getData();
            int length = paqueteRecibido.getLength();

            // Comprobar si es un mensaje especial
            String mensaje = new String(datos, 0, Math.min(length, 50)).trim();
            if (mensaje.equals("ERROR: Archivo no encontrado")) {
                System.out.println("El archivo solicitado no existe en el servidor.");
                throw new IOException("Archivo no encontrado");
            }

            if (mensaje.equals("FIN")) {
                System.out.println("Servidor indica fin de transmisión.");
                return;
            }

            // Extraer número de paquete y datos
            ByteBuffer byteBuffer = ByteBuffer.wrap(datos);
            int numPaquete = byteBuffer.getInt();
            int totalPaquetesRecibido = byteBuffer.getInt(); // no usado, pero necesario para avanzar en el buffer

            // Datos del paquete (saltando los 8 bytes del encabezado)
            byte[] datosPaquete = Arrays.copyOfRange(datos, 8, length);

            // Almacenar el paquete
            paquetesRecibidos.put(numPaquete, datosPaquete);

            // Mostrar progreso
            if (paquetesRecibidos.size() % 10 == 0 || paquetesRecibidos.size() == totalPaquetes) {
                System.out.printf("Progreso: %d/%d paquetes recibidos (%.1f%%)\n",
                        paquetesRecibidos.size(), totalPaquetes,
                        ((double) paquetesRecibidos.size() / totalPaquetes) * 100);
            }
        }
    }

    private void solicitarPaquetesFaltantes(Set<Integer> paquetesFaltantes) throws IOException {
        // Limitar la cantidad de paquetes solicitados en cada solicitud para no exceder el tamaño máximo de un datagrama
        int maxPaquetesPorSolicitud = 50;

        // Dividir en lotes si hay muchos paquetes faltantes
        for (int i = 0; i < paquetesFaltantes.size(); i += maxPaquetesPorSolicitud) {
            Set<Integer> lote = paquetesFaltantes.stream()
                    .skip(i)
                    .limit(maxPaquetesPorSolicitud)
                    .collect(Collectors.toSet());

            // Formato: "RESEND:1,2,3,4,5"
            String solicitud = "RESEND:" + lote.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            byte[] datosSolicitud = solicitud.getBytes();
            DatagramPacket paqueteSolicitud = new DatagramPacket(
                    datosSolicitud,
                    datosSolicitud.length,
                    InetAddress.getByName(servidorHost),
                    servidorPuerto
            );
            socket.send(paqueteSolicitud);

            // Pequeña pausa para no saturar
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private byte[] construirArchivo(TreeMap<Integer, byte[]> paquetesRecibidos, int tamanoArchivo) {
        // Calcular el tamaño real basado en los paquetes recibidos
        int tamanoReal = paquetesRecibidos.values().stream()
                .mapToInt(bytes -> bytes.length)
                .sum();

        // Usar el menor de los dos tamaños para evitar exceder el tamaño real del archivo
        int tamanoFinal = Math.min(tamanoReal, tamanoArchivo);

        // Crear buffer para el archivo completo
        ByteBuffer archivoBuffer = ByteBuffer.allocate(tamanoFinal);

        // Agregar cada paquete en orden
        for (byte[] datosPaquete : paquetesRecibidos.values()) {
            // Si agregar este paquete excedería el tamaño del archivo, solo agregar lo que queda
            if (archivoBuffer.position() + datosPaquete.length > tamanoFinal) {
                int bytesRestantes = tamanoFinal - archivoBuffer.position();
                archivoBuffer.put(datosPaquete, 0, bytesRestantes);
                break;
            } else {
                archivoBuffer.put(datosPaquete);
            }
        }

        return archivoBuffer.array();
    }

    private void enviarConfirmacion() throws IOException {
        byte[] datosConfirmacion = "COMPLETE".getBytes();
        DatagramPacket paqueteConfirmacion = new DatagramPacket(
                datosConfirmacion,
                datosConfirmacion.length,
                InetAddress.getByName(servidorHost),
                servidorPuerto
        );
        socket.send(paqueteConfirmacion);
    }
}
