package org.openflow.lavi.drawables;

import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.Vector;

/**
 * Information about a node with ports in the topology.
 * @author David Underhill
 */
public abstract class NodeWithPorts extends Node {
    private final Vector<Link> links = new Vector<Link>();
    
    public NodeWithPorts(String name, int x, int y) {
        super(name, x, y);
    }
    
    public void unsetDrawn() {
        super.unsetDrawn();
        for(Link l : links)
            l.unsetDrawn();
    }
    
    private class LongPair {
        public final long a, b;
        public LongPair(long l1, long l2) { 
            if(l1 < l2) {
                a = l1;
                b = l2;
            }
            else {
                a = l2;
                b = l1;
            }
        }
        public int hashCode() {
            return 7 * new Long(a).hashCode() + new Long(b).hashCode();
        }
        public boolean equals(Object o) {
            if(o == null) return false;
            if(!(o instanceof LongPair)) return false;
            LongPair l = (LongPair)o;
            return l.a==a && l.b==b;
        }
    }
    
    public void drawLinks(Graphics2D gfx) {
        HashMap<LongPair, Integer> dpidToCount = new HashMap<LongPair, Integer>();
        Integer count;
        
        // draw the port's links
        for(Link link : links) {
            LongPair lp = new LongPair(link.getSource().getDatapathID(),
                                       link.getDestination().getDatapathID());
            
            count = dpidToCount.get(lp);
            if(count == null)
                count = 0;
            
            link.setOffset(count);
            link.drawObject(gfx);
            
            dpidToCount.put(lp, count + 1);
        }
    }
    
    void addLink(Link l) {
        links.add(l);
    }
    
    public Link getLink(int i) {
        return links.get(i);
    }
    
    public java.util.Collection<Link> getLinks() {
        return links;
    }
    
    /** Returns the number of links connected to this node. */
    public int getNumLinks() {
        return links.size();
    }
    
    /** Returns a link from this node to the requested node if such a link exists */
    public Link getLinkTo(NodeWithPorts n) {
        for(Link l : links)
            if(l.getOther(this) == n)
                return l;
            
        return null;
    }

    /** Returns a link from this node to the requested node if such a link exists */
    public Link getLinkTo(short myPort, NodeWithPorts n, short nPort) {
        for(Link l : links)
            if(l.getOther(this) == n)
            	if(l.getMyPort(this)==myPort && l.getOtherPort(n)==nPort)
            		return l;
            
        return null;
    }
    
    /** Gets a link to a neighboring OpenFlowNode with the specified datapath ID. */
    public Link getLinkTo(long dpid) {
        for(Link l : links) {
            NodeWithPorts o = l.getOther(this);
            if(o!=null && o.getDatapathID()==dpid)
                return l;
        }
        
        return null;
    }
    
    public abstract long getDatapathID();
    
    public int hashCode() {
    	return (int)(getDatapathID() ^ (getDatapathID() >>> 32));
    }
    
    public boolean equals(Object o) {
    	if(this == o) return true;
    	if((o == null) || (o.getClass() != this.getClass())) return false;
    	NodeWithPorts n = (NodeWithPorts)o;
    	return n.getDatapathID() == getDatapathID();
    }
}