package org.example;

import com.google.gson.Gson;
import util.Logger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Client extends Thread{

    String hostName;
    int portNumber;
    private final BlockingQueue<String> messageQueue;
    private ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers;
    private PublicKey publicKey;

    private PrivateKey privateKey;



    public Client(String hostName, int portNumber, BlockingQueue<String> messageQueue, ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers, PublicKey publicKey, PrivateKey privateKey) {
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.messageQueue = messageQueue;
        this.connectedPeers = connectedPeers;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    @Override
    public void run() {
        try {
            Socket socket = new Socket(hostName, portNumber);

            // this method takes in the socket that was currently created.
            // what it does is a bit more complicated. It initiates the handshake protocol  and exchanges the public keys with the client and then creates two threads.
            //one thread will only listen to the socket and put messages that it recieves to a message queue
            // one thrad will be created for messaging. it will have a method send that will send a something to that socket output. and i will have an array of those sockets that will handle the sockets? i guess ?

            handleHandshakeFromServer(socket);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleHandshakeFromServer(Socket socket) throws Exception {


        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("Connected to server: " + hostName + ":" + portNumber);

        // read the first message from the server. it sohuld be the handshakeMessage

        String jsonMessage = in.readLine(); // Read the JSON message
        System.out.println("Received handshake message: " + jsonMessage);


        Gson gson = new Gson();
        Message handshakeMessage = gson.fromJson(jsonMessage, Message.class);
        PublicKey serverPublicKey = stringToPublicKey(handshakeMessage.getPublicKey());
        System.out.println("Server's public key: " + serverPublicKey);


        //send a new message to the server to let him know your public key and your port number;

        Message responseMessage = new Message(MessageType.HANDSHAKEKEYRETURN, ""+portNumber, publicKeyToString(publicKey));
        String jsonResponse = gson.toJson(responseMessage);
        System.out.println("Sending response handshake to server: " + jsonResponse);

        out.println(jsonResponse);


        ListenToMeThred listenThread = new ListenToMeThred(socket, in, messageQueue);
        new Thread(listenThread).start(); // Run the listening thread

        WriteMeThread writeMeThread = new WriteMeThread(out);
        new Thread(writeMeThread).start(); // Run the listening thread

        PeerInfo peerInfo = new PeerInfo(socket,writeMeThread,portNumber);

        // Store the client's information in connectedPeers
        //it stores the publicKey and the peers socket and the writemeThread;
        connectedPeers.put(serverPublicKey, peerInfo);

        System.out.println("This is my public key: " + publicKey);


        Logger.log("new connection :  ");
        Logger.log("----> (kao sem se povezes) Local IP :  " +socket.getLocalAddress());
        Logger.log("----> (my port where i'm open) Local PORT :  " + socket.getLocalPort());
        Logger.log("----> IP :  " + socket.getInetAddress());
        Logger.log("----> (odprt port ku poslusa) PORT :  " + socket.getPort());
        Logger.log("----------------------------");

    }

    // Method to handle messages from the server


    //methods that help decode messages and keys and stuff
    public PublicKey stringToPublicKey(String key) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    public static String publicKeyToString(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }
}
