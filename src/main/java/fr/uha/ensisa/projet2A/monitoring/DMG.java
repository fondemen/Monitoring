package fr.uha.ensisa.projet2A.monitoring;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

public class DMG {
	private final ZoneId zone;
	private final TimeZone timezone;

	private Connection connection;
	private PreparedStatement st;
	private ResultSet result;

	public DMG(ZoneId zone) throws ClassNotFoundException {
		Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
		this.zone = zone;
		this.timezone = TimeZone.getTimeZone(zone.getId());
	}

	/**
	 * Open the connection with the DMG SQL Server
	 * @param timezone 
	 * @throws SQLException 
	 */
	public void openConnection(String url) throws SQLException {
		this.connection = DriverManager.getConnection(url);
	}

	/**
	 * Create a list of MachineUpdate object with the DMG History
	 * 
	 * @return
	 * @throws SQLException
	 */
	public ArrayList<MachineUpdate> queryDBHistory() throws SQLException {

		String query = "SELECT Status , Time from mdetail";
		this.st = this.connection.prepareStatement(query);
		this.result = st.executeQuery();

		ArrayList<MachineUpdate> updates = new ArrayList<MachineUpdate>();
		while (this.result.next()) {

			MachineUpdate update = new MachineUpdate();
			update.setMachineID(1);
			update.setMachineName("DMG_CTX");
			update.setState(ElasticSearchUtil.getStateByLabel(result.getString("Status")));
			update.setStateLabel(result.getString("Status"));
			update.setTime(result.getTimestamp("Time",Calendar.getInstance(this.timezone)));

			updates.add(update);
		}

		return updates;

	}

	/**
	 * Return the timestamp of the last modification into the database
	 * 
	 * @return
	 * @throws SQLException
	 */
	public Instant getLastUpdateTime() throws SQLException {

		String query = "SELECT Time from mdetail where (select max(Id) from mdetail)=Id";
		this.st = this.connection.prepareStatement(query);
		this.result = st.executeQuery();

		Timestamp lastDate = null;
		while (this.result.next()) {
			lastDate = result.getTimestamp("Time",Calendar.getInstance(this.timezone));
		}

		return Instant.ofEpochMilli(lastDate.getTime());
	}

	/**
	 * Return the list of updates object from a specified date
	 * 
	 * @param lastESDate
	 * @return
	 * @throws SQLException
	 */
	public ArrayList<MachineUpdate> getUpdatesFromLastDate(Instant lastESDate) throws SQLException {
		ZonedDateTime start = lastESDate.atZone(this.zone);
		String formattedStart = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"));
		if (ElasticSearchUtil.verbose) System.out.println("Searching SQL Server for DMX updates since " + formattedStart);

		String query = "SELECT Status , Time from mdetail WHERE Time > \'" + formattedStart + "\'";

		this.st = this.connection.prepareStatement(query);
		this.result = st.executeQuery();
		ArrayList<MachineUpdate> updates = new ArrayList<MachineUpdate>();
		while (this.result.next()) {

			MachineUpdate update = new MachineUpdate();
			update.setMachineID(1);
			update.setMachineName("DMG_CTX");
			update.setState(ElasticSearchUtil.getStateByLabel(result.getString("Status")));
			update.setStateLabel(result.getString("Status"));
			update.setTime(result.getTimestamp("Time",Calendar.getInstance(this.timezone)));
			updates.add(update);
		}

		return updates;
	}

	public Connection getConnection() {
		return connection;
	}

}
