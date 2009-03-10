package org.pzgui.layout;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.HashSet;
import java.util.LinkedList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;

import org.openflow.lavi.drawables.DrawableIcon;
import org.openflow.lavi.drawables.OpenFlowSwitch;
import org.openflow.lavi.net.protocol.ETTrafficMatrix;
import org.openflow.util.string.StringOps;
import org.pzgui.Constants;
import org.pzgui.Drawable;
import org.pzgui.PZWindow;
import org.pzgui.icon.GeometricIcon;

/**
 * Elastic Tree GUI manager.
 * 
 * @author David Underhill
 */
public class ElasticTreeManager extends PZLayoutManager {
    public static final int SL_WIDTH = 50;
    public static final int LBL_HEIGHT = 20;
    public static final int LBL_WIDTH = 100;
    public static final int LBL_WIDTH_BIG = 400;
    public static final int GAP_X = 5;
    public static final int RESERVED_COLUMN_WIDTH = Math.max(GAP_X + SL_WIDTH + GAP_X + LBL_WIDTH, LBL_WIDTH_BIG);
    
    /** Creates a new Elastic Tree GUI for a k=6 fat tree */
    public ElasticTreeManager() {
        this(6);
    }

    /** Creates a new Elastic Tree GUI for a k fat tree */
    public ElasticTreeManager(int k) {
        fatTreeLayout = new FatTreeLayout<Vertex, Edge>(getGraph(), k);
        this.setLayout(fatTreeLayout);
        setCurrentTrafficMatrixText(null);
        setNextTrafficMatrixText(null);
        
        dialPower = new MultiPointerDial("Power Consumption", "Watts", 2, 460, 50);
        dialPower.setPointerLine(1, 0.95);
        
        dialBandwidth = new MultiPointerDial("Aggregate Xput", "Gbps", 2, 550, 50);
        dialBandwidth.setPointerLine(1, 0.95);
        
        dialLatency = new MultiPointerDial("Layer Latency", "msec", 3, 100, 10);
        
        pnlSidebar.setDoubleBuffered(true);
        pnlSidebar.setLayout(null);
        pnlSidebar.setBorder(new javax.swing.border.LineBorder(Color.BLACK, 2));
        pnlSidebar.add(slDemand);
        pnlSidebar.add(lblDemand);
        pnlSidebar.add(lblDemandVal);
        pnlSidebar.add(slEdge);
        pnlSidebar.add(lblEdge);
        pnlSidebar.add(lblEdgeVal);
        pnlSidebar.add(slAgg);
        pnlSidebar.add(lblAgg);
        pnlSidebar.add(lblAggVal);
        pnlSidebar.add(slPLen);
        pnlSidebar.add(lblPLen);
        pnlSidebar.add(lblPLenVal);
        pnlSidebar.add(lblTrafficMatrixCurrent);
        pnlSidebar.add(lblTrafficMatrixNext);
        pnlSidebar.add(dialPower);
        pnlSidebar.add(dialBandwidth);
        pnlSidebar.add(dialLatency);
    }
    
    // -------- Layout and Redrawing -------- //
    // ************************************** //
    
    private final FatTreeLayout<Vertex, Edge> fatTreeLayout;
    
    /** Gets the FatTreeLayout associated with this GUI */
    public FatTreeLayout getFatTreeLayout() {
        return fatTreeLayout;
    }
    
    /** Adds the drawable as usual and then invokes the fat tree layout engine */
    public void addDrawable(Drawable d) {
        super.addDrawable(d);
        synchronized(this) {
            if(d instanceof Vertex) {
                if(fatTreeLayout.noteVertex((Vertex)d)) {
                    // a bit of a hack: draw switches representing hosts a different color
                    if(d instanceof OpenFlowSwitch) {
                        OpenFlowSwitch o = ((OpenFlowSwitch)d);
                        o.setFillColor(java.awt.Color.DARK_GRAY);
                        o.setSize(OpenFlowSwitch.SIZE_SMALL);
                    }
                }
                relayout();
            }
        }
    }
    
