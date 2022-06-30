package it.cnr.istc.stlab.lgu;

import com.github.jsonldjava.shaded.com.google.common.collect.Sets;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RDFParserBuilder;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFBase;
import org.apache.jena.sparql.core.Quad;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.rocksdb.*;
import org.rocksdb.util.SizeUnit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;


public class CorpusBuilderLaundromat {

    public final static String virtualDocumentFilename = "virtualdocument.txt.bz2",
            generaredFilename = "generated.txt";
    private static final Logger log = LogManager.getLogger(CorpusBuilderLaundromat.class);
    private final RocksDB labelMap;
    private final String laundromatFolder, folderOut;
    private final NumberFormat format;
    private final AtomicLong processedFiles = new AtomicLong(0), skipped = new AtomicLong(0);
    private final Set<Character> toFilter = Sets.newHashSet('_', '-', '.', ',', ':', '|', '\'', '\\');
    private RDFParserBuilder b = RDFParser.create().lang(Lang.NQUADS);
    private boolean filelist = false;

    public CorpusBuilderLaundromat(RocksDB labelMap, String laundromatFolder, String folderOut)
            throws RocksDBException {
        super();

        format = NumberFormat.getInstance();
        format.setGroupingUsed(true);

        this.labelMap = labelMap;
        this.laundromatFolder = laundromatFolder;
        this.folderOut = folderOut;

    }

	public static void main(String[] args) throws RocksDBException {
		RocksDB labelMapDB = openLabelMapDB(args[2],
				10);
		CorpusBuilderLaundromat cb = new CorpusBuilderLaundromat(labelMapDB, args[0],
				args[1]);
		cb.build();
		labelMapDB.close();
	}

    private static List<String> readFileToList(String filename) {
        List<String> result = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(new File(filename)));
            String line;
            while ((line = br.readLine()) != null) {
                result.add(line);
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static RocksDB openLabelMapDB(String pathDB, int gbCahce) throws RocksDBException {
        System.out.println("Opening " + pathDB);
        Options options = new Options();
        BlockBasedTableConfig tableOptions = new BlockBasedTableConfig();
        // table_options.block_size = 16 * 1024;
        tableOptions.setBlockSize(16 * 1024);
        // table_options.cache_index_and_filter_blocks = true;
        tableOptions.setCacheIndexAndFilterBlocks(true);
        // table_options.pin_l0_filter_and_index_blocks_in_cache = true;
        tableOptions.setPinL0FilterAndIndexBlocksInCache(true);
        // https://github.com/facebook/rocksdb/wiki/Setup-Options-and-Basic-Tuning#block-cache-size
        tableOptions.setBlockCache(new LRUCache(gbCahce * SizeUnit.GB));
        //@f:off
        options.setCreateIfMissing(true)
//			.setWriteBufferSize(512 * SizeUnit.MB)
//			.setMaxWriteBufferNumber(2)
                .setIncreaseParallelism(Runtime.getRuntime().availableProcessors())
                // table options
                .setTableFormatConfig(tableOptions)
                // cf_options.level_compaction_dynamic_level_bytes = true;
                .setLevelCompactionDynamicLevelBytes(true)
                // options.max_background_compactions = 4;
                .setMaxBackgroundCompactions(4)
                // options.max_background_flushes = 2;
                .setMaxBackgroundFlushes(2)
                // options.bytes_per_sync = 1048576;
                .setBytesPerSync(1048576)
                // options.compaction_pri = kMinOverlappingRatio;
                .setCompactionPriority(CompactionPriority.MinOverlappingRatio);
        //@f:on
        options.setMergeOperator(new StringAppendOperator());
        RocksDB db = RocksDB.open(options, pathDB);
        return db;

    }



    public boolean isFilelist() {
        return filelist;
    }

    public void setFilelist(boolean filelist) {
        this.filelist = filelist;
    }

    public void build() {
        log.info("Start");
        System.out.println("Start");

        try {

            ScheduledExecutorService ses = Executors.newScheduledThreadPool(1);
            ses.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    log.info("Files processed " + processedFiles.get());
                }
            }, 1, 1, TimeUnit.MINUTES);

            if (new File(laundromatFolder).isDirectory()) {
                log.info(laundromatFolder + " folder");
                Files.walk(Paths.get(laundromatFolder)).parallel()
                        .filter(f -> FilenameUtils.getName(f.toFile().getAbsolutePath()).equals("data.nq.gz"))
                        .forEach(new DatasetConsumer());
            } else {
                log.info(laundromatFolder + " file");
                List<String> files = readFileToList(laundromatFolder);

                List<Path> paths = new ArrayList<>();
                files.forEach(f -> {
                    Path p = new File(f).toPath();
                    if (!new File(p.getParent().toString() + "/" + generaredFilename).exists()
                            && new File(p.getParent().toString()).exists()) {
                        paths.add(new File(f).toPath());
                    } else {
//						log.info(f.toString() + " alredy verbalized!");
                    }

                });

                log.info("Files to process " + paths.size());

                paths.parallelStream().forEach(new DatasetConsumer());
            }

