/*
 * Classname 
 * RecallMissedCalls
 * 
 * Version information
 * 1.0
 *
 * Date
 * 
 * Copyright notice
 */

package replydaemon;

import replydaemon.freeswitch.FreeswitchInterface;
import java.util.ArrayList;
import java.util.Properties;
import org.slf4j.LoggerFactory;
import replydaemon.freeswitch.RecallEventListener;
import replydaemon.missedcall.EmailNotificationParameters;
import replydaemon.util.MailSender;
import replydaemon.util.SofiaURL;
import replydaemon.missedcall.MissedCall;
import static replydaemon.util.Functions.numberCorrection;

/**
 * Класс реализует процесс обратного звонка по пропущенным вызовам для 
 * колл-центра Call-o-Call. Можно запускать одновременно не более одного 
 * экземпляра данного класса, т.к. работа с БД не синхронизирована.
 * 
 * @author zh
 */
public class RecallMissedCalls implements Runnable {
    
    private final org.slf4j.Logger logger = 
            LoggerFactory.getLogger(RecallMissedCalls.class);
    
    private final String dbConnString;
    
    private final int daemonSleepTime;
    private final int oldMissedCallsAge; 
    private final String inboundCallPrefix;
    
    private final String fsPassword;
    private final String fsHost;
    private final int fsPort;
    
    private final DBInterface dbi;
    private final FreeswitchInterface fsi;
    
    private final String smtpServer;
    private final int smtpPort;
    private final String mailUser;
    private final String mailPassword;
    
    private final int operatorAnswerTimeout;
    private final int subscriberAnswerTimeout;
    private final String originatePlaybackCommand;
    
    private int sessionState = -1;
    private MissedCall missedCall;
    private String subscriberUUID;
    private String queueUUID;
    
    private String idString;
    
    private boolean running = true;

    /**
     * Создает новый экземпляр класса RecallMissedCalls на основе 
     * конфигурационного файла.
     */
    public RecallMissedCalls() {
        
        Properties prop = replydaemon.util.
                Functions.loadConfigFromFile(System.getProperty("mcConfig"));
        
        dbConnString = "jdbc:jtds:sqlserver://" 
                + prop.getProperty("dbHost", "127.0.0.1") + ":"
                + prop.getProperty("dbPort", "1433") + ";databaseName="
                + prop.getProperty("dbName", "Callcenter") + ";user="
                + prop.getProperty("dbUser", "callcenter") + ";password="
                + prop.getProperty("dbPassword", "call");
        
        fsHost = prop.getProperty("fsHost", "127.0.0.1");
        fsPort = Integer.valueOf(prop.getProperty("fsPort", "8081"));
        fsPassword = prop.getProperty("fsPassword", "ClueCon");
        
        daemonSleepTime = Integer.valueOf(
                prop.getProperty("requestPause", "60000")
        );
        
        inboundCallPrefix = prop.getProperty("bNumberPrefix", "00");
        
        originatePlaybackCommand = prop.getProperty(
                "originatePlaybackCommand", "&park()"
        );
        
        oldMissedCallsAge = Integer.valueOf(
                prop.getProperty("oldMissedCallDuration", "7200")
        );
        
        operatorAnswerTimeout = Integer.valueOf(
                prop.getProperty("operatorAnswerTimeout", "300")
        );
        
        subscriberAnswerTimeout = Integer.valueOf(
                prop.getProperty("subscriberAnswerTimeout", "120")
        );
        
        mailUser = prop.getProperty("mailUser", "admin@admin.com");
        mailPassword = prop.getProperty("mailPassword", "password");
        smtpServer = prop.getProperty("smtpServer", "smtp.admin.ru");
        smtpPort = Integer.valueOf(prop.getProperty("smtpPort", "465"));
        
        dbi = new DBInterface(dbConnString);
        fsi = new FreeswitchInterface(fsHost, fsPort, fsPassword, 5);
        idString = "";
    }
    
    @Override
    public void run() {
        
        //Удаление старых пропущенных звонков из БД
        dbi.removeOldMissedCalls(oldMissedCallsAge);
        
        fsi.connectToFreeswitch();
        
        //Основное тело демона
        while (running) {
            
            daemonBody();
            
            try {
                Thread.sleep(daemonSleepTime);
            } catch (InterruptedException ex) {
                logger.error(ex.getMessage());
            }
        }
        
        fsi.disconnectFromFreeswitch();
    }
    
    /**
     * Останавливает цикл прозвона по пропущенным вызовам.
     */
    public void stop() {
        logger.info("Stop daemon.");
        fsi.stopReconnect();
        dbi.stopReconnect();
        running = false;
    }
    
