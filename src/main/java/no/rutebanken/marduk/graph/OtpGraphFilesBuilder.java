package no.rutebanken.marduk.graph;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphsInfo;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Retrieve the most recent version of OTP graph files for the latest two serialization ids.
 * A regex is used to identify the serialization id which is a part of the graph file name.
 * The regex is different for transit graphs and street graphs.
 */
public class OtpGraphFilesBuilder {
    private Collection<BlobStoreFiles.File> files;
    private Pattern regex;

    public OtpGraphFilesBuilder withFileNameRegex(String regex) {
        return withFileNameRegex(Pattern.compile(regex));
    }

    public OtpGraphFilesBuilder withFileNameRegex(Pattern regex) {
        this.regex = regex;
        return this;
    }

    public OtpGraphFilesBuilder withFiles(Collection<BlobStoreFiles.File> files) {
        this.files = files;
        return this;
    }

    public List<OtpGraphsInfo.OtpGraphFile> build() {

        Map<String, Optional<BlobStoreFiles.File>> filesGroupedBySerializationId = files.stream().collect(Collectors.groupingBy(this::getSerializationId, Collectors.reducing(this::keepNewestFile)));

        return filesGroupedBySerializationId.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().isEmpty())
                .filter(entry -> entry.getValue().isPresent())
                .map(entry ->  {
                    BlobStoreFiles.File file = entry.getValue().get();
                    return new OtpGraphsInfo.OtpGraphFile(file.getFileNameOnly(), entry.getKey(),  file.getCreated(), file.getFileSize());
                })
                .sorted(Comparator.comparing(OtpGraphsInfo.OtpGraphFile::serializationId).reversed())
                .limit(2)
                .toList();
    }

    private BlobStoreFiles.File keepNewestFile(BlobStoreFiles.File f1, BlobStoreFiles.File f2) {
        if(f1.getCreated().isAfter(f2.getCreated())) {
            return f1;
        }
        return f2;
    }


    private String getSerializationId(BlobStoreFiles.File file) {
        Matcher m = regex.matcher(file.getName());
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

}
