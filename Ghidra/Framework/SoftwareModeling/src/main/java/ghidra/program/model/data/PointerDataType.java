/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.program.model.data;

import java.util.HashSet;
import java.util.Set;

import ghidra.docking.settings.Settings;
import ghidra.program.database.data.DataTypeUtilities;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;

/**
 * Basic implementation for a pointer dataType 
 */
public class PointerDataType extends BuiltIn implements Pointer {

	private final static long serialVersionUID = 1;

	public final static PointerDataType dataType = new PointerDataType();

	public final static int MAX_POINTER_SIZE_BYTES = 8;

	public static final String POINTER_NAME = "pointer";
	public static final String POINTER_LABEL_PREFIX = "PTR";
	public static final String POINTER_LOOP_LABEL_PREFIX = "PTR_LOOP";

	protected DataType referencedDataType;
	protected int length;

	private boolean deleted = false;
	private String displayName;

	/**
	 * Creates a dynamically-sized default pointer data type.
	 * A dynamic pointer size of 4-bytes will be in used, but will adapt to a data type manager's 
	 * data organization when resolved.
	 */
	public PointerDataType() {
		this(null, -1, null);
	}

	/**
	 * Creates a dynamically-sized default pointer data type.
	 * The pointer size is established dynamically based upon the data organization 
	 * associated with the specified dtm but can adapt to another data type manager's 
	 * data organization when resolved.
	 * @param dtm data-type manager whose data organization should be used
	 */
	public PointerDataType(DataTypeManager dtm) {
		this(null, -1, dtm);
	}

	/**
	 * Construct a dynamically-sized pointer to a referencedDataType
	 * A dynamic pointer size of 4-bytes will be in used, but will adapt to a data type manager's 
	 * data organization when resolved.
	 * @param referencedDataType data type this pointer points to
	 */
	public PointerDataType(DataType referencedDataType) {
		this(referencedDataType, -1, null);
	}

	/**
	 * Construct a pointer of a specified length to a referencedDataType.
	 * Note: It is preferred to use default sized pointers when possible (i.e., length=-1, 
	 * see {@link #PointerDataType(DataType)}) instead of explicitly specifying 
	 * the pointer length value.
	 * @param referencedDataType data type this pointer points to
	 * @param length pointer length (values <= 0 will result in dynamically-sized pointer)
	 */
	public PointerDataType(DataType referencedDataType, int length) {
		this(referencedDataType, length, null);
	}

	/**
	 * Construct a dynamically-sized pointer to the given data type.
	 * The pointer size is established dynamically based upon the data organization 
	 * associated with the specified dtm but can adapt to another data type manager's 
	 * data organization when resolved.
	 * @param dt data type this pointer points to
	 * @param dtm data-type manager whose data organization should be used
	 */
	public PointerDataType(DataType referencedDataType, DataTypeManager dtm) {
		this(referencedDataType, -1, dtm);
	}

	/**
	 * Construct a pointer of a specified length to a referencedDataType.
	 * Note: It is preferred to use default sized pointers when possible (i.e., length=-1, 
	 * see {@link #PointerDataType(DataType, DataTypeManager)}) instead of explicitly specifying 
	 * the pointer length value.
	 * @param referencedDataType
	 * @param length pointer length (-1 will result in dynamically-sized pointer)
	 * @param dtm associated data type manager whose data organization will be used
	 */
	public PointerDataType(DataType referencedDataType, int length, DataTypeManager dtm) {
		super(referencedDataType != null ? referencedDataType.getCategoryPath() : null,
			constructUniqueName(referencedDataType, length), dtm);
		if (referencedDataType instanceof BitFieldDataType) {
			throw new IllegalArgumentException(
				"Pointer reference data-type may not be a bitfield: " +
					referencedDataType.getName());
		}
		this.length = length <= 0 ? -1 : length;
		this.referencedDataType = referencedDataType;
		if (referencedDataType != null) {
			referencedDataType.addParent(this);
		}
	}

	@Override
	public final DataType clone(DataTypeManager dtm) {
		if (dtm == getDataTypeManager()) {
			return this;
		}
		// don't clone referenced data-type to avoid potential circular reference
		return new PointerDataType(referencedDataType, length, dtm);
	}

