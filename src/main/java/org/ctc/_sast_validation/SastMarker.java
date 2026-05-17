package org.ctc._sast_validation;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.Statement;

public class SastMarker {

	public static String unsafe(HttpServletRequest req, Statement stmt) throws Exception {
		String sql = "SELECT * FROM users WHERE id = " + req.getParameter("id");
		ResultSet rs = stmt.executeQuery(sql);
		return rs.toString();
	}
}
