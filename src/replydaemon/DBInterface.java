package replydaemon;


import replydaemon.missedcall.MissedCall;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import replydaemon.missedcall.EmailNotificationParameters;
import replydaemon.missedcall.PriorityParameters;
import static replydaemon.util.Functions.getMissedCallWithHighestPriority;
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author zh
 */
public class DBInterface {
    
    private final String connString;
    private Connection conn;
    
    private final int maxConnectAtemmpts = 2;
    private final int maxConnectTimeout = 10000;
    private boolean reconnectFlag;
    private final boolean endlessReconnect = true;
    private final Logger logger = LoggerFactory.getLogger(DBInterface.class);
    
    /**
     * 
     * @param connString 
     * jdbc connection string.
     * Example:
     * jdbc:jtds:sqlserver://127.0.0.1:1433;databaseName=Callcenter;user=callcenter;password=call;
     */
    public DBInterface(String connString) {
        this.connString = connString;
    }
    
    /**
     * Connect to DB. If connection attempt is failed, reconnect is perform.
     * 
     * @param connString
     * jdbc connection string.
     * Example:
     * jdbc:jtds:sqlserver://127.0.0.1:1433;databaseName=Callcenter;user=callcenter;user=callcenter;
     */
    private void connectToDB(String connString) {
        
        logger.debug("Start method: connectToDB");
        
        int connectAttempt = 0;
        int connectTimeout = 500;
        reconnectFlag = true;
        
        while (reconnectFlag) {
            
            if (++connectAttempt > maxConnectAtemmpts && !endlessReconnect) {
                logger.info("Max number of reconnect attempt distinguished.");
                throw new ConnectionFailException("We cannot connect to freeswitch...");
            }
            
            logger.debug("Connect to DB,attempt {}, connection string: {}", 
                connectAttempt, connString);
            
            try {
                conn = DriverManager.getConnection(connString);
                reconnectFlag = false;
            
            } catch (SQLException e) {
                if (e.getErrorCode() == 0) {

                    logger.debug("Connection to DB failed");

                    if (connectTimeout < maxConnectTimeout) {
                        connectTimeout = connectTimeout * 2;
                        if (connectTimeout > maxConnectTimeout) {
                            connectTimeout = maxConnectTimeout;
                        }
                    }

                    logger.debug("pause: {} ms", connectTimeout);

                    try {
                        Thread.sleep(connectTimeout);
                    } catch (InterruptedException ex) {
                        logger.debug(ex.getMessage());
                    }

                } else {
                    logger.debug("Connection failed. Stopping daemon.");
                    throw new ConnectionFailException();
                }
            }
        }
    }
    
    
    /**
     * Immediately stop reconnect to server process.
     */
    public void stopReconnect() {
        logger.debug("Start method stopReconnect.");
        reconnectFlag = false;
    }
    