    /**
     * Основная логика работы демона.
     */
    private void daemonBody() {
        
        // Отсылаем по необходимости оповещения о пропущенных вызовах
        sendEmails();
        
        missedCall = dbi.getMissedCall();
        
        // Если обнаружен пропущенный вызов
        if (missedCall != null) {
            
            //Регистрация обработчика событий FreeSWITCH
            RecallEventListener listener = new RecallEventListener(this);
            fsi.setEventSubscription("CHANNEL_ANSWER CHANNEL_HANGUP CHANNEL_DESTROY");
            fsi.getFSClient().addEventListener(listener);
            
            // Заполняем строку, которая идентифицирует пропущенный вызов
            idString = "ID:" + missedCall.getID() 
                    + "; A:" + missedCall.getAnumber()
                    + "; B:" + missedCall.getBnumber()
                    + "; GrID:" + missedCall.getGrID();
            
            // Переводим номер абонента и номер ГЛ к корректному виду.
            // Эти номера будут использованы для совершения вызова.
            String realANumber = numberCorrection(missedCall.getAnumber(), "");
            String realBNumber = numberCorrection(missedCall.getBnumber(), "8");
            
            // Создаем на основе номеров Sofia URL
            SofiaURL urlA = new SofiaURL();
            SofiaURL urlB = new SofiaURL();
            
            //// Настраиваем Sofia URL для номера абонента
            // Устанавливаем тип gateway
            urlA.setType(0);
            
             // Устанавливаем номер абонента
            urlA.setNumber(realANumber);
            
            // Если номер А имеет неправильный формат, отмечаем номер как
            // прозвоненный и завершаем его обработку.
            if (realANumber == null) {
                logger.info("{} . Incorrect number of subscriber.", idString);
                stopSessionProcessing(0, "Incorrect number of subscriber");
                return;
            }
            
            // Определяем маршрут, через который будем совершать вызов абоненту
            String route  = dbi.getRoute(missedCall.getAnumber(), 
                    missedCall.getBnumber());
            
            // Если маршрут не обнаружен, завершаем обработку сессии
            if (route == null) {
                logger.info("{} . The route isn't found.", idString);
                stopSessionProcessing(0, "The route isn't found");
                return;
            }
            
            // Устанавливаем найденный маршрут
            urlA.setGateway(route);
            
            // Добавляем переменную для использования в целях биллинга
            urlA.addChannelVariable("reply_group_id", 
                    String.valueOf(missedCall.getGrID()));
            
            //// Настройка Sofia URL для номера очереди
            // Вызовы в очередь устанавливаются через loopback
            urlB.setType(1);
            
            // Контекст для loopback
            urlB.setContext("default");
            
            // Устанавливаем префикс для номера очереди. Это нужно для того, 
            // чтобы отличать перезвоны по пропущенным от остальных вызовов.
            urlB.setPrefix(inboundCallPrefix);
            
            // Устанавливаем номер горячей линии
            urlB.setNumber(realBNumber);
            
            // Добавляем переменную канала. Нужно для того, чтобы на АРМ
            // оператора отображался правильный номер абонента.
            urlB.addChannelVariable("origination_caller_id_number", 
                    missedCall.getAnumber());
            
            // Запускаем процесс обратного вызова
            makeReCall(urlB, urlA);
            
            // Отключаемся от FreeSWITCH
            fsi.getFSClient().removeEventListener(listener);
        }
    }
    
