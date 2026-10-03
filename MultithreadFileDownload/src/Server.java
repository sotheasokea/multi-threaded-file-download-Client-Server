import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server{
  static final int PORT = 5050;
  static final String SHARED_DIR = "../../File_Container/shared";

  static void handleClient(Socket client){
    String name = Thread.currentThread().getName();
    System.out.println("[" + name + "] connected: " + client.getRemoteSocketAddress());
    
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

      String[] parts = request.trim().split("\\s+");
      String command = parts[0].toUpperCase();

      switch (command) {
          case "LIST":
            File dir = new File(SHARED_DIR);
            File[] files = dir.listFiles();
            if (files != null && files.length > 0){
              // sendLine(out, String.format("%-30s %-12s", "File", "Size"));
              // sendLine(out, "------------------------------------------");
              for (File f: files){
                if(f.isFile()){
                  // sendLine(out, String.format("%-30s %-12s",
                  // f.getName(),
                  // formatFileSize(f.length())));
                  sendLine(out, "FILE " + f.getName() + " " + f.length());
                }
              }
              sendLine(out, "END");
            }else{
              sendLine(out, "ERROR 404 No files available!");
            }
          break;
          case "INFO":
              if (parts.length != 2) {
                sendLine(out, "ERROR 400 usage: INFO <filename>");
              } else {
                File infoFile = new File(SHARED_DIR, parts[1]);
                if(infoFile.exists() && infoFile.isFile()){
                  // sendLine(out, "Size: " + formatFileSize(infoFile.length()));
                  sendLine(out, "SIZE " + infoFile.length());
                }else{
                  sendLine(out, "ERROR 404 File not found!");
                }
              }
          break;
          case "GET":
              if (parts.length != 4) {
                  sendLine(out, "ERROR 400 usage: GET <filename> <offset> <length>");
              } else {
                String fileName = parts[1];
                try {
                  long offset = Long.parseLong(parts[2]);
                  int length = Integer.parseInt(parts[3]);
                  File getFile = new File(SHARED_DIR, fileName);
                  if(!getFile.exists() || !getFile.isFile()){
                    sendLine(out, "ERROR 400 File not found!");
                  }else if((offset < 0) || (length <= 0) || (offset + length > getFile.length())){
                    sendLine(out, "ERROR 400 Invalid offset or length range!");
                  }else{
                    try (RandomAccessFile raf = new RandomAccessFile(getFile, "r")){
                      raf.seek(offset);
                      sendLine(out, "OK " + length);
                      byte[] buffer = new byte[64*1024];
                      int remaining = length;
                      while (remaining > 0) {
                        int bytesRead = raf.read(buffer, 0, Math.min(buffer.length, remaining));
                        if (bytesRead == -1) {
                          break;
                        }
                        out.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                      }
                      out.flush();
                    }
                  }
                } catch (NumberFormatException e) {
                  sendLine(out, "ERROR 400 Offset and length must be integers!");
                }
              }
          break;
          default:
              sendLine(out, "ERROR 400 unknown command");
      }
    } catch (IOException e){
      System.err.println("Error: "+e.getMessage());
    }
  }

  static String formatFileSize(long size) {
    String[] units = {"B", "KB", "MB", "GB", "TB"};

    double value = size;
    int unitIndex = 0;

    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }

    return String.format("%.2f %s", value, units[unitIndex]);
  }

  static void sendLine(OutputStream out, String line)throws IOException{
    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }
  
  static String formatInfo(String label, String value) {
    return String.format("%-12s : %s", label, value);
  }
  
  // not used
  static File resolveSafe(String name)throws IOException{
    File root = new File(SHARED_DIR).getCanonicalFile();
    File file = new File(root, name).getCanonicalFile();
    if(!file.toPath().startsWith(root.toPath())){
      return null;
    }
    return file;
  }
  
  public static void main(String[] args)throws Exception{
    ExecutorService pool = Executors.newFixedThreadPool(20);
    File dir = new File(SHARED_DIR);
    if (!dir.exists()) {
        dir.mkdirs();
    }

    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    while (true){
      Socket client = serverSocket.accept();
      pool.submit(()-> handleClient(client));
    }

  }
}