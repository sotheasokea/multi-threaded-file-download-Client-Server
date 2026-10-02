import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.Buffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server{
  static final int PORT = 5050;

  static void handleClient(Socket client){
    String name = Thread.currentThread().getName();
    
    try(
      client;
      BufferedReader in = new BufferedReader(
        new InputStreamReader(
          client.getInputStream(), StandardCharsets.UTF_8
        )
      );
      OutputStream out = client.getOutputStream()
    ){
      String request = in.readLine();
      if(request == null){
        return;
      }
      System.out.println("["+name+"] request: "+ request);

      out.write("ERROR 400 not implemented yet\n".getBytes(StandardCharsets.UTF_8));
      out.flush();
    } catch (IOException e){
      System.err.println("Error: "+e.getMessage());
    }
  }

  public static void main(String[] args)throws Exception{
    ExecutorService pool = Executors.newFixedThreadPool(20);

    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    while (true){
      Socket client = serverSocket.accept();
      pool.submit(()-> handleClient(client));
    }

    
  }
}