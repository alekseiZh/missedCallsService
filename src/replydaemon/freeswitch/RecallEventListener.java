/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.freeswitch;

import java.util.Map;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import replydaemon.RecallMissedCalls;

/**
 * Вынес данный класс в отдельный файл, т.к. он содержит очень длинный и 
 * некрасивый перегруженный метод eventReceived.
 * 
 * Экземпляр класса выполняет обработку событий, поступающих от Freeswitch.
 * В зависимости от результатов анализа этих событий производится управление 
 * состоянием сессии обратного вызова.
 * 
 * Используются следующие коды состояний:
 * -1 - сессия не активна; <br/>
 *  0 - выполняется постановка вызова в очередь;<br/>
 *  1 - вызов поставлен в очередь. Ожидание ответа оператора.<br/>
 *  2 - оператор ответил. Ожидание ответа абонента.<br/>
 *  3 - абонент ответил.
 * 
 * @author zh
 */
public class RecallEventListener implements IEslEventListener {
    
    private final RecallMissedCalls recallSession;
    private final Logger logger = LoggerFactory.
            getLogger(RecallEventListener.class);
    
    /**
     * Конструктор класса RecallEventListener.
     * 
     * @param recallSession 
     * Экземпляр класса RecallMissedCalls, для которого будет реализована 
     * логика обработки событий.
     */
    public RecallEventListener(RecallMissedCalls recallSession) {
        this.recallSession = recallSession;
    }
    
    @Override
    public void eventReceived(EslEvent event) {
        
        logger.debug(
                "Event: {}; A: {}; B: {}; Caller-Context: {}; Caller-Callee-ID-Name: {}; Channel-Name: {}", 
                event.getEventName(),
                event.getEventHeaders().get("Caller-Caller-ID-Number"),
                event.getEventHeaders().get("Caller-Destination-Number"),
                event.getEventHeaders().get("Caller-Context"),
                event.getEventHeaders().get("Caller-Callee-ID-Name"),
                event.getEventHeaders().get("Channel-Name")
        );
        
        if (recallSession.getSessionState() >= 0) {
            
            String hotLineNumber = recallSession.getHotlineNumber();
            String SubscriberNumber = recallSession.getSubscriberNumber();
            String idString = recallSession.getIdString();
            String queueUUID = recallSession.getQueueUUID();
            String subscriberUUID = recallSession.getSubscriberUUID();
            
            Map<String, String> headers = event.getEventHeaders();

            //Выполняем проверку на повторный вызов со стороны абонента
            if (subcsriberCallAgain(event, hotLineNumber, SubscriberNumber)) {
                logger.info("{}. Subscriber call again. Stop processing.", 
                        idString);
                recallSession.stopSessionProcessing(1, "Subscriber calls again");
                return;
            }

            switch (recallSession.getSessionState()) {

                //Выполняется постановка обратного вызова в очередь
                case 0:
                    if (headers.get("Unique-ID").equals(queueUUID)) {

                        logger.info("{}. Event: {}", idString, 
                                event.getEventName());

                        switch (event.getEventName()) {

                            // Вызов успешно поставлен в очередь
                            case "CHANNEL_ANSWER":
                                logger.info("{}. Call put into queue: ok.", 
                                        idString);
                                recallSession.setSessionState(1);
                                break;

                            // Сбой постановки в очередь
                            case "CHANNEL_HANGUP":
                                logger.info("{}. Call put into queue: fail.", 
                                        idString);
                                recallSession.setSessionState(-1);
                                break;
                        }
                    }
                    break;

                // Вызов в очереди. Ожидание ответа оператора.  
                case 1:

                    // Проверка на сброс вызова в очереди
                    if (headers.get("Unique-ID").equals(queueUUID)) {

                        logger.info("{}. Event: {}", idString, 
                                event.getEventName());

                        if (event.getEventName().equals("CHANNEL_HANGUP")) {
                            logger.info("{} Call in queue discarded.", 
                                    idString);
                            recallSession.setSessionState(-1);
                        }

                    // Проверка на ответ оператора    
                    } else if (operatorAnswer(event, SubscriberNumber)) {

                        logger.info("{}. Operator {} answer: ok", 
                                idString, headers.get("Channel-Name"));

                        recallSession.setSessionState(2);
                    }
                    break;

                // Оператор ответил. Ожидание ответа абонента.
                case 2:

                    // Событие, относящееся к нашему вызову
                    if (headers.get("Unique-ID").equals(subscriberUUID)) {

                        logger.info("{}. Event: {}", idString, 
                                event.getEventName());

                        switch (event.getEventName()) {
                            // Зафиксирован ответ
                            case "CHANNEL_ANSWER":
                                logger.info("{}. Subscriber answer: ok", 
                                        idString);
                                recallSession.setSessionState(3);
                                break;
                            // Зафиксирован сброс
                            case "CHANNEL_HANGUP":
                                logger.info("{}. Subscriber answer: fail", 
                                        idString);
                                recallSession.setSessionState(-1);
                                break;
                        }
                    } 

                    break;

                default:
                    break;
            }
        }
    }

