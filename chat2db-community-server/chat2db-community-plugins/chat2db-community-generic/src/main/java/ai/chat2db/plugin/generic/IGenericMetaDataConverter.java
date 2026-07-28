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
        // CREATE_PARAMS tells us which clauses the type actually accepts (e.g. "length",
        // "precision,scale"); PRECISION alone is not proof — INTEGER reports precision
        // but rejects a length clause on most engines. Fall back to precision only when
        // the driver does not report CREATE_PARAMS and the type is character-like.
        String params = type.getCreateParams() == null ? "" : type.getCreateParams().toLowerCase();
        boolean lengthLike = params.contains("length") || params.contains("precision");
        boolean scaleLike = params.contains("scale") || params.contains(",");
        if (params.isEmpty()) {
            String name = type.getTypeName() == null ? "" : type.getTypeName().toUpperCase();
            lengthLike = name.contains("CHAR") && type.getPrecision() != null && type.getPrecision() > 0;
        }
        columnType.setSupportLength(lengthLike);
        columnType.setSupportScale(scaleLike);
        columnType.setSupportNullable(type.getNullable() != null
                && type.getNullable() != DatabaseMetaData.typeNoNulls);
        // DEFAULT is standard SQL for SQL-like engines served by this generic fallback;
        // drivers that lack it can still be overridden via explicit columnTypes config.
        columnType.setSupportDefaultValue(true);
        columnType.setSupportAutoIncrement(Boolean.TRUE.equals(type.getAutoIncrement()));
    }
}
