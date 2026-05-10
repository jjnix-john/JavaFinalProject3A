// ============================================================
// QuizServer.java - QuizArena Server
// DEMONSTRATES: Encapsulation, Inheritance, Polymorphism,
//               Abstract Classes, Interfaces, Java IO,
//               TCP Networking, UDP Networking, HTTP Requests
// ============================================================

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// ============================================================
// INTERFACE (Requirement: Interfaces)
// ============================================================
interface Scorable {
    int calculateScore(boolean correct, long responseTimeMs);
}

// ============================================================
// ABSTRACT CLASS (Requirement: Abstract Classes)
// ============================================================
abstract class Question {
    // ENCAPSULATION: private fields with getters/setters
    private String questionText;
    private String correctAnswer;

    public Question(String questionText, String correctAnswer) {
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
    }

    public String getQuestionText() { return questionText; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setQuestionText(String t) { this.questionText = t; }
    public void setCorrectAnswer(String a) { this.correctAnswer = a; }

    // Abstract methods to be overridden by subclasses (Polymorphism)
    public abstract String displayQuestion();
    public abstract boolean checkAnswer(String playerAnswer);

    // Serialize question to save in file
    public abstract String toFileString();
}

// ============================================================
// INHERITANCE: MultipleChoiceQuestion extends Question
// ============================================================
class MultipleChoiceQuestion extends Question implements Scorable {
    private String[] choices; // Raw text: ["Cat", "Dog", "Bird", "Fish"]

    public MultipleChoiceQuestion(String questionText, String correctAnswer, String[] choices) {
        super(questionText, correctAnswer);
        // Ensure choices don't have "A) " prefixes already
        this.choices = new String[choices.length];
        for (int i = 0; i < choices.length; i++) {
            String c = choices[i];
            if (c.length() > 3 && c.substring(1, 3).equals(") ")) {
                this.choices[i] = c.substring(3);
            } else {
                this.choices[i] = c;
            }
        }
    }

    public String[] getChoices() { return choices; }

    @Override
    public String displayQuestion() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=============================================");
        sb.append("\n  QUESTION: ").append(getQuestionText());
        sb.append("\n=============================================\n");
        for (int i = 0; i < choices.length; i++) {
            sb.append("  ").append((char)('A' + i)).append(") ").append(choices[i]).append("\n");
        }
        sb.append("---------------------------------------------\n");
        return sb.toString();
    }

    @Override
    public boolean checkAnswer(String playerAnswer) {
        if (playerAnswer == null) return false;
        String ans = playerAnswer.trim();
        String correct = getCorrectAnswer().trim();
        
        // Check if it matches the letter (A, B, C, D)
        if (ans.equalsIgnoreCase(correct)) return true;
        
        // Check if it matches the text of the correct choice
        try {
            int correctIdx = correct.toUpperCase().charAt(0) - 'A';
            if (correctIdx >= 0 && correctIdx < choices.length) {
                if (ans.equalsIgnoreCase(choices[correctIdx].trim())) return true;
            }
        } catch (Exception ignored) {}
        
        return false;
    }

    // INTERFACE: Scorable implementation
    @Override
    public int calculateScore(boolean correct, long responseTimeMs) {
        if (!correct) return 0;
        // Faster answer = more points (max 100, min 10)
        int timeBonus = (int) Math.max(0, 50 - responseTimeMs / 1000);
        return 50 + timeBonus;
    }

    @Override
    public String toFileString() {
        // Format: MCQ|question|answer|choiceA|choiceB|choiceC|choiceD
        return "MCQ|" + getQuestionText() + "|" + getCorrectAnswer() + "|"
                + String.join("|", choices);
    }
}

// ============================================================
// INHERITANCE: TrueFalseQuestion extends Question
// ============================================================
class TrueFalseQuestion extends Question implements Scorable {

    public TrueFalseQuestion(String questionText, String correctAnswer) {
        super(questionText, correctAnswer); // correctAnswer = "True" or "False"
    }

    // POLYMORPHISM: overrides abstract method differently from MCQ
    @Override
    public String displayQuestion() {
        return "\n=============================================" +
               "\n  QUESTION (True/False): " + getQuestionText() +
               "\n=============================================" +
               "\n  Options: True / False\n" +
               "---------------------------------------------\n";
    }

