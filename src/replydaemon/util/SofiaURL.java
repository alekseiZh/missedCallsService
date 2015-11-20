/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package replydaemon.util;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author zh
 */
public class SofiaURL {
    
    private HashMap<String, String> channelVariables 
            = new HashMap<>();
    
    private String prefix = "";
    private String gateway = "";
    private String number = "";
    private String context = "";
    
    /*
    0 - gateway
    1 - loopback
    */
    private int type = 0;
    
    public void addChannelVariable(String variableName, String variableValue) {
        channelVariables.put(variableName, variableValue);
    }

    public void setContext(String context) {
        this.context = context;
    }

    public void setType(int type) {
        this.type = type;
    }
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setGateway(String gateway) {
        this.gateway = gateway;
    }

    public void setNumber(String number) {
        this.number = number;
    }
    
    public String constructSofiaURL() {
        String variables = "";
        if (channelVariables.size() > 0) {
            variables = "{";
            for (Map.Entry<String, String> entry : channelVariables.entrySet()) {
                variables += entry.getKey() + "=" + entry.getValue() + ",";
            }
            variables = variables.substring(0, variables.length()-1) + "}";
        }
        
        if (type == 0) {
            return variables + gateway + prefix + number;
        }
        
        return variables + "loopback/" + prefix + number + "/" + context;
    }
}
