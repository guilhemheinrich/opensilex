//**********************************************************************************************
//                                       Instance.java 
//
// Author(s): Eloan LAGIER
// PHIS-SILEX version 1.0
// Copyright © - INRA - 2017
// Creation date: Decembre, 8 2017
// Contact: eloan.lagier@inra.fr morgane.vidal@inra.fr, anne.tireau@inra.fr, pascal.neveu@inra.fr
// Last modification date: Decembre, 8 2017
// Subject: A class of Instance for the service call
//***********************************************************************************************
package phis2ws.service.view.model.phis;


public class Instance extends InstanceDefinition {

    String type;
    
    public Instance() {
        
    }
    
    public Instance(String uri,String type) {
        super(uri);
        this.type = type;
        
    }
    
    public void setType(String type) {
        this.type = type;
    }
}