	@Override
	public DataType getDataType() {
		return referencedDataType;
	}

	@Override
	public boolean isDynamicallySized() {
		return length <= 0;
	}

	@Override
	public int getLength() {
		return length <= 0 ? getDataOrganization().getPointerSize() : length;
	}

	@Override
	public String getDefaultLabelPrefix() {
		return POINTER_LABEL_PREFIX;
	}

	@Override
	public String getDefaultLabelPrefix(MemBuffer buf, Settings settings, int len,
			DataTypeDisplayOptions options) {
		return getLabelString(buf, settings, getLength(), options);
	}

	public static String getLabelString(MemBuffer buf, Settings settings, int len,
			DataTypeDisplayOptions options) {

		Program program = buf.getMemory().getProgram();
		if (program == null) {
			return POINTER_LABEL_PREFIX;
		}

		Address fromAddr = buf.getAddress();

		ReferenceManager refMgr = program.getReferenceManager();
		Reference ref = refMgr.getPrimaryReferenceFrom(fromAddr, 0);
		if (ref == null) {
			return POINTER_LABEL_PREFIX;
		}

		PointerReferenceClassification pointerClassification =
			getPointerClassification(program, ref);
		if (pointerClassification == PointerReferenceClassification.DEEP) {
			// pointer exceed depth limit of 2
			return POINTER_LABEL_PREFIX + "_" + POINTER_LABEL_PREFIX;
		}
		if (pointerClassification == PointerReferenceClassification.LOOP) {
			return POINTER_LOOP_LABEL_PREFIX;// pointer is self referencing
		}

		Symbol symbol = program.getSymbolTable().getSymbol(ref);
		if (symbol == null) {
			// unexpected
			return POINTER_LABEL_PREFIX;
		}

		String symName = symbol.getName();
		symName = SymbolUtilities.getCleanSymbolName(symName, ref.getToAddress());
		symName = symName.replace(Namespace.NAMESPACE_DELIMITER, "_");
		return POINTER_LABEL_PREFIX + "_" + symName;
	}

	private enum PointerReferenceClassification {
		// NORMAL - use recursive name generation (e.g., PTR_PTR_BYTE)
		NORMAL,
		// LOOP - references loop back - use label prefix PTR_LOOP  
		LOOP,
		// DEEP - references are too deep - use simple default label prefix
		DEEP
	}

	private static PointerReferenceClassification getPointerClassification(Program program,
			Reference ref) {

		Address fromAddr = ref.getFromAddress();

		Set<Address> refAddrs = new HashSet<>();
		refAddrs.add(fromAddr);
		int depth = 0;
		while (ref != null && ref.isMemoryReference()) {
			Address toAddr = ref.getToAddress();
			if (!refAddrs.add(toAddr)) {
				return PointerReferenceClassification.LOOP;
			}
			if (++depth > 2) {
				return PointerReferenceClassification.DEEP;
			}
			Data data = getDataAt(program, toAddr);
			if (data == null) {
				break;
			}
			ref = data.getPrimaryReference(0);
		}
		return PointerReferenceClassification.NORMAL;
	}

	private static Data getDataAt(Program program, Address addr) {
		Listing listing = program.getListing();
		Data data = listing.getDataContaining(addr);
		if (data != null) {
			int offset = (int) addr.subtract(data.getAddress());
			if (offset != 0) {
				data = data.getPrimitiveAt(offset);
			}
		}
		return data;
	}

	@Override
	public String getDisplayName() {
		if (displayName == null) {
			DataType dt = getDataType();
			if (dt == null) {
				displayName = POINTER_NAME;
				if (length > 0) {
					displayName += Integer.toString(8 * length);
				}
			}
			else {
				displayName = dt.getDisplayName() + " *";
			}
		}
		return displayName;
	}

	/**
	 * Return a unique name for this pointer
	 */
	private static String constructUniqueName(DataType referencedDataType, int ptrLength) {
		if (referencedDataType == null) {
			String s = POINTER_NAME;
			if (ptrLength > 0) {
				s += Integer.toString(8 * ptrLength);
			}
			return s;
		}
		String s = referencedDataType.getName() + " *";
		if (ptrLength > 0) {
			s += Integer.toString(8 * ptrLength);
		}
		return s;
	}

