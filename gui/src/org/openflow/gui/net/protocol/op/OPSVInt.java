/**
 * 
 */
package org.openflow.gui.net.protocol.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.openflow.gui.net.SocketConnection;

/**
 * Integer value of a state variable
 * 
 * @author grg
 *
 */
public class OPSVInt extends OPStateValue {
    /** width of field */
    public final int width;
    
    /** value of field */
    public final long value;
    
    public OPSVInt(String name, int width, long value){
        super(name);
        this.width = width;
        this.value = value;
    }

    public OPSVInt(OPSFInt intField, long value){
        super(intField.name);
        this.width = intField.width;
        this.value = value;
    }

    protected OPSVInt(String name, DataInput in) throws IOException {
        super(name);
        width = in.readByte();
        if (width <= 4)
            value = in.readInt();
        else
            value = in.readLong();
    }
    
    public int length() {
        return super.length() + 1 + ((width <= 4) ? 4 : 8);
    }

    public void write(DataOutput out) throws IOException {
        super.write(out);
        out.writeByte(width);
        if (width <= 4)
            out.writeInt((int)value);
        else
            out.writeLong(value);
    }

    @Override
    protected int getType() {
        return TYPE_INT;
    }

    public String toString() {
        return super.toString() + "type=int width=" + width + 
            " value=" + value + " (" + Long.toHexString(value) + ")"; 
    }
}