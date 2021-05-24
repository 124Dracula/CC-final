import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class UDPListener implements Runnable {
    private  Vector<DatagramPacket> pacotesRecebidos;
    private DatagramSocket servidor;
    private int TAMANHO_MAX = 512;
    private ReentrantLock lock;


    public UDPListener(Vector<DatagramPacket> pacotesRecebidos, DatagramSocket servidor, ReentrantLock l) {
        this.pacotesRecebidos = pacotesRecebidos;
        this.servidor = servidor;
        this.lock = l;
    }
    public void run() {
        while (true) {
            byte[] buffer = new byte[TAMANHO_MAX +16];
            DatagramPacket pacoteRecebido = new DatagramPacket(buffer, buffer.length);
            try {
                servidor.receive(pacoteRecebido);
                lock.lock();
                pacotesRecebidos.add(pacoteRecebido);
                lock.unlock();
                System.out.println("Recebi Pacote");
            } catch (IOException e) {
                System.out.println("Exceção encontrada");
            }
        }
    }
}
