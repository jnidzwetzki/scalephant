package org.bboxdb.experiments.conference;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.concurrent.TimeUnit;

import org.bboxdb.commons.math.Hyperrectangle;
import org.bboxdb.storage.entity.Tuple;
import org.bboxdb.tools.converter.tuple.TupleBuilder;
import org.bboxdb.tools.converter.tuple.TupleBuilderFactory;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;
import com.google.common.base.Stopwatch;

public class TestBaselineApproach implements AutoCloseable {

	/**
	 * The cassandra cluster object
	 */
	private Cluster cluster;

	/**
	 * The cassandra session object
	 */
	private Session session;

	public TestBaselineApproach(final Cluster cluster) {
		this.cluster = cluster;
		this.session = cluster.connect();
	}

	@Override
	public void close() throws Exception {
		session.close();
		cluster.close();
	}

	/**
	 * Import data into cassandra
	 * @param args
	 */
	public void executeImport(final String args[]) {
		if(args.length != 4) {
			System.err.println("Usage: import <file> <desttable>");
			System.exit(-1);
		}

		executeImport(args[1], args[2]);
	}

	/**
	 * Import data into cassandra
	 * @param sourceFile
	 * @param format
	 * @param destTable
	 */
	public void executeImport(final String sourceFile, final String destTable) {
		session.execute("CREATE TABLE " + destTable + "(id long, data text, PRIMARY KEY(id)");

		final PreparedStatement prepared = session.prepare("INSERT INTO " + destTable
				+ " (id, text) values (?, ?)");

		long lineNumber = 0;
		String line = null;

		try(
				final BufferedReader br = new BufferedReader(new FileReader(new File(sourceFile)));
		) {

			while((line = br.readLine()) != null) {
				session.execute(prepared.bind(lineNumber, line));
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	/**
	 * Execute a range query
	 * @param args
	 */
	public void executeRangeQuery(final String args[]) {
		if(args.length != 4) {
			System.err.println("Usage: rangequery <desttable> <format> <range>");
			System.exit(-1);
		}

		final Hyperrectangle hyperrectangle = new Hyperrectangle(args[3]);

		executeRangeQuery(args[1], args[2], hyperrectangle);
	}

	/**
	 * Execute a range query
	 * @param destTable
	 * @param range
	 */
	public void executeRangeQuery(final String destTable, final String format, final Hyperrectangle range) {
		System.out.println("# Execute range query in range " + range);

		final Stopwatch stopwatch = Stopwatch.createStarted();
		final TupleBuilder tupleBuilder = TupleBuilderFactory.getBuilderForFormat(format);

		long readRecords = 0;
		long resultRecords = 0;

		final SimpleStatement statement = new SimpleStatement("SELECT * FROM " + destTable);
		statement.setFetchSize(2000); // Use 2000 tuples per page

		final ResultSet rs = session.execute(statement);

		for (final Row row : rs) {

			// Request next page
		    if (rs.getAvailableWithoutFetching() == 100 && !rs.isFullyFetched()) {
		        rs.fetchMoreResults(); // this is asynchronous
		    }

		    readRecords++;

		    final long id = row.getLong(0);
		    final String text = row.getString(1);

		    final Tuple tuple = tupleBuilder.buildTuple(Long.toString(id), text);

		    if(tuple.getBoundingBox().intersects(range)) {
		    	resultRecords++;
		    }
		}

		System.out.println("# Read records " + readRecords + " result records " + resultRecords);
		System.out.println("# Execution time in sec " + stopwatch.elapsed(TimeUnit.SECONDS));
	}

	/**
	 * Execute a join
	 * @param args
	 */
	public void executeJoin(final String args[]) {
		if(args.length != 5) {
			System.err.println("Usage: join <table1> <table2> <format> <range>");
			System.exit(-1);
		}

		final Hyperrectangle hyperrectangle = new Hyperrectangle(args[4]);

		executeJoin(args[1], args[2], args[3], hyperrectangle);
	}

	/**
	 * Execute a join
	 * @param table1
	 * @param table2
	 * @param range
	 */
	public void executeJoin(final String table1, final String table2, final String format,
			final Hyperrectangle range) {

	}


	/**
	 * Main
	 * @param args
	 */
	public static void main(final String[] args) throws Exception {

		final Cluster cluster = Cluster.builder()
				.addContactPoint("127.0.0.1")
				.build();

		final TestBaselineApproach testBaselineApproach = new TestBaselineApproach(cluster);

		if(args.length == 0) {
			System.err.println("Usage: <Class> <import|rangequery|join>");
			System.exit(-1);
		}

		final String command = args[0];

		switch(command) {
		case "import":
			testBaselineApproach.executeImport(args);
			break;
		case "rangequery":
			testBaselineApproach.executeRangeQuery(args);
			break;
		case "join":
			testBaselineApproach.executeRangeQuery(args);
			break;
		default:
			System.err.println("Unkown command: " + command);
			System.exit(-1);
		}

		testBaselineApproach.close();
	}

}
