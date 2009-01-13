package org.openflow.lavi.drawables;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.geom.Ellipse2D;
import org.openflow.util.string.DPIDUtil;
import org.pzgui.Constants;

/**
 * Describes an OpenFlow node.
 * @author David Underhill
 */
public class OpenFlowSwitch extends NodeWithPorts {
    java.awt.Dimension SIZE = new java.awt.Dimension(5, 5);
    public static final Color NAME_COLOR = new Color(222, 222, 222);
    
    private long datapathID;
    private static final double OUTLINE_RATIO = 4.0 / 3.0;
    
    public OpenFlowSwitch(long dpid) {
        this("", 0, 0, dpid);
    }
    
    public OpenFlowSwitch(String name, int x, int y, long dpid) {
        super(name, x, y);
        this.datapathID = dpid;
    }

    /** Move the switch when it is dragged */
    public void drag(int x, int y) {
        setPos(x, y);
    }

    public void drawBeforeObject(Graphics2D gfx) {
        drawLinks(gfx);
    }

    public void draw(Graphics2D gfx) {
                 Paint outlineColor;
        if(isSelected())
            outlineColor = Constants.COLOR_SELECTED;
        else if(isHovered())
            outlineColor = Constants.COLOR_HOVERING;
        else
            outlineColor = null;
        
        if(outlineColor != null) {
            double w = SIZE.width * OUTLINE_RATIO;
            double h = SIZE.height * OUTLINE_RATIO;
            Ellipse2D.Double outline = new Ellipse2D.Double(getX()-w/2, getY()-h/2, w, h);
            
            gfx.draw(outline);
            gfx.setPaint(outlineColor);
            gfx.fill(outline);
            gfx.setPaint(Constants.PAINT_DEFAULT);
        }
        
        gfx.drawOval(getX(), getY(), SIZE.width, SIZE.height);
        
        gfx.setPaint(NAME_COLOR);
        int textYOffset = -SIZE.height / 2 + 2;
        drawName(gfx, getX(), getY() - textYOffset, getY() + textYOffset);
    }
    
    public java.awt.Dimension getSize() {
        return SIZE;
    }
    
    public String getDebugName() {
        return DPIDUtil.dpidToHex(datapathID);
    }
    
    public long getDatapathID() {
        return datapathID;
    }
    
    public boolean isWithin(int x, int y) {
        return isWithin(x, y, getSize());
    }

    public void setDatapathID(long dpid) {
        this.datapathID = dpid;
    }
    
    public String toString() {
        return getName() + "; dpid=" + DPIDUtil.dpidToHex(getDatapathID());
    }
}
