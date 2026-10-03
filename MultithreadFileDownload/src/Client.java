import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Client {
  static final String HOST = "localhost";
  static final int PORT = 5050;
  static final int WORKERS = 10;

  static class Range{
    final int id;
    final long offset;
    final long length;

    Range (int id, long offset, long lenth){
      this.id = id;
      this.offset = offset;
      this.length = lenth;
    }
  }

  static List<Range> calculateRange(long size, int workers){
    List<Range> ranges = new ArrayList<>();
    long chunk = size / workers;

    for (int i = 0; i < workers; i++){
      long offset = i * chunk;
      long length = (i == workers - 1) ? size - offset : chunk;
      if(length > 0){
        ranges.add(new Range(i, offset, length));
      }
    }
    return ranges;
  }

  static void verifyRanges(List<Range> ranges, long size){
    long expectedOffset = 0;
    long total = 0;

    for(Range r: ranges){
      if(r.offset != expectedOffset){
        throw new IllegalStateException("Gap or overlap at worker "+ r.id);
      }
      expectedOffset = r.offset + r.length;
      total += r.length;
    }

    if(total != size){
      throw new IllegalStateException("Ranges cover "+total+" bytes, expected "+size);
    }
    System.out.println("Ranges verified: no gaps, no overlaps, total = "+total);
  }

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


    List<Range> ranges = calculateRange(size, WORKERS);
    for (Range r : ranges){
      System.out.println("Worker "+r.id+": offset="+r.offset+" length="+r.length+" (bytes "+ r.offset+" to "+(r.offset + r.length - 1) +")");
    }
    verifyRanges(ranges, size);

  }

}
