/**
 * This program acts as a Server in the board game. The Client logs in as a user, who is
 * authenticated via an RSA public/private key pair, and begins entering commands that are
 * recognized and handled by this Server program.
 * @file Server.java
 * @author Josh Ferrero (jpferrer)
 */
import java.util.Map;
import java.util.HashMap;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.Random;
import java.util.ArrayList;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.concurrent.Semaphore;

/** 
 * A server that keeps up with a public key for every user, along
 * with a board for placing letters, like scrabble. 
 */
public class Server {
  /** Port number used by the server */
  public static final int PORT_NUMBER = 26114;

  /** Original state of the board, for resetting at the start of a game. */
  private char[][] template;

  /** Current board, a 2D array of characters. */
  private char[][] board;

  /** A map of all characters mapped with the score associated with them. */
  private Map< Character, Integer > scoreSheet = new HashMap< Character, Integer >();

  /** Record for an individual user. */
  private static class UserRec {
    // Name of this user.
    String name;

    // This user's public key.
    PublicKey publicKey;

    // Current score for this users.
    int score;
  }

  /**
   * A semaphore to protect critical sections of data during multithreaded instances of
   * the board game
   */
  private Semaphore s = new Semaphore( 1 );

  /**
   * A Thread that represents a User of the board game that will run all of their instructions.
   */
  private class UserThread extends Thread {
    // The socket connection for the thread
    private Socket sock;
    
    /**
     * Constructor for the UserThread, creates a new User thread given a socket connection
     * @param sock The socket connection for the thread
     */
    public UserThread(Socket sock) {
      this.sock = sock;
    }

    /**
     * Starts up the UserThread by calling handleClient and passing in its socket connection to
     * begin taking in user commands
     */
    public void run() {
      handleClient( this.sock );
    }
  }

  /** List of all the user records. */
  private ArrayList< UserRec > userList = new ArrayList< UserRec >();

  /**
   * Set the game board back to its initial state.
   */
  private void reset() {
    for ( int i = 0; i < board.length; i++ )
      for ( int j = 0; j < board[ i ].length; j++ )
        board[ i ][ j ] = template[ i ][ j ];

    for ( int i = 0; i < userList.size(); i++ )
      userList.get( i ).score = 0;
  }

  /**
   * Fills in the score sheet with the numbers associated with each possible letter that can be
   * placed on the board.
   */
  private void fillScoreSheet() {
    char itr = 'a';
    int score = 0;
    for( ; itr <= 'z'; itr++ ) {
        // 1 point
        if( itr == 'a' || itr == 'e' || itr == 'i' || itr == 'o' || itr == 'u' || 
            itr == 'n' || itr == 'r' || itr == 's' || itr == 't' || itr == 'l' )
            score = 1;
        else if( itr == 'd' || itr == 'g' )
            score = 2;
        else if( itr == 'b' || itr == 'c' || itr == 'm' || itr == 'p' )
            score = 3;
        else if( itr == 'f' || itr == 'h' || itr == 'v' || itr == 'w' || itr == 'y' )
            score = 4;
        else if( itr == 'k' )
            score = 5;
        else if( itr == 'j' || itr == 'x' )
            score = 8;
        else if( itr == 'q' || itr == 'z' )
            score = 10;

        scoreSheet.put( itr, score );                
    }
  }

  /** 
   * Read the initial board and all the users, done at program start-up. 
   */
  private void readConfig() throws Exception {
    // First, read in the map.
    Scanner input = new Scanner( new File( "../input/board.txt" ) );

    // Read in the initial state of the board.
    int height = input.nextInt();
    int width = input.nextInt();
    input.nextLine(); // Eat the rest of the first line.

    // Make the board state.
    template = new char [ height ][];
    for ( int i = 0; i < height; i++ )
      template[ i ] = input.nextLine().toCharArray();
    board = new char [ height ][ width ];

    // Read in all the users.
    input = new Scanner( new File( "../input/passwd.txt" ) );
    while ( input.hasNext() ) {
      // Create a record for the next user.
      UserRec rec = new UserRec();
      rec.name = input.next();

      // Get the key as a string of hex digits and turn it into a byte array.
      String base64Key = input.nextLine().trim();
      byte[] rawKey = Base64.getDecoder().decode( base64Key );
    
      // Make a key specification based on this key.
      X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec( rawKey );

      // Make an RSA key based on this specification
      KeyFactory keyFactory = KeyFactory.getInstance( "RSA" );
      rec.publicKey = keyFactory.generatePublic( pubKeySpec );

      // Add this user to the list of all users.
      userList.add( rec );
    }

    // Reset the state ofthe game.
    reset();
  }

