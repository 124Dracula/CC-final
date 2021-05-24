import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class AceitarFFS implements Runnable{
     private static Vector<DatagramPacket> listaPacotes;
     private DatagramSocket servidor;
     private static HashMap<InetAddress, Integer> ffs;
     private int TAMANHO_MAX = 512;
     private ReentrantLock lock;


    public AceitarFFS (Vector<DatagramPacket> l,DatagramSocket s,HashMap<InetAddress, Integer> f, ReentrantLock lo) {
        listaPacotes = l;
        servidor = s;
        ffs = f;
        lock = lo;
    }

    public void run() {
        while (true) {
            lock.lock();
            if (!listaPacotes.isEmpty()) {
                DatagramPacket pacoteRecebido = listaPacotes.get(0);
                try {
                    byte[] a = new byte[pacoteRecebido.getLength()];
                    System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                    PDU pduRecebido = new PDU(a);
                    listaPacotes.remove(0);
                    if (pduRecebido.getTipo() == 5) {
                        String password = new String(pduRecebido.getData(),0,pduRecebido.getData().length);
                        byte[]buffer;
                        if (password.equals("cc20/21")) {
                            String resposta = "";
                            ffs.put(pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                            PDU pduResposta = new PDU(0,1, 1, 1, resposta.getBytes());
                            buffer = new byte[TAMANHO_MAX + 16];
                            buffer = pduResposta.toByte();
                            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                            servidor.send(pacote);
                            System.out.println("Enviou: " + pduResposta.getPedido() + " " + pduResposta.getTipo() + " " + pduResposta.getChunk() + " " + pduResposta.getnChunks());
                            System.out.println("Conexão com FFS de endereço " + pacoteRecebido.getAddress() + " Tem agora " + ffs.size() + " FFS.");
                        }
                        else {
                            String resposta = "";
                            PDU pduResposta = new PDU(0,2, 1, 1, resposta.getBytes());
                            buffer = new byte[TAMANHO_MAX + 16];
                            buffer = pduResposta.toByte();
                            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                            servidor.send(pacote);
                            System.out.println("Enviou: " + pduResposta.getPedido() + " " + pduResposta.getTipo() + " " + pduResposta.getChunk() + " " + pduResposta.getnChunks());
                        }
                    }
                    else {
                        if (pduRecebido.getTipo() == 6) {
                            ffs.remove(pacoteRecebido.getAddress());
                            System.out.println("FFS de endereço: " + pacoteRecebido.getAddress() + " removido, existem " + ffs.size() + " FFS agora.");
                        }
                        else {
                            listaPacotes.add(pacoteRecebido);
                        }
                    }
                } catch (IOException  e) {
                    e.printStackTrace();
                }
            }
            lock.unlock();
        }
    }
}
