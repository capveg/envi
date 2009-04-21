package org.openflow.gui.drawables;

import java.awt.Color;
import java.awt.Graphics2D;

import org.openflow.gui.net.protocol.NodeType;
import org.pzgui.icon.Icon;

public class OPModule extends OPNodeWithNameAndPorts {
    /**
     * Creates a new "original" module (isOriginal() will return true).
     */
    public OPModule(boolean hw, String name, long id, Icon icon) {
        super(hw ? NodeType.TYPE_MODULE_HW : NodeType.TYPE_MODULE_SW, name, id, icon);
        setNameColor(Color.WHITE);
        original = true;
    }
    
    /**
     * Returns a copy of mToCopy whose isOriginal() method will return false.
     */
    public OPModule(OPModule mToCopy) {
        super(mToCopy.getType(), mToCopy.getName(), mToCopy.getID(), mToCopy.getIcon());
        original = false;
        setPos(mToCopy.dragX, mToCopy.dragY);
    }
    
    /** node on which this module is installed, if any */
    private OPNodeWithNameAndPorts nodeInstalledOn = null;
    
    /** gets the node the module is installed on */
    public OPNodeWithNameAndPorts getNodeInstalledOn() {
        return nodeInstalledOn;
    }
    
    /** 
     * Tries to install the module on a node - returns false if n is not 
     * compatible with this module as per isCompatibleWith().
     * 
     * @return true on success, false if incompatible
     */
    public boolean installOnNode(OPNodeWithNameAndPorts n) {
        if(!isCompatibleWith(n)) {
            nodeInstalledOn = n;
            return true;
        }
        else
            return false;
    }
    
    /** uninstalls a module */
    public void uninstall() {
        installOnNode(null);
    }
    
    /**
     * Returns whether this module is compatible with n (i.e., it can be 
     * installed on n).
     */
    public boolean isCompatibleWith(OPNodeWithNameAndPorts n) {
        if(getType()==NodeType.TYPE_MODULE_HW && n.getType()==NodeType.TYPE_NETFPGA)
            return true;
        
        if(getType()==NodeType.TYPE_MODULE_SW && n.getType()==NodeType.TYPE_LAPTOP)
            return true;
        
        return false;
    }
    
    /** status of the module, if known */
    private String status = "Unknown: not yet queried.";
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String s) {
        status = s;
    }
    
    /** whether the module is an original (i.e., do not remove) */
    private boolean original;
    
    /** whether the module is an original (else it is a dragged copy) */
    public boolean isOriginal() {
        return original;
    }
    
    /* where the module is being dragged */
    private int dragX, dragY;
    
    public int getDragX() {
        return dragX;
    }
    
    public int getDragY() {
        return dragY;
    }
    
    /** Draw the object using super.drawObject() and then add the name in the middle */
    public void drawObject(Graphics2D gfx) {
        super.drawObject(gfx);
        if(!isOriginal())
            return;
        
        int x = getX();
        int y = getY();
        
        if(dragX!=x || dragY!=y) {
            super.setPos(dragX, dragY);
            super.drawObject(gfx);
            super.setPos(x, y);
        }
    }
    
    public void setXPos(int x) {
        super.setXPos(x);
        dragX = x;
    }
    
    public void setYPos(int y) {
        super.setYPos(y);
        dragY = y;
    }
    
    /**
     * Originals stay in place while a copy of their image is dragged.  
     * Non-originals are dragged as usual.
     */
    public void drag(int x, int y) {
        if(isOriginal()) {
            dragX = x;
            dragY = y;
        }
        else
            super.drag(x, y);
    }
}
