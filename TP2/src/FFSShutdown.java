import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class FFSShutdown implements Runnable{
    DatagramSocket server;
    InetAddress serverAddress;
    int porta;
    public FFSShutdown (DatagramSocket s,InetAddress sa, int p) {
        server = s;
        serverAddress = sa;
        porta = p;
    }

    public void run() {
        PDU pdu = new PDU(0,6, 1, 1, "cc20/21".getBytes());
        byte[] buffer = new byte[0];
        try {
            buffer = pdu.toByte();
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, serverAddress, porta);
            server.send(pacote);
            System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
        }
        catch (IOException e) {
            //System.out.println("Exceção Encontrada");
        }
    }
}
