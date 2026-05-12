// ============================================================
// QuizServer.java - Simple Quiz System
// DEMONSTRATES: OOP (Encapsulation, Inheritance, Polymorphism),
//               Networking (TCP, UDP, HTTP), File I/O
// ============================================================

import java.io.*;
import java.net.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 1. INTERFACE (Requirement: Interfaces)
// Defines a contract for objects that can calculate a score.
interface Scorable {
    int calculateScore(boolean isCorrect, long timeTakenMs);
}

// 2. ABSTRACT CLASS (Requirement: Abstract Classes)
// Base class for all question types. Demonstrates Encapsulation.
abstract class Question {
    private String questionText;
    private String correctAnswer;

    public Question(String questionText, String correctAnswer) {
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
    }

    // Getters and Setters (Encapsulation)
    public String getQuestionText() { return questionText; }
    public String getCorrectAnswer() { return correctAnswer; }

    // Abstract methods to be implemented by subclasses (Polymorphism)
    public abstract String formatQuestion();
    public abstract boolean isAnswerCorrect(String userAnswer);
    public abstract String toFileFormat();
}

// 3. INHERITANCE: MultipleChoiceQuestion extends Question
class MultipleChoiceQuestion extends Question implements Scorable {
    private String[] options;

    public MultipleChoiceQuestion(String text, String answer, String[] options) {
        super(text, answer);
        this.options = options;
    }

    @Override
    public String formatQuestion() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n+--------------------------------------------------+");
        sb.append("\n  QUESTION: ").append(getQuestionText());
        sb.append("\n+--------------------------------------------------+\n");
        for (int i = 0; i < options.length; i++) {
            sb.append("  ").append((char)('A' + i)).append(") ").append(options[i]).append("\n");
        }
        sb.append("+--------------------------------------------------+");
        return sb.toString();
    }

    @Override
    public boolean isAnswerCorrect(String userAnswer) {
        if (userAnswer == null) return false;
        String input = userAnswer.trim().toUpperCase();
        // Check if user typed 'A', 'B', etc. or the full text
        if (input.equals(getCorrectAnswer().toUpperCase())) return true;
        
        // Also check if they typed the text of the correct option
        try {
            int correctIndex = getCorrectAnswer().toUpperCase().charAt(0) - 'A';
            if (correctIndex >= 0 && correctIndex < options.length) {
                if (input.equalsIgnoreCase(options[correctIndex])) return true;
            }
        } catch (Exception ignored) {}
        
        return false;
    }

    @Override
    public int calculateScore(boolean isCorrect, long timeTakenMs) {
        if (!isCorrect) return 0;
        // Base points + speed bonus
        int speedBonus = (int) Math.max(0, 10 - (timeTakenMs / 1000));
        return 10 + speedBonus;
    }

    @Override
    public String toFileFormat() {
        return "MCQ|" + getQuestionText() + "|" + getCorrectAnswer() + "|" + String.join("|", options);
    }
}

// 4. INHERITANCE: TrueFalseQuestion extends Question
class TrueFalseQuestion extends Question implements Scorable {
    public TrueFalseQuestion(String text, String answer) {
        super(text, answer);
    }

    @Override
    public String formatQuestion() {
        return "\n+----------------------------------------------+" +
               "\n|  TRUE/FALSE: " + String.format("%-31s", getQuestionText()) + "|" +
               "\n+----------------------------------------------+" +
               "\n|  > True                                      |" +
               "\n|  > False                                     |" +
               "\n+----------------------------------------------+\n";
    }

    @Override
    public boolean isAnswerCorrect(String userAnswer) {
        if (userAnswer == null) return false;
        String input = userAnswer.trim().toLowerCase();
        String correct = getCorrectAnswer().toLowerCase();
        
        // Handle 't'/'f' or full words
        if (input.equals("t") && correct.startsWith("t")) return true;
        if (input.equals("f") && correct.startsWith("f")) return true;
        return input.equals(correct);
    }

    @Override
    public int calculateScore(boolean isCorrect, long timeTakenMs) {
        return isCorrect ? 10 : 0; // Simple scoring for T/F
    }

    @Override
    public String toFileFormat() {
        return "TF|" + getQuestionText() + "|" + getCorrectAnswer();
    }
}

// 5. PLAYER MODEL
class Player {
    private String username;
    private String password;
    private int score;