    /** 
     * Removes the drawable as usual and then completely resets the fat tree 
     * layout engine (assumes all switches are being removed).
     */
    public void removeDrawable(Drawable d) {
        super.removeDrawable(d);
        synchronized(this) {
            if(d instanceof Vertex) {
                fatTreeLayout.clear();
            }
        }
    }

    /** Overrides parent to add my widgets to the new window. */
    public void attachWindow(final PZWindow w) {
        super.attachWindow(w);
        if(getWindowIndex(w) == 0) {
            w.getContentPane().add(pnlSidebar);
            w.setReservedWidthRight(RESERVED_COLUMN_WIDTH);
            w.setMySize(w.getWidth(), w.getHeight(), w.getZoom());
        }
    }
    
    /**
     * Overrides parent to reduce size of node layout to provide room for other
     * widgets on the GUI.  Also lays out the Elastic Tree specific widgets. 
     */
    public void setLayoutSize(int w, int h) {
        super.setLayoutSize(w, h);
        relayout();

        // place our custom components
        pnlSidebar.setBounds(w, 0, RESERVED_COLUMN_WIDTH-4, h);
        
        int x = GAP_X;
        int y = 0;
        lblTrafficMatrixCurrent.setBounds(x, y, LBL_WIDTH_BIG, LBL_HEIGHT);
        y += LBL_HEIGHT;
        lblTrafficMatrixNext.setBounds(x, y, LBL_WIDTH_BIG, LBL_HEIGHT);
        
        h -= y;
        int SL_HEIGHT = h / 8;
        final int SL_X = GAP_X;
        
        x = GAP_X;
        y = h - SL_HEIGHT - LBL_HEIGHT;
        final int LOWER_SL_HEIGHT = SL_HEIGHT - LBL_HEIGHT;
        int LBL_Y = y + LOWER_SL_HEIGHT;
        slDemand.setBounds(x, y, SL_WIDTH, LOWER_SL_HEIGHT);
        lblDemand.setBounds(x, LBL_Y, LBL_WIDTH, LBL_HEIGHT);
        lblDemandVal.setBounds(x, LBL_Y+LBL_HEIGHT, LBL_WIDTH, LBL_HEIGHT);
        
        int x2 = x + SL_WIDTH + GAP_X;
        slPLen.setBounds(x2, y, LBL_WIDTH, LOWER_SL_HEIGHT);
        lblPLen.setBounds(x2, LBL_Y, LBL_WIDTH, LBL_HEIGHT);
        lblPLenVal.setBounds(x2, LBL_Y+LBL_HEIGHT, LBL_WIDTH, LBL_HEIGHT);
        
        x = SL_X;
        y = h / 3 - 2 * SL_HEIGHT / 3;
        LBL_Y = y + SL_HEIGHT;
        slAgg.setBounds(x, y, SL_WIDTH, SL_HEIGHT);
        lblAgg.setBounds(x, LBL_Y, LBL_WIDTH, LBL_HEIGHT);
        lblAggVal.setBounds(x, LBL_Y+LBL_HEIGHT, LBL_WIDTH, LBL_HEIGHT);
        
        y = 2 * h / 3 - SL_HEIGHT;
        LBL_Y = y + SL_HEIGHT;
        slEdge.setBounds(x, y, SL_WIDTH, SL_HEIGHT);
        lblEdge.setBounds(x, LBL_Y, LBL_WIDTH, LBL_HEIGHT);
        lblEdgeVal.setBounds(x, LBL_Y+LBL_HEIGHT, LBL_WIDTH, LBL_HEIGHT);
        
        int o = SL_WIDTH + GAP_X;
        int sz = LBL_WIDTH_BIG - o - GAP_X * 2;
        x = x + o;
        y = 2 * LBL_HEIGHT + 5;
        dialPower.setBounds(x, y, sz, sz);
        y += sz + 10;
        dialBandwidth.setBounds(x, y, sz, sz);
        y += sz + 10;
        dialLatency.setBounds(x, y, sz, sz);
    }
    
