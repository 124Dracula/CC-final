import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class FastFileSrvAux implements Runnable{
    private static Vector<DatagramPacket> queue;
    private DatagramSocket server;
    private ReentrantLock lock;
    private static int TAMANHO_MAX = 512;

    public FastFileSrvAux(DatagramSocket server, Vector<DatagramPacket> q, ReentrantLock l) {
        this.server = server;
        this.queue = q;
        this.lock = l;

    }

    public void run() {

        try {
            while (true) {
                lock.lock();
                if (!queue.isEmpty()) {
                    DatagramPacket pacoteRecebido = queue.get(0);
                    byte[] a = new byte[pacoteRecebido.getLength()];
                    System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                    PDU pduRecebido = new PDU(a);
                    queue.remove(0);
                    lock.unlock();
                    switch (pduRecebido.getTipo()) {
                        case 3:
                            verificaFicheiro(server, pduRecebido, pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                            break;
                        case 4:
                            downloadChunk(server,pduRecebido,pacoteRecebido.getAddress(),pacoteRecebido.getPort());
                            break;
                    }
                } else {
                    lock.unlock();
                }
            }
        } catch (IOException  e) {
            System.out.println("Encontrei exceção");
        }
    }


        public static void verificaFicheiro(DatagramSocket server, PDU pdu, InetAddress add, int porta) throws IOException {
        byte[] nomeficheiroB = pdu.getData();
        String nomeFicheiro = new String(nomeficheiroB,0,nomeficheiroB.length);
        String ficheiroDiretoria = "./".concat(nomeFicheiro);
        File ficheiro = new File(ficheiroDiretoria);
        System.out.println(ficheiro);
        if(ficheiro.exists()) {
            byte[] mensagemB = new byte[TAMANHO_MAX];
            long tam = ficheiro.length();
            String tamS = String.valueOf(tam);
            mensagemB = tamS.getBytes();
            PDU pdunovo = new PDU(pdu.getPedido(),3,1,pdu.getnChunks(),mensagemB);
            byte[] pduBytes = pdunovo.toByte();
            DatagramPacket resposta = new DatagramPacket (pduBytes,pduBytes.length,add,porta);
            server.send(resposta);
            System.out.println("Enviou: " + pdunovo.getPedido() + " " + pdunovo.getTipo() + " " + pdunovo.getChunk() + " " + pdunovo.getnChunks());
        }
        else{
            String respostaS = "não";
            PDU pdunovo = new PDU(pdu.getPedido(),2,1,pdu.getnChunks(),respostaS.getBytes());
            byte[] pduBytes = pdunovo.toByte();
            DatagramPacket resposta = new DatagramPacket (pduBytes,pduBytes.length,add,porta);
            server.send(resposta);
            System.out.println("Enviou: " + pdunovo.getPedido() + " " + pdunovo.getTipo() + " " + pdunovo.getChunk() + " " + pdunovo.getnChunks());
        }
    }

    public static void downloadChunk(DatagramSocket server, PDU pdu, InetAddress add, int porta) throws IOException {
        byte[] nomeficheiroB = pdu.getData();
        String nomeFicheiro = new String(nomeficheiroB,0,nomeficheiroB.length);
        String ficheiroDiretoria = "./".concat(nomeFicheiro);
        File ficheiro = new File(ficheiroDiretoria);
        System.out.println("Tou a fazer download de: " + nomeFicheiro);
        int tamanhoPacote = TAMANHO_MAX;
        if (pdu.getChunk() == pdu.getnChunks()) {
            tamanhoPacote = (int) ficheiro.length()%TAMANHO_MAX;
        }
        int posicaoInicial = TAMANHO_MAX*(pdu.getChunk()-1);
        FileInputStream fis = new FileInputStream(ficheiro);
        ByteBuffer bytes = ByteBuffer.wrap(new byte[tamanhoPacote]);
        fis.getChannel().read(bytes, posicaoInicial);

        byte[] ficheiroB = bytes.array();
        System.out.println("Chunk nº: " + pdu.getChunk());
        PDU pduResposta = new PDU(pdu.getPedido(), 4, pdu.getChunk(), pdu.getnChunks(), ficheiroB);

        byte[] pduBytes = pduResposta.toByte();
        DatagramPacket resposta = new DatagramPacket(pduBytes, pduBytes.length, add, porta);
        server.send(resposta);
        System.out.println("Enviou: " + pduResposta.getPedido() + " " + pduResposta.getTipo() + " " + pduResposta.getChunk() + " " + pduResposta.getnChunks());
    }



}
