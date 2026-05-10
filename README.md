# QuizArena - Java Final Project (3A)

A CLI-based multiplayer quiz battle system demonstrating core Java OOP and networking concepts.

## Files
- `QuizServer.java` - Server with all OOP classes, file I/O, TCP, UDP, HTTP
- `QuizClient.java` - Client with TCP connection and UDP listener

## How to Compile & Run

```bash
# Compile both files
javac QuizServer.java
javac QuizClient.java

# Run server first (in one terminal)
java QuizServer

# Run two clients (in two separate terminals)
java QuizClient
java QuizClient
```

## OOP Requirements Checklist
| Requirement        | Location                          |
|--------------------|-----------------------------------|
| Encapsulation      | `Player`, `Question` fields/getters |
| Abstract Class     | `abstract class Question`         |
| Inheritance        | `MultipleChoiceQuestion`, `TrueFalseQuestion` |
| Polymorphism       | `Question q = new MultipleChoiceQuestion()` |
| Interface          | `interface Scorable` + `calculateScore()` |
| Java IO            | `FileManager` - users/questions/scores/history.txt |
| TCP Networking     | `ServerSocket`/`Socket` in QuizServer/QuizClient |
| UDP Networking     | `UDPBroadcaster` (server) + `UDPListener` (client) |
| HTTP Requests      | `TriviaFetcher` → opentdb.com API |

## Data Files (auto-created)
- `users.txt` - registered players
- `questions.txt` - quiz questions
- `scores.txt` - score history
- `history.txt` - match history
