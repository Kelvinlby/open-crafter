package mod.kelvinlby.opencrafter.transport.msgpack;

/** MessagePack type bytes (see the MessagePack spec). */
final class MsgPackFormat {
	static final int POSITIVE_FIXINT_MAX = 0x7f;
	static final int FIXMAP = 0x80;
	static final int FIXARRAY = 0x90;
	static final int FIXSTR = 0xa0;
	static final int NIL = 0xc0;
	static final int NEVER_USED = 0xc1;
	static final int FALSE = 0xc2;
	static final int TRUE = 0xc3;
	static final int BIN8 = 0xc4;
	static final int BIN16 = 0xc5;
	static final int BIN32 = 0xc6;
	static final int EXT8 = 0xc7;
	static final int EXT16 = 0xc8;
	static final int EXT32 = 0xc9;
	static final int FLOAT32 = 0xca;
	static final int FLOAT64 = 0xcb;
	static final int UINT8 = 0xcc;
	static final int UINT16 = 0xcd;
	static final int UINT32 = 0xce;
	static final int UINT64 = 0xcf;
	static final int INT8 = 0xd0;
	static final int INT16 = 0xd1;
	static final int INT32 = 0xd2;
	static final int INT64 = 0xd3;
	static final int FIXEXT1 = 0xd4;
	static final int FIXEXT16 = 0xd8;
	static final int STR8 = 0xd9;
	static final int STR16 = 0xda;
	static final int STR32 = 0xdb;
	static final int ARRAY16 = 0xdc;
	static final int ARRAY32 = 0xdd;
	static final int MAP16 = 0xde;
	static final int MAP32 = 0xdf;
	static final int NEGATIVE_FIXINT = 0xe0;

	static final int FIX_COLLECTION_MAX = 15;
	static final int FIXSTR_MAX = 31;
	static final int MAX_DEPTH = 256;

	private MsgPackFormat() {
	}
}
