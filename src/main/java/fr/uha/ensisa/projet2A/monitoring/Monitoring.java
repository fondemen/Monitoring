package fr.uha.ensisa.projet2A.monitoring;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Monitoring {

	private static MonitoringConfiguration config;
	private static DMG dmg;
	private static Moxa moxa;
	private static String configFilePath;

	public static void main(String[] args) throws Exception {

		// Get project configuration from your configuration file
		try {
			if (args.length == 0) {
				configFilePath = "D:\\Cours\\2A\\Projet 2A Monitoring\\config.txt";
				config = new MonitoringConfiguration(configFilePath);
				System.out.println(config);
			} else if (args.length == 1) {
				System.out.println("Configuration file charged = " + args[0]);
				config = new MonitoringConfiguration(args[0]);
				System.out.println(config);
			}
		} catch (Exception e) {
			System.out.println("Cannot read/parse txt file : " + configFilePath);
			System.exit(0);
		}
		
		final boolean verbose = config.isVerbose();
		ElasticSearchUtil.verbose = verbose;

		// Initialization of ES connection
		try {
			ElasticSearchUtil.initElasticSearch(config.getClusterNameES(), config.getHostES(), config.getPortES());
		} catch (Exception e) {
			System.out.println("Have you started Elasticsearch ?");
			e.printStackTrace();
		}
		
		// Open connection to the DMG SQL Server
		dmg = new DMG(config.getDmgTimezone());
		try{
			dmg.openConnection(config.getHostDMGSQL());
			System.out.println("Connected to SQL DMG database");
		}catch(Exception e) {
			System.out.println("Connection to SQL DMG host failed");
			e.printStackTrace();
		}

		// Indexation
		ElasticSearchUtil.indexUpdate(dmg.queryDBHistory().get(0));

		// Open connection to the machines Moxa
		moxa = new Moxa();
		moxa.zone = config.getDmgTimezone();
		final String[] IPs = config.getIPs();
		final String[] machineNames = config.getMachineNames();
		final int moxaPort = config.getMoxaPort();

		// Add of a first element into ES database
		if (ElasticSearchUtil.getLastUpdate(1 /* DMG is hardcoded to 1 */) == null) {
			System.out.println("No DMG history found");
			ElasticSearchUtil.putData(dmg.queryDBHistory().get(1));
		}
		
		Runnable dmgRunnable = new Runnable() {
			
			private boolean first = true;

			@Override
			public void run() {
				try {
					// Init
					MachineUpdate lastUpdate = ElasticSearchUtil.getLastUpdate(1);
					Instant lastESDate = lastUpdate == null ? null : lastUpdate.getTime().toInstant();
					Instant lastSQLDate = dmg.getLastUpdateTime();
					
					if (first) {
						System.out.println("Recovering DMG history from " + lastESDate + " to " + lastSQLDate);
						first = false;
					}
					
					int i = 0;

					if (verbose) { 
						 System.out.println("lastESDate = " + lastESDate);
						 System.out.println("lastSQLDate = " + lastSQLDate);
					}

					// DMG
					if (lastESDate != null && lastESDate.isBefore(lastSQLDate)) {
						if (verbose) { 
							 System.out.println("New data from DMG_CTX SQL Server");
							 System.out.println("****** Loading new data ****** ");
						}
						for (MachineUpdate update : dmg.getUpdatesFromLastDate(lastESDate)) {
							ElasticSearchUtil.putData(update);
							System.out.println(update);
							i++;
						}
					}

					if (verbose) {
						if (i == 0) {
							System.out.println("****** No data from the DMG CTX SQL server ******");
						} else {
							System.out.println("******" + i + " update(s) charged from the DMG CTX SQL server  ******");
						}
					}

				} catch (InterruptedException | ExecutionException | ParseException | SQLException | IOException e) {
					try {
						if (dmg.getConnection().isClosed()) {
							System.out.println("Connection lost with SQL Server. Reboot...");
							dmg.openConnection(config.getHostDMGSQL());
						}
					} catch (SQLException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		};

		Runnable moxaRunnable = new Runnable() {

			@Override
			public void run() {
				try {
					int i = 0;
					ArrayList<MachineUpdate> updates = moxa.pooling(IPs, machineNames, moxaPort);
					if (!updates.isEmpty()) {
						if (verbose) {
							System.out.println("New data from machines connected with a Moxa");
							System.out.println("****** Loading new data ****** ");
						}
						for (MachineUpdate update : updates) {
							ElasticSearchUtil.putData(update);
							System.out.println(update);
							i++;
						}
					}
					if (i == 0) {
						if (verbose) System.out.println("****** No data from machines connected with a moxa ******");
					} else {
						System.out.println("******" + i + " update(s) charged from machines connected with a moxa ******");
					}

				} catch (Exception e) {
					if (verbose) e.printStackTrace();
				}
			}
		};

		ScheduledExecutorService monitoringExecutor = Executors.newScheduledThreadPool(2);
		monitoringExecutor.scheduleAtFixedRate(dmgRunnable, 0, config.getDmgPoolingPeriod(), TimeUnit.SECONDS);
		monitoringExecutor.scheduleAtFixedRate(moxaRunnable, 0, config.getMoxaPoolingPeriod(), TimeUnit.SECONDS);

	}
}
