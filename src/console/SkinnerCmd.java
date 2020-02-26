package console;

import benchmark.BenchUtil;
import buffer.BufferManager;
import catalog.CatalogManager;
import catalog.info.TableInfo;
import compression.Compressor;
import config.GeneralConfig;
import config.NamingConfig;
import config.StartupConfig;
import ddl.TableCreator;
import diskio.LoadCSV;
import diskio.PathUtil;
import execution.Master;
import indexing.Indexer;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import parallel.ParallelService;
import print.RelationPrinter;
import query.SQLexception;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Runs Skinner command line console.
 *
 * @author immanueltrummer
 */
public class SkinnerCmd {
    /**
     * Path to database directory.
     */
    static String dbDir;

    /**
     * Checks whether file exists and displays
     * error message if not. Returns true iff
     * the file exists.
     *
     * @param filePath check for file at that location
     * @return true iff the file exists
     */
    static boolean fileOrError(String filePath) {
        if ((new File(filePath)).exists()) {
            return true;
        } else {
            System.out.println("Error - input file at " +
                    filePath + " does not exist");
            return false;
        }
    }

    /**
     * Processes a command for benchmarking all queries in a
     * given directory.
     *
     * @param input input command
     * @throws Exception
     */
    static void processBenchCmd(String input) throws Exception {
        String[] inputFrags = input.split("\\s");
        if (inputFrags.length < 3) {
            System.out.println("Error - specify only path "
                    + "to directory containing queries and "
                    + "name of output file");
        } else {
            // Check whether directory exists
            String dirPath = inputFrags[1];
            if (fileOrError(dirPath)) {
                if (dirPath.endsWith(".sql")) {
                    String outputName = inputFrags[2];
                    int warmupIterations = Integer.valueOf(inputFrags[3]);

                    FileWriter fw = new FileWriter(outputName, true);
                    PrintWriter benchOut = new PrintWriter(fw);
                    File file = new File(dirPath);
                    String queryName = file.getName();

                    String sql = new String(Files.readAllBytes(file.toPath()));
                    System.out.println(sql);
                    Statement sqlStatement = CCJSqlParserUtil.parse(sql);
                    Select select = (Select) sqlStatement;
                    PlainSelect query =
                            (PlainSelect) select.getSelectBody();

                    for (int i = 0; i < warmupIterations; i++) {
                        processSQL(query.toString(), true);
                        System.runFinalization();
                        System.gc();
                        Thread.sleep(2500);
                    }


                    long startMillis = System.currentTimeMillis();
                    processSQL(query.toString(), true);
                    long totalMillis = System.currentTimeMillis() - startMillis;
                    BenchUtil.writeStats(queryName, totalMillis, benchOut);
                    benchOut.close();
                    return;
                }

                // Open benchmark result file and write header
                String outputName = inputFrags[2];
                PrintWriter benchOut = new PrintWriter(outputName);
                BenchUtil.writeBenchHeader(benchOut);
                // Load all queries to benchmark
                Map<String, PlainSelect> nameToQuery =
                        BenchUtil.readAllQueries(dirPath);
                List<String> keys = new ArrayList<>(nameToQuery.keySet());
                Collections.shuffle(keys);
                // Iterate over queries
                for (String queryName : keys) {
                    PlainSelect query = nameToQuery.get(queryName);
                    System.out.println(queryName);
                    System.out.println(query.toString());
                    long startMillis = System.currentTimeMillis();
                    processSQL(query.toString(), true);
                    long totalMillis = System.currentTimeMillis() - startMillis;
                    BenchUtil.writeStats(queryName, totalMillis, benchOut);
                    System.runFinalization();
                    System.gc();
                    Thread.sleep(2500);
                }
                // Close benchmark result file
                benchOut.close();
            }
        }
    }

    /**
     * Processes a command for loading data from a CSV file on disk.
     *
     * @param input input command
     * @throws Exception
     */
    static void processLoadCmd(String input) throws Exception {
        // Load data from file into table
        String[] inputFrags = input.split("\\s");
        if (inputFrags.length != 5) {
            System.out.println("Error - specify table name, "
                    + "path to .csv file, separator, and null "
                    + "value representation, "
                    + "separated by spaces.");
        } else {
            // Retrieve schema information on table
            String tableName = inputFrags[1];
            TableInfo table = CatalogManager.
                    currentDB.nameToTable.get(tableName);
            // Does the table exist?
            if (table == null) {
                System.out.println("Error - cannot find table " + tableName);
            } else {
                String csvPath = inputFrags[2];
                // Does input path exist?
                if (fileOrError(csvPath)) {
                    String separatorStr = inputFrags[3];
                    if (separatorStr.length() != 1) {
                        System.out.println("Inadmissible separator: " +
                                separatorStr + " (requires one character)");
                    } else {
                        char separator = separatorStr.charAt(0);
                        String nullRepresentation = inputFrags[4];
                        LoadCSV.load(csvPath, table,
                                separator, nullRepresentation);
                    }
                }
            }
        }
    }

