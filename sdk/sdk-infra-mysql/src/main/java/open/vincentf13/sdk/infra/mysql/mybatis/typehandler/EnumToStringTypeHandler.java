package open.vincentf13.sdk.infra.mysql.mybatis.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/*
  Global enum handler that persists enums as their {@code name()} string.
 */
@MappedTypes(Enum.class)
public class EnumToStringTypeHandler<E extends Enum<E>> extends BaseTypeHandler<E> {

    private final Class<E> type;

    public EnumToStringTypeHandler(Class<E> type) {
        if (type == null) {
            throw new IllegalArgumentException("Enum type argument cannot be null");
        }
        this.type = type;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, E parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public E getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toEnum(rs.getString(columnName));
    }

    @Override
    public E getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toEnum(rs.getString(columnIndex));
    }

    @Override
    public E getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toEnum(cs.getString(columnIndex));
    }

    private E toEnum(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Enum.valueOf(this.type, value);
    }
}
