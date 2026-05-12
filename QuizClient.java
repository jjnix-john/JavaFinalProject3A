// ============================================================
// QuizClient.java - Simple Quiz Client
// DEMONSTRATES: TCP connection, UDP listener, CLI menus
// ============================================================

import java.io.*;
import java.net.*;
import java.util.Scanner;

// UDP LISTENER (Requirement: UDP Networking on client)
// Listens for announcements from the server on port 9091
class UDPListener implements Runnable {
    private int port;
    public UDPListener(int port) { this.port = port; }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                System.out.println("\n[SERVER ANNOUNCEMENT] " + msg);
            }
        } catch (IOException e) {
            // This happens if the port is busy and reuseAddress isn't supported/working
            System.out.println("UDP Listener could not start on port " + port);
        }
    }
}

public class QuizClient {
    private static final String HOST = "localhost";
    private static final int TCP_PORT = 9090;
    private static final int UDP_PORT = 9091;

    public static void main(String[] args) {
        System.out.println("--- Welcome to QuizArena ---");
        
        // Start UDP listener in the background
        new Thread(new UDPListener(UDP_PORT)).start();

        try (Socket socket = new Socket(HOST, TCP_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             Scanner scanner = new Scanner(System.in)) {

            String serverMessage;
            // Main loop: read everything from server
            while ((serverMessage = in.readLine()) != null) {
                System.out.println(serverMessage);

                // If the server message expects an input (ends with ": ")
                if (serverMessage.endsWith(": ")) {
                    if (scanner.hasNextLine()) {
                        String input = scanner.nextLine();
                        out.println(input);
                    }
                }

                // Close client if server says goodbye
                if (serverMessage.toLowerCase().contains("goodbye")) break;
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server: " + e.getMessage());
        }
    }
}