    /**
     * Processes SQL commands in specified file.
     *
     * @param input input string for script command
     * @throws Exception
     */
    static void processFile(String input) throws Exception {
        // Check whether right parameters specified
        String[] inputFrags = input.split("\\s");
        if (inputFrags.length != 2) {
            System.err.println("Error - specify script path");
        } else {
            String path = inputFrags[1];
            // Verify whether input file exists
            if (fileOrError(path)) {
                Scanner scanner = new Scanner(new File(path));
                scanner.useDelimiter(Pattern.compile(";"));
                while (scanner.hasNext()) {
                    String sqlCmd = scanner.next().trim();
                    try {
                        System.out.println("Processing statement '" + sqlCmd + "'");
                        processInput(sqlCmd);
                    } catch (Exception e) {
                        System.err.println("Error processing command " + sqlCmd);
                        e.printStackTrace();
                    }
                }
                scanner.close();
            }
        }
    }

    /**
     * Process input string as SQL statement.
     *
     * @param input    input text
     * @param benchRun whether this is a benchmark run (query results
     *                 are not printed for benchmark runs)
     * @throws Exception
     */
    static void processSQL(String input, boolean benchRun) throws Exception {
        // Try parsing as SQL query
        Statement sqlStatement = null;
        try {
            sqlStatement = CCJSqlParserUtil.parse(input);
        } catch (Exception e) {
            System.out.println("Error in parsing SQL command");
            return;
        }
        // Distinguish statement type
        if (sqlStatement instanceof CreateTable) {
            TableInfo table = TableCreator.addTable(
                    (CreateTable) sqlStatement);
            CatalogManager.currentDB.storeDB();
            System.out.println("Created " + table.toString());
        } else if (sqlStatement instanceof Drop) {
            Drop drop = (Drop) sqlStatement;
            String tableName = drop.getName().getName();
            // Verify that table to drop exists
            if (!CatalogManager.currentDB.nameToTable.containsKey(tableName)) {
                throw new SQLexception("Error - table " +
                        tableName + " does not exist");
            }
            CatalogManager.currentDB.nameToTable.remove(tableName);
            CatalogManager.currentDB.storeDB();
            System.out.println("Dropped " + tableName);
        } else if (sqlStatement instanceof Select) {
            Select select = (Select) sqlStatement;
            if (select.getSelectBody() instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                boolean printResult = plainSelect.getIntoTables() == null;
                try {
                    long startTime = System.currentTimeMillis();
                    Master.executeSelect(plainSelect,
                            false, -1, -1, null);
                    long endTime = System.currentTimeMillis();
                    // Display query result if no target tables specified
                    // and if this is not a benchmark run.
                    if (!benchRun && printResult) {
                        // Display on console
                        int cardinality =
                                CatalogManager.getCardinality(
                                        NamingConfig.FINAL_RESULT_NAME);
                        RelationPrinter.print(
                                NamingConfig.FINAL_RESULT_NAME);
                        System.out.println(cardinality + " result tuples.");
                        System.out.println((endTime - startTime) + " ms.");
                    }
                } catch (SQLexception e) {
                    System.out.println(e.getMessage());
                } catch (Exception e) {
                    throw e;
                } finally {
                    // Clean up intermediate results
                    BufferManager.unloadTempData();
                    CatalogManager.removeTempTables();
                }
            } else {
                System.out.println("Only plain select statements supported");
            }
        } else {
            System.out.println("Statement type " +
                    sqlStatement.getClass().toString() +
                    " not supported!");
        }
    }

    /**
     * Processes an explain statement.
     *
     * @param inputFrags fragments of user input - should be explain
     *                   keyword, plot directory, plot bound, and plot
     *                   frequency, followed by query fragments.
     * @throws Exception
     */
    static void processExplain(String[] inputFrags) throws Exception {
        String plotDir = inputFrags[1];
        if (fileOrError(plotDir)) {
            int plotAtMost = Integer.parseInt(inputFrags[2]);
            int plotEvery = Integer.parseInt(inputFrags[3]);
            // Try parsing as SQL query
            StringBuilder sqlBuilder = new StringBuilder();
            int nrFragments = inputFrags.length;
            for (int fragCtr = 4; fragCtr < nrFragments; ++fragCtr) {
                sqlBuilder.append(inputFrags[fragCtr]);
                sqlBuilder.append(" ");
            }
            Statement sqlStatement = null;
            try {
                sqlStatement = CCJSqlParserUtil.parse(sqlBuilder.toString());
            } catch (Exception e) {
                System.out.println("Error in parsing SQL command");
                return;
            }
            // Execute explain command
            if (sqlStatement instanceof Select) {
                Select select = (Select) sqlStatement;
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                try {
                    Master.executeSelect(plainSelect, true,
                            plotAtMost, plotEvery, plotDir);
                    // Output final result
                    String resultRel = NamingConfig.FINAL_RESULT_NAME;
                    RelationPrinter.print(resultRel);
                } catch (SQLexception e) {
                    System.out.println(e.getMessage());
                } catch (Exception e) {
                    throw e;
                } finally {
                    // Clean up intermediate results
                    BufferManager.unloadTempData();
                    CatalogManager.removeTempTables();
                }
            } else {
                System.out.println("Error - explain command supports "
                        + "only simple select queries");
            }
        }
    }