    /**
     * Метод определяет логику взаимодействия с Freeswitch для случая, когда
     * совершается обратный вызов по пропущенному звонку. Происходит подключение
     * к Freeswitch, совершаются вызовы по URL sofiaURLQueue и 
     * sofiaURLSubscriber, затем выполняется операция bridge между двумя этими 
     * вызовами. После этого производится отключение от Freeswitch.<br/><br/>
     * 
     * Процедура обратного вызова: <br/>
     * 1. Постановка вызова в очередь. FS производит внутренний вызов на самого 
     * себя. При этом B-leg вызова направялется в стандартный dialplan по 
     * номеру, который совпадает с номером ГЛ, вызов на которую был пропущен.
     * Номер ГЛ определяется параметром sofiaURLQueue. A-leg подключается к 
     * определенной фунции FS, которую можно определить параметром 
     * originatePlaybackCommand в конфигурационном файле (по умолчанию 
     * - &park()). <br/>
     * 2. После того, как вызов был поставлен в очередь, ожидаем ответа 
     * оператора. Когда ответ получен (зафиксирован переход в состояние 2),
     * производится вызов к абоненту. Если ответ не был получен по истечении
     * таймаута, обработка обратного звонка завершается с кодом 2. Т.е. вызов не
     * отмечается как успешный, счетчик попыток перезвона не увеличивается. 
     * Данный вызов будет в любом случае совершен повторно чуть позже. <br/>
     * 3. Совершение вызова абоненту. Если абонент не поднял трубку по истечении
     * таймаута или оператор сбросил вызов, увеличивается счетчик перезвонов по 
     * данному пропущенному вызову. Если абонент ответил, вызов отмечается как
     * успешный. Больше перезвонов по нему не будет.
     * 
     * @param sofiaURLQueue
     * Внутренний URL горячей линии, вызов на которую был пропущен. Формат:
     * loopback/number/local
     * 
     * @param sofiaURLSubscriber
     * URL абонента, вызов которого на ГЛ был пропущен.
     * Формат: sofia/gateway/provider/number
     */  
    private void makeReCall(SofiaURL sofiaURLQueue, SofiaURL sofiaURLSubscriber) {
        
        logger.debug("{}. Start method makeReCall", idString);
        String fsResult;
        
        try {
            
            // Создаем новые UUID
            setQueueUUID(fsi.getNewUUID());
            setSubscriberUUID(fsi.getNewUUID());
            
            // Постановка вызова а очередь.
            setSessionState(0);
            fsResult = fsi.makeCall(sofiaURLQueue, getQueueUUID(), 
                    originatePlaybackCommand);
            
            // Проверяем, установился ли вызов в очередь 
            if (getQueueUUID() == null) {
                logger.info("{}. Fail to put call into queue. Stop processing.", 
                        idString);
                stopSessionProcessing(2, fsResult);
                return;
            }
            
            logger.info(
                    "{}. Call is added to queue. Wait for operator answer...", 
                    idString);
            
            // Ожидаем овтета оператора.
            if (!waitForState(2, operatorAnswerTimeout)) {
                stopSessionProcessing(2, "Fail when wait for operator answer");
                return;
            }
            
            // Совершаем вызов абоненту.
            logger.info("{}. Call to subscriber...", idString);
            fsResult = fsi.makeCall(sofiaURLSubscriber, getSubscriberUUID(), 
                    originatePlaybackCommand);
            
            if (getSubscriberUUID() == null) {
                logger.info(
                        "{}. Fail to call to subscriber: {}. Stop processing.", 
                        idString, fsResult);
                stopSessionProcessing(0, fsResult);
                return;
            }
            
            // Соединяем каналы оператора и абонента
            fsResult = fsi.bridge(getQueueUUID(), getSubscriberUUID());
            
            if (fsResult != null) {
                if (!"+OK".equals(fsResult.split(" ")[0])) {
                    logger.info("{}. Bridge failed: {}. Stop processing.", 
                            idString, fsResult);
                    stopSessionProcessing(0, fsResult);
                    return;
                }
            }
            
            // Ожидаем овтета абонента.
            logger.info("{}. Wait for subscriber answer...", idString);
            if (!waitForState(3, subscriberAnswerTimeout)) {
                stopSessionProcessing(0, "Fail when wait for subscriber answer");
                return;
            }
            
            logger.info("{}. Subscriber answer detected.", idString);
            
            // Абонент ответил.
            // Завершаем сессию обратного вызова
            stopSessionProcessing(1, "Ok");
            
        } catch (ConnectionFailException e) {
            logger.info(e.getMessage());
        }
    }
    
    /**
     * Perform actions for correct stopping processing of recall actions.
     * 
     * @param recallResult
     * Result of recall process<br/>
     * 0 - recall failed. The subscriber didn't answer a call.<br/>
     * 1 - recall success. The subscriber answered a call.<br/>
     * other - recall failed. Failure.<br/>
     * @param resultDesc
     * Описание результата обработки сессии.
     */
    public void stopSessionProcessing(int recallResult, String resultDesc) {
        
        logger.info("{}. Stop session processing. Result: {}; Description: {}", 
                idString, recallResult, resultDesc);
        
        String sUUID = getSubscriberUUID();
        String qUUID = getQueueUUID();
        
        switch (recallResult) {
            
            case 1:
                // Вызов успешен. Отмечаем это в БД.
                logger.debug("{}. Set MC as recalled.", idString);
                dbi.recallSuccess(missedCall.getID());
                break;
                
            case 0:
                // Не дозвонились до абонента. Увеличиваем счетчик попыток.
                logger.debug("{}. Increment recall count.", idString);
                dbi.incrementRecallCount(missedCall);
            
            default:
                // Убиваем каналы, если они все еще открыты.
                logger.debug("{}. Killing UUIDs.", idString);
                if (qUUID != null) {
                    fsi.killUUID(qUUID);
                }

                if (sUUID != null) {
                    fsi.killUUID(sUUID);
                }
        }
        
        // Переводим сессию обратного вызова в неактивное состояние
        setSessionState(-1);
        
        missedCall = null;
        
    }
    
