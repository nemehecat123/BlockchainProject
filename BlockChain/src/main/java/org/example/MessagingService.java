package org.example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import util.LogLevel;
import util.Logger;

import java.lang.reflect.Type;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class MessagingService extends Thread {
    private final BlockingQueue<String> messageQueue;
    private ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers;

    public Blockchain blockchain;

    private UTXOPool utxoPool;

    private TransactionManager transactionManager;

    public BlockingQueue<String> getMessageQueue() {
        return messageQueue;
    }

    private TransactionPool transactionPool;

    private MinerThread minerThread;
    MiningCoordinator miningCoordinator;
    public MessagingService(BlockingQueue<String> messageQueue, ConcurrentHashMap<PublicKey, PeerInfo> connectedPeers, String hostName, int portNumber, PublicKey publicKey, PrivateKey privateKey, Blockchain blockchain, UTXOPool utxoPool, TransactionManager transactionManager, MiningCoordinator miningCoordinator) {
        this.messageQueue = messageQueue;
        this.connectedPeers = connectedPeers;
        this.hostName = hostName;
        this.portNumber = portNumber;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.blockchain  = blockchain;
        this.utxoPool = UTXOPool.getInstance();  // Ensure singleton instance of UTXO pool
        this.transactionManager = transactionManager;
        this.transactionPool = TransactionPool.getInstance();  // Use the singleton instance
        this.miningCoordinator = miningCoordinator;
        this.setName("Messaging service Thread");
    }

    String hostName;
    int portNumber;

    private PublicKey publicKey;

    private PrivateKey privateKey;


    Gson gson = new Gson();



    @Override
    public void run() {
        try {

            // bussy waiting for someone to put something in the queue. when that happens it proceses it.
            while (true) {
                // Wait for a message to process
                String message = messageQueue.take();
                Type type = new TypeToken<ArrayList<Integer>>() {}.getType();

                Message messageObject = gson.fromJson(message,Message.class);

                PublicKey sender  = stringToPublicKey(messageObject.getPublicKey()) ;
                String senderName = generateNameFromPublicKey(messageObject.getPublicKey());
                switch (messageObject.getHeader()) {
                    case HANDSHAKE -> {
                    }
                    case HANDSHAKEKEYRETURN -> {

                    }
                    case PEERLIST -> {
                        ArrayList<Integer> arrayListOfPorts = gson.fromJson(messageObject.getBody(), type);

                        // in this case i need to connnect to all peers in the list
                        Logger.log("i recived this array of ports i neeed to connect to : " + arrayListOfPorts, LogLevel.Info);

                        for (Integer connectToPort : arrayListOfPorts) {
                            //Is special variable has to be true so the servir will get the correct header.
                            Client novaPovezava = new Client(hostName,portNumber,messageQueue,connectedPeers,publicKey,privateKey,connectToPort,true);
                            novaPovezava.start();

                        }
                    }
                    case PEERLISTRETURN -> {
                    }
                    case BLOCKCHAINREQUEST -> {
                        PeerInfo peerInfo = connectedPeers.get(sender);

                        WriteMeThread thread = (WriteMeThread) peerInfo.getThread();
                        String pkString = publicKeyToString(publicKey);
                        Message m = new Message(MessageType.BLOCKCHAINRESPONSE,gson.toJson(blockchain), pkString);
                        String mString = gson.toJson(m);
                        thread.sendMessage( mString);
                    }
                    case BLOCKCHAINRESPONSE -> {
                        // I got the BLockchain from a peer now i want to update it localy.
                        Blockchain.setInstance(gson.fromJson(messageObject.getBody(), Blockchain.class));
                        synchronized (SharedResources.LOCK) {
                            notifyUpdates();
                        }
                    }

                    case UTXOPOOLINITIALIZATION -> {
                        utxoPool= gson.fromJson(messageObject.getBody(), UTXOPool.class);

                        Logger.log(blockchain.getUTXOPool().toString() ,LogLevel.Warn);

                    }

                    case TRANSACTION -> {
                        Logger.log("RECIEVED A NEW TRANSACTION FROM : "+ senderName,LogLevel.Success);

                        if (utxoPool==null){
                            Logger.log("UTXOpool is null, cannot continue");
                        }

                        Transaction transaction = gson.fromJson(messageObject.getBody(),Transaction.class);
                        transactionManager.validateNewTransaction(transaction);

                    }
                    case REQUESTTRANSPOOL -> {
                        Logger.log("recived REQTRANSPOOL message from : "+ senderName, LogLevel.Status);
                        transactionManager.sendTransactionPool(sender);
                    }
                    case RESPONSETRANSPOOL ->{
                        Logger.log("recived RESPONSE TRANSPOOL message from : "+ senderName, LogLevel.Status);
                        transactionManager.updateTransactionPool(messageObject.getBody());
                    }
                    case REQUESTUTXOPOOL -> {
                        Logger.log("recived REQUESTUTXOPOOL message from : "+ senderName, LogLevel.Status);
                        transactionManager.sendUTXOPool(sender);
                    }
                    case RESPONSEUTXOPOOL->{
                        Logger.log("recived RESPONSE UTXOPOOL message from : "+ senderName+ messageObject.getBody(), LogLevel.Status);
                        transactionManager.updateUTXOPool(messageObject.getBody());
                    }
                    case BLOCK -> {
                        Logger.log("RECIVED A NEW BLOCK from : " + senderName, LogLevel.Info);
                        handleNewBlock(senderName,messageObject);
                    }
                }


                // Process the message (e.g., log, broadcast, or route)
                // For now, simply print it
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void handleNewBlock(String senderName,Message messageObject ){
        //handle multiple cases... poglej blockchain in ce je use kul ga dodaj ....
        miningCoordinator.interruptMining();

        Block recievedBlock = gson.fromJson(messageObject.getBody(),Block.class);

        if (blockchain.getLatestBlock().getIndex()== recievedBlock.getIndex() -1) ; // če je na pravem mesti ga dodaj
        {
            blockchain.addBlock(recievedBlock);
            transactionPool.removeTransactions(recievedBlock.getTransactions()); // removni transakcije z bloka
            miningCoordinator.resetMiningFlag();
        }
        //case drugacenga blocka na istem mestu... to bos rabu implementirat da je blockchain List listou.... in u tistih listih je pol blocki...
        //CASE 2 je ta da si en block dodau in je use kul. druga moznost je zdej ta, da si dobil block in je na istem mestu
        //takrat ga rabis dat samo zdraven ma se zmiri minas tisti tvoj prvi block. ne menjas nic si samo shranis.
        //tudi validirat ne rabis si samo shranis in case da bos dobil nov block k bo meu hash od te druge verige
        //bos lahko vedu da je pol tista bolj kul.



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
    public static String generateNameFromPublicKey(String publicKey) {
        // Generate a UUID based on the public key hash
        UUID uuid = UUID.nameUUIDFromBytes(publicKey.getBytes());
        return uuid.toString().split("-")[0]; // Use the first part for brevity
    }

    public synchronized void notifyUpdates() {
        synchronized (SharedResources.LOCK) {
            SharedResources.LOCK.notifyAll(); // Notify all waiting threads
            Logger.log("Updating threads");
        }

    }
}