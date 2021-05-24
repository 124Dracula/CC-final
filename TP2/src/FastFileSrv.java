import java.io.IOException;
import java.net.*;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class FastFileSrv {;
    private static Vector<DatagramPacket> queue = new Vector<>();
    private static int TAMANHO_MAX = 512;
    private static ReentrantLock lock = new ReentrantLock();

    public static void main(String[] args) throws IOException {
        DatagramSocket server = new DatagramSocket();
        iniciaConexao(server, args[0], args[1]);

        Thread t1 = new Thread(new UDPListener(queue, server, lock));
        t1.start();
        Thread t2 = new Thread(new FastFileSrvAux(server,queue,lock));
        t2.start();
    }

    public static void iniciaConexao(DatagramSocket server, String arg1, String arg2) throws IOException{

        InetAddress serverAddress = InetAddress.getByName(arg1);
        int porta = Integer.parseInt(arg2);
        server.connect(serverAddress,porta);
        while(true) {
            server.setSoTimeout(1000);
            PDU pdu = new PDU(0,5, 1, 1, "cc20/21".getBytes());
            byte[] buffer = pdu.toByte();
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, serverAddress, porta);
            server.send(pacote);
            System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());



            buffer = new byte[TAMANHO_MAX + 16];
            DatagramPacket pacoteRecebido = new DatagramPacket(buffer, buffer.length);

            try {
                server.receive(pacoteRecebido);
                byte[] a = new byte[pacoteRecebido.getLength()];
                System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                PDU pduRecebido = new PDU(a);
                System.out.println("Recebeu: " + pduRecebido.getPedido() + " " + pduRecebido.getTipo() + " " + pduRecebido.getChunk() + " " + pduRecebido.getnChunks());
                if (pduRecebido.getPedido() == 0 && (pduRecebido.getTipo() == 1 || pduRecebido.getTipo() == 2)) {
                    server.setSoTimeout(0);
                    Runtime.getRuntime().addShutdownHook(new Thread (new FFSShutdown(server,serverAddress,porta)));
                    break;
                }
            }catch (SocketTimeoutException e) {
                System.out.println("Não recebeu resposta do Gateway");
            }
        }
    }
}
