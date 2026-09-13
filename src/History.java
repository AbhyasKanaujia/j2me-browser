import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import java.util.Vector;

// App shell: visited-page history via RecordStore. One record per visit,
// oldest evicted once MAX_ENTRIES is exceeded. See
// docs/adr/0006-one-file-per-concern.md.
final class History {
    private History() {
    }

    private static final String STORE_NAME = "history";
    private static final int MAX_ENTRIES = 50;
    private static final char SEPARATOR = '\n';

    static final class Entry {
        final String title;
        final String url;

        Entry(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    // title may be empty -- falls back to showing the URL as the label.
    static void record(String url, String title) {
        String label = (title == null || title.length() == 0) ? url : title;
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            byte[] data = toBytes(label + SEPARATOR + url);
            store.addRecord(data, 0, data.length);
            evictOldest(store);
        } catch (RecordStoreException e) {
            // Best-effort: browsing still works even if history can't be recorded.
        } finally {
            closeQuietly(store);
        }
    }

    // Most recently visited first. Record IDs increase monotonically as
    // records are added, so "most recent" = "highest ID" -- sorted explicitly
    // rather than trusting enumerateRecords' default ordering, which the MIDP
    // spec leaves undefined when no RecordComparator is given (confirmed to
    // differ from the naively-assumed insertion order on at least one real
    // implementation).
    static Vector list() {
        Vector entries = new Vector();
        Vector ids = new Vector();
        RecordStore store = null;
        try {
            store = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration recordEnum = store.enumerateRecords(null, null, false);
            while (recordEnum.hasNextElement()) {
                int id = recordEnum.nextRecordId();
                Entry entry = toEntry(fromBytes(store.getRecord(id)));
                if (entry != null) {
                    insertByIdDescending(ids, entries, id, entry);
                }
            }
            recordEnum.destroy();
        } catch (RecordStoreException e) {
            // Best-effort: an empty list is a safe fallback.
        } finally {
            closeQuietly(store);
        }
        return entries;
    }

    private static void insertByIdDescending(Vector ids, Vector entries, int id, Entry entry) {
        int i = 0;
        while (i < ids.size() && ((Integer) ids.elementAt(i)).intValue() > id) {
            i++;
        }
        ids.insertElementAt(new Integer(id), i);
        entries.insertElementAt(entry, i);
    }

    private static Entry toEntry(String stored) {
        int separator = stored.indexOf(SEPARATOR);
        if (separator < 0) {
            return null;
        }
        return new Entry(stored.substring(0, separator), stored.substring(separator + 1));
    }

    // Deletes the lowest record ID present, i.e. the actual oldest record --
    // same reasoning as list() above, not trusting enumeration order.
    private static void evictOldest(RecordStore store) throws RecordStoreException {
        while (store.getNumRecords() > MAX_ENTRIES) {
            int oldestId = -1;
            RecordEnumeration recordEnum = store.enumerateRecords(null, null, false);
            while (recordEnum.hasNextElement()) {
                int id = recordEnum.nextRecordId();
                if (oldestId < 0 || id < oldestId) {
                    oldestId = id;
                }
            }
            recordEnum.destroy();
            if (oldestId < 0) {
                break;
            }
            store.deleteRecord(oldestId);
        }
    }

    private static byte[] toBytes(String url) {
        try {
            return url.getBytes("UTF-8");
        } catch (Exception e) {
            return url.getBytes();
        }
    }

    private static String fromBytes(byte[] data) {
        try {
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return new String(data);
        }
    }

    private static void closeQuietly(RecordStore store) {
        if (store != null) {
            try {
                store.closeRecordStore();
            } catch (RecordStoreException ignored) {
            }
        }
    }
}
