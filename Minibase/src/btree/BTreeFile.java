/*
 * @(#) bt.java   98/03/24
 * Copyright (c) 1998 UW.  All Rights Reserved.
 *         Author: Xiaohu Li (xioahu@cs.wisc.edu).
 *
 */

package btree;

import java.io.*;
import diskmgr.*;
import bufmgr.*;
import global.*;
import heap.*;

/**
 * btfile.java This is the main definition of class BTreeFile, which derives
 * from abstract base class IndexFile. It provides an insert/delete interface.
 */
public class BTreeFile extends IndexFile implements GlobalConst {

	private final static int MAGIC0 = 1989;

	private final static String lineSep = System.getProperty("line.separator");

	private static FileOutputStream fos;
	private static DataOutputStream trace;

	/**
	 * It causes a structured trace to be written to a file. This output is used
	 * to drive a visualization tool that shows the inner workings of the b-tree
	 * during its operations.
	 * 
	 * @param filename
	 *            input parameter. The trace file name
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void traceFilename(String filename) throws IOException {

		fos = new FileOutputStream(filename);
		trace = new DataOutputStream(fos);
	}

	/**
	 * Stop tracing. And close trace file.
	 * 
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void destroyTrace() throws IOException {
		if (trace != null)
			trace.close();
		if (fos != null)
			fos.close();
		fos = null;
		trace = null;
	}

	private BTreeHeaderPage headerPage;
	private PageId headerPageId;
	private String dbname;

	/**
	 * Access method to data member.
	 * 
	 * @return Return a BTreeHeaderPage object that is the header page of this
	 *         btree file.
	 */
	public BTreeHeaderPage getHeaderPage() {
		return headerPage;
	}

	private PageId get_file_entry(String filename) throws GetFileEntryException {
		try {
			return SystemDefs.JavabaseDB.get_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new GetFileEntryException(e, "");
		}
	}

	private Page pinPage(PageId pageno) throws PinPageException {
		try {
			Page page = new Page();
			SystemDefs.JavabaseBM.pinPage(pageno, page, false/* Rdisk */);
			return page;
		} catch (Exception e) {
			e.printStackTrace();
			throw new PinPageException(e, "");
		}
	}

