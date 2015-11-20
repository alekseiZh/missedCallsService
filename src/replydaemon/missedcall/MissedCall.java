package replydaemon.missedcall;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author zh
 */
public class MissedCall {

    private final int id;
    private final int grID;
    private final String anumber;
    private final String bnumber;
    private final PriorityParameters priorityParameters;
    private final EmailNotificationParameters emailParameters;

    public MissedCall(int id, int grID, String anumber, String bnumber,
            PriorityParameters priorityParameters, 
            EmailNotificationParameters emailParameters) {
        this.id = id;
        this.grID = grID;
        this.anumber = anumber;
        this.bnumber = bnumber;
        this.priorityParameters = priorityParameters;
        this.emailParameters = emailParameters;
    }

    public int getID() {
        return id;
    }

    public int getGrID() {
        return grID;
    }

    public String getAnumber() {
        return anumber;
    }

    public String getBnumber() {
        return bnumber;
    }

    public int getId() {
        return id;
    }

    public PriorityParameters getPriorityParameters() {
        return priorityParameters;
    }

    public EmailNotificationParameters getEmailParameters() {
        return emailParameters;
    }
    
    @Override
    public String toString() {
        
        String result = "id: " + id + 
                "\nanumber: " + anumber + 
                "\nbnumber: " + bnumber + 
                "\ngr_id: " + grID;
        
        if (emailParameters != null) {
            result += "\n" + emailParameters.toString();
        }
        
        return result;
    }

}
