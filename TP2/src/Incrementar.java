public class Incrementar {
    private int id;
    private int pedido;

    public Incrementar() {
        this.pedido =1;
    }

    public synchronized  int getPedido() {
        int p = pedido;
        pedido++;
        return p;
    }
}