    /**
     * Устанавливает значение sessionState и вызывает метод notifyAll(), что
     * фактически используется для информирования для метода waitForState о том, 
     * что состояние сессии изменилось.
     * 
     * @param sessionState 
     * Новое значение sessionState.
     */
    public synchronized void setSessionState(int sessionState) {
        logger.info("Set session state: {}", sessionState);
        this.sessionState = sessionState;
        notifyAll();
    }
    
    /**
     * Stop processing until sessionState won't become equal to targetState or
     * -1.
     * @param targetState
     * @return 
     * True if sessionState is not -1. False in otherwise.
     */
    private synchronized boolean waitForState(int targetState, int timeout) {
        logger.info("Wait for session state: {}", targetState);
        long beginTime = System.currentTimeMillis();
        
        while (sessionState != targetState && sessionState >= 0) {
            try {
                wait(1000);
                if (timeout*1000 <= System.currentTimeMillis() - beginTime ) {
                    sessionState = -1;
                    logger.info("Operator answer timeout.");
                }
            } catch (InterruptedException ex) {
                logger.debug(ex.getMessage());
            }
        }
        return (sessionState >= 0);
    }
    
    /**
     * Возвращает код текущего состояния сессии обратного вызова.
     * @return 
     * Код текущего состояния сессии обратного вызова. <br/>
     * -1 - сессия не активна; <br/>
     *  0 - выполняется постановка вызова в очередь;<br/>
     *  1 - вызов поставлен в очередь. Ожидание ответа оператора.<br/>
     *  2 - оператор ответил. Ожидание ответа абонента.<br/>
     *  3 - абонент ответил.
     */
    public synchronized int getSessionState() {
        return sessionState;
    }
    
    /**
     * Возвращает UUID канала вызова к абоненту в рамках обратного звонка.
     * @return 
     * UUID вызова к абоненту
     */
    public synchronized String getSubscriberUUID() {
        return subscriberUUID;
    }
    
    /**
     * Возвращает UUID канала внутреннего вызова в колл-центр, произведенного 
     * в рамках процедуры обратного звонка. 
     * 
     * @return 
     * UUID канала внутреннего вызова в колл-центр. Т.е. UUID вызова, 
     * поставленного в очередь 
     */
    public synchronized String getQueueUUID() {
        return queueUUID;
    }
    
    /**
     * Устанавливает новое значение subscriberUUID, т.е. UUID канала вызова к 
     * абоненту. 
     * @param subscriberUUID 
     * UUID канала вызова к абоненту.
     */
    private synchronized void setSubscriberUUID(String subscriberUUID) {
        this.subscriberUUID = subscriberUUID;
    }
    
    /**
     * Устанавливает новое значение queueUUID, т.е. UUID внутреннего вызова в 
     * колл-центр на определенную горячую линию.
     * @param queueUUID 
     * UUID внутреннего вызова в колл-центр на горячую линию, вызов на которую 
     * был пропущен.
     */
    private synchronized void setQueueUUID(String queueUUID) {
        this.queueUUID = queueUUID;
    }
    
    /**
     * Расссылка оповещений по пропущенным вызовам. Производится рассылка сразу 
     * по всем пропущенным вызовам, для которых она необходима. Сообщение
     * содержит номер абонента, вызов от которого был пропущен, номер ГЛ, дату
     * и время вызова. Тема: "Missed call to [название проекта]".
     */
    private void sendEmails() {
        MailSender mailSender = new MailSender(smtpServer, smtpPort, 
                                            mailUser, mailPassword);
        ArrayList<MissedCall> missedCalls = dbi.getNotificationList();
        for (MissedCall mc : missedCalls) {
            EmailNotificationParameters mp = mc.getEmailParameters();
            ArrayList<String> mailList = mc.getEmailParameters().getEmailList();
            for (String mail : mailList) {
                
                String message = 
                        "to: " + mc.getBnumber() + "\n" + 
                        "from: " + mc.getAnumber() + "\n" +
                        "call date:" + mp.getCallDate();
                
                String subject = "Missed call to " + mp.getComment();
                
                mailSender.sendMail(mail, subject, message);
            }
        }
    }
    
    /**
     * Возвращает номер абонента, вызов от которого был пропущен.
     * 
     * @return 
     * Номер абонента.
     */
    public String getSubscriberNumber() {
        return missedCall.getAnumber();
    }
    
    /**
     * Возвращает номер горячей линии, вызов на которую был пропущен.
     * 
     * @return 
     * Номер горячей линии.
     */
    public String getHotlineNumber() {
        return missedCall.getBnumber();
    }
    
    /**
     * Возвращает информацию о сессии обратного вызова.
     * 
     * @return 
     * Строка вида:<br/>
     * ID:[ID пропущенного вызова]; A:[номер абонента, вызов от которого был
     * пропущен]; B:[номер горячей линии, вызов на которую был пропущен] 
     */
    public String getIdString() {
        return idString;
    }
}
