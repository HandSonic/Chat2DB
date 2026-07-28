package ai.chat2db.plugin.generic;

import ai.chat2db.community.domain.api.model.metadata.ColumnType;
import ai.chat2db.community.domain.api.model.metadata.Type;
import java.sql.DatabaseMetaData;
import java.util.List;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

@Mapper
public interface IGenericMetaDataConverter {

    IGenericMetaDataConverter INSTANCE = Mappers.getMapper(IGenericMetaDataConverter.class);

    ColumnType type2columnType(Type type);

    List<ColumnType> type2columnType(List<Type> types);

    /**
     * Derive the supportXxx flags from JDBC {@code getTypeInfo} metadata. Without this hook MapStruct
     * only maps typeName and every flag stays false, so the table editor generates DDL without
     * NULL/DEFAULT/length clauses for databases that rely on this fallback (e.g. TDENGINE,
     * ELASTICSEARCH).
     */
    @AfterMapping
    default void fillSupportFlags(Type type, @MappingTarget ColumnType columnType) {
        if (type == null || columnType == null) {
            return;
        }
        columnType.setSupportLength(type.getPrecision() != null && type.getPrecision() > 0);
        columnType.setSupportScale(type.getMaximumScale() != null && type.getMaximumScale() > 0);
        columnType.setSupportNullable(type.getNullable() != null
                && type.getNullable() != DatabaseMetaData.typeNoNulls);
        columnType.setSupportAutoIncrement(Boolean.TRUE.equals(type.getAutoIncrement()));
    }
}