	private void add_file_entry(String fileName, PageId pageno) throws AddFileEntryException {
		try {
			SystemDefs.JavabaseDB.add_file_entry(fileName, pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AddFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, false /* = not DIRTY */);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	private void freePage(PageId pageno) throws FreePageException {
		try {
			SystemDefs.JavabaseBM.freePage(pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new FreePageException(e, "");
		}

	}

	private void delete_file_entry(String filename) throws DeleteFileEntryException {
		try {
			SystemDefs.JavabaseDB.delete_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeleteFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno, boolean dirty) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, dirty);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	/**
	 * BTreeFile class an index file with given filename should already exist;
	 * this opens it.
	 * 
	 * @param filename
	 *            the B+ tree file name. Input parameter.
	 * @exception GetFileEntryException
	 *                can not ger the file from DB
	 * @exception PinPageException
	 *                failed when pin a page
	 * @exception ConstructPageException
	 *                BT page constructor failed
	 */
	public BTreeFile(String filename) throws GetFileEntryException, PinPageException, ConstructPageException {

		headerPageId = get_file_entry(filename);

		headerPage = new BTreeHeaderPage(headerPageId);
		dbname = new String(filename);
		/*
		 *
		 * - headerPageId is the PageId of this BTreeFile's header page; -
		 * headerPage, headerPageId valid and pinned - dbname contains a copy of
		 * the name of the database
		 */
	}

	/**
	 * if index file exists, open it; else create it.
	 * 
	 * @param filename
	 *            file name. Input parameter.
	 * @param keytype
	 *            the type of key. Input parameter.
	 * @param keysize
	 *            the maximum size of a key. Input parameter.
	 * @param delete_fashion
	 *            full delete or naive delete. Input parameter. It is either
	 *            DeleteFashion.NAIVE_DELETE or DeleteFashion.FULL_DELETE.
	 * @exception GetFileEntryException
	 *                can not get file
	 * @exception ConstructPageException
	 *                page constructor failed
	 * @exception IOException
	 *                error from lower layer
	 * @exception AddFileEntryException
	 *                can not add file into DB
	 */
	public BTreeFile(String filename, int keytype, int keysize, int delete_fashion)
			throws GetFileEntryException, ConstructPageException, IOException, AddFileEntryException {

		headerPageId = get_file_entry(filename);
		if (headerPageId == null) // file not exist
		{
			headerPage = new BTreeHeaderPage();
			headerPageId = headerPage.getPageId();
			add_file_entry(filename, headerPageId);
			headerPage.set_magic0(MAGIC0);
			headerPage.set_rootId(new PageId(INVALID_PAGE));
			headerPage.set_keyType((short) keytype);
			headerPage.set_maxKeySize(keysize);
			headerPage.set_deleteFashion(delete_fashion);
			headerPage.setType(NodeType.BTHEAD);
		} else {
			headerPage = new BTreeHeaderPage(headerPageId);
		}

		dbname = new String(filename);

	}

	/**
	 * Close the B+ tree file. Unpin header page.
	 * 
	 * @exception PageUnpinnedException
	 *                error from the lower layer
	 * @exception InvalidFrameNumberException
	 *                error from the lower layer
	 * @exception HashEntryNotFoundException
	 *                error from the lower layer
	 * @exception ReplacerException
	 *                error from the lower layer
	 */
	public void close()
			throws PageUnpinnedException, InvalidFrameNumberException, HashEntryNotFoundException, ReplacerException {
		if (headerPage != null) {
			SystemDefs.JavabaseBM.unpinPage(headerPageId, true);
			headerPage = null;
		}
	}

	/**
	 * Destroy entire B+ tree file.
	 * 
	 * @exception IOException
	 *                error from the lower layer
	 * @exception IteratorException
	 *                iterator error
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception FreePageException
	 *                error when free a page
	 * @exception DeleteFileEntryException
	 *                failed when delete a file from DM
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                failed when pin a page
	 */
	public void destroyFile() throws IOException, IteratorException, UnpinPageException, FreePageException,
			DeleteFileEntryException, ConstructPageException, PinPageException {
		if (headerPage != null) {
			PageId pgId = headerPage.get_rootId();
			if (pgId.pid != INVALID_PAGE)
				_destroyFile(pgId);
			unpinPage(headerPageId);
			freePage(headerPageId);
			delete_file_entry(dbname);
			headerPage = null;
		}
	}

	private void _destroyFile(PageId pageno) throws IOException, IteratorException, PinPageException,
			ConstructPageException, UnpinPageException, FreePageException {

		BTSortedPage sortedPage;
		Page page = pinPage(pageno);
		sortedPage = new BTSortedPage(page, headerPage.get_keyType());

		if (sortedPage.getType() == NodeType.INDEX) {
			BTIndexPage indexPage = new BTIndexPage(page, headerPage.get_keyType());
			RID rid = new RID();
			PageId childId;
			KeyDataEntry entry;
			for (entry = indexPage.getFirst(rid); entry != null; entry = indexPage.getNext(rid)) {
				childId = ((IndexData) (entry.data)).getData();
				_destroyFile(childId);
			}
		} else { // BTLeafPage

			unpinPage(pageno);
			freePage(pageno);
		}

	}

	private void updateHeader(PageId newRoot) throws IOException, PinPageException, UnpinPageException {

		BTreeHeaderPage header;
		PageId old_data;

		header = new BTreeHeaderPage(pinPage(headerPageId));

		old_data = headerPage.get_rootId();
		header.set_rootId(newRoot);

		// clock in dirty bit to bm so our dtor needn't have to worry about it
		unpinPage(headerPageId, true /* = DIRTY */ );

		// ASSERTIONS:
		// - headerPage, headerPageId valid, pinned and marked as dirty

	}

	/**
	 * insert record with the given key and rid
	 * 
	 * @param key
	 *            the key of the record. Input parameter.
	 * @param rid
	 *            the rid of the record. Input parameter.
	 * @exception KeyTooLongException
	 *                key size exceeds the max keysize.
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IOException
	 *                error from the lower layer
	 * @exception LeafInsertRecException
	 *                insert error in leaf page
	 * @exception IndexInsertRecException
	 *                insert error in index page
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception NodeNotMatchException
	 *                node not match index page nor leaf page
	 * @exception ConvertException
	 *                error when convert between revord and byte array
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error when search
	 * @exception IteratorException
	 *                iterator error
	 * @exception LeafDeleteException
	 *                error when delete in leaf page
	 * @exception InsertException
	 *                error when insert in index page
	 */
	public void insert(KeyClass key, RID rid) throws KeyTooLongException, KeyNotMatchException, LeafInsertRecException,
			IndexInsertRecException, ConstructPageException, UnpinPageException, PinPageException,
			NodeNotMatchException, ConvertException, DeleteRecException, IndexSearchException, IteratorException,
			LeafDeleteException, InsertException, IOException

	{
		KeyDataEntry newRootEntry;

		if (BT.getKeyLength(key) > headerPage.get_maxKeySize())
			throw new KeyTooLongException(null, "");

		if (key instanceof StringKey) {
			if (headerPage.get_keyType() != AttrType.attrString) {
				throw new KeyNotMatchException(null, "");
			}
		} else if (key instanceof IntegerKey) {
			if (headerPage.get_keyType() != AttrType.attrInteger) {
				throw new KeyNotMatchException(null, "");
			}
		} else
			throw new KeyNotMatchException(null, "");

		// TWO CASES:
		// 1. headerPage.root == INVALID_PAGE:
		// - the tree is empty and we have to create a new first page;
		// this page will be a leaf page
		// 2. headerPage.root != INVALID_PAGE:
		// - we call _insert() to insert the pair (key, rid)

		if (trace != null) {
			trace.writeBytes("INSERT " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
			trace.writeBytes("DO" + lineSep);
			trace.flush();
		}

		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			PageId newRootPageId;
			BTLeafPage newRootPage;
			RID dummyrid;

			newRootPage = new BTLeafPage(headerPage.get_keyType());
			newRootPageId = newRootPage.getCurPage();

			if (trace != null) {
				trace.writeBytes("NEWROOT " + newRootPageId + lineSep);
				trace.flush();
			}

			newRootPage.setNextPage(new PageId(INVALID_PAGE));
			newRootPage.setPrevPage(new PageId(INVALID_PAGE));

			// ASSERTIONS:
			// - newRootPage, newRootPageId valid and pinned

			newRootPage.insertRecord(key, rid);

			if (trace != null) {
				trace.writeBytes("PUTIN node " + newRootPageId + lineSep);
				trace.flush();
			}

			unpinPage(newRootPageId, true); /* = DIRTY */
			updateHeader(newRootPageId);

			if (trace != null) {
				trace.writeBytes("DONE" + lineSep);
				trace.flush();
			}

			return;
		}

		// ASSERTIONS:
		// - headerPageId, headerPage valid and pinned
		// - headerPage.root holds the pageId of the root of the B-tree
		// - none of the pages of the tree is pinned yet

		if (trace != null) {
			trace.writeBytes("SEARCH" + lineSep);
			trace.flush();
		}

		newRootEntry = _insert(key, rid, headerPage.get_rootId());

		// TWO CASES:
		// - newRootEntry != null: a leaf split propagated up to the root
		// and the root split: the new pageNo is in
		// newChildEntry.data.pageNo
		// - newRootEntry == null: no new root was created;
		// information on headerpage is still valid

		// ASSERTIONS:
		// - no page pinned

		if (newRootEntry != null) {
			BTIndexPage newRootPage;
			PageId newRootPageId;
			Object newEntryKey;

			// the information about the pair <key, PageId> is
			// packed in newRootEntry: extract it

			newRootPage = new BTIndexPage(headerPage.get_keyType());
			newRootPageId = newRootPage.getCurPage();

			// ASSERTIONS:
			// - newRootPage, newRootPageId valid and pinned
			// - newEntryKey, newEntryPage contain the data for the new entry
			// which was given up from the level down in the recursion

			if (trace != null) {
				trace.writeBytes("NEWROOT " + newRootPageId + lineSep);
				trace.flush();
			}

			newRootPage.insertKey(newRootEntry.key, ((IndexData) newRootEntry.data).getData());

			// the old root split and is now the left child of the new root
			newRootPage.setPrevPage(headerPage.get_rootId());

			unpinPage(newRootPageId, true /* = DIRTY */);

			updateHeader(newRootPageId);

		}

		if (trace != null) {
			trace.writeBytes("DONE" + lineSep);
			trace.flush();
		}

		return;
	}

	private KeyDataEntry _insert(KeyClass key, RID rid, PageId currentPageId)
			throws PinPageException, IOException, ConstructPageException, LeafDeleteException, ConstructPageException,
			DeleteRecException, IndexSearchException, UnpinPageException, LeafInsertRecException, ConvertException,
			IteratorException, IndexInsertRecException, KeyNotMatchException, NodeNotMatchException, InsertException

	{
		BTSortedPage currentPage;
		Page page;
		KeyDataEntry upEntry;

		page = pinPage(currentPageId);
		currentPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + currentPageId + lineSep);
			trace.flush();
		}

		// TWO CASES:
		// - pageType == INDEX:
		// recurse and then split if necessary
		// - pageType == LEAF:
		// try to insert pair (key, rid), maybe split

		if (currentPage.getType() == NodeType.INDEX) {
			BTIndexPage currentIndexPage = new BTIndexPage(page, headerPage.get_keyType());
			PageId currentIndexPageId = currentPageId;
			PageId nextPageId;

			nextPageId = currentIndexPage.getPageNoByKey(key);

			// now unpin the page, recurse and then pin it again
			unpinPage(currentIndexPageId);

			upEntry = _insert(key, rid, nextPageId);

			// two cases:
			// - upEntry == null: one level lower no split has occurred:
			// we are done.
			// - upEntry != null: one of the children has split and
			// upEntry is the new data entry which has
			// to be inserted on this index page

			if (upEntry == null)
				return null;

			currentIndexPage = new BTIndexPage(pinPage(currentPageId), headerPage.get_keyType());

			// ASSERTIONS:
			// - upEntry != null
			// - currentIndexPage, currentIndexPageId valid and pinned

			// the information about the pair <key, PageId> is
			// packed in upEntry

			// check whether there can still be entries inserted on that page
			if (currentIndexPage.available_space() >= BT.getKeyDataLength(upEntry.key, NodeType.INDEX)) {

				// no split has occurred
				currentIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());

				unpinPage(currentIndexPageId, true /* DIRTY */);

				return null;
			}

			// ASSERTIONS:
			// - on the current index page is not enough space available .
			// it splits

			// therefore we have to allocate a new index page and we will
			// distribute the entries
			// - currentIndexPage, currentIndexPageId valid and pinned

			BTIndexPage newIndexPage;
			PageId newIndexPageId;

			// we have to allocate a new INDEX page and
			// to redistribute the index entries
			newIndexPage = new BTIndexPage(headerPage.get_keyType());
			newIndexPageId = newIndexPage.getCurPage();

			if (trace != null) {
				if (headerPage.get_rootId().pid != currentIndexPageId.pid)
					trace.writeBytes("SPLIT node " + currentIndexPageId + " IN nodes " + currentIndexPageId + " "
							+ newIndexPageId + lineSep);
				else
					trace.writeBytes("ROOTSPLIT IN nodes " + currentIndexPageId + " " + newIndexPageId + lineSep);
				trace.flush();
			}

			// ASSERTIONS:
			// - newIndexPage, newIndexPageId valid and pinned
			// - currentIndexPage, currentIndexPageId valid and pinned
			// - upEntry containing (Key, Page) for the new entry which was
			// given up from the level down in the recursion

			KeyDataEntry tmpEntry;
			PageId tmpPageId;
			RID insertRid;
			RID delRid = new RID();

			for (tmpEntry = currentIndexPage.getFirst(delRid); tmpEntry != null; tmpEntry = currentIndexPage
					.getFirst(delRid)) {
				newIndexPage.insertKey(tmpEntry.key, ((IndexData) tmpEntry.data).getData());
				currentIndexPage.deleteSortedRecord(delRid);
			}

			// ASSERTIONS:
			// - currentIndexPage empty
			// - newIndexPage holds all former records from currentIndexPage

			// we will try to make an equal split
			RID firstRid = new RID();
			KeyDataEntry undoEntry = null;
			for (tmpEntry = newIndexPage.getFirst(firstRid); (currentIndexPage.available_space() > newIndexPage
					.available_space()); tmpEntry = newIndexPage.getFirst(firstRid)) {
				// now insert the <key,pageId> pair on the new
				// index page
				undoEntry = tmpEntry;
				currentIndexPage.insertKey(tmpEntry.key, ((IndexData) tmpEntry.data).getData());
				newIndexPage.deleteSortedRecord(firstRid);
			}

			// undo the final record
			if (currentIndexPage.available_space() < newIndexPage.available_space()) {

				newIndexPage.insertKey(undoEntry.key, ((IndexData) undoEntry.data).getData());

				currentIndexPage.deleteSortedRecord(
						new RID(currentIndexPage.getCurPage(), (int) currentIndexPage.getSlotCnt() - 1));
			}

			// check whether <newKey, newIndexPageId>
			// will be inserted
			// on the newly allocated or on the old index page

			tmpEntry = newIndexPage.getFirst(firstRid);

			if (BT.keyCompare(upEntry.key, tmpEntry.key) >= 0) {
				// the new data entry belongs on the new index page
				newIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());
			} else {
				currentIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());

				// int i= (int)currentIndexPage.getSlotCnt()-1;
				// tmpEntry =BT.getEntryFromBytes(currentIndexPage.getpage(),
				// currentIndexPage.getSlotOffset(i),
				// currentIndexPage.getSlotLength(i),
				// headerPage.get_keyType(),NodeType.INDEX);

				// newIndexPage.insertKey( tmpEntry.key,
				// ((IndexData)tmpEntry.data).getData());

				// currentIndexPage.deleteSortedRecord
				// (new RID(currentIndexPage.getCurPage(), i) );

			}