    @Override
    public boolean checkAnswer(String playerAnswer) {
        return playerAnswer.trim().equalsIgnoreCase(getCorrectAnswer().trim());
    }

    // INTERFACE: Scorable implementation (simpler scoring for T/F)
    @Override
    public int calculateScore(boolean correct, long responseTimeMs) {
        if (!correct) return 0;
        int timeBonus = (int) Math.max(0, 25 - responseTimeMs / 1000);
        return 25 + timeBonus;
    }

    @Override
    public String toFileString() {
        return "TF|" + getQuestionText() + "|" + getCorrectAnswer();
    }
}

// ============================================================
// ENCAPSULATION: Player model
// ============================================================
class Player {
    private String username;
    private String password;
    private int totalScore;
    private int gamesPlayed;

    public Player(String username, String password) {
        this.username = username;
        this.password = password;
        this.totalScore = 0;
        this.gamesPlayed = 0;
    }

    public Player(String username, String password, int totalScore, int gamesPlayed) {
        this.username = username;
        this.password = password;
        this.totalScore = totalScore;
        this.gamesPlayed = gamesPlayed;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getTotalScore() { return totalScore; }
    public int getGamesPlayed() { return gamesPlayed; }
    public void addScore(int s) { this.totalScore += s; }
    public void incrementGames() { this.gamesPlayed++; }

    public String toFileString() {
        return username + "|" + password + "|" + totalScore + "|" + gamesPlayed;
    }
}

// ============================================================
// FILE MANAGER (Requirement: Java IO)
// Handles all read/write for users, questions, scores, history
// ============================================================
class FileManager {
    private static final String USERS_FILE    = "users.txt";
    private static final String QUESTIONS_FILE = "questions.txt";
    private static final String SCORES_FILE   = "scores.txt";
    private static final String HISTORY_FILE  = "history.txt";

    // --- USER MANAGEMENT ---

    // Load all players from users.txt using BufferedReader + FileReader
    public static Map<String, Player> loadUsers() {
        Map<String, Player> users = new HashMap<>();
        File f = new File(USERS_FILE);
        if (!f.exists()) return users;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 4) {
                    Player p = new Player(parts[0], parts[1],
                            Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    users.put(parts[0], p);
                }
            }
        } catch (IOException e) {
            System.out.println("[FileManager] Error loading users: " + e.getMessage());
        }
        return users;
    }

    // Save all players to users.txt using BufferedWriter + FileWriter
    public static void saveUsers(Map<String, Player> users) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(USERS_FILE))) {
            for (Player p : users.values()) {
                bw.write(p.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("[FileManager] Error saving users: " + e.getMessage());
        }
    }

    // --- QUESTION MANAGEMENT ---

    // Load questions from questions.txt
    public static List<Question> loadQuestions() {
        List<Question> questions = new ArrayList<>();
        File f = new File(QUESTIONS_FILE);
        if (!f.exists()) return questions;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts[0].equals("MCQ") && parts.length >= 7) {
                    String[] choices = Arrays.copyOfRange(parts, 3, parts.length);
                    // POLYMORPHISM: stored as Question reference
                    Question q = new MultipleChoiceQuestion(parts[1], parts[2], choices);
                    questions.add(q);
                } else if (parts[0].equals("TF") && parts.length >= 3) {
                    Question q = new TrueFalseQuestion(parts[1], parts[2]);
                    questions.add(q);
                }
            }
        } catch (IOException e) {
            System.out.println("[FileManager] Error loading questions: " + e.getMessage());
        }
        return questions;
    }

    // Save questions list to questions.txt
    public static void saveQuestions(List<Question> questions) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(QUESTIONS_FILE))) {
            for (Question q : questions) {
                bw.write(q.toFileString());
                bw.newLine();
            }
        } catch (IOException e) {
            System.out.println("[FileManager] Error saving questions: " + e.getMessage());
        }
    }

    // --- SCORE MANAGEMENT ---

    // Append score entry to scores.txt
    public static void appendScore(String username, int score, String date) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(SCORES_FILE, true))) {
            bw.write(username + "|" + score + "|" + date);
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[FileManager] Error saving score: " + e.getMessage());
        }
    }

    // Load scores as formatted leaderboard string
    public static String loadLeaderboard() {
        Map<String, Integer> totals = new LinkedHashMap<>();
        File f = new File(SCORES_FILE);
        if (!f.exists()) return "No scores yet.";
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    totals.merge(parts[0], Integer.parseInt(parts[1]), Integer::sum);
                }
            }
        } catch (IOException e) {
            return "Error loading leaderboard.";
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(totals.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder sb = new StringBuilder();
        sb.append("=== LEADERBOARD ===\n");
        int rank = 1;
        for (Map.Entry<String, Integer> e : sorted) {
            sb.append(rank++).append(". ").append(e.getKey())
              .append(" - ").append(e.getValue()).append(" pts\n");
        }
        return sb.toString();
    }

    // --- HISTORY MANAGEMENT ---

    // Append match history entry
    public static void appendHistory(String entry) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HISTORY_FILE, true))) {
            bw.write(entry);
            bw.newLine();
        } catch (IOException e) {
            System.out.println("[FileManager] Error saving history: " + e.getMessage());
        }
    }

    // Load match history for a specific player
    public static String loadHistory(String username) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MATCH HISTORY: ").append(username).append(" ===\n");
        File f = new File(HISTORY_FILE);
        if (!f.exists()) return sb.append("No history found.\n").toString();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            boolean found = false;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(username + "|")) {
                    sb.append(line.replace("|", " | ")).append("\n");
                    found = true;
                }
            }
            if (!found) sb.append("No history found.\n");
        } catch (IOException e) {
            sb.append("Error loading history.\n");
        }
        return sb.toString();
    }
}

