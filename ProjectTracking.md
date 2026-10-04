**The roadmap**

1. Design the protocol
2. Server skeleton: accept connections and handle many at once
4. Server commands LIST and INFO
5. Server command GET using Traditional I/O
6. Test the server by hand, before the client exists
7. Client: LIST and INFO
8. Client: split the file into 10 ranges and run 10 workers
9. Verify the result (size and hash)
10. Add the NIO/native-transfer mode
11. Run the experiments and write up the results


---
# Create Server with Tradictional I/O
+ creating port for network connection
>
**code**

```java
import java.net.ServerSocket;
import java.net.Socket;

public class Server{
  static final int PORT = 5050;

  public static void main(String[] args)throws Exception{
    // this code is for testing if the port is working correctly and client can connect to it
    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    Socket client = serverSocket.accept();
    System.out.println("Client connected: "+client.getRemoteSocketAddress());

    client.close();
    serverSocket.close();
  }
}
```

`in powershell: Test-NetConnection localhost -Port 5050`
> this acts as a client and makes an actual TCP connection to port 5050
---

+ always run the server
>
**code**
```java
while (true){
      Socket client = serverSocket.accept();
      System.out.println("Client connected: "+ client.getRemoteSocketAddress());

      client.close();
    }
```
---

+ create thread so that when there's new request, client don't have to wait ( wait when all thread are busy)
```java
ExecutorService pool = Executors.newFixedThreadPool(20);

ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);

    while (true){
      Socket client = serverSocket.accept();
      pool.submit(()-> handleClient(client));
    }
```

---
| Line | Purpose |
|---|---|
| `$c = New-Object System.Net.Sockets.TcpClient("localhost", 5050)` | Connects to your server. This is the moment your server's `accept()` returns. |
| `$s = $c.GetStream()` | Gets the connection's byte pipe, the client-side equivalent of `getInputStream()` and `getOutputStream()`. |
| `$w = New-Object System.IO.StreamWriter($s); $w.AutoFlush = $true` | Creates a writer for sending text. `AutoFlush` sends each line immediately, like the `flush()` in your server. |
| `$r = New-Object System.IO.StreamReader($s)` | Creates a reader for text coming back. |
| `$w.WriteLine("LIST")`| Sends the request line. Your server's `readLine()` receives this. |
| `$r.ReadLine()`| Waits for the server's reply and prints it. |
| `$c.Close()` | Closes the connection. Always do this so the server thread is released. |

> instead of use $r.ReadLine() : use `while (($line = $r.ReadLine()) -ne "END") {
    $line
}` to read more line

+ handleClient function

```java
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
```

+ Edit handleClient Function again by replacing placeholder with the exact reponse

```java
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

      String[] parts = request.trim().split("\\s+");
      String command = parts[0].toUpperCase();

      switch (command) {
          case "LIST":
            File dir = new File(SHARED_DIR);
            File[] files = dir.listFiles();
            if (files != null && files.length > 0){
              for (File f: files){
                if(f.isFile()){
                  sendLine(out, "FILE_NAME: "+f.getName() + " , SIZE: " + f.length()+" bytes");
                }
              }
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
                      byte[] buffer = new byte[length];
                      int bytesRead = raf.read(buffer, 0, length);
                      if(bytesRead > 0){
                        out.write(buffer, 0, bytesRead);
                        out.flush();
                      }
                    }
                  }
                } catch (NumberFormatException e) {
                  sendLine(out, "ERROR 400 Offset and length must be integers!");
                }
                  sendLine(out, "OK GET received for " + parts[1]);
              }
          break;
          default:
              sendLine(out, "ERROR 400 unknown command");
      }
    } catch (IOException e){
      System.err.println("Error: "+e.getMessage());
    }
  }
  ```

+ instead of using command in Terminal create a TestServerSide.java file : [view code](MultithreadFileDownload\src\TestServerSide.java)


---------

# Create Client

**GET request for client**
```java
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

  public static void main(String[] args)throws IOException {
    askForFileList();
  }

}
```

>`the client will get the file list from the shared folder`

**add INFO request**
```java
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
```
`use this command to test this part after run the server`

```command
javac Client.java
java Client
java Client BigFile.zip
java Client nothing.txt
```

**code for downloading file to a specific location**
```java
  // for download operation
  static String readHeaderLine(InputStream in)throws IOException{
    StringBuilder sb = new StringBuilder();
    int b;
    while ((b = in.read()) != 1){
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
    try(
      Socket socket = new Socket(HOST, PORT);
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
```

+ main

```java
  public static void main(String[] args)throws IOException {

    String fileName = args.length > 0 ? args[0] : "test.txt";

    askForFileList();

    long size = getFileSize(fileName);
    System.out.println("Size of "+fileName+" = "+size+" bytes");

    /*
    // testing file chunk
    List<Range> ranges = calculateRange(size, WORKERS);
    for (Range r : ranges){
      System.out.println("Worker "+r.id+": offset="+r.offset+" length="+r.length+" (bytes "+ r.offset+" to "+(r.offset + r.length - 1) +")");
    }
    verifyRanges(ranges, size);
    */

    String outputPath = "../../File_Container/downloaded_file/downdloaded_" + fileName;
    prepareOutputFile(outputPath, size);

    // download the whole file = 1 range
    Range whole = new Range(0, 0, size);
    downloadRange(fileName, whole, outputPath);
    System.out.println("Downloaded to " + outputPath);

  }
```
+ to ckeck if 2 files are identical:

``` command
Get-FileHash "..\..\File_Container\downloaded_file\<name your program printed>" -Algorithm SHA256
Get-FileHash "..\..\File_Container\shared\examples_of_os.png" -Algorithm SHA256
```

