package ai.chat2db.plugin.gbase8s;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GBase8sMetaDataTest {

    private final GBase8sMetaData metaData = new GBase8sMetaData();

    @Test
    void databaseAndTableAreColonSeparated() {
        // GBase 8s (Informix lineage) parses 'mydb.t' as owner.table in the current database,
        // so a database-qualified reference must use the Informix-style colon separator.
        assertEquals("mydb:t", metaData.getMetaDataName("mydb", "", "t"));
        assertEquals("mydb:t", metaData.getMetaDataName("mydb", null, "t"));
    }

    @Test
    void databaseOwnerAndTableUseColonThenDot() {
        assertEquals("mydb:gbasedbt.t", metaData.getMetaDataName("mydb", "gbasedbt", "t"));
    }

    @Test
    void singleNameIsReturnedBare() {
        assertEquals("t", metaData.getMetaDataName("t"));
        assertEquals("t", metaData.getMetaDataName("", "", "t"));
        assertEquals("t", metaData.getMetaDataName(null, null, "t"));
        assertEquals("", metaData.getMetaDataName("", null, ""));
    }
}
