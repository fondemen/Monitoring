package fr.uha.ensisa.projet2A.monitoring;

import static fr.uha.ensisa.projet2A.monitoring.ElasticSearchUtil.verbose;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;

import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
import net.wimpi.modbus.msg.ReadInputDiscretesResponse;
import net.wimpi.modbus.net.TCPMasterConnection;

public class Moxa {
	
	ZoneId zone = ZoneId.systemDefault();

	private TCPMasterConnection connection = null;
	private ModbusTCPTransaction transaction = null;
	private ReadInputDiscretesRequest rreq = null;
	private ReadInputDiscretesResponse rres = null;

	/**
	 * Retrieve data and return a list of update object
	 * 
	 * @param IPs
	 * @param port
	 * @return
	 * @throws Exception
	 */
	public ArrayList<MachineUpdate> pooling(String[] IPs, String[] machineNames, int port) throws Exception {

		ArrayList<MachineUpdate> updates = new ArrayList<MachineUpdate>();
		
		Instant today = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(0, 0), zone).toInstant();
		if (ElasticSearchUtil.verbose) System.out.println("today is " + today);
		
		for (int i = 0; i < IPs.length; i++) {
			InetAddress inet = InetAddress.getByName(IPs[i]);
			
			int state = -1;

			if (inet.isReachable(3000)) {

				connection = new TCPMasterConnection(inet);
				connection.setPort(port);
				connection.connect();
				if (verbose) System.out.println("The machine : " + inet.getHostAddress() + " is on ( " + machineNames[i] + " )");

				this.rreq = new ReadInputDiscretesRequest(0, 2);
				this.transaction = new ModbusTCPTransaction(connection);
				this.transaction.setRequest(rreq);
				this.transaction.execute();
				this.rres = (ReadInputDiscretesResponse) transaction.getResponse();

				if (verbose) {
					System.out.println("Sortie 0 :" + rres.getDiscreteStatus(0));
					System.out.println("Sortie 1 :" + rres.getDiscreteStatus(1));
					System.out.println("Sortie 2 :" + rres.getDiscreteStatus(2));
					System.out.println("Sortie 3 :" + rres.getDiscreteStatus(3));
				}
				
				if (rres.getDiscreteStatus(0) == false && rres.getDiscreteStatus(1) == false) {
					state = 3 ; //Réglage 
				} else if (rres.getDiscreteStatus(0) == false && rres.getDiscreteStatus(1) == true) {
					state = 2;  //Production
				} else {
					state = 1 ; //Arrêt 
				}

				connection.close();

			} else {
				if (verbose) System.out.println("The machine : " + inet.getHostAddress() + " is off ");
				state = 0;
			}
			
			MachineUpdate lastUpdate = ElasticSearchUtil.getLastUpdate(i+2);
			if (lastUpdate == null || lastUpdate.getTime().toInstant().isBefore(today)) {
				if (ElasticSearchUtil.verbose) System.out.println("Inserting previous state as day changed for " + machineNames[i] + 
						(lastUpdate == null ? "no previous state found" : (" last state found on " + lastUpdate.getTime().toInstant())));
				// We should write it in the DB
			} else if (lastUpdate.getState() == state) {
				// No need to write that to the DB (state didn't change)
				if (ElasticSearchUtil.verbose) System.out.println("Non need to insert new state " + machineNames[i]);
				state = -1;
			} else {
				if (ElasticSearchUtil.verbose) System.out.println("New state found for " + machineNames[i]);
			}

			if (state != -1) {
				MachineUpdate update = new MachineUpdate();
				update.setMachineName(machineNames[i]);
				update.setMachineID(i + 2); // ID = 1 is for DMG_CTX and ID = 0 isn't attributed
				update.setState(state);
				update.setStateLabel(ElasticSearchUtil.getStateLabel(update.getState()));
				update.setTime(new Timestamp(System.currentTimeMillis()));
				updates.add(update);
			}
		}

		return updates;

	}

	public ModbusTCPTransaction getTransaction() {
		return transaction;
	}

	public void setTransaction(ModbusTCPTransaction transaction) {
		this.transaction = transaction;
	}

	public ReadInputDiscretesRequest getRreq() {
		return rreq;
	}

	public void setRreq(ReadInputDiscretesRequest rreq) {
		this.rreq = rreq;
	}

	public ReadInputDiscretesResponse getRres() {
		return rres;
	}

	public void setRres(ReadInputDiscretesResponse rres) {
		this.rres = rres;
	}

}
