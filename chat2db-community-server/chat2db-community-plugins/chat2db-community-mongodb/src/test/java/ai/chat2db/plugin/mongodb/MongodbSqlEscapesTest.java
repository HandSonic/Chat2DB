package ai.chat2db.plugin.mongodb;

import java.util.List;

import ai.chat2db.community.domain.api.model.result.Header;
import ai.chat2db.community.domain.api.model.result.QueryResponse;
import ai.chat2db.community.domain.api.model.result.ResultOperation;
import ai.chat2db.spi.constant.SQLConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MongodbSqlEscapesTest {

    @Test
    void requireMongoNameAcceptsLegitimateNames() {
        assertEquals("mydb", MongodbSqlEscapes.requireMongoName("mydb", "database name"));
        assertEquals("my_db-1$A", MongodbSqlEscapes.requireMongoName("my_db-1$A", "collection name"));
    }

    @Test
    void requireMongoNameRejectsInjection() {
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName("a.b", "collection name"));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName("a b", "collection name"));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName("x; db.dropDatabase(); //", "database name"));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName("x\")}", "collection name"));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName("", "collection name"));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlEscapes.requireMongoName(null, "collection name"));
    }

    @Test
    void escapeJsonStringEscapesQuotesBackslashAndControls() {
        assertEquals("plain", MongodbSqlEscapes.escapeJsonString("plain"));
        assertEquals("a\\\"b", MongodbSqlEscapes.escapeJsonString("a\"b"));
        assertEquals("a\\\\b", MongodbSqlEscapes.escapeJsonString("a\\b"));
        assertEquals("a\\nb", MongodbSqlEscapes.escapeJsonString("a\nb"));
        assertEquals("\\u0001", MongodbSqlEscapes.escapeJsonString("\u0001"));
        assertNull(MongodbSqlEscapes.escapeJsonString(null));
    }

    @Test
    void dropTableRejectsMaliciousName() {
        MongodbDBManager manager = new MongodbDBManager();
        assertEquals(" db. users.drop();", manager.dropTable(null, null, null, "users"));
        assertThrows(IllegalArgumentException.class,
            () -> manager.dropTable(null, null, null, "users; db.dropDatabase(); //"));
    }

    @Test
    void truncateTableRejectsMaliciousName() throws Exception {
        MongodbDBManager manager = new MongodbDBManager();
        assertEquals("db.users.deleteMany({})", manager.truncateTable(null, null, null, "users"));
        assertThrows(IllegalArgumentException.class,
            () -> manager.truncateTable(null, null, null, "a.b"));
    }

    @Test
    void deleteCommandEscapesObjectIdAndValidatesTable() {
        QueryResponse response = new QueryResponse();
        response.setTableName("users");
        response.setHeaderList(List.of(
            Header.builder().name("rn").build(),
            Header.builder().name("_id").build()));
        ResultOperation operation = new ResultOperation();
        operation.setType(SQLConstants.DELETE_KEYWORD);
        operation.setOldDataList(List.of("1", "abc123"));
        response.setOperations(List.of(operation));
        assertEquals("db.users.deleteOne({_id: ObjectId(\"abc123\")})",
            MongodbSqlBuilder.getInstance().buildByQueryResult(response));

        operation.setOldDataList(List.of("1", "a\"), $where: 1, x: (\""));
        String sql = MongodbSqlBuilder.getInstance().buildByQueryResult(response);
        assertEquals("db.users.deleteOne({_id: ObjectId(\"a\\\"), $where: 1, x: (\\\"\")})", sql);

        response.setTableName("users; //");
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlBuilder.getInstance().buildByQueryResult(response));
    }

    @Test
    void insertCommandEscapesValuesAndValidatesNames() {
        QueryResponse response = new QueryResponse();
        response.setTableName("users");
        response.setHeaderList(List.of(
            Header.builder().name("rn").build(),
            Header.builder().name("_id").build(),
            Header.builder().name("name").build()));
        ResultOperation operation = new ResultOperation();
        operation.setType(SQLConstants.CREATE_KEYWORD);
        operation.setDataList(List.of("1", "id1", "a\"}), db.dropDatabase(), //"));
        response.setOperations(List.of(operation));
        String sql = MongodbSqlBuilder.getInstance().buildByQueryResult(response);
        assertEquals("db.users.insertOne({name:\"a\\\"}), db.dropDatabase(), //\"})", sql);

        // malicious field name is rejected
        response.setHeaderList(List.of(
            Header.builder().name("rn").build(),
            Header.builder().name("_id").build(),
            Header.builder().name("a}), x:(").build()));
        assertThrows(IllegalArgumentException.class,
            () -> MongodbSqlBuilder.getInstance().buildByQueryResult(response));
    }

    @Test
    void updateCommandEscapesValuesAndId() {
        QueryResponse response = new QueryResponse();
        response.setTableName("users");
        response.setHeaderList(List.of(
            Header.builder().name("rn").build(),
            Header.builder().name("_id").build(),
            Header.builder().name("name").build()));
        ResultOperation operation = new ResultOperation();
        operation.setType(SQLConstants.UPDATE_KEYWORD);
        operation.setOldDataList(List.of("1", "id\"1", "old"));
        operation.setDataList(List.of("1", "id\"1", "new\"value"));
        response.setOperations(List.of(operation));
        String sql = MongodbSqlBuilder.getInstance().buildByQueryResult(response);
        assertEquals("db.users.updateOne({_id:ObjectId(\"id\\\"1\")},{$set:{name:\"new\\\"value\"}})", sql);
    }
}