    /**
     * Executes input command, returns false iff
     * the input was a termination command.
     *
     * @param input input command to process
     * @return false iff input was termination command
     * @throws Exception
     */
    static boolean processInput(String input) throws Exception {
        // Delete semicolons if any
        input = input.replace(";", "");
        // Determine input category
        if (input.equals("quit")) {
            // Terminate console
            return false;
        } else if (input.startsWith("bench")) {
            processBenchCmd(input);
        } else if (input.equals("compress")) {
            Compressor.compress();
        } else if (input.startsWith("exec")) {
            processFile(input);
        } else if (input.startsWith("explain")) {
            String[] inputFrags = input.split("\\s");
            processExplain(inputFrags);
        } else if (input.equals("help")) {
            System.out.println("'bench <query Dir> <output file>' to " +
                    "benchmark queries in *.sql files");
            System.out.println("'compress' to compress database");
            System.out.println("'exec <SQL file>' to execute file");
            System.out.println("'explain <Plot Dir> <Plot Bound> "
                    + "<Plot Frequency> <Query>' to visualize query execution");
            System.out.println("'help' for help");
            System.out.println("'index all' to index each column");
            System.out.println("'list' to list database tables");
            System.out.println("'load <table> <CSV file> <separator> <NULL " +
                    "representation>' "
                    + "to load table data from .csv file");
            System.out.println("'quit' for quit");
            System.out.println("Write SQL queries in a single line");
        } else if (input.equals("index all")) {
            Indexer.indexAll(StartupConfig.INDEX_CRITERIA);
        } else if (input.equals("list")) {
            // Show overview of the database
            System.out.println(CatalogManager.currentDB.toString());
        } else if (input.startsWith("load ")) {
            processLoadCmd(input);
        } else if (input.isEmpty()) {
            // Nothing to do ...
        } else {
            try {
                processSQL(input, false);
            } catch (SQLexception e) {
                System.out.println(e.getMessage());
            }
        }
        return true;
    }

    /**
     * Run Skinner console, using database schema
     * at specified location.
     *
     * @param args path to database directory
     */
    public static void main(String[] args) throws Exception {
        // Verify number of command line arguments
        if (args.length != 1) {
            System.out.println("Error - specify the path"
                    + " to database directory!");
            return;
        }
        // Load database schema and initialize path mapping
        dbDir = args[0];
        ParallelService.init();
        PathUtil.initSchemaPaths(dbDir);
        CatalogManager.loadDB(PathUtil.schemaPath);
        PathUtil.initDataPaths(CatalogManager.currentDB);
        // Load data and/or dictionary
        if (GeneralConfig.inMemory) {
            // In-memory data processing
            BufferManager.loadDB();
        } else {
            // Disc data processing (not fully implemented!) -
            // string dictionary is still loaded.
            BufferManager.loadDictionary();
        }
        CatalogManager.generateStats();

        if (System.console() == null) { // Piped input
            boolean continueProcessing = true;
            Scanner scanner = new Scanner(System.in);
            while (continueProcessing) {
                try {
                    String input = scanner.nextLine();
                    continueProcessing = processInput(input);
                } catch (NoSuchElementException e) {
                    continueProcessing = false;
                } catch (Exception e) {
                    System.err.println("Error processing command: ");
                    e.printStackTrace();
                }
            }
        } else {
            // Command line processing
            System.out.println("Enter 'help' for help and 'quit' to exit");
            LineReader reader = LineReaderBuilder.builder()
                    .history(new DefaultHistory())
                    .build();
            boolean continueProcessing = true;
            while (continueProcessing) {
                try {
                    String input = reader.readLine("> ");
                    continueProcessing = processInput(input);
                } catch (UserInterruptException e) {
                    // Ignore
                } catch (EndOfFileException e) {
                    continueProcessing = false;
                } catch (Exception e) {
                    System.err.println("Error processing command: ");
                    e.printStackTrace();
                }
            }
        }

        ParallelService.shutdown();
    }
}
