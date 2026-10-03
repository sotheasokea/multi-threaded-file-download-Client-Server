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