            log.info("All files processed");
            ses.shutdownNow();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public String verbaliseNode(final Node n) throws RocksDBException {
        try {
            if (n.isURI()) {
                final byte[] lsb = labelMap.get(n.getURI().getBytes());
                if (lsb == null) {
                    return Utils.clean(new StringBuilder(URIUtils.getID(n.getURI())), toFilter).toString();
                } else {
                    return new String(lsb);
                }
            } else if (n.isLiteral()) {
                return n.getLiteral().getValue().toString();
            }
        } catch (Exception e) {
//			e.printStackTrace();
        }
        return "";
    }

    class DatasetConsumer implements Consumer<Path> {

        @Override
        public void accept(Path t) {
            log.info("Processing " + t.getFileName().toString() + " " + processedFiles.get() + " " + skipped.get());

            // String fileOutPath = t.getParent().toString() + "/" +
            // virtualDocumentFilename;
            String virtualDocumentFolderPath = folderOut + "/" + t.getParent().getParent().getFileName().toString() + "/"
                    + t.getParent().getFileName().toString();
            new File(virtualDocumentFolderPath).mkdirs();
            String fileOutPath = virtualDocumentFolderPath + "/" + virtualDocumentFilename;

            String generatedFilePath = t.getParent().toString() + "/" + generaredFilename;

            if (!(new File(generatedFilePath).exists())) {
                final FileOutputStream fos_f;
                final OutputStream os;
                final File fileout = new File(fileOutPath);

                if (fileout.exists()) {
                    fileout.delete();
                }

                try {
                    fos_f = new FileOutputStream(new File(fileOutPath));
                    os = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2,
                            fos_f);

                    final StreamRDF s = new StreamRDFBase() {
                        public void triple(Triple q) {
                            try {
                                os.write(verbalizeTriple(q).getBytes());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        public void quad(Quad q) {
                            try {
                                os.write(verbalizeTriple(q.asTriple()).getBytes());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };

                    final InputStream is = new GZIPInputStream(new FileInputStream(t.toFile()), 20 * 1024);
                    final BufferedReader br = new BufferedReader(new InputStreamReader(is), 20 * 1024);
                    final AtomicLong al = new AtomicLong(0L);
                    String l;
                    StringBuilder sb = new StringBuilder();
                    while ((l = br.readLine()) != null) {

                        sb.append(l);
                        sb.append('\n');
                        al.incrementAndGet();

                        if (al.get() % 10000 == 0) {
                            sb = parse(t, s, al, sb);
                        }

                    }

                    if (al.get() % 10000 > 0)
                        sb = parse(t, s, al, sb);

                    fos_f.flush();
                    os.flush();

                    os.close();
                    fos_f.close();

                    FileOutputStream fos = new FileOutputStream(new File(generatedFilePath));
                    fos.write(new Date(System.currentTimeMillis()).toString().getBytes());
                    fos.flush();
                    fos.close();

                    processedFiles.incrementAndGet();
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                    log.error(e1.getMessage());
                } catch (CompressorException e1) {
                    e1.printStackTrace();
                    log.error(e1.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error(e.getMessage());
                }
                log.trace(fileOutPath + " written!");

            } else {
                log.info(fileOutPath + " already written! Skip! ");
                skipped.incrementAndGet();
            }
        }

        StringBuilder parse(Path t, final StreamRDF s, final AtomicLong al, StringBuilder sb) {
            log.trace("Parsing");
            String toParse = sb.toString();
            try {
                b.fromString(toParse).parse(s);
            } catch (Exception e) {
                log.error("Error line " + t.getParent().toFile().getAbsolutePath());
                for (String ll : toParse.split("\n")) {
                    b.fromString(ll).parse(s);
                }
            }
            sb = new StringBuilder();
            log.info("Parsed " + al.get() + " lines from " + t.getParent().toFile().getAbsolutePath());
            return sb;
        }

        public String verbalizeTriple(final Triple ts) {

            final StringBuilder sb = new StringBuilder();

            try {
                sb.append(' ');
                sb.append(verbaliseNode(ts.getSubject()));
                sb.append(' ');
                sb.append(verbaliseNode(ts.getPredicate()));
                sb.append(' ');
                sb.append(verbaliseNode(ts.getObject()));
                sb.append('\n');
            } catch (Exception e) {
                e.printStackTrace();
            }

            log.trace("Verbalized triple " + sb);

            return sb.toString();

        }
    }

}