	@Override
	public String getDescription() {
		StringBuffer sbuf = new StringBuffer();
		if (length > 0) {
			sbuf.append(Integer.toString(8 * length));
			sbuf.append("-bit ");
		}
		sbuf.append(POINTER_NAME);
		DataType dt = getDataType();
		if (dt != null) {
			sbuf.append(" to ");
			if (dt instanceof Pointer) {
				sbuf.append(getDataType().getDescription());
			}
			else {
				sbuf.append(getDataType().getDisplayName());
			}
		}
		return sbuf.toString();
	}

	@Override
	public String getMnemonic(Settings settings) {
		if (referencedDataType == null || referencedDataType == DataType.DEFAULT) {
			return "addr";
		}
		return referencedDataType.getMnemonic(settings) + " *";
	}

	@Override
	public Object getValue(MemBuffer buf, Settings settings, int len) {

		// TODO: Which address space should pointer refer to ??

		return getAddressValue(buf, getLength(), buf.getAddress().getAddressSpace());
	}

	@Override
	public Class<?> getValueClass(Settings settings) {
		return Address.class;
	}

	/**
	 * Generate an address value based upon bytes stored at the specified buf location
	 * @param buf memory buffer and stored pointer location
	 * @param size pointer size in bytes
	 * @param targetSpace address space for returned pointer
	 * @return pointer value or null if unusable buf or data
	 */
	public static Address getAddressValue(MemBuffer buf, int size, AddressSpace targetSpace) {

		if (size <= 0 || size > 8) {
			return null;
		}

		if (buf.getAddress() instanceof SegmentedAddress) {
			try {
				return getSegmentedAddressValue(buf, size);
			}
			catch (AddressOutOfBoundsException e) {
				// offset too large
			}
			catch (IllegalArgumentException iae) {
				// Do nothing... Tried to create an address that was too large
				// for the address space
				//
				// For example, trying to create a 56 bit pointer in a
				// 32 bit address space.
			}
			return null;
		}

		byte[] bytes = new byte[size];
		if (buf.getBytes(bytes, 0) != size) {
			return null;
		}

		boolean isBigEndian = buf.isBigEndian(); // ENDIAN.isBigEndian(settings, buf);

		if (!isBigEndian) {
			byte[] flipped = new byte[size];
			for (int i = 0; i < size; i++) {
				flipped[i] = bytes[size - i - 1];
			}
			bytes = flipped;
		}

		// Use long when possible
		long val = 0;
		for (byte b : bytes) {
			val = (val << 8) + (b & 0x0ffL);
		}

		try {
			return targetSpace.getAddress(val, true);
		}
		catch (AddressOutOfBoundsException e) {
			// offset too large
		}
		return null;
	}

	private static Address getSegmentedAddressValue(MemBuffer buf, int dataLen) {
		SegmentedAddress a = (SegmentedAddress) buf.getAddress();
		int segment = a.getSegment();
		int offset = 0;
		try {
			switch (dataLen) {
				case 1:
					offset = buf.getByte(0) & 0xff;
					break;
				case 2:
					offset = buf.getShort(0) & 0xffff;
					break;
				case 4:
				case 8:
					segment = buf.getShort(0) & 0xffff;
					offset = buf.getShort(2) & 0xffff;
					break;
				default:
					return null;
			}

		}
		catch (MemoryAccessException e) {
			return null;
		}
		SegmentedAddressSpace space = (SegmentedAddressSpace) a.getAddressSpace();
		SegmentedAddress addr = space.getAddress(segment, offset);
		return normalize(addr, buf.getMemory());
	}

	private static SegmentedAddress normalize(SegmentedAddress addr, Memory memory) {
		if (memory == null) {
			return addr;
		}
		MemoryBlock block = memory.getBlock(addr);
		if (block == null) {
			return addr;
		}
		SegmentedAddress start = (SegmentedAddress) block.getStart();
		return addr.normalize(start.getSegment());

	}

