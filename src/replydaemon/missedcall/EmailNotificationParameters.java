/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.missedcall;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Класс предназначен для хранения параметров email оповещений о пропущенных
 * вызовах. Предполагается, что объекты этого класса будут использоваться в 
 * качестве полей класса MissedCall.
 * 
 * @author zh
 */
public class EmailNotificationParameters {
    
    private ArrayList<String> emailList = new ArrayList<>();
    private final String comment;
    private final String callDate;
    
    /**
     * Конструктор для класса EmailNotificationParameters.
     * 
     * @param comment
     * Комментарий. Используется для передачи дополнительной иинформации. 
     * Напрмер, название проекта.
     * 
     * @param emailListString 
     * Строка, содержащая email адреса, разделенные запятыми.
     * 
     * @param callDate
     * Дата и время пропущенного вызова.
     */
    public EmailNotificationParameters(String comment, String emailListString, 
            String callDate) {
        this.comment = comment;
        this.emailList = getEmailList(emailListString);
        this.callDate = callDate;
    }
    
    /**
     * Формирует из строки, представляющей собой список email адресов, 
     * разделенных запятой, список ArrayList из объектов String, содержащих 
     * email адреса.
     * 
     * @param emailListString
     * Список email адресов, разделенных запятой.
     * 
     * @return
     * Объект ArrayList, каждый элемент которого представляет собой строку,
     * содержащую email адрес.
     */
    public static ArrayList<String> getEmailList(String emailListString) {
        
        ArrayList<String> emailList = new ArrayList<>();
        emailList.addAll(Arrays.asList(emailListString.split(",")));
        return emailList;
    }
    
    /**
     * Возвращает объект ArrayList, каждый элемент которого представляет собой 
     * строку, содержащую email адрес.
     * 
     * @return 
     * Объект ArrayList, каждый элемент которого представляет собой 
     * строку, содержащую email адрес.
     */
    public ArrayList<String> getEmailList() {
        return emailList;
    }
    
    /**
     * Возвращает объект comment -- комментарий, содержание которого можно 
     * использовать в теле сообщения.
     * 
     * @return 
     * Объект comment -- комментарий, содержание которого можно 
     * использовать в теле сообщения.
     */
    public String getComment() {
        return comment;
    }
    
    /**
     * Возвращает время пропущенного звонка.
     * 
     * @return 
     * Дата и время пропущенного звонка.
     */
    public String getCallDate() {
        return callDate;
    }
    
    
    @Override
    public String toString() {
        
        String result = "email list: ";
        
        for (String email : emailList) {
            result += email + ";";
        }
        
        result += "\ncomment: " + comment;
        
        return result;
    }
}
