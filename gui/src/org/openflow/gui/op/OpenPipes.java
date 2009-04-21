package org.openflow.gui.op;

import java.util.ArrayList;

import org.openflow.gui.ConnectionHandler;
import org.openflow.gui.OpenFlowGUI;
import org.openflow.gui.Options;
import org.openflow.gui.op.OPConnectionHandler;
import org.openflow.gui.op.OPLayoutManager;

public final class OpenPipes {
    /** cannot instantiate this class */
    private OpenPipes() {}
    
    /** run the front-end */
    public static void main(String args[]) {
        String server = OpenFlowGUI.getServer(args);
        Short port = Options.DEFAULT_PORT;
        
        // create a manager to handle drawing the topology info received by the connection
        OPLayoutManager gm = new OPLayoutManager();
        gm.loadDrawablePositionsFromFile("op.yaml");
        ArrayList<Class> drawOrder = new ArrayList<Class>();
        drawOrder.add(org.openflow.gui.drawables.OPModule.class);
        drawOrder.add(org.openflow.gui.drawables.OPNodeWithNameAndPorts.class);
        gm.setDrawOrder(drawOrder);
        
        // create a manager to handle the connection itself
        ConnectionHandler cm = new OPConnectionHandler(gm, server, port);
        
        // start our managers
        gm.start();
        cm.getConnection().start();
    }
}