    @Override
    public void backgroundJobResultReceived(EslEvent event) {
        throw new UnsupportedOperationException("Not supported yet."); 
        //To change body of generated methods, choose Tools | Templates.
    }
    
    /**
     * Метод используется проверки того, что поступил повторный вызов от 
     * абонента во время обработки сессии обратного звонка.
     * 
     * @param event
     * Обрабатывваемое событие от FS.
     * 
     * @param hotLineNumber
     * Номер горячей линии, вызов на которую был пропущен.
     * 
     * @param SubscriberNumber
     * Номер абонента.
     * 
     * @return 
     * Возвращает true, если было зафиксировано поступление события 
     * CHANNEL_ANSWER. При этом заголовок Caller-Destination-Number совпадает
     * с номером горячей линии, а Caller-Caller-ID-Number с номером абонента.
     */
    private static boolean subcsriberCallAgain(EslEvent event, String hotLineNumber,
            String SubscriberNumber) {
        
        Map<String, String> headers = event.getEventHeaders();
        
        return event.getEventName().equals("CHANNEL_ANSWER") 
                    && headers.get("Caller-Destination-Number").
                            equals(hotLineNumber)
                    && headers.get("Caller-Caller-ID-Number").
                            equals(SubscriberNumber);
    }
    
    /**
     * Проверка на предмет ответа абонента на обратный звонок, поставленный в 
     * очередь. В рассматриваемой системе колл-центра определить, поступил ли 
     * ответ со стороны одного из операторов по данному пропущенному вызову, 
     * можно проанализировав заголовки Caller-Caller-ID-Number,
     * Caller-Callee-ID-Name и Caller-Context события CHANNEL_ANSWER.
     * Для того, чтобы метод вернул true, должны бать соблюдена следующая их
     * конфигурация:
     * <br/>
     * <b>Caller-Caller-ID-Number = [номер абонента, вызов от которого был 
     * пропущен]</b>. Caller ID вызовов, поступающих операторам в системе 
     * Call-o-Call всегда совпадает с номером вызывающего абонента.
     * <br/>
     * <b>Caller-Callee-ID-Name = Outbound Call</b>. В текущей конфигурации 
     * FSTelserv данный параметр при вызове к оператору имеет значение 
     * "Outbound Call".
     * <br/>
     * <b>Caller-Context = default</b>. В текущей конфигурации FSTelserv вызовы 
     * операторам осуществляются через контекст default.
     * 
     * @param event
     * Событие FS.
     * 
     * @param subscriberNumber
     * Номер абонента, вызов от которого был пропущен.
     * 
     * @return 
     * true, если зафиксирован ответ оператора. false -  в противном случае.
     */
    private static boolean operatorAnswer(EslEvent event, String subscriberNumber) {
        
        Map<String, String> headers = event.getEventHeaders();
        
        return headers.get("Caller-Caller-ID-Number").equals(subscriberNumber) 
                && headers.get("Caller-Callee-ID-Name").equals("Outbound Call")
                && headers.get("Caller-Context").equals("default") 
                && event.getEventName().equals("CHANNEL_ANSWER");
    }
}
