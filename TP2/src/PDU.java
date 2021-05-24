import java.io.*;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;


public class PDU implements Serializable {
    private int pedido;
    private int tipo;
    private int chunk;
    private int nChunks;
    private byte[] data;

    public PDU(int pedido,int tipo, int chunk, int nChunks, byte[] data) {
        this.pedido = pedido;
        this.tipo = tipo;
        this.chunk = chunk;
        this.nChunks = nChunks;
        this.data = data;
    }

    public PDU(byte[] data) throws UnknownHostException {

        this.pedido = ByteBuffer.wrap(data,0,4).getInt();
        this.tipo = ByteBuffer.wrap(data,4,4).getInt();
        this.chunk = ByteBuffer.wrap(data,8,4).getInt();
        this.nChunks = ByteBuffer.wrap(data,12,4).getInt();

        byte [] dados = new byte[data.length-16];

        System.arraycopy(data,16,dados,0,data.length-16);
        this.data = dados;

    }



    public byte[] toByte ()  throws IOException{
        byte[] pedido = intToBytes(this.pedido); //4
        byte[] tipo = intToBytes(this.tipo); //4
        byte[] chunk = intToBytes(this.chunk); //4
        byte[] nchunks = intToBytes(this.nChunks); //4

        byte [] buffer = new byte[4*4 + this.data.length];

        System.arraycopy(pedido,0,buffer,0,4);
        System.arraycopy(tipo,0,buffer,4,4);
        System.arraycopy(chunk,0,buffer,8,4);
        System.arraycopy(nchunks,0,buffer,12,4);
        System.arraycopy(data,0,buffer,16,data.length);

        return buffer;

    }


    public int getTipo() {
        return tipo;
    }


    public int getChunk() {
        return chunk;
    }

    public int getnChunks() {
        return nChunks;
    }

    public byte[] getData() {
        return data;
    }
    public int getPedido() {
        return pedido;
    }


    private byte[] intToBytes( final int i ) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }

}
