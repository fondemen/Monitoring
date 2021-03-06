package fr.uha.ensisa.projet2A.monitoring;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.elasticsearch.common.xcontent.XContentFactory;

public class ElasticSearchUtil {

	static boolean verbose;
	static ZoneId zone;
	private static Client client;

	/**
	 * Initialize ElasticSearch connection
	 * 
	 * @param clusterName
	 * @param host
	 * @param port
	 * @throws UnknownHostException
	 */
	public static void initElasticSearch(String clusterName, String host, int port) throws UnknownHostException {
		Settings settings = Settings.builder().put("cluster.name", clusterName).build();
		client = new PreBuiltTransportClient(settings)
				.addTransportAddress(new TransportAddress(InetAddress.getByName(host), port));
	}

	/**
	 * Close the Elasticsearch client stream
	 */
	public static void closeES() {
		client.close();
	}

	/**
	 * Return true if the index "update" already exist
	 * 
	 * @param indexName
	 * @return
	 */
	public static boolean isIndexRegistered() {

		IndicesExistsResponse response = client.admin().indices().prepareExists("update")
				.get(TimeValue.timeValueSeconds(1));

		if (!response.isExists()) {
			return false;
		}
		return true;
	}

	/**
	 * Returns the label according to the state of the machine
	 * 
	 * @param state
	 * @return
	 */
	public static String getStateLabel(int state) {

		switch (state) {
		case 0:
			return "Off";
		case 1:
			return "Stop";
		case 2:
			return "Run";
		case 3:
			return "Alarm";
		}
		return null;

	}

	/**
	 * Return the state according to the state label of the machine
	 * 
	 * @param label
	 * @return
	 */
	public static int getStateByLabel(String label) {

		if (label.equals("Off")) {
			return 0;
		}
		if (label.equals("Stop")) {
			return 1;
		}
		if (label.equals("Run")) {
			return 2;
		}
		if (label.equals("Alarm")) {
			return 3;
		}

		return -1;
	}

	/**
	 * Index an update
	 * 
	 * @param firstUpdate
	 * 
	 * @throws IOException
	 */
	public static void indexUpdate(MachineUpdate firstUpdate) throws IOException {
		// A changer -> créer l'index avec le premier élement à pousser dans la
		// base
		if (!isIndexRegistered()) {
			client.prepareIndex("update", "MachineUpdate").setSource(XContentFactory.jsonBuilder().startObject()
					.field("machineID", firstUpdate.getMachineID()).field("machineName", firstUpdate.getMachineName())
					.field("state", firstUpdate.getState()).field("stateLabel", getStateLabel(firstUpdate.getState()))
					.field("time", firstUpdate.getTime()).endObject()).execute().actionGet();
		}
		System.out.println("Indexation of ES done");
	}

	/**
	 * Put data by parsing an object to JSON into the ElasticSearch index "update"
	 * 
	 * @param update
	 * @throws IOException
	 */
	public static void putData(MachineUpdate update) throws IOException {

		if (!update.equals(null)) {
			IndexRequest indexRequest = new IndexRequest("update", "MachineUpdate");
			indexRequest.source(XContentFactory.jsonBuilder().startObject().field("machineID", update.getMachineID())
					.field("machineName", update.getMachineName()).field("state", update.getState())
					.field("stateLabel", getStateLabel(update.getState())).field("time", update.getTime()).endObject());

			client.index(indexRequest).actionGet();
		}

	}

	public static MachineUpdate getLastUpdate(int machineID) throws InterruptedException, ExecutionException, ParseException {

		try {
			SearchResponse response = client.prepareSearch("update").setTypes("MachineUpdate")
					.setQuery(QueryBuilders.termsQuery("machineID", Integer.toString(machineID))).setSize(1).addSort("time", SortOrder.DESC).get();
			SearchHits hits = response.getHits();
			if (hits.getTotalHits() != 0) {
				Map<String, Object> hit = hits.getAt(0).getSourceAsMap();
				
				MachineUpdate ret = new MachineUpdate();
				ret.setMachineID(machineID);
	
				ret.setState(Integer.parseInt(hit.get("state").toString()));
				ret.setStateLabel(hit.get("stateLabel").toString());
				ret.setMachineName(hit.get("machineName").toString());
				
				String lastTime = hit.get("time").toString();
				// Change of the date format from "yyyy-MM-dd'T'HH:mm:ss.SSSX" to
				// "yyyy-MM-dd HH:mm:ss.S"
				//DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX"); 
				ret.setTime(Timestamp.from(Instant.parse(lastTime)));
				
				return ret;
			}
		} catch (Exception x) {
			x.printStackTrace();
		}

		return null;

	}
	
	/**
	 * Delete an index by name
	 * 
	 * @param client
	 * @param indexName
	 */
	public static void deleteIndex(String indexName) throws Exception {
		client.admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet();
		System.out.println("Index : " + indexName + " deleted");
	}

	/**
	 * Return true if the elasticsearch database is empty
	 * 
	 * @return
	 */
	public static boolean isESDatabaseEmpty() {
		SearchResponse response = client.prepareSearch("update").setTypes("MachineUpdate")
				.setQuery(QueryBuilders.matchAllQuery()).setSize(0).get();

		SearchHits hits = response.getHits();
		long hitsCount = hits.getTotalHits();

		return hitsCount == 0;
	}

	public static Client getClient() {
		return client;
	}

}