    /**
     * Overrides the parent implementation by appending the drawing of the 
     * additional Elastic Tree widgets.
     */
    public void preRedraw(PZWindow window) {
        super.preRedraw(window);
        Graphics2D gfx = window.getDisplayGfx();
        if(gfx == null)
            return;
        
        for(DrawableIcon d : liveIcons)
            d.drawObject(gfx);
    }
    
    public void postRedraw() {}

    private int[] baseX = new int[]{0,1,1,0};
    private int[] baseY = new int[]{0,0,1,1};
    private HashSet<DrawableIcon> liveIcons = new HashSet<DrawableIcon>();
    private void addRectangle(int x, int y, int w, int h, Color c) {
        DrawableIcon d = new DrawableIcon(new GeometricIcon(baseX, baseY, w, h, c), x, y, w, h);
        liveIcons.add(d);
    }
    
    private static final int POD_COLOR_HIGH = (Constants.INVERT_COLORS ? 32 : 255);
    private static final int POD_COLOR_LOW  = (Constants.INVERT_COLORS ? 16 : 243);
    private static final Color[] POD_COLORS = new Color[] {new Color(POD_COLOR_HIGH,POD_COLOR_LOW,POD_COLOR_LOW),
                                                           new Color(POD_COLOR_LOW,POD_COLOR_HIGH,POD_COLOR_LOW),
                                                           new Color(POD_COLOR_LOW,POD_COLOR_LOW,POD_COLOR_HIGH),
                                                           new Color(POD_COLOR_HIGH,POD_COLOR_HIGH,POD_COLOR_LOW),
                                                           new Color(POD_COLOR_HIGH,POD_COLOR_LOW,POD_COLOR_HIGH),
                                                           new Color(POD_COLOR_LOW,POD_COLOR_HIGH,POD_COLOR_HIGH)};
    
    private void relayout() {
        // only relayout once all nodes are present
        if(fatTreeLayout.getGraph().getVertexCount() < fatTreeLayout.size())
            return;
        
        for(DrawableIcon d : liveIcons)
            removeDrawable(d);
        liveIcons.clear();
        
        fatTreeLayout.reset();
        fatTreeLayout.relayout();
        
        int y = fatTreeLayout.agg_y - 20;
        int w = fatTreeLayout.pod_sz;
        int h = fatTreeLayout.getSize().height - y;
        int i;
        for(i=0; i<fatTreeLayout.getK()-1; i++)
            addRectangle(w*i, y, w, h, POD_COLORS[i]);
        addRectangle(w*i, y, w*2, h, POD_COLORS[i]);
    }

    
    // ----- Traffic Matrix Components ------ //
    // ************************************** //
    
    /** JSlider which calls back when its value changes. */
    private class MyJSlider extends JSlider {
        public MyJSlider(int orientation, int min, int max, int value) {
            super(orientation, min, max, value);
        }
        public void setValue(int i) {
            super.setValue(i);
            notifyTrafficMatrixChangeListeners();
        }
    }
    
    private JPanel pnlSidebar = new JPanel();
    
    private JLabel lblDemand = new JLabel("Demand", SwingConstants.LEFT);
    private JLabel lblEdge = new JLabel("Edge", SwingConstants.LEFT);
    private JLabel lblAgg = new JLabel("Agg", SwingConstants.LEFT);
    private JLabel lblPLen = new JLabel("PktLen", SwingConstants.CENTER);

    private JLabel lblDemandVal = new JLabel(StringOps.formatBitsPerSec(1000*1000*1000), SwingConstants.LEFT);
    private JLabel lblEdgeVal = new JLabel("100%", SwingConstants.LEFT);
    private JLabel lblAggVal = new JLabel("100%", SwingConstants.LEFT);
    private JLabel lblPLenVal = new JLabel("1514B", SwingConstants.CENTER);

