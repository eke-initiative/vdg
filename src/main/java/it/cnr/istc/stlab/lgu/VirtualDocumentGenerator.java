package it.cnr.istc.stlab.lgu;

import com.github.owlcs.ontapi.OntManagers;
import com.github.owlcs.ontapi.Ontology;
import com.github.owlcs.ontapi.OntologyManager;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.datatypes.DatatypeFormatException;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFBase;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.tdb2.TDB2Factory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class VirtualDocumentGenerator {

    private static final Logger logger = LoggerFactory.getLogger(VirtualDocumentGenerator.class);

    private String folderPath, folderOut;
    private Strategy strategy = Strategy.ALL_TRIPLES;
    private boolean stream = false;
    private List<String> targetPrefixes;
    private Map<String, String> cache = new HashMap<String, String>();
    private String tdb;

    public VirtualDocumentGenerator(String folderPath, String folderOut) {
        this.folderPath = folderPath;
        this.folderOut = folderOut;
    }

    public static void main(String[] args) throws IOException {
//		VirtualDocumentGenerator vdg = new VirtualDocumentGenerator("src/main/resources/ontologies",
//				"src/main/resources/virtualDocuments");
//		vdg.getIndex("src/main/resources/getindex.txt");
//		vdg.collectOntologies("src/main/resources/ontologyindex.txt");
//		vdg.generateDocuments();
//		Model m = ModelFactory.createDefaultModel();
//		RDFDataMgr.read(m, "/Users/lgu/workspace/ekr/vdg/src/main/resources/ontologies/3b523078c5726c2976feb5e5b3ff2362/3b523078c5726c2976feb5e5b3ff2362",Lang.TTL);
//		System.out.println(m.size());

//		BioPortalFetcher bpf = new BioPortalFetcher("875000b0-fc87-45ce-8e96-c4b06e58dd2d",
//				"src/main/resources/ontologies/bioportal", "http://data.bioontology.org");
//		bpf.fetchOntologies();
//
//		BioPortalFetcher apf = new BioPortalFetcher("a83a7b93-4b88-4b30-abee-9c8c15f5246b",
//				"src/main/resources/ontologies/agroportal", "http://data.agroportal.lirmm.fr");
//		apf.fetchOntologies();

//		DERIFetcher df = new DERIFetcher("src/main/resources/adms.rdf", "src/main/resources/ontologies/DERI");
//		df.fetchDERIOntologies();
//		Model m = vdg.read("/Users/lgu/workspace/ekr/vdg/src/main/resources/ontologies/agroportal/GEMET/GEMET");
//		System.out.println(m.size());

        VirtualDocumentGenerator vdg = new VirtualDocumentGenerator(null,
                "/Users/lgu/Desktop/NOTime/EKR/_DatasetClean/VirtualDocuments/TPB");
        List<String> prefixes = Utils.readPrefixesFromTSV("/Users/lgu/Desktop/NOTime/EKR/_DatasetClean/InputRDF/dataset_ids");
        vdg.setTargetPrefixes(prefixes);
        vdg.setTdb("/Users/lgu/Desktop/NOTime/EKR/_DatasetClean/InputRDF/TPB");
//		vdg.useStream(true);
        vdg.generateDocuments();

//		System.err.println(vdg.getTargetPrefixStringContains("http://wikier.org"));
//		System.err.println(vdg.getTargetPrefixStringContains("http://www.w3.org/2006/03/wn/wn20/asd"));
//		System.err.println(vdg.getTargetPrefixStringContains("wifo5-03.informatik.uni-mannheim.de/bookmashup"));

    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public void useStream(boolean useStream) {
        this.stream = useStream;
    }

    public void setTargetPrefixes(List<String> list) {
        targetPrefixes = list;
    }

    public String getTdb() {
        return tdb;
    }

    public void setTdb(String tdb) {
        this.tdb = tdb;
    }

    public void generateDocuments() throws IOException {
        new File(folderOut).mkdirs();
        FileOutputStream fosIndex = new FileOutputStream(new File(folderOut + "/index.tsv"));

        if (tdb != null) {
            processTDB(fosIndex, new File(tdb).toPath());
        } else {
            Files.walk(Paths.get(folderPath)).filter(Files::isRegularFile).filter(f -> !f.toFile().isHidden())
                    .forEach(f -> {
                        if (FilenameUtils.isExtension(f.toString(), "nq")
                                || (FilenameUtils.isExtension(f.toString(), "gz")
                                && FilenameUtils.isExtension(FilenameUtils.getBaseName(f.toString()), "nq"))) {
                            try {
                                processNqFile(fosIndex, f);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        } else {
                            try {
                                Model m = read(f.toFile().getAbsolutePath());
                                if (m == null) {
                                    logger.error("Can't read {}", f.toFile().getAbsolutePath());
                                } else {
                                    logger.info("{} read!", f.toFile().getAbsolutePath());
                                    String uri = getOntologyURI(m);
                                    Map<String, String> labels = indexLabelsForModel(m);
                                    generatedVirtualDocumentForModel(m, uri, labels, fosIndex);
                                }
                            } catch (Exception e) {
                                logger.error("Can't read {}", f.toFile().getAbsolutePath());
                            }
                        }
                    });
            fosIndex.flush();
            fosIndex.close();
        }
    }

    void processNqFile(FileOutputStream fosIndex, Path f) throws FileNotFoundException {
        if (!stream) {
            try {
                Dataset d = loadDatasetFromNQuads(f.toFile().getAbsolutePath().toString());
                Map<String, String> labels = indexLabelsForNQuads(d);
                d.listNames().forEachRemaining(n -> {
                    Model m = d.getNamedModel(n);
                    generatedVirtualDocumentForModel(m, n, labels, fosIndex);
                });
                d.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {

            Map<String, String> labels = indexLabelsForNQuads(f.toFile().getAbsolutePath().toString());
            ProgressCounter pc = new ProgressCounter().setCheckpoint(100000).setSLF4jLogger(logger);
            Set<String> notFound = new HashSet<>();

            StreamRDF sink = new StreamRDFBase() {
                OutputStream os = null;
                String osUri = null;

                public void triple(Triple triple) {
                    pc.increase();
                    try {
                        os.write(getVirtualSentenceForTriple(triple, labels).getBytes());
                        os.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                public void quad(Quad quad) {
                    String targetUri = getTargetPrefix(quad.getGraph().getURI());
                    if (targetUri == null) {
//						logger.error("Couldn't find {}", quad.getGraph().getURI());
                        notFound.add(quad.getGraph().getURI());
                        return;
                    }
                    if (osUri == null || !osUri.equals(targetUri)) {
                        try {
                            if (osUri != null) {
                                os.flush();
                                os.close();
                            }
                            osUri = targetUri;
                            String folderout = folderOut + "/" + DigestUtils.md5Hex(targetUri);
                            String fileout = folderout + "/virtualdocument.txt.bz2";
                            new File(folderout).mkdirs();

                            if (!new File(fileout).exists()) {
                                logger.trace("New graph {}", targetUri);
                                try {
                                    fosIndex.write(targetUri.getBytes());
                                    fosIndex.write('\t');
                                    fosIndex.write(folderout.getBytes());
                                    fosIndex.write('\n');
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            try {
                                os = new CompressorStreamFactory().createCompressorOutputStream(
                                        CompressorStreamFactory.BZIP2, new FileOutputStream(new File(fileout), true));
                            } catch (FileNotFoundException | CompressorException e) {
                                e.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    triple(quad.asTriple());
                }
            };

            RDFDataMgr.parse(sink, f.toFile().getAbsolutePath());

            logger.info("Graphs not found ({}): ", notFound.size());
            for (String g : notFound) {
                logger.info(g);
            }
        }
    }

    void processTDB(FileOutputStream fosIndex, Path f) throws FileNotFoundException {
        logger.trace("Processing TDB");
        try {
            logger.trace("Connecting TDB");
            Dataset d = TDB2Factory.connectDataset(f.toFile().getAbsolutePath());
            logger.trace("Connected");
            d.begin(ReadWrite.READ);
            logger.trace("Indexing labels");
            Map<String, String> labels = indexLabelsForNQuads(d);
            logger.trace("Labels indexed");
            d.listNames().forEachRemaining(n -> {
                String targetLabel = getTargetPrefixStringContains(n);
                if (targetLabel != null) {
                    logger.trace("Processing {}", n);
                    Model m = d.getNamedModel(n);
                    generatedVirtualDocumentForModel(m, targetLabel, labels, fosIndex);
                } else {
                    logger.trace("Skip {}", n);
                }
            });
            d.end();
            d.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    private String getOntologyURI(Model m) {
        StmtIterator si = m.listStatements(null, RDF.type, OWL.Ontology);
        if (si.hasNext()) {
            return si.next().asTriple().getSubject().getURI();
        }
        return null;
    }

    private Model read(String uri) {
        Model m = ModelFactory.createDefaultModel();
        if (read(uri, m, Lang.RDFXML)) {
            return m;
        }
        if (read(uri, m, Lang.TTL)) {
            return m;
        }
        if (read(uri, m, Lang.NTRIPLES)) {
            return m;
        }
        if (read(uri, m, Lang.JSONLD)) {
            return m;
        }
        if (readWithOWLAPI(new File(uri), m)) {
            return m;
        }
        if (read(uri, m, Lang.RDFTHRIFT)) {
            return m;
        }
        if (read(uri, m, Lang.RDFJSON)) {
            return m;
        }
        return null;
    }

    private boolean read(String uri, Model m, Lang l) {
        try {
            RDFDataMgr.read(m, uri, l);
            return true;
        } catch (RiotException e) {
            return false;
        }
    }

    private void generatedVirtualDocumentForModel(Model m, String uri, Map<String, String> labels,
                                                  FileOutputStream fosIndex) {
        String foldername = DigestUtils.md5Hex(uri);
        File folder = new File(folderOut + "/" + foldername);
        if (!folder.exists()) {
            folder.mkdir();
            getNamedDocumentFromVirtualDocument(m, labels, folderOut + "/" + foldername + "/virtualdocument.txt.bz2");
            try {
                fosIndex.write(uri.getBytes());
                fosIndex.write('\t');
                fosIndex.write(foldername.getBytes());
                fosIndex.write('\n');
            } catch (IOException e) {
                e.printStackTrace();
            }
            m.close();
        }
    }

    public void getNamedDocumentFromVirtualDocument(Model m, Map<String, String> labels, String fileOut) {
        try {
            OutputStream os = new CompressorStreamFactory().createCompressorOutputStream(CompressorStreamFactory.BZIP2,
                    new FileOutputStream(new File(fileOut), true));

            switch (this.strategy) {
                case ALL_TRIPLES:
                    m.getGraph().find().forEachRemaining(t -> {
                        try {
                            os.write(getVirtualSentenceForTriple(t, labels).getBytes());
                            os.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                    break;
                case CLASSES_PROPERTIES_ONTOLOGIES_LABELS_AND_COMMENTS:
                    // retrieve labels and comments of classes
                    m.getGraph().find(null, RDF.type.asNode(), OWL.Class.asNode())
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getSubject()));
                    // retrieve labels and comments of ontologies
                    m.getGraph().find(null, RDF.type.asNode(), OWL.Ontology.asNode())
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getSubject()));
                    // retrieve labels and comments of ontologies
                    m.getGraph().find(null, null, null)
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getPredicate()));
                    m.getGraph().find(null, RDF.type.asNode(), OWL.ObjectProperty.asNode())
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getSubject()));
                    m.getGraph().find(null, RDF.type.asNode(), OWL.DatatypeProperty.asNode())
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getSubject()));
                    m.getGraph().find(null, RDF.type.asNode(), RDF.Property.asNode())
                            .forEachRemaining(t -> writeLabelsAndComments(m, labels, os, t.getSubject()));

                    break;

            }

            os.flush();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void writeLabelsAndComments(Model m, Map<String, String> labels, OutputStream os, Node n) {
        // class comments
        m.getGraph().find(n, RDFS.comment.asNode(), null).forEachRemaining(tc -> {
            if (tc.getObject().isLiteral()) {
                try {
                    os.write(tc.getObject().getLiteral().getValue().toString().getBytes());
                    os.write(' ');
                } catch (DatatypeFormatException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        // class labels
        try {
            os.write(resolveLabel(n, labels).getBytes());
            os.write(' ');
            os.flush();
        } catch (DatatypeFormatException | IOException e) {
            e.printStackTrace();
        }
    }

    public String getVirtualSentenceForTriple(Triple t, Map<String, String> labels) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolveLabel(t.getSubject(), labels));
        sb.append(' ');
        sb.append(resolveLabel(t.getPredicate(), labels));
        sb.append(' ');
        sb.append(resolveLabel(t.getObject(), labels));
        sb.append('\n');
        return sb.toString();
    }

    private String resolveLabel(Node n, Map<String, String> labels) {
        if (n.isURI()) {
            if (labels.containsKey(n.getURI())) {
                return labels.get(n.getURI());
            } else {
                return Utils.uncamelize(n.getLocalName());
            }
        } else if (n.isLiteral()) {
            try {
                return n.getLiteralValue().toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return "";
    }

    private Map<String, String> indexLabelsForNQuads(Dataset ds) throws FileNotFoundException {
        String q = "SELECT * {GRAPH ?g {?s ?lab ?o}}";
        ParameterizedSparqlString pss = new ParameterizedSparqlString(q);
        pss.setIri("lab", RDFS.label.getURI());
        QueryExecution qexec = QueryExecutionFactory.create(pss.asQuery(), ds);
        ResultSet rs = qexec.execSelect();
        Map<String, String> labels = new HashMap<>();
        String l, k;
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            if (qs.get("s").isResource() && qs.get("o").isLiteral()) {
                l = Utils.uncamelize(qs.get("o").asLiteral().getString());
                k = qs.get("s").asResource().getURI();
                if (!labels.containsKey(k)) {
                    labels.put(k, l);
                } else {
                    labels.put(k, labels.get(k) + " " + l);
                }
            }
        }
        qexec.close();
        logger.trace("Labels collected {}", labels.size());
        return labels;

    }

    private Map<String, String> indexLabelsForNQuads(String file) throws FileNotFoundException {
        logger.trace("Indexing labels contained in {}", file);
        Map<String, String> labels = new HashMap<>();
        ProgressCounter pc = new ProgressCounter().setCheckpoint(100000).setSLF4jLogger(logger);

        StreamRDF sink = new StreamRDFBase() {

            @Override
            public void triple(Triple triple) {
                pc.increase();
                if (triple.getSubject().isURI() && triple.getPredicate().equals(RDFS.label.asNode())
                        && triple.getObject().isLiteral()) {
                    String label = Utils.uncamelize(triple.getObject().getLiteral().getValue().toString());
                    String k = triple.getSubject().getURI();
                    if (!labels.containsKey(k)) {
                        labels.put(k, label);
                    } else {
                        labels.put(k, labels.get(k) + " " + label);
                    }
                }

            }

            @Override
            public void quad(Quad quad) {
                triple(quad.asTriple());
            }

        };
        RDFDataMgr.parse(sink, file);
        logger.trace("Number of labels in {}: {} ", file, labels.size());
        return labels;

    }

    public Map<String, String> indexLabelsForModel(Model ds) throws FileNotFoundException {
        String q = "SELECT * {?s ?lab ?o}";
        ParameterizedSparqlString pss = new ParameterizedSparqlString(q);
        pss.setIri("lab", RDFS.label.getURI());
        QueryExecution qexec = QueryExecutionFactory.create(pss.asQuery(), ds);
        ResultSet rs = qexec.execSelect();
        Map<String, String> labels = new HashMap<>();
        String l, k;
        QuerySolution qs;
        while (rs.hasNext()) {
            qs = rs.next();
            if (qs.get("s").isResource() && qs.get("o").isLiteral()) {
                l = Utils.uncamelize(qs.get("o").asLiteral().getString());
                k = qs.get("s").asResource().getURI();
                if (!labels.containsKey(k)) {
                    labels.put(k, l);
                } else {
                    labels.put(k, labels.get(k) + " " + l);
                }
            }
        }
        qexec.close();
        logger.trace("Labels collected {}", labels.size());
        return labels;

    }

    public void collectOntologies(String indexFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(indexFile));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("#")) {
                logger.trace("Processing {}", line);
                try {
                    downloadOntology(line);
                } catch (Exception e) {
                    logger.error("Error {} {}", e.getMessage(), line);
                }
            } else {
                logger.trace("Comment:{}", line);
            }
        }
        br.close();
    }

    public void getIndex(String indexFile) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(indexFile));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.startsWith("#")) {
                String folder = folderPath + "/" + DigestUtils.md5Hex(line);
                File folderFile = new File(folder);
                if (folderFile.exists()) {
                    logger.trace("Already exists!");
                    continue;
                }
                folderFile.mkdir();
                FileUtils.copyURLToFile(new URL(line), new File(folder + "/" + DigestUtils.md5Hex(line)));
            } else {
                logger.trace("Comment:{}", line);
            }

        }
        br.close();
    }

    private String getTargetPrefix(String uriGraph) {
        if (targetPrefixes.contains(uriGraph)) {
            return uriGraph;
        }

        if (cache.containsKey(uriGraph)) {
            return cache.get(uriGraph);
        }

        try {
            URL u = new URL(uriGraph);
            String[] path = u.getPath().split("/");
            StringBuilder sb = new StringBuilder();
            sb.append(u.getProtocol());
            sb.append("://");
            sb.append(u.getHost());

            if (targetPrefixes.contains(sb.toString())) {
                cache.put(uriGraph, sb.toString());
                return sb.toString();
            }

            for (String p : path) {
                if (p.length() == 0)
                    continue;
                sb.append("/");
                sb.append(p);
                if (targetPrefixes.contains(sb.toString())) {
                    cache.put(uriGraph, sb.toString());
                    return sb.toString();
                }
            }
        } catch (MalformedURLException e) {
        }
        cache.put(uriGraph, null);
        return null;
    }

    private String getTargetPrefixStringContains(String uriGraph) {

        if (cache.containsKey(uriGraph)) {
            return cache.get(uriGraph);
        }

        String match = null;
        for (String targetPrefix : targetPrefixes) {
            if (uriGraph.toLowerCase().contains(targetPrefix.toLowerCase())
                    && ((match != null && targetPrefix.length() > match.length()) || match == null)) {
                match = targetPrefix;
            }
        }
        cache.put(uriGraph, match);
        return match;
    }

    private void downloadOntology(String url) throws IOException {
        logger.trace("Downloading {}", url);
        String folder = folderPath + "/" + DigestUtils.md5Hex(url);
        File folderFile = new File(folder);
        if (folderFile.exists()) {
            logger.trace("Already exists!");
            return;
        }
        folderFile.mkdir();

        OWLOntology ontology;
        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            ontology = loadOntology(manager, url);
            logger.trace("{} loaded!", url);

            String filename;
            String iri = getIRIString(ontology);
            if (iri != null) {
                filename = folder + "/" + FilenameUtils.getBaseName(iri) + ".owl";
            } else {
                filename = folder + "/ontology.owl";
            }
            logger.trace("{} saved!", url);

            FileOutputStream fos = new FileOutputStream(new File(filename));
            manager.saveOntology(ontology, new RDFXMLDocumentFormat(), fos);
            fos.flush();
            fos.close();
            ontology.directImportsDocuments().forEach(i -> {
                try {
                    logger.trace("importing {}", i.getIRIString());
                    downloadOntology(i.getIRIString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

        } catch (OWLOntologyCreationException e1) {
            e1.printStackTrace();
        } catch (OWLOntologyStorageException e) {
            e.printStackTrace();
        }
    }

    private String getIRIString(OWLOntology o) {
        if (o.getOntologyID().getOntologyIRI().isPresent()) {
            return o.getOntologyID().getOntologyIRI().get().toString();
        }
        return null;
    }

    private Dataset loadDatasetFromNQuads(String file) {
        logger.trace("Loading {}", file);
        Dataset ds = DatasetFactory.create();
        RDFDataMgr.read(ds, file);
        logger.trace("Loaded {} {}", file, ds.getUnionModel().size());
        return ds;
    }

    private OWLOntology loadOntology(OWLOntologyManager manager, String url) throws OWLOntologyCreationException {
        OWLOntologyLoaderConfiguration loadingConfig = new OWLOntologyLoaderConfiguration();
        loadingConfig = loadingConfig.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        loadingConfig = loadingConfig.setRepairIllegalPunnings(true);
        IRIDocumentSource s = new IRIDocumentSource(IRI.create(url));
        return manager.loadOntologyFromOntologyDocument(s, loadingConfig);
    }

    private boolean readWithOWLAPI(File f, Model m) {
        logger.info("Loading {}", f.getAbsolutePath());
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        try {
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(f);
            logger.info("Number of axioms {}", ontology.axioms().count());
            OntologyManager ontManager = OntManagers.createManager();
            Ontology ontOntology = ontManager.copyOntology(ontology, OntologyCopy.DEEP);
            Model m1 = ontOntology.asGraphModel();
            m.add(m1);
            logger.info("Number of triples {} {} {}", ontOntology.asGraphModel().size(), m1.size(), m.size());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public enum Strategy {
        ALL_TRIPLES, CLASSES_PROPERTIES_ONTOLOGIES_LABELS_AND_COMMENTS
    }

}
