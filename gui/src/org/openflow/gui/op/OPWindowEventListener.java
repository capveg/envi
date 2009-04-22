package org.openflow.gui.op;

import java.awt.event.KeyEvent;

import org.pzgui.PZWindow;
import org.pzgui.PZWindowEventListener;

public class OPWindowEventListener extends PZWindowEventListener {
    public static final String MODE_CHANGED_EVENT = "mode_changed";
    
    /** the global link add mode */
    private static boolean linkAddMode = false;
    
    /** the global link delete mode status */
    private static boolean linkDeleteMode = false;
    
    /** returns true if we are in link add mode */
    public static boolean isLinkAddMode() {
        return linkAddMode;
    }
    
    /** returns true if we are in link delete mode */
    public static boolean isLinkDeleteMode() {
        return linkDeleteMode;
    }
    
    /** returns true if we are in module status mode */
    public static boolean isModuleStatusMode() {
        return !isLinkAddMode() && !isLinkDeleteMode();
    }
    
    /** 
     * Updates the specified window's title with the new mode.
     */
    private void modeChanged(PZWindow w) {
        if(isModuleStatusMode())
            w.setCustomTitle(OpenPipes.OPENPIPES_TITLE);
        else
            w.setCustomTitle(OpenPipes.OPENPIPES_TITLE + " - " + getModeName());
        
        w.getManager().fireDrawableEvent(null, null, MODE_CHANGED_EVENT);
    }
    
    /** returns a string representation of the current mode */
    private static String getModeName() {
        if(isLinkAddMode())
            return "Link Add Mode";
        else if(isLinkDeleteMode())
            return "Link Delete Mode";
        else if(isModuleStatusMode())
            return "Module Status Mode";
        else
            return "Unknown Mode";
            
    }
    
    /**
     * Adds key bindings for A (toggle add link mode) and D (toggle remove 
     * link mode).
     */
    public void keyReleased(KeyEvent e) {
        if(e.getKeyCode() == KeyEvent.VK_A) {
            linkAddMode = !linkAddMode;
            linkDeleteMode = false;
            modeChanged(getWindow(e));
        }
        else if(e.getKeyCode() == KeyEvent.VK_D) {
            linkDeleteMode = !linkDeleteMode;
            linkAddMode = false;
            modeChanged(getWindow(e));
        }
        else
            super.keyReleased(e);
    }
}