	@Override
	public String getRepresentation(MemBuffer buf, Settings settings, int len) {

		Address addr = (Address) getValue(buf, settings, len);
		if (addr == null) { // could not create address, so return "Not a pointer (NaP)"
			return "NaP";
		}
		return addr.toString();
	}

	@Override
	public boolean isEquivalent(DataType dt) {
		if (dt == null) {
			return false;
		}
		if (this == dt) {
			return true;
		}
		if (!(dt instanceof Pointer)) {
			return false;
		}

		Pointer p = (Pointer) dt;
		DataType otherDataType = p.getDataType();
		if (isDynamicallySized() != p.isDynamicallySized()) {
			return false;
		}
		if (!isDynamicallySized() && (getLength() != p.getLength())) {
			return false;
		}
		if (referencedDataType == null) {
			return otherDataType == null;
		}
		if (otherDataType == null) {
			return false;
		}

		// if they contain datatypes that have same ids, then we are essentially equivalent.
		if (DataTypeUtilities.isSameDataType(referencedDataType, otherDataType)) {
			return true;
		}

		return DataTypeUtilities.equalsIgnoreConflict(referencedDataType.getPathName(),
			otherDataType.getPathName());
	}

	@Override
	public void dataTypeDeleted(DataType dt) {
		if (referencedDataType == dt) {
			notifyDeleted();
			deleted = true;
		}
	}

	@Override
	public boolean isDeleted() {
		return deleted;
	}

	@Override
	public void dataTypeReplaced(DataType oldDt, DataType newDt) {
		if (referencedDataType == oldDt) {
			referencedDataType.removeParent(this);
			referencedDataType = newDt;
			referencedDataType.addParent(this);
			displayName = null;
			String oldName = name;
			name = constructUniqueName(referencedDataType, length);
			notifyNameChanged(oldName);
		}
	}

	@Override
	public void dataTypeNameChanged(DataType dt, String oldName) {
		if (referencedDataType == dt) {
			displayName = null;
			name = constructUniqueName(referencedDataType, length);
			notifyNameChanged(oldName);
		}
	}

	@Override
	public CategoryPath getCategoryPath() {
		DataType dt = getDataType();
		if (dt == null) {
			return CategoryPath.ROOT;
		}
		return dt.getCategoryPath();
	}

	@Override
	public boolean dependsOn(DataType dt) {
		if (referencedDataType == null) {
			return false;
		}
		return (referencedDataType == dt || referencedDataType.dependsOn(dt));
	}

	/**
	 * Get a pointer data-type instance with a default size
	 * @param dt data-type referenced by pointer
	 * @param dtm program data-type manager (required)
	 * a generic data-type will be returned if possible.
	 * @return signed integer data type
	 */
	public static Pointer getPointer(DataType dt, DataTypeManager dtm) {
		return new PointerDataType(dt, dtm);
	}

	/**
	 * Get a pointer data-type instance of the requested size.
	 * NOTE: The returned data-type will not be associated with any 
	 * particular data-type-manager and may therefore not utilize 
	 * dynamically-sized-pointers when a valid pointerSize is specified.
	 * If an invalid pointerSize is specified, a dynamically-size pointer
	 * will be returned whose length is based upon the default-data-organization.
	 * 
	 * @param dt data-type referenced by pointer
	 * @param pointerSize pointer size
	 * @return signed integer data type
	 */
	public static Pointer getPointer(DataType dt, int pointerSize) {
// TODO: Need to eliminate all uses of this method !!
		if (pointerSize < 1 || pointerSize > 8) {
			return new PointerDataType(dt);
		}
		return new PointerDataType(dt, pointerSize);
	}

	@Override
	public Pointer newPointer(DataType dt) {
		if (dt != null) {
			dt = dt.clone(getDataTypeManager());
		}
		return new PointerDataType(dt, length, getDataTypeManager());
	}

	@Override
	public String getName() {
		if (referencedDataType != null) {
			// referencedDataType may have had name set, so re-create this pointer name. 
			name = constructUniqueName(referencedDataType, length);
		}
		return super.getName();
	}
}
