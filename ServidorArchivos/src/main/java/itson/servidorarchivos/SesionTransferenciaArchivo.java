package itson.servidorarchivos;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Clase que representa una sesion de transferencia de archivo,
 * manteniendo el estado y los datos de los paquetes para posibles retransmisiones.
 * @author asielapodaca
 */
public class SesionTransferenciaArchivo {
    private final File archivo;
    private final int totalPaquetes;
    private final Map<Integer, byte[]> dataEmpaquetada;
    public final long tiempoCreacion;
    
    public SesionTransferenciaArchivo(File archivo, int totalPaquetes) {
        this.archivo = archivo;
        this.totalPaquetes = totalPaquetes;
        this.dataEmpaquetada = new HashMap<>();
        this.tiempoCreacion = System.currentTimeMillis();
    }
    
    public void addPaquete(int idPaquete, byte[] data) {
        dataEmpaquetada.put(idPaquete, data);
    }
    
    public byte[] getPaquete(int idPaquete) {
        return dataEmpaquetada.get(idPaquete);
    }
    
    public File getArchivo() {
        return archivo;
    }
    
    public int getTotalPaquetes() {
        return totalPaquetes;
    }
    
    public long getTiempoCreacion() {
        return tiempoCreacion;
    }
    
}
