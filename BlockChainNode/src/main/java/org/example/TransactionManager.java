package org.example;


import com.google.gson.Gson;
import util.LogLevel;
import util.Logger;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionManager extends Thread{


    private UTXOPool utxoPool ;

    private ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers;

    public Gson gson = new Gson();

    private PublicKey publicKey;

    private Random random; // Random generator for testing
    private Blockchain blockchain;

    private TransactionPool transactionPool;


    public TransactionManager(UTXOPool utxoPool, ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers, PublicKey publicKey, Blockchain blockchain, TransactionPool transactionPool) {
        this.utxoPool = utxoPool;
        this.connectedPeers = connectedPeers;
        this.publicKey = publicKey;
        this.blockchain = blockchain;
        this.transactionPool = transactionPool;
        this.random = new Random();
        this.setName("transaction Mannager Thread");

    }

    public void run(){

            //  vedno vprasas prvega na katerega se povezes. za zdej je uredi za debuging
            synchronized (SharedResources.LOCK) {

                // Wait until blockchain` is not null
                try {
                    // Wait until blockchain is not null AND connectedPeers is not empty
                    while (connectedPeers.isEmpty()) {
                        Logger.log("WAITING FOR A Updated connected peers");
                        SharedResources.LOCK.wait();
                    }
                    Logger.log("NOT WAITING ANYMORE REQUESTING");

                    // When notified and conditions are met, send requests
                    List<PublicKey> peerKeys = new ArrayList<>(connectedPeers.keySet());
                    Random random = new Random();
                    requestTransactionPool(peerKeys.get(random.nextInt(peerKeys.size())));
                    requestUTXOPool(peerKeys.get(random.nextInt(peerKeys.size())));
                    requestBlockchain(peerKeys.get(random.nextInt(peerKeys.size())));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }

                // If the blockchain, transactionPool, and UTXOPool are already available
                Logger.log(transactionPool + "" + blockchain, LogLevel.Debug);
            }
        }


    // to bo class ki booo na zacetku requestou blockchain
    //oz bo requestau za thread pool in pol ko bo dobil kaksno transakcijo jo bo moral obbdelat in ja...

    private void requestUTXOPool(PublicKey sendToPublicKey) {

        Message m = new Message(MessageType.REQUESTUTXOPOOL,"",publicKeyToString(publicKey));
        String mString = gson.toJson(m);

        WriteMeThread thread = (WriteMeThread) connectedPeers.get(sendToPublicKey).getThread();

        thread.sendMessage( mString);
    }

    private void requestTransactionPool(PublicKey sendToPublicKey) {

    Message m = new Message(MessageType.REQUESTTRANSPOOL,"",publicKeyToString(publicKey));
    String mString = gson.toJson(m);

    WriteMeThread thread = (WriteMeThread) connectedPeers.get(sendToPublicKey).getThread();

    thread.sendMessage( mString);


}



public void requestBlockchain(PublicKey sendToPublicKey) {

    //enega rendom peera dobi in poslji blockchainrequest.

    Message m = new Message(MessageType.BLOCKCHAINREQUEST,"",publicKeyToString(publicKey));
    String mString = gson.toJson(m);

    WriteMeThread thread = (WriteMeThread) connectedPeers.get(sendToPublicKey).getThread();

    thread.sendMessage( mString);

}

    public void sendUTXOPool(PublicKey sendToPublicKey) {
        String UTXOpoolString = gson.toJson(utxoPool);

        Message m = new Message(MessageType.RESPONSEUTXOPOOL,UTXOpoolString,publicKeyToString(publicKey));
        String mString = gson.toJson(m);

        WriteMeThread thread = (WriteMeThread) connectedPeers.get(sendToPublicKey).getThread();

        thread.sendMessage( mString);
    }

    public void updateUTXOPool(String UTXOPoolString){
        utxoPool = gson.fromJson(UTXOPoolString,UTXOPool.class);
        Logger.log("Updating Utxo pool",LogLevel.Success);
    }


public PublicKey stringToPublicKey(String key) throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(key);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return keyFactory.generatePublic(spec);
}

public static String publicKeyToString(PublicKey publicKey) {
    return Base64.getEncoder().encodeToString(publicKey.getEncoded());
}

    public void sendTransactionPool(PublicKey sendToPublicKey) {
        String transPoolString = gson.toJson(transactionPool);

        Message m = new Message(MessageType.RESPONSETRANSPOOL,transPoolString,publicKeyToString(publicKey));
        String mString = gson.toJson(m);

        WriteMeThread thread = (WriteMeThread) connectedPeers.get(sendToPublicKey).getThread();

        thread.sendMessage( mString);
    }

    public void updateTransactionPool(String transactionPoolString) {
        transactionPool=gson.fromJson(transactionPoolString,TransactionPool.class);
        Logger.log("Updating Transaction pool", LogLevel.Success);

    }
}