    private JSlider slDemand = new MyJSlider(SwingConstants.VERTICAL, 0, 1000*1000*1000, 1000*1000*1000);
    private JSlider slEdge   = new MyJSlider(SwingConstants.VERTICAL, 0, 100, 100);
    private JSlider slAgg    = new MyJSlider(SwingConstants.VERTICAL, 0, 100, 100);
    private JSlider slPLen    = new MyJSlider(SwingConstants.VERTICAL, 64, 1514, 1514);

    private JLabel lblTrafficMatrixCurrent = new JLabel();
    private JLabel lblTrafficMatrixNext = new JLabel();
    
    /**
     * Sets the text description of the data currently being visualized.
     * @param tm  the matrix which led to the latest data
     */
    public void setCurrentTrafficMatrixText(ETTrafficMatrix tm) {
        String s = (tm == null) ? "n/a" : tm.toStringShort();
        lblTrafficMatrixCurrent.setText("Traffic Now: " + s);
    }
    
    /**
     * Sets the text description of the data currently pending results.
     * @param tm  the matrix which has been sent to the server but not responded to yet
     */
    public void setNextTrafficMatrixText(ETTrafficMatrix tm) {
        String s = (tm == null) ? "n/a" : tm.toStringShort();
        lblTrafficMatrixNext.setText("Next Traffic: " + s);
    }

    private final MultiPointerDial dialPower, dialBandwidth, dialLatency;    
    
    public void setPowerData(int cur, int traditional, int max) {
        dialPower.setValue(0, cur);
        dialPower.setValue(1, traditional);
        dialPower.setMax(max);
    }
    
    public void setExpectedAggregateThroughput(double total_bps) {
        dialBandwidth.setValue(1, (int)total_bps);
    }

    public void setAchievedAggregateThroughput(int bandwidth_achieved_bps) {
        dialBandwidth.setValue(0, bandwidth_achieved_bps);
    }

    public void setLatencyData(int latency_ms_edge, int latency_ms_agg, int latency_ms_core) {
        dialLatency.setValue(0, latency_ms_edge);
        dialLatency.setValue(1, latency_ms_agg);
        dialLatency.setValue(2, latency_ms_core);
    }
    
    // --- Traffic Matrix Change Handling --- //
    // ************************************** //
    
    /** closing event listeners */
    private final LinkedList<TrafficMatrixChangeListener> trafficMatrixChangeListeneres = new LinkedList<TrafficMatrixChangeListener>();
    
    /** adds a listener to be notified when the traffic matrix has changed */
    public void addTrafficMatrixChangeListener(TrafficMatrixChangeListener c) {
        if(!trafficMatrixChangeListeneres.contains(c))
            trafficMatrixChangeListeneres.add(c);
    }

    /** removes the specified traffic matrix change listener */
    public void removeTrafficMatrixChangeListener(TrafficMatrixChangeListener c) {
        trafficMatrixChangeListeneres.remove(c);
    }
    
    /** gets the current traffic matrix */
    public ETTrafficMatrix getCurrentTrafficMatrix() {
        float demand = slDemand.getValue() / (float)slDemand.getMaximum();
        float edge = slEdge.getValue() / (float)slEdge.getMaximum();
        float aggDef = slAgg.getValue() / (float)slAgg.getMaximum();
        float agg = Math.min(1.0f-edge, aggDef);
        if(agg != aggDef) {
            slAgg.setValue((int)(100*agg));
        }
        return new ETTrafficMatrix(false, fatTreeLayout.getK(), demand, edge, agg, slPLen.getValue());
    }

    /**
     * Updates the slider labels and notify those listening for traffic matrix changes.
     */
    public void notifyTrafficMatrixChangeListeners() {
        lblDemandVal.setText(StringOps.formatBitsPerSec(slDemand.getValue()));
        lblEdgeVal.setText(slEdge.getValue() + "%");
        lblAggVal.setText(slAgg.getValue() + "%");
        lblPLenVal.setText(slPLen.getValue() + "B");
        
        ETTrafficMatrix tm = getCurrentTrafficMatrix();
        for(TrafficMatrixChangeListener c : trafficMatrixChangeListeneres)
            c.trafficMatrixChanged(tm);
    }
}