    /**
     * Connect to DB and execute stored procedure reply_online_oper_for_group,
     * which return operators ids with "online" status for specific group. 
     * Return number of operators, which have online status.
     * 
     * @param grID
     * id of group which operators status checking perform.
     * 
     * @return
     * Number of operators that have idle status.
     */
    public int numberOfIdleOpers(int grID) {
        
        logger.debug("Start method isThereIdleOper");
        
        connectToDB(connString);
        
        int idleOpers = 0;
        
        try {
            
            String query = "EXEC [reply_online_oper_for_group] @groupid = " 
                    + grID;
            logger.info("Is there idle opers?");
            
            ResultSet resultSet = conn.createStatement().executeQuery(query);
            
            while (resultSet.next()) {                
                idleOpers++;
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        logger.info("Number of idle opers: {}", idleOpers);
        return idleOpers;
    }
    
    /**
     * Connect to DB and execute stored procedure reply_online_oper_for_group,
     * which return operators ids with "online" status for specific group.
     * 
     * @param grID
     * id of group which operators status checking perform.
     * 
     * @return 
     * Array, that contains ids of idle opers.
     */
    public ArrayList<Integer> getIdleOpers(int grID) {
        
        logger.debug("Start method isThereIdleOper");
        
        connectToDB(connString);
        
        ArrayList<Integer> idleOpers = new ArrayList<>();
        
        try {
            
            String query = "EXEC [CallCenter].[dbo].[reply_online_oper_for_group] @groupid = " 
                    + grID;
            logger.info("Is there idle opers?");
            
            ResultSet resultSet = conn.createStatement().executeQuery(query);
            
            while (resultSet.next()) {                
                idleOpers.add(resultSet.getInt("OP_ID"));
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        logger.info("Idle opers: {}", idleOpers);
        
        return idleOpers;
    }
    
    /**
     * Connect to Call-o-Call DB and execute stored procedure 
     * reply_mc_get_next_missed_call. After processing result of procedure
     * return MissedCall object, which contain info about missed call. If no
     * missed call is found return Null.
     * 
     * @return 
     * MissedCall or Null, if no missed calls is found.
     */
    public MissedCall getMissedCall() {
        
        logger.debug("Start method getMissedCalls");
        
        connectToDB(connString);
        
        ArrayList<MissedCall> missedCalls = new ArrayList<>();
        
        try {
            
            logger.info("Try to get next missed call info from DB...");
            String query = "EXEC [reply_mc_get_missed_calls]";
            ResultSet resultSet = conn.createStatement().executeQuery(query);
            
            while (resultSet.next()) {
                
                MissedCall missedCall = new MissedCall(
                        
                        resultSet.getInt("ID"), 
                        resultSet.getInt("GR_ID"),
                        resultSet.getString("ANUMBER"),
                        resultSet.getString("BNUMBER"),
                        
                        new PriorityParameters(
                                resultSet.getDate("LC_DATE").getTime(), 
                                resultSet.getInt("PRIORITY"), 
                                resultSet.getInt("RECALL_COUNT"), 
                                resultSet.getInt("MAX_RECALL")
                        ),
                        // В данном случае информация о email оповещениях не требуется
                        null
                );
                
                missedCalls.add(missedCall);
                
                logger.debug("Missed call detected: ID:{}; A:{}; B:{}; GrID:{}.",
                        missedCall.getID(), missedCall.getAnumber(), 
                        missedCall.getBnumber(), missedCall.getGrID());
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage());
            
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        if (missedCalls.size() > 0) {
            return getMissedCallWithHighestPriority(missedCalls);
        } else {
            logger.debug("Missed calls not found!");
            return null;
        }
    }
    
    /**
     * 
     * @return 
     */
    public ArrayList<MissedCall> getNotificationList() {
        
        logger.debug("Start method getNotificationList");
        
        connectToDB(connString);
        
        ArrayList<MissedCall> missedCalls = new ArrayList<>();
        
        try {
             logger.info("Try to notification from DB...");
            String query = "EXEC [reply_mc_get_email_list]";
            ResultSet resultSet = conn.createStatement().executeQuery(query);
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            while (resultSet.next()) {
                
                MissedCall missedCall = new MissedCall(
                        
                        resultSet.getInt("ID"), 
                        //Код группы операторов не требуется
                        0,
                        resultSet.getString("ANUMBER"),
                        resultSet.getString("BNUMBER"),
                        
                        // В данном случае информация о приоритете не требуется
                        null,
                        
                        new EmailNotificationParameters(
                                resultSet.getString("COMMENT"), 
                                resultSet.getString("EMAIL_LIST"),
                                dateFormat.format(
                                        resultSet.getTimestamp("CALL_DATE")
                                )
                        )
                );
                
                missedCalls.add(missedCall);
                
                logger.info("Notification need: {} {} {} {}",
                        missedCall.getAnumber(), missedCall.getBnumber(),
                        missedCall.getID(), missedCall.getGrID());
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage());
            
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        return missedCalls;
    }
    
    /**
     * Connect to Call-o-Call DB and execute stored procedure 
     * reply_mc_missed_calls, that mark missed call instance in DB table as 
     * succefull recalled.
     * 
     * @param id 
     * Identifier of missed call instance in DB table.
     */
    public void recallSuccess(int id) {
        
        logger.debug("Start method recallSuccess");
        
        connectToDB(connString);
        
        try {
            
            logger.info("Trying to mark missed call as recalled...");
            String query = "UPDATE [CallCenter].[dbo].[reply_mc_missed_calls] "
                    + "SET DONE = 1 WHERE ID = " + id;
            conn.createStatement().executeUpdate(query);
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        logger.info("recallSuccess: Ok");
    }
    
    /**
     * Connect to Call-o-Call DB and execute stored procedure 
     * reply_mc_update_missed_calls, which perform update of table 
     * reply_mc_missed_calls. Updated line specified by id argument. Procedure
     * perform increment of field RECALL_COUNT.
     * 
     * @param missedCall 
     * id of line in reply_mc_missed_calls table.
     */
    public void incrementRecallCount(MissedCall missedCall) {
        
        logger.debug("Start method incrementRecallCount");
        
        int id = missedCall.getID();
        
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String nextCallDate = dateFormat.format(
                missedCall.getPriorityParameters().getNextCallDate()
        );
        
        connectToDB(connString);
        
        try {
            
            logger.debug("Trying to increment recall attempt count for id {} ...",
                    id);
            String query = "EXEC [Callcenter].[dbo].[reply_mc_update_missed_calls] "
                    + "@missed_call_id = " + id
                    + ", @nc_date = '" + nextCallDate + "'";
            logger.debug("Query: {}", query);
            conn.createStatement().execute(query);
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        logger.info("increment recall count for missed call {}: Ok", id);
    }
    
    /**
     * Connect to Call-o-Call DB and execute stored procedure update_operStatus, 
     * which set operator's status to specific value.
     * 
     * @param opID
     * ID of operator.
     * 
     * @param status
     * New status:
     * 0 - online
     * 1 - busy
     * 2 - pause
     * 3 - offline
     */
    public void setOperStatus(int opID, int status) {
        logger.debug("Start method setOperStatus");
        
        connectToDB(connString);
        
        try {
            
            logger.info("Trying to set status {} for operator {} ...", status,
                    opID);
            String query = "DECLARE @a INTEGER, @b INTEGER; "
                    + "EXEC [CallCenter].[dbo].[update_operStatus] "
                    + "@i_opId = " + opID
                    + ", @i_status = " + status
                    + ", @c_reason = 'jhgjhg', "
                    + "@i_realStatus = @a OUTPUT, "
                    + "@i_pauseTime = @b OUTPUT";
            
            conn.createStatement().execute(query);
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
    }
    
    /**
     * Отмечает пропущенные вызовы как прозвоненные, если их возраст превышает 
     * пороговый.
     * 
     * @param age 
     * Возраст пропущенного звонка в секундах, при превышении которого 
     * пропущенный вызов должен быть почечен как прозвоненный.
     */
    public void removeOldMissedCalls(int age) {
        logger.debug("Start method removeOldMissedCalls");
        
        connectToDB(connString);
        
        try {
            
            logger.info("Trying to remove old missed calls ...");
            
            String query = 
                    "UPDATE [CallCenter].[dbo].[reply_mc_missed_calls]" +
                    "SET [DONE] = 1" +
                    "WHERE DATEDIFF(second, CALL_DATE, GETDATE()) > " + age;
            
            conn.createStatement().execute(query);
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
    }
    
    /**
     * Возвращает маршрут полученный на основании анализа номеров numberA и 
     * numberB. Метод обращается к таблице [CallCenter].[dbo].[reply_mc_routing]
     * , используя хранимую процедуру [CallCenter].[dbo].[reply_mc_get_route].
     * Процедура возвращает одну строку, в состав которой входит только поле
     * SOFIA_URL. Значение, хранимое в данном поле, как правило предтавлено
     * в следующем формате: <br/>
     * sofia/gateway/[provider]/<br/>
     * где provider - наименование gateway в FreeSwitch.<br/>
     * Данный формат крайне рекомендуется к использованию, однако на самом деле
     * никаких ограничений к содержимому поля SOFIA_URL не предъявляется.<br/>
     * <br/>
     * Схема выбора маршрута из таблицы проста:<br/>
     * 1. Строки таблицы сортируются по приоритету от большего к меньшему. 
     * Для этого используется поле PRIORITY. <br/>
     * 2. Выбирается первая строка, в которой поле PREFIXA является префиксом 
     * для номера numberA, а поле PREFIXB является префиксом для номера numberB.
     * <br/>
     * 3. Если поиск не увенчался успехом, возвращается NULL.
     * 
     * @param numberA
     * Номер А для поиска маршрута.
     * 
     * @param numberB
     * Номер Б для поиска маршрута.
     * 
     * @return 
     * Маршрут для распределения вызова. Если поиск не увенчался успехом, 
     * возвращается null.
     */
    public String getRoute(String numberA, String numberB) {
        logger.debug("Start method getRoute");
        
        connectToDB(connString);
        
        try {
            
            logger.info("Check routing table: prefixa:{}; prefixb: {} ...", 
                    numberA, numberB);
            
            String query = "EXEC [CallCenter].[dbo].[reply_mc_get_route] "
                    + "@prefixa = " + numberA
                    + ",@prefixb = " + numberB;
            
            ResultSet resultSet = conn.createStatement().executeQuery(query);
            
            if (resultSet.next()) {
                logger.info("The route is found: {}", 
                        resultSet.getString("SOFIA_URL"));
                return resultSet.getString("SOFIA_URL");
            }
            
        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (SQLException e) {
                logger.error(e.getMessage());
            }
        }
        
        logger.info("The route isn't found.");
        
        return null;
    }
}
