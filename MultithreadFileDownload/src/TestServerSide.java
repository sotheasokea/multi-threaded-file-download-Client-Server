import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class TestServerSide {
    public static void main(String[] args) {
        System.out.println("--- Starting Tests ---");
        
        // 1. Test LIST command
        testTextCommand("LIST");
        
        // 2. Test INFO command
        testTextCommand("INFO test.txt");
        
        // 3. Test GET command (Fetching the first 10 bytes)
        testGetCommand("test.txt", 0, 100); 
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

    static void testGetCommand(String filename, int offset, int length) {
        try (Socket socket = new Socket("localhost", 5050);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream()) {
             
            String command = "GET " + filename + " " + offset + " " + length;
            System.out.println("Sending: " + command);
            out.println(command);
            
            byte[] buffer = new byte[length];
            int bytesRead = in.read(buffer);
            
            if (bytesRead > 0) {
                String payload = new String(buffer, 0, bytesRead);
                System.out.println("Received Payload: " + payload);
            } else {
                System.out.println("No payload received or error occurred.");
            }
            System.out.println("-------------------------");
            
        } catch (Exception e) {
            System.err.println("Test Failed: " + e.getMessage());
        }
    }
}