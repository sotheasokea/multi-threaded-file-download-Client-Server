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