"""Defines the OpenFlow GUI-based OpenPipes protocol."""

import struct

from twisted.internet import reactor

from OFGMessage import OFG_DEFAULT_PORT, OFG_MESSAGES
from OFGMessage import OFGMessage, LinksAdd, LinksDel, Node, NodesAdd
from ltprotocol.ltprotocol import LTProtocol

OP_MESSAGES = []

class OPMoveModule(OFGMessage):
    # used by MoveModule to represent when a module is added (from_node is NONE) or
    # removed (to_node is NONE)
    NODE_NONE = Node(Node.TYPE_UNKNOWN, 0x00000000FFFFFFFFL)

    @staticmethod
    def get_type():
        return 0xF0

    def __init__(self, module, from_node, to_node, xid=0):
        OFGMessage.__init__(self, xid)
        self.module = module
        self.from_node = from_node
        self.to_node = to_node

    def length(self):
        return OFGMessage.SIZE + 3 * Node.SIZE

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = self.module.pack() + self.from_node.pack() + self.to_node.pack()
        return hdr + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        module = Node.unpack(body[:Node.SIZE])
        body = body[Node.SIZE:]
        from_node = Node.unpack(body[:Node.SIZE])
        body = body[Node.SIZE:]
        to_node = Node.unpack(body[:Node.SIZE])
        return OPMoveModule(module, from_node, to_node, xid)

    def __str__(self):
        noneID = OPMoveModule.NODE_NONE.id
        noneT = OPMoveModule.NODE_NONE.node_type
        if self.from_node.id==noneID and self.from_node.node_type==noneT:
            fmt = 'OP_MOVE_MODULE: ' + OFGMessage.__str__(self) + " add %s to %s"
            return fmt % (self.module, self.to_node)
        elif self.to_node.id == noneID and self.to_node.node_type == noneT:
            fmt = 'OP_MOVE_MODULE: ' + OFGMessage.__str__(self) + " remove %s from %s"
            return fmt % (self.module, self.from_node)
        else:
            fmt = 'OP_MOVE_MODULE: ' + OFGMessage.__str__(self) + " move %s from %s to %s"
            return fmt % (self.module, self.from_node, self.to_node)

OP_MESSAGES.append(OPMoveModule)

