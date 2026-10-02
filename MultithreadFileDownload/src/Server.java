import java.net.ServerSocket;
import java.net.Socket;

public class Server{
  static final int PORT = 5050;

  public static void main(String[] args)throws Exception{
    // this code is for testing if the port is working correctly and client can connect to it
    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    while (true){
      Socket client = serverSocket.accept();
      System.out.println("Client connected: "+ client.getRemoteSocketAddress());

      client.close();
    }
  }
}