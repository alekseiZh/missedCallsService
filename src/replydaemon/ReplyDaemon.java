/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author zh
 */
public class ReplyDaemon {
    
    private static final Logger logger = 
            LoggerFactory.getLogger("replydaemon.StaticLogger");
    
    private static final RecallMissedCalls missedCallDaemon = 
            new RecallMissedCalls();
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        registerShutdownHook();
        Thread missedCallDaemonThread = new Thread(missedCallDaemon);
        missedCallDaemonThread.start();
        
        try {
            missedCallDaemonThread.join();
        } catch (InterruptedException ex) {
            logger.debug(ex.getMessage());
        }
        
        System.exit(0);
    }
    
    private static void registerShutdownHook()
    {
        Runtime.getRuntime().addShutdownHook(
            new Thread() {
                public void run() {
                        missedCallDaemon.stop();
                }
            }
        );
    }
}
