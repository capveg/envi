package org.openflow.gui.fv;

import org.openflow.gui.ConnectionHandler;
import org.openflow.gui.drawables.Node;
import org.openflow.gui.net.protocol.NodeType;

/**
 * The FlowVisor GUI individual connection handler.  Just a thin wrapper at the
 * moment.
 * 
 * @author David Underhill
 */
public class FVConnectionHandler extends ConnectionHandler {
    /**
     * Construct the front-end for EXConnectionHandler.
     * 
     * @param manager the manager responsible for drawing the GUI
     * @param server  the IP or hostname where the back-end is located
     * @param port    the port the back-end is listening on
     */
    public FVConnectionHandler(FVLayoutManager manager, String server, Short port, String title) {
        super(new FVTopology(manager, server + ":" + port), server, port, true, true);
        manager.addDisplaySlice((FVTopology)getTopology(),title);
    }
    
    /** 
     * Calls super.connectionStateChange() and then does some custom processing.
     */
    public void connectionStateChange(boolean connected) {
        super.connectionStateChange(connected);
        
        if(!getConnection().isConnected()) {
            // TODO: we just got disconnected - maybe need to do some cleanup
        }
    }

    /** override to ignore hosts (short-term) */
    protected Node processNodeAdd(org.openflow.gui.net.protocol.Node n) {
        if(n.nodeType == NodeType.HOST)
            return null; // ignore hosts
        else
            return super.processNodeAdd(n);
    }
}