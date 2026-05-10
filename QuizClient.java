// ============================================================
// QuizClient.java - QuizArena Client
// DEMONSTRATES: TCP connection, UDP listener, CLI menus
// ============================================================

import java.io.*;
import java.net.*;
import java.util.Scanner;

// ============================================================
// UDP LISTENER THREAD (Requirement: UDP Networking on client)
// Listens for broadcast announcements from the server
// ============================================================
class UDPListener implements Runnable {
    private int port;
    private volatile boolean running = true;

    public UDPListener(int port) {
        this.port = port;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(null)) {
            // Allow multiple clients on the same machine to listen on same port
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            socket.setSoTimeout(2000); // 2 second timeout so we can check running flag

            byte[] buffer = new byte[1024];
            System.out.println("[UDP] Listening for broadcast announcements on port " + port);

            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    // Print announcement clearly
                    System.out.println("\n>>> " + message + " <<<\n");
                } catch (SocketTimeoutException ignored) {
                    // Timeout just means no broadcast received; loop again
                }
            }
        } catch (IOException e) {
            if (running) System.out.println("[UDP] Listener error: " + e.getMessage());
        }
    }
}

// ============================================================
// QUIZ CLIENT MAIN CLASS (Requirement: TCP Client)
// ============================================================
public class QuizClient {
    private static final String SERVER_HOST = "localhost";
    private static final int TCP_PORT = 9090;
    private static final int UDP_PORT = 9091;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner = new Scanner(System.in);
    private boolean loggedIn = false;
    private String username = "";
    private UDPListener udpListener;
    private Thread udpThread;

    // Connect to the server via TCP
    public void connect() {
        try {
            System.out.println("Connecting to QuizArena server...");
            socket = new Socket(SERVER_HOST, TCP_PORT); // TCP Connection
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected!\n");

            // Start UDP listener in background thread (UDP Requirement)
            udpListener = new UDPListener(UDP_PORT);
            udpThread = new Thread(udpListener);
            udpThread.setDaemon(true); // Stops automatically when main thread ends
            udpThread.start();

            // Start the main client loop
            run();
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
            System.out.println("Make sure the server is running on port " + TCP_PORT);
        } finally {
            if (udpListener != null) udpListener.stop();
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    // Main loop: read lines from server and respond
    private void run() throws IOException {
        String serverLine;
        while ((serverLine = in.readLine()) != null) {
            // Handle special server signals
            if (serverLine.equals("LOGIN_SUCCESS")) {
                loggedIn = true;
                continue; // Skip, next line is welcome message
            }
            if (serverLine.equals("REGISTER_SUCCESS")) {
                continue; // Skip, next line is confirmation
            }

            System.out.println(serverLine);

            // If server is asking for input (ends with ": " or "Choice: ")
            if (serverLine.endsWith(": ") || serverLine.endsWith("Choice: ")
                    || serverLine.endsWith("answer: ")) {
                try {
                    if (scanner.hasNextLine()) {
                        String userInput = scanner.nextLine();
                        out.println(userInput);
                        out.flush();
                    }
                } catch (Exception e) {
                    // Silently handle scanner issues
                }
            }

            // If server says "Joining matchmaking", enter battle mode
            if (serverLine.startsWith("Joining matchmaking")) {
                System.out.println("[System] Searching for a battle opponent...");
                handleBattleMode();
            }

            // If we're disconnected or told goodbye
            if (serverLine.toLowerCase().contains("goodbye")) {
                System.out.println("Disconnected from server.");
                break;
            }
        }
    }

    // Handle the quiz battle phase: receive questions and send answers
    private void handleBattleMode() throws IOException {
        System.out.println("[Battle Mode Active - Answer questions as they appear]");
        String line;
        while ((line = in.readLine()) != null) {
            System.out.println(line);

            // Battle over signal
            if (line.contains("Returning to main menu")) {
                System.out.println("\n[Returning to main menu...]\n");
                return;
            }

            // Server is asking for our answer
            if (line.equals("Your answer: ")) {
                System.out.print(">>> Type your answer (Letter or Text): ");
                try {
                    if (scanner.hasNextLine()) {
                        String ans = scanner.nextLine();
                        out.println(ans);
                        out.flush();
                    } else {
                        out.println(""); 
                    }
                } catch (Exception e) {
                    out.println("");
                }
            }
        }
    }

    // Entry point
    public static void main(String[] args) {
        // Show startup banner
        System.out.println("╔════════════════════════════╗");
        System.out.println("║   QUIZ ARENA CLIENT v1.0   ║");
        System.out.println("╠════════════════════════════╣");
        System.out.println("║  Connecting to server...   ║");
        System.out.println("╚════════════════════════════╝\n");

        new QuizClient().connect();
    }
}