  /** 
   * Utility function to read a length then a byte array from the
   * given stream.  TCP doesn't respect message boundaraies, but this
   * is essientially a technique for marking the start and end of
   * each message in the byte stream.  This can also be used by the
   * client.
   * @param input The Data stream that we will receive data from the Client
   */
  public static byte[] getMessage( DataInputStream input ) throws IOException {
    int len = input.readInt();
    byte[] msg = new byte [ len ];
    input.readFully( msg );
    return msg;
  }

  /** 
   * Function analogous to the previous one, for sending messages.
   * @param output The output data stream with which to send data to the Client 
   * @param msg The message to give to the client
   */
  public static void putMessage( DataOutputStream output, byte[] msg ) throws IOException {
    // Write the length of the given message, followed by its contents.
    output.writeInt( msg.length );
    output.write( msg, 0, msg.length );
    output.flush();
  }

  /** 
   * Function to handle interaction with a client.  For a multi-threaded
   * server, this should be done in a separate thread.
   * @param sock The socket connection for the Client 
   */
  public void handleClient( Socket sock ) {
    try {
      // Get formatted input/output streams for this thread.  These can read and write
      // strings, arrays of bytes, ints, lots of things.
      DataOutputStream output = new DataOutputStream( sock.getOutputStream() );
      DataInputStream input = new DataInputStream( sock.getInputStream() );
      
      // Get the username.
      String username = input.readUTF();

      // Make a random sequence of bytes to use as a challenge string.
      Random rand = new Random();
      byte[] challenge = new byte [ 16 ];
      rand.nextBytes( challenge );

      // Make a session key for communiating over AES.  We use it later, if the
      // client successfully authenticates.
      byte[] sessionKey = new byte [ 16 ];
      rand.nextBytes( sessionKey );

      // Find this user.  We don't need to synchronize here, since the set of users never
      // changes.
      UserRec rec = null;
      for ( int i = 0; rec == null && i < userList.size(); i++ )
        if ( userList.get( i ).name.equals( username ) )
          rec = userList.get( i );

      // Did we find a record for this user?
      if ( rec != null ) {
        // Make sure the client encrypted the challenge properly.
        Cipher RSADecrypter = Cipher.getInstance( "RSA" );
        RSADecrypter.init( Cipher.DECRYPT_MODE, rec.publicKey );
          
        Cipher RSAEncrypter = Cipher.getInstance( "RSA" );
        RSAEncrypter.init( Cipher.ENCRYPT_MODE, rec.publicKey );
          
        // Send the client the challenge.
        putMessage( output, challenge );
          
        // Get back the client's encrypted challenge.
        byte[] encryptedChallenge = getMessage( input );

        // Make sure the client properly encrypted the challenge.
        byte[] decryptedChallenge = RSADecrypter.doFinal( encryptedChallenge );
        if ( !Arrays.equals( decryptedChallenge, challenge ) ) {
          try {
            // Close the socket
            sock.close();
           } catch ( Exception e ) {}
        }
        // Send the client the session key (encrypted with the client's public
        // key).
        byte[] encryptedSessionKey = RSAEncrypter.doFinal(sessionKey);
        putMessage(output, encryptedSessionKey);
        
        // Make AES cipher objects to encrypt and decrypt with
        // the session key.
        SecretKey aesKey = new SecretKeySpec(sessionKey, 0, sessionKey.length, "AES");
        Cipher AESEncrypter = Cipher.getInstance( "AES/ECB/PKCS5Padding" );
        Cipher AESDecrypter = Cipher.getInstance( "AES/ECB/PKCS5Padding" );
        AESEncrypter.init( Cipher.ENCRYPT_MODE, aesKey );
        AESDecrypter.init( Cipher.DECRYPT_MODE, aesKey );
        
        // Get the first client command
        byte[] decryptedRequest = AESDecrypter.doFinal( getMessage( input ) );
        String request = new String( decryptedRequest );

        // Until the client asks us to exit.
        while ( ! request.equals( "exit" ) ) {

          s.acquireUninterruptibly();

          StringBuilder reply = new StringBuilder();

          if( request.equals( "board" ) ) {
            // Output board then scores
            StringBuilder boardOutput = new StringBuilder();
            for( int i = 0; i < board.length; i++ ) {
                for( int j = 0; j < board[ i ].length; j++ ) {
                    reply.append( board[ i ][ j ] );
                }
                reply.append( "\n" );
            }
            for( int i = 0; i < userList.size(); i++ ) {
                reply.append( userList.get(i).name + ": " + userList.get(i).score );
                reply.append( "\n" );
            }
          } else if( request.contains( "place" ) ) {

            reply.append( place( request, rec ) );

          } else { 
            reply.append( "Invalid Command\n" );
          }
          // Send the reply back to our client.
          byte[] replyBytes = reply.toString().getBytes();
          byte[] encryptedReply = AESEncrypter.doFinal( replyBytes );
          putMessage( output, encryptedReply );

          s.release();
              
          // Get the next command.
          decryptedRequest = AESDecrypter.doFinal( getMessage( input ) );
          request = new String( decryptedRequest );
        }
      }
    } catch ( IOException e ) {
      System.out.println( "IO Error: " + e );
    } catch( GeneralSecurityException e ){
      System.err.println( "Encryption error: " + e );
    } finally {
      try {
        // Close the socket on the way out.
        sock.close();
      } catch ( Exception e ) {
      }
    }
  }