    public Player(String username, String password, int score) {
        this.username = username;
        this.password = password;
        this.score = score;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public int getScore() { return score; }
    public void addScore(int pts) { this.score += pts; }

    public String toFileFormat() {
        return username + "|" + password + "|" + score;
    }
}

// 6. FILE MANAGER (Requirement: File I/O)
class FileManager {
    private static final String USERS_FILE = "users.txt";
    private static final String QUESTIONS_FILE = "questions.txt";
    private static final String SCORES_FILE = "scores.txt";

    public static Map<String, Player> loadUsers() {
        Map<String, Player> users = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length == 3) {
                    users.put(p[0], new Player(p[0], p[1], Integer.parseInt(p[2])));
                }
            }
        } catch (IOException ignored) {}
        return users;
    }

    public static void saveUsers(Map<String, Player> users) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USERS_FILE))) {
            for (Player p : users.values()) pw.println(p.toFileFormat());
        } catch (IOException e) { System.out.println("Error saving users: " + e.getMessage()); }
    }

    public static List<Question> loadQuestions() {
        List<Question> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(QUESTIONS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p[0].equals("MCQ")) {
                    list.add(new MultipleChoiceQuestion(p[1], p[2], Arrays.copyOfRange(p, 3, p.length)));
                } else if (p[0].equals("TF")) {
                    list.add(new TrueFalseQuestion(p[1], p[2]));
                }
            }
        } catch (IOException ignored) {}
        
        // Fallback if file is missing or empty
        if (list.isEmpty()) {
            list.add(new MultipleChoiceQuestion("What is 2+2?", "B", new String[]{"3", "4", "5", "6"}));
            list.add(new TrueFalseQuestion("Is the sky blue?", "True"));
        }
        return list;
    }

    public static void saveScore(String user, int score) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(SCORES_FILE, true))) {
            pw.println(user + "|" + score);
        } catch (IOException ignored) {}
    }

    public static String getLeaderboard() {
        List<String[]> scores = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(SCORES_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\|");
                if (p.length >= 2) scores.add(p);
            }
        } catch (IOException ignored) { return "No scores yet."; }

        if (scores.isEmpty()) return "No scores yet.";

        // Sort by score (index 1) descending
        scores.sort((a, b) -> Integer.parseInt(b[1]) - Integer.parseInt(a[1]));

        StringBuilder sb = new StringBuilder();
        sb.append("\n+------------------------------------------+");
        sb.append("\n|             LEADERBOARD                  |");
        sb.append("\n+------------------------------------------+\n");
        int rank = 1;
        for (String[] s : scores) {
            String row = String.format("%d. %-20s - %4s pts", rank++, s[0], s[1]);
            sb.append("| ").append(String.format("%-41s", row)).append("|\n");
            if (rank > 10) break; // Top 10 only
        }
        sb.append("+------------------------------------------+\n");
        return sb.toString();
    }
}

// 7. HTTP FETCHER (Requirement: HTTP Requests)
class TriviaFetcher {
    public static List<Question> fetchTrivia() {
        List<Question> questions = new ArrayList<>();
        try {
            URL url = new URL("https://opentdb.com/api.php?amount=8&type=multiple");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) json.append(line);
            in.close();

            // Simple manual parsing
            String content = json.toString();
            String[] items = content.split("\"question\":\"");
            for (int i = 1; i < items.length; i++) {
                String qChunk = items[i];
                String qText = qChunk.split("\"")[0]
                    .replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&");
                
                String correct = qChunk.split("\"correct_answer\":\"")[1].split("\"")[0]
                    .replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&");

                // Extract incorrect answers
                List<String> options = new ArrayList<>();
                options.add(correct);
                if (qChunk.contains("\"incorrect_answers\":[")) {
                    String incPart = qChunk.split("\"incorrect_answers\":\\[")[1].split("\\]")[0];
                    String[] incs = incPart.split(",");
                    for (String inc : incs) {
                        options.add(inc.replace("\"", "").replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&"));
                        if (options.size() >= 4) break;
                    }
                }
                
                Collections.shuffle(options);
                
                // Find correct answer letter
                String correctLetter = "A";
                for (int j = 0; j < options.size(); j++) {
                    if (options.get(j).equals(correct)) {
                        correctLetter = String.valueOf((char)('A' + j));
                        break;
                    }
                }
                
                questions.add(new MultipleChoiceQuestion(qText, correctLetter, options.toArray(new String[0])));
            }
        } catch (Exception e) { 
            System.out.println("HTTP Fetch failed: " + e.getMessage()); 
        }
        return questions;
    }
}

