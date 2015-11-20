/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.freeswitch;

import replydaemon.util.SofiaURL;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import replydaemon.ConnectionFailException;

/**
 *
 * @author zh
 */
public class FreeswitchInterface {
    
    private final Client fsClient = new Client();
    private final Logger logger = 
            LoggerFactory.getLogger(FreeswitchInterface.class);
    
    private boolean reconnectFlag = true;
    private final boolean endlessReconnect = true;
    private final int maxConnectAtemmpts = 2;
    private final int maxConnectTimeout = 10000;
    
    private final String host;
    private final int port;
    private final String password;
    private final int fsTimeout;
    
    public FreeswitchInterface(String host, int port, String password, int timeout) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.fsTimeout = timeout;
    }
    
    public Client getFSClient() {
        return fsClient;
    }
    
    /**
     * Immediately stop reconnect to server process.
     */
    public void stopReconnect() {
        logger.debug("Start method stopReconnect.");
        reconnectFlag = false;
    }
    
    /**
     * Make connection to Freeswitch. If connection process failed, reconnection 
     * will be performed. Maximum number of reconnection attempts determined by
     * maxConnectAtemmpts field. Pause between reconnection attempts increese by
     * two per each attempt. When pause duration become equal maxConnectTimeout 
     * field, it dont increase anymore.
     */
    public void connectToFreeswitch() {
        
        logger.debug("Start method connectToFreeswitch");
        
        int connectAttempt = 0;
        int connectTimeout = 500;
        reconnectFlag = true;
        
        while (reconnectFlag) {
            
            if (++connectAttempt > maxConnectAtemmpts && !endlessReconnect) {
                logger.info("Max number of reconnect attempt distinguished.");
                throw new ConnectionFailException("We cannot connect to freeswitch...");
            }
            
            logger.info("Connect to FreeSWITCH, attempt {}, connection string: {}:{}", 
                connectAttempt, host, port);
            
            try {
                fsClient.connect(host, port, password, fsTimeout);
                reconnectFlag = false;
            
            } catch (InboundConnectionFailure e) {
                
                logger.info("Connection to Freeswitch ({}:{}) failed. {}", 
                    host, port, e.getMessage());
                
                if (connectTimeout < maxConnectTimeout) {
                    connectTimeout = connectTimeout * 2;
                    if (connectTimeout > maxConnectTimeout) {
                        connectTimeout = maxConnectTimeout;
                    }
                }
                
                logger.info("pause: {} ms", connectTimeout);

                try {
                    Thread.sleep(connectTimeout);
                } catch (InterruptedException ex) {
                    logger.debug("Thread.sleep({})", connectTimeout);
                }
            }
        }
    }
    
    /**
     * 
     * @param events
     */
    public void setEventSubscription(String events) {
        logger.debug("Start method: setEventSubscription.");
        try {
            boolean accepted = false;
            while (!accepted) {
                CommandResponse cr = 
                        fsClient.setEventSubscriptions("plain", events);
                logger.debug("Result of command \"{}\": {}", cr.getCommand(), 
                        cr.getReplyText());
                accepted = cr.getReplyText().split(" ")[0].equals("+OK");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    logger.debug(ex.getMessage());
                }
            }
            
        } catch(IllegalStateException ex) {
            connectToFreeswitch();
            setEventSubscription(events);
        }
    }
    
    /**
     * Cancel event subscriptions and close connection to FS.
     */
    public void disconnectFromFreeswitch() {
        
        logger.debug("Start method disconnectFromFreeswitch.");
        
        try {
            fsClient.cancelEventSubscriptions();
            fsClient.close();
            logger.debug("disconnectFromFreeswitch: Disconnect success.");
        } catch (IllegalStateException e) {
            logger.debug("Already disconnected fro FS.");
        }
    }
        
    /**
     * Make call to sofiaURL. A-leg then connect to playback wait.wav.
     * @param sofiaURL
     * Sofia URL to call.
     * 
     * @param originationUUID
     * UUID for new channel.
     * 
     * @return 
     * UUID of channel. If call failed, then null is return.
     * 
     * @param originatePlaybackCommand 
     * originate playback FS command.<br/><br/>
     * 
     * Examples:<br/>
     * &park()<br/>
     * &bridge(sofia/gateway/porvider1/1234566)<br/>
     * &playback(D:\\dir1\\file.wav)
     */    
    public String makeCall(SofiaURL sofiaURL, String originationUUID, 
            String originatePlaybackCommand) {
        
        logger.debug("Start method makeCall");
        try {
            
            sofiaURL.addChannelVariable("origination_uuid", originationUUID);
            EslMessage response = fsClient.sendSyncApiCommand("originate",
                            sofiaURL.constructSofiaURL() +
                            " " + originatePlaybackCommand);

            String result = response.getBodyLines().get(0);
            
            logger.info("Call to URL {}", sofiaURL.constructSofiaURL());
            
            if ("+OK".equals(result.split(" ")[0])) {
                logger.info("Making call success: URL: {}", 
                        sofiaURL.constructSofiaURL());
                return result;
            }
            
            logger.info("Making call failed: URL: {}", sofiaURL.constructSofiaURL());
            
            return null; 
            
        } catch (IllegalStateException e) {
            logger.debug("Connection to freeswitch is lost. Try to reconnect.");
            connectToFreeswitch();
            return makeCall(sofiaURL, originationUUID, 
                    originatePlaybackCommand);
        }
    }
    
    /**
     * Bridge two channel between each other.
     * @param aUUID
     * @param bUUID
     * @return 
     * True if bridge action successefull. False in otherwise.
     */
    public String bridge(String aUUID, String bUUID) {
        
        try {
            logger.info("Bridge channels: {} and {}", aUUID, bUUID);
            
            EslMessage response = 
            fsClient.sendSyncApiCommand("uuid_bridge", aUUID + " " + bUUID);
            return response.getBodyLines().get(0);
//            return "+OK".equals(response.getBodyLines().get(0).split(" ")[0]);
            
        } catch (IllegalStateException e) {
            logger.debug("Connection to freeswitch is lost. Try to reconnect.");
            connectToFreeswitch();
            return bridge(aUUID, bUUID);
        }
    }
    
    /**
     * Break channel with specific UUID. 
     * 
     * @param uuid
     * uuid which will be killed.
     * 
     * @return
     * If breaking channel perform correct, method return true. Otherwise return 
     * false.
     */
    public boolean killUUID(String uuid) {
        
        logger.debug("Start method killUUID: {}", uuid);
        
        try {
            EslMessage response = fsClient.sendSyncApiCommand("uuid_kill", uuid);        
            String[] result = response.getBodyLines().get(0).split(" ");

            if ("+OK".equals(result[0])) {
                logger.debug("killUUID succes: UUID: {}", uuid);
                return true;
            }

            return false;
            
        } catch (IllegalStateException e) {
            logger.debug("Connection to freeswitch is lost. Try to reconnect.");
            connectToFreeswitch();
            return killUUID(uuid);
        }
    }
    
    /**
     * Get new UUID from FS.
     * 
     * @return 
     * UUID.
     */
    public String getNewUUID() {
        logger.debug("Start method getNewUUID");
        
        try {
            EslMessage response = fsClient.sendSyncApiCommand("create_uuid", "");
            return response.getBodyLines().get(0);
            
        } catch (IllegalStateException e) {
            logger.debug("Connection to freeswitch is lost. Try to reconnect.");
            connectToFreeswitch();
            return getNewUUID();
        }
    }
    
    /**
     * Отправляет синхронную команду на FS.
     * 
     * @param command
     * Команда.
     * 
     * @param arguments
     * Аргументы команды.
     * 
     * @return 
     * Результат выполнения.
     */
    public String sendSyncApiCommand(String command, String arguments) {
        logger.debug("Start method sendSyncApiCommand");
        
        try {
            EslMessage response = fsClient.
                    sendSyncApiCommand(command, arguments);
            return response.getBodyLines().get(0);
            
        } catch (IllegalStateException e) {
            logger.debug("Connection to freeswitch is lost. Try to reconnect.");
            connectToFreeswitch();
            return sendSyncApiCommand(command, arguments);
        }
    }
}