import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

public class HTTPGWThread implements Runnable {
    private Socket cliente;
    private BufferedReader in;
    private PrintWriter out;
    private DatagramSocket servidor;
    private Incrementar inc;
    private static HashMap<InetAddress, Integer> ffs;
    private static Vector<DatagramPacket> listaPacotes;
    private ReentrantLock lock;

    public HTTPGWThread(Socket c, DatagramSocket s, HashMap<InetAddress, Integer> f, Incrementar i,Vector<DatagramPacket> l, ReentrantLock lo) throws IOException {
        cliente = c;
        servidor = s;
        in = new BufferedReader(new InputStreamReader((c.getInputStream())));
        out =new PrintWriter(new OutputStreamWriter(cliente.getOutputStream()));
        inc = i;
        ffs = f;
        listaPacotes = l;
        lock = lo;
    }
    public void run() {
        try {
        StringBuilder msg = new StringBuilder();
        String str;
        String comando;
        while (!(str = in.readLine()).equals("")) {
            msg.append(str).append("\n");
        }
        String[] tokens = (msg.toString().split("\n"));
        for (int n=0; n<tokens.length; n+=6) {
            String[] tokens1 = tokens[0].split("GET /");
            String[] tokens2 = tokens1[1].split(" HTTP.+");
            comando = tokens2[0];
            System.out.println("A tratar o ficheiro: " + comando);
            Thread t = new Thread(new HTTPGWTPedido(cliente,servidor,comando,ffs,inc,inc.getPedido(),listaPacotes,lock));
            t.start();
        }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
