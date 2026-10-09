package com.hify.common.handler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

/**
 * Maps {@code float[]} onto a pgvector {@code vector} column.
 *
 * <p>JDBC has no vector type — the PostgreSQL driver surfaces these columns as
 * unknown (OTHER) values — but pgvector accepts and emits the textual literal
 * {@code [0.1,0.2,0.3]} in SQL. This handler is therefore a thin translation
 * layer between {@code float[]} and that literal, and needs no extra driver.
 *
 * <p>On an entity, pair it with {@code @TableName(autoResultMap = true)}:
 * {@code @TableField(typeHandler = ...)} alone makes inserts work while selects
 * silently return null — the same trap as JacksonTypeHandler.
 *
 * <p>Similarity search cannot be expressed through MyBatis-Plus wrappers. Write
 * the SQL by hand and pass the query vector through {@link #toLiteral(float[])}
 * with an explicit cast: {@code ORDER BY embedding <=> #{q}::vector}. Keep the
 * bare operator form — wrapping it in an expression disables the HNSW index.
 */
@MappedTypes(float[].class)
@MappedJdbcTypes(JdbcType.OTHER)
public class VectorTypeHandler extends BaseTypeHandler<float[]> {

  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter,
      JdbcType jdbcType) throws SQLException {
    // Types.OTHER marks this as a custom type to be inferred from context;
    // without it the value is sent as varchar and PG rejects it for a vector column.
    ps.setObject(i, toLiteral(parameter), Types.OTHER);
  }

  @Override
  public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return parse(rs.getString(columnName));
  }

  @Override
  public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return parse(rs.getString(columnIndex));
  }

  @Override
  public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    return parse(cs.getString(columnIndex));
  }

  /** {@code float[]} to the literal pgvector expects: {@code [0.1,0.2,0.3]}. */
  public static String toLiteral(float[] vector) {
    if (vector == null) {
      return null;
    }
    StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
    for (int i = 0; i < vector.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(vector[i]);
    }
    return sb.append(']').toString();
  }

  /** Parses {@code [0.1,0.2,0.3]} back into a {@code float[]}. */
  public static float[] parse(String literal) {
    if (literal == null || literal.isEmpty()) {
      return null;
    }
    if (literal.charAt(0) != '[' || literal.charAt(literal.length() - 1) != ']') {
      throw new IllegalArgumentException("Not a pgvector literal: " + literal);
    }
    String[] parts = literal.substring(1, literal.length() - 1).split(",");
    float[] vector = new float[parts.length];
    for (int i = 0; i < parts.length; i++) {
      vector[i] = Float.parseFloat(parts[i].trim());
    }
    return vector;
  }
}
