package com.minisql.engine.planner;

import com.minisql.engine.binder.BoundStatement;
import com.minisql.storage.Catalog;
import com.minisql.storage.TableMetadata;
import com.minisql.types.IntegerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerTest {

    private final Planner planner = new Planner();

    private TableMetadata table() {
        return new TableMetadata(0, "users", "users.dat", List.of(
            new com.minisql.storage.ColumnMetadata("id", IntegerType.INSTANCE, 0)));
    }

    @Test
    void selectWithoutClausesSkipsFilterSortLimit() {
        BoundStatement.Select bound = new BoundStatement.Select(
            table(), List.of(), null, List.of(), -1, -1);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Project.class);
        PlanNode.Project project = (PlanNode.Project) node;
        assertThat(project.child()).isInstanceOf(PlanNode.TableScan.class);
    }

    @Test
    void selectWithWhereOrderByLimitProducesFilterProjectSortLimitNesting() {
        BoundStatement.Select bound = new BoundStatement.Select(
            table(),
            List.of(),
            com.minisql.engine.binder.BoundExpression.columnRef("id", IntegerType.INSTANCE, 0),
            List.of(new com.minisql.engine.parser.ast.Statement.OrderBy("id")),
            5, 0);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Limit.class);
        PlanNode.Limit limit = (PlanNode.Limit) node;
        assertThat(limit.limit()).isEqualTo(5);

        assertThat(limit.child()).isInstanceOf(PlanNode.Sort.class);
        PlanNode.Sort sort = (PlanNode.Sort) limit.child();

        assertThat(sort.child()).isInstanceOf(PlanNode.Project.class);
        PlanNode.Project project = (PlanNode.Project) sort.child();

        assertThat(project.child()).isInstanceOf(PlanNode.Filter.class);
        PlanNode.Filter filter = (PlanNode.Filter) project.child();

        assertThat(filter.child()).isInstanceOf(PlanNode.TableScan.class);
    }

    @Test
    void selectWithOnlyWhereProducesFilterThenProject() {
        BoundStatement.Select bound = new BoundStatement.Select(
            table(),
            List.of(),
            com.minisql.engine.binder.BoundExpression.columnRef("id", IntegerType.INSTANCE, 0),
            List.of(), -1, -1);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Project.class);
        assertThat(((PlanNode.Project) node).child()).isInstanceOf(PlanNode.Filter.class);
    }

    @Test
    void insertProducesInsertNode() {
        BoundStatement.Insert bound = new BoundStatement.Insert(
            table(), List.of("id"), List.of(List.of(1)));

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Insert.class);
        assertThat(((PlanNode.Insert) node).rows()).containsExactly(List.of(1));
    }

    @Test
    void updateProducesUpdateNode() {
        BoundStatement.Update bound = new BoundStatement.Update(table(), List.of(), null);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Update.class);
    }

    @Test
    void deleteProducesDeleteNode() {
        BoundStatement.Delete bound = new BoundStatement.Delete(table(), null);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.Delete.class);
    }

    @Test
    void createTableProducesCreateTableNode() {
        BoundStatement.CreateTable bound = new BoundStatement.CreateTable("orders", List.of());

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.CreateTable.class);
        assertThat(((PlanNode.CreateTable) node).tableName()).isEqualTo("orders");
    }

    @Test
    void dropTableProducesDropTableNode() {
        BoundStatement.DropTable bound = new BoundStatement.DropTable("orders", true);

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.DropTable.class);
        assertThat(((PlanNode.DropTable) node).ifExists()).isTrue();
    }

    @Test
    void showTablesProducesShowTablesNode() {
        PlanNode node = planner.plan(new BoundStatement.ShowTables());
        assertThat(node).isInstanceOf(PlanNode.ShowTables.class);
    }

    @Test
    void describeProducesDescribeTableNode() {
        BoundStatement.Describe bound = new BoundStatement.Describe(table());

        PlanNode node = planner.plan(bound);

        assertThat(node).isInstanceOf(PlanNode.DescribeTable.class);
        assertThat(((PlanNode.DescribeTable) node).table()).isEqualTo(table());
    }
}
