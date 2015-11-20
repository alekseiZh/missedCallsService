/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.util;

import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Написанный на коленке класс, призванный упростить процедуру посылки почты с
 * почтового ящика яндекса. С другими почтовыми системами не тестировался.
 * @author zh
 */
public class MailSender {
    
    private final String username;
    private final String password;
    private final Properties props;
    
    Logger logger = LoggerFactory.getLogger(this.getClass());

    public MailSender(String smptServer, int port, String username, String password) {
        
        this.username = username;
        this.password = password;
        
        props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.host", smptServer);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
    }
    
    
    /**
     * Отправляет сообщение с заданными параметрами.
     * 
     * @param to
     * Получатель.
     * @param subject
     * Тема.
     * @param messageText 
     * Текст сообщения.
     */
    public void sendMail(String to, String subject, String messageText) {
        
        logger.debug("Start method sendMail.");
        
        Session session = Session.getInstance(props, 
                new javax.mail.Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
            });

        logger.info("Try to send mail.\n\tto: {}.\n\tfrom: {}\n\tSubject: {}\n\tText: {}", 
                to, username, subject, messageText);
        try {

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(messageText);
            
            logger.debug("Now we call method Transport.send");
            Transport.send(message);
            logger.debug("Aaaand... we done it!");
        } catch (Exception e) {
            logger.info("Sending mail failed: {}", e.getMessage());
        }
    }
}
