package art;

import java.util.Arrays;

class Node16 extends InnerNode {
	static final int NODE_SIZE = 16;
	private final Node[] child = new Node[NODE_SIZE];
	private final byte[] keys = new byte[NODE_SIZE];

	Node16(Node4 node) {
		super(node);
		if (node.noOfChildren != Node4.NODE_SIZE) {
			throw new IllegalArgumentException("Given Node4 still has capacity, cannot grow into Node16.");
		}
		byte[] keys = node.getKeys();
		Node[] child = node.getChild();
		System.arraycopy(keys, 0, this.keys, 0, node.noOfChildren);
		System.arraycopy(child, 0, this.child, 0, node.noOfChildren);
	}

	Node16(Node48 node48) {
		super(node48);
		if (!node48.shouldShrink()) {
			throw new IllegalArgumentException("Given Node48 hasn't crossed shrinking threshold yet");
		}
		byte[] keyIndex = node48.getKeyIndex();
		Node[] children = node48.getChild();

		// keyIndex by virtue of being "array indexed" is already sorted
		// so we can iterate and keep adding into Node16
		int j = 0;

		// go from -128 to -1 (interpreted as +128 to +255 unsigned int)
		for (byte i = Byte.MIN_VALUE; i < 0; i++) {
			int index = Byte.toUnsignedInt(i);
			if (keyIndex[index] != Node48.ABSENT) {
				keys[j] = BinaryComparableUtils.unsigned(i);
				child[j] = children[keyIndex[index]];
				j++;
			}
		}

		// i goes upto Byte.MAX_VALUE 127 and then becomes -128, where the loop will break
		for (byte i = 0; i >= 0; i++) {
			if (keyIndex[i] != Node48.ABSENT) {
				keys[j] = BinaryComparableUtils.unsigned(i);
				child[j] = children[keyIndex[i]];
				j++;
			}
		}

		assert j == NODE_SIZE;
	}

	@Override
	public Node findChild(byte partialKey) {
		partialKey = BinaryComparableUtils.unsigned(partialKey);
		// binary search for key
		// having the from and to gives us only a valid view into what are the
		// valid array elements that actually have keys and are not ABSENT
		int index = Arrays.binarySearch(keys, 0, noOfChildren, partialKey);
		if (index < 0) {
			return null;
		}
		return child[index];
	}

	// TODO: unit test binary search insertion point edge cases (first, last)
	@Override
	public boolean addChild(byte partialKey, Node child) {
		if (noOfChildren == NODE_SIZE) {
			return false;
		}
		partialKey = BinaryComparableUtils.unsigned(partialKey);

		int index = Arrays.binarySearch(keys, 0, noOfChildren, partialKey);
		// the partialKey should not exist
		if (index >= 0) {
			throw new IllegalArgumentException("Cannot insert partial key " + BinaryComparableUtils.signed(partialKey) + " that already exists in Node. "
					+ "If you want to replace the associated child pointer, use Node#replace(byte, Node)");
		}
		int insertionPoint = -(index + 1);
		// shift elements from this point to right by one place
		assert insertionPoint <= noOfChildren;
		for (int i = noOfChildren; i > insertionPoint; i--) {
			keys[i] = keys[i - 1];
			this.child[i] = this.child[i - 1];
		}
		keys[insertionPoint] = partialKey;
		this.child[insertionPoint] = child;
		noOfChildren++;
		return true;
	}

	@Override
	public void replace(byte partialKey, Node newChild) {
		partialKey = BinaryComparableUtils.unsigned(partialKey);
		int index = Arrays.binarySearch(keys, 0, noOfChildren, partialKey);
		if (index < 0) {
			throw new IllegalArgumentException("Partial key " + partialKey + " does not exist in this Node.");
		}
		child[index] = newChild;
	}

	@Override
	public void removeChild(byte partialKey) {
		partialKey = BinaryComparableUtils.unsigned(partialKey);
		int index = Arrays.binarySearch(keys, 0, noOfChildren, partialKey);
		// if this fails, the question is, how could you reach the leaf node?
		// this node must've been your follow on pointer holding the partialKey
		if (index < 0) {
			throw new IllegalArgumentException("Partial key " + partialKey + " does not exist in this Node.");
		}
		for (int i = index; i < noOfChildren - 1; i++) {
			keys[i] = keys[i + 1];
			child[i] = child[i + 1];
		}
		child[noOfChildren - 1] = null;
		noOfChildren--;
	}

	@Override
	public Node grow() {
		if (noOfChildren != NODE_SIZE) {
			throw new IllegalStateException("Grow should be called only when you reach a node's full capacity");
		}
		Node node = new Node48(this);
		return node;
	}

	@Override
	public boolean shouldShrink() {
		return noOfChildren == Node4.NODE_SIZE;
	}

	@Override
	public Node shrink() {
		if (!shouldShrink()) {
			throw new IllegalStateException("Haven't crossed shrinking threshold yet");
		}
		Node4 node4 = new Node4(this);
		return node4;
	}

	@Override
	public Node first() {
		return child[0];
	}

	@Override
	public Node last() {
		if(noOfChildren == 0){
			return null;
		}
		return child[noOfChildren - 1];
	}

	byte[] getKeys() {
		return keys;
	}

	Node[] getChild() {
		return child;
	}
}
