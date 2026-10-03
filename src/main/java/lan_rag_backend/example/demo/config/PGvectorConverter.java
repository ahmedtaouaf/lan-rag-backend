package lan_rag_backend.example.demo.config;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.SQLException;

@Converter(autoApply = true)
public class PGvectorConverter implements AttributeConverter<PGvector, Object> {

    @Override
    public Object convertToDatabaseColumn(PGvector attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getValue(); // Renvoie la représentation textuelle '[0.1, 0.2, ...]'
    }

    @Override
    public PGvector convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return new PGvector(dbData.toString());
        } catch (SQLException e) {
            throw new IllegalArgumentException("Impossible de convertir la valeur SQL en PGvector", e);
        }
    }
}
