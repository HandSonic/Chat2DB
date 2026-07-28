package ai.chat2db.community.storage.small;

import ai.chat2db.community.domain.api.enums.NodeTypeEnum;
import ai.chat2db.community.domain.api.model.db.TreeNode;
import ai.chat2db.community.domain.api.model.workspace.Node;
import ai.chat2db.community.storage.TestHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for core:storage-4 (duplicate node on drag to root)
 * and core:storage-6 (interrupt flag swallowed).
 */
class TreeNodeStorageTest {

    private TreeNodeStorage storage;

    @BeforeAll
    static void useTempHome() {
        TestHome.init();
    }

    @BeforeEach
    void setUp() {
        storage = new TreeNodeStorage();
        for (TreeNode treeNode : new ArrayList<>(storage.getDataList())) {
            storage.delete(treeNode.getId());
        }
    }

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void dropToNullRootDoesNotDuplicateDraggedNode() {
        Node nodeA = dataSourceNode(1L);
        Node nodeB = dataSourceNode(2L);
        storage.createTree(new ArrayList<>(List.of(nodeA, nodeB)));

        storage.updatePosition(null, dataSourceNode(2L), null);

        List<Node> nodes = storage.getNodes();
        assertEquals(2, nodes.size());
        long occurrencesOfDragged = nodes.stream()
                .filter(node -> Long.valueOf(2L).equals(node.getId())
                        && NodeTypeEnum.DATA_SOURCE.name().equals(node.getType()))
                .count();
        assertEquals(1, occurrencesOfDragged);
    }

    @Test
    void interruptStatusIsRestoredAfterUpdatePosition() {
        Thread.currentThread().interrupt();
        storage.updatePosition(null, dataSourceNode(99L), null);
        assertTrue(Thread.currentThread().isInterrupted(),
                "interrupt flag must be restored after InterruptedException");
    }

    private static Node dataSourceNode(Long id) {
        return Node.builder().id(id).type(NodeTypeEnum.DATA_SOURCE.name()).build();
    }
}
