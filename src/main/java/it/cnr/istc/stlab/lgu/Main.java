package it.cnr.istc.stlab.lgu;

import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws RocksDBException, IOException {
        String mode = args[0];

        if (mode.equalsIgnoreCase("Laundromat")) {
            System.out.println("Creating Virtual Documents for Laundromat.\nLaundromat folder: " + args[1] + "\nLabel Index: " + args[2] + "\nOut folder: " + args[3]);
            // args[1] <Laundromat_folder> args[2] out_folder args[3] labelMap
            RocksDB labelMapDB = CorpusBuilderLaundromat.openLabelMapDB(args[3], 10);
            CorpusBuilderLaundromat cb = new CorpusBuilderLaundromat(labelMapDB, args[1], args[2]);
            cb.build();
            labelMapDB.close();
        } else if (mode.equalsIgnoreCase("TPB")) {
            // args[1] TPB_folder args[2] datasets_ids args[3] out_folder
            System.out.println("Creating Virtual Documents for Topic Profiling Benchmark.\nInput folder: " + args[1] + "\nDataset index: " + args[2] + "\nOut folder: " + args[3]);
            VirtualDocumentGenerator vdg = new VirtualDocumentGenerator(null, args[3]);
            List<String> prefixes = Utils.readPrefixesFromTSV(args[2]);
            vdg.setTargetPrefixes(prefixes);
            vdg.setTdb(args[1]);
            vdg.generateDocuments();
        } else if (mode.equalsIgnoreCase("LOV")) {
            // args[1] lov_pagh args[2] out_folder
            System.out.println("Creating Virtual Documents for Linked Open Vocabularies.\nInput folder: " + args[1] + "\nOut folder: " + args[2]);
            VirtualDocumentGenerator vdg = new VirtualDocumentGenerator(args[1], args[2]);
            vdg.generateDocuments();
        }
    }
}
