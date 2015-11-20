/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import replydaemon.missedcall.MissedCall;

/**
 *
 * @author zh
 */
public class Functions {
    
    private static final Logger logger = LoggerFactory.getLogger(Functions.class);
    
    /**
     * Метод используется для преобразования строки вида "(/+7|7|8)?([0-9]{10})"
     * к виду $2. Предполагается, что параметр number представляет собой 
     * телефонный номер представленый строкой вышеозначенного вида. Для
     * совепшения исходящего вызова рекомендуется использовать всегда одинаковый
     * формат номера. 
     * 
     * @param number
     * Строка, представляющая собой номер телефона.
     * 
     * @param prefix
     * Префикс, подставляемый перед $2. 
     * Пример:
     * Вызов метода numberCorrection("+79215789654", "8") вернет строку
     * "89215789654". В данном случае $2 = "9215789654".
     * 
     * 
     * @return 
     * Телефонный номер вида 8[0-9]{10}. В случае если параметр number не 
     * соответствует вышеозначенному шаблону, возвращается null. 
     */
    public static String numberCorrection(String number, String prefix) {
        
        Pattern pattern = Pattern.compile("(/+7|7|8)?([0-9]{10})");
        Matcher matcher = pattern.matcher(number);
        
        if (matcher.matches()) {
            return matcher.replaceAll(prefix + "$2");
        }
        
        return null;
    }
    
    /**
     * Осуществляет поиск по списку пропущенного вызова с наивысшим приоритетом
     * обратного вызова.
     * 
     * @param missedCalls
     * Список объектов MissedCalls.
     * 
     * @return 
     * Объект MissedCall  с наивысшим приоритетом. 
     */
    public static MissedCall 
        getMissedCallWithHighestPriority(ArrayList<MissedCall> missedCalls) {
        
        logger.debug("Start method getMissedCallWithHighestPriority");
        
        double highestPriority = -1;
        MissedCall resultmc = null;
        
        for (MissedCall missedCall : missedCalls) {
            double priority = missedCall.getPriorityParameters().getPriority();
            if (priority > highestPriority) {
                highestPriority = priority;
                resultmc = missedCall;
            }
        }
        
        if (resultmc != null) {
            logger.debug("MC with highest priority: {} {} ({})", 
                    resultmc.getID(), resultmc.getAnumber(), highestPriority);
        }
        
        return resultmc;
    }
        
    /**
     * Загружает параметры из файла fullPath.
     * @param fullPath
     * fullPath - путь к файлу, из которого загружаются параметры.
     * @return 
     * Объект, содержащий параметры.
     */
    public static Properties loadConfigFromFile(String fullPath) {
        
        Properties prop = new Properties();
        
        if (fullPath == null) {
            logger.info("Load config from config.properties...");
            try {
                prop.load(new FileInputStream(new File("config.properties")));
                logger.info("Load config from config.properties: Ok.");
            } catch (Exception e) {
                logger.info(
                        "Problem with config file config.properties: {}. {}", 
                        e.getMessage(), "Load default values."
                );
            }
        } else {
            logger.info("Load config from {} ...", fullPath);
            try {
                prop.load(new FileInputStream(new File(fullPath)));
                logger.info("Load config from {}: Ok.", fullPath);
            } catch (IOException ex) {
                logger.info("Problem with config file {}: {}. {}", 
                        fullPath, ex.getMessage(), 
                        "Try to load config.properties...");
                try {
                    prop.load(new FileInputStream(new File("config.properties")));
                    logger.info("Load config from config.properties: Ok.");
                } catch (Exception e) {
                    logger.info(
                            "Problem with config file config.properties: {}. {}", 
                            ex.getMessage(), "Load default values."
                    );
                }
            }
        }
        
        return prop;
    }
}