// ============================================================
// HTTP FETCHER (Requirement: HTTP Requests using HttpURLConnection)
// Fetches trivia questions from Open Trivia DB API
// ============================================================
class TriviaFetcher {
    private static final String API_URL =
        "https://opentdb.com/api.php?amount=5&difficulty=easy&type=multiple";

    // Returns list of questions fetched from API, or empty list on failure
    public static List<Question> fetchFromAPI() {
        // DISABLING API FETCH TO USE MANUAL QUESTIONS AS REQUESTED
        return new ArrayList<>();
    }

    // Simple manual parser for Open Trivia DB JSON response
    private static List<Question> parseOpenTDBJson(String json) {
        List<Question> questions = new ArrayList<>();
        try {
            // Split by "question" key occurrences to find each item
            String[] items = json.split("\"question\":");
            for (int i = 1; i < items.length; i++) {
                String chunk = items[i];

                String qText = extractValue(chunk, "question");
                String correct = extractValue(chunk, "correct_answer");

                // Gather incorrect answers
                List<String> choices = new ArrayList<>();
                choices.add("A) " + decodeHtml(correct));

                String incPart = chunk;
                int idx = incPart.indexOf("\"incorrect_answers\":");
                if (idx >= 0) {
                    incPart = incPart.substring(idx);
                    String[] parts = incPart.split("\"");
                    char letter = 'B';
                    for (String p : parts) {
                        p = p.trim();
                        if (!p.isEmpty() && !p.startsWith("[") && !p.startsWith("]")
                                && !p.contains("incorrect") && !p.contains(":") && p.length() > 1) {
                            choices.add(letter + ") " + decodeHtml(p));
                            letter++;
                            if (letter > 'D') break;
                        }
                    }
                }
                Collections.shuffle(choices);
                // Find which letter is the correct answer after shuffle
                String corrLetter = "A";
                for (String c : choices) {
                    if (c.contains(decodeHtml(correct))) {
                        corrLetter = String.valueOf(c.charAt(0));
                        break;
                    }
                }
                questions.add(new MultipleChoiceQuestion(
                    decodeHtml(qText), corrLetter, choices.toArray(new String[0])));
            }
        } catch (Exception e) {
            System.out.println("[HTTP] JSON parse error: " + e.getMessage());
        }
        return questions;
    }

    private static String extractValue(String chunk, String key) {
        int start = chunk.indexOf("\"" + key + "\":");
        if (start < 0) return "";
        start = chunk.indexOf("\"", start + key.length() + 3);
        int end = chunk.indexOf("\"", start + 1);
        if (start < 0 || end < 0) return "";
        return chunk.substring(start + 1, end);
    }

    // Decode common HTML entities from API response
    private static String decodeHtml(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#039;", "'").replace("&eacute;", "e")
                .replace("&ldquo;", "\"").replace("&rdquo;", "\"");
    }
}