  /**
   * Attempts to place a letter on the board at a particular location. If it is an invalid move,
   * or an invalid request it will append "Invalid Command" to the reply string to inform the user
   * of their invalid move.
   * @param request The request string the user has made
   */
  synchronized private String place( String request, UserRec rec ) {
    StringBuilder reply = new StringBuilder();
    String requestSplit[] = request.split( " " );
    
    if( requestSplit.length != 4 || requestSplit[ 1 ].length() != 1 ) {
      reply.append( "Invalid Command\n" );
      return reply.toString();
    }

    int row, col;
    try { // Error check to ensure these are Integers and they fall within board range
      row = Integer.parseInt( requestSplit[ 2 ] );
      col = Integer.parseInt( requestSplit[ 3 ] );
      char let = requestSplit[ 1 ].charAt( 0 );
      int pointsAwarded = 0;
      char pos = board[ row ][ col ];
      if( pos == 'o' )
        pointsAwarded = scoreSheet.get( let );
      else if( pos == '.' || pos == ':' ) {
        if( checkUp( row, col ) || checkDown( row, col ) || checkLeft( row, col ) || checkRight( row, col ) )
          pointsAwarded = scoreSheet.get( let );

        if( pos == ':' )
          pointsAwarded *= 2;
      }

      rec.score += pointsAwarded;

      if( pointsAwarded > 0 ) {
        board[ row ][ col ] = let;
        reply.append( pointsAwarded + " points\n" );
      } else
        reply.append( "Invalid Command\n" );

    } catch( Exception e ) {
        reply.append( "Invalid Command\n" );
    }
    
    return reply.toString();
  }

  /**
   * Checks the tile above the one the user entered to see if there is another letter above it.
   * @param row The row the user wishes to place a letter
   * @param col The column the user wishes to place a letter
   */
  private boolean checkUp( int row, int col ) {
    try {
      if( Character.isLetter( board[ row - 1 ][ col ] ) )
        return true;
    } catch( ArrayIndexOutOfBoundsException e ) {
      return false;
    }

    return false;
  }

  /**
   * Checks the tile above the one the user entered to see if there is another letter below it.
   * @param row The row the user wishes to place a letter
   * @param col The column the user wishes to place a letter
   */
  private boolean checkDown( int row, int col ) {
    try {
      if( Character.isLetter( board[ row + 1 ][ col ] ) )
        return true;
    } catch( ArrayIndexOutOfBoundsException e ) {
      return false;
    }

    return false;
  }

  /**
   * Checks the tile above the one the user entered to see if there is another letter left it.
   * @param row The row the user wishes to place a letter
   * @param col The column the user wishes to place a letter
   */
  private boolean checkLeft( int row, int col ) {
    try {
      if( Character.isLetter( board[ row ][ col - 1 ] ) )
        return true;
    } catch( ArrayIndexOutOfBoundsException e ) {
      return false;
    }

    return false;
  }

  /**
   * Checks the tile above the one the user entered to see if there is another letter right it.
   * @param row The row the user wishes to place a letter
   * @param col The column the user wishes to place a letter
   */
  private boolean checkRight( int row, int col ) {
    try {
      if( Character.isLetter( board[ row ][ col + 1 ] ) )
        return true;
    } catch( ArrayIndexOutOfBoundsException e ) {
      return false;
    }

    return false;
  }
  
  /** 
   * Esentially, the main method for our server, 
   * as an instance method so we can access non-static fields. 
   */
  private void run( String[] args ) {
    ServerSocket serverSocket = null;
    
    // One-time setup.
    try {
      // Read the map and the public keys for all the users.
      readConfig();

      fillScoreSheet(); 

      // Open a socket for listening.
      serverSocket = new ServerSocket( PORT_NUMBER );
    } catch( Exception e ){
      System.err.println( "Can't initialize server: " + e );
      e.printStackTrace();
      System.exit( 1 );
    }
     
    // Keep trying to accept new connections and serve them.
    while( true ){
      try {
        // Try to get a new client connection.
        Socket sock = serverSocket.accept();

        UserThread user = new UserThread( sock );
        user.start();

      } catch( IOException e ){
        System.err.println( "Failure accepting client " + e );
      }
    }
  }

  /**
   * Starting point for the program. Creates a server object and begins to run it.
   * @param args The program arguments given at runtime
   */
  public static void main( String[] args ) {
    // Make a server object, so we can have non-static fields.
    Server server = new Server();
    server.run( args );
  }
}
