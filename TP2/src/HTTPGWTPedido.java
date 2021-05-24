import java.io.*;
import java.net.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class HTTPGWTPedido implements Runnable {
    private DataOutputStream outFicheiro;
    private PrintStream out;
    private DatagramSocket servidor;
    private String comando;
    private static HashMap<InetAddress, Integer> ffs;
    private Incrementar inc;
    private static HashMap<InetAddress, Integer> ffsFicheiro;
    private static HashMap<InetAddress, Integer> ffsResposta;
    private long tam;
    private int TAMANHO_MAX = 512;
    private int pedido;
    private Vector<DatagramPacket> listaPacotes;
    private ReentrantLock lock;
    private HashMap<Integer,byte[]> chunks = new HashMap<>();

    public HTTPGWTPedido(Socket c, DatagramSocket s, String com, HashMap<InetAddress, Integer> f, Incrementar i, int p, Vector<DatagramPacket> l, ReentrantLock lo) throws IOException {
        servidor = s;
        outFicheiro = new DataOutputStream(new BufferedOutputStream(c.getOutputStream()));
        out = new PrintStream(c.getOutputStream());
        comando = com;
        ffs = f;
        inc = i;
        ffsFicheiro = new HashMap<>();
        ffsResposta = new HashMap<>();
        tam = 0;
        pedido = p;
        listaPacotes = l;
        lock = lo;
    }

    public void run() {
        try {
            lock.lock();
            if (ffs.isEmpty()) {
                fileNotFound();
            lock.unlock();
            }
            else {
                byte buffer[] = new byte[TAMANHO_MAX + 16];
                for (Map.Entry<InetAddress, Integer> entry : ffs.entrySet()) {
                    InetAddress address = entry.getKey();
                    Integer porta = entry.getValue();

                    PDU pdu = new PDU(pedido, 3, 1, ffs.size(), comando.getBytes());
                    buffer = pdu.toByte();
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                    servidor.send(pacote);
                    System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                }
                boolean timeout = false;
                long startTime = System.nanoTime();
                lock.unlock();
                boolean pedidoTerminado = false;
                while (!pedidoTerminado && !timeout) {
                    lock.lock();
                    long endTime = System.nanoTime();
                    long tempoEspera = endTime - startTime;
                    if (tempoEspera/1000000 >= 5000) {
                        timeout = true;
                        System.out.println("Timed Out " + comando);
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                        break;
                    }
                    if (!listaPacotes.isEmpty()) {
                        DatagramPacket pacoteRecebido = listaPacotes.get(0);
                        byte[] a = new byte[pacoteRecebido.getLength()];
                        System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                        PDU pduRecebido = new PDU(a);
                        listaPacotes.remove(0);
                        if (pduRecebido.getPedido() == pedido) {
                            startTime = System.nanoTime();
                            switch (pduRecebido.getTipo()) {
                                case 2:
                                    boolean reenvioPedido = false;
                                    int k=0;
                                    k++;
                                    int pacotesEspera = pduRecebido.getnChunks();
                                    ffsResposta.put(pacoteRecebido.getAddress(),pacoteRecebido.getPort());
                                    while (k != pacotesEspera) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        endTime = System.nanoTime();
                                        tempoEspera = endTime - startTime;
                                        if (tempoEspera/1000000 >= 10000) {
                                            timeout = true;
                                            System.out.println("Timed Out " + comando);
                                            if (lock.isHeldByCurrentThread()) {
                                                lock.unlock();
                                            }
                                            break;
                                        }

                                        lock.lock();
                                        if (tempoEspera/1000000 >= 5000 && !reenvioPedido ) {
                                            reenvioPedido = true;
                                            reenviaPedidoInformacao();
                                        }
                                        if (!listaPacotes.isEmpty()) {
                                            pacoteRecebido = listaPacotes.get(0);
                                            a = new byte[pacoteRecebido.getLength()];
                                            System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                                            pduRecebido = new PDU(a);
                                            listaPacotes.remove(0);
                                            if (pduRecebido.getPedido() == pedido) {
                                                reenvioPedido = false;
                                                startTime = System.nanoTime();
                                                switch (pduRecebido.getTipo()) {
                                                    case 2:
                                                        ffsResposta.put(pacoteRecebido.getAddress(),pacoteRecebido.getPort());
                                                        k++;
                                                        break;
                                                    case 3:
                                                        ffsFicheiro.put(pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                                                        k++;
                                                        break;
                                                    default: break;
                                                }
                                            }
                                            else {listaPacotes.add(pacoteRecebido);}
                                        }
                                        lock.unlock();
                                    }
                                    if (timeout) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        break;
                                    }
                                    if (ffsFicheiro.isEmpty()) {
                                        fileNotFound();
                                        pedidoTerminado = true;
                                    }
                                    else {
                                        Vector<Integer> idPacotes = new Vector<>();
                                        int nPacotes = (int)tam/(TAMANHO_MAX);
                                        if (tam%(TAMANHO_MAX) != 0) nPacotes++;
                                        System.out.println("Serão necessários "+ nPacotes+ " pacotes para o ficheiro "+ comando);
                                        buffer = new byte[TAMANHO_MAX + 16];
                                        if (nPacotes <= ffsFicheiro.size()) {
                                            int i =1;
                                            for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                                                if (i-1 == nPacotes) break;
                                                InetAddress address = entry.getKey();
                                                Integer porta = entry.getValue();
                                                PDU pdu = new PDU(pedido,4, i, nPacotes, comando.getBytes());
                                                buffer = pdu.toByte();
                                                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                                                servidor.send(pacote);
                                                System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                                                i++;
                                            }
                                        }
                                        else {
                                            int nIteracoes = nPacotes/ffsFicheiro.size();
                                            int i=1;
                                            if (nPacotes%ffsFicheiro.size() != 0) nIteracoes++;
                                            for (int j=0; j<nIteracoes;j++) {
                                                for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                                                    if (i-1 == nPacotes) break;
                                                    InetAddress address = entry.getKey();
                                                    Integer porta = entry.getValue();

                                                    PDU pdu = new PDU(pedido,4, i, nPacotes, comando.getBytes());
                                                    buffer = pdu.toByte();
                                                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                                                    servidor.send(pacote);
                                                    System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                                                    i++;
                                                }
                                            }
                                        }
                                    }
                                    break;


                                case 3:
                                    k =0;
                                    ffsResposta.put(pacoteRecebido.getAddress(),pacoteRecebido.getPort());
                                    ffsFicheiro.put(pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                                    String tamanhoS = new String(pduRecebido.getData(), 0, pduRecebido.getData().length);
                                    tam = Long.parseLong(tamanhoS);
                                    k++;
                                    pacotesEspera = pduRecebido.getnChunks();
                                    reenvioPedido = false;
                                    while (k != pacotesEspera) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        endTime = System.nanoTime();
                                        tempoEspera = endTime - startTime;
                                        if (tempoEspera/1000000 >= 10000) {
                                            timeout = true;
                                            System.out.println("Timed Out " + comando);
                                            if (lock.isHeldByCurrentThread()) {
                                                lock.unlock();
                                            }
                                            break;
                                        }
                                        lock.lock();
                                        if (tempoEspera/1000000 >= 5000 && !reenvioPedido ) {
                                            reenvioPedido = true;
                                            reenviaPedidoInformacao();
                                        }
                                        if (!listaPacotes.isEmpty()) {
                                            pacoteRecebido = listaPacotes.get(0);
                                            a = new byte[pacoteRecebido.getLength()];
                                            System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                                            pduRecebido = new PDU(a);
                                            listaPacotes.remove(0);
                                            if (pduRecebido.getPedido() == pedido) {
                                                startTime = System.nanoTime();
                                                reenvioPedido = false;
                                                reenvioPedido = true;
                                                switch (pduRecebido.getTipo()) {
                                                    case 2:
                                                        ffsResposta.put(pacoteRecebido.getAddress(),pacoteRecebido.getPort());
                                                        k++;
                                                        break;
                                                    case 3:
                                                        ffsFicheiro.put(pacoteRecebido.getAddress(), pacoteRecebido.getPort());
                                                        k++;
                                                        break;
                                                    default:
                                                        break;
                                                }
                                            }
                                            else {listaPacotes.add(pacoteRecebido);}
                                            lock.unlock();
                                        }
                                    }
                                    if (timeout) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        break;
                                    }
                                    Vector<Integer> idPacotes = new Vector<>();
                                    int nPacotes = (int)tam/(TAMANHO_MAX);
                                    if (tam%(TAMANHO_MAX) != 0) nPacotes++;
                                    buffer = new byte[TAMANHO_MAX + 16];
                                    if (nPacotes <= ffsFicheiro.size()) {
                                        int i =1;
                                        for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                                            if (i-1 == nPacotes) break;
                                            InetAddress address = entry.getKey();
                                            Integer porta = entry.getValue();
                                            PDU pdu = new PDU(pedido,4, i, nPacotes, comando.getBytes());
                                            buffer = pdu.toByte();
                                            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                                            servidor.send(pacote);
                                            System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                                            i++;
                                        }
                                    }
                                    else {
                                        int nIteracoes = nPacotes/ffsFicheiro.size();
                                        int i=1;
                                        if (nPacotes%ffsFicheiro.size() != 0) nIteracoes++;
                                        for (int j=0; j<nIteracoes;j++) {
                                            for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                                                if (i-1 == nPacotes) break;
                                                InetAddress address = entry.getKey();
                                                Integer porta = entry.getValue();

                                                PDU pdu = new PDU(pedido,4, i, nPacotes, comando.getBytes());
                                                buffer = pdu.toByte();
                                                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                                                servidor.send(pacote);
                                                System.out.println("Enviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                                                i++;
                                            }
                                        }
                                    }
                                    break;
                                case 4:
                                    chunks.put(pduRecebido.getChunk(),pduRecebido.getData());
                                    int tamanhoTotal = pduRecebido.getnChunks();
                                    reenvioPedido = false;
                                    while (chunks.size() != tamanhoTotal) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        endTime = System.nanoTime();
                                        tempoEspera = endTime - startTime;
                                        if (tempoEspera/1000000 >= 10000) {
                                            System.out.println("Timed Out" + comando);
                                            timeout = true;
                                            break;
                                        }
                                        lock.lock();
                                        if (tempoEspera/1000000 >= 5000 && !reenvioPedido ) {
                                            reenvioPedido = true;
                                            reenviaPedidoFicheiro(tamanhoTotal);
                                        }
                                        if (!listaPacotes.isEmpty()) {
                                            pacoteRecebido = listaPacotes.get(0);
                                            a = new byte[pacoteRecebido.getLength()];
                                            System.arraycopy(pacoteRecebido.getData(),0,a,0,pacoteRecebido.getLength());
                                            pduRecebido = new PDU(a);
                                            listaPacotes.remove(0);
                                            if (pduRecebido.getPedido() == pedido) {
                                                if (pduRecebido.getTipo() ==4) {
                                                    startTime = System.nanoTime();
                                                    reenvioPedido = false;
                                                    chunks.put(pduRecebido.getChunk(), pduRecebido.getData());
                                                }
                                            }
                                            else {listaPacotes.add(pacoteRecebido);}
                                        }
                                        lock.unlock();
                                    }
                                    if (timeout) {
                                        if (lock.isHeldByCurrentThread()) {
                                            lock.unlock();
                                        }
                                        break;
                                    }
                                    byte[] ficheiro = reagrupaFicheiro(chunks);
                                    out.println("HTTP/1.1 200 OK");
                                    out.println("Server: Java HTTP Server from HTTGW : 1.0");
                                    out.println("Date: " + new Date());
                                    out.println("Content-type: " + tipoConteudo(comando));
                                    out.println("Content-length: " + tam);
                                    out.println();
                                    out.flush();
                                    outFicheiro.write(ficheiro);
                                    outFicheiro.flush();
                                    pedidoTerminado = true;
                                    System.out.println("Terminei " +pedido);
                                    break;
                                default: break;
                            }
                        }
                        else{
                            listaPacotes.add(pacoteRecebido);
                        }
                    }
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
                if (timeout) {
                    out.println("HTTP/1.1 408 Request Timeout");
                    out.println("Server: Java HTTP Server from HTTPGW : 1.0");
                    out.println("Date: " + new Date());
                    out.println("Content-type: " + comando);
                    out.println("Content-length: " + 0);
                    out.println();
                    out.flush();
                }

            }
        }
        catch (IOException  e) {
            e.printStackTrace();
        }


    }

    public byte[] reagrupaFicheiro(HashMap<Integer,byte[]> c) {
        int i =0;
        byte[] ficheiro = new byte[(int)tam];
        for (Map.Entry<Integer,byte[]> entry : c.entrySet()) {
            System.arraycopy(entry.getValue(), 0, ficheiro, i*TAMANHO_MAX, entry.getValue().length);
            i++;
        }
        return ficheiro;
    }

    public void reenviaPedidoFicheiro (int nPacotes) throws IOException {
        byte[] buffer = new byte[TAMANHO_MAX + 16];
        if (nPacotes <= ffsFicheiro.size()) {
            int i =1;
            for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                if (i-1 == nPacotes) break;
                if (!chunks.containsKey(i)) {
                    InetAddress address = entry.getKey();
                    Integer porta = entry.getValue();
                    PDU pdu = new PDU(pedido, 4, i, nPacotes, comando.getBytes());
                    buffer = pdu.toByte();
                    DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                    servidor.send(pacote);
                    System.out.println("Reenviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                }
                i++;
            }
        }
        else {
            int nIteracoes = nPacotes/ffsFicheiro.size();
            int i=1;
            if (nPacotes%ffsFicheiro.size() != 0) nIteracoes++;
            for (int j=0; j<nIteracoes;j++) {
                for (Map.Entry<InetAddress, Integer> entry : ffsFicheiro.entrySet()) {
                    if (i-1 == nPacotes) break;
                    if (!chunks.containsKey(i)) {
                        InetAddress address = entry.getKey();
                        Integer porta = entry.getValue();

                        PDU pdu = new PDU(pedido, 4, i, nPacotes, comando.getBytes());
                        buffer = pdu.toByte();
                        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                        servidor.send(pacote);
                        System.out.println("Reenviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
                    }
                    i++;
                }
            }
        }
    }

    public void reenviaPedidoInformacao () throws IOException {
        byte buffer[] = new byte[TAMANHO_MAX + 16];
        for (Map.Entry<InetAddress, Integer> entry : ffs.entrySet()) {
            InetAddress address = entry.getKey();
            Integer porta = entry.getValue();
            if (!ffsResposta.containsKey(address)) {
                PDU pdu = new PDU(pedido, 3, 1, ffs.size(), comando.getBytes());
                buffer = pdu.toByte();
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, address, porta);
                servidor.send(pacote);
                System.out.println("Reenviou: " + pdu.getPedido() + " " + pdu.getTipo() + " " + pdu.getChunk() + " " + pdu.getnChunks());
            }
        }
    }




    private String tipoConteudo(String fileRequested) {
        if (fileRequested.endsWith(".htm")  ||  fileRequested.endsWith(".html"))
            return "text/html";
        else
            return "text/plain";
    }

    private void fileNotFound() throws IOException {
        out.println("HTTP/1.1 404 File Not Found");
        out.println("Server: Java HTTP Server from HTTPGW : 1.0");
        out.println("Date: " + new Date());
        out.println("Content-type: " + comando);
        out.println("Content-length: " + 0);
        out.println();
        out.flush();
    }



}
