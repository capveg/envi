package org.openflow.lavi.drawables;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import org.pzgui.Constants;
import org.pzgui.Drawable;
import org.pzgui.math.Vector2f;

/**
 * Information about a link.
 * @author David Underhill
 */
public class Link extends Drawable {
    public static final int LINE_WIDTH = 3;
    public static final BasicStroke LINE_DEFAULT_STROKE = new BasicStroke(LINE_WIDTH, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    public static final BasicStroke LINE_OUTLINE_STROKE = new BasicStroke(LINE_WIDTH+4, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
    
    /** link endpoints */
    protected NodeWithPorts src;
    protected NodeWithPorts dst;
    protected short srcPort;
    protected short dstPort;
    
    private int numOtherLinks = 0;
    private Polygon boundingBox = null;
    private boolean hovering = false;
    
    /**
     * Constructs a new uni-directional link between src and dst.
     * @param src                The source of data on this link.
     * @param dst                The endpoint of this link.
     */
    public Link(NodeWithPorts dst, short dstPort, NodeWithPorts src, short srcPort) {
        if(src == dst)
            throw new Error("Error: src == dst in Link constructor (" + src.toString() + ")");
        
        this.src = src;
        this.dst = dst;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        
        src.addLink(this);
        dst.addLink(this);
    }
    
    private static final Color computeGradient(int type, float goodness) {
        if(goodness < 0)
            goodness = 0;
        else if (goodness > 1)
            goodness = 1;
        
        return new Color((type % 3 == 0) ? goodness : 0f,
                          (type % 3 == 1) ? goodness : 0f, 
                          (type % 3 == 2) ? goodness : 0f);
    }
    
    public void drawObject(Graphics2D gfx) {
        Stroke s = gfx.getStroke();
        
        int ocount = ((numOtherLinks+1)/2)*2;
        if(ocount == numOtherLinks)
            ocount = -ocount;
        
        int offsetX = 10 * ocount;
        int offsetY = 3 * ocount;
        
        updateBoundingBox(src.getX()+offsetX, src.getY()+offsetY, 
                          dst.getX()+offsetX, dst.getY()+offsetY);
        
        Paint c = Constants.PAINT_DEFAULT;
        if(numOtherLinks > 0)
            c = computeGradient(numOtherLinks, numOtherLinks / 24.0f + 0.25f);
        
        // outline the link if it is being hovered over
        if(hovering) {
            gfx.draw(boundingBox);
            gfx.setPaint(Constants.COLOR_HOVERING);
            gfx.fill(boundingBox);
        }
        
        // draw the simple link as a line
        gfx.setStroke(LINE_DEFAULT_STROKE);
        gfx.setPaint(c);
        gfx.drawLine(src.getX()+offsetX, src.getY()+offsetY, 
                     dst.getX()+offsetX, dst.getY()+offsetY);
        
        // restroe the defaults
        gfx.setStroke(s);
        gfx.setPaint(Constants.PAINT_DEFAULT);
    }
    
    private void updateBoundingBox(int x1, int y1, int x2, int y2) {
        Vector2f from = new Vector2f(x1, y1);
        Vector2f to = new Vector2f(x2, y2);
        Vector2f dir = Vector2f.subtract			(to, from);
        Vector2f unitDir = Vector2f.makeUnit(dir);
        Vector2f perp = new Vector2f(unitDir.y, -unitDir.x).multiply(LINE_WIDTH / 2.0f + 4.0f);

        // build the bounding box
        float o = LINE_WIDTH / 2.0f;
        int[] bx = new int[]{ (int)(x1 - perp.x + o), (int)(x1 + perp.x + o), (int)(x2 + perp.x + o), (int)(x2 - perp.x + o) };
        int[] by = new int[]{ (int)(y1 - perp.y + o), (int)(y1 + perp.y + o), (int)(y2 + perp.y + o), (int)(y2 - perp.y + o) };
        boundingBox = new Polygon(bx, by, bx.length);
    }

    /** Disconnects this link from its attached ports */
    public void disconnect() {
        src.getLinks().remove(this);
        dst.getLinks().remove(this);
    }
    
    public NodeWithPorts getSource() {
        return src;
    }
    
    public NodeWithPorts getDestination() {
        return dst;
    }
    
    public NodeWithPorts getOther(NodeWithPorts p) {
        // throw an error if n is neither src nor dst
        if(src!=p && dst!=p)
            throw new Error("Link::getOther Error: neither src (" + src
                    + ") nor dst (" + dst + ") is p (" + p + ")");
        
        return dst==p ? src : dst;
    }
    
	public short getMyPort(NodeWithPorts p) {
		if(src == p)
			return srcPort;
		else
			return dstPort;
	}
	
	public short getOtherPort(NodeWithPorts p) {
		if(src == p)
			return dstPort;
		else
			return srcPort;
	}
    
    public int hashCode() {
    	int hash = 7;
    	
    	hash += dst.hashCode();
    	hash += 31 * dstPort;
    	hash += 31 * src.hashCode();
    	hash += 31 * srcPort;
    	
    	return hash;
    }
    
    public boolean equals(Object o) {
    	if(this == o) return true;
    	if((o == null) || (o.getClass() != this.getClass())) return false;
    	Link l = (Link)o;
    	return l.dst.getDatapathID() == dst.getDatapathID() &&
    	       l.dstPort == dstPort &&
    	       l.src.getDatapathID() == src.getDatapathID() &&
    	       l.srcPort == srcPort;
    }
    
    public String toString() {
        return src.toString() + " ===> " + dst.toString();
    }
    
    public boolean isWithin(int x, int y) {
        if(boundingBox == null)
            return false;
        else
            return boundingBox.contains(x, y);
    }

    void setOffset(int numOtherLinks) {
        this.numOtherLinks = numOtherLinks;
    }

    public boolean isHovered() {
        return hovering;
    }
    
    public void setHovered(boolean b) {
        hovering = b;
    }
}