// ============================================================
// UDP BROADCASTER (Requirement: UDP Networking)
// Sends announcements to all clients via UDP broadcast
// ============================================================
class UDPBroadcaster {
    private DatagramSocket socket;
    private int broadcastPort;

    public UDPBroadcaster(int port) {
        this.broadcastPort = port;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
        } catch (SocketException e) {
            System.out.println("[UDP] Failed to create broadcaster: " + e.getMessage());
        }
    }

    // Broadcast a message to all clients on the network
    public void broadcast(String message) {
        if (socket == null) return;
        try {
            byte[] data = message.getBytes();
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, broadcastPort);
            socket.send(packet);
            System.out.println("[UDP] Broadcasted: " + message);
        } catch (IOException e) {
            System.out.println("[UDP] Broadcast error: " + e.getMessage());
        }
    }

    // Countdown broadcast (e.g., "Match starts in 3 seconds")
    public void broadcastCountdown(int seconds) {
        for (int i = seconds; i >= 1; i--) {
            broadcast("Match starts in " + i + " second(s)...");
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        broadcast("GO! Match has started!");
    }

    public void close() {
        if (socket != null) socket.close();
    }
}

// ============================================================
// QUIZ SESSION: manages a quiz battle between players
// ============================================================
class QuizSession {
    private List<Question> questions;
    private List<ClientHandler> handlers;
    private UDPBroadcaster udpBroadcaster;
    private Map<String, Integer> sessionScores = new HashMap<>();

    public QuizSession(List<Question> questions, List<ClientHandler> handlers,
                       UDPBroadcaster udpBroadcaster) {
        this.questions = questions;
        this.handlers = handlers;
        this.udpBroadcaster = udpBroadcaster;
        for (ClientHandler h : handlers) {
            sessionScores.put(h.getLoggedInUser(), 0);
            // CLEAR STRAY INPUT before battle starts
            try {
                while (h.getIn().ready()) {
                    h.getIn().readLine();
                }
            } catch (IOException ignored) {}
        }
    }

    public Map<String, Integer> getSessionScores() { return sessionScores; }

    // Run the full quiz battle session
    public void run() {
        broadcast("=== QUIZ BATTLE STARTED ===");
        List<String> names = new ArrayList<>();
        for (ClientHandler h : handlers) names.add(h.getLoggedInUser());
        broadcast("Players: " + String.join(" vs ", names));

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            broadcast("\n--- Question " + (i + 1) + " of " + questions.size() + " ---");
            broadcast(q.displayQuestion());
            broadcast("Your answer: ");

            final long questionStartTime = System.currentTimeMillis();
            Map<String, String> playerAnswers = Collections.synchronizedMap(new HashMap<>());
            Map<String, Long> playerTimes = Collections.synchronizedMap(new HashMap<>());

            // Create threads to collect answers in parallel
            List<Thread> threads = new ArrayList<>();
            for (ClientHandler h : handlers) {
                Thread t = new Thread(() -> {
                    try {
                        String ans = h.getIn().readLine();
                        if (ans != null) {
                            playerAnswers.put(h.getLoggedInUser(), ans);
                            playerTimes.put(h.getLoggedInUser(), System.currentTimeMillis() - questionStartTime);
                        }
                    } catch (IOException ignored) {}
                });
                threads.add(t);
                t.start();
            }

            // Wait for all players to answer (max 15 seconds)
            long waitStart = System.currentTimeMillis();
            while (playerAnswers.size() < handlers.size() && (System.currentTimeMillis() - waitStart) < 15000) {
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }

            // Interrupt any threads still waiting
            for (Thread t : threads) {
                if (t.isAlive()) t.interrupt();
            }

            // Check answers and update scores individually
            for (ClientHandler h : handlers) {
                String name = h.getLoggedInUser();
                String ans = playerAnswers.getOrDefault(name, "");
                long timeTaken = playerTimes.getOrDefault(name, 15000L);
                
                boolean correct = q.checkAnswer(ans);
                int pts = 0;
                if (q instanceof Scorable) {
                    pts = ((Scorable) q).calculateScore(correct, timeTaken);
                }
                sessionScores.merge(name, pts, Integer::sum);

                String result = correct ? "CORRECT! +" + pts + " pts" : "WRONG! Answer: " + q.getCorrectAnswer();
                h.getOut().println(result);
                h.getOut().println("Your score so far: " + sessionScores.get(name));
                h.getOut().flush();
            }
        }
        
