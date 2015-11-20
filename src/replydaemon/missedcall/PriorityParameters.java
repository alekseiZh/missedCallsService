/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.missedcall;

import java.time.Instant;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author zh
 */
public class PriorityParameters {
    
    private final long lastCallDate;
    private final int weight;
    private final int recallCount;
    private final int maxRecallCount;
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    /**
     * Создает новый экземляр класса PriorityParameters.
     * 
     * @param lastCallDate
     * Дата и время пропущенного вызова.
     * 
     * @param weight
     * Вес ГЛ.
     * 
     * @param recallCount
     * Количество совершенных в прошлом перезвонов по данному пропущенному 
     * звонку.
     * 
     * @param maxRecallCount 
     * Максимальное количество перезвонов по пропущенному вызову для данной ГЛ.
     */
    public PriorityParameters(long lastCallDate, 
            int weight, int recallCount, int maxRecallCount) {
        this.lastCallDate = lastCallDate;
        this.weight = weight;
        this.recallCount = recallCount;
        this.maxRecallCount = maxRecallCount;
    }
    
    /**
     * Возвращает дату и время пропущенного вызова.
     * @return 
     * Дата и время пропущенного вызова.
     */
    public long getLastCallDate() {
        return lastCallDate;
    }
    
    /**
     * Возвращает вес ГЛ, вызов на которую был пропущен. Вес определяет 
     * приоритет пропущенного вызова.
     * @return 
     * Вес ГЛ.
     */
    public int getWeight() {
        return weight;
    }
    
    /**
     * Возвращает количество совершенных в прошлом перезвонов по данному 
     * пропущенному звонку.
     * @return 
     * Количество совершенных в прошлом перезвонов по данному пропущенному 
     * звонку.
     */
    public int getRecallCount() {
        return recallCount;
    }
    
    /**
     * Возвращает максимальное количество перезвонов по пропущенному вызову для
     * данной ГЛ.
     * @return 
     * Максимальное количество перезвонов по пропущенному вызову для
     * данной ГЛ.
     */
    public int getMaxRecallCount() {
        return maxRecallCount;
    }
    
    /**
     * Метод используется для расчета приоритета для выбора номера одного из 
     * пропущенных вызовов для перезвона.
     * 
     * @return 
     * Приоритет пропущенного вызова.
     */
    public double getPriority() {
        logger.debug("Start method getPriority");
        
        return weight * ((System.currentTimeMillis() - lastCallDate)/1000/60/30 
                + recallCount / maxRecallCount);
    }
    
    /**
     * На основе параметров приоритета вычисляет время следующей попытки дозвона.
     * 
     * @return 
     * Время следующей попытки обратного вызова.
     */
    public Date getNextCallDate() {
        return Date.from(
                Instant.now().plusSeconds(
                        (recallCount*20+10)*60
                )
        );
    }
    
    public String print() {
        return "Str";
    }
}
