import java.net.*;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;


public class HTTPGW {
    private static HashMap<InetAddress, Integer> ffs;
    private static int TAMANHO_MAX = 512;
    private static Vector<DatagramPacket> pacotesRecebidos;
    private static ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws Exception {
        ffs = new HashMap<>();
        System.out.println("Servidor inciado");
        ServerSocket socket = new ServerSocket(8080);
        DatagramSocket socketServidor = new DatagramSocket(8088);
        Incrementar inc = new Incrementar();
        pacotesRecebidos = new Vector<>();

        Thread t1 = new Thread(new UDPListener(pacotesRecebidos,socketServidor,lock));
        t1.start();

        Thread t2 = new Thread(new AceitarFFS(pacotesRecebidos,socketServidor,ffs,lock));
        t2.start();

        while (true) {
               Socket c = socket.accept();
               System.out.println("Cliente aceite");
               Thread t3 = new Thread(new HTTPGWThread(c,socketServidor,ffs,inc,pacotesRecebidos,lock));
               t3.start();
        }
    }
}
