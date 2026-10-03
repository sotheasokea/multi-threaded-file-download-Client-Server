import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TestServerSide {
    public static void main(String[] args) {
        System.out.println("--- Starting Tests ---");
        
        testTextCommand("LIST");
        testTextCommand("INFO test.txt");
        testGetCommand("test.txt", 0, 5);     // should give OK 5
        testGetCommand("BigFile.zip", 0, 100_000);  
        testGetCommand("test.txt", 3, 4);     // a range in the middle
        testGetCommand("test.txt", -2, 100);  // should give ERROR 416...
        testGetCommand("nothing.txt", 0, 5);  // should give ERROR 404...
    }

    static void testTextCommand(String command) {
        try (Socket socket = new Socket("localhost", 5050);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
             
            System.out.println("Sending: " + command);
            out.println(command);
            
            System.out.println("Server Response:");
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println(response);
            }
            System.out.println("-------------------------");
            
        } catch (Exception e) {
            System.err.println("Test Failed: " + e.getMessage());
        }
    }

    static String readHeaderLine(InputStream in) throws Exception {
    StringBuilder sb = new StringBuilder();
    int b;
    while ((b = in.read()) != -1) {
        if (b == '\n') {
            return sb.toString().trim();
        }
        sb.append((char) b);
    }
    return sb.length() == 0 ? null : sb.toString().trim();
}

    static void testGetCommand(String filename, int offset, int length) {
        try (Socket socket = new Socket("localhost", 5050);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            InputStream in = socket.getInputStream()) {

            String command = "GET " + filename + " " + offset + " " + length;
            System.out.println("Sending: " + command);
            out.println(command);

            String header = readHeaderLine(in);
            System.out.println("Header: " + header);

            if (header != null && header.startsWith("OK ")) {
                int expected = Integer.parseInt(header.substring(3));
                byte[] payload = in.readNBytes(expected);
                System.out.println("Expected " + expected + " bytes, received " + payload.length);
                System.out.println("Payload: " + new String(payload, java.nio.charset.StandardCharsets.UTF_8));

                if (in.read() == -1) {
                    System.out.println("No extra bytes after payload (good)");
                } else {
                    System.out.println("WARNING: server sent extra bytes after the payload");
                }
            } else {
                System.out.println("Server returned an error, so no payload.");
            }
            System.out.println("-------------------------");

        } catch (Exception e) {
            System.err.println("Test Failed: " + e.getMessage());
        }
    }
}