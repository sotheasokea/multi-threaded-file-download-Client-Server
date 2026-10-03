import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Client {
  static final String HOST = "localhost";
  static final int PORT = 5050;

  static void askForFileList()throws IOException{
    try(
      Socket socket = new Socket(HOST, PORT);
      OutputStream out = socket.getOutputStream();
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
      )
    ){
      out.write("LIST\n".getBytes(StandardCharsets.UTF_8));
      out.flush();

      String line;
      while ((line = in.readLine()) != null){
        if(line.equals("END")){
          break;
        }
        if(line.startsWith("FILE ")){
          String[] parts = line.split("\\s+");
          String name = parts[1];

          long size = Long.parseLong(parts[2]);
          System.out.println(name+" ("+size+" bytes)");
        }else{
          System.out.println("Server respond: "+line);
        }
      }
    }
  }

  static long getFileSize(String fileName)throws IOException{
    try(
      Socket socket = new Socket(HOST, PORT);
      OutputStream out = socket.getOutputStream();
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
      )
    ){
      out.write(("INFO "+fileName+"\n").getBytes(StandardCharsets.UTF_8));
      out.flush();

      String line = in.readLine();
      if(line == null){
        throw new IOException("Server closed the connection without replying");
      }
      if(line.startsWith("SIZE ")){
        return Long.parseLong(line.substring(5).trim());
      }
      throw new IOException("Server respond: "+line);
    }
  }
  public static void main(String[] args)throws IOException {

    String fileName = args.length > 0 ? args[0] : "test.txt";

    askForFileList();

    long size = getFileSize(fileName);
    System.out.println("Size of "+fileName+" = "+size+" bytes");
  }

}
