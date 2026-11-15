package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CarImageRestorer implements EntityRestorer {
    private static final String SQL = "INSERT INTO car_images (id,car_id,kind,source,source_key,label,image_path,width,height,sort_index,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() { return "car_images"; }
    @Override public void restore(List<JsonNode> rows, JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL, rows, 500, (ps, row) -> GalleryImageRows.bind(ps, row, "car"));
    }
}
