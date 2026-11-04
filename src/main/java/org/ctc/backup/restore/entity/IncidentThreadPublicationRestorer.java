package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.ctc.backup.restore.EntityRestorer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncidentThreadPublicationRestorer implements EntityRestorer {
    private static final String SQL="INSERT INTO incident_thread_publications (id,delivery_id,stewards_channel_id,staff_role_ids,forum_tag_ids,message_chunks,private_starter_id,private_thread_id,private_message_ids,public_starter_id,public_thread_id,public_message_ids,reference_message_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    @Override public String tableName() {return "incident_thread_publications";}
    @Override public void restore(List<JsonNode> rows,JdbcTemplate jdbc) {
        jdbc.batchUpdate(SQL,rows,500,(ps,row) -> {
            ps.setObject(1,IncidentRestoreSupport.uuid(row,"id"));
            ps.setObject(2,IncidentRestoreSupport.uuid(row,"delivery"));
            ps.setObject(3,IncidentRestoreSupport.text(row,"stewardsChannelId"));
            ps.setObject(4,IncidentRestoreSupport.text(row,"staffRoleIds"));
            ps.setObject(5,IncidentRestoreSupport.text(row,"forumTagIds"));
            ps.setObject(6,IncidentRestoreSupport.number(row,"messageChunks"));
            ps.setObject(7,IncidentRestoreSupport.text(row,"privateStarterId"));
            ps.setObject(8,IncidentRestoreSupport.text(row,"privateThreadId"));
            ps.setObject(9,IncidentRestoreSupport.text(row,"privateMessageIds"));
            ps.setObject(10,IncidentRestoreSupport.text(row,"publicStarterId"));
            ps.setObject(11,IncidentRestoreSupport.text(row,"publicThreadId"));
            ps.setObject(12,IncidentRestoreSupport.text(row,"publicMessageIds"));
            ps.setObject(13,IncidentRestoreSupport.text(row,"referenceMessageId"));
            ps.setObject(14,PickRestoreSupport.auditTime(row,"createdAt"));
            ps.setObject(15,PickRestoreSupport.auditTime(row,"updatedAt"));
        });
    }
}
