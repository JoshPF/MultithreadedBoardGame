/**
 * Represents the Client in the board game program. Attempts to log in as a certain, user specified
 * player and begins sending commands to be processed by the Server
 * @file Client.java
 * @author Josh Ferrero (jpferrer)
 */
import java.io.*;
import java.util.Scanner;
import java.net.Socket;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.GeneralSecurityException;
import java.util.Base64;

/** 
 * Client supporting simple interactionw with the server. 
 */
public class Client {
  
  /**
   * Takes in the number of arguments, then begins to connect with the Server. Takes in User 
   * commands and encrypts them, then sends them to the Server for processing
   * @param args The arguments specified by the user at startup
   */
  public static void main( String[] args ) {
    // Complain if we don't get the right number of arguments. 
    if ( args.length != 1 ) {
      System.out.println( "Usage: Client <host>" );
      System.exit( -1 );
    }

    try {
      // Try to create a socket connection to the server.
      Socket sock = new Socket( args[ 0 ], Server.PORT_NUMBER );

      // Get formatted input/output streams for talking with the server.
      DataInputStream input = new DataInputStream( sock.getInputStream() );
      DataOutputStream output = new DataOutputStream( sock.getOutputStream() );

      // Get a username from the user and send it to the server.
      Scanner scanner = new Scanner( System.in );
      System.out.print( "Username: " );
      String name = scanner.nextLine();
      output.writeUTF( name );
      output.flush();

      // Get the challenge string (really a byte array) from the server.
      byte[] challenge = Server.getMessage( input );

      // Try to read the user's private key.
      Scanner keyScanner = new Scanner( new File( "../input/" + name + ".txt" ) );
      String base64Key = keyScanner.nextLine();
      byte[] rawKey = Base64.getDecoder().decode( base64Key );
      keyScanner.close();
    
      // Make a key specification based on this key.
      PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec( rawKey );
      
      // Get an RSA key based on this specification
      KeyFactory keyFactory = KeyFactory.getInstance( "RSA" );
      PrivateKey privateKey = keyFactory.generatePrivate( privKeySpec );

      // Make a cipher object that will encrypt using this key.
      Cipher RSAEncrypter = Cipher.getInstance( "RSA" );
      RSAEncrypter.init( Cipher.ENCRYPT_MODE, privateKey );

      // Make another cipher object that will decrypt using this key.
      Cipher RSADecrypter = Cipher.getInstance( "RSA" );
      RSADecrypter.init( Cipher.DECRYPT_MODE, privateKey );

      // Encrypt the challenge with our private key and send it back.
      byte[] rawCiphertext = RSAEncrypter.doFinal( challenge );
      Server.putMessage( output, rawCiphertext );

      // Get the symmetric key from the server and make AES
      // encrypt/decrypt objects for it.
      byte[] encryptedKey = Server.getMessage( input );
      byte[] decryptedKey = RSADecrypter.doFinal( encryptedKey );
      //SecretKey sessionKey = new SecretKeySpec( decryptedKey, 0, decryptedKey.length, "AES" );
      SecretKey sessionKey = new SecretKeySpec( decryptedKey, "AES" ); 
      Cipher AESEncrypter = Cipher.getInstance( "AES/ECB/PKCS5Padding" );
      Cipher AESDecrypter = Cipher.getInstance( "AES/ECB/PKCS5Padding" );
      AESEncrypter.init( Cipher.ENCRYPT_MODE, sessionKey );
      AESDecrypter.init( Cipher.DECRYPT_MODE, sessionKey );

      // Read commands from the user and print server responses.
      String request = "";
      System.out.print( "cmd> " );
      while ( scanner.hasNextLine() && ! ( request = scanner.nextLine() ).equals( "exit" ) ) {
        byte[] encryptedRequest = AESEncrypter.doFinal( request.getBytes() );
        Server.putMessage( output, encryptedRequest );

        // Read and print the response.
        byte[] encryptedResponse = Server.getMessage( input );
        String response = new String( AESDecrypter.doFinal( encryptedResponse ) );
        System.out.print( response );
        
        System.out.print( "cmd> " );
      }
      
      // Send the exit command to the server.
      byte[] encryptedExit = AESEncrypter.doFinal( request.getBytes() );
      Server.putMessage( output, encryptedExit );
 
      // We are done communicating with the server.
      sock.close();
    } catch( IOException e ){
      System.err.println( "IO Error: " + e );
    } catch( GeneralSecurityException e ){
      System.err.println( "Encryption error: " + e );
    }
  }
}
