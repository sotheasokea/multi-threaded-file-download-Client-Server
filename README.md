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

# 1. Design the protocol 

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
| `$w.WriteLine("LIST")` | Sends the request line. Your server's `readLine()` receives this. |
| `$r.ReadLine()` | Waits for the server's reply and prints it. |
| `$c.Close()` | Closes the connection. Always do this so the server thread is released. |

+ handling request function

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