----

+ download file by chunk
```java
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
```
+ main

```java
    String fileName = args.length > 0 ? args[0] : "test.txt";

    askForFileList();

    long size = getFileSize(fileName);
    System.out.println("Size of "+fileName+" = "+size+" bytes");
    
    String outputPath = "../../File_Container/downloaded_file/downdloaded_" + fileName;
    prepareOutputFile(outputPath, size);

    List<Range> ranges = calculateRange(size, WORKERS);
    verifyRanges(ranges, size);

    try {
      long start = System.nanoTime();
      downloadParallel(fileName, ranges, outputPath);
      double seconds = (System.nanoTime() - start)/ 1e9;
      System.out.printf("Downloaded in %.3f s (%.2f MB/s)%n", seconds, size / (1024.0 * 1024.0) / seconds);
    } catch (Exception e) {
      System.err.println("Download failed....!");
    }
```

> downdload file not the whole, seperate pieces for 10 workers

----

+ addedd method to check if file is identical : 

```java
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
```

+ run this command to check

```command
java Client BigFile.zip ../../File_Container/shared/BigFile.zip
```



----
# Add NIO native transfer to Server

`adding NIO mode transfer into the existed server, with some change as mentioned here: `

+ adding mode so that we can choose when run the Server
```java
  static void acceptTraditional(ExecutorService pool)throws IOException{
    ServerSocket serverSocket = new ServerSocket(PORT);
    System.out.println("Server listening on port "+PORT);
    while(true){
      Socket client = serverSocket.accept();
      pool.submit(()-> handleClient(client));
    }
  }

  static void acceptNio(ExecutorService pool)throws IOException{
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.bind(new InetSocketAddress(PORT));
    System.out.println("Server listening on port "+ PORT + " (NIO accept)");

    while(true){
      SocketChannel channel = serverChannel.accept();
      pool.submit(()-> handleClient(channel.socket()));
    }
  }
```
+ current main:
```java
  public static void main(String[] args)throws Exception{
    ExecutorService pool = Executors.newFixedThreadPool(20);
    File dir = new File(SHARED_DIR);
    if (!dir.exists()) {
        dir.mkdirs();
    }

    String mode = args.length > 0 ? args[0].toLowerCase() : "traditional";
    if(!mode.equals("traditional") && !mode.equals("nio")){
      System.err.println("Usage: Java Server [traditional | nio]");
      return;
    }

    System.out.println("Mode: "+mode);
    if(mode.equals("nio")){
      acceptNio(pool);
    }else{
      acceptTraditional(pool);
    }
  }
```
----
+ to run the server
```command
javac Server.java
java Server                 (traditional)
java Server nio             (NIO accept)
```
>`defualt: traditional, nio = nio mode, else error`

----

+ use `transferTo` for nio:
```java
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

            if(mode.equals("nio")){
              FileChannel fileChannel = raf.getChannel();
              SocketChannel socketChannel = client.getChannel();
              long position = offset;
              long left = length;
              while (left > 0) {
                long sent = fileChannel.transferTo(position, left, socketChannel);
                if(sent <= 0){
                  break;
                }
                position += sent;
                left -= sent;
              }
            }else{
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
        }
      } catch (NumberFormatException e) {
        sendLine(out, "ERROR 400 Offset and length must be integers!");
      }
    }
  break;
```

>`here we are editing in Server only, for client still use the same Tranditional mode to GET file..`

+ in Client Side: 2 ways to download using traditional | nio

```java
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
```
> `Use transferFrom() to transfer the file instead of reading bytes by bytes like traditional mode`

+ current main in client:

```java
public static void main(String[] args)throws IOException {

    String fileName = args.length > 0 ? args[0] : "test.txt";

    mode = args.length > 2 ? args[2].toLowerCase() : "traditional";

    if(!mode.equals("traditional") && !mode.equals("nio")){
      System.err.println("Usage: java Client <file> <original path> [traditional | nio]");
      return;
    }
    System.out.println("Mode: "+mode);

    askForFileList();

    long size = getFileSize(fileName);
    System.out.println("Size of "+fileName+" = "+size+" bytes");

    /*
    // testing file chunk
    List<Range> ranges = calculateRange(size, WORKERS);
    for (Range r : ranges){
      System.out.println("Worker "+r.id+": offset="+r.offset+" length="+r.length+" (bytes "+ r.offset+" to "+(r.offset + r.length - 1) +")");
    }
    verifyRanges(ranges, size);
    */

    /*
    String outputPath = "../../File_Container/downloaded_file/downdloaded_" + fileName;
    prepareOutputFile(outputPath, size);

    // download the whole file = 1 range
    Range whole = new Range(0, 0, size);
    downloadRange(fileName, whole, outputPath);
    System.out.println("Downloaded to " + outputPath);
    */

    String outputPath = "../../File_Container/downloaded_file/downdloaded_" + fileName;
    prepareOutputFile(outputPath, size);

    List<Range> ranges = calculateRange(size,1);
    // for (Range r : ranges){
    //   System.out.println("Worker "+r.id+": offset="+r.offset+" length="+r.length+" (bytes "+ r.offset+" to "+(r.offset + r.length - 1) +")");
    // }
    verifyRanges(ranges, size);

    try {
      long start = System.nanoTime();
      downloadParallel(fileName, ranges, outputPath);
      double seconds = (System.nanoTime() - start)/ 1e9;
      System.out.printf("Downloaded in %.3f s (%.2f MB/s)%n", seconds, size / (1024.0 * 1024.0) / seconds);
      verifyDownload(outputPath, size, args.length > 1 ? args[1] : null);
    } catch (Exception e) {
      System.err.println("Download failed....!");
    }

  }
```

----

