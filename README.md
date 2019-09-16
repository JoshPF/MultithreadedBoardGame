# MultithreadedBoardGame

A multiplayer, scrabble-esque board game implemented using a multi-threaded TCP Server/Client(s) pair in Java, with synchronization and challenge-response authentication using RSA and a session key.
Players participate by placing letters on the board and earning points based on the letter used. Letters can only be placed on top of `.` or `:` spaces, and only when they're placed next to an existing letter on the board. A `#` indicates a space where a letter can never be placed. An `o` indicates a location where a letter can be placed initially, meaning no existing letters are required to be adjacent to the placed letter. Additionally, placing a letter on top of a `:` space will result in double the points being awarded.
The initial configuration of the board itself is determined via the input file `board.txt`.

## Steps to Run Program
0. Ensure you're in the proper directory using `pwd`. You should be in the `MultithreadedBoardGame/src` directory.
1. Compile both the Server and Client code using `javac Server.java` and `javac Client.java`
2. Run the server program in one Terminal window using `java Server`
3. In another Terminal window (on the same machine or a different one), run the client program using `java Client <your hostname here>`.
4. Enter the Username of an existing user (full list of users located in `input/` directory; NOTE: the user `becky` is a test user that is meant to fail)

## Commands
| Command  |  Description |
|---|---|
| `board` | Displays the contents of the board along with every user's current score |
| `place <letter> <row> <column>` | Places a specified letter on the board at the given row and column |
| `exit` | Exits the game and terminates the client, who can choose to later reconnect if they so wish. |

## Scoring
| Letter(s) | Point Value |
|---|---|
| a, e, i, o, u, n, r, s, t, l | 1 |
| d, g | 2 |
| b, c, m, p | 3 |
| f, h, v, w, y | 4 |
| k | 5 |
| j, x | 8 |
| q, z | 10 |
