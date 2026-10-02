import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server{
  static final int PORT = 5050;

  static void handleClient(Socket client){
    String name = Thread.currentThread().getName();
    System.out.println("["+name+"] start: "+client.getRemoteSocketAddress());
    try {
      Thread.sleep(5000); // just an ex. that this thread take 5000ms to finish
    } catch (Exception e) {
      System.err.println("Error: "+ e.getMessage());
    }
    System.out.println("["+name+"] done!");
  }

  public static void main(String[] args)throws Exception{
    ExecutorService pool = Executors.newFixedThreadPool(20);

    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    while (true){
      Socket client = serverSocket.accept();
      pool.submit(()-> handleClient(client));

      client.close();
    }

    
  }
}