// 8. UDP BROADCASTER (Requirement: UDP Networking)
class UDPBroadcaster {
    public static void broadcast(String msg, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), port);
            socket.send(packet);
        } catch (Exception e) { System.out.println("UDP Broadcast failed."); }
    }
}

// 9. CLIENT HANDLER (TCP Logic)
class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Map<String, Player> users;
    private Player currentUser;

    public ClientHandler(Socket socket, Map<String, Player> users) {
        this.socket = socket;
        this.users = users;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (currentUser == null) {
                    showAuthMenu();
                } else {
                    showGameMenu();
                }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        }
    }

    private void showAuthMenu() throws IOException {
        out.println("\n+---------------------------+");
        out.println("|        QUIZ ARENA         |");
        out.println("+---------------------------+");
        out.println("|  1. Login                 |");
        out.println("|  2. Register              |");
        out.println("|  3. Exit                  |");
        out.println("+---------------------------+");
        out.println("Choice: ");
        String choice = in.readLine();
        if ("1".equals(choice)) {
            out.println("Username: "); String u = in.readLine();
            out.println("Password: "); String p = in.readLine();
            if (users.containsKey(u) && users.get(u).getPassword().equals(p)) {
                currentUser = users.get(u);
                out.println("\n[SUCCESS] Login Success! Welcome, " + u);
            } else out.println("\n[ERROR] Invalid login credentials.");
        } else if ("2".equals(choice)) {
            out.println("New Username: "); String u = in.readLine();
            out.println("New Password: "); String p = in.readLine();
            if (!users.containsKey(u)) {
                users.put(u, new Player(u, p, 0));
                FileManager.saveUsers(users);
                out.println("\n[SUCCESS] Registered successfully!");
            } else out.println("\n[ERROR] Username already taken.");
        } else if ("3".equals(choice)) {
            out.println("Goodbye! Thanks for playing.");
            socket.close();
            throw new IOException();
        }
    }

    private void showGameMenu() throws IOException {
        out.println("\n+------------------------------+");
        out.println("|   PLAYER MENU: " + String.format("%-14s", currentUser.getUsername()) + "|");
        out.println("+------------------------------+");
        out.println("|  1. Start Quiz               |");
        out.println("|  2. View Leaderboard         |");
        out.println("|  3. Fetch Online Trivia      |");
        out.println("|  4. Logout                   |");
        out.println("+------------------------------+");
        out.println("Choice: ");
        String choice = in.readLine();
        switch (choice) {
            case "1": startQuiz(FileManager.loadQuestions()); break;
            case "2": out.println(FileManager.getLeaderboard()); break;
            case "3": 
                out.println("\n[FETCHING] Fetching fresh questions from the web...");
                startQuiz(TriviaFetcher.fetchTrivia()); 
                break;
            case "4": 
                out.println("\n[LOGOUT] Logging out...");
                currentUser = null; 
                break;
        }
    }

    private void startQuiz(List<Question> questions) throws IOException {
        if (questions.isEmpty()) {
            out.println("No questions available.");
            return;
        }
        
        // Notify others via UDP (Requirement)
        UDPBroadcaster.broadcast(currentUser.getUsername() + " just started a quiz!", 9091);
        
        int sessionScore = 0;
        out.println("\n--- QUIZ START ---");
        for (Question q : questions) {
            out.println(q.formatQuestion());
            out.println("Your answer: ");
            long start = System.currentTimeMillis();
            String ans = in.readLine();
            long timeTaken = System.currentTimeMillis() - start;

            boolean correct = q.isAnswerCorrect(ans);
            int pts = 0;
            if (q instanceof Scorable) {
                pts = ((Scorable) q).calculateScore(correct, timeTaken);
            }
            sessionScore += pts;
            
            if (correct) out.println("Correct! +" + pts + " points.");
            else out.println("Wrong! The answer was: " + q.getCorrectAnswer());
        }
        
        out.println("\nQuiz Over! Total Session Score: " + sessionScore);
        currentUser.addScore(sessionScore);
        FileManager.saveUsers(users);
        FileManager.saveScore(currentUser.getUsername(), sessionScore);
    }
}

// 10. MAIN SERVER CLASS
public class QuizServer {
    public static void main(String[] args) {
        int port = 9090;
        Map<String, Player> users = FileManager.loadUsers();
        System.out.println("Server started on port " + port);
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket, users)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            if (e.getMessage().contains("Address already in use")) {
                System.err.println("HINT: Another instance of the server is already running. Please close it first!");
            }
        }
    }
}