class OPTestInfo(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF1

    def __init__(self, test_input, test_output, xid=0):
        OFGMessage.__init__(self, xid)
        self.input = str(test_input)
        self.output = str(test_output)

    def length(self):
        return OFGMessage.SIZE + len(self.input) + 1 + len(self.output) + 1

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = struct.pack('> %us %us' % (len(self.input)+1, len(self.output)+1),
                           self.input, self.output)
        return hdr + body

    @staticmethod
    def unpack(body):
        raise Exception('OPTestInfo.unpack() not implemented (one-way message)')

    def __str__(self):
        fmt = 'OP_TEST_INFO: ' + OFGMessage.__str__(self) + " %s ==> %s"
        return fmt % (self.input, self.output)
OP_MESSAGES.append(OPTestInfo)

class OPNode(Node):
    NAME_LEN = 32
    DESC_LEN = 128
    SIZE = Node.SIZE + NAME_LEN + DESC_LEN

    def __init__(self, node_type, node_id, name, desc):
        Node.__init__(self, node_type, node_id)
        self.name = str(name)
        self.desc = str(desc)

    def pack(self):
        return Node.pack(self) + struct.pack('> %us%us' % (OPNode.NAME_LEN, OPNode.DESC_LEN), self.name, self.desc)

    @staticmethod
    def unpack(buf):
        node_type = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        node_id = struct.unpack('> Q', buf[:8])[0]
        buf = buf[8:]
        name = struct.unpack('> %us' % OPNode.NAME_LEN, buf[:OPNode.NAME_LEN])[0][:-1]
        buf = buf[OPNode.NAME_LEN:]
        desc = struct.unpack('> %us' % OPNode.DESC_LEN, buf[:OPNode.NAME_DESC])[0][:-1]
        return OPNode(node_type, node_id, name, desc)

    def __str__(self):
        return Node.__str__(self) + ' name=%s desc=%s' % (self.name, self.desc)

class OPNodesList(OFGMessage):
    def __init__(self, nodes, xid=0):
        OFGMessage.__init__(self, xid)
        self.nodes = nodes

    def length(self):
        return OFGMessage.SIZE + len(self.nodes) * OPNode.SIZE

    def pack(self):
        return OFGMessage.pack(self) + ''.join([n.pack() for n in self.nodes])

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        num_nodes = len(body) / OPNode.SIZE
        nodes = []
        for _ in range(num_nodes):
            nodes.append(OPNode.unpack(body[:OPNode.SIZE]))
            body = body[OPNode.SIZE:]
        return OPNodesList(nodes, xid)

    def __str__(self):
        return OFGMessage.__str__(self) + ' nodes=[%s]' % ''.join([str(n) + ',' for n in self.nodes])

class OPNodesAdd(OPNodesList):
    @staticmethod
    def get_type():
        return 0xF2

    def __init__(self, nodes, xid=0):
        OPNodesList.__init__(self, nodes, xid)

    @staticmethod
    def unpack(body):
        return OPNodesList.unpack(body)

    def __str__(self):
        return 'NODES_ADD: ' + OPNodesList.__str__(self)
OFG_MESSAGES.append(OPNodesAdd)

class OPNodesDel(OPNodesList):
    @staticmethod
    def get_type():
        return 0xF3

    def __init__(self, nodes, xid=0):
        OPNodesList.__init__(self, nodes, xid)

    @staticmethod
    def unpack(body):
        return OPNodesList.unpack(body)

    def __str__(self):
        return 'NODES_DEL: ' + OPNodesList.__str__(self)
OFG_MESSAGES.append(OPNodesDel)

class OPModule(Node):
    NAME_LEN = 32
    SIZE = Node.SIZE + 32

    @staticmethod
    def extractModuleID(nid):
        """Extracts the portion of the ID which correspond to module ID"""
        return int(nid & 0x00000000FFFFFFFFL)

    @staticmethod
    def extractCopyID(nid):
        """Extracts the portion of the ID which correspond to module copy ID"""
        return int(nid >> 32L)

    @staticmethod
    def createNodeID(mid, cid):
        """create the node ID from its constituent parts"""
        if (0xFFFFFFFF00000000L & mid) != 0:
            raise Exception("Error: upper 4 bytes of module IDs should be 0 for original modules!  Got: %0X" % mid)

        return (cid << 32L) | mid

    def __init__(self, node_type, node_id, name, ports, state_fields):
        Node.__init__(self, node_type, node_id)
        self.name = str(name)
        self.ports = ports
        self.state_fields = state_fields

    def length(self):
        port_size = 0
        for p in self.ports:
            port_size += p.length()
        state_size = 0
        for s in self.state_fields:
            state_size += s.length()
        return OPModule.SIZE + 2 + port_size + 2 + state_size

    def pack(self):
        return Node.pack(self) + struct.pack('> %us H' % OPModule.NAME_LEN, self.name, len(self.ports)) + \
                ''.join([p.pack() for p in self.ports]) + struct.pack('> H', len(self.state_fields)) + \
                ''.join([s.pack() for s in self.state_fields])

    @staticmethod
    def unpack(buf):
        node_type = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        node_id = struct.unpack('> Q', buf[:8])[0]
        buf = buf[8:]
        name = struct.unpack('> %us' % OPModule.NAME_LEN, buf[:OPModule.NAME_LEN])[0][:-1]
        buf = buf[OPModule.NAME_LEN:]
        num_ports = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        ports = []
        for _ in range(num_ports):
            port = OPModulePort.unpack(buf)
            ports.append(port)
            buf = buf[port.length():]
        num_state_vars = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        state_fields = []
        for _ in range(num_state_vars):
            field = OPStateField.unpack(buf)
            state_fields.append(field)
            buf = buf[field.length():]
        return OPModule(node_type, node_id, name, ports, state_fields)

    def __str__(self):
        return Node.__str__(self) + ' ports=[%s]' % ''.join([str(p) + ',' for p in self.ports]) + \
                ' state_fields=[%s]' % ''.join([str(s) + ',' for s in self.state_fields])

class OPModulesList(OFGMessage):
    def __init__(self, modules, xid=0):
        OFGMessage.__init__(self, xid)
        self.modules = modules

    def length(self):
        module_size = 0
        for m in self.modules:
            module_size += m.length()
        return OFGMessage.SIZE + module_size

    def pack(self):
        return OFGMessage.pack(self) + struct.pack('> H', len(self.modules)) + \
                ''.join([m.pack() for m in self.modules])

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        num_modules = struct.unpack('> H', body[:2])[0]
        body = body[2:]
        modules = []
        for _ in range(num_modules):
            module = OPModule.unpack(body)
            modules.append(module)
            body = body[module.length():]
        return OPModulesList(modules, xid)

    def __str__(self):
        return OFGMessage.__str__(self) + ' modules=[%s]' % ''.join([str(m) + ',' for m in self.modules])

class OPModulesAdd(OPModulesList):
    @staticmethod
    def get_type():
        return 0xF4

    def __init__(self, modules, xid=0):
        OPModulesList.__init__(self, modules, xid)

    @staticmethod
    def unpack(body):
        return OPModulesList.unpack(body)

    def __str__(self):
        return 'MODULES_ADD: ' + OPModulesList.__str__(self)
OFG_MESSAGES.append(OPModulesAdd)

class OPModulesDel(OPModulesList):
    @staticmethod
    def get_type():
        return 0xF5

    def __init__(self, dpids, xid=0):
        OPModulesList.__init__(self, dpids, xid)

    @staticmethod
    def unpack(body):
        return OPModulesList.unpack_child(body)

    def __str__(self):
        return 'MODULES_DEL: ' + OPModulesList.__str__(self)
OFG_MESSAGES.append(OPModulesDel)

class OPModulePort(object):
    NAME_LEN_MAX = 32
    DESC_LEN_MAX = 128

    def __init__(self, port_id, name, desc):
        self.id = port_id
        self.name = str(name)[0:OPModulePort.NAME_LEN_MAX]
        self.desc = str(desc)[0:OPModulePort.DESC_LEN_MAX]

    def length(self):
        return 2 + 1 + len(self.name) + 1 + 1 + len(self.desc) + 1

    def pack(self):
        name_len = len(self.name) + 1
        desc_len = len(self.desc) + 1
        return struct.pack('> H B %us B %us' % (name_len, desc_len), self.id, name_len, self.name, desc_len, self.desc)

    @staticmethod
    def unpack(buf):
        port_id = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        name_len = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        name = struct.unpack('> %us' % name_len, buf[:name_len])[0][:-1]
        buf = buf[name_len:]
        desc_len = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        desc = struct.unpack('> %us' % desc_len, buf[:desc_len])[0][:-1]
        return OPModulePort(port_id, name, desc)

    def __str__(self):
        fmt = "OP_MODULE_PORT: id=%d name='%s' desc='%s'"
        return fmt % (self.id, self.name, self.desc)

class OPModuleReg(object):
    NAME_LEN_MAX = 32
    DESC_LEN_MAX = 128

    def __init__(self, addr, name, desc, rd_only):
        self.addr = addr
        self.name = str(name)[0:OPModuleReg.NAME_LEN_MAX]
        self.desc = str(desc)[0:OPModuleReg.DESC_LEN_MAX]
        self.rd_only = rd_only

    def length(self):
        return 4 + 1 + len(self.name) + 1 + 1 + len(self.desc) + 1 + 1

    def pack(self):
        name_len = len(self.name) + 1
        desc_len = len(self.desc) + 1
        return struct.pack('> I B %us B %us ?' % (name_len, desc_len), self.addr, name_len, self.name, desc_len, self.desc, self.rd_only)

    @staticmethod
    def unpack(buf):
        addr = struct.unpack('> I', buf[:4])[0]
        buf = buf[4:]
        name_len = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        name = struct.unpack('> %us' % name_len, buf[:name_len])[0][:-1]
        buf = buf[name_len:]
        desc_len = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        desc = struct.unpack('> %us' % desc_len, buf[:desc_len])[0][:-1]
        buf = buf[desc_len:]
        rd_only = struct.unpack('> ?', buf[:1])[0]
        return OPModuleReg(addr, name, desc, rd_only)

    def __str__(self):
        fmt = "OP_MODULE_REG: addr=0x%08x name='%s' desc='%s' rdonly='%d'"
        return fmt % (self.addr, self.name, self.desc, self.rd_only)

class OPModuleStatusRequest(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF6

    def __init__(self, node, module, xid=0):
        OFGMessage.__init__(self, xid)
        self.node = node
        self.module = module

    def length(self):
        return OFGMessage.SIZE + 2 * Node.SIZE

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = self.node.pack() + self.module.pack()
        return hdr + body

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        node = Node.unpack(body[:Node.SIZE])
        body = body[Node.SIZE:]
        module = Node.unpack(body[:Node.SIZE])
        return OPModuleStatusRequest(node, module, xid)

    def __str__(self):
        fmt = 'OP_MODULE_STATUS_REQUEST: ' + OFGMessage.__str__(self) + " request status for module %s on %s"
        return fmt % (self.module, self.node)
OP_MESSAGES.append(OPModuleStatusRequest)

class OPModuleStatusReply(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF7

    def __init__(self, node, module, status, xid=0):
        OFGMessage.__init__(self, xid)
        self.node = node
        self.module = module
        self.status = str(status)

    def length(self):
        return OFGMessage.SIZE + 2 * Node.SIZE + len(self.status) + 1

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = self.node.pack() + self.module.pack()
        body += struct.pack('> %us' % (len(self.status)+1), self.status)
        return hdr + body

    @staticmethod
    def unpack(body):
        raise Exception('OPModuleStatusReply.unpack() not implemented (one-way message)')

    def __str__(self):
        fmt = 'OP_MODULE_STATUS_REPLY: ' + OFGMessage.__str__(self) + " status for module %s on %s: %s"
        return fmt % (self.module, self.node, self.status)
OP_MESSAGES.append(OPModuleStatusReply)

class OPStateField(object):
    NAME_LEN = 16

    TYPE_INT = 1
    TYPE_INT_CHOICE = 2
    TYPE_TABLE = 3

    def __init__(self, name, desc, readOnly, type):
        self.name = name[:OPStateField.NAME_LEN]
        self.desc = desc
        self.readOnly = readOnly
        self.type = type

    def length(self):
        return 1 + OPStateField.NAME_LEN + 1 + len(self.desc) + 1 + 1

    def pack(self):
        desc_len = len(self.desc) + 1
        return struct.pack('> B %us B %us B' % (OPStateField.NAME_LEN, desc_len),
                self.type, self.name, desc_len, self.desc, self.readOnly)

    @staticmethod
    def unpack(buf):
        type = struct.unpack('> B', buf[:1])[0]
        if type == OPStateField.TYPE_INT:
            return OPSFInt.unpack(buf)
        elif type == OPStateField.TYPE_INT_CHOICE:
            return OPSFIntChoice.unpack(buf)
        elif type == OPSFTable.TYPE_TABLE:
            return OPSFTable.unpack(buf)
        else:
            raise LookupError("Unknown type '%d' while unpacking in OPStateField"%type)

    @staticmethod
    def partial_unpack(buf):
        type = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        name = struct.unpack('> %us' % OPStateField.NAME_LEN, buf[:OPStateField.NAME_LEN])[0][:-1]
        buf = buf[OPStateField.NAME_LEN:]
        desc_len = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        desc = struct.unpack('> %us' % desc_len, buf[:desc_len])[0][:-1]
        buf = buf[desc_len:]
        readOnly = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        return (name, desc, readOnly, buf)

    def __str__(self):
        return "OP_STATE_FIELD: name=%s desc='%s' read_only=%d "% \
                (self.name, self.desc, self.readOnly)

class OPSFInt(OPStateField):
    DISP_INT    = 1
    DISP_IP     = 2
    DISP_MAC    = 3
    DISP_BOOL   = 4
    DISP_CHOICE = 5

    def __init__(self, name, desc, readOnly, width, display):
        OPStateField.__init__(self, name, desc, readOnly, OPStateField.TYPE_INT)
        self.width = width
        self.display = display

    def length(self):
        return OPStateField.length(self) + 1 + 1

    def pack(self):
        hdr = OPStateField.pack(self)
        body = struct.pack('> B B', self.width, self.display)
        return hdr + body

    @staticmethod
    def unpack(buf):
        (name, desc, readOnly, buf) = OPStateField.partial_unpack(buf)
        width = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        display = struct.unpack('> B', buf[:1])[0]
        return OPSFInt(name, desc, readOnly, width, display)

    def __str__(self):
        if self.display == OPSFInt.DISP_INT:
            display = 'int'
        elif self.display == OPSFInt.DISP_IP:
            display = 'IP'
        elif self.display == OPSFInt.DISP_MAC:
            display = 'MAC'
        elif self.display == OPSFInt.DISP_BOOL:
            display = 'boolean'
        elif self.display == OPSFInt.DISP_CHOICE:
            display = 'choice'
        return OPStateField.__str__(self) + " type=int width=%d display=%s"%(
                self.width, display)

class OPSFIntChoice(OPSFInt):
    CHOICE_LEN = 40

    def __init__(self, name, desc, readOnly, choices):
        OPSFInt.__init__(self, name, desc, readOnly, 4, OPSFInt.DISP_CHOICE)
        self.type = OPStateField.TYPE_INT_CHOICE
        self.choices = choices

    def length(self):
        choice_len = len(self.choices) * (4 + OPSFIntChoice.CHOICE_LEN)
        return OPSFInt.length(self) + 2 + choice_len

    def pack(self):
        choice_len = len(self.choices) * (4 + OPSFIntChoice.CHOICE_LEN)
        hdr = OPSFInt.pack(self)
        body = struct.pack('> H', len(self.choices))
        for (val, choice) in self.choices:
            body += struct.pack('> I %us' % OPSFIntChoice.CHOICE_LEN, val, choice)
        return hdr + body

    @staticmethod
    def unpack(buf):
        (name, desc, readOnly, buf) = OPStateField.partial_unpack(buf)
        # Skip the width and the display
        buf = buf[1:]
        buf = buf[1:]
        num_choices = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        choices = []
        for _ in xrange(num_choices):
            val = struct.unpack('> I', buf[:4])[0]
            buf = buf[4:]
            choice = struct.unpack('> %us' % OPSFIntChoice.CHOICE_LEN, buf[:OPSFIntChoice.CHOICE_LEN])[0][:-1]
            buf = buf[OPSFIntChoice.CHOICE_LEN:]
            choices.append((val, choice))
        return OPSFIntChoice(name, desc, readOnly, choices)

    def __str__(self):
        return OPSFInt.__str__(self) + " choices=[%s]" % \
                ''.join(["%d:'%s', "%(v, c) for (v, c) in self.choices])

class OPSFTable(OPStateField):
    def __init__(self, name, desc, readOnly, depth, fields):
        OPStateField.__init__(self, name, desc, readOnly, OPStateField.TYPE_TABLE)
        self.depth = depth
        self.fields = fields

    def length(self):
        field_len = 0
        for field in self.fields:
            field_len += field.length()
        return OPStateField.length(self) + 2 + 2 + field_len

    def pack(self):
        hdr = OPStateField.pack(self)
        body = struct.pack('> H H', self.depth, len(self.fields))
        for field in self.fields:
            body += field.pack()
        return hdr + body

    @staticmethod
    def unpack(buf):
        # Skip the type
        (name, desc, readOnly, buf) = OPStateField.partial_unpack(buf)
        depth = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        num_fields = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        fields = []
        for _ in xrange(num_fields):
            field = OPStateField.unpack(buf)
            buf = buf[field.length():]
            fields.append(field)
        return OPSFTable(name, desc, readOnly, depth, fields)

    def __str__(self):
        return OPStateField.__str__(self) + " depth=%d fields=[%s]" % \
                (self.depth, ''.join([str(f) + ',' for f in self.fields]))


class OPStateValue(object):
    TYPE_INT = 1
    TYPE_TABLE_ENTRY = 2

    def __init__(self, name, type):
        self.name = name
        self.type = type

    def length(self):
        return OPStateField.NAME_LEN + 1

    def pack(self):
        return struct.pack('> B %us' % OPStateField.NAME_LEN, self.type, self.name)

    @staticmethod
    def unpack(buf):
        type = struct.unpack('> B', buf[:1])[0]
        if type == OPStateValue.TYPE_INT:
            return OPSVInt.unpack(buf)
        elif type == OPStateValue.TYPE_TABLE_ENTRY:
            return OPSVTableEntry.unpack(buf)
        else:
            raise LookupError("Unknown type '%d' while unpacking in OPStateValue"%type)

    @staticmethod
    def partial_unpack(buf):
        type = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        name = struct.unpack('> %us' % OPStateField.NAME_LEN, buf[:OPStateField.NAME_LEN])[0][:-1]
        buf = buf[OPStateField.NAME_LEN:]
        return (name, type, buf)

    def __str__(self):
        if self.type == OPStateValue.TYPE_INT:
            type = "integer"
        elif self.type == OPStateValue.TYPE_TABLE_ENTRY:
            type = "table_entry"
        return "OP_STATE_VALUE: name=%s type=%s" % \
                (self.name, type)

class OPSVInt(OPStateValue):
    def __init__(self, name, width, value):
        OPStateValue.__init__(self, name, OPStateValue.TYPE_INT)
        self.width = width
        self.value = value

    def length(self):
        return OPStateValue.length(self) + 1 + self.width

    def pack(self):
        hdr = OPStateValue.pack(self)
        body = struct.pack('> B', self.width)
        if self.width == 4:
            body += struct.pack('> I', self.value)
        elif self.width == 8:
            body += struct.pack('> Q', self.value)
        return hdr + body

    @staticmethod
    def unpack(buf):
        (name, type, buf) = OPStateValue.partial_unpack(buf)
        width = struct.unpack('> B', buf[:1])[0]
        buf = buf[1:]
        if width == 4:
            value = struct.unpack('> I', buf[:4])[0]
            buf = buf[4:]
        elif width == 8:
            value = struct.unpack('> Q', buf[:8])[0]
            buf = buf[8:]
        return OPSVInt(name, width, value)

    def __str__(self):
        return OPStateValue.__str__(self) + " width=%d value=%d (0x%x)" % \
                (self.width, self.value, self.value)

class OPSVTableEntry(OPStateValue):
    def __init__(self, name, entry, values):
        OPStateValue.__init__(self, name, OPStateValue.TYPE_TABLE_ENTRY)
        self.entry = entry
        self.values = values

    def length(self):
        val_len = 0
        for value in self.values:
            val_len += value.length()
        return OPStateValue.length(self) + 2 + 2 + val_len

    def pack(self):
        hdr = OPStateValue.pack(self)
        body = struct.pack('> HH', self.entry, len(self.values))
        for value in self.values:
            body += value.pack()
        return hdr + body

    @staticmethod
    def unpack(buf):
        (name, type, buf) = OPStateValue.partial_unpack(buf)
        entry = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        num_values = struct.unpack('> H', buf[:2])[0]
        buf = buf[2:]
        values = []
        for _ in xrange(num_values):
            value = OPStateValue.unpack(buf)
            buf = buf[value.length():]
            values.append(value)
        return OPSVTableEntry(name, entry, values)

    def __str__(self):
        return OPStateValue.__str__(self) + " entry=%d values=[%s]" % \
                (self.entry, ''.join([str(v) + ',' for v in self.values]))

class OPReadStateValues(OFGMessage):
    NAME_LEN = 16

    @staticmethod
    def get_type():
        return 0xF8

    def __init__(self, module, values, xid=0):
        OFGMessage.__init__(self, xid)
        self.module = module
        self.values = values

    def length(self):
        return OFGMessage.SIZE + Node.SIZE + \
                len(self.values) * OPReadStateValues.NAME_LEN

    def pack(self):
        valuesStr = ''
        for v in self.values:
            valuesStr += struct.pack('> %us'%OPReadStateValues.NAME_LEN, v)
        return OFGMessage.pack(self) + self.module.pack() + valuesStr

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        module = Node.unpack(body[:Node.SIZE])
        body = body[Node.SIZE:]
        num_values = len(body) / OPReadStateValues.NAME_LEN
        values = []
        for _ in xrange(num_values):
            value = struct.unpack('> %us' % OPReadStateValues.NAME_LEN, body[:OPReadStateValues.NAME_LEN])[0][:-1]
            values.append(value)
            body = body[OPReadStateValues.NAME_LEN:]
        return OPReadStateValues(module, values, xid)

    def __str__(self):
        return 'OP_READ_STATE_VALUES: ' + OFGMessage.__str__(self) + \
                ' module=%s values=[%s]' % \
                (str(self.module), ''.join([v + ',' for v in self.values]))

OFG_MESSAGES.append(OPReadStateValues)


class OPSetStateValues(OFGMessage):
    @staticmethod
    def get_type():
        return 0xF9

    def __init__(self, module, values, xid=0):
        OFGMessage.__init__(self, xid)
        self.module = module
        self.values = values

    def getValuesLength():
        valuesLen = 0
        for v in self.values:
            valuesLen += v.length()

    def length(self):
        return OFGMessage.SIZE + Node.SIZE + 2 + self.getValuesLength()

    def pack(self):
        return OFGMessage.pack(self) + self.module.pack() + \
                struct.pack('> H', len(self.values)) + \
                ''.join([v.pack() for v in self.values])

    @staticmethod
    def unpack(body):
        xid = struct.unpack('> I', body[:4])[0]
        body = body[4:]
        module = Node.unpack(body[:Node.SIZE])
        body = body[Node.SIZE:]
        num_values = struct.unpack('> H', body[:2])[0]
        body = body[2:]
        values = []
        for _ in xrange(num_values):
            value = OPStateValue.unpack(body)
            values.append(value)
            body = body[value.length():]
        return OPSetStateValues(module, values, xid)

    def __str__(self):
        return "OP_SET_STATE_VALUES: " + OFGMessage.__str__(self) + ' module=%s values=[%s]' % \
                (str(self.module), ''.join([str(v) + ',' for v in self.values]))

OFG_MESSAGES.append(OPSetStateValues)


class OPModuleStatusChange(OFGMessage):
    STATUS_READY = 1
    STATUS_NOT_READY = 2

    @staticmethod
    def get_type():
        return 0xFA

    def __init__(self, module, status, xid=0):
        OFGMessage.__init__(self, xid)
        self.module = module
        self.status = status

    def length(self):
        return OFGMessage.SIZE + Node.SIZE + 1

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = self.module.pack()
        body += struct.pack('> B', self.status)
        return hdr + body

    @staticmethod
    def unpack(body):
        raise Exception('OPModuleStatusChange.unpack() not implemented (one-way message)')

    def __str__(self):
        if self.status == OPModuleStatusChange.STATUS_READY:
            status = 'ready'
        elif self.status == OPModuleStatusChange.STATUS_NOT_READY:
            status = 'not ready'
        fmt = 'OP_MODULE_STATUS_CHANGE: ' + OFGMessage.__str__(self) + " status for module %s: %s"
        return fmt % (self.module, status)
OP_MESSAGES.append(OPModuleStatusChange)


class OPModuleAlert(OFGMessage):
    @staticmethod
    def get_type():
        return 0xFB

    def __init__(self, module, msg, xid=0):
        OFGMessage.__init__(self, xid)
        self.module = module
        self.msg = msg

    def length(self):
        return OFGMessage.SIZE + Node.SIZE + len(self.msg) + 1

    def pack(self):
        hdr = OFGMessage.pack(self)
        body = self.module.pack()
        body += struct.pack('> %us'%(len(self.msg) + 1), self.msg)
        return hdr + body

    @staticmethod
    def unpack(body):
        raise Exception('OPModuleAlert.unpack() not implemented (one-way message)')

    def __str__(self):
        fmt = 'OP_MODULE_ALERT: ' + OFGMessage.__str__(self) + " module: %s alert: '%s'"
        return fmt % (self.module, self.msg)
OP_MESSAGES.append(OPModuleAlert)


OP_PROTOCOL = LTProtocol(OFG_MESSAGES + OP_MESSAGES, 'H', 'B')

def run_op_server(port, recv_callback):
    """Starts a server which listens for Open Pipes clients on the specified port.

    @param port  the port to listen on
    @param recv_callback  the function to call with received message content
                         (takes two arguments: transport, msg)

    @return returns the new LTTwistedServer
    """
    from ltprotocol.ltprotocol import LTTwistedServer
    server = LTTwistedServer(OP_PROTOCOL, recv_callback)
    server.listen(port)
    reactor.run()

def test():
    # simply print out all received messages
    def print_ltm(xport, ltm):
        if ltm is not None:
            print 'recv: %s' % str(ltm)
            t = ltm.get_type()
            if t==LinksAdd.get_type() or t==LinksDel.get_type():
                # got request to add/del a link: tell the GUI we've done so
                xport.write(OP_PROTOCOL.pack_with_header(ltm))

    from ltprotocol.ltprotocol import LTTwistedServer
    server = LTTwistedServer(OP_PROTOCOL, print_ltm)
    server.listen(OFG_DEFAULT_PORT)

    # when the gui connects, tell it about the modules and nodes
    def new_conn_callback(conn):
        modules = [
            OPModule(Node.TYPE_MODULE_HW, 1, "MAC Lookup", []),
            OPModule(Node.TYPE_MODULE_HW, 2, "TTL Decrement", []),
            OPModule(Node.TYPE_MODULE_HW, 3, "TTL Decrement (FAULTY)", []),
            OPModule(Node.TYPE_MODULE_HW, 4, "Route Lookup", []),
            OPModule(Node.TYPE_MODULE_HW, 5, "Checksum Update", []),
            OPModule(Node.TYPE_MODULE_HW, 6, "TTL / Checksum Validate", []),
            OPModule(Node.TYPE_MODULE_SW, 100, "TTL / Checksum Validate", []),
            OPModule(Node.TYPE_MODULE_SW, 101, "Compar-ison Module", []),
            ]
        server.send_msg_to_client(conn, OPModulesAdd(modules))

        nodes = [
            OPNode(Node.TYPE_IN,       111, "Input", "Input"),
            OPNode(Node.TYPE_OUT,      999, "Output", "Output"),
            OPNode(Node.TYPE_NETFPGA, 1000, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1001, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1002, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1003, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1004, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1005, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_NETFPGA, 1006, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_PC,      2000, "pc1", "Core 2 Duo with 1G RAM"),
            OPNode(Node.TYPE_PC,      2001, "pc2", "Core 2 Duo with 2G RAM"),
            OPNode(Node.TYPE_PC,      2002, "pc3", "Centrino with 1G RAM"),
            OPNode(Node.TYPE_PC,      2003, "pc4", "Centrino with 1G RAM"),
            ]
        server.send_msg_to_client(conn, OPNodesAdd(nodes))

        nodes = [
            OPNode(Node.TYPE_NETFPGA, 1006, "NetFPGA", "NetFPGA"),
            OPNode(Node.TYPE_PC,      2003, "pc2", "Core 2 Duo with 2G RAM"),
            ]
        server.send_msg_to_client(conn, OPNodesDel(nodes))

        server.send_msg_to_client(conn, OPTestInfo("hello world", "happy world"))

        # tell the gui the route lookup module on netfpga 1000 works
        n = Node(Node.TYPE_NETFPGA, 1000)
        m = Node(Node.TYPE_MODULE_HW, 4)
        server.send_msg_to_client(conn, OPModuleStatusReply(n, m, "it works!"))

    server.new_conn_callback = new_conn_callback
    reactor.run()

if __name__ == "__main__":
    test()