        // Announce final results
        broadcast("\n=== BATTLE OVER ===");
        StringBuilder results = new StringBuilder("FINAL SCORES:\n");
        sessionScores.entrySet().stream()
            .sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> results.append(e.getKey()).append(": ").append(e.getValue()).append(" pts\n"));
        broadcast(results.toString());
    }

    private void broadcast(String msg) {
        for (ClientHandler h : handlers) {
            h.getOut().println(msg);
            h.getOut().flush();
        }
    }
}

// ============================================================
// CLIENT HANDLER: handles one connected TCP client in a thread
// ============================================================
class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Map<String, Player> users;
    private List<Question> questions;
    private QuizServer server; // reference to server for matchmaking
    private String loggedInUser = null;
    private volatile boolean isBusy = false; // Flag to prevent reading while in battle

    public ClientHandler(Socket socket, Map<String, Player> users,
                         List<Question> questions, QuizServer server) {
        this.socket = socket;
        this.users = users;
        this.questions = questions;
        this.server = server;
        try {
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("[Server] Error setting up client handler: " + e.getMessage());
        }
    }

    public String getLoggedInUser() { return loggedInUser; }
    public PrintWriter getOut() { return out; }
    public BufferedReader getIn() { return in; }
    public void setBusy(boolean busy) { this.isBusy = busy; }
    public boolean isBusy() { return isBusy; }

    @Override
    public void run() {
        try {
            showMainMenu();
            String input;
            while ((input = in.readLine()) != null) {
                if (loggedInUser == null) {
                    handleAuth(input);
                } else {
                    handleMainMenu(input);
                }
                // If the client just joined matchmaking, wait here until the match is over
                if (isBusy) {
                    while (isBusy) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    }
                    if (loggedInUser != null) {
                        showGameMenu(); // ONLY re-show here after a battle finishes
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("[Server] Client disconnected: " + (loggedInUser != null ? loggedInUser : "unknown"));
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void showMainMenu() {
        out.println("\n=========================");
        out.println("      QUIZ ARENA");
        out.println("=========================");
        out.println("1. Login");
        out.println("2. Register");
        out.println("3. Exit");
        out.println("Choice: ");
        out.flush();
    }

    private void handleAuth(String choice) throws IOException {
        switch (choice.trim()) {
            case "1": // Login
                out.println("Username: ");
                String uname = in.readLine();
                out.println("Password: ");
                String pass = in.readLine();
                if (users.containsKey(uname) && users.get(uname).getPassword().equals(pass)) {
                    loggedInUser = uname;
                    out.println("LOGIN_SUCCESS");
                    out.println("Welcome back, " + uname + "!");
                    showGameMenu();
                } else {
                    out.println("Invalid credentials. Try again.");
                    showMainMenu();
                }
                break;
            case "2": // Register
                out.println("Choose a username: ");
                String newUser = in.readLine();
                out.println("Choose a password: ");
                String newPass = in.readLine();
                if (users.containsKey(newUser)) {
                    out.println("Username already taken. Try again.");
                } else {
                    Player p = new Player(newUser, newPass);
                    users.put(newUser, p);
                    FileManager.saveUsers(users); // Save to file immediately
                    out.println("REGISTER_SUCCESS");
                    out.println("Account created! You can now login.");
                }
                showMainMenu();
                break;
            case "3": // Exit
                out.println("Goodbye!");
                socket.close();
                break;
            default:
                out.println("Invalid choice.");
                showMainMenu();
        }
    }

    private void showGameMenu() {
        out.println("\n=========================");
        out.println("  Welcome, " + loggedInUser);
        out.println("=========================");
        out.println("1. Join Battle");
        out.println("2. Leaderboard");
        out.println("3. Match History");
        
        // SIMPLE ADMIN CHECK: only 'admin' can see these
        if ("admin".equalsIgnoreCase(loggedInUser)) {
            out.println("4. Admin: Add Question");
            out.println("5. Admin: Remove Question");
        }
        
        out.println("6. Logout");
        out.println("Choice: ");
        out.flush();
    }

    private void handleMainMenu(String choice) throws IOException {
        switch (choice.trim()) {
            case "1": // Join Battle
                out.println("Joining matchmaking... Waiting for opponent...");
                isBusy = true; // Mark as busy BEFORE joining matchmaking
                server.joinMatchmaking(this);
                break;
            case "2": // Leaderboard
                out.println(FileManager.loadLeaderboard());
                showGameMenu();
                break;
            case "3": // Match History
                out.println(FileManager.loadHistory(loggedInUser));
                showGameMenu();
                break;
            case "4": // Admin: Add Question
                if ("admin".equalsIgnoreCase(loggedInUser)) {
                    handleAddQuestion();
                } else {
                    out.println("Access denied. Admin only.");
                }
                showGameMenu();
                break;
            case "5": // Admin: Remove Question
                if ("admin".equalsIgnoreCase(loggedInUser)) {
                    handleRemoveQuestion();
                } else {
                    out.println("Access denied. Admin only.");
                }
                showGameMenu();
                break;
            case "6": // Logout
                out.println("Logged out. Goodbye, " + loggedInUser + "!");
                loggedInUser = null;
                showMainMenu();
                break;
            default:
                out.println("Invalid choice.");
                showGameMenu();
        }
    }

    private void handleAddQuestion() throws IOException {
        out.println("Question type (1=MCQ, 2=TrueFalse): ");
        String type = in.readLine();
        out.println("Enter question text: ");
        String qText = in.readLine();
        out.println("Enter correct answer: ");
        String cAnswer = in.readLine();

        if ("1".equals(type)) {
            String[] choices = new String[4];
            for (int i = 0; i < 4; i++) {
                out.println("Enter choice " + (char)('A' + i) + ": ");
                choices[i] = in.readLine(); // Store raw text
            }
            questions.add(new MultipleChoiceQuestion(qText, cAnswer, choices));
        } else {
            questions.add(new TrueFalseQuestion(qText, cAnswer));
        }
        FileManager.saveQuestions(questions);
        out.println("Question added successfully!");
    }

    private void handleRemoveQuestion() throws IOException {
        if (questions.isEmpty()) {
            out.println("No questions available.");
            return;
        }
        out.println("Current Questions:");
        for (int i = 0; i < questions.size(); i++) {
            out.println((i + 1) + ". " + questions.get(i).getQuestionText());
        }
        out.println("Enter question number to remove (0 to cancel): ");
        try {
            int num = Integer.parseInt(in.readLine().trim());
            if (num > 0 && num <= questions.size()) {
                questions.remove(num - 1);
                FileManager.saveQuestions(questions);
                out.println("Question removed.");
            } else {
                out.println("Cancelled.");
            }
        } catch (NumberFormatException e) {
            out.println("Invalid input.");
        }
    }
}

// ============================================================
// QUIZ SERVER MAIN CLASS (Requirement: TCP Server, UDP, Threads)
// ============================================================
public class QuizServer {
    private static final int TCP_PORT = 9090;
    private static final int UDP_PORT = 9091;

    private Map<String, Player> users;
    private List<Question> questions;
    private UDPBroadcaster udpBroadcaster;

    // Matchmaking queue
    private final Queue<ClientHandler> matchQueue = new LinkedList<>();
    private final Object matchLock = new Object();

    private List<ClientHandler> allClients = Collections.synchronizedList(new ArrayList<>());

    public QuizServer() {
        // Clear files to fix corrupted question state
        new File("questions.txt").delete();
        
        // Load existing users
        users = FileManager.loadUsers();
        
        // Always start with fresh elementary questions
        questions = new ArrayList<>();
        addDefaultQuestions();

        // Start UDP broadcaster
        udpBroadcaster = new UDPBroadcaster(UDP_PORT);
    }

    private void addDefaultQuestions() {
        // ELEMENTARY LEVEL QUESTIONS
        questions.add(new MultipleChoiceQuestion(
            "What color is the sky on a clear day?", "A",
            new String[]{"A) Blue", "B) Green", "C) Red", "D) Yellow"}));
            
        questions.add(new TrueFalseQuestion(
            "The sun rises in the morning.", "True"));
            
        questions.add(new MultipleChoiceQuestion(
            "How many legs does a dog have?", "B",
            new String[]{"A) 2", "B) 4", "C) 6", "D) 8"}));
            
        questions.add(new MultipleChoiceQuestion(
            "Which of these is a fruit?", "C",
            new String[]{"A) Carrot", "B) Potato", "C) Apple", "D) Broccoli"}));
            
        questions.add(new TrueFalseQuestion(
            "Fish live in the water.", "True"));
            
        questions.add(new MultipleChoiceQuestion(
            "What is 2 + 2?", "D",
            new String[]{"A) 1", "B) 2", "C) 3", "D) 4"}));

        FileManager.saveQuestions(questions);
        System.out.println("[Server] Elementary manual questions loaded.");
    }

    // Called by ClientHandler when a player wants to join a match
    public synchronized void joinMatchmaking(ClientHandler client) {
        if (!matchQueue.contains(client)) {
            matchQueue.add(client);
        }
        client.getOut().println("You are in queue. Position: " + matchQueue.size());
        client.getOut().flush();

        // When 2 players are queued, start a match
        if (matchQueue.size() >= 2) {
            ClientHandler p1 = matchQueue.poll();
            ClientHandler p2 = matchQueue.poll();
            startMatch(p1, p2);
        }
    }

    private void startMatch(ClientHandler p1, ClientHandler p2) {
        // Double check if both are still connected/busy
        if (p1 == null || p2 == null) return;
        
        // UDP countdown broadcast (UDP Requirement)
        new Thread(() -> udpBroadcaster.broadcastCountdown(3)).start();

        p1.getOut().println("Opponent found: " + p2.getLoggedInUser());
        p2.getOut().println("Opponent found: " + p1.getLoggedInUser());
        p1.getOut().flush();
        p2.getOut().flush();

        // Select random questions for this match (up to 5)
        List<Question> matchQ = new ArrayList<>(questions);
        Collections.shuffle(matchQ);
        List<Question> sessionQ = matchQ.subList(0, Math.min(5, matchQ.size()));

        // Run quiz session in a new thread so server stays responsive
        new Thread(() -> {
            List<ClientHandler> handlers = Arrays.asList(p1, p2);
            QuizSession session = new QuizSession(sessionQ, handlers, udpBroadcaster);
            session.run();

            // Save results
            String date = new java.util.Date().toString();
            Map<String, Integer> scores = session.getSessionScores();
            List<String> names = Arrays.asList(p1.getLoggedInUser(), p2.getLoggedInUser());
            for (String name : names) {
                int pts = scores.getOrDefault(name, 0);
                FileManager.appendScore(name, pts, date);
                FileManager.appendHistory(name + "|" + pts + " pts|vs " +
                    (name.equals(names.get(0)) ? names.get(1) : names.get(0)) + "|" + date);
                if (users.containsKey(name)) {
                    users.get(name).addScore(pts);
                    users.get(name).incrementGames();
                }
            }
            FileManager.saveUsers(users);

            // Return players to main menu
            p1.getOut().println("\nReturning to main menu...");
            p2.getOut().println("\nReturning to main menu...");
            
            // Allow ClientHandler threads to resume reading
            p1.setBusy(false);
            p2.setBusy(false);
        }).start();
    }

    public synchronized void removeClient(ClientHandler c) {
        allClients.remove(c);
        matchQueue.remove(c);
    }

    // Start the TCP server (TCP Requirement)
    public void start() {
        System.out.println("╔════════════════════════════╗");
        System.out.println("║   QUIZ ARENA SERVER v1.0   ║");
        System.out.println("╠════════════════════════════╣");
        System.out.println("║ TCP Port : " + TCP_PORT + "             ║");
        System.out.println("║ UDP Port : " + UDP_PORT + "             ║");
        System.out.println("╚════════════════════════════╝");

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[Server] Listening for clients...\n");
            udpBroadcaster.broadcast("QuizArena Server is now ONLINE!");

            while (true) {
                Socket clientSocket = serverSocket.accept(); // TCP accept
                System.out.println("[Server] New client: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, users, questions, this);
                allClients.add(handler);
                new Thread(handler).start(); // Each client gets its own thread
            }
        } catch (IOException e) {
            System.out.println("[Server] Fatal error: " + e.getMessage());
        } finally {
            udpBroadcaster.close();
        }
    }

    public static void main(String[] args) {
        new QuizServer().start();
    }
}