			unpinPage(currentIndexPageId, true /* dirty */);

			// fill upEntry
			upEntry = newIndexPage.getFirst(delRid);

			// now set prevPageId of the newIndexPage to the pageId
			// of the deleted entry:
			newIndexPage.setPrevPage(((IndexData) upEntry.data).getData());

			// delete first record on new index page since it is given up
			newIndexPage.deleteSortedRecord(delRid);

			unpinPage(newIndexPageId, true /* dirty */);

			if (trace != null) {
				trace_children(currentIndexPageId);
				trace_children(newIndexPageId);
			}

			((IndexData) upEntry.data).setData(newIndexPageId);

			return upEntry;

			// ASSERTIONS:
			// - no pages pinned
			// - upEntry holds the pointer to the KeyDataEntry which is
			// to be inserted on the index page one level up

		}

		else if (currentPage.getType() == NodeType.LEAF) {
			BTLeafPage currentLeafPage = new BTLeafPage(page, headerPage.get_keyType());

			PageId currentLeafPageId = currentPageId;

			// ASSERTIONS:
			// - currentLeafPage, currentLeafPageId valid and pinned

			// check whether there can still be entries inserted on that page
			if (currentLeafPage.available_space() >= BT.getKeyDataLength(key, NodeType.LEAF)) {
				// no split has occurred

				currentLeafPage.insertRecord(key, rid);

				unpinPage(currentLeafPageId, true /* DIRTY */);

				if (trace != null) {
					trace.writeBytes("PUTIN node " + currentLeafPageId + lineSep);
					trace.flush();
				}

				return null;
			}

			// ASSERTIONS:
			// - on the current leaf page is not enough space available.
			// It splits.
			// - therefore we have to allocate a new leaf page and we will
			// - distribute the entries

			BTLeafPage newLeafPage;
			PageId newLeafPageId;
			// we have to allocate a new LEAF page and
			// to redistribute the data entries entries
			newLeafPage = new BTLeafPage(headerPage.get_keyType());
			newLeafPageId = newLeafPage.getCurPage();

			newLeafPage.setNextPage(currentLeafPage.getNextPage());
			newLeafPage.setPrevPage(currentLeafPageId); // for dbl-linked list
			currentLeafPage.setNextPage(newLeafPageId);

			// change the prevPage pointer on the next page:
			// PageId rightPageId;
			// rightPageId = newLeafPage.getNextPage();

			// if (rightPageId.pid != INVALID_PAGE)
			// {
			// System.out.println(rightPageId.pid);
			// BTLeafPage rightPage;
			// rightPage=new BTLeafPage(rightPageId, headerPage.get_keyType());

			// rightPage.setPrevPage(newLeafPageId);
			// unpinPage(rightPageId, true /* = DIRTY */);

			// ASSERTIONS:
			// - newLeafPage, newLeafPageId valid and pinned
			// - currentLeafPage, currentLeafPageId valid and pinned
			// }

			if (trace != null) {
				if (headerPage.get_rootId().pid != currentLeafPageId.pid)
					trace.writeBytes("SPLIT node " + currentLeafPageId + " IN nodes " + currentLeafPageId + " "
							+ newLeafPageId + lineSep);
				else
					trace.writeBytes("ROOTSPLIT IN nodes " + currentLeafPageId + " " + newLeafPageId + lineSep);
				trace.flush();
			}

			KeyDataEntry tmpEntry;
			RID firstRid = new RID();

			for (tmpEntry = currentLeafPage.getFirst(firstRid); tmpEntry != null; tmpEntry = currentLeafPage
					.getFirst(firstRid)) {

				newLeafPage.insertRecord(tmpEntry.key, ((LeafData) (tmpEntry.data)).getData());
				currentLeafPage.deleteSortedRecord(firstRid);

			}

			// ASSERTIONS:
			// - currentLeafPage empty
			// - newLeafPage holds all former records from currentLeafPage

			KeyDataEntry undoEntry = null;
			for (tmpEntry = newLeafPage.getFirst(firstRid); newLeafPage.available_space() < currentLeafPage
					.available_space(); tmpEntry = newLeafPage.getFirst(firstRid)) {
				undoEntry = tmpEntry;
				currentLeafPage.insertRecord(tmpEntry.key, ((LeafData) tmpEntry.data).getData());
				newLeafPage.deleteSortedRecord(firstRid);
			}

			if (BT.keyCompare(key, undoEntry.key) < 0) {
				// undo the final record
				if (currentLeafPage.available_space() < newLeafPage.available_space()) {
					newLeafPage.insertRecord(undoEntry.key, ((LeafData) undoEntry.data).getData());

					currentLeafPage.deleteSortedRecord(
							new RID(currentLeafPage.getCurPage(), (int) currentLeafPage.getSlotCnt() - 1));
				}
			}

			// check whether <key, rid>
			// will be inserted
			// on the newly allocated or on the old leaf page

			if (BT.keyCompare(key, undoEntry.key) >= 0) {
				// the new data entry belongs on the new Leaf page
				newLeafPage.insertRecord(key, rid);

				if (trace != null) {
					trace.writeBytes("PUTIN node " + newLeafPageId + lineSep);
					trace.flush();
				}

			} else {
				currentLeafPage.insertRecord(key, rid);
			}

			unpinPage(currentLeafPageId, true /* dirty */);

			if (trace != null) {
				trace_children(currentLeafPageId);
				trace_children(newLeafPageId);
			}

			// fill upEntry
			tmpEntry = newLeafPage.getFirst(firstRid);
			upEntry = new KeyDataEntry(tmpEntry.key, newLeafPageId);

			unpinPage(newLeafPageId, true /* dirty */);

			// ASSERTIONS:
			// - no pages pinned
			// - upEntry holds the valid KeyDataEntry which is to be inserted
			// on the index page one level up
			return upEntry;
		} else {
			throw new InsertException(null, "");
		}
	}

	/**
	 * delete leaf entry given its <key, rid> pair. `rid' is IN the data entry;
	 * it is not the id of the data entry)
	 * 
	 * @param key
	 *            the key in pair <key, rid>. Input Parameter.
	 * @param rid
	 *            the rid in pair <key, rid>. Input Parameter.
	 * @return true if deleted. false if no such record.
	 * @exception DeleteFashionException
	 *                neither full delete nor naive delete
	 * @exception LeafRedistributeException
	 *                redistribution error in leaf pages
	 * @exception RedistributeException
	 *                redistribution error in index pages
	 * @exception InsertRecException
	 *                error when insert in index page
	 * @exception KeyNotMatchException
	 *                key is neither integer key nor string key
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception IndexInsertRecException
	 *                error when insert in index page
	 * @exception FreePageException
	 *                error in BT page constructor
	 * @exception RecordNotFoundException
	 *                error delete a record in a BT page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception IndexFullDeleteException
	 *                fill delete error
	 * @exception LeafDeleteException
	 *                delete error in leaf page
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error in search in index pages
	 * @exception IOException
	 *                error from the lower layer
	 *
	 */
	public boolean Delete(KeyClass key, RID rid)
			throws DeleteFashionException, LeafRedistributeException, RedistributeException, InsertRecException,
			KeyNotMatchException, UnpinPageException, IndexInsertRecException, FreePageException,
			RecordNotFoundException, PinPageException, IndexFullDeleteException, LeafDeleteException, IteratorException,
			ConstructPageException, DeleteRecException, IndexSearchException, IOException {
		if (headerPage.get_deleteFashion() == DeleteFashion.FULL_DELETE)
			return FullDelete(key, rid);
		else if (headerPage.get_deleteFashion() == DeleteFashion.NAIVE_DELETE)
			return NaiveDelete(key, rid);
		else
			throw new DeleteFashionException(null, "");
	}

	/*
	 * findRunStart. Status BTreeFile::findRunStart (const void lo_key, RID
	 * *pstartrid)
	 *
	 * find left-most occurrence of `lo_key', going all the way left if lo_key
	 * is null.
	 * 
	 * Starting record returned in *pstartrid, on page *pppage, which is pinned.
	 *
	 * Since we allow duplicates, this must "go left" as described in the text
	 * (for the search algorithm).
	 * 
	 * @param lo_key find left-most occurrence of `lo_key', going all the way
	 * left if lo_key is null.
	 * 
	 * @param startrid it will reurn the first rid =< lo_key
	 * 
	 * @return return a BTLeafPage instance which is pinned. null if no key was
	 * found.
	 */

	BTLeafPage findRunStart(KeyClass lo_key, RID startrid) throws IOException, IteratorException, KeyNotMatchException,
			ConstructPageException, PinPageException, UnpinPageException {
		BTLeafPage pageLeaf;
		BTIndexPage pageIndex;
		Page page;
		BTSortedPage sortPage;
		PageId pageno;
		PageId curpageno = null; // iterator
		PageId prevpageno;
		PageId nextpageno;
		RID curRid;
		KeyDataEntry curEntry;

		pageno = headerPage.get_rootId();

		if (pageno.pid == INVALID_PAGE) { // no pages in the BTREE
			pageLeaf = null; // should be handled by
			// startrid =INVALID_PAGEID ; // the caller
			return pageLeaf;
		}

		page = pinPage(pageno);
		sortPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + pageno + lineSep);
			trace.flush();
		}

		// ASSERTION
		// - pageno and sortPage is the root of the btree
		// - pageno and sortPage valid and pinned

		while (sortPage.getType() == NodeType.INDEX) {
			pageIndex = new BTIndexPage(page, headerPage.get_keyType());
			prevpageno = pageIndex.getPrevPage();
			curEntry = pageIndex.getFirst(startrid);
			while (curEntry != null && lo_key != null && BT.keyCompare(curEntry.key, lo_key) < 0) {

				prevpageno = ((IndexData) curEntry.data).getData();
				curEntry = pageIndex.getNext(startrid);
			}

			unpinPage(pageno);

			pageno = prevpageno;
			page = pinPage(pageno);
			sortPage = new BTSortedPage(page, headerPage.get_keyType());

			if (trace != null) {
				trace.writeBytes("VISIT node " + pageno + lineSep);
				trace.flush();
			}

		}

		pageLeaf = new BTLeafPage(page, headerPage.get_keyType());

		curEntry = pageLeaf.getFirst(startrid);
		while (curEntry == null) {
			// skip empty leaf pages off to left
			nextpageno = pageLeaf.getNextPage();
			unpinPage(pageno);
			if (nextpageno.pid == INVALID_PAGE) {
				// oops, no more records, so set this scan to indicate this.
				return null;
			}

			pageno = nextpageno;
			pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());
			curEntry = pageLeaf.getFirst(startrid);
		}

		// ASSERTIONS:
		// - curkey, curRid: contain the first record on the
		// current leaf page (curkey its key, cur
		// - pageLeaf, pageno valid and pinned

		if (lo_key == null) {
			return pageLeaf;
			// note that pageno/pageLeaf is still pinned;
			// scan will unpin it when done
		}

		while (BT.keyCompare(curEntry.key, lo_key) < 0) {
			curEntry = pageLeaf.getNext(startrid);
			while (curEntry == null) { // have to go right
				nextpageno = pageLeaf.getNextPage();
				unpinPage(pageno);

				if (nextpageno.pid == INVALID_PAGE) {
					return null;
				}

				pageno = nextpageno;
				pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());

				curEntry = pageLeaf.getFirst(startrid);
			}
		}

		return pageLeaf;
	}

	/*
	 * Status BTreeFile::NaiveDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 *
	 * We don't do merging or redistribution, but do allow duplicates.
	 *
	 * Page containing first occurrence of key `key' is found for us by
	 * findRunStart. We then iterate for (just a few) pages, if necesary, to
	 * find the one containing <key,rid>, which we then delete via
	 * BTLeafPage::delUserRid.
	 */

	private boolean NaiveDelete(KeyClass key, RID rid)
			throws LeafDeleteException, KeyNotMatchException, PinPageException, ConstructPageException, IOException,
			UnpinPageException, PinPageException, IndexSearchException, IteratorException {
		BTLeafPage leafPage;
		RID curRid = new RID(); // iterator
		KeyClass curkey;
		RID dummyRid;
		PageId nextpage;
		boolean deleted;
		KeyDataEntry entry;
		if (trace != null) {
			trace.writeBytes("DELETE " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
			trace.writeBytes("DO" + lineSep);
			trace.writeBytes("SEARCH" + lineSep);
			trace.flush();
		}

		leafPage = findRunStart(key, curRid); // find first page,rid of key
		if (leafPage == null)
			return false;

		entry = leafPage.getCurrent(curRid);

		while (true) {

			while (entry == null) { // have to go right
				nextpage = leafPage.getNextPage();
				unpinPage(leafPage.getCurPage());
				if (nextpage.pid == INVALID_PAGE) {
					return false;
				}

				leafPage = new BTLeafPage(pinPage(nextpage), headerPage.get_keyType());
				entry = leafPage.getFirst(new RID());
			}

			if (BT.keyCompare(key, entry.key) > 0)
				break;

			if (leafPage.delEntry(new KeyDataEntry(key, rid)) == true) {

				// successfully found <key, rid> on this page and deleted it.
				// unpin dirty page and return OK.
				unpinPage(leafPage.getCurPage(), true /* = DIRTY */);

				if (trace != null) {
					trace.writeBytes("TAKEFROM node " + leafPage.getCurPage() + lineSep);
					trace.writeBytes("DONE" + lineSep);
					trace.flush();
				}

				return true;
			}

			nextpage = leafPage.getNextPage();
			unpinPage(leafPage.getCurPage());

			leafPage = new BTLeafPage(pinPage(nextpage), headerPage.get_keyType());

			entry = leafPage.getFirst(curRid);
		}

		/*
		 * We reached a page with first key > `key', so return an error. We
		 * should have got true back from delUserRid above. Apparently the
		 * specified <key,rid> data entry does not exist.
		 */

		unpinPage(leafPage.getCurPage());
		return false;
	}

	/*
	 * Status BTreeFile::FullDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 *
	 * Most work done recursively by _Delete
	 *
	 * Special case: delete root if the tree is empty
	 *
	 * Page containing first occurrence of key `key' is found for us After the
	 * page containing first occurence of key 'key' is found, we iterate for
	 * (just a few) pages, if necesary, to find the one containing <key,rid>,
	 * which we then delete via BTLeafPage::delUserRid.
	 * 
	 * @return false if no such record; true if succees
	 */

	private boolean FullDelete(KeyClass key, RID rid)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, IOException,
			IteratorException, PinPageException, UnpinPageException, IteratorException {

		try {

			if (trace != null) {
				trace.writeBytes("DELETE " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
				trace.writeBytes("DO" + lineSep);
				trace.writeBytes("SEARCH" + lineSep);
				trace.flush();
			}

			//_Delete(key, rid, headerPage.get_rootId(), null);
			int i = -1;
			StringBuilder status = new StringBuilder("inprogress");
			while(!"done".equals(status.toString())){
				_Delete(key, rid, headerPage.get_rootId(), null,status);
				i++;
			}
			if(i<1){
				// key didn't even exist
				if (trace != null) {
					trace.writeBytes("DELETE " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
					trace.writeBytes("Not Found" + lineSep);
					trace.flush();
				}
				return false;
			}
			if (trace != null) {
				trace.writeBytes("FOUND " + i + " TIMES" + lineSep);
				trace.writeBytes("DONE" + lineSep);
				trace.flush();
			}

			return true;
		} catch (RecordNotFoundException e) {
			return false;
		}

	}

	private KeyClass _Delete(KeyClass key, RID rid, PageId currentPageId, PageId parentPageId)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, UnpinPageException,
			IteratorException, PinPageException, IOException {
		return _Delete(key, rid, currentPageId, parentPageId,new StringBuilder());
		
	}
	
	private KeyClass _Delete(KeyClass key, RID rid, PageId currentPageId, PageId parentPageId,StringBuilder status)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, UnpinPageException,
			IteratorException, PinPageException, IOException {
		// if the table is empty do nothing
		// TODO: we can throw some error 
		if ( trace!=null )
		{
		  trace.writeBytes("Reached node " + currentPageId +lineSep);
		  trace.flush();
		}
		if(currentPageId.pid==INVALID_PAGE){
			// change the status for while loop to end
			status.setLength(0);
			status.append("done");
			return null;
		}
		HFPage currentPage = new HFPage(pinPage(currentPageId));		
		KeyClass keyToBeRemovedFromParent = null;
		// check if we have reached the leaf
		if(currentPage.getType()==NodeType.LEAF){
			// we have reached the leaf node, delete the entry
			BTLeafPage leaf = new BTLeafPage(currentPage,headerPage.get_keyType());
			KeyClass firstKeyOfLeaf = leaf.getFirst(new RID()).key;
			if(leaf.delEntry(new KeyDataEntry(key, rid))){
				if ( trace!=null )
				{
				  trace.writeBytes("Deleted key " + key +" from Page "+ leaf.getCurPage()+lineSep);
				  trace.flush();
				}
				// we have successfully deleted the key
				// if current page is the root also, occupancy condition doesn't apply on it
				if(leaf.getCurPage().pid==headerPage.get_rootId().pid){
					// if the root page is empty, free it, table is empty, header can point to invalid page
					// just as it does when the btree is initialized
					if(leaf.numberOfRecords()==0){
						if ( trace!=null )
						{
						  trace.writeBytes("Tree is empty "  +lineSep);
						  trace.flush();
						}
						freePage(currentPageId);
						updateHeader(new PageId(INVALID_PAGE));
					}else{
						// it is root page, so we don't care abput the occupancy
						unpinPage(currentPageId,true);
					}
					// it is root so no entry is passed to parent
					return null;
				}
				// we have reached here, so it is not root
				// occupancy should be checked
				if(checkIfUnderflow(leaf)){
					// we need to redistribute or merge 
					// keyToBeRemovedFromParent will be null in case of redistribution
					// keyToBeRemovedFromParent will be the first child of right sibling in case if it is merged
					// keyToBeRemovedFromParent is to be removed from parent
					BTIndexPage parentPage = parentPageId==null?null:new BTIndexPage(pinPage(parentPageId),headerPage.get_keyType());
					if ( trace!=null )
					{
					  trace.writeBytes("Underflow at node " + leaf.getCurPage() +lineSep);
					  trace.flush();
					}
					keyToBeRemovedFromParent = handleUnderflowInLeaf(leaf, parentPage, key, rid);					
				}else{
					BTIndexPage parentPage = parentPageId==null?null:new BTIndexPage(pinPage(parentPageId),headerPage.get_keyType());
					try{						
						if(BT.keyCompare(key, firstKeyOfLeaf)==0 ){	
							parentPage.adjustKey(leaf.getFirst(new RID()).key, key);							
						}
					}catch(Exception ex){
						// TODO: put the unpin part in finally
						
					}
					unpinPage(parentPageId,true);
					unpinPage(currentPageId,true);					
				}
			}else{
				// find reason why it can't be deleted
				// maybe it didn't find the key
				// set the status to be done to stop the while loop
				status.setLength(0);
				status.append("done");
				unpinPage(currentPageId,true);
				
			}
			
		// if it is index, almost same conditions as leaf
		}else if(currentPage.getType()==NodeType.INDEX){
			BTIndexPage indexPage = new BTIndexPage(currentPage,headerPage.get_keyType());
			// get the pageid of the child which can point to the key
			// child can be either another index page or leaf page
			PageId childNodePageId = indexPage.getPageNoByKey(key);
			// unpin it for now, recursion call may use this page
			unpinPage(currentPageId);
			// go one level down to its child
			// keyToBeRemoved can be from leaf or child index 
			// keyToBeRemoved will not be null only if merge occurred in its child node
			KeyClass keyToBeRemoved = _Delete(key, rid, childNodePageId, currentPageId,status);
			// merging of child nodes
			// entry pointing to the right child of the key needs to be removed as the child itself is no more
			if(keyToBeRemoved!=null){
				indexPage = new BTIndexPage(pinPage(currentPageId),headerPage.get_keyType());
				KeyClass firstKeyOfIndex = indexPage.getFirst(new RID()).key;
				RID recordRemoved = indexPage.deleteKey(keyToBeRemoved);
				// if it is the root page
				// occupancy check doesn't apply on it
				if(indexPage.getCurPage().pid==headerPage.get_rootId().pid){
					// if it is root and it is empty, leaf page becomes the new header
					if ( trace!=null )
					{
					  trace.writeBytes("Changing root from  " + indexPage.getCurPage() + " to " +indexPage.getLeftLink()  +lineSep);
					  trace.flush();
					}
					if(indexPage.numberOfRecords()==0){						
						updateHeader(indexPage.getLeftLink());
						freePage(currentPageId);
					}else{
						unpinPage(currentPageId,true);
					}					
					return null;
				}
				// we have reached here so it is not the root page
				// occupancy check applies
				if(checkIfUnderflow(indexPage)){
					// if it is less than half filled
					BTIndexPage parentPage = parentPageId==null?null:new BTIndexPage(pinPage(parentPageId),headerPage.get_keyType());
					// redistribution or merging will occur
					// keyToBeRemovedFromParent is null if children did redistribution
					// keyToBeRemovedFromParent is not null if merging occurred
					keyToBeRemovedFromParent = handleUnderflowInIndex(indexPage, parentPage, keyToBeRemoved, rid);				
				}else{ 
					
					unpinPage(currentPageId,true);					
				}
				//unpinPage(currentPageId,true);
				
			}
		}
		// send the key to the parent, so that it can know if it needs to remove the key
		// sending null means we are done, and parent doesn't need to do anything
		// merging always send key
		return keyToBeRemovedFromParent;
	}
	// this is common for both index and leaf page
	private boolean checkIfUnderflow(BTSortedPage page)throws IOException{
		// empty space is available space + 4 bits from the slot that got empty due to the deletion of the key
		// keys are stored in slot directory, 4 bit for each slot
		int emptySpace = page.available_space()+4;
		int filledSpace = (MAX_SPACE - HFPage.DPFIXED) - emptySpace;
		// this is for 50% occupancy, else (MAX_SPACE - HFPage.DPFIXED)*fanout
		return (emptySpace>filledSpace);
	}
	
	// this handles underflow event in leaf, filled space is less than 50%
	private KeyClass handleUnderflowInLeaf(BTLeafPage leaf,BTIndexPage parentPage,KeyClass key,RID rid)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, UnpinPageException,
			IteratorException, PinPageException, IOException {
		KeyClass keyToBeRemovedFromParent = null;
		PageId siblingPageId = new PageId();
		// get whether the sibling is on the right side or left side
		// it will return left sibling, unless the current leaf is the leftmost
		int direction = parentPage.getSibling(key, siblingPageId);
		
		// there is no sibling
		if(direction == 0){
			// nothing can be done			
			unpinPage(leaf.getCurPage());
			unpinPage(parentPage.getCurPage());
			return null;
		}
		// if we have a sibling 
		BTLeafPage sibling = new BTLeafPage(pinPage(siblingPageId),headerPage.get_keyType());
		// first try to redistribute
		if(sibling.redistribute(leaf, parentPage, direction, key)){
			// if redistribution is successful;
			// we are done, nothing else to be done
			if ( trace!=null ){			      
			      trace_children(leaf.getCurPage());
			      trace_children(sibling.getCurPage());			      
			}
			unpinPage(siblingPageId,true);
			unpinPage(leaf.getCurPage(),true);
			unpinPage(parentPage.getCurPage(),true);
			return null;
		}
		// unpin the page to avoid confusion, if i try to free this page and it is pinned somewhere else
		// it will throw error, so unpin it as soon as the work is done
		unpinPage(parentPage.getCurPage());
		// redistribution failed, so check out for the reasons
		int availableSpace = sibling.available_space()+leaf.available_space()+8;
		int spaceRequired =MAX_SPACE- HFPage.DPFIXED;
		// the sum of both page's filled space is less than a whole page size
		if(availableSpace>spaceRequired){
			// we can merge them in a single node
			BTLeafPage leftLeaf,rightLeaf;
			// check if sibling was right or left sibling
			if(direction==-1){
				leftLeaf = sibling;
				rightLeaf = leaf;
			}else{
				rightLeaf = sibling;
				leftLeaf = leaf;
			}
			keyToBeRemovedFromParent = mergeLeaf(leftLeaf, rightLeaf);
			if ( trace!=null ){
				trace.writeBytes("Merged from node "+rightLeaf.getCurPage()+ " into " + leftLeaf.getCurPage()+lineSep);
				trace.flush();
		    }
			if ( trace!=null ){			      
			      trace_children(leftLeaf.getCurPage());			      
			}
		}else{
			// cannot be redistributed or merged
			// Todo: find out if there can be any other reason, anyhow nothing can be done
			// unpin pages and return null
			unpinPage(siblingPageId,false);
			unpinPage(leaf.getCurPage());
			return null;
		}		
		return keyToBeRemovedFromParent;
	}
	
	private KeyClass mergeLeaf(BTLeafPage leftLeaf,BTLeafPage rightLeaf)
	throws IteratorException,DeleteRecException,InsertRecException,
	IOException,ConstructPageException,PinPageException,UnpinPageException,
	FreePageException{
		RID rid = new RID();
		KeyClass keyAtParentNode = rightLeaf.getFirst(rid).key;
		// first entry of the right child is what parent will be holding
		// we will need to remove this key from parent, as we are removing the child, so the pointer
		// is of no use
		KeyDataEntry firstEntryOfRightLeaf = rightLeaf.getFirst(rid);
		// move all from right leaf to left leaf
		while(firstEntryOfRightLeaf!=null){
			leftLeaf.insertRecord(firstEntryOfRightLeaf);
			rightLeaf.deleteSortedRecord(rid);
			firstEntryOfRightLeaf = rightLeaf.getFirst(rid);
		}
		// correct the linking, as there will be no right leaf now
		leftLeaf.setNextPage(rightLeaf.getNextPage());
		// if the right leaf was the right most then nothing needs to be done
		if(rightLeaf.getNextPage().pid!=INVALID_PAGE){
			// if it wasn't rightmost, correct the linking, as it is doubly linked
			BTLeafPage newRightSibling = new BTLeafPage(pinPage(rightLeaf.getNextPage()),headerPage.get_keyType());
			newRightSibling.setPrevPage(leftLeaf.getCurPage());
			unpinPage(newRightSibling.getCurPage(),true);
		}
		// now the right sibling is empty so we can remove it
		freePage(rightLeaf.getCurPage());
		unpinPage(leftLeaf.getCurPage(),true);
		return keyAtParentNode;
	}
	
	private KeyClass handleUnderflowInIndex(BTIndexPage indexPage,BTIndexPage parentPage,KeyClass key,RID rid)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, UnpinPageException,
			IteratorException, PinPageException, IOException {
		KeyClass keyToBeRemovedFromParent = null;
		PageId siblingPageId = new PageId();
		// get whether the sibling is on the right side or left side
		// it will return left sibling, unless the current leaf is the leftmost
		int direction = parentPage.getSibling(key, siblingPageId);
		// if there is no sibling
		if(direction == 0){
			// nothing can be done
			unpinPage(indexPage.getCurPage());
			unpinPage(parentPage.getCurPage());
			return null;
		}
		// if we have a sibling 
		BTIndexPage sibling = new BTIndexPage(pinPage(siblingPageId),headerPage.get_keyType());
		// first try to redistribute
		if(sibling.redistribute(indexPage, parentPage, direction, key)){
			// if successfully redistributed, we are done
			if ( trace!=null ){			      
			      trace_children(indexPage.getCurPage());
			      trace_children(sibling.getCurPage());			      
			}
			unpinPage(siblingPageId,true);
			unpinPage(indexPage.getCurPage(),true);
			unpinPage(parentPage.getCurPage(),true);
			return null;
		}
		// if we have reached here, it means redistribution failed
		// so lets try if we can merge
		BTIndexPage leftIndex,rightIndex;
		if(direction==-1){
			leftIndex = sibling;
			rightIndex = indexPage;
		}else{
			rightIndex = sibling;
			leftIndex = indexPage;
		}
		// available space is the sum of their free space plus 4 byte of the slot that was deleted
		// due to deletion of key, and 4 byte that will be not be required to keep the count in slot directory
		int availableSpace = sibling.available_space()+indexPage.available_space()+8;
		int spaceRequired =MAX_SPACE- HFPage.DPFIXED + BT.getKeyLength(parentPage.findKey(rightIndex.getFirst(new RID()).key))+8 ;
		// if space required by both is less than the space in full page
		if(availableSpace>spaceRequired){		
			// we can merge both the index
			keyToBeRemovedFromParent = mergeIndex(leftIndex, rightIndex,parentPage);
			if ( trace!=null ){
				trace.writeBytes("Merged from node "+rightIndex.getCurPage()+ " into " + leftIndex.getCurPage()+lineSep);
				trace.flush();
		    }
			if ( trace!=null ){			      
			      trace_children(leftIndex.getCurPage());			      
			}
		}else{
			// cannot be redistributed or merged
			unpinPage(siblingPageId,false);
			unpinPage(indexPage.getCurPage());
			unpinPage(parentPage.getCurPage());
			return null;
		}		
		return keyToBeRemovedFromParent;
	}
	
	private KeyClass mergeIndex(BTIndexPage leftIndex,BTIndexPage rightIndex,BTIndexPage parentPage)
	throws IteratorException,DeleteRecException,InsertRecException,
	IOException,ConstructPageException,PinPageException,UnpinPageException,
	FreePageException,IndexInsertRecException,IndexSearchException{
		RID rid = new RID();
		KeyClass keyAtParentNode = rightIndex.getFirst(rid).key;
		// key at parent node is not same as right index first key
		// so take the first key from right child, search parent for the one that is 
		// just less than it, and pull it down
		keyAtParentNode = parentPage.findKey(keyAtParentNode);
		unpinPage(parentPage.getCurPage());
		leftIndex.insertKey(keyAtParentNode, rightIndex.getLeftLink());
		// move all from right leaf to left leaf
		KeyDataEntry firstEntryOfRightIndex = rightIndex.getFirst(rid);
		while(firstEntryOfRightIndex!=null){
			leftIndex.insertRecord(firstEntryOfRightIndex);
			rightIndex.deleteSortedRecord(rid);
			firstEntryOfRightIndex = rightIndex.getFirst(rid);
		}
		// index don't have linking
//		leftIndex.setNextPage(rightIndex.getNextPage());
//		if(rightIndex.getNextPage().pid!=INVALID_PAGE){
//			BTLeafPage newRightSibling = new BTLeafPage(pinPage(rightIndex.getNextPage()),headerPage.get_keyType());
//			newRightSibling.setPrevPage(leftIndex.getCurPage());
//			unpinPage(newRightSibling.getCurPage(),true);
//		}
		freePage(rightIndex.getCurPage());
		unpinPage(leftIndex.getCurPage());
		return keyAtParentNode;
	}

	/**
	 * create a scan with given keys Cases: (1) lo_key = null, hi_key = null
	 * scan the whole index (2) lo_key = null, hi_key!= null range scan from min
	 * to the hi_key (3) lo_key!= null, hi_key = null range scan from the lo_key
	 * to max (4) lo_key!= null, hi_key!= null, lo_key = hi_key exact match (
	 * might not unique) (5) lo_key!= null, hi_key!= null, lo_key < hi_key range
	 * scan from lo_key to hi_key
	 * 
	 * @param lo_key
	 *            the key where we begin scanning. Input parameter.
	 * @param hi_key
	 *            the key where we stop scanning. Input parameter.
	 * @exception IOException
	 *                error from the lower layer
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception UnpinPageException
	 *                error when unpin a page
	 */
	public BTFileScan new_scan(KeyClass lo_key, KeyClass hi_key) throws IOException, KeyNotMatchException,
			IteratorException, ConstructPageException, PinPageException, UnpinPageException

	{
		BTFileScan scan = new BTFileScan();
		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			scan.leafPage = null;
			return scan;
		}

		scan.treeFilename = dbname;
		scan.endkey = hi_key;
		scan.didfirst = false;
		scan.deletedcurrent = false;
		scan.curRid = new RID();
		scan.keyType = headerPage.get_keyType();
		scan.maxKeysize = headerPage.get_maxKeySize();
		scan.bfile = this;

		// this sets up scan at the starting position, ready for iteration
		scan.leafPage = findRunStart(lo_key, scan.curRid);
		return scan;
	}

	void trace_children(PageId id)
			throws IOException, IteratorException, ConstructPageException, PinPageException, UnpinPageException {

		if (trace != null) {

			BTSortedPage sortedPage;
			RID metaRid = new RID();
			PageId childPageId;
			KeyClass key;
			KeyDataEntry entry;
			sortedPage = new BTSortedPage(pinPage(id), headerPage.get_keyType());

			// Now print all the child nodes of the page.
			if (sortedPage.getType() == NodeType.INDEX) {
				BTIndexPage indexPage = new BTIndexPage(sortedPage, headerPage.get_keyType());
				trace.writeBytes("INDEX CHILDREN " + id + " nodes" + lineSep);
				trace.writeBytes(" " + indexPage.getPrevPage());
				for (entry = indexPage.getFirst(metaRid); entry != null; entry = indexPage.getNext(metaRid)) {
					trace.writeBytes("   " + ((IndexData) entry.data).getData());
				}
			} else if (sortedPage.getType() == NodeType.LEAF) {
				BTLeafPage leafPage = new BTLeafPage(sortedPage, headerPage.get_keyType());
				trace.writeBytes("LEAF CHILDREN " + id + " nodes" + lineSep);
				for (entry = leafPage.getFirst(metaRid); entry != null; entry = leafPage.getNext(metaRid)) {
					trace.writeBytes("   " + entry.key + " " + entry.data);
				}
			}
			unpinPage(id);
			trace.writeBytes(lineSep);
			trace.flush();
		}

	}

}
