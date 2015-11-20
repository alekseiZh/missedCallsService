/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.freeswitch;

import java.util.logging.Level;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.CommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author zh
 */
public class FSTest {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final Client fsClient = new Client();
    private static int connectCount = 0;
    private static int maxConnectCount = 100;
    
    /**
     * 
     */
    public void testEventSubscriptionManualDisconnect() {
        
        try {
            
            logger.debug("Start method: testEventSubscriptionManualDisconnect.");
            
            fsClient.connect("127.0.0.1", 8021, "ClueCon", 5);
            
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                logger.debug(ex.getMessage());
            }
            
            fsClient.close();
            
            fsClient.connect("127.0.0.1", 8021, "ClueCon", 5);
            
            try {
                Thread.sleep(5000);
            } catch (InterruptedException ex) {
                logger.debug(ex.getMessage());
            }
            
            fsClient.close();
            
        } catch (InboundConnectionFailure ex) {
            logger.debug(ex.getMessage());
        }
        
    }
    
    public final void connect() {
        try {
            fsClient.connect("127.0.0.1", 8021, "ClueCon", 5);
            connectCount = 0;
        } catch (InboundConnectionFailure ex) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.debug(e.getMessage());
            }
            if (connectCount == maxConnectCount) {
                return;
            }
            connectCount++;
            connect();
        }
    }
    
    public void testEventSubscriptionUnexpectedDisconnect() {
        
        logger.debug("Start method: testEventSubscriptionUnexpectedDisconnect.");
        
        connect();
        
        for (int i = 0; i < 300; i++) {
            
            try {
                CommandResponse cr = fsClient.setEventSubscriptions("plain", 
                        "CHANNEL_ANSWER CHANNEL_HANGUP CHANNEL_DESTROY");
                logger.debug("Result of command \"{}\": {}", cr.getCommand(),
                    cr.getReplyText());
                fsClient.sendAsyncApiCommand("show", "aliases");
            } catch (IllegalStateException e) {
                connect();
            }
            
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                logger.debug(ex.getMessage());
            }
        }

        fsClient.close();

    }
    
    
    
}
