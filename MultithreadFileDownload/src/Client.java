import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;
// for nio mode
import java.nio.channels.*;

public class Client {
  static final String HOST = "localhost";
  static final int PORT = 5050;
  static String mode = "traditional";
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
  
  // for download operation
  static String readHeaderLine(InputStream in)throws IOException{
    StringBuilder sb = new StringBuilder();
    int b;
    while ((b = in.read()) != -1){
      if(b == '\n'){
        return sb.toString().trim();
      }
      sb.append((char) b);
    }
    return sb.length() == 0? null : sb.toString().trim();
  }
  
  static void prepareOutputFile(String path, long size)throws IOException{
    File file = new File(path);
    file.getParentFile().mkdir();
    try(
      RandomAccessFile raf = new RandomAccessFile(file, "rw")
    ){
      raf.setLength(size);
    }
  }

  //- worker method
  static void downloadRange(String fileName, Range r, String outputPath)throws IOException{
    SocketChannel channel = null;
    Socket socket;
    if(mode.equals("nio")){
      channel = SocketChannel.open(new InetSocketAddress(HOST, PORT));
      socket  = channel.socket();
    }else{
      socket = new Socket(HOST, PORT);
    }
    try(
      socket;
      OutputStream out = socket.getOutputStream();
      InputStream in = socket.getInputStream();

      RandomAccessFile raf = new RandomAccessFile(outputPath, "rw")
    ){
      String request = "GET "+fileName+" "+r.offset + " "+ r.length + "\n";
      out.write(request.getBytes(StandardCharsets.UTF_8));
      out.flush();

      String header = readHeaderLine(in);
      if(header == null || !header.startsWith("OK ")){
        throw new IOException("Server respond: "+header);
      }
      long expected = Long.parseLong(header.substring(3).trim());
      if(expected != r.length){
        throw new IOException("Asked for "+r.length+" bytes but server announced "+expected);
      }

      if(channel != null){
        FileChannel fileChannel = raf.getChannel();
        long position = r.offset;
        long left = r.length;
        while(left>0){
          long received = fileChannel.transferFrom(channel, position, left);
          if(received <= 0){
            throw new IOException("Connection closed early, "+left+" bytes missing");
          }
          position += received;
          left -= received;
        }
      }else{
        raf.seek(r.offset);
        byte[] buffer = new byte[64*1024];
        long remaining = r.length;
        while(remaining > 0){
          int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
          if(n == -1){
            throw new EOFException("Connection closed early, "+remaining+" bytes missing");
          }
          raf.write(buffer, 0, n);
          remaining -= n;
        }
      }
    }
  }

  static void downloadParallel(String fileName, List<Range> ranges, String outputPath)throws Exception{
    ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
    List<Future<?>> futures = new ArrayList<>();

    for(Range r : ranges){
      futures.add(pool.submit(() ->{
        try {
          downloadRange(fileName, r, outputPath);
          System.out.println("Worker " + r.id + " finished!");
        } catch (IOException e) {
          throw new RuntimeException("Worker " + r.id + " failed to download", e);
        }
      }));
    }
    try{
      for (Future<?> f : futures){
        // waits for this worker; rethrows its error if it failed
        f.get();
      }
    }finally{
      pool.shutdown();
    }

  }

  static String sha256(String path)throws Exception{
    MessageDigest md = MessageDigest.getInstance("SHA-256");

    try(
      InputStream in = new FileInputStream(path)
    ){
      byte[] buf = new byte[64*1024];
      int n;
      while((n = in.read(buf)) != -1){
        md.update(buf, 0, n);
      }
    }
    StringBuilder sb = new StringBuilder();
    for(byte b : md.digest()){
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  static void verifyDownload(String outputPath, long expectatedSize, String originalPath)throws Exception{
    long actual = new File(outputPath).length();
    System.out.println("Size check: " + (actual == expectatedSize ? "OK" : "MISMATCH ("+actual+")"));

    String hash = sha256(outputPath);
    System.out.println("SHA-256 of download: "+hash);

    if(originalPath != null){
      String original = sha256(originalPath);
      System.out.println("Hash check: "+(hash.equals(original) ? "OK (identical to original)" : "MISMATCH"));
    }

  }

  public static void main(String[] args)throws IOException {

    // ask for file only
    if(args.length == 0 || args[0].equalsIgnoreCase("LIST")){
      System.out.println("FILE list: ");
      askForFileList();
      return;
    }


    String fileName = args.length > 0 ? args[0] : "test.txt";

    mode = args.length > 2 ? args[2].toLowerCase() : "traditional";

    if(!mode.equals("traditional") && !mode.equals("nio")){
      System.err.println("Usage: java Client <file> <original path> [traditional | nio]");
      return;
    }
    System.out.println("Mode: "+mode);

    // askForFileList();

    long size = getFileSize(fileName);
    System.out.println("Size of "+fileName+" = "+size+" bytes");


    String outputPath = "../../File_Container/downloaded_file/downdloaded_" + fileName;
    prepareOutputFile(outputPath, size);

    int workers = WORKERS;
    if(args.length > 1){
      try{
        workers = Integer.parseInt(args[1]);
      }catch(NumberFormatException e){
        System.err.println("Usage: java Client <file> [workers] [traditional | nio] [original path]");
        return;
      }
      if(workers < 1){
        System.err.println("Workers must be a whole number 1 or more.");
        return;
      }
    }

    List<Range> ranges = calculateRange(size,workers);
    // for (Range r : ranges){
    //   System.out.println("Worker "+r.id+": offset="+r.offset+" length="+r.length+" (bytes "+ r.offset+" to "+(r.offset + r.length - 1) +")");
    // }
    // verifyRanges(ranges, size);

    try {
      long start = System.nanoTime();
      downloadParallel(fileName, ranges, outputPath);
      double seconds = (System.nanoTime() - start)/ 1e9;
      System.out.printf("Downloaded in %.3f s (%.2f MB/s)%n", seconds, size / (1024.0 * 1024.0) / seconds);
      verifyDownload(outputPath, size, args.length > 3 ? args[3] : null);
    } catch (Exception e) {
      System.err.println("Download failed: "+e);
    }

  }

}
